package com.styset.sales.app.data

import android.content.Context
import android.util.Log
import com.styset.sales.app.data.local.RoutePointEntity
import com.styset.sales.app.location.RouteRepository
import com.styset.sales.app.repository.SyncRepository
import com.styset.sales.app.ui.DailyReportData
import com.styset.sales.app.ui.RouteSegment
import com.styset.sales.app.ui.RouteWithDetails
import com.styset.sales.app.ui.StopWithDetails
import com.styset.sales.app.ui.SyncRecordDisplay
import com.google.android.gms.maps.model.LatLng
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class ReportRepository(private val context: Context) {

    private val routeRepo = RouteRepository(context)
    private val stopRepo = StopRepository(context)
    private val syncRepo = SyncRepository(context)  // ✅ Add this

    /**
     * Get report data for a specific date
     */
    suspend fun getDailyReport(dateMillis: Long): DailyReportData {
        val startOfDay = startOfDayMillis(dateMillis)
        val endOfDay = endOfDayMillis(dateMillis)

        val routes = routeRepo.getRoutesInTimeRange(startOfDay, endOfDay)

        val routesWithDetails = mutableListOf<RouteWithDetails>()
        var totalStops = 0
        var totalDuration = 0L
        var totalDistance = 0.0

        val latestSyncRecord  = syncRepo.getLatestSuccessfulSyncRecord()
        val dateFormat = SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.getDefault())

        val syncHistory = if (latestSyncRecord != null) {
            listOf(
                SyncRecordDisplay(
                    apiName = latestSyncRecord.apiName,
                    lastSyncedTime = dateFormat.format(Date(latestSyncRecord.lastSyncedTime)),
                    status = latestSyncRecord.status
                )
            )
        } else {
            emptyList()
        }

        for (route in routes) {
            // ✅ Get stops - already deduplicated by your DAO
            val stopsFromDb = routeRepo.getStopsForRoute(route.id)

            // ✅ Second layer: Deduplicate by ID (just in case)
            val stops = stopsFromDb.distinctBy { it.id }.sortedBy { it.startTimeMillis }

            // ✅ Third layer: Log if duplicates found
            if (stopsFromDb.size != stops.size) {
                Log.w("REPORT_DEBUG", "Route ${route.id}: Removed ${stopsFromDb.size - stops.size} duplicates")
            }
            val letterGroups = stops.groupBy { it.letter }
            letterGroups.filter { it.value.size > 1 }.forEach { (letter, duplicates) ->
                Log.w("REPORT_DEBUG", "⚠️ Same letter '$letter' appears ${duplicates.size} times in route ${route.id}")
                duplicates.forEach { stop ->
                    Log.w("REPORT_DEBUG", "   Stop ID: ${stop.id}, Time: ${stop.startTimeMillis}, Location: ${stop.center}")
                }
            }

            val routePoints = routeRepo.getRoutePoints(route.id)
            val routeDistance = calculateRouteDistance(routePoints)
            totalDistance += routeDistance

            // ✅ Create stops with details (NO segments mixed in)
            val stopsWithDetails = mutableListOf<StopWithDetails>()

            stops.forEachIndexed { index, stop ->
                val letter = stop.letter ?: ('A'.plus(index)).toString()

                // Calculate travel from previous stop
                val (timeFromPrev, distanceFromPrev) = if (index > 0) {
                    val prevStop = stops[index - 1]
                    val travelTime = if (prevStop.endTimeMillis != null && stop.startTimeMillis != null) {
                        val minutes = (stop.startTimeMillis - prevStop.endTimeMillis!!) / 60000
                        if (minutes < 0) 0 else minutes  // ✅ Never negative
                    } else {
                        0
                    }
                    val travelDistance = calculateDistanceBetweenStops(prevStop, stop)
                    Pair(travelTime, travelDistance)
                } else {
                    Pair(null, null)
                }

                stopsWithDetails.add(
                    StopWithDetails(
                        stop = stop,
                        letter = letter,
                        timeFromPrev = timeFromPrev,
                        distanceFromPrev = distanceFromPrev
                    )
                )

                totalStops++
                totalDuration += stop.timeSpentMinutes
            }

            // ✅ Create segments SEPARATELY (for journey display, not as stops)
            val segments = mutableListOf<RouteSegment>()
            for (i in 0 until stops.size - 1) {
                val fromStop = stops[i]
                val toStop = stops[i + 1]

                val travelTime = if (fromStop.endTimeMillis != null && toStop.startTimeMillis != null) {
                    (toStop.startTimeMillis - fromStop.endTimeMillis!!) / 60000
                } else {
                    0
                }

                val travelDistance = calculateDistanceBetweenStops(fromStop, toStop)

                segments.add(
                    RouteSegment(
                        fromStop = "${stopsWithDetails[i].letter}. ${fromStop.name ?: "Unnamed"}",
                        toStop = "${stopsWithDetails[i + 1].letter}. ${toStop.name ?: "Unnamed"}",
                        distance = travelDistance,
                        duration = if (travelTime < 0) 0 else travelTime,
                        startTime = fromStop.endTimeMillis ?: fromStop.startTimeMillis,
                        endTime = toStop.startTimeMillis ?: toStop.startTimeMillis
                    )
                )
            }

            routesWithDetails.add(
                RouteWithDetails(
                    route = route,
                    stops = stopsWithDetails,  // ✅ Only stops here
                    routeDistance = routeDistance,
                    segments = segments,  // ✅ Travel segments separate
                    routePoints = routePoints
                )
            )

            Log.d("REPORT_DEBUG", "Route ${route.id}: ${stops.size} stops, ${segments.size} segments")
        }

        return DailyReportData(
            date = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date(dateMillis)),
            routes = routesWithDetails.sortedBy { it.route.startTimeMillis },
            totalRoutes = routes.size,
            totalStops = totalStops,
            totalDuration = totalDuration,
            totalDistance = totalDistance,
            syncHistory = syncHistory  // ✅ Add sync history
        )
    }

    private fun calculateDistanceBetweenStops(stop1: StopPoint, stop2: StopPoint): Double {
        return distanceBetween(stop1.center, stop2.center) / 1000.0
    }



    private fun calculateRouteDistance(points: List<RoutePointEntity>): Double {
        if (points.size < 2) return 0.0

        var total = 0.0
        for (i in 0 until points.size - 1) {
            val p1 = LatLng(points[i].latitude, points[i].longitude)
            val p2 = LatLng(points[i + 1].latitude, points[i + 1].longitude)
            total += distanceBetween(p1, p2)
        }
        return total / 1000.0 // Convert to km
    }

    private fun distanceBetween(p1: LatLng, p2: LatLng): Double {
        val R = 6371e3
        val φ1 = Math.toRadians(p1.latitude)
        val φ2 = Math.toRadians(p2.latitude)
        val Δφ = Math.toRadians(p2.latitude - p1.latitude)
        val Δλ = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(Δφ / 2) * sin(Δφ / 2) +
                cos(φ1) * cos(φ2) *
                sin(Δλ / 2) * sin(Δλ / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }

    private fun startOfDayMillis(timeMillis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = timeMillis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun endOfDayMillis(timeMillis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = timeMillis
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        c.set(Calendar.MILLISECOND, 999)
        return c.timeInMillis
    }
}