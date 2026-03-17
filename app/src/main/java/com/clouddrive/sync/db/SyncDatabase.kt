package com.clouddrive.sync.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SyncedFileEntity::class], version = 1, exportSchema = false)
abstract class SyncDatabase : RoomDatabase() {

    abstract fun syncedFileDao(): SyncedFileDao

    companion object {
        @Volatile
        private var INSTANCE: SyncDatabase? = null

        fun getInstance(context: Context): SyncDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SyncDatabase::class.java,
                    "gallery_sync_db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}
