package com.example.salesstysetgps.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [StopEntity::class,RoutePointEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stopDao(): StopDao

    abstract fun routePointDao(): RoutePointDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "tracking-db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}


