package com.chanakya.core.vpn.rules

/**
 * Decides whether a DNS query should be allowed or blocked.
 *
 * The engine is intentionally small and pure: it takes a domain and the package name
 * of the app behind it, and returns a [RuleDecision]. That makes the precedence rules
 * trivial to unit-test.
 *
 * Precedence, first match wins:
 *   1. per-app allow rule   (the user allowed a domain for one app)
 *   2. global allow rule    (the user allowed a domain everywhere)
 *   3. per-app block rule   (the user blocked a domain for one app)
 *   4. global block rule    (the user blocked a domain everywhere)
 *   5. blocklist match
 *   6. default: allow
 *
 * Steps 1-4 live in [UserRuleSet]; steps 5-6 here.
 *
 * @param blocklist the current blocklist snapshot.
 * @param userRules the current user-rule snapshot.
 */
class RuleEngine(
    private val blocklist: BlocklistStore = BlocklistStore.STARTER,
    private val userRules: UserRuleSet = UserRuleSet(),
) {

    /**
     * Apply the rules to a query.
     *
     * @param domain the queried domain.
     * @param packageName the app that made the query, or [UNKNOWN_PACKAGE].
     */
    fun decide(domain: String, packageName: String = UNKNOWN_PACKAGE): RuleDecision {
        // User rules always take precedence over the blocklist.
        userRules.evaluate(domain, packageName)?.let { return it }
        return if (blocklist.isBlocked(domain)) RuleDecision.BLOCK else RuleDecision.ALLOW
    }

    companion object {
        /** Used when the app behind a query could not be resolved. */
        const val UNKNOWN_PACKAGE = "unknown"
    }
}
