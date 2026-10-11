package com.chanakya.core.data.model

/**
 * An app and how many trackers it contacted within a report window.
 */
data class AppCount(
    val app: AppInfo,
    val count: Int,
)

/**
 * An aggregated summary of protection activity over a window of time.
 *
 * @param blockedCount queries blocked in the window.
 * @param appCount distinct apps that contacted a tracker in the window.
 * @param topNetworks the busiest tracker networks, highest first.
 * @param topApps the busiest apps, highest first.
 */
data class ReportWindow(
    val blockedCount: Int,
    val appCount: Int,
    val topNetworks: List<NetworkCount>,
    val topApps: List<AppCount>,
) {
    /** Whether nothing was recorded in the window. */
    val isEmpty: Boolean get() = blockedCount == 0 && topApps.isEmpty()

    companion object {
        /** An empty window, used as a loading placeholder. */
        val EMPTY = ReportWindow(
            blockedCount = 0,
            appCount = 0,
            topNetworks = emptyList(),
            topApps = emptyList(),
        )
    }
}
