package com.example.salesstysetgps.data

import android.content.Context
import android.util.Log
import com.example.salesstysetgps.data.local.RoutePointEntity
import com.example.salesstysetgps.location.RouteRepository
import com.example.salesstysetgps.ui.DailyReportData
import com.example.salesstysetgps.ui.PlacesActivity
import com.example.salesstysetgps.ui.RouteSegment
import com.example.salesstysetgps.ui.RouteWithDetails
import com.example.salesstysetgps.ui.RouteWithStops
import com.example.salesstysetgps.ui.StopWithDetails
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

    /**
     * Get report data for a specific date
     */
    suspend fun getDailyReport(dateMillis: Long): DailyReportData {
        val startOfDay = startOfDayMillis(dateMillis)
        val endOfDay = endOfDayMillis(dateMillis)

        // Get all routes that started on this day
        val routes = routeRepo.getRoutesInTimeRange(startOfDay, endOfDay)

        val routesWithDetails = mutableListOf<RouteWithDetails>()
        var totalStops = 0
        var totalDuration = 0L
        var totalDistance = 0.0

        for (route in routes) {
            // Get stops for this route
            val stops = routeRepo.getStopsForRoute(route.id).sortedBy { it.startTimeMillis }
            val routePoints = routeRepo.getRoutePoints(route.id)

            // Calculate route distance
            val routeDistance = calculateRouteDistance(routePoints)
            totalDistance += routeDistance

            // Calculate stops with details (distances between stops)
            val stopsWithDetails = mutableListOf<StopWithDetails>()
            val segments = mutableListOf<RouteSegment>()

            stops.forEachIndexed { index, stop ->
                val letter = stop.letter ?: ('A'.plus(index)).toString()

                // Calculate distance from previous stop
                val distanceFromPrev = if (index > 0) {
                    calculateDistanceBetweenStops(stops[index - 1], stop)
                } else {
                    null
                }

                // Calculate travel time from previous stop (time between stops)
                val timeFromPrev = if (index > 0 && stops[index - 1].endTimeMillis != null) {
                    (stop.startTimeMillis - stops[index - 1].endTimeMillis!!) / 60000 // in minutes
                } else {
                    null
                }

                stopsWithDetails.add(
                    StopWithDetails(
                        stop = stop,
                        letter = letter,
                        distanceFromPrev = distanceFromPrev,
                        timeFromPrev = timeFromPrev
                    )
                )
            }

            // Calculate journey segments between stops
            for (i in 0 until stops.size - 1) {
                val fromStop = stops[i]
                val toStop = stops[i + 1]

                // Get route points between these stops (based on timestamps)
                val segmentPoints = if (fromStop.endTimeMillis != null && toStop.startTimeMillis != null) {
                    routePoints.filter { point ->
                        point.timestamp in fromStop.endTimeMillis!!..toStop.startTimeMillis
                    }
                } else {
                    emptyList()
                }

                val segmentDistance = calculateRouteDistance(segmentPoints)
                val segmentDuration = if (fromStop.endTimeMillis != null && toStop.startTimeMillis != null) {
                    (toStop.startTimeMillis - fromStop.endTimeMillis!!) / 60000 // in minutes
                } else {
                    0
                }

                segments.add(
                    RouteSegment(
                        fromStop = "${stopsWithDetails[i].letter}. ${fromStop.name ?: "Unnamed"}",
                        toStop = "${stopsWithDetails[i + 1].letter}. ${toStop.name ?: "Unnamed"}",
                        distance = segmentDistance,
                        duration = segmentDuration,
                        startTime = fromStop.endTimeMillis ?: 0,
                        endTime = toStop.startTimeMillis ?: 0
                    )
                )
            }

            // Update totals
            totalStops += stops.size
            route.durationSeconds?.let { totalDuration += it / 60 }

            // Add route with details
            routesWithDetails.add(
                RouteWithDetails(
                    route = route,
                    stops = stopsWithDetails,
                    routeDistance = routeDistance,
                    segments = segments,
                    routePoints = routePoints // Add route points
                )
            )

            Log.d("REPORT_DEBUG", "Route ${route.id}: ${stops.size} stops, ${String.format("%.2f", routeDistance)} km")
        }

        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())

        return DailyReportData(
            date = dateFormat.format(Date(dateMillis)),
            routes = routesWithDetails.sortedBy { it.route.startTimeMillis },
            totalRoutes = routes.size,
            totalStops = totalStops,
            totalDuration = totalDuration,
            totalDistance = totalDistance
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