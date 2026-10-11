package com.chanakya.app

import android.app.Application
import com.chanakya.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The application entry point. Creates the single [AppContainer] that wires the whole
 * dependency graph together, and - in debug builds only - seeds demonstration data so
 * the dashboard has something to show before the VPN engine is exercised.
 */
class ChanakyaApplication : Application() {

    /** The manual dependency container for the whole app. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        if (BuildConfig.DEBUG) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                container.trackerRepository.seedDemoData()
            }
        }
    }
}
