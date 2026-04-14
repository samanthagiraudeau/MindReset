package com.example.mindreset.dao

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mindreset.models.BlockedApp
import com.example.mindreset.models.Reminder
import com.example.mindreset.models.AppUsageLog
import com.example.mindreset.models.ThoughtList

@Database(
    entities = [Reminder::class, BlockedApp::class, AppUsageLog::class, ThoughtList::class],
    version = 6
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun appUsageLogDao(): AppUsageLogDao
    abstract fun thoughtListDao(): ThoughtListDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS blocked_apps (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        blockedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_usage_logs (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        packageName TEXT NOT NULL,
                        openedAt INTEGER NOT NULL,
                        closedAt INTEGER,
                        date TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS thought_lists (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        title TEXT NOT NULL,
                        text TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE thought_lists ADD COLUMN isCheckable INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE thought_lists ADD COLUMN checkedItems TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN date TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reminders ADD COLUMN isDaily INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE reminders SET date = strftime('%Y-%m-%d','now','localtime') WHERE date = ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mindreset_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
