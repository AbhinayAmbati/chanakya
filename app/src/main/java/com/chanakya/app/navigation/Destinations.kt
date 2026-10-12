package com.chanakya.app.navigation

/**
 * The navigation graph's route definitions, kept in one place so routes are never
 * spelled out as loose strings at call sites.
 */
object Destinations {

    /** The Home dashboard. */
    const val HOME = "home"

    /** The list of apps that contacted trackers. */
    const val APPS = "apps"

    /** Summary reports. */
    const val REPORTS = "reports"

    /** Settings. */
    const val SETTINGS = "settings"

    /** The about screen. */
    const val ABOUT = "about"

    /** The terms of use. */
    const val TERMS = "terms"

    /** The privacy policy. */
    const val PRIVACY = "privacy"

    /** The per-app detail screen. */
    const val APP_DETAIL = "app/{packageName}"

    /** The cross-app detail screen for one tracker network. */
    const val NETWORK_DETAIL = "network/{networkId}"

    /** The argument key used by [APP_DETAIL]. */
    const val ARG_PACKAGE_NAME = "packageName"

    /** The argument key used by [NETWORK_DETAIL]. */
    const val ARG_NETWORK_ID = "networkId"

    /** Build the route for a specific app's detail screen. */
    fun appDetail(packageName: String): String = "app/$packageName"

    /** Build the route for a specific network's detail screen. */
    fun networkDetail(networkId: String): String = "network/$networkId"

    /** Routes that show the bottom navigation bar. */
    val topLevel = setOf(HOME, APPS, REPORTS, SETTINGS)
}
