package com.chanakya.core.data.repository

import com.chanakya.core.data.model.AppInfo
import com.chanakya.core.data.model.AppUsageSummary
import com.chanakya.core.data.model.BlockedEvent
import com.chanakya.core.data.model.NetworkCount
import com.chanakya.core.data.model.ProtectionSummary
import kotlinx.coroutines.flow.Flow

/**
 * Read model for tracking activity. The UI depends only on this interface, which
 * keeps ViewModels testable and lets the implementation evolve freely.
 */
interface TrackerRepository {

    /** A device-wide snapshot for the Home screen. */
    fun observeProtectionSummary(): Flow<ProtectionSummary>

    /** Per-app summaries for the Apps list, most active first. */
    fun observeAppSummaries(): Flow<List<AppUsageSummary>>

    /** Everything known about one app for the detail screen. */
    fun observeAppDetail(packageName: String): Flow<AppDetail>

    /** The most recent raw events, for live views. */
    fun observeRecentEvents(limit: Int = 100): Flow<List<BlockedEvent>>

    /** Seeds demonstration data. Only called from debug builds. */
    suspend fun seedDemoData()
}

/**
 * The full detail payload for a single app's screen.
 *
 * @param app the application.
 * @param blockedCount queries blocked for this app.
 * @param allowedCount queries allowed for this app.
 * @param networks per-network breakdown, highest first.
 * @param events the raw events, most recent first.
 */
data class AppDetail(
    val app: AppInfo,
    val blockedCount: Int,
    val allowedCount: Int,
    val networks: List<NetworkCount>,
    val events: List<BlockedEvent>,
)
