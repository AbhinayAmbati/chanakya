package com.chanakya.core.data.repository

import com.chanakya.core.data.db.dao.BlockedEventDao

/**
 * One-shot aggregate queries used by the background report workers.
 *
 * Unlike [TrackerRepository], these are suspend functions returning a single value: a
 * worker runs once and does not need to observe changes, so there is no reason to keep
 * a Flow open.
 */
class ReportRepository(private val eventDao: BlockedEventDao) {

    /** Number of blocked queries since [since]. */
    suspend fun blockedSince(since: Long): Int = eventDao.blockedSince(since)

    /** Number of distinct apps that contacted a tracker since [since]. */
    suspend fun appsSince(since: Long): Int = eventDao.appsSince(since)

    /** The package name of the busiest app since [since], if any. */
    suspend fun topAppSince(since: Long): String? = eventDao.topAppSince(since)
}
