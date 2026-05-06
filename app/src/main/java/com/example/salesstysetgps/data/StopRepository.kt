package com.example.salesstysetgps.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.salesstysetgps.data.local.AppDatabase
import com.example.salesstysetgps.data.local.StopEntity
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class StopRepository(context: Context) {
    private val dao = AppDatabase.get(context).stopDao()

    fun observeStops(): Flow<List<StopPoint>> = dao.observeStops().map { list ->
        list.map { e ->
            StopPoint(
                letter = e.letter,
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

    suspend fun upsertStop(stop: StopPoint, startElapsed: Long, endElapsed: Long?) {
        val e = StopEntity(
            letter = stop.letter,
            id = stop.id,
            name = stop.name,
            locationLabel = stop.locationLabel,
            address = stop.address,
            phone = stop.phone,
            imageUri = stop.imageUri,
            lat = stop.center.latitude,
            lng = stop.center.longitude,
            startWallTimeMillis = stop.startTimeMillis,
            endWallTimeMillis = stop.endTimeMillis,
            durationElapsedMinutes = stop.timeSpentMinutes,
            startElapsedRealtimeMillis = startElapsed,
            endElapsedRealtimeMillis = endElapsed
        )
        dao.upsert(e)
    }

    suspend fun updateStopName(id: Long, name: String) { dao.updateName(id, name) }


//    suspend fun getStopsInTimeRange(startTime: Long, endTime: Long): List<StopPoint> {
//        val stopEntities = dao.getStopsInTimeRange(startTime, endTime)
//        return stopEntities.map { e ->
//            StopPoint(
//                letter = e.letter,
//                id = e.id,
//                center = LatLng(e.lat, e.lng),
//                startTimeMillis = e.startWallTimeMillis,
//                endTimeMillis = e.endWallTimeMillis,
//                name = e.name,
//                locationLabel = e.locationLabel,
//                address = e.address,
//                phone = e.phone,
//                imageUri = e.imageUri,
//                timeSpentMinutes = e.durationElapsedMinutes
//            )
//        }
//    }

    suspend fun getStopsInTimeRange(startTime: Long, endTime: Long): List<StopPoint> {
        Log.d("LETTER_FLOW", "=== getStopsInTimeRange ===")
        Log.d("LETTER_FLOW", "Start: $startTime, End: $endTime")

        val stopEntities = dao.getStopsInTimeRange(startTime, endTime)
        Log.d("LETTER_FLOW", "Found ${stopEntities.size} entities in database")

        // Log raw entities from database
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

    suspend fun getStopById(stopId: Long): StopPoint? {
        return withContext(Dispatchers.IO) {
            try {
                // You'll need to add this query to your StopDao
                val entity = dao.getStopById(stopId)
                entity?.let { e ->
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
            } catch (e: Exception) {
                Log.e("StopRepository", "Error getting stop by ID", e)
                null
            }
        }
    }

    suspend fun getOngoingStops() : List<StopEntity> {
      return  dao.getOngoingStops()
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}


