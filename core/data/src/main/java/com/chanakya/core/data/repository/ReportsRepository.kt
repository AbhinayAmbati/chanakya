package com.chanakya.core.data.repository

import com.chanakya.core.data.catalog.TrackerCatalog
import com.chanakya.core.data.db.dao.AppMetaDao
import com.chanakya.core.data.db.dao.BlockedEventDao
import com.chanakya.core.data.db.NetworkCountRow
import com.chanakya.core.data.db.dao.DailyCountRow
import com.chanakya.core.data.db.entity.toModel
import com.chanakya.core.data.model.AppCount
import com.chanakya.core.data.model.AppInfo
import com.chanakya.core.data.model.CategoryCount
import com.chanakya.core.data.model.DayCount
import com.chanakya.core.data.model.NetworkCount
import com.chanakya.core.data.model.ReportBreakdown
import com.chanakya.core.data.model.ReportWindow
import com.chanakya.core.data.model.TrackerCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Aggregated protection activity for the Reports screen.
 */
interface ReportsRepository {

    /** Activity since midnight today. */
    fun observeToday(): Flow<ReportWindow>

    /** Activity over the last seven days. */
    fun observeWeek(): Flow<ReportWindow>

    /** Activity since the app started recording. */
    fun observeAllTime(): Flow<ReportWindow>

    /**
     * Chart data for the last seven days: a daily trend and a split by kind of tracking.
     */
    fun observeBreakdown(): Flow<ReportBreakdown>
}

/**
 * The Room-backed implementation of [ReportsRepository].
 *
 * Each window is built from four aggregate queries plus the app metadata table, so the
 * database does the counting and the UI never has to load raw events.
 *
 * @param nowProvider supplies the current time; injectable so tests are deterministic.
 */
class RoomReportsRepository(
    private val eventDao: BlockedEventDao,
    private val appDao: AppMetaDao,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : ReportsRepository {

    override fun observeToday(): Flow<ReportWindow> = window(startOfToday())

    override fun observeWeek(): Flow<ReportWindow> = window(startOfToday() - SIX_DAYS_MILLIS)

    override fun observeAllTime(): Flow<ReportWindow> = window(0L)

    override fun observeBreakdown(): Flow<ReportBreakdown> = combine(
        eventDao.observeDailyCountsSince(startOfWeek()),
        eventDao.observeNetworkCountsSince(startOfWeek()),
    ) { daily, networks ->
        ReportBreakdown(
            daily = buildDailySeries(daily),
            byCategory = buildCategorySplit(networks),
        )
    }

    /**
     * Turn the sparse day rows into a fixed seven-day series.
     *
     * A day with no activity has no row at all, so the series is walked from the date
     * rather than from the result - otherwise the bars would slide left on quiet days.
     */
    private fun buildDailySeries(rows: List<DailyCountRow>): List<DayCount> {
        val counts = rows.associate { it.day to it.count }

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = startOfToday()
        calendar.add(Calendar.DAY_OF_YEAR, -(SERIES_DAYS - 1))

        return (0 until SERIES_DAYS).map {
            val key = DAY_KEY_FORMAT.format(calendar.time)
            val label = DAY_LABEL_FORMAT.format(calendar.time)
            val count = counts[key] ?: 0
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            DayCount(label = label, count = count)
        }
    }

    /**
     * Sum the per-network counts into the categories the catalogue assigns them.
     *
     * A network the catalogue does not know about still counts, under "Other" - a
     * blocked query is a blocked query, even if we cannot name who wanted it.
     */
    private fun buildCategorySplit(rows: List<NetworkCountRow>): List<CategoryCount> {
        val byId = TrackerCatalog.networks.associateBy { it.id }
        val totals = mutableMapOf<TrackerCategory, Int>()

        for (row in rows) {
            val category = byId[row.networkId]?.category ?: TrackerCategory.OTHER
            totals[category] = (totals[category] ?: 0) + row.count
        }

        return totals.entries
            .map { CategoryCount(category = it.key, count = it.value) }
            .sortedByDescending { it.count }
    }

    /** Build the aggregate flow for everything since [since]. */
    private fun window(since: Long): Flow<ReportWindow> = combine(
        eventDao.observeBlockedSince(since),
        eventDao.observeAppsSince(since),
        eventDao.observeTopNetworksSince(since, TOP_LIMIT),
        eventDao.observeTopAppsSince(since, TOP_LIMIT),
        appDao.observeAll(),
    ) { blockedCount, appCount, networks, apps, appMeta ->
        val labels = appMeta.associate { it.packageName to it.toModel() }

        ReportWindow(
            blockedCount = blockedCount,
            appCount = appCount,
            topNetworks = networks.mapNotNull { row ->
                TrackerCatalog.networks
                    .firstOrNull { it.id == row.networkId }
                    ?.let { NetworkCount(it, row.count) }
            },
            topApps = apps.map { row ->
                AppCount(
                    app = labels[row.packageName]
                        ?: AppInfo(uid = -1, packageName = row.packageName, label = row.packageName),
                    count = row.count,
                )
            },
        )
    }

    /** Epoch millis at the start of the seven-day window. */
    private fun startOfWeek(): Long = startOfToday() - SIX_DAYS_MILLIS

    /** Epoch millis at 00:00 local time today. */
    private fun startOfToday(): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowProvider()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private companion object {
        const val TOP_LIMIT = 5
        const val SIX_DAYS_MILLIS = 6L * 24 * 60 * 60 * 1000

        /** Days shown in the trend chart, including today. */
        const val SERIES_DAYS = 7

        /** Matches the format the daily-counts query emits. */
        val DAY_KEY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        /** The short weekday shown under each bar. */
        val DAY_LABEL_FORMAT = SimpleDateFormat("EEE", Locale.US)
    }
}
