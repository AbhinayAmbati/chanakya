package com.chanakya.core.data.db.entity

import androidx.room.Entity

/**
 * A rolled-up count of blocked queries for one day, one app and one tracker network.
 *
 * Raw events are pruned after a retention window to keep the database bounded, so these
 * aggregates are what let the lifetime and long-window totals stay accurate. The grain
 * is (day, app, network) because that is the finest detail the Reports screen needs.
 *
 * @param dayStart the UTC day the counts belong to, in epoch millis at midnight.
 * @param networkId the tracker network id, or an empty string when it was unknown.
 */
@Entity(
    tableName = "daily_aggregates",
    primaryKeys = ["dayStart", "packageName", "networkId"],
)
data class DailyAggregateEntity(
    val dayStart: Long,
    val packageName: String,
    val networkId: String,
    val blockedCount: Int,
)
