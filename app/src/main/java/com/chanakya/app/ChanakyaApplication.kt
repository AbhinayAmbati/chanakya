package com.chanakya.app

import android.app.Application
import com.chanakya.app.di.AppContainer
import com.chanakya.app.work.ReportScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The application entry point. Creates the single [AppContainer] that wires the whole
 * dependency graph together, schedules the daily protection summary, and - in debug
 * builds only - seeds demonstration data.
 */
class ChanakyaApplication : Application() {

    /** The manual dependency container for the whole app. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // The daily summary is idempotent: re-scheduling never duplicates the job.
        ReportScheduler(this).scheduleDailyReport()

        if (BuildConfig.DEBUG) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                container.trackerRepository.seedDemoData()
            }
        }
    }
}
