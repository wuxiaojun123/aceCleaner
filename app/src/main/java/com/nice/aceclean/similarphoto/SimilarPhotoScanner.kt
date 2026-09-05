package com.nice.aceclean.similarphoto

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SimilarPhotoScanner {
    @Volatile private var signature: String? = null
    @Volatile private var cachedClusters: List<SimilarPhotoCluster> = emptyList()

    fun invalidateCache() {
        signature = null
        cachedClusters = emptyList()
    }

    suspend fun scan(context: Context): List<SimilarPhotoCluster> = withContext(Dispatchers.IO) {
        val candidates = queryCandidates(context)
        val newSignature = candidates.fold(candidates.size.toLong()) { value, item ->
            ((value * 31 + item.id) * 31 + item.sizeBytes) * 31 + item.dateTaken
        }.toString()
        if (newSignature == signature) return@withContext cachedClusters

        val groups = DifferenceHashSimilarPhotoMatcher(context.contentResolver)
            .findSimilarGroups(candidates)
            .sortedWith(
                compareByDescending<List<SimilarPhotoItem>> { it.size }
                    .thenByDescending { group -> group.maxOfOrNull { it.dateTaken } ?: 0L },
            )
        val result = groups.mapIndexed { clusterIndex, group ->
            SimilarPhotoCluster(
                clusterIndex + 1,
                group.mapIndexed { itemIndex, item -> SimilarPhotoRow.Photo(item, isBestPhoto = itemIndex == 0) },
            )
        }
        signature = newSignature
        cachedClusters = result
        result
    }

    private fun queryCandidates(context: Context): List<SimilarPhotoItem> {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.SIZE)
            add(MediaStore.Images.Media.DATE_TAKEN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Images.Media.RELATIVE_PATH)
        }.toTypedArray()
        return buildList {
            resolver.query(collection, projection, null, null, "${MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH) else -1
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    add(
                        SimilarPhotoItem(
                            id,
                            ContentUris.withAppendedId(collection, id),
                            cursor.getString(nameColumn).orEmpty(),
                            cursor.getLong(sizeColumn),
                            cursor.getLong(dateColumn),
                            if (pathColumn >= 0) cursor.getString(pathColumn).orEmpty() else "",
                        ),
                    )
                }
            }
        }
    }
}
