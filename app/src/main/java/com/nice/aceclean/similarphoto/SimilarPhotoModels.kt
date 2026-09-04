package com.nice.aceclean.similarphoto

import android.net.Uri

data class SimilarPhotoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val dateTaken: Long,
    val relativePath: String,
)

data class SimilarPhotoCluster(
    val id: Int,
    val items: List<SimilarPhotoRow.Photo>,
)

sealed interface SimilarPhotoRow {
    data class SectionHeader(val clusterId: Int, val count: Int) : SimilarPhotoRow

    data class Photo(
        val item: SimilarPhotoItem,
        val selected: Boolean = false,
        val isBestPhoto: Boolean = false,
    ) : SimilarPhotoRow
}
