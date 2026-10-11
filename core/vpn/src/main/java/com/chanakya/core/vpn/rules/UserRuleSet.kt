package com.chanakya.core.vpn.rules

import com.chanakya.core.data.model.RuleScope
import com.chanakya.core.data.model.RuleType
import com.chanakya.core.data.model.UserRule

/**
 * An immutable snapshot of the user's allow/block rules, prepared for fast lookup.
 *
 * Like [BlocklistStore], the set is replaced wholesale rather than mutated, so it can
 * be read from the packet loop without locking. Matching is suffix-aware, so a rule
 * for `example.com` also covers `ads.example.com`.
 */
class UserRuleSet(rules: List<UserRule> = emptyList()) {

    private val active: List<UserRule> = rules.filter { it.enabled }

    /** Whether there are no active rules. */
    val isEmpty: Boolean get() = active.isEmpty()

    /**
     * Evaluate the user rules for a query.
     *
     * Precedence, first match wins:
     *   1. app allow
     *   2. global allow
     *   3. app block
     *   4. global block
     *
     * @return the decision, or null when no rule applies (the caller then consults the
     *   blocklist).
     */
    fun evaluate(domain: String, packageName: String): RuleDecision? {
        val normalised = domain.lowercase().trimEnd('.')
        if (matches(normalised, packageName, RuleType.ALLOW, RuleScope.APP)) return RuleDecision.ALLOW
        if (matches(normalised, packageName, RuleType.ALLOW, RuleScope.GLOBAL)) return RuleDecision.ALLOW
        if (matches(normalised, packageName, RuleType.BLOCK, RuleScope.APP)) return RuleDecision.BLOCK
        if (matches(normalised, packageName, RuleType.BLOCK, RuleScope.GLOBAL)) return RuleDecision.BLOCK
        return null
    }

    /** Whether any rule of the given type and scope matches. */
    private fun matches(
        domain: String,
        packageName: String,
        type: RuleType,
        scope: RuleScope,
    ): Boolean = active.any { rule ->
        rule.type == type &&
            rule.scope == scope &&
            (scope == RuleScope.GLOBAL || rule.packageName == packageName) &&
            domainMatches(domain, rule.domain)
    }

    /** Suffix-aware domain match: `a.b.example.com` matches a rule for `example.com`. */
    private fun domainMatches(domain: String, ruleDomain: String): Boolean {
        val target = ruleDomain.lowercase().trimEnd('.')
        var candidate = domain
        while (candidate.isNotEmpty()) {
            if (candidate == target) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) break
            candidate = candidate.substring(dot + 1)
        }
        return false
    }
}
