package com.chanakya.core.data.db

/**
 * Query projections used by the aggregate report queries.
 *
 * Room maps the selected columns onto these classes by name, so the property names
 * must match the column aliases in the corresponding `@Query`.
 */

/** A per-network count returned by the top-networks query. */
data class NetworkCountRow(
    val networkId: String,
    val count: Int,
)

/** A per-app count returned by the top-apps query. */
data class AppCountRow(
    val packageName: String,
    val count: Int,
)
