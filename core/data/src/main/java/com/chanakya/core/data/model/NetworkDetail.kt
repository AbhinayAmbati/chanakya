package com.chanakya.core.data.model

/**
 * A domain and how much it was seen, for the network and domain detail views.
 *
 * @param domain the resolved domain.
 * @param count how many times it was seen in total.
 * @param blockedCount how many of those were blocked.
 * @param firstSeen when it was first seen, epoch millis.
 * @param lastSeen when it was last seen, epoch millis.
 */
data class DomainSummary(
    val domain: String,
    val count: Int,
    val blockedCount: Int,
    val firstSeen: Long,
    val lastSeen: Long,
)

/**
 * Everything known about one tracker network, across every app on the device.
 *
 * This is the cross-app view: it answers "who is this company, and how much of my phone
 * is talking to it" rather than the per-app question the app detail screen answers.
 *
 * @param network the company behind the domains.
 * @param blockedCount queries blocked for this network, across all apps.
 * @param appCount how many distinct apps contacted it.
 * @param domains every domain attributed to it, busiest first.
 * @param apps every app that contacted it, busiest first.
 */
data class NetworkDetail(
    val network: TrackerNetwork,
    val blockedCount: Int,
    val appCount: Int,
    val domains: List<DomainSummary>,
    val apps: List<AppCount>,
)

/**
 * Everything known about one domain, across every app on the device.
 *
 * @param domain the domain itself.
 * @param network the company it is attributed to, if the catalogue knows it.
 * @param blockedCount how many requests to it were blocked.
 * @param allowedCount how many were allowed through.
 * @param firstSeen when it was first seen, epoch millis.
 * @param lastSeen when it was last seen, epoch millis.
 * @param apps every app that asked for it, busiest first.
 */
data class DomainDetail(
    val domain: String,
    val network: TrackerNetwork?,
    val blockedCount: Int,
    val allowedCount: Int,
    val firstSeen: Long,
    val lastSeen: Long,
    val apps: List<AppCount>,
) {
    /** Total requests seen for this domain. */
    val count: Int get() = blockedCount + allowedCount
}
