package com.example.salesstysetgps.data.local



import android.util.Log
import com.example.salesstysetgps.data.StopPoint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MapSnapshotHelper(private val context: Context) {

    suspend fun getStaticMapBitmap(
        routePoints: List<RoutePointEntity>,
        stops: List<StopPoint>
    ): Bitmap? = withContext(Dispatchers.IO) {

        if (routePoints.isEmpty()) {
            Log.e("MAP_SNAPSHOT", "No route points")
            return@withContext null
        }

        try {
            // Use OpenStreetMap (more reliable)
            val url = buildOpenStreetMapUrl(routePoints, stops)
            Log.d("MAP_SNAPSHOT", "Loading URL: $url")

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "SalesStySetGPS-App/1.0")
            connection.connect()

            val responseCode = connection.responseCode
            Log.d("MAP_SNAPSHOT", "Response code: $responseCode")

            if (responseCode == 200) {
                val inputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (bitmap != null) {
                    Log.d("MAP_SNAPSHOT", "Bitmap loaded: ${bitmap.width}x${bitmap.height}")
                } else {
                    Log.e("MAP_SNAPSHOT", "Bitmap decoding returned null")
                }

                return@withContext bitmap
            } else {
                val errorStream = connection.errorStream
                val errorText = errorStream?.bufferedReader()?.use { it.readText() }
                Log.e("MAP_SNAPSHOT", "Error response ($responseCode): $errorText")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("MAP_SNAPSHOT", "Error loading static map", e)
            return@withContext null
        }
    }

    /**
     * Build OpenStreetMap static map URL (most reliable free option)
     */
    fun buildOpenStreetMapUrl(
        routePoints: List<RoutePointEntity>,
        stops: List<StopPoint>
    ): String {
        if (routePoints.isEmpty()) return ""

        val latLngs = routePoints.map { LatLng(it.latitude, it.longitude) }
        val bounds = calculateBounds(latLngs)
        val centerLat = (bounds.northeast.latitude + bounds.southwest.latitude) / 2
        val centerLon = (bounds.northeast.longitude + bounds.southwest.longitude) / 2
        val zoom = calculateZoom(bounds)

        return "https://staticmap.openstreetmap.de/staticmap.php?" +
                "center=$centerLat,$centerLon&zoom=$zoom&size=600x300&maptype=mapnik"
    }

    private fun calculateBounds(latLngs: List<LatLng>): LatLngBounds {
        val builder = LatLngBounds.builder()
        latLngs.forEach { builder.include(it) }
        return builder.build()
    }

    private fun calculateZoom(bounds: LatLngBounds): Int {
        val latDiff = Math.abs(bounds.northeast.latitude - bounds.southwest.latitude)
        val lonDiff = Math.abs(bounds.northeast.longitude - bounds.southwest.longitude)
        val maxDiff = maxOf(latDiff, lonDiff)

        return when {
            maxDiff > 0.1 -> 12
            maxDiff > 0.05 -> 13
            maxDiff > 0.02 -> 14
            maxDiff > 0.01 -> 15
            maxDiff > 0.005 -> 16
            else -> 17
        }
    }
}