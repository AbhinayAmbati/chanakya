package com.chanakya.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chanakya.core.data.model.BlockedEvent

/**
 * Room representation of a [BlockedEvent]. Indexed on the columns the UI filters and
 * sorts by so the dashboard stays responsive as the table grows.
 */
@Entity(
    tableName = "blocked_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["packageName"]),
    ],
)
data class BlockedEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val uid: Int,
    val packageName: String,
    val domain: String,
    val networkId: String?,
    val blocked: Boolean,
)

/** Maps this entity to its domain model. */
fun BlockedEventEntity.toModel(): BlockedEvent = BlockedEvent(
    id = id,
    timestamp = timestamp,
    uid = uid,
    packageName = packageName,
    domain = domain,
    networkId = networkId,
    blocked = blocked,
)

/** Maps a domain model back to its entity for persistence. */
fun BlockedEvent.toEntity(): BlockedEventEntity = BlockedEventEntity(
    id = id,
    timestamp = timestamp,
    uid = uid,
    packageName = packageName,
    domain = domain,
    networkId = networkId,
    blocked = blocked,
)
