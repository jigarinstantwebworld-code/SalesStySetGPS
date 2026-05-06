package com.example.salesstysetgps.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Insert
    suspend fun insertRoute(route: RouteEntity): Long

    @Update
    suspend fun updateRoute(route: RouteEntity)

    @Query("UPDATE routes SET screenshotPath = :path WHERE id = :routeId")
    suspend fun updateScreenshotPath(routeId: Long, path: String)

    @Insert
    suspend fun insertRoutePoint(point: RoutePointEntity)

    @Query("SELECT * FROM routes ORDER BY startTimeMillis DESC")
    fun observeAllRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getRouteById(routeId: Long): RouteEntity?


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRouteStopRelation(relation: RouteStopRelation)

    @Query("SELECT stopId FROM route_stop_relations WHERE routeId = :routeId")
    suspend fun getStopIdsForRoute(routeId: Long): List<Long>

    @Query("""
    SELECT DISTINCT s.* FROM stops s 
    INNER JOIN route_stop_relations r ON s.id = r.stopId 
    WHERE r.routeId = :routeId 
    ORDER BY s.startWallTimeMillis ASC
""")
    suspend fun getStopsForRoute(routeId: Long): List<StopEntity>

    @Query("SELECT * FROM route_points WHERE routeId = :routeId ORDER BY timestamp ASC")
    suspend fun getRoutePoints(routeId: Long): List<RoutePointEntity>


    @Query("SELECT * FROM routes WHERE startTimeMillis BETWEEN :startMillis AND :endMillis ORDER BY startTimeMillis ASC")
    suspend fun getRoutesInTimeRange(startMillis: Long, endMillis: Long): List<RouteEntity>


    @Query("SELECT * FROM routes WHERE endTimeMillis IS NULL ORDER BY startTimeMillis DESC LIMIT 1")
    suspend fun getOngoingRoute(): RouteEntity?

    @Query("SELECT * FROM routes ORDER BY startTimeMillis DESC")
    suspend fun getAllRoutes(): List<RouteEntity>



    @Query("DELETE FROM routes")
    suspend fun deleteAllRoutes()
}