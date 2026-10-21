package com.nice.aceclean.cleanup

import com.nice.aceclean.similarphoto.SimilarPhotoItem

internal enum class CleaningType { JUNK, SIMILAR_PHOTOS }

/** Only the session ID crosses Binder; the ViewModel consumes the payload once. */
internal sealed interface CleaningSession {
    val type: CleaningType

    data class Junk(val candidates: List<CleanupCandidate>) : CleaningSession {
        override val type = CleaningType.JUNK
    }

    data class SimilarPhotos(val items: List<SimilarPhotoItem>) : CleaningSession {
        override val type = CleaningType.SIMILAR_PHOTOS
    }
}

/** Retained by the ViewModel while the system sheet recreates or covers the Activity. */
internal class MediaDeleteConsent {
    private var launched = false
    private var completed = false

    fun claimRequest(): Boolean {
        if (launched) return false
        launched = true
        return true
    }

    fun complete(): Boolean {
        if (!launched || completed) return false
        completed = true
        return true
    }
}
