package com.nice.aceclean.cleanup

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.nice.aceclean.util.LogUtils
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 独立清理任务：配置变化时复用任务，避免再次删除同一批文件。 */
class JunkCleaningViewModel(application: Application, savedState: SavedStateHandle) : AndroidViewModel(application) {
    data class State(
        val progress: Int = 0,
        val releasedBytes: Long = 0,
        val result: CleanupDeleteResult? = null,
        val unavailable: Boolean = false,
        val failed: Boolean = false,
    )

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    init {
        // 仅传递会话 ID，不将可能含数万文件的列表放入 Intent/Binder。
        val candidates = savedState.get<String>(EXTRA_SESSION)?.let(pending::remove)
        if (candidates.isNullOrEmpty()) {
            // 进程被回收后不猜测待删除文件，返回扫描页重新确认。
            mutableState.value = State(unavailable = true)
        } else {
            viewModelScope.launch {
                val started = SystemClock.elapsedRealtime()
                try {
                    coroutineScope {
                        val actualProgress = MutableStateFlow(0)
                        val cleaning = async {
                            CleanupRepository.delete(getApplication(), candidates) { processed, total, bytes ->
                                actualProgress.value = (processed.toLong() * 100 / total).toInt()
                                mutableState.update { it.copy(releasedBytes = bytes) }
                            }
                        }
                        // 每 40ms 最多前进一步，避免快速删除时从 0 瞬间跳到 99。
                        // 同时受真实处理进度限制，任务未完成时不会显示 100%。
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
                        // 让满格与 100% 实际呈现一小段时间，再进入成功页。
                        delay(300L)
                        mutableState.value = State(100, result.releasedBytes, result)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    LogUtils.e("Junk cleanup failed", tag = "JunkCleaning", throwable = error)
                    mutableState.value = mutableState.value.copy(failed = true)
                }
            }
        }
    }

    companion object {
        const val EXTRA_SESSION = "junk_cleaning_session"
        private const val MIN_DISPLAY_MS = 4_000L
        private val pending = ConcurrentHashMap<String, List<CleanupCandidate>>()

        fun enqueue(candidates: List<CleanupCandidate>): String = UUID.randomUUID().toString().also {
            pending[it] = candidates.toList()
        }
    }
}
