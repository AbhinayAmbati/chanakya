package com.chanakya.core.data.model

/**
 * A blocklist the user can enable - either one bundled with the app or one they
 * imported themselves.
 *
 * @param id a stable identifier.
 * @param name the user-facing name.
 * @param description a short explanation of what the list covers.
 * @param entryCount how many domains the list contains.
 * @param enabled whether the list is currently contributing to filtering.
 * @param isCustom whether the list was imported by the user.
 */
data class BlocklistInfo(
    val id: String,
    val name: String,
    val description: String,
    val entryCount: Int,
    val enabled: Boolean,
    val isCustom: Boolean,
)
