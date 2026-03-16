package com.example.salesstysetgps.location

import android.content.Context
import com.example.salesstysetgps.data.LocationPoint
import com.example.salesstysetgps.data.local.AppDatabase
import com.example.salesstysetgps.data.local.RoutePointEntity
import com.google.android.gms.maps.model.LatLng

class RouteRepository(context: Context) {
    private val dao = AppDatabase.get(context).routePointDao()

    suspend fun add(point: LocationPoint) {
        dao.insert(
            RoutePointEntity(
                timestampMillis = point.timestampMillis,
                lat = point.latLng.latitude,
                lng = point.latLng.longitude
            )
        )
    }

    suspend fun getBetween(start: Long, end: Long): List<LocationPoint> =
        dao.getBetween(start, end).map {
            LocationPoint(LatLng(it.lat, it.lng), it.timestampMillis)
        }
}