package com.chanakya.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.chanakya.core.data.db.entity.AppMetaEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access for the cached list of installed applications.
 */
@Dao
interface AppMetaDao {

    /** Inserts new apps and updates existing ones in a single transaction. */
    @Upsert
    suspend fun upsertAll(apps: List<AppMetaEntity>)

    /** Observe every known app. */
    @Query("SELECT * FROM app_meta")
    fun observeAll(): Flow<List<AppMetaEntity>>

    /** Look up a single app by package name. */
    @Query("SELECT * FROM app_meta WHERE packageName = :packageName LIMIT 1")
    suspend fun find(packageName: String): AppMetaEntity?
}
