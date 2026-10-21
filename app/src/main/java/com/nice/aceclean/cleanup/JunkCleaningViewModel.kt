package com.nice.aceclean.cleanup

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.nice.aceclean.similarphoto.SimilarPhotoItem
import com.nice.aceclean.similarphoto.SimilarPhotoScanner
import com.nice.aceclean.util.LogUtils
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 独立清理任务：配置变化时复用任务，避免再次删除同一批文件。 */
class JunkCleaningViewModel(application: Application, savedState: SavedStateHandle) : AndroidViewModel(application) {
    data class State(
        val progress: Int = 0,
        val releasedBytes: Long = 0,
        val result: CleanupDeleteResult? = null,
        val unavailable: Boolean = false,
        val failed: Boolean = false,
        val awaitingPhotoConsent: Boolean = false,
        val cancelled: Boolean = false,
    )

    // 仅传递会话 ID，不将可能含数万文件的列表放入 Intent/Binder。
    private val session = savedState.get<String>(EXTRA_SESSION)?.let(pending::remove)
    internal val type = session?.type ?: savedState.get<String>(EXTRA_TYPE)
        ?.let { value -> CleaningType.entries.firstOrNull { it.name == value } }
        ?: CleaningType.JUNK
    private val photoConsent = MediaDeleteConsent()
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    init {
        when (val task = session) {
            null -> mutableState.value = State(unavailable = true)
            is CleaningSession.Junk -> {
                if (task.candidates.isEmpty()) mutableState.value = State(unavailable = true)
                else startCleaning { report -> CleanupRepository.delete(getApplication(), task.candidates, report) }
            }
            is CleaningSession.SimilarPhotos -> {
                when {
                    task.items.isEmpty() -> mutableState.value = State(unavailable = true)
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                        mutableState.value = State(awaitingPhotoConsent = true)
                    else -> startCleaning { report -> deleteLegacyPhotos(task.items, report) }
                }
            }
        }
    }

    internal fun takePhotoDeleteRequest(): List<Uri>? {
        val photos = (session as? CleaningSession.SimilarPhotos)?.items ?: return null
        if (!mutableState.value.awaitingPhotoConsent || !photoConsent.claimRequest()) return null
        mutableState.update { it.copy(awaitingPhotoConsent = false) }
        return photos.map { it.uri }
    }

    internal fun onPhotoDeleteConsentResult(approved: Boolean): Boolean {
        val photos = (session as? CleaningSession.SimilarPhotos)?.items ?: return false
        if (!photoConsent.complete()) return false
        if (!approved) {
            mutableState.value = State(cancelled = true)
        } else {
            // createDeleteRequest 已在返回 RESULT_OK 前完成删除，不再重复调用 delete。
            SimilarPhotoScanner.invalidateCache()
            startCleaning { report ->
                val bytes = photos.sumOf { it.sizeBytes }
                report(photos.size, photos.size, bytes)
                CleanupDeleteResult(photos.size, 0, bytes)
            }
        }
        return true
    }

    internal fun onPhotoDeleteConsentFailed(error: Exception) {
        if (!photoConsent.complete()) return
        LogUtils.e("Unable to request photo deletion", tag = "JunkCleaning", throwable = error)
        mutableState.value = State(failed = true)
    }

    private suspend fun deleteLegacyPhotos(
        photos: List<SimilarPhotoItem>,
        report: (Int, Int, Long) -> Unit,
    ): CleanupDeleteResult = withContext(Dispatchers.IO) {
        var deleted = 0
        var bytes = 0L
        photos.forEachIndexed { index, photo ->
            currentCoroutineContext().ensureActive()
            val removed = runCatching {
                getApplication<Application>().contentResolver.delete(photo.uri, null, null) > 0
            }.getOrDefault(false)
            if (removed) {
                deleted++
                bytes += photo.sizeBytes
            }
            report(index + 1, photos.size, bytes)
        }
        if (deleted > 0) SimilarPhotoScanner.invalidateCache()
        CleanupDeleteResult(deleted, photos.size - deleted, bytes)
    }

    private fun startCleaning(delete: suspend ((Int, Int, Long) -> Unit) -> CleanupDeleteResult) {
        viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            try {
                coroutineScope {
                    val actualProgress = MutableStateFlow(0)
                    val cleaning = async {
                        delete { processed, total, bytes ->
                            actualProgress.value = (processed.toLong() * 100 / total).toInt()
                            mutableState.update { it.copy(releasedBytes = bytes) }
                        }
                    }
                    // 进度受真实处理结果与最短展示时间共同限制，未完成不会到 100%。
                    while (mutableState.value.progress < 99 || !cleaning.isCompleted) {
                        delay(40L)
                        val timeProgress = ((SystemClock.elapsedRealtime() - started) * 100 / MIN_DISPLAY_MS)
                            .toInt().coerceIn(0, 99)
                        mutableState.update {
                            it.copy(progress = minOf(it.progress + 1, timeProgress, actualProgress.value))
                        }
                    }
                    val result = cleaning.await()
                    mutableState.update { it.copy(progress = 100, releasedBytes = result.releasedBytes) }
                    delay(300L)
                    mutableState.value = State(100, result.releasedBytes, result)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtils.e("Cleanup failed", tag = "JunkCleaning", throwable = error)
                mutableState.value = mutableState.value.copy(failed = true)
            }
        }
    }

    companion object {
        const val EXTRA_SESSION = "junk_cleaning_session"
        const val EXTRA_TYPE = "cleaning_type"
        private const val MIN_DISPLAY_MS = 4_000L
        private val pending = ConcurrentHashMap<String, CleaningSession>()

        fun enqueue(candidates: List<CleanupCandidate>): String = enqueue(CleaningSession.Junk(candidates.toList()))

        fun enqueueSimilarPhotos(items: List<SimilarPhotoItem>): String =
            enqueue(CleaningSession.SimilarPhotos(items.distinctBy { it.uri }))

        private fun enqueue(session: CleaningSession): String = UUID.randomUUID().toString().also {
            pending[it] = session
        }
    }
}
