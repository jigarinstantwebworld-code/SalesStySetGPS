package com.example.salesstysetgps.data

import android.util.Log
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
    // Human-readable location resolved from LatLng (non-editable)
    val locationLabel: String? = null,
    // User-provided address (editable)
    val address: String? = null,
    val phone: String? = null,
    val imageUri: String? = null,
    val timeSpentMinutes: Long = 0,
    val letter: String? = null
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

    // In TrackingSessionState.addLocation()
    fun addLocation(point: LocationPoint) {
        Log.d("SessionState", "Adding location: ${point.latLng.latitude}, ${point.latLng.longitude}")
        val currentList = _routePoints.value.toMutableList()
        currentList.add(point)
        _routePoints.value = currentList

        // Log the size after addition
        Log.d("SessionState", "Total points now: ${_routePoints.value.size}")

        // Verify the point was added
        val lastPoint = _routePoints.value.lastOrNull()
        Log.d("SessionState", "Last point: ${lastPoint?.latLng}")
    }

    // In TrackingSessionState class
    fun addStopAndForceUpdate(stop: StopPoint): List<StopPoint> {
        val currentList = _stopPoints.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == stop.id }

        if (existingIndex >= 0) {
            currentList[existingIndex] = stop
        } else {
            currentList.add(stop)
        }

        val newList = currentList.sortedBy { it.startTimeMillis }
        _stopPoints.value = newList
        return newList
    }

    fun addOrUpdateStop(stop: StopPoint) {
        val currentList = _stopPoints.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == stop.id }

        if (existingIndex >= 0) {
            // Update existing stop
            currentList[existingIndex] = stop
        } else {
            // Add new stop
            currentList.add(stop)
        }

        // ALWAYS sort by start time for correct letter order
        _stopPoints.value = currentList.sortedBy { it.startTimeMillis }
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


