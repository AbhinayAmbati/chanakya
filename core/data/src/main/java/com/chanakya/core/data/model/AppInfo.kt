package com.chanakya.core.data.model

/**
 * An application installed on the device.
 *
 * @param uid the Linux user id Android assigns to the app; shared by apps that
 *   share a signing key, which is why attribution is done at the UID level.
 * @param packageName the app's package name, e.g. `com.instagram.android`.
 * @param label the human-readable name shown to the user, e.g. "Instagram".
 * @param isSystem whether this is a system application.
 */
data class AppInfo(
    val uid: Int,
    val packageName: String,
    val label: String,
    val isSystem: Boolean = false,
)
