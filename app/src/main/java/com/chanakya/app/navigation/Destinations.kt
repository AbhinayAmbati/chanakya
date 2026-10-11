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

    /** The argument key used by [APP_DETAIL]. */
    const val ARG_PACKAGE_NAME = "packageName"

    /** Build the route for a specific app's detail screen. */
    fun appDetail(packageName: String): String = "app/$packageName"

    /** Routes that show the bottom navigation bar. */
    val topLevel = setOf(HOME, APPS, REPORTS, SETTINGS)
}
