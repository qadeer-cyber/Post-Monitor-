package com.affiliatemonitor.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SourceEntity::class,
        PostEntity::class,
        LogEntity::class,
        ScanHistoryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun postDao(): PostDao
    abstract fun logDao(): LogDao
    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v1 → v2: add nullable couponCode column to posts. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE posts ADD COLUMN couponCode TEXT DEFAULT NULL")
            }
        }

        fun get(context: Context): AppDatabase {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "apm.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    // Last-resort safety net for any older debug builds with broken
                    // schemas; declared migrations always run first.
                    .fallbackToDestructiveMigration()
                    .build()
                instance = db
                return db
            }
        }
    }
}
