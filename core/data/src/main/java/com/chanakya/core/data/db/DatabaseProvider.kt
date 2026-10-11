package com.chanakya.core.data.db

import android.content.Context
import androidx.room.Room

/**
 * Process-wide access to the single [ChanakyaDatabase] instance.
 *
 * Both the UI (through the app's dependency container) and the VPN service need the
 * same database. Because they run in the same process, a small guarded singleton is
 * the simplest way to guarantee there is exactly one Room instance over the file -
 * opening the same database twice would risk inconsistent caches and wasted memory.
 */
object DatabaseProvider {

    @Volatile
    private var instance: ChanakyaDatabase? = null

    /**
     * Return the shared database, building it on first use.
     *
     * Double-checked locking keeps construction cheap on the hot path while still
     * being safe if two threads race on first access.
     */
    fun get(context: Context): ChanakyaDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ChanakyaDatabase::class.java,
                ChanakyaDatabase.NAME,
            ).addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
}
