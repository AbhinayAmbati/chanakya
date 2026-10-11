package com.chanakya.core.data.model

/**
 * A device-wide snapshot of protection activity for the Home screen.
 *
 * @param blockedToday queries blocked since midnight.
 * @param blockedTotal queries blocked since the app started tracking.
 * @param appsTrackedToday number of distinct apps that contacted a tracker today.
 * @param topNetwork the network contacted most often today, if any.
 */
data class ProtectionSummary(
    val blockedToday: Int,
    val blockedTotal: Int,
    val appsTrackedToday: Int,
    val topNetwork: TrackerNetwork?,
)
