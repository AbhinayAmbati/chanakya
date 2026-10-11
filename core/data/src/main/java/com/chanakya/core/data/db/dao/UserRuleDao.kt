package com.chanakya.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.chanakya.core.data.db.entity.UserRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access for user-defined allow/block rules.
 */
@Dao
interface UserRuleDao {

    /** Observe every rule, ordered by domain. */
    @Query("SELECT * FROM user_rules ORDER BY domain ASC")
    fun observeAll(): Flow<List<UserRuleEntity>>

    /** Read every rule once. */
    @Query("SELECT * FROM user_rules")
    suspend fun getAll(): List<UserRuleEntity>

    /** Insert a rule and return its generated id. */
    @Insert
    suspend fun insert(rule: UserRuleEntity): Long

    /**
     * Count rules that are identical to the given one, so duplicates are not stored.
     *
     * A null package name is compared via IFNULL, because `= NULL` never matches.
     */
    @Query(
        "SELECT COUNT(*) FROM user_rules WHERE type = :type AND scope = :scope " +
            "AND domain = :domain AND IFNULL(packageName, '') = IFNULL(:packageName, '')",
    )
    suspend fun countMatching(type: String, scope: String, domain: String, packageName: String?): Int

    /** Delete a rule by id. */
    @Query("DELETE FROM user_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Enable or disable a rule. */
    @Query("UPDATE user_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
