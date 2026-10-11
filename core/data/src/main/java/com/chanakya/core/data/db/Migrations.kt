package com.chanakya.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations.
 *
 * Every change to an entity must ship with a migration here - release builds never
 * fall back to destructive migration, so a missing migration would wipe user data.
 */

/** Adds the `user_rules` and `custom_blocklists` tables. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `user_rules` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`type` TEXT NOT NULL, " +
                "`scope` TEXT NOT NULL, " +
                "`domain` TEXT NOT NULL, " +
                "`packageName` TEXT, " +
                "`enabled` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_blocklists` (" +
                "`id` TEXT NOT NULL PRIMARY KEY, " +
                "`name` TEXT NOT NULL, " +
                "`domains` TEXT NOT NULL, " +
                "`importedAt` INTEGER NOT NULL)",
        )
    }
}
