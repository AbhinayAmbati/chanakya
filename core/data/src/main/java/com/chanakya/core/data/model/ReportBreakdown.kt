package com.chanakya.core.data.model

/**
 * One day's blocked count, for the trend chart.
 *
 * @param label a short day label, e.g. "Mon".
 * @param count how many queries were blocked that day.
 */
data class DayCount(
    val label: String,
    val count: Int,
)

/**
 * How much of one kind of tracking was blocked, for the split chart.
 *
 * @param category the kind of tracking.
 * @param count how many queries of that kind were blocked.
 */
data class CategoryCount(
    val category: TrackerCategory,
    val count: Int,
)

/**
 * The chart data behind the Reports screen: a daily trend and a split by kind of
 * tracking. Kept separate from [ReportWindow] because it is a different shape of data -
 * a series and a breakdown, rather than a handful of totals.
 */
data class ReportBreakdown(
    val daily: List<DayCount>,
    val byCategory: List<CategoryCount>,
) {
    /** Total across the category split, used for the donut's centre. */
    val total: Int get() = byCategory.sumOf { it.count }

    /** Whether there is anything worth drawing. */
    val isEmpty: Boolean get() = total == 0 && daily.all { it.count == 0 }

    companion object {
        /** An empty breakdown, used as a loading placeholder. */
        val EMPTY = ReportBreakdown(daily = emptyList(), byCategory = emptyList())
    }
}
