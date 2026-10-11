package com.chanakya.core.vpn.model

/**
 * A snapshot of what the VPN engine is actually doing, for the in-app diagnostics view.
 *
 * When filtering appears not to work, these numbers say where it stopped: whether the
 * tunnel is routing resolvers at all, whether the blocklist actually loaded, and
 * whether observations are being produced.
 *
 * @param routedDnsServers how many resolver addresses were routed into the tunnel.
 * @param domainsLoaded how many blocked domains the rule engine holds.
 * @param ipRangesLoaded how many blocked IP ranges the engine holds.
 * @param observationsThisSession queries and dropped packets seen since the tunnel came up.
 * @param blockedThisSession how many of those were blocked.
 */
data class VpnDiagnostics(
    val routedDnsServers: Int = 0,
    val domainsLoaded: Int = 0,
    val ipRangesLoaded: Int = 0,
    val observationsThisSession: Int = 0,
    val blockedThisSession: Int = 0,
)
