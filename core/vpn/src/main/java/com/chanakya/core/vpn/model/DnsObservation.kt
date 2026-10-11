package com.chanakya.core.vpn.model

/**
 * A single DNS query observed by the interceptor, before it is persisted.
 *
 * @param domain the queried domain.
 * @param blocked whether the rule engine blocked it.
 * @param uid the uid of the app that made the query, or -1 if it could not be resolved.
 * @param sourcePort the local UDP port the query came from; used for UID attribution.
 * @param timestamp epoch millis the query was seen.
 */
data class DnsObservation(
    val domain: String,
    val blocked: Boolean,
    val uid: Int,
    val sourcePort: Int,
    val timestamp: Long,
)
