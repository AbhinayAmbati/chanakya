package com.chanakya.app.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.room.Room
import com.chanakya.core.data.db.ChanakyaDatabase
import com.chanakya.core.data.prefs.SettingsRepository
import com.chanakya.core.data.repository.RoomTrackerRepository
import com.chanakya.core.data.repository.TrackerRepository
import com.chanakya.core.vpn.service.VpnConnectionManager

/**
 * The manual dependency-injection container.
 *
 * Everything the app needs is built here, once, and handed to ViewModels. This is a
 * deliberate alternative to a DI framework: with a graph this small, explicit wiring
 * is easier to read and to reason about than generated code.
 *
 * @param context the application context.
 */
class AppContainer(private val context: Context) {

    /** The Room database. */
    private val database: ChanakyaDatabase by lazy {
        Room.databaseBuilder(context, ChanakyaDatabase::class.java, ChanakyaDatabase.NAME)
            .build()
    }

    /** Persisted user preferences. */
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }

    /** Read model for tracking activity. */
    val trackerRepository: TrackerRepository by lazy {
        RoomTrackerRepository(database.blockedEventDao(), database.appMetaDao())
    }

    /** Controls the local VPN. */
    val vpnConnectionManager: VpnConnectionManager by lazy { VpnConnectionManager(context) }
}

/**
 * Exposes the [AppContainer] to the composition without threading it through every
 * composable as a parameter.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided. Wrap the UI in CompositionLocalProvider(LocalAppContainer provides ...).")
}
