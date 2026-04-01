package com.example.salesstysetgps.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StopEntity::class, RoutePointEntity::class, RouteEntity::class, RouteStopRelation::class],
    version = 1, // Increment version from 3 to 4
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stopDao(): StopDao
    abstract fun routePointDao(): RouteDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // Migration from version 3 to 4 - Add letter column to stops table
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add letter column to stops table
                database.execSQL("ALTER TABLE stops ADD COLUMN letter TEXT")

                // Optional: Update existing stops with calculated letters
                // This will assign letters based on start time order
                database.execSQL("""
                    UPDATE stops 
                    SET letter = (
                        SELECT CHAR(65 + rn) FROM (
                            SELECT id, ROW_NUMBER() OVER (ORDER BY startWallTimeMillis) - 1 as rn 
                            FROM stops
                        ) ranked 
                        WHERE ranked.id = stops.id
                    )
                """)
            }
        }

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "tracking-db"
            )
//                .addMigrations(MIGRATION_3_4) // Use proper migration
                .build()
                .also { INSTANCE = it }
        }

//        952861855187-2juogpib3iqpq8isp35vjgucc0vhl331.apps.googleusercontent.com //android
//        952861855187-bjbe44ek9os88f4g6g6vg3bqspbeoi4u.apps.googleusercontent.com // webClientID

    }
}


