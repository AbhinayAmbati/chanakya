package com.chanakya.core.vpn.rules

/**
 * Decides whether a DNS query should be allowed or blocked.
 *
 * The engine is intentionally small and pure: it takes a domain and a UID and returns
 * a [RuleDecision]. That makes it trivial to unit-test the precedence rules.
 *
 * Precedence, first match wins:
 *   1. per-app allow rule   (user whitelisted a domain for one app)
 *   2. global allow rule    (user whitelisted a domain everywhere)
 *   3. per-app block rule   (user blocked a domain for one app)
 *   4. blocklist match
 *   5. default: allow
 *
 * Only step 4 is implemented today; the user-rule steps are the next increment and
 * the shape of this class already accommodates them.
 *
 * @param blocklist the current blocklist snapshot.
 */
class RuleEngine(
    private val blocklist: BlocklistStore = BlocklistStore.STARTER,
) {

    /**
     * Apply the rules to a query.
     *
     * @param domain the queried domain.
     * @param uid the app that made the query, or -1 if unknown.
     */
    fun decide(domain: String, uid: Int = -1): RuleDecision {
        // Steps 1-3 (user rules) will slot in here.
        return if (blocklist.isBlocked(domain)) RuleDecision.BLOCK else RuleDecision.ALLOW
    }

    /** Replace the blocklist snapshot atomically. */
    fun updateBlocklist(newBlocklist: BlocklistStore): RuleEngine = RuleEngine(newBlocklist)
}
