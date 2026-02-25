package com.example.salesstysetgps.data

import android.content.Context
import android.os.SystemClock
import com.example.salesstysetgps.data.local.AppDatabase
import com.example.salesstysetgps.data.local.StopEntity
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StopRepository(context: Context) {
    private val dao = AppDatabase.get(context).stopDao()

    fun observeStops(): Flow<List<StopPoint>> = dao.observeStops().map { list ->
        list.map { e ->
            StopPoint(
                id = e.id,
                center = LatLng(e.lat, e.lng),
                startTimeMillis = e.startWallTimeMillis,
                endTimeMillis = e.endWallTimeMillis,
                name = e.name,
                address = e.address,
                phone = e.phone,
                imageUri = e.imageUri,
                timeSpentMinutes = e.durationElapsedMinutes
            )
        }
    }

    suspend fun upsertStop(stop: StopPoint, startElapsed: Long, endElapsed: Long?) {
        val e = StopEntity(
            id = stop.id,
            name = stop.name,
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

    suspend fun clearAll() {
        dao.clearAll()
    }
}


