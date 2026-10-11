package com.chanakya.core.data.model

/**
 * A single observed DNS query and the decision the rule engine took.
 *
 * @param id database identifier.
 * @param timestamp epoch millis when the query was seen.
 * @param uid the app that made the query.
 * @param packageName the package name of that app.
 * @param domain the queried domain, e.g. `graph.facebook.com`.
 * @param networkId the id of the matching [TrackerNetwork], if the domain is known.
 * @param blocked whether the query was blocked (true) or allowed (false).
 */
data class BlockedEvent(
    val id: Long = 0,
    val timestamp: Long,
    val uid: Int,
    val packageName: String,
    val domain: String,
    val networkId: String? = null,
    val blocked: Boolean = true,
)
