package com.example.salesstysetgps.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "route_points")
data class RoutePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val lat: Double,
    val lng: Double
)