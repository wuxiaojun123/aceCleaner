package com.nice.library_push.triggers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.nice.library_push.api.PushLibrary

class PushTimerWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        PushLibrary.timer()
        return Result.success()
    }

    companion object { const val WORK_NAME = "nice_push_timer" }
}
