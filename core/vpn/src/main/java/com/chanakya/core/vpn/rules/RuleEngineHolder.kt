package com.chanakya.core.vpn.rules

/**
 * Holds the current [RuleEngine] so it can be swapped while the tunnel is running.
 *
 * When the user changes a rule or toggles a blocklist, the service rebuilds the engine
 * and calls [update]. The packet loop reads through [decide], so a change takes effect
 * on the very next query without restarting the VPN.
 */
class RuleEngineHolder(initial: RuleEngine = RuleEngine()) {

    @Volatile
    private var engine: RuleEngine = initial

    /** Replace the engine atomically. */
    fun update(newEngine: RuleEngine) {
        engine = newEngine
    }

    /** Decide whether a query should be allowed or blocked. */
    fun decide(domain: String, packageName: String): RuleDecision =
        engine.decide(domain, packageName)
}
