package com.chanakya.core.data.sample

import com.chanakya.core.data.model.AppInfo
import com.chanakya.core.data.model.BlockedEvent
import kotlin.random.Random

/**
 * Deterministic sample data used to exercise the UI before the VPN engine is wired
 * up, and inside Compose previews. It is only ever inserted from a debug build.
 */
object SampleData {

    /** A handful of well-known apps with realistic UIDs. */
    val apps: List<AppInfo> = listOf(
        AppInfo(uid = 10101, packageName = "com.instagram.android", label = "Instagram"),
        AppInfo(uid = 10102, packageName = "com.google.android.youtube", label = "YouTube"),
        AppInfo(uid = 10103, packageName = "com.whatsapp", label = "WhatsApp"),
        AppInfo(uid = 10104, packageName = "com.android.chrome", label = "Chrome"),
        AppInfo(uid = 10105, packageName = "com.flipkart.android", label = "Flipkart"),
        AppInfo(uid = 10106, packageName = "com.spotify.music", label = "Spotify"),
        AppInfo(uid = 10107, packageName = "com.swiggy.android", label = "Swiggy"),
        AppInfo(uid = 10108, packageName = "com.zhiliaoapp.musically", label = "TikTok"),
    )

    // package -> (domain, networkId, weight)
    private val seeds: List<Triple<String, Pair<String, String>, Int>> = listOf(
        Triple("com.instagram.android", "graph.facebook.com" to "facebook", 8),
        Triple("com.instagram.android", "connect.facebook.net" to "facebook", 4),
        Triple("com.instagram.android", "analytics.tiktok.com" to "tiktok", 2),
        Triple("com.google.android.youtube", "doubleclick.net" to "google", 9),
        Triple("com.google.android.youtube", "googlesyndication.com" to "google", 5),
        Triple("com.google.android.youtube", "google-analytics.com" to "google-analytics", 3),
        Triple("com.whatsapp", "graph.facebook.com" to "facebook", 6),
        Triple("com.android.chrome", "scorecardresearch.com" to "scorecard", 3),
        Triple("com.android.chrome", "criteo.com" to "criteo", 2),
        Triple("com.flipkart.android", "clevertap.com" to "clevertap", 5),
        Triple("com.flipkart.android", "appsflyer.com" to "appsflyer", 4),
        Triple("com.flipkart.android", "inmobi.com" to "inmobi", 3),
        Triple("com.spotify.music", "mixpanel.com" to "mixpanel", 4),
        Triple("com.spotify.music", "amplitude.com" to "amplitude", 2),
        Triple("com.swiggy.android", "moengage.com" to "moengage", 6),
        Triple("com.swiggy.android", "webengage.com" to "webengage", 3),
        Triple("com.zhiliaoapp.musically", "analytics.tiktok.com" to "tiktok", 7),
    )

    /**
     * Produce a spread of events across the last 24 hours.
     *
     * @param now the reference "current" time; defaults to the wall clock.
     */
    fun events(now: Long = System.currentTimeMillis()): List<BlockedEvent> {
        val random = Random(42) // fixed seed -> stable previews
        val uidByPackage = apps.associate { it.packageName to it.uid }
        val result = mutableListOf<BlockedEvent>()
        var id = 1L
        for ((packageName, domainAndNetwork, weight) in seeds) {
            val (domain, networkId) = domainAndNetwork
            repeat(weight) {
                val ageMillis = random.nextLong(0, 24L * 60 * 60 * 1000)
                result += BlockedEvent(
                    id = id++,
                    timestamp = now - ageMillis,
                    uid = uidByPackage[packageName] ?: -1,
                    packageName = packageName,
                    domain = domain,
                    networkId = networkId,
                    blocked = true,
                )
            }
        }
        return result
    }
}
