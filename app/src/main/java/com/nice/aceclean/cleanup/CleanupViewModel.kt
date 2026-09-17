package com.nice.aceclean.cleanup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CleanupViewModel(application: Application) : AndroidViewModel(application) {
    var scanResult: CleanupScanResult? = null
        private set

    private val _scanProgress = MutableStateFlow(CleanupScanStats())
    val scanProgress: StateFlow<CleanupScanStats> = _scanProgress.asStateFlow()

    suspend fun scan(): CleanupScanResult {
        _scanProgress.value = CleanupScanStats()
        return CleanupRepository.scan(getApplication()) { progress ->
            _scanProgress.value = progress
        }.also { scanResult = it }
    }

    suspend fun delete(categories: Set<CleanupCategory>): CleanupDeleteResult {
        val selected = scanResult?.candidates?.filter { it.category in categories }.orEmpty()
        return CleanupRepository.delete(getApplication(), selected).also {
            scanResult = CleanupRepository.scan(getApplication())
        }
    }
}
