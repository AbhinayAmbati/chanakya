package com.chanakya.app.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Small formatting helpers for timestamps shown in the UI.
 */
object TimeFormat {

    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    /** Format a timestamp as a clock time, e.g. "14:05". */
    fun clockTime(epochMillis: Long): String = timeFormatter.format(Date(epochMillis))

    /**
     * Express a timestamp as a short relative phrase, e.g. "just now", "12m ago",
     * "3h ago", "2d ago".
     */
    fun relative(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val elapsed = (now - epochMillis).coerceAtLeast(0)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
        val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
        val days = TimeUnit.MILLISECONDS.toDays(elapsed)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            else -> "${days}d ago"
        }
    }
}
