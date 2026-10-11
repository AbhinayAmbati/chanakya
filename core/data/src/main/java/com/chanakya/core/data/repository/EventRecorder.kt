package com.chanakya.core.data.repository

import com.chanakya.core.data.catalog.TrackerCatalog
import com.chanakya.core.data.db.dao.AppMetaDao
import com.chanakya.core.data.db.dao.BlockedEventDao
import com.chanakya.core.data.db.entity.BlockedEventEntity
import com.chanakya.core.data.db.entity.toEntity
import com.chanakya.core.data.model.AppInfo

/**
 * Writes observed tracking activity into the database.
 *
 * This is the write-side counterpart to [TrackerRepository] (which is read-only). The
 * VPN service uses it to persist each DNS observation, so the packet loop itself never
 * touches Room - keeping the hot path free of database work.
 */
class EventRecorder(
    private val eventDao: BlockedEventDao,
    private val appDao: AppMetaDao,
) {

    /**
     * Persist a single observed query.
     *
     * The tracker network is resolved here (not by the caller) so that every stored
     * event carries the attribution the UI needs to group and colour it.
     *
     * @param domain the queried domain.
     * @param packageName the app that made the query, or "unknown" when unresolved.
     * @param uid the owning app's uid, or -1 when unresolved.
     * @param blocked whether the query was blocked.
     * @param timestamp epoch millis the query was seen.
     */
    suspend fun record(
        domain: String,
        packageName: String,
        uid: Int,
        blocked: Boolean,
        timestamp: Long,
    ) {
        val networkId = TrackerCatalog.networkForDomain(domain)?.id
        eventDao.insert(
            BlockedEventEntity(
                timestamp = timestamp,
                uid = uid,
                packageName = packageName,
                domain = domain,
                networkId = networkId,
                blocked = blocked,
            ),
        )
    }

    /**
     * Insert or refresh the cached list of installed applications, so that historical
     * events can still be labelled with a human-readable app name.
     */
    suspend fun upsertApps(apps: List<AppInfo>) {
        if (apps.isEmpty()) return
        appDao.upsertAll(apps.map { it.toEntity() })
    }
}
