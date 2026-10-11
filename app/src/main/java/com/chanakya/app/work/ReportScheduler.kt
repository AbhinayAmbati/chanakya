package com.chanakya.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules the app's periodic background work.
 *
 * Work is registered with unique names so re-scheduling is idempotent - a second call
 * never produces a duplicate job.
 */
class ReportScheduler(private val context: Context) {

    /** Schedule the daily protection summary, first firing at around 09:00 local time. */
    fun scheduleDailyReport() {
        val request = PeriodicWorkRequestBuilder<ReportWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntilHour(DAILY_REPORT_HOUR), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder().setRequiresBatteryNotLow(true).build(),
            )
            .addTag(DAILY_REPORT_WORK)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_REPORT_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Turn the monthly advertising-ID reminder on or off. */
    fun setAdIdReminder(enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(AD_ID_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<AdIdReminderWorker>(30, TimeUnit.DAYS)
            .addTag(AD_ID_WORK)
            .build()
        workManager.enqueueUniquePeriodicWork(
            AD_ID_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** Milliseconds from now until the next occurrence of [hourOfDay]. */
    private fun millisUntilHour(hourOfDay: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }

    private companion object {
        const val DAILY_REPORT_WORK = "chanakya_daily_report"
        const val AD_ID_WORK = "chanakya_ad_id_reminder"
        const val DAILY_REPORT_HOUR = 9
    }
}
