package com.chanakya.core.data.repository

import com.chanakya.core.data.db.dao.UserRuleDao
import com.chanakya.core.data.db.entity.toEntity
import com.chanakya.core.data.db.entity.toModel
import com.chanakya.core.data.model.UserRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The read/write model for user-defined allow/block rules.
 */
class UserRuleRepository(private val userRuleDao: UserRuleDao) {

    /** Observe every rule so the engine and the UI stay in sync. */
    fun observeRules(): Flow<List<UserRule>> =
        userRuleDao.observeAll().map { entities -> entities.map { it.toModel() } }

    /** Read every rule once. */
    suspend fun getAll(): List<UserRule> = userRuleDao.getAll().map { it.toModel() }

    /**
     * Add a rule, ignoring an exact duplicate.
     *
     * Without this, tapping the same menu action twice would leave two identical rows
     * that both have to be deleted.
     */
    suspend fun add(rule: UserRule) {
        val alreadyExists = userRuleDao.countMatching(
            type = rule.type.name,
            scope = rule.scope.name,
            domain = rule.domain,
            packageName = rule.packageName,
        ) > 0
        if (alreadyExists) return
        userRuleDao.insert(rule.toEntity())
    }

    /** Delete a rule by id. */
    suspend fun delete(id: Long) {
        userRuleDao.deleteById(id)
    }

    /** Enable or disable a rule. */
    suspend fun setEnabled(id: Long, enabled: Boolean) {
        userRuleDao.setEnabled(id, enabled)
    }
}
