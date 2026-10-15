package com.nice.aceclean.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

internal class FirstJunkCompleteViewModel(private val state: SavedStateHandle) : ViewModel() {
    var notificationPromptShown: Boolean
        get() = state["notification_prompt_shown"] ?: false
        set(value) { state["notification_prompt_shown"] = value }
}
