package com.chanakya.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.chanakya.core.data.db.entity.BlockedEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access for tracking events. All read methods return [Flow]s so the UI updates
 * automatically as new events arrive.
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

    /** Every event, used to build aggregate summaries. */
    @Query("SELECT * FROM blocked_events ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<BlockedEventEntity>>

    /** Total blocked count since the given time. */
    @Query("SELECT COUNT(*) FROM blocked_events WHERE blocked = 1 AND timestamp >= :since")
    fun observeBlockedSince(since: Long): Flow<Int>

    /** Total blocked count of all time. */
    @Query("SELECT COUNT(*) FROM blocked_events WHERE blocked = 1")
    fun observeBlockedTotal(): Flow<Int>

    /** Deletes events older than the given time; used for retention. */
    @Query("DELETE FROM blocked_events WHERE timestamp < :before")
    suspend fun pruneBefore(before: Long)

    /** Total number of stored events. Used to decide whether to seed demo data. */
    @Query("SELECT COUNT(*) FROM blocked_events")
    suspend fun count(): Int
}
