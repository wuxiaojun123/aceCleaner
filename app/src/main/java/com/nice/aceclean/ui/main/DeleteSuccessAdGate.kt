package com.nice.aceclean.ui.main

/** One entry attempt and, only without an impression, one serialized back attempt per visit. */
internal class DeleteSuccessAdGate(
    var entryAttempted: Boolean = false,
    var shown: Boolean = false,
    var backAttempted: Boolean = false,
    var backRequested: Boolean = false,
) {
    enum class Action { NONE, SHOW_ENTRY, SHOW_BACK, RETURN_HOME }
    var running = false
        private set
    var finished = false
        private set

    fun onEnter(): Action {
        if (backRequested) return onBack()
        if (entryAttempted || finished) return Action.NONE
        entryAttempted = true
        running = true
        return Action.SHOW_ENTRY
    }

    fun onShown() { shown = true }

    fun onBack(): Action {
        if (finished) return Action.NONE
        backRequested = true
        return if (running) Action.NONE else decideBack()
    }

    fun onAttemptFinished(): Action {
        running = false
        return if (backRequested && !finished) decideBack() else Action.NONE
    }

    fun leaveForFeature() { finished = true }

    private fun decideBack(): Action {
        if (shown || backAttempted) {
            finished = true
            return Action.RETURN_HOME
        }
        backAttempted = true
        running = true
        return Action.SHOW_BACK
    }
}
