package com.nice.library_push.display

import android.app.Activity
import android.os.Bundle
import com.nice.library_push.api.PushClick
import com.nice.library_push.api.PushLibrary

class PushClickActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PushLibrary.handleClick(
            this,
            PushClick(
                scene = intent.getStringExtra(EXTRA_SCENE).orEmpty(),
                tempId = intent.getIntExtra(EXTRA_TEMP_ID, 0),
                contentId = intent.getIntExtra(EXTRA_CONTENT_ID, 0),
                action = intent.getStringExtra(EXTRA_ACTION).orEmpty(),
                target = intent.getStringExtra(EXTRA_TARGET).orEmpty(),
                type = intent.getStringExtra(EXTRA_TYPE) ?: "default",
                autoCancel = intent.getBooleanExtra(EXTRA_AUTO_CANCEL, true),
            ),
            intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0),
        )
        finish()
    }

    companion object {
        const val EXTRA_SCENE = "nice_push_scene"
        const val EXTRA_TEMP_ID = "nice_push_temp_id"
        const val EXTRA_CONTENT_ID = "nice_push_content_id"
        const val EXTRA_ACTION = "nice_push_action"
        const val EXTRA_TARGET = "nice_push_target"
        const val EXTRA_NOTIFICATION_ID = "nice_push_notification_id"
        const val EXTRA_TYPE = "nice_push_type"
        const val EXTRA_AUTO_CANCEL = "nice_push_auto_cancel"
    }
}
