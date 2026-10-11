package com.chanakya.core.vpn.model

/**
 * The result of attributing a network request to an application.
 *
 * @param uid the app's uid.
 * @param packageName the app's package name, or null if it could not be resolved.
 */
data class ResolvedApp(
    val uid: Int,
    val packageName: String?,
)
