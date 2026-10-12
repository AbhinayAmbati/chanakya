package com.chanakya.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.chanakya.core.data.db.AppCountRow
import com.chanakya.core.data.db.NetworkCountRow
import com.chanakya.core.data.db.entity.BlockedEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access for tracking events, their rolled-up aggregates and retention.
 *
 * The report queries deliberately read from **both** the raw table and the aggregate
 * table: raw events are pruned after a retention window, and the aggregates carry the
 * history that would otherwise be lost, so a lifetime total stays correct forever.
 */
@Dao
interface BlockedEventDao {

    /** Inserts a single event and returns its generated row id. */
    @Insert
    suspend fun insert(event: BlockedEventEntity): Long

    /** Inserts a batch of events in one transaction. */
    @Insert
    suspend fun insertAll(events: List<BlockedEventEntity>)

    /** The most recent events across all apps. */
    @Query("SELECT * FROM blocked_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BlockedEventEntity>>

    /** All events for one app, most recent first. */
    @Query("SELECT * FROM blocked_events WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun observeForApp(packageName: String): Flow<List<BlockedEventEntity>>

    /** Every raw event, used to build the per-app summaries. */
    @Query("SELECT * FROM blocked_events ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<BlockedEventEntity>>

    // ---- Report aggregates -------------------------------------------------

    /** Blocked count since the given time, across raw events and aggregates. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM blocked_events WHERE blocked = 1 AND timestamp >= :since) " +
            "+ (SELECT COALESCE(SUM(blockedCount), 0) FROM daily_aggregates WHERE dayStart >= :since)",
    )
    fun observeBlockedSince(since: Long): Flow<Int>

    /** Blocked count of all time, across raw events and aggregates. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM blocked_events WHERE blocked = 1) " +
            "+ (SELECT COALESCE(SUM(blockedCount), 0) FROM daily_aggregates)",
    )
    fun observeBlockedTotal(): Flow<Int>

    /** Distinct apps that contacted a tracker since the given time. */
    @Query(
        "SELECT COUNT(DISTINCT packageName) FROM (" +
            "SELECT packageName FROM blocked_events WHERE blocked = 1 AND timestamp >= :since " +
            "UNION " +
            "SELECT packageName FROM daily_aggregates WHERE dayStart >= :since)",
    )
    fun observeAppsSince(since: Long): Flow<Int>

    /** The busiest tracker networks since the given time. */
    @Query(
        "SELECT networkId, SUM(entryCount) AS count FROM (" +
            "SELECT networkId, COUNT(*) AS entryCount FROM blocked_events " +
            "WHERE blocked = 1 AND timestamp >= :since AND networkId IS NOT NULL GROUP BY networkId " +
            "UNION ALL " +
            "SELECT networkId, SUM(blockedCount) AS entryCount FROM daily_aggregates " +
            "WHERE dayStart >= :since AND networkId != '' GROUP BY networkId" +
            ") GROUP BY networkId ORDER BY count DESC LIMIT :limit",
    )
    fun observeTopNetworksSince(since: Long, limit: Int): Flow<List<NetworkCountRow>>

    /**
     * Blocked counts per network since the given time, with no limit.
     *
     * The top-networks query caps its result because it is for display. The category
     * split needs every network, because the categories are summed from them and a
     * truncated list would silently under-count.
     */
    @Query(
        "SELECT networkId, SUM(entryCount) AS count FROM (" +
            "SELECT networkId, COUNT(*) AS entryCount FROM blocked_events " +
            "WHERE blocked = 1 AND timestamp >= :since AND networkId IS NOT NULL GROUP BY networkId " +
            "UNION ALL " +
            "SELECT networkId, SUM(blockedCount) AS entryCount FROM daily_aggregates " +
            "WHERE dayStart >= :since AND networkId != '' GROUP BY networkId" +
            ") GROUP BY networkId ORDER BY count DESC",
    )
    fun observeNetworkCountsSince(since: Long): Flow<List<NetworkCountRow>>

    /** The busiest apps since the given time. */
    @Query(
        "SELECT packageName, SUM(entryCount) AS count FROM (" +
            "SELECT packageName, COUNT(*) AS entryCount FROM blocked_events " +
            "WHERE blocked = 1 AND timestamp >= :since GROUP BY packageName " +
            "UNION ALL " +
            "SELECT packageName, SUM(blockedCount) AS entryCount FROM daily_aggregates " +
            "WHERE dayStart >= :since GROUP BY packageName" +
            ") GROUP BY packageName ORDER BY count DESC LIMIT :limit",
    )
    fun observeTopAppsSince(since: Long, limit: Int): Flow<List<AppCountRow>>

    // ---- Retention ---------------------------------------------------------

    /**
     * Roll every blocked event older than [boundary] into the daily aggregate table.
     *
     * Idempotent: running it again replaces the rows for the same day/app/network, so
     * it is safe to call on every retention pass.
     */
    @Query(
        "INSERT OR REPLACE INTO daily_aggregates (dayStart, packageName, networkId, blockedCount) " +
            "SELECT (timestamp / 86400000) * 86400000, packageName, IFNULL(networkId, ''), COUNT(*) " +
            "FROM blocked_events WHERE blocked = 1 AND timestamp < :boundary " +
            "GROUP BY (timestamp / 86400000) * 86400000, packageName, IFNULL(networkId, '')",
    )
    suspend fun rollUpOldEvents(boundary: Long)

    /** Deletes raw events older than the given time. */
    @Query("DELETE FROM blocked_events WHERE timestamp < :before")
    suspend fun pruneBefore(before: Long)

    // ---- One-shot queries for the report worker ---------------------------

    /** Blocked count since the given time (raw events only; within retention). */
    @Query("SELECT COUNT(*) FROM blocked_events WHERE blocked = 1 AND timestamp >= :since")
    suspend fun blockedSince(since: Long): Int

    /** Distinct apps since the given time (raw events only). */
    @Query("SELECT COUNT(DISTINCT packageName) FROM blocked_events WHERE blocked = 1 AND timestamp >= :since")
    suspend fun appsSince(since: Long): Int

    /** The busiest app since the given time (raw events only). */
    @Query(
        "SELECT packageName FROM blocked_events WHERE blocked = 1 AND timestamp >= :since " +
            "GROUP BY packageName ORDER BY COUNT(*) DESC LIMIT 1",
    )
    suspend fun topAppSince(since: Long): String?
}
