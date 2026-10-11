package com.chanakya.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chanakya.core.data.model.RuleScope
import com.chanakya.core.data.model.RuleType
import com.chanakya.core.data.model.UserRule

/**
 * Room representation of a [UserRule]. Enums are stored by name so the schema stays
 * readable and stable across reordering.
 */
@Entity(tableName = "user_rules")
data class UserRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val scope: String,
    val domain: String,
    val packageName: String?,
    val enabled: Boolean,
)

/** Maps this entity to its domain model. */
fun UserRuleEntity.toModel(): UserRule = UserRule(
    id = id,
    type = RuleType.valueOf(type),
    scope = RuleScope.valueOf(scope),
    domain = domain,
    packageName = packageName,
    enabled = enabled,
)

/** Maps a domain model back to its entity. */
fun UserRule.toEntity(): UserRuleEntity = UserRuleEntity(
    id = id,
    type = type.name,
    scope = scope.name,
    domain = domain,
    packageName = packageName,
    enabled = enabled,
)
