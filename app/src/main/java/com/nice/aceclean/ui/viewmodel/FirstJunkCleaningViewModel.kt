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

/** Retains the one deletion job across Activity recreation instead of deleting the selection twice. */
internal class FirstJunkCleaningViewModel(application: Application) : AndroidViewModel(application) {
    private val cleaningProgress = MutableStateFlow(0L)
    val progress = cleaningProgress.asStateFlow()
    private var cleaning: Deferred<Long>? = null

    fun clean(result: FirstRunJunkResult): Deferred<Long> = cleaning ?: viewModelScope.async {
        FirstRunJunkScanner.cleanSelected(getApplication(), result) { cleaningProgress.value = it }
    }.also { cleaning = it }
}
