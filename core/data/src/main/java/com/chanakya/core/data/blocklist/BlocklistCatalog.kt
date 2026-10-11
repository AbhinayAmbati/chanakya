package com.chanakya.core.data.blocklist

/**
 * A blocklist bundled with the application.
 *
 * @param id a stable identifier, also used as the asset file name.
 * @param assetPath the file inside the app's assets.
 * @param enabledByDefault whether the list is on when the user first runs the app.
 */
data class BundledBlocklist(
    val id: String,
    val name: String,
    val description: String,
    val assetPath: String,
    val enabledByDefault: Boolean,
)

/**
 * The catalogue of lists shipped with the app. The India list is a deliberate
 * differentiator: global lists under-cover the SDKs embedded in Indian apps.
 */
object BlocklistCatalog {

    /** Every bundled list, in the order they appear in Settings. */
    val bundled: List<BundledBlocklist> = listOf(
        BundledBlocklist(
            id = "base",
            name = "Core trackers",
            description = "A curated set of the most common ad and tracking domains.",
            assetPath = "blocklists/base.txt",
            enabledByDefault = true,
        ),
        BundledBlocklist(
            id = "ads",
            name = "Ad networks",
            description = "Programmatic advertising, exchanges and ad servers.",
            assetPath = "blocklists/ads.txt",
            enabledByDefault = true,
        ),
        BundledBlocklist(
            id = "analytics",
            name = "Analytics SDKs",
            description = "App analytics, attribution and crash-reporting services.",
            assetPath = "blocklists/analytics.txt",
            enabledByDefault = true,
        ),
        BundledBlocklist(
            id = "india",
            name = "India SDKs",
            description = "Trackers and SDKs common in Indian apps.",
            assetPath = "blocklists/india.txt",
            enabledByDefault = false,
        ),
    )

    /** The ids of the lists enabled on a fresh install. */
    fun defaultIds(): Set<String> = bundled.filter { it.enabledByDefault }.map { it.id }.toSet()
}
