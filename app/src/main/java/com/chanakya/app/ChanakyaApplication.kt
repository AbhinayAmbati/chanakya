package com.chanakya.app

import android.app.Application
import com.chanakya.app.di.AppContainer
import com.chanakya.app.work.ReportScheduler

/**
 * The application entry point. Creates the single [AppContainer] that wires the whole
 * dependency graph together and schedules the daily protection summary.
 *
 * Nothing is seeded or synthesised here: every figure the app displays comes from real
 * activity recorded by the VPN service.
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
    }
}
