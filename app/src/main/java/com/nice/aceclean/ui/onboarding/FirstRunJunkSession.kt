package com.nice.aceclean.ui.onboarding

/** In-memory scan handoff. A missing session always sends result/cleaning pages back to scanning. */
internal object FirstRunJunkSession {
    var result: FirstRunJunkResult? = null
}
