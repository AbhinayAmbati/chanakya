package com.chanakya.core.data.repository

import com.chanakya.core.data.catalog.TrackerCatalog
import com.chanakya.core.data.db.dao.AppMetaDao
import com.chanakya.core.data.db.dao.BlockedEventDao
import com.chanakya.core.data.db.entity.toEntity
import com.chanakya.core.data.db.entity.toModel
import com.chanakya.core.data.model.AppInfo
import com.chanakya.core.data.model.AppUsageSummary
import com.chanakya.core.data.model.BlockedEvent
import com.chanakya.core.data.model.NetworkCount
import com.chanakya.core.data.model.ProtectionSummary
import com.chanakya.core.data.model.TrackerNetwork
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Calendar

/**
 * The Room-backed implementation of [TrackerRepository].
 *
 * Aggregation is done in memory over the event stream. This is deliberate for the
 * current data volumes; if the table grows large, the group-by queries should move
 * into SQL. Keeping the aggregation here means the DAO stays simple and portable.
 *
 * @param eventDao access to raw events.
 * @param appDao access to cached app metadata.
 * @param nowProvider supplies the current time; injectable so tests are deterministic.
 */
class RoomTrackerRepository(
    private val eventDao: BlockedEventDao,
    private val appDao: AppMetaDao,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : TrackerRepository {

    override fun observeProtectionSummary(): Flow<ProtectionSummary> = combine(
        eventDao.observeAll(),
        eventDao.observeBlockedTotal(),
    ) { events, total ->
        val startOfDay = startOfToday()
        val todaysEvents = events.filter { it.timestamp >= startOfDay }
        val blockedToday = todaysEvents.count { it.blocked }
        val appsToday = todaysEvents.map { it.packageName }.distinct().size

        // The network contacted most often today, if any.
        val topNetworkId = todaysEvents
            .mapNotNull { it.networkId }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val topNetwork: TrackerNetwork? = TrackerCatalog.networks.firstOrNull { it.id == topNetworkId }

        ProtectionSummary(
            blockedToday = blockedToday,
            blockedTotal = total,
            appsTrackedToday = appsToday,
            topNetwork = topNetwork,
        )
    }

    override fun observeAppSummaries(): Flow<List<AppUsageSummary>> = combine(
        eventDao.observeAll(),
        appDao.observeAll(),
    ) { events, apps ->
        val labelByPackage = apps.associate { it.packageName to it.toModel() }

        events.groupBy { it.packageName }
            .map { (packageName, appEvents) ->
                val app = labelByPackage[packageName]
                    ?: AppInfo(
                        uid = appEvents.first().uid,
                        packageName = packageName,
                        label = packageName,
                    )
                appEvents.toSummary(app)
            }
            .sortedByDescending { it.blockedCount }
    }

    override fun observeAppDetail(packageName: String): Flow<AppDetail> = combine(
        eventDao.observeForApp(packageName),
        appDao.observeAll(),
    ) { events, apps ->
        val app = apps.firstOrNull { it.packageName == packageName }?.toModel()
            ?: AppInfo(uid = events.firstOrNull()?.uid ?: -1, packageName = packageName, label = packageName)

        AppDetail(
            app = app,
            blockedCount = events.count { it.blocked },
            allowedCount = events.count { !it.blocked },
            networks = events.toNetworkCounts(),
            events = events.map { it.toModel() },
        )
    }

    override fun observeRecentEvents(limit: Int): Flow<List<BlockedEvent>> =
        eventDao.observeRecent(limit).map { list -> list.map { it.toModel() } }

    override suspend fun seedDemoData() {
        // Seed only once so repeated debug launches do not multiply the data.
        if (eventDao.count() > 0) return
        appDao.upsertAll(com.chanakya.core.data.sample.SampleData.apps.map { it.toEntity() })
        eventDao.insertAll(com.chanakya.core.data.sample.SampleData.events().map { it.toEntity() })
    }

    // ---- Helpers ----------------------------------------------------------

    /** Build a per-app summary from its raw events. */
    private fun List<com.chanakya.core.data.db.entity.BlockedEventEntity>.toSummary(
        app: AppInfo,
    ): AppUsageSummary = AppUsageSummary(
        app = app,
        blockedCount = count { it.blocked },
        allowedCount = count { !it.blocked },
        lastSeen = maxOfOrNull { it.timestamp } ?: 0L,
        networks = toNetworkCounts(),
    )

    /** Group events into per-network counts, highest first. */
    private fun List<com.chanakya.core.data.db.entity.BlockedEventEntity>.toNetworkCounts(): List<NetworkCount> {
        val byNetwork = mutableMapOf<TrackerNetwork, Int>()
        forEach { event ->
            val network = event.networkId
                ?.let { id -> TrackerCatalog.networks.firstOrNull { it.id == id } }
                ?: TrackerCatalog.networkForDomain(event.domain)
                ?: return@forEach
            byNetwork[network] = (byNetwork[network] ?: 0) + 1
        }
        return byNetwork.map { (network, count) -> NetworkCount(network, count) }
            .sortedByDescending { it.count }
    }

    /** Epoch millis at 00:00 local time today. */
    private fun startOfToday(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
