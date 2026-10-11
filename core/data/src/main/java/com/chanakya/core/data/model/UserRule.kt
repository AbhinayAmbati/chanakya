package com.chanakya.core.data.model

/** Whether a user rule allows or blocks matching traffic. */
enum class RuleType { ALLOW, BLOCK }

/** Whether a user rule applies to every app or to a single app. */
enum class RuleScope { GLOBAL, APP }

/**
 * A user-defined exception to the blocklist.
 *
 * Rules are evaluated before the blocklist, in this order: app allow, global allow,
 * app block, global block. The first match wins, which lets a user carve out a
 * precise exception without losing the protection everywhere else.
 *
 * @param domain the domain the rule applies to; subdomains match too.
 * @param packageName the app the rule is scoped to, or null for a global rule.
 */
data class UserRule(
    val id: Long = 0,
    val type: RuleType,
    val scope: RuleScope,
    val domain: String,
    val packageName: String? = null,
    val enabled: Boolean = true,
)
