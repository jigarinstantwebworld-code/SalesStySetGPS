package com.example.salesstysetgps.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "route_points")
data class RoutePointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routeId: Long,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)


@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTimeMillis: Long,
    val endTimeMillis: Long? = null,
    val durationSeconds: Long = 0,
    val stopCount: Int = 0,
    val distanceMeters: Double? = null,
    val screenshotPath: String? = null // Add this field
)

@Entity(tableName = "route_stop_relations")
data class RouteStopRelation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routeId: Long,
    val stopId: Long
)