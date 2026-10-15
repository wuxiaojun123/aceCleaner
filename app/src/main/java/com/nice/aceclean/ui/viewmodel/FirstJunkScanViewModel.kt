package com.nice.aceclean.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nice.aceclean.ui.onboarding.FirstRunJunkResult
import com.nice.aceclean.ui.onboarding.FirstRunJunkScanner
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class FirstJunkScanViewModel(application: Application) : AndroidViewModel(application) {
    private val scannedItems = MutableStateFlow(0)
    val progress = scannedItems.asStateFlow()
    private var scan: Deferred<FirstRunJunkResult>? = null

    fun scan(): Deferred<FirstRunJunkResult> = scan ?: viewModelScope.async {
        FirstRunJunkScanner.scan(getApplication()) { scannedItems.value = it }
    }.also { scan = it }
}
