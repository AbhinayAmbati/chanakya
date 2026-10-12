package com.chanakya.core.data.db.dao

/**
 * Blocked count for one local day, as returned by the daily-series query.
 *
 * @param day the day in `YYYY-MM-DD` form, in the device's own timezone.
 * @param count how many queries were blocked that day.
 */
data class DailyCountRow(
    val day: String,
    val count: Int,
)
