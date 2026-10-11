package com.chanakya.app.ui.common

import java.util.Locale

/**
 * Formats a byte count for display, e.g. `12.4 MB`.
 *
 * Kept intentionally small: the app only ever shows an estimate, so precision beyond
 * one decimal place would imply accuracy the number does not have.
 */
object ByteFormat {

    /** Render [bytes] as a short human-readable string. */
    fun human(bytes: Long): String = when {
        bytes < KILOBYTE -> "$bytes B"
        bytes < MEGABYTE -> format(bytes / KILOBYTE.toDouble(), "%.1f KB")
        bytes < GIGABYTE -> format(bytes / MEGABYTE.toDouble(), "%.1f MB")
        else -> format(bytes / GIGABYTE.toDouble(), "%.2f GB")
    }

    private fun format(value: Double, pattern: String): String =
        String.format(Locale.getDefault(), pattern, value)

    private const val KILOBYTE = 1024L
    private const val MEGABYTE = KILOBYTE * 1024
    private const val GIGABYTE = MEGABYTE * 1024
}
