package com.example.salesstysetgps.location

import android.content.Context
import android.util.Log
import androidx.room.Query
import com.example.salesstysetgps.data.LocationPoint
import com.example.salesstysetgps.data.StopPoint
import com.example.salesstysetgps.data.local.AppDatabase
import com.example.salesstysetgps.data.local.RouteEntity
import com.example.salesstysetgps.data.local.RoutePointEntity
import com.example.salesstysetgps.data.local.RouteStopRelation
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow

class RouteRepository(context: Context) {
    private val dao = AppDatabase.get(context).routePointDao()
    private val stopDao = AppDatabase.get(context).stopDao()

//    suspend fun add(point: LocationPoint) {
//        dao.insert(
//            RoutePointEntity(
//                timestampMillis = point.timestampMillis,
//                lat = point.latLng.latitude,
//                lng = point.latLng.longitude
//            )
//        )
//    }

    suspend fun addPoint(routeId: Long, point: LocationPoint) {
        // You need a table for route points
        val routePoint = RoutePointEntity(
            routeId = routeId,
            latitude = point.latLng.latitude,
            longitude = point.latLng.longitude,
            timestamp = point.timestampMillis
        )
        dao.insertRoutePoint(routePoint)
    }

    suspend fun createRoute(route: RouteEntity): Long {
        return dao.insertRoute(route)
    }


    suspend fun linkStopToRoute(routeId: Long, stopId: Long) {
        val relation = RouteStopRelation(
            routeId = routeId,
            stopId = stopId
        )
        dao.insertRouteStopRelation(relation)
        Log.d("ROUTE_DEBUG", "✅ Linked stop $stopId to route $routeId")
    }

    /*suspend fun finalizeRoute(routeId: Long, endTime: Long, stopCount: Int) {
        val route = dao.getRouteById(routeId) ?: return
        val updatedRoute = route.copy(
            endTimeMillis = endTime,
            durationSeconds = (endTime - route.startTimeMillis) / 1000,
            stopCount = stopCount
        )
        dao.updateRoute(updatedRoute)
    }*/
    suspend fun finalizeRoute(routeId: Long, endTime: Long, stopCount: Int, screenshotPath: String? = null) {
        val route = dao.getRouteById(routeId) ?: return
        val updatedRoute = route.copy(
            endTimeMillis = endTime,
            durationSeconds = (endTime - route.startTimeMillis) / 1000,
            stopCount = stopCount,
            screenshotPath = screenshotPath
        )
        dao.updateRoute(updatedRoute)
    }


    fun observeAllRoutes(): Flow<List<RouteEntity>> = dao.observeAllRoutes()

    suspend fun getRouteById(routeId: Long): RouteEntity? = dao.getRouteById(routeId)

    suspend fun getRoutePoints(routeId: Long): List<RoutePointEntity> {
        return dao.getRoutePoints(routeId)
    }


    /*suspend fun getStopsForRoute(routeId: Long): List<StopPoint> {
        val stopEntities = dao.getStopsForRoute(routeId)
        return stopEntities.map { e ->
            StopPoint(
                id = e.id,
                center = LatLng(e.lat, e.lng),
                startTimeMillis = e.startWallTimeMillis,
                endTimeMillis = e.endWallTimeMillis,
                name = e.name,
                locationLabel = e.locationLabel,
                address = e.address,
                phone = e.phone,
                imageUri = e.imageUri,
                timeSpentMinutes = e.durationElapsedMinutes
            )
        }
    }*/

    suspend fun getStopsForRoute(routeId: Long): List<StopPoint> {
        Log.d("LETTER_FLOW", "=== getStopsForRoute($routeId) ===")

        val stopEntities = dao.getStopsForRoute(routeId)
        Log.d("LETTER_FLOW", "Found ${stopEntities.size} stops for route $routeId")

        // Log raw entities
        stopEntities.forEach { entity ->
            Log.d("LETTER_FLOW", "  DB Entity - ID: ${entity.id}, Letter: '${entity.letter}'")
        }

        val result = stopEntities.map { e ->
            StopPoint(
                id = e.id,
                center = LatLng(e.lat, e.lng),
                startTimeMillis = e.startWallTimeMillis,
                endTimeMillis = e.endWallTimeMillis,
                name = e.name,
                locationLabel = e.locationLabel,
                address = e.address,
                phone = e.phone,
                imageUri = e.imageUri,
                timeSpentMinutes = e.durationElapsedMinutes,
                letter = e.letter
            )
        }

        // Log converted StopPoints
        result.forEach { stop ->
            Log.d("LETTER_FLOW", "  Converted StopPoint - ID: ${stop.id}, Letter: '${stop.letter}'")
        }

        return result
    }

    suspend fun getStopsInTimeRange(startTime: Long, endTime: Long): List<StopPoint> {
        val stopEntities = stopDao.getStopsInTimeRange(startTime, endTime)
        return stopEntities.map { e ->
            StopPoint(
                id = e.id,
                center = LatLng(e.lat, e.lng),
                startTimeMillis = e.startWallTimeMillis,
                endTimeMillis = e.endWallTimeMillis,
                name = e.name,
                locationLabel = e.locationLabel,
                address = e.address,
                phone = e.phone,
                imageUri = e.imageUri,
                timeSpentMinutes = e.durationElapsedMinutes
            )
        }
    }

    suspend fun updateRouteScreenshot(routeId: Long, path: String) {
        dao.updateScreenshotPath(routeId, path)
    }

    // Alternative: Update entire route
    suspend fun updateRouteWithScreenshot(routeId: Long, path: String) {
        val route = dao.getRouteById(routeId) ?: return
        val updatedRoute = route.copy(screenshotPath = path)
        dao.updateRoute(updatedRoute)
    }

    // In RouteRepository.kt

    suspend fun getRoutesInTimeRange(startMillis: Long, endMillis: Long): List<RouteEntity> {
        return dao.getRoutesInTimeRange(startMillis, endMillis)
    }



//    suspend fun getBetween(start: Long, end: Long): List<LocationPoint> =
//        dao.getBetween(start, end).map {
//            LocationPoint(LatLng(it.lat, it.lng), it.timestampMillis)
//        }
}