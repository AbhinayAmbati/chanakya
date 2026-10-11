package com.chanakya.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Periodically reminds the user that Android's advertising ID can be reset.
 *
 * Chanakya never reads the advertising ID itself; it only nudges the user towards the
 * system control that resets or deletes it.
 */
class AdIdReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            ChanakyaNotifications(applicationContext).showAdIdReminder()
            Result.success()
        } catch (exception: Exception) {
            Result.retry()
        }
    }
}
