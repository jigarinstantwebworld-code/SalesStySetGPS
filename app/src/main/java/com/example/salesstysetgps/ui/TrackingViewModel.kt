package com.example.salesstysetgps.ui

import android.app.Application
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.salesstysetgps.data.LocationPoint
import com.example.salesstysetgps.data.StopPoint
import com.example.salesstysetgps.data.TrackingSessionState
import com.example.salesstysetgps.data.StopRepository
import com.example.salesstysetgps.location.LocationForegroundService
import com.example.salesstysetgps.location.LocationRepository
import com.example.salesstysetgps.util.GeoUtils
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.Locale
import android.location.Geocoder

class TrackingViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = LocationRepository(app.applicationContext)
    private val stopRepo = StopRepository(app.applicationContext)
    private val sessionState = TrackingSessionState()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    val routePoints = sessionState.routePoints
    val stopPoints = sessionState.stopPoints

    private var collectionJob: Job? = null
    private val movingSpeedThresholdKmh = 10f  // ignore stop detection above 10 km/h


    // Stop detection state
    private var stopCenter: LatLng? = null
    private var stopStartTime: Long? = null // wall time for display only
    private var stopStartElapsed: Long? = null // monotonic clock for duration
    private val stopRadiusMeters = 15f
    private val stopThresholdMinutes = 1L // threshold (minutes)
    private var isCurrentlyStopped = false
    private var currentStopId: Long? = null
    private var thresholdToastShown = false

    // Session timer (wall clock)
    private var sessionStartTimeMillis: Long? = null

    init {
        // Load persisted stops and reflect into session state on app start
        viewModelScope.launch {
            stopRepo.observeStops().collect { persisted ->
                persisted.forEach { sessionState.addOrUpdateStop(it) }
            }
        }
    }

    fun startTracking() {
        if (_isTracking.value) return
        _isTracking.value = true
        if (sessionStartTimeMillis == null){
            sessionStartTimeMillis = System.currentTimeMillis()
        }
        //Foreground service starts:
        startService()

        collectionJob?.cancel()
        //Now the app starts listening to GPS continuously.
        collectionJob = viewModelScope.launch {
            repository.locationUpdates().collect { location ->
                handleLocation(location)
            }
        }
    }

    fun stopTracking() {
        if (!_isTracking.value) return
        _isTracking.value = false
        collectionJob?.cancel()
        stopService()
        // finalize any open stop (close it on stop)
        val center = stopCenter
        val startWall = stopStartTime
        val startElapsedLocal = stopStartElapsed
        val id = currentStopId
        if (center != null && startWall != null && isCurrentlyStopped && id != null && startElapsedLocal != null) {
            val nowWall = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()
            val minutes = ((nowElapsed - startElapsedLocal) / 60000)
            val stop = StopPoint(id = id, center = center, startTimeMillis = startWall, endTimeMillis = nowWall, timeSpentMinutes = minutes)
            sessionState.addOrUpdateStop(stop)
            viewModelScope.launch { stopRepo.upsertStop(stop, startElapsedLocal, nowElapsed) }
            Toast.makeText(getApplication(), "Stop ended: ${minutes} min", Toast.LENGTH_SHORT).show()
            // Try to resolve a human-readable location name for this stop
            resolveLocationNameAsync(stop)
        }
        stopCenter = null
        stopStartTime = null
        stopStartElapsed = null
        isCurrentlyStopped = false
        currentStopId = null
        thresholdToastShown = false
    }

    fun resetSession() {
        sessionState.reset()
        stopCenter = null
        stopStartTime = null
        stopStartElapsed = null
        isCurrentlyStopped = false
        sessionStartTimeMillis = null
        currentStopId = null
        thresholdToastShown = false
//        viewModelScope.launch { stopRepo.clearAll() }
    }

    private fun startService() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, LocationForegroundService::class.java)
        ctx.startForegroundService(intent)
    }

    private fun stopService() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, LocationForegroundService::class.java)
        ctx.stopService(intent)
    }

    private fun handleLocation(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        sessionState.addLocation(LocationPoint(latLng, System.currentTimeMillis()))

        val currentCenter = stopCenter
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        
        if (currentCenter == null) {
            // Start tracking a potential stop
            stopCenter = latLng
            stopStartTime = nowWall
            stopStartElapsed = nowElapsed
            isCurrentlyStopped = false
            currentStopId = null
            thresholdToastShown = false
        } else {
            val distance = GeoUtils.distanceMeters(currentCenter, latLng)
            val elapsedMinutes = ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 60000)
            
            if (distance <= stopRadiusMeters) {
                // Still within stop area
                if (elapsedMinutes >= stopThresholdMinutes && !isCurrentlyStopped) {
                    // Threshold reached - mark as a stop and assign a stable id
                    currentStopId = System.currentTimeMillis()
                    val stop = StopPoint(
                        id = currentStopId!!,
                        center = currentCenter,
                        startTimeMillis = stopStartTime ?: nowWall,
                        endTimeMillis = null,
                        timeSpentMinutes = elapsedMinutes
                    )
                    sessionState.addOrUpdateStop(stop)
                    val startElapsedLocal = stopStartElapsed ?: nowElapsed
                    viewModelScope.launch { stopRepo.upsertStop(stop, startElapsedLocal, null) }
                    // Resolve a human-readable location name once the stop is detected
                    resolveLocationNameAsync(stop)
                    isCurrentlyStopped = true
                    if (!thresholdToastShown) {
                        Toast.makeText(getApplication(), "Stop started (>= ${stopThresholdMinutes} min)", Toast.LENGTH_SHORT).show()
                        thresholdToastShown = true
                    }
                } else if (isCurrentlyStopped) {
                    // Update ongoing stop time, reuse same id
                    val stop = StopPoint(
                        id = currentStopId!!,
                        center = currentCenter,
                        startTimeMillis = stopStartTime ?: nowWall,
                        endTimeMillis = null,
                        timeSpentMinutes = elapsedMinutes
                    )
                    sessionState.addOrUpdateStop(stop)
                    val startElapsedLocal = stopStartElapsed ?: nowElapsed
                    viewModelScope.launch { stopRepo.upsertStop(stop, startElapsedLocal, null) }
                }
            } else {
                // Moved out of stop area
                if (isCurrentlyStopped && currentStopId != null) {
                    val minutes = ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 60000)
                    val stop = StopPoint(
                        id = currentStopId!!,
                        center = currentCenter,
                        startTimeMillis = stopStartTime ?: nowWall,
                        endTimeMillis = nowWall,
                        timeSpentMinutes = minutes
                    )
                    sessionState.addOrUpdateStop(stop)
                    val startElapsedLocal = stopStartElapsed ?: nowElapsed
                    viewModelScope.launch { stopRepo.upsertStop(stop, startElapsedLocal, nowElapsed) }
                    Toast.makeText(getApplication(), "Stop ended: ${minutes} min", Toast.LENGTH_SHORT).show()
                    // Also resolve location name for this finalized stop
                    resolveLocationNameAsync(stop)
                }
                // Start tracking new potential stop
                stopCenter = latLng
                stopStartTime = nowWall
                stopStartElapsed = nowElapsed
                isCurrentlyStopped = false
                currentStopId = null
                thresholdToastShown = false
            }
        }
    }

    /**
     * Resolve a human-readable location name (\"latLng name\") for a stop using reverse geocoding.
     * This fills the stop's address field so the UI / PDF can show a name instead of raw lat,lng.
     */
    private fun resolveLocationNameAsync(stop: StopPoint) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // If we already have an address for this stop in memory, don't override it
                val current = sessionState.stopPoints.value.firstOrNull { it.id == stop.id }
                if (current != null && !current.address.isNullOrBlank()) {
                    return@launch
                }

                val ctx = getApplication<Application>()
                val geocoder = Geocoder(ctx, Locale.getDefault())
                val results = geocoder.getFromLocation(stop.center.latitude, stop.center.longitude, 1)
                val addressLine = results?.firstOrNull()?.getAddressLine(0)

                if (!addressLine.isNullOrBlank()) {
                    val base = current ?: stop
                    val updated = base.copy(address = addressLine)
                    // Update in-memory session state
                    sessionState.addOrUpdateStop(updated)
                    // Persist back to DB (reuse the simple upsert pattern used elsewhere)
                    stopRepo.upsertStop(updated, 0, updated.endTimeMillis?.let { 0L })
                }
            } catch (_: Exception) {
                // Ignore geocoder failures; we'll just show raw lat,lng
            }
        }
    }

    fun setStopName(stopId: Long, name: String) {
        sessionState.updateStopName(stopId, name)
        viewModelScope.launch { stopRepo.updateStopName(stopId, name) }
    }

    fun getCurrentStopTime(): Long {
        return sessionState.getCurrentStopTime()
    }

    fun getCompletedStops(): List<StopPoint> {
        return sessionState.stopPoints.value.filter { it.endTimeMillis != null }
    }

    // Merge by name (only for named stops). Unnamed stops remain individual entries. 10:00 AM → Shop A (15 min)
    //2:00 PM → Shop A (20 min)
    //
    //Two stops stored separately.It merges:
    //
    //Shop A → Total 35 min
    fun getCompletedStopsMergedByName(): List<StopPoint> {
        val completed = getCompletedStops()
        val named = completed.filter { !it.name.isNullOrBlank() }
            .groupBy { it.name!!.trim() }
            .map { (name, list) ->
                val totalMinutes = list.sumOf { it.timeSpentMinutes }
                val first = list.minByOrNull { it.startTimeMillis }!!
                val last = list.maxByOrNull { it.endTimeMillis ?: it.startTimeMillis }!!
                // Preserve address, phone, and imageUri from the first stop (or find one that has them)
                val stopWithDetails = list.firstOrNull { 
                    !it.address.isNullOrBlank() || !it.phone.isNullOrBlank() || !it.imageUri.isNullOrBlank() 
                } ?: first
                StopPoint(
                    id = first.id,
                    center = first.center,
                    startTimeMillis = first.startTimeMillis,
                    endTimeMillis = last.endTimeMillis,
                    name = name,
                    address = stopWithDetails.address,
                    phone = stopWithDetails.phone,
                    imageUri = stopWithDetails.imageUri,
                    timeSpentMinutes = totalMinutes
                )
            }
        val unnamed = completed.filter { it.name.isNullOrBlank() }
        return named + unnamed
    }

    fun getSessionElapsedSeconds(): Long {
        val start = sessionStartTimeMillis
        return if (_isTracking.value && start != null) {
            (System.currentTimeMillis() - start) / 1000
        } else 0
    }

    fun updateStopDetails(id: Long, name: String, address: String?, phone: String?, imageUri: String?) {
        // Update in-memory
        val existing = stopPoints.value.firstOrNull { it.id == id } ?: return
        val updated = existing.copy(name = name, address = address, phone = phone, imageUri = imageUri)
        sessionState.addOrUpdateStop(updated)
        // Persist
        viewModelScope.launch {
            // Upsert with same duration and times
            stopRepo.upsertStop(updated, 0, updated.endTimeMillis?.let { 0L })
        }
    }
}


