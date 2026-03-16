package com.example.salesstysetgps.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RoutePointDao {
    @Insert
    suspend fun insert(point: RoutePointEntity)

    @Query("""
        SELECT * FROM route_points
        WHERE timestampMillis BETWEEN :start AND :end
        ORDER BY timestampMillis ASC
    """)
    suspend fun getBetween(start: Long, end: Long): List<RoutePointEntity>

    @Query("DELETE FROM route_points")
    suspend fun clearAll()
}