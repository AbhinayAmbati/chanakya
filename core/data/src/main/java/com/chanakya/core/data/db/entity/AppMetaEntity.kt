package com.chanakya.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chanakya.core.data.model.AppInfo

/**
 * Room representation of an installed app. Kept in the database so that historical
 * events can still be labelled after an app is uninstalled.
 */
@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val uid: Int,
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

/** Maps this entity to its domain model. */
fun AppMetaEntity.toModel(): AppInfo = AppInfo(
    uid = uid,
    packageName = packageName,
    label = label,
    isSystem = isSystem,
)

/** Maps a domain model back to its entity. */
fun AppInfo.toEntity(): AppMetaEntity = AppMetaEntity(
    uid = uid,
    packageName = packageName,
    label = label,
    isSystem = isSystem,
)
