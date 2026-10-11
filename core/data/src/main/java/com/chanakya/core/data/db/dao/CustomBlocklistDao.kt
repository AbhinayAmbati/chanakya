package com.chanakya.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.chanakya.core.data.db.entity.CustomBlocklistEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access for imported blocklists.
 */
@Dao
interface CustomBlocklistDao {

    /** Observe every imported list, most recent first. */
    @Query("SELECT * FROM custom_blocklists ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<CustomBlocklistEntity>>

    /** Read every imported list once. */
    @Query("SELECT * FROM custom_blocklists")
    suspend fun getAll(): List<CustomBlocklistEntity>

    /** Insert or replace an imported list. */
    @Upsert
    suspend fun upsert(list: CustomBlocklistEntity)

    /** Delete an imported list by id. */
    @Query("DELETE FROM custom_blocklists WHERE id = :id")
    suspend fun deleteById(id: String)
}
