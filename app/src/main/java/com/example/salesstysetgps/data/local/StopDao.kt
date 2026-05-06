package com.example.salesstysetgps.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StopDao {
    @Query("SELECT * FROM stops ORDER BY startWallTimeMillis ASC")
    fun observeStops(): Flow<List<StopEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stop: StopEntity)

    @Query("UPDATE stops SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Query("DELETE FROM stops")
    suspend fun clearAll()

    // Add this method to get stops for a specific route by time range
    @Query("""
    SELECT * FROM stops 
    WHERE startWallTimeMillis >= :routeStartTime 
    AND (endWallTimeMillis <= :routeEndTime AND endWallTimeMillis IS NOT NULL)
    ORDER BY startWallTimeMillis ASC
""")
    suspend fun getStopsInTimeRange(routeStartTime: Long, routeEndTime: Long): List<StopEntity>


    @Query("SELECT * FROM stops WHERE id = :stopId")
    suspend fun getStopById(stopId: Long): StopEntity?



    @Query("""
    SELECT * FROM stops 
    WHERE endWallTimeMillis BETWEEN :fromTime AND :toTime
    AND endWallTimeMillis IS NOT NULL
    AND endWallTimeMillis > 0
    ORDER BY endWallTimeMillis ASC
""")
    suspend fun getStopsInTimeRanges(fromTime: Long, toTime: Long): List<StopEntity>


    @Query("SELECT * FROM stops ORDER BY startWallTimeMillis DESC")
    suspend fun getAllStops(): List<StopEntity>


    @Query("""
    SELECT * FROM stops 
    WHERE endWallTimeMillis IS NULL
""")
    suspend fun getOngoingStops(): List<StopEntity>

    @Delete
    suspend fun deleteStop(stop: StopEntity)
}



