package com.chanakya.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chanakya.core.data.db.DatabaseProvider
import com.chanakya.core.data.repository.ReportRepository
import java.util.concurrent.TimeUnit

/**
 * Runs the daily maintenance pass and posts the protection summary.
 *
 * Maintenance first: blocked events older than the retention window are rolled into the
 * daily aggregates and then pruned, so the raw table stays bounded while lifetime totals
 * remain accurate. The summary is computed afterwards.
 *
 * All work is local; nothing is uploaded anywhere.
 */
class ReportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val database = DatabaseProvider.get(applicationContext)
            val eventDao = database.blockedEventDao()

            val boundary = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
            eventDao.rollUpOldEvents(boundary)
            eventDao.pruneBefore(boundary)

            val reports = ReportRepository(eventDao)
            val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
            val blockedCount = reports.blockedSince(since)
            val appCount = reports.appsSince(since)
            val topPackage = reports.topAppSince(since)
            val topLabel = topPackage?.let { database.appMetaDao().find(it)?.label } ?: topPackage

            // Only notify when there is something to report.
            if (blockedCount > 0) {
                ChanakyaNotifications(applicationContext).showReport(blockedCount, appCount, topLabel)
            }
            Result.success()
        } catch (exception: Exception) {
            // A transient failure should be retried rather than silently dropped.
            Result.retry()
        }
    }

    private companion object {
        /** Raw events older than this many days are rolled up and pruned. */
        const val RETENTION_DAYS = 30L
    }
}
