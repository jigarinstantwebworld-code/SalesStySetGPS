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
import android.util.Log
import com.example.salesstysetgps.location.RouteRepository
import com.google.maps.android.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

class TrackingViewModel(app: Application) : AndroidViewModel(app) {

    enum class MovementState {
        MOVING,
        POSSIBLE_STOP,
        STOPPED

    }

    private val TEST_MODE = true
    private val TEST_STOP_SECONDS = 4

    private val _startLocation = MutableStateFlow<LatLng?>(null)
    val startLocation: StateFlow<LatLng?> = _startLocation.asStateFlow()

    private var isFirstLocationOfSession = true

    private var hasActiveSession = false


    private var movementState = MovementState.MOVING
    private var pointsInsideRadius = 0
    private val exitRadiusMeters = 70f
    private val walkingMinSpeed = 0.5f
    private val walkingMaxSpeed = 6f
    private val trafficSpeedMin = 2f      // 2 km/h
    private val trafficSpeedMax = 12f     // 12 km/h
    private val trafficRadiusMeters = 50f



    private val repository = LocationRepository(app.applicationContext)
        private val stopRepo = StopRepository(app.applicationContext)
        private val sessionState = TrackingSessionState()

        private val routeRepo = RouteRepository(app.applicationContext)

        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

        val routePoints = sessionState.routePoints
        val stopPoints = sessionState.stopPoints

        private var collectionJob: Job? = null
        private val movingSpeedThresholdKmh = 15f  // ignore stop detection above 12 km/h  (6 for testing)


        // Stop detection state
        private var stopCenter: LatLng? = null
        private var stopStartTime: Long? = null // wall time for display only
        private var stopStartElapsed: Long? = null // monotonic clock for duration
        private val stopRadiusMeters = 30f // 40 to testing 25 is set for production
        private val stopThresholdMinutes = 1L // threshold (minutes) 1 for test
        private var isCurrentlyStopped = false
        private var currentStopId: Long? = null
        private var thresholdToastShown = false

        private val minAccuracyMeters = 100f // 10 for testing 30 for production
        // check last 5 Speed Movement
        private val speedWindowSize = 5 // 3 for testing 5 for product
        private val requiredMovingConfirmations = 3 // 2 for testing 3 for production

        private val speedHistory = ArrayDeque<Float>()
        private var consecutiveMovingReadings = 0

        // Session timer (wall clock)
        private var sessionStartTimeMillis: Long? = null


    private val maxJumpMeters = 120f          // ignore unrealistic GPS jumps
    private val walkingSpeedThreshold = 8f   // slow movement filter
    private val minStopPoints = 4            // minimum points before creating stop (4 new location for conform stop user has stop)

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

        // Reset for new session
//        isFirstLocationOfSession = true

        if (!hasActiveSession) {
            isFirstLocationOfSession = true
            hasActiveSession = true
            Log.d("StartMarker", "🆕 New session starting - will capture start point")
        } else {
            // This is just resuming after stop - DON'T capture new start point
            isFirstLocationOfSession = false
            Log.d("StartMarker", "↪️ Resuming existing session - keeping original start point")
        }

        startService()

        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            repository.locationUpdates().collect { location ->
                Log.d("LocationFlow", "Received location in flow: $location")

                // Capture first location of THIS session as start point
                if (isFirstLocationOfSession) {
                    val startLatLng = LatLng(location.latitude, location.longitude)
                    _startLocation.value = startLatLng
                    isFirstLocationOfSession = false
                    Log.d("StartMarker", "📍 NEW SESSION start location: $startLatLng")
                }

                val speedKmh = location.speed * 3.6f
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "📍 Speed: ${speedKmh.toInt()} km/h",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                handleLocation(location)
            }
        }
    }

    fun getStopsWithSequence(): List<Pair<String, StopPoint>> {
        return getCompletedStops()
            .sortedWith(compareBy({ it.startTimeMillis }, { it.id }))
            .mapIndexed { index, stop ->
                val letter = ('A' + index).toString()
                letter to stop
            }
    }

    fun getStopLetter(stopId: Long): String? {
        return getCompletedStops()
            .sortedBy { it.startTimeMillis }
            .mapIndexed { index, stop -> stop.id to ('A' + index).toString() }
            .firstOrNull { it.first == stopId }?.second
    }


    fun stopTracking() {
        if (!_isTracking.value) return
        _isTracking.value = false
        collectionJob?.cancel()
        collectionJob = null

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

        // Clear start location for next session
        _startLocation.value = null
        isFirstLocationOfSession = true
        hasActiveSession = false

        Log.d("StartMarker", "🔄 Session reset - ready for new start marker")
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
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val latLng = LatLng(location.latitude, location.longitude)
        val speedKmh = location.speed * 3.6f
        val center = stopCenter


        /*if (TEST_MODE) {
            // =====================================================
            // FIRST LOCATION - START MONITORING
            // =====================================================
            if (stopCenter == null) {
                stopCenter = latLng
                stopStartTime = nowWall
                stopStartElapsed = nowElapsed
                isCurrentlyStopped = false
                pointsInsideRadius = 1

                Log.d("StopDebug", "🧪 TEST MODE: Monitoring stop...")
                showUserToast("🧪 TEST MODE: Waiting 10 seconds")

                return
            }

            val distance = GeoUtils.distanceMeters(stopCenter!!, latLng)
            val elapsedSeconds = (nowElapsed - (stopStartElapsed ?: nowElapsed)) / 1000

            Log.d("StopDebug", "🧪 TEST MODE elapsed: $elapsedSeconds sec, distance: $distance m")

            // =====================================================
            // CREATE STOP AFTER 10 SECONDS (if still in same area)
            // =====================================================
            if (!isCurrentlyStopped && elapsedSeconds >= TEST_STOP_SECONDS && distance <= 10) {
                currentStopId = System.currentTimeMillis()

                // Use the original start time when user first stopped
                val stopStartTime = stopStartTime ?: nowWall

                val stop = StopPoint(
                    id = currentStopId!!,
                    center = stopCenter!!,
                    startTimeMillis = stopStartTime,
                    endTimeMillis = null,  // Ongoing stop
                    timeSpentMinutes = 0
                )

                // ✅ Use the new force update method
                sessionState.addStopAndForceUpdate(stop)

                isCurrentlyStopped = true
                movementState = MovementState.STOPPED

                Log.d("StopDebug", "🧪 STOP CREATED: ID=$currentStopId, StartTime=$stopStartTime")
                showUserToast("🧪 STOP CREATED after 10 seconds - Red marker should appear now!")

                // Show current stop count
                val stopCount = sessionState.stopPoints.value.size
                showUserToast("📍 Total stops so far: $stopCount")
            }

            // =====================================================
            // UPDATE STOP TIMER (while staying in same area)
            // =====================================================
            // UPDATE STOP TIMER (while staying in same area)
            if (isCurrentlyStopped && currentStopId != null && distance <= 10) {
                val minutes = ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 60000)
                val seconds = ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 1000) % 60

                // Find and update the existing stop
                val existingStop = sessionState.stopPoints.value.firstOrNull { it.id == currentStopId }
                if (existingStop != null) {
                    val updatedStop = existingStop.copy(
                        timeSpentMinutes = minutes
                    )
                    // ✅ Use force update method here too
                    sessionState.addStopAndForceUpdate(updatedStop)

                    // Show timer every 10 seconds
                    if (seconds % 10 == 0L) {
                        showUserToast("⏱️ Stop duration: ${minutes}m ${seconds}s")
                    }
                }
            }

            // =====================================================
            // EXIT STOP IF USER MOVES AWAY (distance > 30 meters)
            // =====================================================
            // EXIT STOP IF USER MOVES AWAY (distance > 30 meters)
            if (distance > 10) {
                Log.d("StopDebug", "🧪 User moved away - distance: $distance m")

                if (isCurrentlyStopped && currentStopId != null) {
                    val minutes = ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 60000)
                    val seconds = ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 1000) % 60

                    // Finalize the stop
                    val existingStop = sessionState.stopPoints.value.firstOrNull { it.id == currentStopId }
                    if (existingStop != null) {
                        val finalizedStop = existingStop.copy(
                            endTimeMillis = nowWall,
                            timeSpentMinutes = minutes
                        )
                        // ✅ Use force update method here too
                        sessionState.addStopAndForceUpdate(finalizedStop)

                        Log.d("StopDebug", "✅ Stop FINALIZED: ${minutes}m ${seconds}s")
                        showUserToast("✅ Stop finished: ${minutes}m ${seconds}s")

                        // Show updated stop count
                        val completedStops = sessionState.stopPoints.value.filter { it.endTimeMillis != null }.size
                        showUserToast("📊 Completed stops: $completedStops")
                    }
                }

                // Reset for next potential stop
                resetStopState()
                movementState = MovementState.MOVING
                stopCenter = null

                Log.d("StopDebug", "🧪 Ready for next stop")
                showUserToast("👋 Moved away - Ready for next stop")
            }

            // =====================================================
            // SHOW CURRENT STOP COUNT EVERY 30 SECONDS (for debugging)
            // =====================================================
            if (elapsedSeconds % 30 == 0L && elapsedSeconds > 0) {
                val totalStops = sessionState.stopPoints.value.size
                val completedStops = sessionState.stopPoints.value.filter { it.endTimeMillis != null }.size
                val ongoingStops = totalStops - completedStops

                Log.d("StopDebug", "📊 Stats - Total: $totalStops, Completed: $completedStops, Ongoing: $ongoingStops")

                // Show the current order
                val stops = sessionState.stopPoints.value.sortedBy { it.startTimeMillis }
                stops.forEachIndexed { index, s ->
                    val letter = ('A' + index).toString()
                    val status = if (s.endTimeMillis == null) "🟡" else "✅"
                    Log.d("StopDebug", "   $letter$status: ${s.startTimeMillis}")
                }
            }

            return
        }*/



        if (location.accuracy > minAccuracyMeters) {

            when {
                location.accuracy > 200f -> {
                    showUserToast("📡 Very poor GPS signal - Move to open area")
                }
                location.accuracy > minAccuracyMeters -> {
                    showUserToast("📡 Weak GPS signal (${location.accuracy.toInt()}m) - Waiting for better accuracy")
                }
            }


            sessionState.addLocation(LocationPoint(latLng, System.currentTimeMillis()))
            return
        }

        // add data in DB for export gpx
        val point = LocationPoint(latLng, nowWall)
        viewModelScope.launch(Dispatchers.IO) {
            routeRepo.add(point)
        }

        sessionState.addLocation(LocationPoint(latLng, System.currentTimeMillis()))
        // =====================================================================
        // 📍 RAW LOCATION LOG - Always show this
        // =====================================================================
        Log.d("StopDebug", "════════════════════════════════════════════════════")
        Log.d("StopDebug", "🕐 TIME: ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(nowWall))}")
        Log.d("StopDebug", "📍 LOCATION: ${location.latitude}, ${location.longitude}")
        Log.d("StopDebug", "⚡ SPEED: $speedKmh km/h (raw: ${location.speed})")
        Log.d("StopDebug", "🎯 ACCURACY: ${location.accuracy}m")
        Log.d("StopDebug", "📱 PROVIDER: ${location.provider}")
        Log.d("StopDebug", "🔄 CURRENT STATE: $movementState")
        Log.d("StopDebug", "📍 STOP CENTER: $stopCenter")
        Log.d("StopDebug", "🔢 POINTS INSIDE: $pointsInsideRadius")
        Log.d("StopDebug", "🆔 CURRENT STOP ID: $currentStopId")
        Log.d("StopDebug", "⏱️ STOP START ELAPSED: $stopStartElapsed")
        Log.d("StopDebug", "════════════════════════════════════════════════════")
        when (movementState) {

            // =================================================================
            // 🚗 MOVING STATE
            // =================================================================
            MovementState.MOVING -> {
                Log.d("StopDebug", "🏁 IN MOVING STATE - Speed: $speedKmh km/h")

                when {
                    speedKmh > movingSpeedThresholdKmh -> {
                        Log.d("StopDebug", "🚗 DRIVING: $speedKmh > $movingSpeedThresholdKmh")
                        showUserToast("🚗 Driving - No stop detection")
                    }

                    speedKmh in walkingMinSpeed..walkingMaxSpeed -> {
                        Log.d("StopDebug", "🚶 WALKING SPEED: $speedKmh km/h")
                        showUserToast("🚶 Walking - Will detect when you stop")
                    }

                    speedKmh in trafficSpeedMin..trafficSpeedMax -> {
                        Log.d("StopDebug", "🚦 TRAFFIC SPEED: $speedKmh km/h")
                        showUserToast("🚦 Slow traffic - Stop detection active")
                    }
                }

                // Check for possible stop
                if (speedKmh < walkingSpeedThreshold) {
                    Log.d("StopDebug", "🎯 POSSIBLE STOP CONDITION MET: $speedKmh < $walkingSpeedThreshold")

                    stopCenter = latLng
                    stopStartTime = nowWall
                    stopStartElapsed = nowElapsed
                    pointsInsideRadius = 1
                    movementState = MovementState.POSSIBLE_STOP

                    Log.d("StopDebug", "✅ STATE CHANGED: MOVING → POSSIBLE_STOP")
                    showUserToast("🅿️ Vehicle stopped - Monitoring for stop...")
                }
            }

            // =================================================================
            // 🟡 POSSIBLE_STOP STATE
            // =================================================================
            MovementState.POSSIBLE_STOP -> {
                Log.d("StopDebug", "🟡 IN POSSIBLE_STOP STATE")

                if (center == null) {
                    Log.e("StopDebug", "❌ CRITICAL: center is null in POSSIBLE_STOP")
                    return
                }

                val distance = GeoUtils.distanceMeters(center, latLng)
                Log.d("StopDebug", "📏 Distance from stop center: $distance m (radius: $stopRadiusMeters m)")

                if (distance <= stopRadiusMeters) {
                    pointsInsideRadius++
                    val minutes = ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 60000)

                    Log.d("StopDebug", "✅ WITHIN RADIUS - points: $pointsInsideRadius/$minStopPoints, minutes: $minutes/$stopThresholdMinutes")

                    // Show progress to user
                    val pointsNeeded = minStopPoints - pointsInsideRadius
                    val timeNeeded = stopThresholdMinutes - minutes

                    when (pointsInsideRadius) {
                        1 -> {
                            showUserToast("📍 Stop location detected - Need ${pointsNeeded} more GPS readings")
                        }
                        2 -> {
                            showUserToast("📍 Still at location - ${pointsNeeded} more readings needed")
                        }
                        3 -> {
                            showUserToast("📍 Almost there! One more GPS reading needed")
                        }
                    }

                    if (minutes >= stopThresholdMinutes && pointsInsideRadius >= minStopPoints) {
                        Log.d("StopDebug", "🎉🎉🎉 STOP CONFIRMATION CRITERIA MET! 🎉🎉🎉")

                        currentStopId = System.currentTimeMillis()
                        Log.d("StopDebug", "🆔 New currentStopId set to: $currentStopId")

                        val stop = StopPoint(
                            id = currentStopId!!,
                            center = center,
                            startTimeMillis = stopStartTime ?: nowWall,
                            endTimeMillis = null,
                            timeSpentMinutes = minutes
                        )

                        sessionState.addOrUpdateStop(stop)
                        isCurrentlyStopped = true
                        Log.d("StopDebug", "💾 Stop saved to sessionState")

                        movementState = MovementState.STOPPED
                        Log.d("StopDebug", "✅ STATE CHANGED: POSSIBLE_STOP → STOPPED")

                        // 🎉 SUCCESS TOAST - Red marker appears!
                        showUserToast("✅ STOP CONFIRMED! Location saved (${minutes} min)")
                    }
                } else {
                    Log.d("StopDebug", "❌ OUTSIDE RADIUS: $distance m > $stopRadiusMeters m")
                    showUserToast("➡️ Moved away - Stop cancelled (not enough time)")
                    resetStopState()
                    movementState = MovementState.MOVING
                }
            }

            // Working Fine Main Logic
            // =================================================================
            // 🔴 STOPPED STATE
            // =================================================================
//            MovementState.STOPPED -> {
//                Log.d("StopDebug", "🔴 IN STOPPED STATE")
//
//                if (center == null) {
//                    Log.e("StopDebug", "❌ CRITICAL: center is null in STOPPED state")
//                    resetStopState()
//                    movementState = MovementState.MOVING
//                    return
//                }
//
//                // ===========================================
//                // SAFETY CHECK 1: Handle null currentStopId
//                // ===========================================
//                if (currentStopId == null) {
//                    Log.d("StopDebug", "⚠️ WARNING: currentStopId is null in STOPPED state")
//                    val minutes = ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 60000)
//
//                    when {
//                        minutes >= stopThresholdMinutes -> {
//                            Log.d("StopDebug", "🆔 RECOVERING: Creating new stop ID")
//                            // CRITICAL: Set isCurrentlyStopped to true!
//                            isCurrentlyStopped = true
//                            currentStopId = System.currentTimeMillis()
//
//                            val stop = StopPoint(
//                                id = currentStopId!!,
//                                center = center,
//                                startTimeMillis = stopStartTime ?: nowWall,
//                                endTimeMillis = null,
//                                timeSpentMinutes = minutes
//                            )
//                            sessionState.addOrUpdateStop(stop)
//                            showUserToast("✅ Stop recovered - Location saved")
//                        }
//
//                        else -> {
//                            Log.d("StopDebug", "↩️ Not enough time, returning to POSSIBLE_STOP")
//                            movementState = MovementState.POSSIBLE_STOP
//                            return
//                        }
//                    }
//                }
//
//                // ===========================================
//                // SAFETY CHECK 2: Double-check ID still exists
//                // ===========================================
//                if (currentStopId == null) {
//                    Log.e("StopDebug", "❌ CRITICAL: currentStopId still null after recovery!")
//                    resetStopState()
//                    movementState = MovementState.MOVING
//                    return
//                }
//
//                // ===========================================
//                // Calculate current state
//                // ===========================================
//                val distance = GeoUtils.distanceMeters(center, latLng)
//                val minutes = ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 60000)
//
//                val isDriving = speedKmh > movingSpeedThresholdKmh
//                val isWalking = speedKmh in walkingMinSpeed..walkingMaxSpeed
//
//                Log.d("StopDebug", "📊 STOPPED STATE - distance: $distance, minutes: $minutes")
//
//                // ===========================================
//                // Handle state transitions with user-friendly toasts
//                // ===========================================
//                when {
//                    isDriving -> {
//                        Log.d("StopDebug", "🚗 DRIVING DETECTED - ending stop")
//                        finalizeStop(nowWall, nowElapsed)
//                        resetStopState()
//                        movementState = MovementState.MOVING
//                        showUserToast("🚗 Driving away - Stop saved (${minutes} min)")
//                        return
//                    }
//
//                    distance > exitRadiusMeters -> {
//                        Log.d("StopDebug", "🚶 EXIT RADIUS EXCEEDED: $distance m")
//                        finalizeStop(nowWall, nowElapsed)
//                        resetStopState()
//                        movementState = MovementState.MOVING
//                        showUserToast("👋 Left location - Stop saved (${minutes} min)")
//                        return
//                    }
//
//                    isWalking -> {
//                        // Show different messages based on distance
//                        when {
//                            distance < stopRadiusMeters -> {
//                                showUserToast("🚶 Walking inside shop - Stop continues")
//                            }
//                            else -> {
//                                showUserToast("🚶 Walking nearby - Still recording stop")
//                            }
//                        }
//                    }
//
//                    else -> {
//                        // Update user on stop duration every minute
//                        when {
//                            minutes == 1L -> showUserToast("📍 Stopped for 1 minute")
//                            minutes == 2L -> showUserToast("📍 Stopped for 2 minutes")
//                            minutes == 3L -> showUserToast("📍 Stopped for 3 minutes")
//                            minutes == 5L -> showUserToast("📍 Stopped for 5 minutes")
//                            minutes % 5 == 0L -> showUserToast("📍 Stopped for $minutes minutes")
//                            minutes % 2 == 0L -> showUserToast("📍 Still here - $minutes minutes")
//                        }
//                    }
//                }
//
//                // ===========================================
//                // Update the stop
//                // ===========================================
//                try {
//                    val stop = StopPoint(
//                        id = currentStopId!!,
//                        center = center,
//                        startTimeMillis = stopStartTime ?: nowWall,
//                        endTimeMillis = null,
//                        timeSpentMinutes = minutes
//                    )
//                    sessionState.addOrUpdateStop(stop)
//                    Log.d("StopDebug", "✅ Stop updated: $minutes minutes")
//                } catch (e: Exception) {
//                    Log.e("StopDebug", "❌ Failed to update stop: ${e.message}")
//                }
//            }


            MovementState.STOPPED -> {
                Log.d("StopDebug", "🔴 IN STOPPED STATE")

                if (center == null) {
                    Log.e("StopDebug", "❌ CRITICAL: center is null in STOPPED state")
                    resetStopState()
                    movementState = MovementState.MOVING
                    return
                }

                // ===========================================
                // SAFETY CHECK 1: Handle null currentStopId
                // ===========================================
                if (currentStopId == null) {
                    Log.d("StopDebug", "⚠️ WARNING: currentStopId is null in STOPPED state")
                    val minutes = ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 60000)

                    when {
                        minutes >= stopThresholdMinutes -> {
                            Log.d("StopDebug", "🆔 RECOVERING: Creating new stop ID")
                            isCurrentlyStopped = true
                            currentStopId = System.currentTimeMillis()

                            val stop = StopPoint(
                                id = currentStopId!!,
                                center = center,
                                startTimeMillis = stopStartTime ?: nowWall,
                                endTimeMillis = null,
                                timeSpentMinutes = minutes
                            )
                            sessionState.addOrUpdateStop(stop)
                            showUserToast("✅ Stop recovered - Location saved")
                        }

                        else -> {
                            Log.d("StopDebug", "↩️ Not enough time, returning to POSSIBLE_STOP")
                            movementState = MovementState.POSSIBLE_STOP
                            return
                        }
                    }
                }

                // ===========================================
                // SAFETY CHECK 2: Double-check ID still exists
                // ===========================================
                if (currentStopId == null) {
                    Log.e("StopDebug", "❌ CRITICAL: currentStopId still null after recovery!")
                    resetStopState()
                    movementState = MovementState.MOVING
                    return
                }

                // ===========================================
                // Calculate current state
                // ===========================================
                val distance = GeoUtils.distanceMeters(center, latLng)
                val minutes = ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 60000)
                val isDriving = speedKmh > movingSpeedThresholdKmh
                val isWalking = speedKmh in walkingMinSpeed..walkingMaxSpeed

                Log.d("StopDebug", "📊 STOPPED STATE - distance: $distance, minutes: $minutes")

                // ===========================================
                // Handle state transitions with user-friendly toasts
                // ===========================================
                when {
                    isDriving -> {
                        Log.d("StopDebug", "🚗 DRIVING DETECTED - ending stop")
                        finalizeStop(nowWall, nowElapsed)
                        resetStopState()
                        movementState = MovementState.MOVING
                        showUserToast("🚗 Driving away - Stop saved (${minutes} min)")
                        return
                    }

                    distance > exitRadiusMeters -> {
                        Log.d("StopDebug", "🚶 EXIT RADIUS EXCEEDED: $distance m")
                        finalizeStop(nowWall, nowElapsed)
                        resetStopState()
                        movementState = MovementState.MOVING
                        showUserToast("👋 Left location - Stop saved (${minutes} min)")
                        return
                    }

                    isWalking -> {
                        when {
                            distance < stopRadiusMeters -> {
                                showUserToast("🚶 Walking inside shop - Stop continues")
                            }
                            else -> {
                                showUserToast("🚶 Walking nearby - Still recording stop")
                            }
                        }
                    }

                    else -> {
                        when {
                            minutes == 1L -> showUserToast("📍 Stopped for 1 minute")
                            minutes == 2L -> showUserToast("📍 Stopped for 2 minutes")
                            minutes == 3L -> showUserToast("📍 Stopped for 3 minutes")
                            minutes == 5L -> showUserToast("📍 Stopped for 5 minutes")
                            minutes % 5 == 0L -> showUserToast("📍 Stopped for $minutes minutes")
                            minutes % 2 == 0L -> showUserToast("📍 Still here - $minutes minutes")
                        }
                    }
                }

                // ===========================================
                // UPDATE THE STOP - FIXED VERSION
                // ===========================================
                try {
                    // IMPORTANT: Check if stop exists before updating
                    val existingStop = sessionState.stopPoints.value.firstOrNull { it.id == currentStopId }

                    if (existingStop != null) {
                        // Update existing stop by copying with new minutes
                        val updatedStop = existingStop.copy(
                            timeSpentMinutes = minutes
                            // ⚠️ DO NOT change endTimeMillis here - it should remain null until finalized
                        )
                        sessionState.addOrUpdateStop(updatedStop)
                        Log.d("StopDebug", "✅ Stop updated: $minutes minutes (ID: $currentStopId)")
                    } else {
                        // This shouldn't happen, but create it if it doesn't exist
                        Log.e("StopDebug", "⚠️ Stop not found, creating new one")
                        val newStop = StopPoint(
                            id = currentStopId!!,
                            center = center,
                            startTimeMillis = stopStartTime ?: nowWall,
                            endTimeMillis = null,
                            timeSpentMinutes = minutes
                        )
                        sessionState.addOrUpdateStop(newStop)
                    }
                } catch (e: Exception) {
                    Log.e("StopDebug", "❌ Failed to update stop: ${e.message}")
                }
            }
        }
    }

    fun debugStopOrder() {
        val stops = sessionState.stopPoints.value
        Log.d("STOP_ORDER", "=== CURRENT STOPS (${stops.size}) ===")
        stops.sortedBy { it.startTimeMillis }.forEachIndexed { index, stop ->
            val letter = ('A' + index).toString()
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(stop.startTimeMillis))
            Log.d("STOP_ORDER", "$letter: ID=${stop.id}, Time=$timeStr, Minutes=${stop.timeSpentMinutes}")

        }
    }

    private fun showUserToast(message: String) {
        // Only show toasts that are important for the user
        val importantToasts = listOf(
            "🅿️ Vehicle stopped - Monitoring for stop...",
            "📍 Stop location detected",
            "✅ STOP CONFIRMED",
            "🚗 Driving away - Stop saved",
            "👋 Left location - Stop saved",
            "📍 Stopped for",
            "🧪 TEST MODE: Waiting 10 seconds",
            "🧪 TEST STOP CREATED (10 sec)"
        )

        // Check if this is an important toast or we're in debug mode
        if (importantToasts.any { message.contains(it) }) {
            Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
        }

        // Always log
        Log.d("StopDebug", "📢 USER TOAST: $message")
    }





    private fun resetStopState() {
        // CRITICAL: Reset isCurrentlyStopped to false
        isCurrentlyStopped = false
        stopCenter = null
        stopStartTime = null
        stopStartElapsed = null
        currentStopId = null
        pointsInsideRadius = 0
    }


    // Working Fine
//    private fun finalizeStop(nowWall: Long, nowElapsed: Long) {
//        Log.d("StopDebug", "🔚 FINALIZING STOP:")
//        Log.d("StopDebug", "   stopCenter: $stopCenter")
//        Log.d("StopDebug", "   currentStopId: $currentStopId")
//        Log.d("StopDebug", "   stopStartElapsed: $stopStartElapsed")
//
//        val center = stopCenter ?: run {
//            Log.e("StopDebug", "❌ Cannot finalize: stopCenter is null")
//            return
//        }
//
//        val minutes = ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 60000)
//        Log.d("StopDebug", "   Final minutes: $minutes")
//
//        val stop = StopPoint(
//            id = currentStopId ?: run {
//                Log.e("StopDebug", "❌ Cannot finalize: currentStopId is null")
//                return
//            },
//            center = center,
//            startTimeMillis = stopStartTime ?: nowWall,
//            endTimeMillis = nowWall,
//            timeSpentMinutes = minutes
//        )
//
//        sessionState.addOrUpdateStop(stop)
//        resolveLocationNameAsync(stop)
//        Log.d("StopDebug", "✅ Stop finalized and saved")
//    }


    private fun finalizeStop(nowWall: Long, nowElapsed: Long) {
        Log.d("StopDebug", "🔚 FINALIZING STOP:")
        Log.d("StopDebug", "   stopCenter: $stopCenter")
        Log.d("StopDebug", "   currentStopId: $currentStopId")

        val center = stopCenter ?: run {
            Log.e("StopDebug", "❌ Cannot finalize: stopCenter is null")
            return
        }

        val stopId = currentStopId ?: run {
            Log.e("StopDebug", "❌ Cannot finalize: currentStopId is null")
            return
        }

        val minutes = ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 60000)

        // Find existing stop and update it with end time
        val existingStop = sessionState.stopPoints.value.firstOrNull { it.id == stopId }

        if (existingStop != null) {
            val finalizedStop = existingStop.copy(
                endTimeMillis = nowWall,
                timeSpentMinutes = minutes
            )
            sessionState.addOrUpdateStop(finalizedStop)
            Log.d("StopDebug", "✅ Stop finalized: $minutes minutes")

            // Save to repository
            viewModelScope.launch {
                stopRepo.upsertStop(finalizedStop, stopStartElapsed ?: nowElapsed, nowElapsed)
            }

            resolveLocationNameAsync(finalizedStop)
        } else {
            Log.e("StopDebug", "❌ Cannot finalize: stop not found in session")
            // Create it as a last resort
            val newStop = StopPoint(
                id = stopId,
                center = center,
                startTimeMillis = stopStartTime ?: nowWall,
                endTimeMillis = nowWall,
                timeSpentMinutes = minutes
            )
            sessionState.addOrUpdateStop(newStop)
        }
    }

    private fun debugToast(msg: String) {

        Log.d("StopDebug", msg)

        Toast.makeText(
            getApplication(),
            msg,
            Toast.LENGTH_SHORT
        ).show()
    }

        private fun finalizeStopIfNeeded(nowWall: Long, nowElapsed: Long) {

            if (isCurrentlyStopped && currentStopId != null) {

                val minutes =
                    ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 60000)

                val stop = StopPoint(
                    id = currentStopId!!,
                    center = stopCenter!!,
                    startTimeMillis = stopStartTime ?: nowWall,
                    endTimeMillis = nowWall,
                    timeSpentMinutes = minutes
                )

                sessionState.addOrUpdateStop(stop)

                val startElapsedLocal = stopStartElapsed ?: nowElapsed
                viewModelScope.launch {
                    stopRepo.upsertStop(stop, startElapsedLocal, nowElapsed)
                }

                resolveLocationNameAsync(stop)
            }
        }

        private fun resetStopState(
            latLng: LatLng,
            nowWall: Long,
            nowElapsed: Long
        ) {
            stopCenter = latLng
            stopStartTime = nowWall
            stopStartElapsed = nowElapsed
            isCurrentlyStopped = false
            currentStopId = null
            thresholdToastShown = false
        }

        /**
         * Resolve a human-readable location name (\"latLng name\") for a stop using reverse geocoding.
         * This fills the stop's address field so the UI / PDF can show a name instead of raw lat,lng.
         */
        private fun resolveLocationNameAsync(stop: StopPoint) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // If we already have a location label for this stop in memory, don't override it
                    val current = sessionState.stopPoints.value.firstOrNull { it.id == stop.id }
                    if (current != null && !current.locationLabel.isNullOrBlank()) {
                        return@launch
                    }

                    val ctx = getApplication<Application>()
                    val geocoder = Geocoder(ctx, Locale.getDefault())
                    val results = geocoder.getFromLocation(stop.center.latitude, stop.center.longitude, 1)
                    val addressLine = results?.firstOrNull()?.getAddressLine(0)

                    if (!addressLine.isNullOrBlank()) {
                        val base = current ?: stop
                        val updated = base.copy(locationLabel = addressLine)
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
                    // Preserve location label, address, phone, and imageUri from the first stop (or find one that has them)
                    val stopWithDetails = list.firstOrNull {
                        !it.locationLabel.isNullOrBlank() || !it.address.isNullOrBlank() || !it.phone.isNullOrBlank() || !it.imageUri.isNullOrBlank()
                    } ?: first
                    StopPoint(
                        id = first.id,
                        center = first.center,
                        startTimeMillis = first.startTimeMillis,
                        endTimeMillis = last.endTimeMillis,
                        name = name,
                        locationLabel = stopWithDetails.locationLabel,
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

        suspend fun getRouteBetween(startMillis: Long, endMillis: Long): List<LocationPoint> {
            return routeRepo.getBetween(startMillis, endMillis)
        }

    //    private fun handleLocation(location: Location) {
    //        val now = System.currentTimeMillis()
    //        val latLng = LatLng(location.latitude, location.longitude)
    //        val point = LocationPoint(latLng, now)
    //        sessionState.addLocation(LocationPoint(latLng, System.currentTimeMillis()))
    //
    //        viewModelScope.launch(Dispatchers.IO) {
    //            routeRepo.add(point)
    //        }
    //
    //        val currentCenter = stopCenter
    //        val nowWall = System.currentTimeMillis()
    //        val nowElapsed = SystemClock.elapsedRealtime()
    //
    //        // DEBUG: Log current state
    //        Log.d("StopDebug", "currentCenter: $currentCenter")
    //        Log.d("StopDebug", "isCurrentlyStopped: $isCurrentlyStopped")
    //
    //        if (currentCenter == null) {
    //            Log.d("StopDebug", "Starting new potential stop at $latLng")
    //            stopCenter = latLng
    //            stopStartTime = nowWall
    //            stopStartElapsed = nowElapsed
    //            isCurrentlyStopped = false
    //            currentStopId = null
    //            thresholdToastShown = false
    //        } else {
    //            val distance = GeoUtils.distanceMeters(currentCenter, latLng)
    //            val elapsedMinutes = ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 60000)
    //
    //            Log.d("StopDebug", "Distance from center: $distance meters")
    //            Log.d("StopDebug", "Elapsed minutes: $elapsedMinutes")
    //
    //            if (distance <= stopRadiusMeters) {
    //                Log.d("StopDebug", "Within stop radius")
    //                // Still within stop area
    //                if (elapsedMinutes >= stopThresholdMinutes && !isCurrentlyStopped) {
    //                    Log.d("StopDebug", "*** THRESHOLD REACHED - CREATING STOP ***")
    //                    // Threshold reached - mark as a stop and assign a stable id
    //                    currentStopId = System.currentTimeMillis()
    //                    val stop = StopPoint(
    //                        id = currentStopId!!,
    //                        center = currentCenter,
    //                        startTimeMillis = stopStartTime ?: nowWall,
    //                        endTimeMillis = null,
    //                        timeSpentMinutes = elapsedMinutes
    //                    )
    //                    sessionState.addOrUpdateStop(stop)
    //                    val startElapsedLocal = stopStartElapsed ?: nowElapsed
    //                    viewModelScope.launch { stopRepo.upsertStop(stop, startElapsedLocal, null) }
    //                    // Resolve a human-readable location name once the stop is detected
    //                    resolveLocationNameAsync(stop)
    //                    isCurrentlyStopped = true
    //                    if (!thresholdToastShown) {
    //                        Toast.makeText(getApplication(), "Stop started (>= ${stopThresholdMinutes} min)", Toast.LENGTH_SHORT).show()
    //                        thresholdToastShown = true
    //                    }
    //                } else if (isCurrentlyStopped) {
    //                    Log.d("StopDebug", "Updating existing stop")
    //                    // Update ongoing stop time, reuse same id
    //                    val stop = StopPoint(
    //                        id = currentStopId!!,
    //                        center = currentCenter,
    //                        startTimeMillis = stopStartTime ?: nowWall,
    //                        endTimeMillis = null,
    //                        timeSpentMinutes = elapsedMinutes
    //                    )
    //                    sessionState.addOrUpdateStop(stop)
    //                    val startElapsedLocal = stopStartElapsed ?: nowElapsed
    //                    viewModelScope.launch { stopRepo.upsertStop(stop, startElapsedLocal, null) }
    //                }
    //            } else {
    //                Log.d("StopDebug", "Moved outside stop radius")
    //                // Moved out of stop area
    //                if (isCurrentlyStopped && currentStopId != null) {
    //                    val minutes = ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 60000)
    //                    val stop = StopPoint(
    //                        id = currentStopId!!,
    //                        center = currentCenter,
    //                        startTimeMillis = stopStartTime ?: nowWall,
    //                        endTimeMillis = nowWall,
    //                        timeSpentMinutes = minutes
    //                    )
    //                    sessionState.addOrUpdateStop(stop)
    //                    val startElapsedLocal = stopStartElapsed ?: nowElapsed
    //                    viewModelScope.launch { stopRepo.upsertStop(stop, startElapsedLocal, nowElapsed) }
    //                    Toast.makeText(getApplication(), "Stop ended: ${minutes} min", Toast.LENGTH_SHORT).show()
    //                    // Also resolve location name for this finalized stop
    //                    resolveLocationNameAsync(stop)
    //                }
    //                // Start tracking new potential stop
    //                Log.d("StopDebug", "Starting new potential stop at $latLng")
    //                stopCenter = latLng
    //                stopStartTime = nowWall
    //                stopStartElapsed = nowElapsed
    //                isCurrentlyStopped = false
    //                currentStopId = null
    //                thresholdToastShown = false
    //            }
    //        }
    //    }

    // working fine just not accept traffic
    //    private fun handleLocation(location: Location) {
    //
    //        val nowWall = System.currentTimeMillis()
    //        val nowElapsed = SystemClock.elapsedRealtime()
    //        val latLng = LatLng(location.latitude, location.longitude)
    //
    //        val point = LocationPoint(latLng, nowWall)
    //        sessionState.addLocation(point)
    //
    //        viewModelScope.launch(Dispatchers.IO) {
    //            routeRepo.add(point)
    //        }
    //
    //        val currentCenter = stopCenter
    //
    //        // 🟢 FIRST LOCATION → start tracking
    //        if (currentCenter == null) {
    //            Log.d("StopDebug", "Initializing stop tracking at $latLng")
    //
    //            stopCenter = latLng
    //            stopStartTime = nowWall
    //            stopStartElapsed = nowElapsed
    //            isCurrentlyStopped = false
    //            currentStopId = null
    //            thresholdToastShown = false
    //            return
    //        }
    //
    //        val distance = GeoUtils.distanceMeters(currentCenter, latLng)
    //        val elapsedMinutes =
    //            ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 60000)
    //
    //        Log.d("StopDebug", "Distance: $distance m")
    //        Log.d("StopDebug", "Elapsed minutes: $elapsedMinutes")
    //        Log.d("StopDebug", "isCurrentlyStopped: $isCurrentlyStopped")
    //
    //        // 🎯 CONFIG
    //        val enterRadius = stopRadiusMeters              // e.g. 25m
    //        val exitRadius = stopRadiusMeters + 20         // buffer to prevent jitter reset
    //
    //        // ==============================
    //        // 🟢 STILL INSIDE STOP ZONE
    //        // ==============================
    //        if (distance <= enterRadius) {
    //
    //            if (!isCurrentlyStopped && elapsedMinutes >= stopThresholdMinutes) {
    //
    //                Log.d("StopDebug", "*** STOP STARTED ***")
    //
    //                currentStopId = System.currentTimeMillis()
    //
    //                val stop = StopPoint(
    //                    id = currentStopId!!,
    //                    center = currentCenter,
    //                    startTimeMillis = stopStartTime ?: nowWall,
    //                    endTimeMillis = null,
    //                    timeSpentMinutes = elapsedMinutes
    //                )
    //
    //                sessionState.addOrUpdateStop(stop)
    //
    //                val startElapsedLocal = stopStartElapsed ?: nowElapsed
    //                viewModelScope.launch {
    //                    stopRepo.upsertStop(stop, startElapsedLocal, null)
    //                }
    //
    //                resolveLocationNameAsync(stop)
    //
    //                isCurrentlyStopped = true
    //
    //                if (!thresholdToastShown) {
    //                    Toast.makeText(
    //                        getApplication(),
    //                        "Stop started (>= $stopThresholdMinutes min)",
    //                        Toast.LENGTH_SHORT
    //                    ).show()
    //                    thresholdToastShown = true
    //                }
    //            }
    //
    //            // 🔄 Update ongoing stop
    //            else if (isCurrentlyStopped && currentStopId != null) {
    //
    //                val stop = StopPoint(
    //                    id = currentStopId!!,
    //                    center = currentCenter,
    //                    startTimeMillis = stopStartTime ?: nowWall,
    //                    endTimeMillis = null,
    //                    timeSpentMinutes = elapsedMinutes
    //                )
    //
    //                sessionState.addOrUpdateStop(stop)
    //
    //                val startElapsedLocal = stopStartElapsed ?: nowElapsed
    //                viewModelScope.launch {
    //                    stopRepo.upsertStop(stop, startElapsedLocal, null)
    //                }
    //            }
    //        }
    //
    //        // ==============================
    //        // 🔴 OUTSIDE ENTER RADIUS
    //        // ==============================
    //        else {
    //
    //            // 🟡 Ignore small GPS drift
    //            if (distance <= exitRadius) {
    //                Log.d("StopDebug", "GPS drift ignored")
    //                return
    //            }
    //
    //            Log.d("StopDebug", "Significant movement detected")
    //
    //            // 🔴 If was stopped → end stop
    //            if (isCurrentlyStopped && currentStopId != null) {
    //
    //                val minutes =
    //                    ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 60000)
    //
    //                val stop = StopPoint(
    //                    id = currentStopId!!,
    //                    center = currentCenter,
    //                    startTimeMillis = stopStartTime ?: nowWall,
    //                    endTimeMillis = nowWall,
    //                    timeSpentMinutes = minutes
    //                )
    //
    //                sessionState.addOrUpdateStop(stop)
    //
    //                val startElapsedLocal = stopStartElapsed ?: nowElapsed
    //                viewModelScope.launch {
    //                    stopRepo.upsertStop(stop, startElapsedLocal, nowElapsed)
    //                }
    //
    //                Toast.makeText(
    //                    getApplication(),
    //                    "Stop ended: $minutes min",
    //                    Toast.LENGTH_SHORT
    //                ).show()
    //
    //                resolveLocationNameAsync(stop)
    //            }
    //
    //            // 🔄 Reset for new potential stop
    //            stopCenter = latLng
    //            stopStartTime = nowWall
    //            stopStartElapsed = nowElapsed
    //            isCurrentlyStopped = false
    //            currentStopId = null
    //            thresholdToastShown = false
    //        }
    //    }

//    private fun handleLocation(location: Location) {
//
//        val nowWall = System.currentTimeMillis()
//        val nowElapsed = SystemClock.elapsedRealtime()
//        val latLng = LatLng(location.latitude, location.longitude)
//
//        Log.d("StopDebug", "-----------------------------------")
//        Log.d("StopDebug", "New location received")
//        Log.d("StopDebug", "Lat=${location.latitude} Lng=${location.longitude}")
//        Log.d("StopDebug", "Accuracy=${location.accuracy}m Speed=${location.speed * 3.6f} km/h")
//
//        // ==============================
//        // 🛡️ ENTERPRISE FILTERING START
//        // ==============================
//
//        // 1️⃣ Ignore poor GPS accuracy
//        if (location.accuracy > minAccuracyMeters) {
//            Log.d("StopDebug", "❌ Ignoring location due to poor accuracy (${location.accuracy}m)")
//            return
//        }
//
//        Log.d("StopDebug", "✅ Accuracy OK (${location.accuracy}m)")
//
//        // 2️⃣ Rolling average speed
//        val speedKmh = location.speed * 3.6f
//        speedHistory.addLast(speedKmh)
//
//        if (speedHistory.size > speedWindowSize) {
//            speedHistory.removeFirst()
//        }
//
//        val avgSpeed = speedHistory.average().toFloat()
//
//        Log.d("StopDebug", "Speed history=$speedHistory")
//        Log.d("StopDebug", "Average speed=$avgSpeed km/h")
//
//
//        /////// for production
////        if (avgSpeed > movingSpeedThresholdKmh) {
////
////            consecutiveMovingReadings++
////
////            Log.d(
////                "StopDebug",
////                "🚗 Moving detected avgSpeed=$avgSpeed > threshold=$movingSpeedThresholdKmh"
////            )
////
////            Log.d(
////                "StopDebug",
////                "Moving confirmations $consecutiveMovingReadings / $requiredMovingConfirmations"
////            )
////
////            if (consecutiveMovingReadings >= requiredMovingConfirmations) {
////
////                Log.d("StopDebug", "✅ Confirmed moving → finalize stop and reset state")
////
////                finalizeStopIfNeeded(nowWall, nowElapsed)
////                resetStopState(latLng, nowWall, nowElapsed)
////                return
////            }
////
////        } else {
////
////            Log.d("StopDebug", "🟡 Speed below moving threshold → possible stop")
////
////            consecutiveMovingReadings = 0
////        }
//
//
//
//
//
//
//        // If current speed is almost zero, allow stop detection immediately
//        if (speedKmh < 2f) {
//
//            Log.d("StopDebug", "🟢 Current speed very low ($speedKmh km/h) → possible stop")
//
//            consecutiveMovingReadings = 0
//
//            speedHistory.clear()
//            speedHistory.add(speedKmh)
//
//        } else if (avgSpeed > walkingSpeedThreshold && avgSpeed < movingSpeedThresholdKmh) {
//
//            Log.d("StopDebug", "🚶 Slow movement detected ($avgSpeed km/h) → not a stop yet")
//
////            return
//
//        }
//
//        else if (avgSpeed > movingSpeedThresholdKmh ) {
//
//            consecutiveMovingReadings++
//
//            Log.d(
//                "StopDebug",
//                "🚗 Moving detected avgSpeed=$avgSpeed km/h confirmations=$consecutiveMovingReadings"
//            )
//
//            if (consecutiveMovingReadings >= requiredMovingConfirmations) {
//
//                Log.d("StopDebug", "✅ Confirmed moving → finalize stop and reset state")
//
//                finalizeStopIfNeeded(nowWall, nowElapsed)
////                resetStopState(latLng, nowWall, nowElapsed)
////                return
//                stopCenter = null
//
//                isCurrentlyStopped = false
//                currentStopId = null
//
//                return
//            }
//
//        } else {
//            consecutiveMovingReadings = 0
//        }
//
//
//
//        val point = LocationPoint(latLng, nowWall)
//
//        sessionState.addLocation(point)
//
//        Log.d("StopDebug", "📍 Location added to route history")
//
//        viewModelScope.launch(Dispatchers.IO) {
//            routeRepo.add(point)
//        }
//
//        val currentCenter = stopCenter
//
//        // 🟢 FIRST LOCATION
//        if (currentCenter == null) {
//
//            Log.d("StopDebug", "🟢 First location received → setting stop center")
//
//            stopCenter = latLng
//            stopStartTime = nowWall
//            stopStartElapsed = nowElapsed
//            isCurrentlyStopped = false
//            currentStopId = null
//            thresholdToastShown = false
//
//            return
//        }
//
//        val distance = GeoUtils.distanceMeters(currentCenter, latLng)
//
//        if (distance > maxJumpMeters) {
//            Log.d("StopDebug", "⚠️ GPS jump detected ($distance m) → ignoring point")
//            return
//        }
//
//        val elapsedMinutes =
//            ((nowElapsed - (stopStartElapsed ?: nowElapsed)) / 60000)
//
//        Log.d(
//            "StopDebug",
//            "Distance from stop center = $distance m | elapsed=$elapsedMinutes min"
//        )
//
//        val enterRadius = stopRadiusMeters
//        val exitRadius = stopRadiusMeters + 20
//
//        Log.d(
//            "StopDebug",
//            "EnterRadius=$enterRadius ExitRadius=$exitRadius"
//        )
//
//        if (distance <= enterRadius) {
//
//            Log.d("StopDebug", "📍 Inside stop radius")
//
//            if (!isCurrentlyStopped && elapsedMinutes >= stopThresholdMinutes) {
//
//                Log.d(
//                    "StopDebug",
//                    "🛑 STOP DETECTED elapsed=$elapsedMinutes min >= threshold=$stopThresholdMinutes"
//                )
//
//                currentStopId = System.currentTimeMillis()
//
//                val stop = StopPoint(
//                    id = currentStopId!!,
//                    center = currentCenter,
//                    startTimeMillis = stopStartTime ?: nowWall,
//                    endTimeMillis = null,
//                    timeSpentMinutes = elapsedMinutes
//                )
//
//                sessionState.addOrUpdateStop(stop)
//
//                Log.d("StopDebug", "📌 Stop created with ID=$currentStopId")
//
//                val startElapsedLocal = stopStartElapsed ?: nowElapsed
//
//                viewModelScope.launch {
//                    stopRepo.upsertStop(stop, startElapsedLocal, null)
//                }
//
//                resolveLocationNameAsync(stop)
//
//                isCurrentlyStopped = true
//                thresholdToastShown = true
//
//            }
//
//            else if (isCurrentlyStopped && currentStopId != null) {
//
//                Log.d("StopDebug", "⏱ Updating existing stop duration")
//
//                val stop = StopPoint(
//                    id = currentStopId!!,
//                    center = currentCenter,
//                    startTimeMillis = stopStartTime ?: nowWall,
//                    endTimeMillis = null,
//                    timeSpentMinutes = elapsedMinutes
//                )
//
//                sessionState.addOrUpdateStop(stop)
//
//                val startElapsedLocal = stopStartElapsed ?: nowElapsed
//
//                viewModelScope.launch {
//                    stopRepo.upsertStop(stop, startElapsedLocal, null)
//                }
//            }
//
//        } else {
//
//            Log.d("StopDebug", "🚶 Outside enter radius")
//
//            if (distance <= exitRadius) {
//
//                Log.d("StopDebug", "⚠️ Inside buffer zone (GPS drift protection) → ignoring")
//
//                return
//            }
//
//            Log.d("StopDebug", "🚗 User left stop area → finalize stop")
//
//            finalizeStopIfNeeded(nowWall, nowElapsed)
//
//            Log.d("StopDebug", "🔄 Resetting stop state")
//
//            resetStopState(latLng, nowWall, nowElapsed)
//        }
//    }
    // upper func is working fine last change
    }


// These are perfect for production:
//private val stopThresholdMinutes = 1L        // 1 minute minimum visit
//private val minStopPoints = 4                 // Need 4 GPS readings
//private val stopRadiusMeters = 30f            // Shop radius
//private val exitRadiusMeters = 70f            // Exit buffer
//private val movingSpeedThresholdKmh = 15f     // Driving threshold
//private val walkingSpeedThreshold = 8f        // Walking threshold
//private val minAccuracyMeters = 100f          // Ignore very poor GPS

// ===========================================
// ⏱️ TIME THRESHOLDS
// ===========================================

/**
 * How long someone must stay in one place to count as a visit
 * 1 minute = filters out traffic lights and quick stops
 */
//private val minimumVisitDurationMinutes = 1L
//
///**
// * How many GPS readings needed to confirm a real stop
// * 4 readings = prevents false stops from GPS glitches
// */
//private val gpsReadingsToConfirmStop = 4
//
//
//// ===========================================
//// 📏 DISTANCE THRESHOLDS
//// ===========================================
//
///**
// * How close to the parking spot counts as "at the shop"
// * 30 meters = covers shop entrance and immediate area
// */
//private val shopRadiusMeters = 30f
//
///**
// * How far you must go before the app considers you "left"
// * 70 meters = allows walking inside large shops without ending stop
// */
//private val exitRadiusMeters = 70f
//
//
//// ===========================================
//// 🚗 SPEED THRESHOLDS
//// ===========================================
//
///**
// * Speed above this means definitely driving (ignore for stops)
// * 15 km/h = faster than running, definitely in vehicle
// */
//private val drivingSpeedThresholdKmh = 15f
//
///**
// * Speed below this could be a stop (parking, traffic, etc.)
// * 8 km/h = includes walking and very slow traffic
// */
//private val possibleStopSpeedThresholdKmh = 8f
//
//
//// ===========================================
//// 🎯 GPS QUALITY
//// ===========================================
//
///**
// * Maximum allowed GPS error in meters
// * 100 meters = ignores very bad GPS but accepts normal variation
// */
//private val maxAllowedGpsAccuracyMeters = 100f


