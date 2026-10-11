package com.chanakya.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chanakya.core.data.db.DatabaseProvider
import com.chanakya.core.data.model.ReportWindow
import com.chanakya.core.data.repository.ReportRepository
import java.util.concurrent.TimeUnit

/**
 * Runs the periodic maintenance pass and posts a protection summary.
 *
 * The same worker serves both cadences: the daily run also performs retention (rolling
 * old events into aggregates and pruning them), while the weekly run just reports over
 * a seven-day window.
 *
 * All work is local; nothing is uploaded anywhere.
 */
class ReportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val weekly = inputData.getBoolean(KEY_WEEKLY, false)
            val database = DatabaseProvider.get(applicationContext)
            val eventDao = database.blockedEventDao()

            // Retention runs once a day, not on every pass.
            if (!weekly) {
                val boundary = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
                eventDao.rollUpOldEvents(boundary)
                eventDao.pruneBefore(boundary)
            }

            val windowDays = if (weekly) WEEKLY_WINDOW_DAYS else DAILY_WINDOW_DAYS
            val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(windowDays)

            val reports = ReportRepository(eventDao)
            val blockedCount = reports.blockedSince(since)
            val appCount = reports.appsSince(since)
            val topPackage = reports.topAppSince(since)
            val topLabel = topPackage?.let { database.appMetaDao().find(it)?.label } ?: topPackage

            // Only notify when there is something to report.
            if (blockedCount > 0) {
                ChanakyaNotifications(applicationContext).showReport(
                    blockedCount = blockedCount,
                    appCount = appCount,
                    topApp = topLabel,
                    estimatedBytesSaved = blockedCount.toLong() *
                        ReportWindow.ESTIMATED_BYTES_PER_BLOCKED_REQUEST,
                    weekly = weekly,
                )
            }
            Result.success()
        } catch (exception: Exception) {
            // A transient failure should be retried rather than silently dropped.
            Result.retry()
        }
    }

    companion object {
        /** Input key selecting the weekly cadence. */
        const val KEY_WEEKLY = "weekly"

        /** Raw events older than this many days are rolled up and pruned. */
        private const val RETENTION_DAYS = 30L

        private const val DAILY_WINDOW_DAYS = 1L
        private const val WEEKLY_WINDOW_DAYS = 7L
    }
}
