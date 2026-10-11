package com.chanakya.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chanakya.core.data.db.DatabaseProvider
import com.chanakya.core.data.repository.ReportRepository
import java.util.concurrent.TimeUnit

/**
 * Computes the last 24 hours of protection activity and posts it as a notification.
 *
 * All work is local: the summary is derived from the on-device database and nothing is
 * uploaded anywhere.
 */
class ReportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val database = DatabaseProvider.get(applicationContext)
            val reports = ReportRepository(database.blockedEventDao())

            val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
            val blockedCount = reports.blockedSince(since)
            val appCount = reports.appsSince(since)
            val topPackage = reports.topAppSince(since)
            val topLabel = topPackage?.let { database.appMetaDao().find(it)?.label } ?: topPackage

            ChanakyaNotifications(applicationContext).showReport(blockedCount, appCount, topLabel)
            Result.success()
        } catch (exception: Exception) {
            // A transient failure should be retried rather than silently dropped.
            Result.retry()
        }
    }
}
