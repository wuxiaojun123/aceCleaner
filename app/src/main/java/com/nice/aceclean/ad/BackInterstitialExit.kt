package com.nice.aceclean.ad

import kotlinx.coroutines.CancellationException

/** One exit request owns both the ad attempt and the final navigation. */
internal class BackInterstitialExit(
    private val showAd: suspend () -> Unit,
    private val finish: () -> Unit,
    private val onFailure: (Exception) -> Unit,
) {
    var requested = false
        private set

    suspend fun request() {
        if (requested) return
        requested = true
        try {
            showAd()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onFailure(error)
        }
        finish()
    }
}
