package com.example.salesstysetgps.data

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LocationPoint(
    val latLng: LatLng,
    val timestampMillis: Long
)

data class StopPoint(
    val id: Long = System.currentTimeMillis(),
    val center: LatLng,
    val startTimeMillis: Long,
    val endTimeMillis: Long? = null,
    val name: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val imageUri: String? = null,
    val timeSpentMinutes: Long = 0
)

class TrackingSessionState {
    private val _routePoints = MutableStateFlow<List<LocationPoint>>(emptyList())
    val routePoints: StateFlow<List<LocationPoint>> = _routePoints

    private val _stopPoints = MutableStateFlow<List<StopPoint>>(emptyList())
    val stopPoints: StateFlow<List<StopPoint>> = _stopPoints

    fun reset() {
        _routePoints.value = emptyList()
        _stopPoints.value = emptyList()
    }

    fun addLocation(point: LocationPoint) {
        _routePoints.value = _routePoints.value + point
    }

    fun addOrUpdateStop(stop: StopPoint) {
        val existing = _stopPoints.value
        if (existing.isEmpty()) {
            _stopPoints.value = listOf(stop)
        } else {
            // Replace by id if present as last item; otherwise append
            val updated = existing.map { if (it.id == stop.id) stop else it }
            _stopPoints.value = if (updated == existing){
                existing + stop
            } else{
                updated
            }
        }
    }

    fun updateStopName(stopId: Long, newName: String) {
        _stopPoints.value = _stopPoints.value.map { stop ->
            if (stop.id == stopId) stop.copy(name = newName) else stop
        }
    }

    fun getCurrentStopTime(): Long {
        val lastStop = _stopPoints.value.lastOrNull()
        return if (lastStop != null && lastStop.endTimeMillis == null) {
            (System.currentTimeMillis() - lastStop.startTimeMillis) / 60000 // minutes
        } else 0
    }
}


