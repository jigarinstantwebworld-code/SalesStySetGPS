package com.example.salesstysetgps.ui

import com.example.salesstysetgps.data.StopPoint
import com.example.salesstysetgps.data.local.RouteEntity
import com.example.salesstysetgps.data.local.RoutePointEntity

data class DailyReportData(
    val date: String,
    val routes: List<RouteWithDetails>,
    val totalRoutes: Int,
    val totalStops: Int,
    val totalDuration: Long,
    val totalDistance: Double,
    val syncHistory: List<SyncRecordDisplay> = emptyList()  // ✅ Add this
)

data class SyncRecordDisplay(
    val apiName: String,
    val lastSyncedTime: String,
    val status: Int
)

data class RouteWithDetails(
    val route: RouteEntity,
    val stops: List<StopWithDetails>,
    val routeDistance: Double,
    val segments: List<RouteSegment>,
    val routePoints: List<RoutePointEntity> = emptyList() // Add this
)

data class RouteWithStops(
    val route: RouteEntity,
    val stops: List<StopPoint>,
    var isExpanded : Boolean= false
)

data class StopWithDetails(
    val stop: StopPoint,
    val letter: String,
    val distanceFromPrev: Double? = null, // Distance from previous stop in km
    val timeFromPrev: Long? = null // Time from previous stop in minutes
)