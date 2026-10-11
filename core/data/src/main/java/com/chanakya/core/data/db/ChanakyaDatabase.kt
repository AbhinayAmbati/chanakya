package com.chanakya.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chanakya.core.data.db.dao.AppMetaDao
import com.chanakya.core.data.db.dao.BlockedEventDao
import com.chanakya.core.data.db.entity.AppMetaEntity
import com.chanakya.core.data.db.entity.BlockedEventEntity

/**
 * The application database. Bump [version] and add a migration whenever an entity
 * changes - never fall back to destructive migration in a release build.
 */
@Database(
    entities = [
        BlockedEventEntity::class,
        AppMetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ChanakyaDatabase : RoomDatabase() {

    /** Access to tracking events. */
    abstract fun blockedEventDao(): BlockedEventDao

    /** Access to cached app metadata. */
    abstract fun appMetaDao(): AppMetaDao

    companion object {
        /** The on-disk database file name. */
        const val NAME = "chanakya.db"
    }
}
