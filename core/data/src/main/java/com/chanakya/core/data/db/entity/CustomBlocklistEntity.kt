package com.chanakya.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A blocklist the user imported.
 *
 * The domains are stored as a single newline-separated blob rather than one row per
 * domain: a large imported list can contain hundreds of thousands of entries, and a
 * blob keeps the database small and the import fast.
 */
@Entity(tableName = "custom_blocklists")
data class CustomBlocklistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val domains: String,
    val importedAt: Long,
) {
    /** The domains as a set, skipping blank lines and any CIDR entries. */
    fun domainSet(): Set<String> = lines().filterNot { it.contains('/') }.toSet()

    /** The IP ranges as a set, taking only the CIDR entries. */
    fun ipRangeSet(): Set<String> = lines().filter { it.contains('/') }.toSet()

    /** Every non-blank entry, domains and IP ranges together. */
    fun lines(): Set<String> = domains
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}
