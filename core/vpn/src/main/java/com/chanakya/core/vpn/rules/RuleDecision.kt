package com.chanakya.core.vpn.rules

/**
 * The outcome of applying the rules to a query.
 */
enum class RuleDecision {
    /** Let the query through to the real resolver. */
    ALLOW,

    /** Answer the query locally with a null address. */
    BLOCK,
}
