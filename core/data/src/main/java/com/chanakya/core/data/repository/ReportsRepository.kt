package com.chanakya.core.data.repository

import com.chanakya.core.data.catalog.TrackerCatalog
import com.chanakya.core.data.db.dao.AppMetaDao
import com.chanakya.core.data.db.dao.BlockedEventDao
import com.chanakya.core.data.db.entity.toModel
import com.chanakya.core.data.model.AppCount
import com.chanakya.core.data.model.AppInfo
import com.chanakya.core.data.model.NetworkCount
import com.chanakya.core.data.model.ReportWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar

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
    }
}
