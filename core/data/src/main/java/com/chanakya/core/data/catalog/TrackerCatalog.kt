package com.chanakya.core.data.catalog

import com.chanakya.core.data.model.TrackerCategory
import com.chanakya.core.data.model.TrackerNetwork

/**
 * A curated catalogue mapping known tracker domains to the company behind them and
 * the kind of tracking they perform.
 *
 * This is what turns a raw domain like `graph.facebook.com` into the human-readable
 * "Meta - Social pixels" that the UI shows. It includes India-relevant SDKs
 * (CleverTap, MoEngage, WebEngage, ...) that global lists under-cover.
 *
 * The lookup is suffix-aware: `stats.doubleclick.net` resolves to the
 * `doubleclick.net` entry without needing an explicit rule.
 */
object TrackerCatalog {

    // ---- Networks ---------------------------------------------------------
    private val google = TrackerNetwork("google", "Google", TrackerCategory.AD_NETWORKS)
    private val googleAnalytics = TrackerNetwork("google-analytics", "Google Analytics", TrackerCategory.ANALYTICS)
    private val meta = TrackerNetwork("meta", "Meta", TrackerCategory.SOCIAL_PIXELS)
    private val facebook = TrackerNetwork("facebook", "Facebook", TrackerCategory.SOCIAL_PIXELS)
    private val instagram = TrackerNetwork("instagram", "Instagram", TrackerCategory.SOCIAL_PIXELS)
    private val amazon = TrackerNetwork("amazon", "Amazon Ads", TrackerCategory.AD_NETWORKS)
    private val appsflyer = TrackerNetwork("appsflyer", "AppsFlyer", TrackerCategory.ANALYTICS)
    private val adjust = TrackerNetwork("adjust", "Adjust", TrackerCategory.ANALYTICS)
    private val branch = TrackerNetwork("branch", "Branch", TrackerCategory.ANALYTICS)
    private val unity = TrackerNetwork("unity", "Unity Ads", TrackerCategory.AD_NETWORKS)
    private val applovin = TrackerNetwork("applovin", "AppLovin", TrackerCategory.AD_NETWORKS)
    private val adcolony = TrackerNetwork("adcolony", "AdColony", TrackerCategory.AD_NETWORKS)
    private val mixpanel = TrackerNetwork("mixpanel", "Mixpanel", TrackerCategory.ANALYTICS)
    private val amplitude = TrackerNetwork("amplitude", "Amplitude", TrackerCategory.ANALYTICS)
    private val firebase = TrackerNetwork("firebase", "Firebase", TrackerCategory.ANALYTICS)
    private val crashlytics = TrackerNetwork("crashlytics", "Crashlytics", TrackerCategory.ANALYTICS)
    private val tiktok = TrackerNetwork("tiktok", "TikTok", TrackerCategory.SOCIAL_PIXELS)
    private val twitter = TrackerNetwork("twitter", "X (Twitter)", TrackerCategory.SOCIAL_PIXELS)
    private val snapchat = TrackerNetwork("snapchat", "Snapchat", TrackerCategory.SOCIAL_PIXELS)
    private val criteo = TrackerNetwork("criteo", "Criteo", TrackerCategory.AD_NETWORKS)
    private val moengage = TrackerNetwork("moengage", "MoEngage", TrackerCategory.ANALYTICS)
    private val clevertap = TrackerNetwork("clevertap", "CleverTap", TrackerCategory.ANALYTICS)
    private val webengage = TrackerNetwork("webengage", "WebEngage", TrackerCategory.ANALYTICS)
    private val truecaller = TrackerNetwork("truecaller", "Truecaller SDK", TrackerCategory.OTHER)
    private val mparticle = TrackerNetwork("mparticle", "mParticle", TrackerCategory.ANALYTICS)
    private val onesignal = TrackerNetwork("onesignal", "OneSignal", TrackerCategory.OTHER)
    private val scorecard = TrackerNetwork("scorecard", "Scorecard", TrackerCategory.ANALYTICS)
    private val inMobi = TrackerNetwork("inmobi", "InMobi", TrackerCategory.AD_NETWORKS)

    /** Every network known to the catalogue. */
    val networks: List<TrackerNetwork> = listOf(
        google, googleAnalytics, meta, facebook, instagram, amazon, appsflyer, adjust,
        branch, unity, applovin, adcolony, mixpanel, amplitude, firebase, crashlytics,
        tiktok, twitter, snapchat, criteo, moengage, clevertap, webengage, truecaller,
        mparticle, onesignal, scorecard, inMobi,
    )

    // ---- Domain index -----------------------------------------------------
    private val domainIndex: Map<String, TrackerNetwork> = mapOf(
        "doubleclick.net" to google,
        "googlesyndication.com" to google,
        "googleadservices.com" to google,
        "google-analytics.com" to googleAnalytics,
        "analytics.google.com" to googleAnalytics,
        "graph.facebook.com" to facebook,
        "connect.facebook.net" to facebook,
        "facebook.com" to facebook,
        "instagram.com" to instagram,
        "cdninstagram.com" to instagram,
        "amazon-adsystem.com" to amazon,
        "appsflyer.com" to appsflyer,
        "adjust.com" to adjust,
        "branch.io" to branch,
        "unityads.unity3d.com" to unity,
        "applovin.com" to applovin,
        "adcolony.com" to adcolony,
        "mixpanel.com" to mixpanel,
        "amplitude.com" to amplitude,
        "firebaseinstallations.googleapis.com" to firebase,
        "firebaselogging-pa.googleapis.com" to firebase,
        "crashlytics.com" to crashlytics,
        "tiktok.com" to tiktok,
        "analytics.tiktok.com" to tiktok,
        "twitter.com" to twitter,
        "t.co" to twitter,
        "snapchat.com" to snapchat,
        "sc-static.net" to snapchat,
        "criteo.com" to criteo,
        "moengage.com" to moengage,
        "clevertap.com" to clevertap,
        "webengage.com" to webengage,
        "truecaller.com" to truecaller,
        "mparticle.com" to mparticle,
        "onesignal.com" to onesignal,
        "scorecardresearch.com" to scorecard,
        "inmobi.com" to inMobi,
        "googletagmanager.com" to googleAnalytics,
        "googleapis.com" to google,
    )

    /**
     * Resolve the tracker network for a domain, walking up its parent suffixes so
     * that `stats.doubleclick.net` matches the `doubleclick.net` entry.
     *
     * @return the matching network, or null if the domain is unknown.
     */
    fun networkForDomain(domain: String): TrackerNetwork? {
        var candidate = domain.lowercase().trimEnd('.')
        while (candidate.isNotEmpty()) {
            domainIndex[candidate]?.let { return it }
            val dot = candidate.indexOf('.')
            if (dot < 0) break
            candidate = candidate.substring(dot + 1)
        }
        return null
    }
}
