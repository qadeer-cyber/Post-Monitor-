package com.affiliatemonitor.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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

        fun get(context: Context): AppDatabase {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "apm.db",
                ).fallbackToDestructiveMigration().build()
                instance = db
                return db
            }
        }
    }
}
