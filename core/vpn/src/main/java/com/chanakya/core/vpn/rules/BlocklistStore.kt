package com.chanakya.core.vpn.rules

/**
 * An immutable snapshot of blocked domains.
 *
 * Lookups are suffix-aware: a query for `stats.doubleclick.net` matches a rule for
 * `doubleclick.net` because the store walks up the domain's parent suffixes. The
 * snapshot is replaced wholesale (never mutated) so it can be read concurrently from
 * the packet loop without locking.
 */
class BlocklistStore(domains: Set<String>) {

    /** Normalised, lower-cased domain set. */
    private val entries: Set<String> = domains
        .asSequence()
        .map { it.trim().lowercase().trimEnd('.') }
        .filter { it.isNotEmpty() }
        .toSet()

    /** The number of domains in the snapshot. */
    val size: Int get() = entries.size

    /**
     * Whether the given domain, or any of its parent domains, is blocked.
     *
     * @param domain the queried domain, e.g. `graph.facebook.com`.
     */
    fun isBlocked(domain: String): Boolean {
        var candidate = domain.lowercase().trimEnd('.')
        while (candidate.isNotEmpty()) {
            if (candidate in entries) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) break
            candidate = candidate.substring(dot + 1)
        }
        return false
    }

    companion object {
        /** An empty snapshot that blocks nothing. */
        val EMPTY = BlocklistStore(emptySet())

        /**
         * A small built-in list used until blocklist management is wired up. It covers
         * the most common ad and analytics domains, including India-relevant ones.
         */
        val STARTER: BlocklistStore = BlocklistStore(
            setOf(
                "doubleclick.net",
                "googlesyndication.com",
                "googleadservices.com",
                "google-analytics.com",
                "googletagmanager.com",
                "adservice.google.com",
                "graph.facebook.com",
                "connect.facebook.net",
                "analytics.tiktok.com",
                "ads.tiktok.com",
                "amazon-adsystem.com",
                "appsflyer.com",
                "adjust.com",
                "branch.io",
                "unityads.unity3d.com",
                "applovin.com",
                "adcolony.com",
                "mixpanel.com",
                "amplitude.com",
                "crashlytics.com",
                "scorecardresearch.com",
                "criteo.com",
                "moengage.com",
                "clevertap.com",
                "webengage.com",
                "inmobi.com",
                "mopub.com",
                "flurry.com",
                "chartboost.com",
                "vungle.com",
                "tapjoy.com",
                "startapp.com",
                "facebook.com",
                "an.facebook.com",
                "app-measurement.com",
                "firebaseinstallations.googleapis.com",
                "onesignal.com",
                "mrads.applovin.com",
                "adnxs.com",
                "adsrvr.org",
                "rubiconproject.com",
                "pubmatic.com",
                "openx.net",
                "smartadserver.com",
                "teads.tv",
                "outbrain.com",
                "taboola.com",
                "zemanta.com",
                "bidswitch.net",
                "casalemedia.com",
                "sharethrough.com",
                "3lift.com",
                "smaato.net",
                "yieldmo.com",
                "lijit.com",
                "gumgum.com",
                "adform.net",
                "serving-sys.com",
                "adsafeprotected.com",
                "moatads.com",
                "doubleverify.com",
                "newrelic.com",
                "bugsnag.com",
                "instabug.com",
                "leanplum.com",
                "kissmetrics.com",
                "hotjar.com",
                "fullstory.com",
                "braze.com",
                "airship.com",
                "swrve.com",
            ),
        )
    }
}
