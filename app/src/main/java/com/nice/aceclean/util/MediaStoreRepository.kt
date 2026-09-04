package com.nice.aceclean.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MediaFileInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val modifiedSeconds: Long,
    val mimeType: String,
    val relativePath: String,
) {
    fun monthLabel(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(modifiedSeconds * 1000L))
}

object MediaStoreRepository {

    private val projection: Array<String>
        get() = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH)
        }.toTypedArray()

    fun screenshots(context: Context): List<MediaFileInfo> {
        return pictures(context).first
    }

    fun otherPictures(context: Context): List<MediaFileInfo> {
        return pictures(context).second
    }

    fun pictures(context: Context): Pair<List<MediaFileInfo>, List<MediaFileInfo>> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return query(context, collection, null, null).partition { it.isScreenshot() }
    }

    fun videos(context: Context): List<MediaFileInfo> =
        query(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null)

    fun largeFiles(context: Context, minimumBytes: Long = 10L * 1024L * 1024L): List<MediaFileInfo> {
        val collection = MediaStore.Files.getContentUri("external")
        val selection = "${MediaStore.MediaColumns.SIZE} >= ?"
        return query(context, collection, selection, arrayOf(minimumBytes.toString()))
    }

    private fun query(
        context: Context,
        collection: Uri,
        selection: String?,
        arguments: Array<String>?,
    ): List<MediaFileInfo> {
        val files = mutableListOf<MediaFileInfo>()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            arguments,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val pathColumn = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val size = cursor.getLong(sizeColumn)
                if (size <= 0L) continue
                files += MediaFileInfo(
                    uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn)),
                    name = cursor.getString(nameColumn).orEmpty(),
                    sizeBytes = size,
                    modifiedSeconds = cursor.getLong(modifiedColumn),
                    mimeType = cursor.getString(mimeColumn).orEmpty(),
                    relativePath = if (pathColumn >= 0) cursor.getString(pathColumn).orEmpty() else "",
                )
            }
        }
        return files
    }

    private fun MediaFileInfo.isScreenshot(): Boolean =
        name.contains("screenshot", ignoreCase = true) || relativePath.contains("screenshot", ignoreCase = true)
}
