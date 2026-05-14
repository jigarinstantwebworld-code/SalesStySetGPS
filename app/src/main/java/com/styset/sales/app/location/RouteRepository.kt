package com.styset.sales.app.location

import android.content.Context
import android.util.Log
import com.styset.sales.app.data.LocationPoint
import com.styset.sales.app.data.StopPoint
import com.styset.sales.app.data.local.AppDatabase
import com.styset.sales.app.data.local.RouteEntity
import com.styset.sales.app.data.local.RoutePointEntity
import com.styset.sales.app.data.local.RouteStopRelation
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow

class RouteRepository(context: Context) {
    private val dao = AppDatabase.Companion.get(context).routePointDao()
    private val stopDao = AppDatabase.Companion.get(context).stopDao()

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
            Log.d("LETTER_FLOW", "  DB Entity - ID: ${entity.id}, Letter: '${entity.letter}', Name: '${entity.name}'")
        }

        // ✅ LAYER 1: Deduplicate by ID (in case same stop is linked multiple times)
        val uniqueById = stopEntities.distinctBy { it.id }

        if (stopEntities.size != uniqueById.size) {
            Log.w("LETTER_FLOW", "⚠️ Removed ${stopEntities.size - uniqueById.size} duplicate stops by ID")
        }

        // ✅ LAYER 2: Deduplicate by time + location (in case exact duplicate stops exist)
        val uniqueByTimeAndLocation = uniqueById.distinctBy { entity ->
            val timeSlot = entity.startWallTimeMillis / (1000 * 60 * 5) // 5-minute time slot
            val lat = String.format("%.3f", entity.lat)
            val lng = String.format("%.3f", entity.lng)
            "$timeSlot-$lat-$lng"
        }

        if (uniqueById.size != uniqueByTimeAndLocation.size) {
            Log.w("LETTER_FLOW", "⚠️ Removed ${uniqueById.size - uniqueByTimeAndLocation.size} duplicate stops by time/location")
        }

        // ✅ LAYER 3: Filter out invalid stops
        val validStops = uniqueByTimeAndLocation.filter { entity ->
            entity.startWallTimeMillis > 0 &&
                    entity.lat != 0.0 &&
                    entity.lng != 0.0 &&
                    (entity.durationElapsedMinutes >= 0 || entity.endWallTimeMillis == null)
        }

        if (uniqueByTimeAndLocation.size != validStops.size) {
            Log.w("LETTER_FLOW", "⚠️ Filtered out ${uniqueByTimeAndLocation.size - validStops.size} invalid stops")
        }

        // ✅ LAYER 4: Sort by time
        val sortedStops = validStops.sortedBy { it.startWallTimeMillis }

        val result = sortedStops.map { e ->
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

        Log.d("LETTER_FLOW", "✅ Final result: ${result.size} stops (original: ${stopEntities.size})")

        // Log final stops with their letters
        result.forEachIndexed { index, stop ->
            Log.d("LETTER_FLOW", "  Final Stop $index - ID: ${stop.id}, Letter: '${stop.letter}', Name: '${stop.name}'")
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

    suspend fun getOngoingRoute(): RouteEntity? {
        return dao.getOngoingRoute()
    }

    suspend fun getAllRoutes(): List<RouteEntity> {
        return dao.getAllRoutes()
    }



    suspend fun createOrGetOngoingRoute(): RouteEntity {
        val ongoingRoute = getOngoingRoute()
        if (ongoingRoute != null) {
            return ongoingRoute
        }
        val routeId = createRoute(
            RouteEntity(
                startTimeMillis = System.currentTimeMillis(),
                stopCount = 0
            )
        )
        return dao.getRouteById(routeId) ?: throw Exception("Failed to create route")
    }



//    suspend fun getBetween(start: Long, end: Long): List<LocationPoint> =
//        dao.getBetween(start, end).map {
//            LocationPoint(LatLng(it.lat, it.lng), it.timestampMillis)
//        }
}