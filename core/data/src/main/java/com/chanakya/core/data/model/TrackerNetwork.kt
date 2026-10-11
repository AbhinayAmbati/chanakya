package com.chanakya.core.data.model

/**
 * A tracker operator - the company behind one or more tracking domains.
 *
 * @param id a stable identifier, e.g. "meta".
 * @param name the user-facing company name, e.g. "Meta".
 * @param category the kind of tracking it performs.
 */
data class TrackerNetwork(
    val id: String,
    val name: String,
    val category: TrackerCategory,
)
