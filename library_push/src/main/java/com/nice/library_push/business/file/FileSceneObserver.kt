package com.nice.library_push.business.file

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.nice.library_push.api.PushEvent
import com.nice.library_push.config.PushScene
import com.nice.library_push.config.PushSceneConfig
import com.nice.library_push.R

/** MediaStore observer; initial query establishes a baseline and never emits old media. */
internal class FileSceneObserver(
    private val app: Application,
    private val emit: (PushEvent) -> Unit,
) {
    private var thread: HandlerThread? = null
    private var imageObserver: ContentObserver? = null
    private var fileObserver: ContentObserver? = null
    private var images = emptyMap<Long, MediaRow>()
    private var files = emptyMap<Long, MediaRow>()
    private var suffixes: List<String> = emptyList()

    @Synchronized fun update(screenshotEnabled: Boolean, fileScene: PushSceneConfig) {
        stop()
        if (!screenshotEnabled && !fileScene.enabled) return
        val thread = HandlerThread("nice-push-media").also { it.start() }
        this.thread = thread
        val handler = Handler(thread.looper)
        if (screenshotEnabled && canReadImages()) {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val baseline = snapshot(uri)
            if (baseline != null) {
                images = baseline
                imageObserver = observer(handler) {
                    val updated = snapshot(uri) ?: return@observer
                    val added = updated.values.filter { it.id !in images }
                    images = updated
                    added.filter { it.name.contains("screenshot", true) || it.name.contains("screen_shot", true) }
                        .forEach { emit(PushEvent(PushScene.SCREENSHOT, title = app.getString(R.string.nice_push_screenshot_saved), content = it.name)) }
                }.also { app.contentResolver.registerContentObserver(uri, true, it) }
            }
        }
        suffixes = fileScene.fileSuffixes
        if (fileScene.enabled && suffixes.isNotEmpty() && canReadFiles()) {
            val uri = MediaStore.Files.getContentUri("external")
            val baseline = snapshot(uri)
            if (baseline != null) {
                files = baseline
                fileObserver = observer(handler) {
                    val updated = snapshot(uri) ?: return@observer
                    val before = files
                    files = updated
                    updated.values.forEach { row ->
                        if (row.name.substringAfterLast('.', "").lowercase() in suffixes) {
                            when {
                                row.id !in before -> emit(PushEvent(PushScene.FILE, 1, title = app.getString(R.string.nice_push_file_new), content = row.name))
                                before[row.id]?.modified != row.modified -> emit(PushEvent(PushScene.FILE, 3, title = app.getString(R.string.nice_push_file_update), content = row.name))
                            }
                        }
                    }
                    val lowestCurrent = updated.keys.minOrNull() ?: Long.MIN_VALUE
                    before.values.filter { it.id !in updated && (updated.size < 100 || it.id >= lowestCurrent) }
                        .filter { it.name.substringAfterLast('.', "").lowercase() in suffixes }
                        .forEach { emit(PushEvent(PushScene.FILE, 2, title = app.getString(R.string.nice_push_file_removed), content = it.name)) }
                }.also { app.contentResolver.registerContentObserver(uri, true, it) }
            }
        }
    }

    private fun observer(handler: Handler, onChange: () -> Unit) = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { onChange() }
        override fun onChange(selfChange: Boolean, uri: Uri?) { onChange() }
    }

    private data class MediaRow(val id: Long, val name: String, val modified: Long)

    private fun snapshot(uri: Uri): Map<Long, MediaRow>? = runCatching {
        app.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_MODIFIED),
            null, null, "${MediaStore.MediaColumns._ID} DESC",
        )?.use { cursor ->
            buildMap {
                while (cursor.moveToNext() && size < 100) {
                    val row = MediaRow(cursor.getLong(0), cursor.getString(1) ?: "", cursor.getLong(2))
                    put(row.id, row)
                }
            }
        }
    }.getOrNull()

    private fun canReadImages() = when {
        Build.VERSION.SDK_INT >= 33 -> has(Manifest.permission.READ_MEDIA_IMAGES)
        else -> has(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun canReadFiles() = when {
        Build.VERSION.SDK_INT >= 30 -> android.os.Environment.isExternalStorageManager()
        else -> has(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun has(permission: String) = ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

    @Synchronized fun stop() {
        imageObserver?.let { app.contentResolver.unregisterContentObserver(it) }
        fileObserver?.let { app.contentResolver.unregisterContentObserver(it) }
        imageObserver = null
        fileObserver = null
        thread?.quitSafely()
        thread = null
        images = emptyMap()
        files = emptyMap()
    }
}
