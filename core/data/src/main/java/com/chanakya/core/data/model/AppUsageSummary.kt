package com.chanakya.core.data.model

/**
 * How many times one app contacted one network. Used inside [AppUsageSummary].
 */
data class NetworkCount(
    val network: TrackerNetwork,
    val count: Int,
)

/**
 * An aggregated view of one app's tracking activity, used by the Apps list and the
 * Home screen. Built by combining raw events with app metadata.
 *
 * @param app the application this summary describes.
 * @param blockedCount total queries from this app that were blocked.
 * @param allowedCount total queries from this app that were allowed.
 * @param lastSeen epoch millis of the most recent event.
 * @param networks per-network breakdown, highest count first.
 */
data class AppUsageSummary(
    val app: AppInfo,
    val blockedCount: Int,
    val allowedCount: Int,
    val lastSeen: Long,
    val networks: List<NetworkCount>,
) {
    /** Total queries observed from this app. */
    val totalCount: Int get() = blockedCount + allowedCount
}
