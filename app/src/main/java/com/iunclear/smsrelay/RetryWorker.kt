package com.iunclear.smsrelay

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class RetryWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val messageId = inputData.getString(MESSAGE_ID) ?: return Result.failure()
        return if (MessageRepository(applicationContext).retry(messageId, runAttemptCount)) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        const val MESSAGE_ID = "message_id"
    }
}
