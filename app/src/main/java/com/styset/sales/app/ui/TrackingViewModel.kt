package com.styset.sales.app.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.styset.sales.app.data.LocationPoint
import com.styset.sales.app.data.StopPoint
import com.styset.sales.app.data.StopRepository
import com.styset.sales.app.data.TrackingSessionState
import com.styset.sales.app.data.local.RouteEntity
import com.styset.sales.app.location.LocationForegroundService
import com.styset.sales.app.location.LocationRepository
import com.styset.sales.app.location.RouteRepository
import com.styset.sales.app.models.AttendanceResponse
import com.styset.sales.app.models.EndTripResponse
import com.styset.sales.app.models.LeadModel
import com.styset.sales.app.models.LeadSelection
import com.styset.sales.app.models.Resource
import com.styset.sales.app.models.StartTripResponse
import com.styset.sales.app.models.toLeadModel
import com.styset.sales.app.repository.TripRepository
import com.styset.sales.app.util.GeoUtils
import com.styset.sales.app.util.PreferenceManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume

class TrackingViewModel(
    app: Application, private val tripRepository: TripRepository,
    private val preferenceManager: PreferenceManager
) : AndroidViewModel(app) {

    enum class MovementState {
        MOVING,
        POSSIBLE_STOP,
        STOPPED

    }

    private val TEST_MODE = true
    private val TEST_STOP_SECONDS = 4

    private var currentRouteId: Long? = null
    private var currentRouteStartTime: Long? = null

    private val _routes = MutableStateFlow<List<RouteEntity>>(emptyList())
    val routes: StateFlow<List<RouteEntity>> = _routes.asStateFlow()

    private val _tripStartState = MutableStateFlow<Resource<StartTripResponse>?>(null)
    val tripStartState: StateFlow<Resource<StartTripResponse>?> = _tripStartState.asStateFlow()


    private val _tripEndState = MutableStateFlow<Resource<EndTripResponse>?>(null)
    val tripEndState: StateFlow<Resource<EndTripResponse>?> = _tripEndState.asStateFlow()


    private val _loginState = MutableStateFlow<Resource<AttendanceResponse>?>(null)
    val loginState: StateFlow<Resource<AttendanceResponse>?> = _loginState.asStateFlow()

    private val _breakInState = MutableStateFlow<Resource<AttendanceResponse>?>(null)
    val breakInState: StateFlow<Resource<AttendanceResponse>?> = _breakInState.asStateFlow()

    private val _breakOutState = MutableStateFlow<Resource<AttendanceResponse>?>(null)
    val breakOutState: StateFlow<Resource<AttendanceResponse>?> = _breakOutState.asStateFlow()

    private val _logoutState = MutableStateFlow<Resource<AttendanceResponse>?>(null)
    val logoutState: StateFlow<Resource<AttendanceResponse>?> = _logoutState.asStateFlow()

    private val _todaysLeads = MutableStateFlow<List<LeadModel>>(emptyList())
    val todaysLeads: StateFlow<List<LeadModel>> = _todaysLeads.asStateFlow()

    private val _isLoadingLeads = MutableStateFlow(false)
    val isLoadingLeads: StateFlow<Boolean> = _isLoadingLeads.asStateFlow()

    private val _startLocation = MutableStateFlow<LatLng?>(null)
    val startLocation: StateFlow<LatLng?> = _startLocation.asStateFlow()

    private val _forceLogoutState = MutableStateFlow<Resource<AttendanceResponse>?>(null)
    val forceLogoutState: StateFlow<Resource<AttendanceResponse>?> = _forceLogoutState.asStateFlow()


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
    private val movingSpeedThresholdKmh =
        15f  // ignore stop detection above 12 km/h  (6 for testing)


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

        // Load routes
        viewModelScope.launch {
            routeRepo.observeAllRoutes().collect { routeList ->
                _routes.value = routeList
            }
        }
    }

    fun forceLogoutWithAttendanceId(
        attendanceId: Int,
        latitude: Double,
        longitude: Double,
        onSuccess: (message: String) -> Unit = {},
        onError: (errorMessage: String) -> Unit = {}
    ) {
        viewModelScope.launch {
            tripRepository.forceLogoutAttendance(attendanceId, latitude, longitude)
                .catch { exception ->
                    val errorMsg = exception.message ?: "Unknown error"
                    _forceLogoutState.value = Resource.Error(errorMsg)
                    onError(errorMsg)
                }
                .collect { resource ->
                    _forceLogoutState.value = resource

                    when (resource) {
                        is Resource.Success -> {
                            if (resource.data?.success == 1) {
                                val msg = resource.data?.message ?: "Previous session closed successfully"
                                onSuccess(msg)
                            } else {
                                val errorMsg = resource.data?.message ?: "Failed to close previous session"
                                onError(errorMsg)
                            }
                        }
                        is Resource.Error -> {
                            onError(resource.message ?: "An error occurred")
                        }
                        else -> { /* Loading state */ }
                    }
                }
        }
    }
    fun clearForceLogoutState() {
        _forceLogoutState.value = null
    }

    fun startTripWithApi(
        salesExecutiveId: String,
        latitude: Double,
        longitude: Double,
        onSuccess: (tripId: Int) -> Unit = {},
        onError: (errorMessage: String, onGoingTripId: Int?) -> Unit = { _, _ -> }
    ) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val currentTime = dateFormat.format(Date())
        val status = "ONGOING"

        viewModelScope.launch {
            tripRepository.startTrip(salesExecutiveId, currentTime, status, latitude, longitude)
                .catch { exception ->
                    val errorMsg = exception.message ?: "Unknown error"
                    _tripStartState.value = Resource.Error(errorMsg)
                    onError(errorMsg,null)
                }
                .collect { resource ->
                    _tripStartState.value = resource

                    when (resource) {
                        is Resource.Success -> {
                            if (resource.data?.success == 1) {
                                resource.data.data?.let { tripData ->
                                    // Save trip ID to preferences
                                    preferenceManager.saveTripId(tripData.tripId.toString())
                                    preferenceManager.setTripActive(true)

                                    // Call success callback
                                    onSuccess(tripData.tripId)
                                }
                            } else {
                                val errorMsg = resource.data?.message ?: "Failed to start trip"
                                onError(errorMsg,null)
                            }
                        }

                        is Resource.Error -> {
                            onError(resource.message ?: "An error occurred", resource.onGoingTripId)
                        }

                        else -> { /* Loading state */
                        }
                    }
                }
        }
    }


    fun loginWithApi(
        latitude: Double,
        longitude: Double,
        createdBy: String = "system",
        onSuccess: (message:String) -> Unit = {},
        onError: (errorMessage: String) -> Unit = {}
    ) {
        viewModelScope.launch {
            tripRepository.manageAttendance("LOGIN", latitude, longitude, createdBy)
                .catch { exception ->
                    val errorMsg = exception.message ?: "Unknown error"
                    _loginState.value = Resource.Error(errorMsg)
                    onError(errorMsg)
                }
                .collect { resource ->
                    _loginState.value = resource

                    when (resource) {
                        is Resource.Success -> {
                            if (resource.data?.success == 1) {
                                // Save login info if needed
                                val msg = resource.data?.message ?: "Login Successfully...."
                                onSuccess(msg)
                            } else {
                                val errorMsg = resource.data?.message ?: "Failed to login"
                                onError(errorMsg)
                            }
                        }

                        is Resource.Error -> {
                            onError(resource.message ?: "An error occurred")
                        }

                        else -> {
                            /* Loading state */
                        }
                    }
                }
        }
    }

    fun breakInWithApi(
        latitude: Double,
        longitude: Double,
        onSuccess: (message:String) -> Unit = {},
        onError: (errorMessage: String) -> Unit = {}
    ) {
        viewModelScope.launch {
            tripRepository.manageAttendance("BREAK_IN", latitude, longitude)
                .catch { exception ->
                    val errorMsg = exception.message ?: "Unknown error"
                    _breakInState.value = Resource.Error(errorMsg)
                    onError(errorMsg)
                }
                .collect { resource ->
                    _breakInState.value = resource

                    when (resource) {
                        is Resource.Success -> {
                            if (resource.data?.success == 1) {
                                val msg = resource.data?.message ?: "Break In Successfullyy...."
                                onSuccess(msg)
                            } else {
                                val errorMsg = resource.data?.message ?: "Failed to start break"
                                onError(errorMsg)
                            }
                        }

                        is Resource.Error -> {
                            onError(resource.message ?: "An error occurred")
                        }

                        else -> { /* Loading state */ }
                    }
                }
        }
    }

    fun breakOutWithApi(
        latitude: Double,
        longitude: Double,
        onSuccess: (message:String) -> Unit = {},
        onError: (errorMessage: String) -> Unit = {}
    ) {
        viewModelScope.launch {
            tripRepository.manageAttendance("BREAK_OUT", latitude, longitude)
                .catch { exception ->
                    val errorMsg = exception.message ?: "Unknown error"
                    _breakOutState.value = Resource.Error(errorMsg)
                    onError(errorMsg)
                }
                .collect { resource ->
                    _breakOutState.value = resource

                    when (resource) {
                        is Resource.Success -> {
                            if (resource.data?.success == 1) {
                                val msg = resource.data?.message ?: "Break out successfully..."
                                onSuccess(msg)
                            } else {
                                val errorMsg = resource.data?.message ?: "Failed to end break"
                                onError(errorMsg)
                            }
                        }

                        is Resource.Error -> {
                            onError(resource.message ?: "An error occurred")
                        }

                        else -> { /* Loading state */ }
                    }
                }
        }
    }

    fun logoutWithApi(
        latitude: Double,
        longitude: Double,
        onSuccess: (message: String) -> Unit = {},
        onError: (errorMessage: String) -> Unit = {}
    ) {
        viewModelScope.launch {
            tripRepository.manageAttendance("LOGOUT", latitude, longitude)
                .catch { exception ->
                    val errorMsg = exception.message ?: "Unknown error"
                    _logoutState.value = Resource.Error(errorMsg)
                    onError(errorMsg)
                }
                .collect { resource ->
                    _logoutState.value = resource

                    when (resource) {
                        is Resource.Success -> {
                            if (resource.data?.success == 1) {
                                val apiMessage = resource.data.message ?: "Logout successful"
                                onSuccess(apiMessage)
                            } else {
                                val errorMsg = resource.data?.message ?: "Failed to logout"
                                onError(errorMsg)
                            }
                        }

                        is Resource.Error -> {
                            onError(resource.message ?: "An error occurred")
                        }

                        else -> { /* Loading state */ }
                    }
                }
        }
    }


    fun endTripWithApi(
        tripId: Int,
        salesExecutiveId: String,
        latitude: Double,
        longitude: Double,
        onSuccess: (message: String) -> Unit = {},
        onError: (errorMessage: String) -> Unit = {}
    ) {
        viewModelScope.launch {
            tripRepository.endTrip(salesExecutiveId,tripId, latitude, longitude)
                .catch { exception ->
                    val errorMsg = exception.message ?: "Unknown error"
                    _tripEndState.value = Resource.Error(errorMsg)
                    onError(errorMsg)
                }
                .collect { resource ->
                    _tripEndState.value = resource

                    when (resource) {
                        is Resource.Success -> {
                            if (resource.data?.success == 1) {
                                _isTracking.value = false
                                val apiMessage = resource.data.message ?: "Login successful"
                                onSuccess(apiMessage)
                            } else {
                                val errorMsg = resource.data?.message ?: "Failed to end trip"
                                onError(errorMsg)
                            }
                        }

                        is Resource.Error -> {
                            onError(resource.message ?: "An error occurred")
                        }

                        else -> {
                            /* Loading state */
                        }
                    }
                }
        }
    }

    fun fetchTodayLeads(page: String, limit:String,onComplete: (List<LeadSelection>) -> Unit = {}) {
        viewModelScope.launch {
            _isLoadingLeads.value = true

            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Date())
            val result = tripRepository.fetchTodaysLeads(page,limit,currentDate)

            when (result) {
                is Resource.Success -> {
                    val leads = result.data?.data?.leads?.map {
                        LeadSelection(
                            lead = it.toLeadModel(),
                            isSelected = false
                        )
                    } ?: emptyList()
                    _todaysLeads.value = leads.map { it.lead }
                    onComplete(leads)
                }
                is Resource.Error -> {
//                    _errorMessage.value = result.message ?: "Failed to load leads"
                    onComplete(emptyList())
                }
                else -> {}
            }

            _isLoadingLeads.value = false
        }
    }

    fun continueExistingTrip(existingRouteId: Long) {
        if (_isTracking.value) return

        viewModelScope.launch {
            // Use the existing route instead of creating a new one
            currentRouteId = existingRouteId
            currentRouteStartTime = System.currentTimeMillis()
            sessionStartTimeMillis = System.currentTimeMillis()

            _isTracking.value = true

            if (!hasActiveSession) {
                isFirstLocationOfSession = true
                hasActiveSession = true
                Log.d("StartMarker", "🆕 Continuing existing trip - will capture start point")
            }

            startService()

            collectionJob?.cancel()
            collectionJob = viewModelScope.launch {
                repository.locationUpdates().collect { location ->
                    Log.d("LocationFlow", "Received location in flow: $location")

                    if (isFirstLocationOfSession) {
                        val startLatLng = LatLng(location.latitude, location.longitude)
                        _startLocation.value = startLatLng
                        isFirstLocationOfSession = false
                        Log.d("StartMarker", "📍 Continuing trip start location: $startLatLng")
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
    }

    fun clearTripStartState() {
        _tripStartState.value = null
    }


    fun startTracking() {
        if (_isTracking.value) return

        // Create a new route
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val routeId = routeRepo.createRoute(
                RouteEntity(
                    startTimeMillis = startTime,
                    stopCount = 0
                )
            )

            currentRouteId = routeId
            currentRouteStartTime = startTime
            sessionStartTimeMillis = startTime

            _isTracking.value = true

            if (!hasActiveSession) {
                isFirstLocationOfSession = true
                hasActiveSession = true
                Log.d("StartMarker", "🆕 New session starting - will capture start point")
            }

            startService()

            collectionJob?.cancel()
            collectionJob = viewModelScope.launch {
                repository.locationUpdates().collect { location ->
                    Log.d("LocationFlow", "Received location in flow: $location")

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
    }

    fun getCurrentTripId(): Int? {
        return preferenceManager.getTripId()?.toIntOrNull()
    }
    fun getCurrentSalesExecutiveId(): Int? {
        return preferenceManager.getSalesExecutiveId()
    }

    // In TrackingViewModel.kt - modify existing method
    fun getStopsWithSequence(): List<Pair<String, StopPoint>> {
        return getCompletedStops()
            .sortedWith(compareBy({ it.startTimeMillis }, { it.id }))
            .mapIndexed { index, stop ->
                // ✅ Use stored letter if available, otherwise calculate
                val letter = stop.letter ?: ('A' + index).toString()
                letter to stop
            }
    }

    fun getStopLetter(stopId: Long): String? {
        return getCompletedStops()
            .sortedBy { it.startTimeMillis }
            .mapIndexed { index, stop -> stop.id to ('A' + index).toString() }
            .firstOrNull { it.first == stopId }?.second
    }


    // In TrackingViewModel.kt
    suspend fun getStopById(stopId: Long): StopPoint? {
        return stopRepo.getStopById(stopId)
    }


//    fun stopTracking(googleMap: GoogleMap? = null): Deferred<String?> {
//        return viewModelScope.async {
//            if (!_isTracking.value) return@async null
//
//            val endTime = System.currentTimeMillis()
//
//            // Finalize any open stop
//            val center = stopCenter
//            val startWall = stopStartTime
//            val startElapsedLocal = stopStartElapsed
//            val id = currentStopId
//
//            if (center != null && startWall != null && isCurrentlyStopped && id != null && startElapsedLocal != null) {
//                val nowWall = endTime
//                val nowElapsed = SystemClock.elapsedRealtime()
//                val minutes = ((nowElapsed - startElapsedLocal) / 60000)
//
//                val existingStop = sessionState.stopPoints.value.firstOrNull { it.id == id }
//                val stop = StopPoint(
//                    id = id,
//                    center = center,
//                    startTimeMillis = startWall,
//                    endTimeMillis = nowWall,
//                    timeSpentMinutes = minutes,
//                    // ✅ Preserve the letter from existing stop
//                    letter = existingStop?.letter,
//                    // ✅ Preserve other fields too
//                    name = existingStop?.name,
//                    locationLabel = existingStop?.locationLabel,
//                    address = existingStop?.address,
//                    phone = existingStop?.phone,
//                    imageUri = existingStop?.imageUri
//                )
//                resolveLocationNameAsync(stop)
//                sessionState.addOrUpdateStop(stop)
//                stopRepo.upsertStop(stop, startElapsedLocal, nowElapsed)
//            }
//            _startLocation.value = null
//
//
//            // Get all stops created during this route
//            val routeStartTime = currentRouteStartTime ?: sessionStartTimeMillis ?: 0
//            val stopsInRoute = stopRepo.getStopsInTimeRange(routeStartTime, endTime)
//
//            // Take map screenshot if GoogleMap is available
//            var screenshotPath: String? = null
//            if (googleMap != null) {
//                screenshotPath = takeMapScreenshot(googleMap, currentRouteId ?: 0)
//            }
//
//            // Update the route with end time, stop count, and screenshot path
//            currentRouteId?.let { routeId ->
//                routeRepo.finalizeRoute(
//                    routeId = routeId,
//                    endTime = endTime,
//                    stopCount = stopsInRoute.size,
//                    screenshotPath = screenshotPath
//                )
//            }
//
//            // Stop tracking
//            _isTracking.value = false
//            collectionJob?.cancel()
//            collectionJob = null
//            stopService()
//
//            // Reset state for next route
//            resetStopState()
////            resetSession()
//            sessionState.reset()
//            sessionStartTimeMillis = null
//            currentRouteId = null
//            currentRouteStartTime = null
//
//            return@async screenshotPath
//        }
//    }

    fun stopTracking(googleMap: GoogleMap? = null): Deferred<String?> {
        return viewModelScope.async {
            if (!_isTracking.value) return@async null

            val endTime = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()

            // =====================================================
            // ✅ STEP 1: FINALIZE ALL OPEN STOPS (DB SOURCE OF TRUTH)
            // =====================================================
            val ongoingStops = stopRepo.getOngoingStops()

            ongoingStops.forEach { stopEntity ->

                val minutes = ((nowElapsed - (stopEntity.startElapsedRealtimeMillis ?: nowElapsed)) / 60000)

                // 🔁 Convert Entity → Point
                val stopPoint = StopPoint(
                    id = stopEntity.id,
                    center = LatLng(stopEntity.lat, stopEntity.lng),
                    startTimeMillis = stopEntity.startWallTimeMillis,
                    endTimeMillis = endTime,
                    timeSpentMinutes = minutes,
                    letter = stopEntity.letter,
                    name = stopEntity.name,
                    locationLabel = stopEntity.locationLabel,
                    address = stopEntity.address,
                    phone = stopEntity.phone,
                    imageUri = stopEntity.imageUri,
                    tripId = stopEntity.tripId,
                    salesExecutiveId = stopEntity.salesExecutiveId!!
                )

                // ✅ Update UI if exists
                val existingStop = sessionState.stopPoints.value
                    .firstOrNull { it.id == stopPoint.id }

                if (existingStop != null) {
                    val updated = existingStop.copy(
                        endTimeMillis = endTime,
                        timeSpentMinutes = minutes
                    )
                    sessionState.addOrUpdateStop(updated)
                }

                // ✅ Save to DB
                stopRepo.upsertStop(
                    stopPoint,
                    stopEntity.startElapsedRealtimeMillis,
                    nowElapsed
                )

                Log.d("StopDebug", "✅ FORCE FINALIZED STOP: ${stopPoint.id}")
            }


            _startLocation.value = null


            // Get all stops created during this route
            val routeStartTime = currentRouteStartTime ?: sessionStartTimeMillis ?: 0
            val stopsInRoute = stopRepo.getStopsInTimeRange(routeStartTime, endTime)

            // Take map screenshot if GoogleMap is available
            var screenshotPath: String? = null
            if (googleMap != null) {
                screenshotPath = takeMapScreenshot(googleMap, currentRouteId ?: 0)
            }

            // Update the route with end time, stop count, and screenshot path
            currentRouteId?.let { routeId ->
                routeRepo.finalizeRoute(
                    routeId = routeId,
                    endTime = endTime,
                    stopCount = stopsInRoute.size,
                    screenshotPath = screenshotPath
                )
            }

            // Stop tracking
            _isTracking.value = false
            collectionJob?.cancel()
            collectionJob = null
            stopService()

            // Reset state for next route
            resetStopState()
            sessionState.reset()
            sessionStartTimeMillis = null
            currentRouteId = null
            currentRouteStartTime = null

            return@async screenshotPath
        }
    }

    /**
     * Take a screenshot of the current map and save it to internal storage
     */
    /*private fun takeMapScreenshot(googleMap: GoogleMap, routeId: Long): String? {
        var screenshotPath: String? = null
        val latch = CountDownLatch(1)

        // Check if we have route points
        val routePoints = sessionState.routePoints.value
        val stopPoints = sessionState.stopPoints.value

        // Store current camera position to restore later
        val currentPosition = googleMap.cameraPosition

        // Function to actually take and save screenshot
        fun captureAndSave() {
            googleMap.snapshot { bitmap ->
                if (bitmap != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val dir = File(getApplication<Application>().filesDir, "route_screenshots")
                            if (!dir.exists()) dir.mkdirs()

                            val file = File(dir, "route_$routeId.jpg")
                            if (file.exists()) file.delete()

                            FileOutputStream(file).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            }

                            screenshotPath = file.absolutePath
                            Log.d("SCREENSHOT", "Screenshot saved: $screenshotPath")
                            routeRepo.updateRouteScreenshot(routeId, file.absolutePath)
                        } catch (e: Exception) {
                            Log.e("SCREENSHOT", "Error saving screenshot", e)
                        } finally {
                            // Restore camera position
                            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(currentPosition), 500, null)
                            latch.countDown()
                        }
                    }
                } else {
                    Log.e("SCREENSHOT", "Bitmap is null")
                    latch.countDown()
                }
            }
        }

        // If we have points, zoom to show everything
        if (routePoints.isNotEmpty() || stopPoints.isNotEmpty()) {
            try {
                val builder = LatLngBounds.Builder()
                routePoints.forEach { builder.include(it.latLng) }
                stopPoints.forEach { builder.include(it.center) }
                startLocation.value?.let { builder.include(it) }

                val bounds = builder.build()

                // Animate to show entire route, then capture
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, 200),
                    object : GoogleMap.CancelableCallback {
                        override fun onFinish() {
                            // Wait for labels to render
                            Handler(Looper.getMainLooper()).postDelayed({
                                captureAndSave()
                            }, 1500)
                        }

                        override fun onCancel() {
                            captureAndSave() // Still capture even if cancelled
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("SCREENSHOT", "Error calculating bounds", e)
                captureAndSave() // Fallback to current view
            }
        } else {
            // No points, just capture current view
            captureAndSave()
        }

        try {
            latch.await(5, TimeUnit.SECONDS) // Wait up to 5 seconds
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        return screenshotPath
    }*/


    /*private fun takeMapScreenshot(googleMap: GoogleMap, routeId: Long): String? {

        var screenshotPath: String? = null
        val latch = CountDownLatch(1)

        val routePoints = sessionState.routePoints.value
        val stopPoints = sessionState.stopPoints.value

        val currentPosition = googleMap.cameraPosition

        fun captureAndSave() {
            googleMap.snapshot { bitmap ->
                if (bitmap != null) {

                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val dir = File(getApplication<Application>().filesDir, "route_screenshots")
                            if (!dir.exists()) dir.mkdirs()

                            val file = File(dir, "route_$routeId.jpg")
                            if (file.exists()) file.delete()

                            FileOutputStream(file).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }

                            screenshotPath = file.absolutePath
                            routeRepo.updateRouteScreenshot(routeId, screenshotPath!!)

                            Log.d("SCREENSHOT", "✅ Saved: $screenshotPath")

                        } catch (e: Exception) {
                            Log.e("SCREENSHOT", "❌ Save error", e)
                        }

                        withContext(Dispatchers.Main) {
                            googleMap.animateCamera(
                                CameraUpdateFactory.newCameraPosition(currentPosition)
                            )
                            latch.countDown()
                        }
                    }

                } else {
                    Log.e("SCREENSHOT", "❌ Bitmap null")
                    latch.countDown()
                }
            }
        }

        if (routePoints.isNotEmpty() || stopPoints.isNotEmpty()) {
            try {

                val allPoints = mutableListOf<LatLng>()

                routePoints.forEach { allPoints.add(it.latLng) }
                stopPoints.forEach { allPoints.add(it.center) }
                startLocation.value?.let { allPoints.add(it) }

                if (allPoints.isEmpty()) {
                    captureAndSave()
                } else {

                    val builder = LatLngBounds.Builder()
                    allPoints.forEach { builder.include(it) }

                    val bounds = builder.build()

                    // 🔥 IMPORTANT FIXES
                    val padding = 300 // more padding for full route

                    googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
                    googleMap.uiSettings.setAllGesturesEnabled(false)

                    // ✅ Reset padding (avoid UI crop)
                    googleMap.setPadding(0, 0, 0, 0)

                    // ✅ Move instantly (better than animate)
                    googleMap.moveCamera(
                        CameraUpdateFactory.newLatLngBounds(bounds, padding)
                    )

                    // ✅ Extra zoom out (VERY IMPORTANT for large routes)
                    googleMap.animateCamera(CameraUpdateFactory.zoomOut())
                    googleMap.animateCamera(CameraUpdateFactory.zoomOut())

                    // ✅ Wait until map fully loaded (BEST FIX)
                    googleMap.setOnMapLoadedCallback {
                        Log.d("SCREENSHOT", "Map loaded → capturing")
                        captureAndSave()
                    }
                }

            } catch (e: Exception) {
                Log.e("SCREENSHOT", "❌ Bounds error", e)
                captureAndSave()
            }

        } else {
            captureAndSave()
        }

        try {
            latch.await(10, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        return screenshotPath
    }*/


    // In TrackingViewModel - Change parameter type from Int to Long
    private suspend fun takeMapScreenshot(googleMap: GoogleMap, routeId: Long): String? {
        return suspendCancellableCoroutine { continuation ->
            var screenshotPath: String? = null
            val routePoints = sessionState.routePoints.value
            val stopPoints = sessionState.stopPoints.value
            val startLocationValue = _startLocation.value

            // Store current camera position
            val currentPosition = googleMap.cameraPosition

            fun captureAndSave() {
                googleMap.snapshot { bitmap ->
                    if (bitmap != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val dir = File(
                                    getApplication<Application>().filesDir,
                                    "route_screenshots"
                                )
                                if (!dir.exists()) dir.mkdirs()

                                val file = File(dir, "route_$routeId.png")
                                if (file.exists()) file.delete()

                                val scaledBitmap = Bitmap.createScaledBitmap(
                                    bitmap,
                                    bitmap.width * 2,
                                    bitmap.height * 2,
                                    true
                                )

                                FileOutputStream(file).use { out ->
                                    scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }

                                screenshotPath = file.absolutePath
                                routeRepo.updateRouteScreenshot(routeId, screenshotPath!!)
                                Log.d("SCREENSHOT", "✅ Saved: $screenshotPath")
                                scaledBitmap.recycle()

                            } catch (e: Exception) {
                                Log.e("SCREENSHOT", "❌ Save error", e)
                            } finally {
                                // Restore camera position on main thread
                                withContext(Dispatchers.Main) {
                                    googleMap.animateCamera(
                                        CameraUpdateFactory.newCameraPosition(currentPosition),
                                        300,
                                        null
                                    )
                                    continuation.resume(screenshotPath)
                                }
                            }
                        }
                    } else {
                        Log.e("SCREENSHOT", "❌ Bitmap null")
                        continuation.resume(null)
                    }
                }
            }

            // Execute on main thread - use Handler instead of withContext
            Handler(Looper.getMainLooper()).post {
                try {
                    val allPoints = mutableListOf<LatLng>()
                    routePoints.forEach { allPoints.add(it.latLng) }
                    stopPoints.forEach { allPoints.add(it.center) }
                    startLocationValue?.let { allPoints.add(it) }

                    if (allPoints.isEmpty()) {
                        captureAndSave()
                    } else {
                        val builder = LatLngBounds.Builder()
                        allPoints.forEach { builder.include(it) }
                        val bounds = builder.build()
                        val padding = 200

                        googleMap.animateCamera(
                            CameraUpdateFactory.newLatLngBounds(bounds, padding),
                            object : GoogleMap.CancelableCallback {
                                override fun onFinish() {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        captureAndSave()
                                    }, 800)
                                }

                                override fun onCancel() {
                                    captureAndSave()
                                }
                            }
                        )
                    }
                } catch (e: Exception) {
                    Log.e("SCREENSHOT", "❌ Bounds error", e)
                    captureAndSave()
                }
            }
        }
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
        _startLocation.value = null
        isFirstLocationOfSession = true
        hasActiveSession = false
        currentRouteId = null
        currentRouteStartTime = null
        // Signal that start marker should be removed
        _startLocation.value = null

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



    fun restoreTrackingStateFromDatabase() {
        viewModelScope.launch {
            try {
                // Check if there's an ongoing route in database
                val ongoingRoute = routeRepo.getOngoingRoute()

                if (ongoingRoute != null && ongoingRoute.endTimeMillis == null) {
                    Log.d("StateRestore", "✅ Found ongoing route: ${ongoingRoute.id}")

                    // There's an ongoing route, restore tracking state
                    currentRouteId = ongoingRoute.id
                    currentRouteStartTime = ongoingRoute.startTimeMillis
                    sessionStartTimeMillis = ongoingRoute.startTimeMillis

                    // 🔥 CRITICAL FIX: Load and restore route points
                    val routePoints = routeRepo.getRoutePoints(ongoingRoute.id)
                    Log.d("StateRestore", "Loading ${routePoints.size} route points")

                    // Clear existing points first
                    sessionState.reset()

                    // Add each point to session state
                    routePoints.forEach { point ->
                        val locationPoint = LocationPoint(
                            latLng = LatLng(point.latitude, point.longitude),
                            timestampMillis = point.timestamp
                        )
                        sessionState.addLocation(locationPoint)
                    }
                    Log.d("StateRestore", "✅ Restored ${routePoints.size} route points to session state")

                    // 🔥 CRITICAL FIX: Load and restore stops
                    val stops = routeRepo.getStopsForRoute(ongoingRoute.id)
                    Log.d("StateRestore", "Loading ${stops.size} stops")

                    stops.forEach { stop ->
                        sessionState.addOrUpdateStop(stop)
                    }
                    Log.d("StateRestore", "✅ Restored ${stops.size} stops to session state")

                    // Restore start location from route points if available
                    if (routePoints.isNotEmpty()) {
                        val startPoint = routePoints.first()
                        _startLocation.value = LatLng(startPoint.latitude, startPoint.longitude)
                        Log.d("StateRestore", "✅ Restored start location")
                    }

                    _isTracking.value = true
                    hasActiveSession = true

                    Log.d("StateRestore", "✅ Tracking state restored - Route: ${ongoingRoute.id}, Points: ${routePoints.size}, Stops: ${stops.size}")
                } else {
                    Log.d("StateRestore", "No ongoing route found in database")
                    _isTracking.value = false
                    sessionStartTimeMillis = null
                    currentRouteId = null
                    currentRouteStartTime = null
                }
            } catch (e: Exception) {
                Log.e("StateRestore", "Error restoring tracking state", e)
            }
        }
    }






    private suspend fun handleLocation(location: Location) {
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val latLng = LatLng(location.latitude, location.longitude)
        val speedKmh = location.speed * 3.6f
        val center = stopCenter


        if (TEST_MODE) {
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

                sessionState.addLocation(LocationPoint(latLng, System.currentTimeMillis()))

                // ✅ ADD THIS - Save to database for route points
                val point = LocationPoint(latLng, nowWall)
                viewModelScope.launch(Dispatchers.IO) {
                    currentRouteId?.let { routeId ->
                        routeRepo.addPoint(routeId, point)
                    }
                }

                showUserToast("🧪 TEST MODE: Waiting 10 seconds")

                return
            }

            val distance = GeoUtils.distanceMeters(stopCenter!!, latLng)
            val elapsedSeconds = (nowElapsed - (stopStartElapsed ?: nowElapsed)) / 1000

            Log.d("StopDebug", "🧪 TEST MODE elapsed: $elapsedSeconds sec, distance: $distance m")

            // ✅ ADD THIS - Save location point for polyline on EVERY location update
            sessionState.addLocation(LocationPoint(latLng, System.currentTimeMillis()))

            // ✅ ADD THIS - Save to database for route points
            val point = LocationPoint(latLng, nowWall)
            viewModelScope.launch(Dispatchers.IO) {
                currentRouteId?.let { routeId ->
                    routeRepo.addPoint(routeId, point)
                }
            }

            // =====================================================
            // CREATE STOP AFTER 10 SECONDS (if still in same area)
            // =====================================================
            if (!isCurrentlyStopped && elapsedSeconds >= TEST_STOP_SECONDS && distance <= 10) {
                currentStopId = System.currentTimeMillis()

                // Use the original start time when user first stopped
                val stopStartTime = stopStartTime ?: nowWall

//                val existingStopsCount = sessionState.stopPoints.value.size
//                val letter = ('A'.plus(existingStopsCount)).toString()
//
//                val stop = StopPoint(
//                    id = currentStopId!!,
//                    center = stopCenter!!,
//                    startTimeMillis = stopStartTime,
//                    endTimeMillis = null,  // Ongoing stop
//                    timeSpentMinutes = 0,
//                    letter = letter
//                )

                // NEW CODE - Get stops count for current route only
                val currentRouteStops = if (currentRouteId != null) {
                    routeRepo.getStopsForRoute(currentRouteId!!)
                } else {
                    emptyList()
                }
                val existingStopsCount = currentRouteStops.size
                val letter = ('A'.plus(existingStopsCount)).toString()

                val stop = StopPoint(
                    id = currentStopId!!,
                    center = stopCenter!!,
                    startTimeMillis = stopStartTime,
                    endTimeMillis = null,  // Ongoing stop
                    timeSpentMinutes = 0,
                    letter = letter,
                    tripId = getCurrentTripId().toString(),
                    salesExecutiveId = getCurrentSalesExecutiveId().toString()
                )

                // ✅ Use the new force update method
                // Update UI
                sessionState.addStopAndForceUpdate(stop)

                // ✅ ADD THIS - Save to database!
                viewModelScope.launch {
                    stopRepo.upsertStop(stop, stopStartElapsed ?: nowElapsed, null)
                    Log.d("StopDebug", "💾 TEST STOP SAVED TO DB: ID=${stop.id}")
                    currentRouteId?.let { routeId ->
                        routeRepo.linkStopToRoute(routeId, stop.id)
                        Log.d("ROUTE_DEBUG", "✅ Linked stop ${stop.id} to route $routeId")
                    }
                }

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
                    val existingStop =
                        sessionState.stopPoints.value.firstOrNull { it.id == currentStopId }
                    if (existingStop != null) {
                        val finalizedStop = existingStop.copy(
                            endTimeMillis = nowWall,
                            timeSpentMinutes = minutes
                        )
                        // ✅ Use force update method here too
                        sessionState.addStopAndForceUpdate(finalizedStop)
                        // ✅ ADD THIS - Update in database with end time!
                        viewModelScope.launch {
                            stopRepo.upsertStop(
                                finalizedStop,
                                stopStartElapsed ?: nowElapsed,
                                nowElapsed
                            )
                            Log.d(
                                "StopDebug",
                                "💾 TEST STOP FINALIZED IN DB: ID=${finalizedStop.id}"
                            )
                        }

                        Log.d("StopDebug", "✅ Stop FINALIZED: ${minutes}m ${seconds}s")
                        showUserToast("✅ Stop finished: ${minutes}m ${seconds}s")

                        // Show updated stop count
                        val completedStops =
                            sessionState.stopPoints.value.filter { it.endTimeMillis != null }.size
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
                val completedStops =
                    sessionState.stopPoints.value.filter { it.endTimeMillis != null }.size
                val ongoingStops = totalStops - completedStops

                Log.d(
                    "StopDebug",
                    "📊 Stats - Total: $totalStops, Completed: $completedStops, Ongoing: $ongoingStops"
                )

                // Show the current order
                val stops = sessionState.stopPoints.value.sortedBy { it.startTimeMillis }
                stops.forEachIndexed { index, s ->
                    val letter = ('A' + index).toString()
                    val status = if (s.endTimeMillis == null) "🟡" else "✅"
                    Log.d("StopDebug", "   $letter$status: ${s.startTimeMillis}")
                }
            }

            return
        }



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
//        val point = LocationPoint(latLng, nowWall)
//        viewModelScope.launch(Dispatchers.IO) {
//            routeRepo.add(point)
//        }

        val point = LocationPoint(latLng, nowWall)
        viewModelScope.launch(Dispatchers.IO) {
            currentRouteId?.let { routeId ->
                routeRepo.addPoint(routeId, point)  // You need to modify this method
            }
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
//                        val existingStopsCount = sessionState.stopPoints.value.size
                        val currentRouteStops = if (currentRouteId != null) {
                            routeRepo.getStopsForRoute(currentRouteId!!)
                        } else {
                            emptyList()
                        }
                        val existingStopsCount = currentRouteStops.size
                        val letter = ('A'.plus(existingStopsCount)).toString()


                        val stop = StopPoint(
                            id = currentStopId!!,
                            center = center,
                            startTimeMillis = stopStartTime ?: nowWall,
                            endTimeMillis = null,
                            timeSpentMinutes = minutes,
                            letter = letter,
                            tripId = getCurrentTripId().toString(),
                            salesExecutiveId = getCurrentSalesExecutiveId().toString()
                        )

                        sessionState.addOrUpdateStop(stop)
                        viewModelScope.launch {
                            stopRepo.upsertStop(stop, stopStartElapsed ?: nowElapsed, null)
                            currentRouteId?.let { routeId ->
                                routeRepo.linkStopToRoute(routeId, stop.id)
                                Log.d("ROUTE_DEBUG", "✅ Linked stop ${stop.id} to route $routeId")
                            }
                        }
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
                                timeSpentMinutes = minutes,
                                tripId = getCurrentTripId().toString(),
                                salesExecutiveId = getCurrentSalesExecutiveId().toString()
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
                            timeSpentMinutes = minutes,
                            tripId = getCurrentTripId().toString(),
                            salesExecutiveId = getCurrentSalesExecutiveId().toString()
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


    suspend fun getStopsForRoute(routeId: Long): List<StopPoint> {
        return routeRepo.getStopsForRoute(routeId)
    }

    // Add this method to get route details
    suspend fun getRouteDetails(routeId: Long): RouteEntity? {
        return routeRepo.getRouteById(routeId)
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

            resolveLocationNameAsync(finalizedStop)
            // Save to repository
            viewModelScope.launch {
                stopRepo.upsertStop(finalizedStop, stopStartElapsed ?: nowElapsed, nowElapsed)
            }


        } else {
            Log.e("StopDebug", "❌ Cannot finalize: stop not found in session")
            // Create it as a last resort
            val newStop = StopPoint(
                id = stopId,
                center = center,
                startTimeMillis = stopStartTime ?: nowWall,
                endTimeMillis = nowWall,
                timeSpentMinutes = minutes,
                tripId = getCurrentTripId().toString(),
                salesExecutiveId = getCurrentSalesExecutiveId().toString()
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
                timeSpentMinutes = minutes,
                tripId = getCurrentTripId().toString(),
                salesExecutiveId = getCurrentSalesExecutiveId().toString()
            )

            sessionState.addOrUpdateStop(stop)

            val startElapsedLocal = stopStartElapsed ?: nowElapsed
            resolveLocationNameAsync(stop)
            viewModelScope.launch {
                stopRepo.upsertStop(stop, startElapsedLocal, nowElapsed)
            }


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
                val results =
                    geocoder.getFromLocation(stop.center.latitude, stop.center.longitude, 1)
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

    fun clearLoginState() {
        _loginState.value = null
    }

    fun clearBreakInState() {
        _breakInState.value = null
    }

    fun clearBreakOutState() {
        _breakOutState.value = null
    }

    fun clearLogoutState() {
        _logoutState.value = null
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
                    timeSpentMinutes = totalMinutes,
                    tripId = getCurrentTripId().toString(),
                    salesExecutiveId = getCurrentSalesExecutiveId().toString()
                )
            }
        val unnamed = completed.filter { it.name.isNullOrBlank() }
        return named + unnamed
    }

//    fun getSessionElapsedSeconds(): Long {
//        val start = sessionStartTimeMillis
//        return if (_isTracking.value && start != null) {
//            (System.currentTimeMillis() - start) / 1000
//        } else 0
//    }

    fun updateStopDetails(
        id: Long,
        name: String,
        address: String?,
        phone: String?,
        imageUri: String?
    ) {
        // Update in-memory
        val existing = stopPoints.value.firstOrNull { it.id == id } ?: return
        val updated =
            existing.copy(name = name, address = address, phone = phone, imageUri = imageUri)
        sessionState.addOrUpdateStop(updated)
        // Persist
        viewModelScope.launch {
            // Upsert with same duration and times
            stopRepo.upsertStop(updated, 0, updated.endTimeMillis?.let { 0L })
        }
    }

    // In TripViewModel.kt
    fun cancelOngoingRequests() {
        viewModelScope.coroutineContext.cancelChildren()
    }

    // In TrackingViewModel.kt

    // Direct methods to bypass any flow issues
    private var originalStartTimeMillis: Long? = null

    fun setOriginalStartTime(startTime: Long) {
        originalStartTimeMillis = startTime
        sessionStartTimeMillis = startTime  // ← Set session start time to original
        Log.d("ViewModel", "✅ Original start time set to: $startTime")
    }

    fun continueExistingTripWithOriginalTime(existingRouteId: Long, originalStartTime: Long) {
        if (_isTracking.value) return

        viewModelScope.launch {
            // Use the existing route instead of creating a new one
            currentRouteId = existingRouteId
            currentRouteStartTime = originalStartTime  // ← Use original time
            sessionStartTimeMillis = originalStartTime  // ← Use original time

            _isTracking.value = true

            if (!hasActiveSession) {
                isFirstLocationOfSession = true
                hasActiveSession = true
                Log.d("StartMarker", "🆕 Continuing existing trip - will capture start point")
            }

            startService()

            collectionJob?.cancel()
            collectionJob = viewModelScope.launch {
                repository.locationUpdates().collect { location ->
                    Log.d("LocationFlow", "Received location in flow: $location")

                    if (isFirstLocationOfSession) {
                        val startLatLng = LatLng(location.latitude, location.longitude)
                        _startLocation.value = startLatLng
                        isFirstLocationOfSession = false
                        Log.d("StartMarker", "📍 Continuing trip start location: $startLatLng")
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
    }

    // Update getSessionElapsedSeconds to use originalStartTimeMillis
    fun getSessionElapsedSeconds(): Long {
        val start = sessionStartTimeMillis ?: originalStartTimeMillis
        return if (_isTracking.value && start != null) {
            (System.currentTimeMillis() - start) / 1000
        } else 0
    }

    // Direct methods to bypass any flow issues
    fun addLocationPointDirectly(point: LocationPoint) {
        sessionState.addLocation(point)
        Log.d("ViewModel", "Directly added location point. Total: ${sessionState.routePoints.value.size}")
    }

    fun addStopPointDirectly(stop: StopPoint) {
        sessionState.addOrUpdateStop(stop)
        Log.d("ViewModel", "Directly added stop: ${stop.id}")
    }

    fun stopTrackingForRestore() {
        // Cancel collection job
        collectionJob?.cancel()
        collectionJob = null

        // Stop service
        stopService()

        // Reset flags but keep route ID
        _isTracking.value = false
        hasActiveSession = false
        isFirstLocationOfSession = true

        Log.d("ViewModel", "Stopped tracking for restore")
    }

    fun setCurrentRouteId(routeId: Long) {
        currentRouteId = routeId
        Log.d("ViewModel", "✅ CurrentRouteId set to: $routeId")
    }

    fun setTrackingState(tracking: Boolean) {
        _isTracking.value = tracking
        Log.d("ViewModel", "✅ Tracking state set to: $tracking")
    }

    fun getCurrentRouteId(): Long? = currentRouteId

    // In TrackingViewModel.kt
    fun resetTrackingStateForAttendanceLogout() {
        _isTracking.value = false
//        _routePoints.value = emptyList()
//        _stopPoints.value = emptyList()
        _startLocation.value = null
//        _sessionElapsedSeconds.value = 0
    }

}


//        suspend fun getRouteBetween(startMillis: Long, endMillis: Long): List<LocationPoint> {
//            return routeRepo.getBetween(startMillis, endMillis)
//        }


class TrackingViewModelFactory(
    private val application: Application,
    private val tripRepository: TripRepository,
    private val preferenceManager: PreferenceManager
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrackingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TrackingViewModel(application, tripRepository, preferenceManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}