package com.chanakya.core.data.model

/**
 * The broad kind of tracking a network performs. Used to colour and group the UI.
 *
 * @property displayName a user-facing label.
 */
enum class TrackerCategory(val displayName: String) {
    AD_NETWORKS("Ad networks"),
    ANALYTICS("Analytics"),
    SOCIAL_PIXELS("Social pixels"),
    FINGERPRINTING("Fingerprinting"),
    OTHER("Other"),
}
