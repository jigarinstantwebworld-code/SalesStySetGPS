package com.styset.sales.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.styset.sales.app.data.StopRepository
import com.styset.sales.app.data.network.NetworkClient
import com.styset.sales.app.location.RouteRepository
import com.styset.sales.app.models.AttendanceData
import com.styset.sales.app.models.AttendanceResponse
import com.styset.sales.app.models.BreakData
import com.styset.sales.app.models.Resource
import com.styset.sales.app.presentation.viewmodel.base.BaseActivity
import com.styset.sales.app.repository.TripRepository
import com.styset.sales.app.util.PreferenceManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.styset.sales.app.databinding.ActivityAttendanceBinding
import com.styset.sales.app.workers.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Route
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.getValue

class ActivityAttendance : BaseActivity<ActivityAttendanceBinding>() {

    //    private lateinit var viewModel: AttendanceViewModel
    private val viewModel: TrackingViewModel by viewModels {
        val preferenceManager = PreferenceManager.Companion.getInstance(this@ActivityAttendance)
        val apiService = NetworkClient.apiService
        val tripRepository = TripRepository(apiService, preferenceManager)
        TrackingViewModelFactory(
            this@ActivityAttendance.application,
            tripRepository,
            preferenceManager
        )
    }

    private lateinit var syncManager: SyncManager


    private var isPreviousSessionHandled = false  // Add this with other variables
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var stopRepository: StopRepository
    private lateinit var routeRepository: RouteRepository

    private var currentLocation: LatLng? = null
    private var isOnBreak = false

    private lateinit var preferenceManager: PreferenceManager

    private var breakCount = 0
    private val breakList = mutableListOf<BreakData>()

    // Timer variables
    private var workStartTime: Long = 0
    private var workTimerJob: Job? = null
    private var breakStartTime: Long = 0
    private var breakTimerJob: Job? = null
    private var currentAttendanceId: Int = 0

    private var lastLoginInfo: String? = null
    private var isProcessingSession = false
    private var isAttendanceLoggedIn = false


    override fun getViewBinding(): ActivityAttendanceBinding {
        return ActivityAttendanceBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        enableEdgeToEdge()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        stopRepository = StopRepository(this)
        routeRepository = RouteRepository(this)

        preferenceManager = PreferenceManager(this)

        syncManager = SyncManager(this)


        // Get passed location from intent
        val passedLatitude = intent.getDoubleExtra("latitude", Double.NaN)
        val passedLongitude = intent.getDoubleExtra("longitude", Double.NaN)
        val hasLocation = intent.getBooleanExtra("has_location", false)

        currentLocation = if (hasLocation && !passedLatitude.isNaN() && !passedLongitude.isNaN()) {
            LatLng(passedLatitude, passedLongitude)
        } else {
            null
        }

        // Load saved states
//        isLoggedIn = preferenceManager.isLoggedIn()
//        isOnBreak = preferenceManager.isOnBreak()

//        updateUIForState()

        initializeSession()

        setupWindowInsets()
    }

    private fun initializeSession() {
        displayLastLoginInfo()

        isPreviousSessionHandled = false
        // IMPORTANT: Check if attendance is logged in for today
        // This method checks UTC date to ensure it's today's login
        loadLastLoginInfo()
        displayLastLoginInfo()

        isAttendanceLoggedIn = preferenceManager.isAttendanceLoggedIn()

        if (isAttendanceLoggedIn) {
            // User has an active attendance session for today
            loadCurrentSession()
        } else {
            // No active attendance session
            val hasIncompleteSession = preferenceManager.hasIncompletePreviousSession()
            val dialogAlreadyShown = preferenceManager.isPreviousSessionDialogShown()
            val sessionAlreadyHandled = preferenceManager.isPreviousSessionHandled()

            Log.d("Attendance", "Session Check - hasIncompleteSession: $hasIncompleteSession, " +
                    "dialogAlreadyShown: $dialogAlreadyShown, " +
                    "sessionAlreadyHandled: $sessionAlreadyHandled, " +
                    "isProcessingSession: $isProcessingSession")

            if (hasIncompleteSession && !dialogAlreadyShown && !sessionAlreadyHandled && !isProcessingSession && !isPreviousSessionHandled) {
                showPreviousSessionDialog()
            } else {
                updateUIForState()
            }
        }
    }

    private fun loadCurrentSession() {
        // Load all attendance data
        isOnBreak = preferenceManager.isOnBreak()
        currentAttendanceId = preferenceManager.getAttendanceId()
        workStartTime = preferenceManager.getWorkStartTime()
        breakCount = preferenceManager.getBreakCount()
        breakStartTime = preferenceManager.getBreakStartTime()

        breakList.clear()
        breakList.addAll(preferenceManager.getBreaksList())

        // Start appropriate timers
        if (isOnBreak) {
            startBreakTimer()
        } else {
            startWorkTimer()
        }

        updateUIForState()
        updateStatistics()

        // Show a toast to inform user about active session
        showToast("Welcome back! Your attendance session is active.")
    }

    private fun loadLastLoginInfo() {
        val lastLoginTime = preferenceManager.getLastLoginTime()
        val lastLoginDate = preferenceManager.getLastLoginDate()

        if (!lastLoginTime.isNullOrEmpty()) {
            lastLoginInfo = "Last Login: ${formatTimeFromString(lastLoginTime)} on ${
                formatDisplayDate(lastLoginDate)
            }"
        }
    }

    private fun formatTimeFromString(timeString: String?): String {
        if (timeString.isNullOrBlank()) return "N/A"
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            format.timeZone = TimeZone.getTimeZone("UTC")
            val date = format.parse(timeString)
            val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            timeString
        }
    }

    private fun getCurrentDate(): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return format.format(Date())
    }

    private fun formatDisplayDate(dateString: String?): String {
        if (dateString.isNullOrBlank()) return "N/A"
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = format.parse(dateString)
            val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }

    private fun displayLastLoginInfo() {
        if (!lastLoginInfo.isNullOrEmpty()) {
            binding.tvLastLoginInfo.visibility = View.VISIBLE
            binding.tvLastLoginInfo.text = lastLoginInfo
        } else {
            binding.tvLastLoginInfo.visibility = View.GONE
        }
    }

    private fun showPreviousSessionDialog() {
        val lastLoginDate = preferenceManager.getLastLoginDate()
        val lastLoginTime = preferenceManager.getLastLoginTime()
        val lastLoginCoordinates = preferenceManager.getLastLoginCoordinates()

        preferenceManager.setPreviousSessionDialogShown(true)
        preferenceManager.setHasIncompletePreviousSession(true)

        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Previous Day Session Found")
            .setMessage(
                "We found an incomplete attendance session from:\n\n" +
                        "📅 Date: ${formatDisplayDate(lastLoginDate)}\n" +
                        "⏰ Time: ${formatTimeFromString(lastLoginTime)}\n" +
                        "📍 Location: $lastLoginCoordinates\n\n" +
                        "This session was not closed properly. Would you like to close it and start a new session for today?"
            )
            .setPositiveButton("Yes, Close & Start New") { _, _ ->
                handlePreviousSessionLogout()
            }
            .setNegativeButton("Later") { _, _ ->
                preferenceManager.setPreviousSessionHandled(true)
                updateUIForState()
                displayLastLoginInfo()
            }
            .setCancelable(false)
            .show()
    }

    private fun handlePreviousSessionLogout() {
        if (isProcessingSession) return
        isProcessingSession = true

        showProgress("Closing previous session...")

        val lastAttendanceId = preferenceManager.getLastAttendanceId()

        if (lastAttendanceId != 0) {
            currentLocation?.let { location ->
                viewModel.forceLogoutWithAttendanceId(
                    attendanceId = lastAttendanceId,
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            } ?: run {
                viewModel.forceLogoutWithAttendanceId(
                    attendanceId = lastAttendanceId,
                    latitude = 0.0,
                    longitude = 0.0
                )
            }
        } else {
            hideProgress()
            isProcessingSession = false
            updateUIForState()
        }


    }

    override fun setupObservers() {
        // Observe Login State
        observeStateFlow(viewModel.loginState) { resource ->
            handleLoginResponse(resource)
        }

        // Observe Break In State
        observeStateFlow(viewModel.breakInState) { resource ->
            handleBreakInResponse(resource)
        }

        // Observe Break Out State
        observeStateFlow(viewModel.breakOutState) { resource ->
            handleBreakOutResponse(resource)
        }

        // Observe Logout State
        observeStateFlow(viewModel.logoutState) { resource ->
            handleLogoutResponse(resource)
        }

        observeStateFlow(viewModel.forceLogoutState) { resource ->
            handleForceLogoutResponse(resource)
        }


    }

    private fun handleForceLogoutResponse(resource: Resource<AttendanceResponse>?) {
        when (resource) {
            is Resource.Loading -> {
                // Already showing progress
            }
            is Resource.Success -> {
                hideProgress()

                if (resource.data?.success == 1) {
                    // Clear previous session data
                    preferenceManager.clearAttendanceData()
                    preferenceManager.setAttendanceLoggedIn(false)
                    preferenceManager.setHasIncompletePreviousSession(false)
                    preferenceManager.clearLastSessionData()
                    isPreviousSessionHandled = true

                    // Just clear the flags once
                    preferenceManager.clearPreviousSessionFlags()


                    showToast("Previous session closed successfully")

                    // Now auto login for today
                    showAutoLoginDialog()
                } else {
                    showErrorDialog("Error", resource.data?.message ?: "Failed to close previous session")
                    updateUIForState()
                    displayLastLoginInfo()
                }
                isProcessingSession = false
                viewModel.clearForceLogoutState()
            }
            is Resource.Error -> {
                hideProgress()
                showErrorDialog("Error", resource.message ?: "Failed to close previous session. Please try manual login.")
                updateUIForState()
                displayLastLoginInfo()
                isProcessingSession = false
                viewModel.clearForceLogoutState()
            }
            else -> {}
        }
    }

    private fun showAutoLoginDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Ready for Today")
            .setMessage("Previous session closed successfully. Would you like to login for today?")
            .setPositiveButton("Yes, Login") { _, _ ->
                currentLocation?.let { location ->
                    viewModel.loginWithApi(
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                } ?: run {
                    showErrorDialog("Location Error", "Please enable location and try again")
                    updateUIForState()
                }
            }
            .setNegativeButton("Later") { _, _ ->
                updateUIForState()
                displayLastLoginInfo()
            }
            .setOnDismissListener {
                // Ensure processing session flag is reset
                isProcessingSession = false
            }
            .show()
    }

    override fun setupListeners() {
        binding.btnLogin.setOnClickListener {
//            performLogin()
            showLoginConfirmationDialog()
        }

        binding.btnBreakIn.setOnClickListener {
//            performBreakIn()
            showBreakInConfirmationDialog()
        }

        binding.btnBreakOut.setOnClickListener {
//            performBreakOut()
            showBreakOutConfirmationDialog()
        }

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        binding.btnViewBreakHistory.setOnClickListener {
            showBreakHistoryDialog()
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

    }

    override fun loadData() {
        setupTimerCards()
    }

    private fun setupTimerCards() {
        // Work timer card
        binding.cardWorkTimer.setOnClickListener {
            showWorkTimeDetails()
        }

        // Current break timer card
        binding.cardBreakTimer.setOnClickListener {
            if (isOnBreak) {
                showCurrentBreakDetails()
            }
        }
    }

    private fun checkOngoingTripBeforeLogout() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Check if there's an ongoing route/trip
                var ongoingRoute = routeRepository.getOngoingRoute()

                // If not found, try to find ANY route without end time
                if (ongoingRoute == null) {
                    val allRoutes = routeRepository.getAllRoutes()
                    ongoingRoute = allRoutes.firstOrNull { it.endTimeMillis == null }
                }

                withContext(Dispatchers.Main) {
                    if (ongoingRoute != null) {
                        // Show popup for ongoing trip
                        showOngoingTripDialog(ongoingRoute)
                    } else {
                        // No ongoing trip, proceed with attendance logout directly
                        proceedWithAttendanceLogout()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("Attendance", "Error checking ongoing trip: ${e.message}")
                    // If error checking trip, proceed with logout anyway
                    proceedWithAttendanceLogout()
                }
            }
        }
    }

    private fun showOngoingTripDialog(ongoingRoute: Any) { // Replace 'Any' with your Route class type
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Ongoing Trip Detected")
            .setMessage(
                "You have an ongoing trip that hasn't been ended.\n\n" +
                        "Would you like to end the trip before closing your attendance?\n\n" +
                        "This will ensure your trip data is saved properly."
            )
            .setPositiveButton("Yes, End Trip & Logout") { _, _ ->
                endOngoingTripAndLogout(ongoingRoute)
            }
            .setNegativeButton("Skip, Logout Only") { _, _ ->
                proceedWithAttendanceLogout()
            }
            .setNeutralButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    private fun endOngoingTripAndLogout(ongoingRoute: Any) {
        showProgress("Ending trip and logging out...")

        val onGoingTripId = preferenceManager.getTripId()?.toInt()
        val salesExecutiveId = preferenceManager.getSalesExecutiveId()?.toString() ?: ""

        if (onGoingTripId == null) {
            Log.w("Attendance", "No ongoing trip ID found")
            proceedWithAttendanceLogout()
            return
        }

        // Get current location on main thread first
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                val latitude = location?.latitude ?: 0.0
                val longitude = location?.longitude ?: 0.0

                // Use IO thread for database operations, but ensure observers are on Main thread
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        // ============================================
                        // STEP 1: Find the ongoing route in local DB
                        // ============================================
                        var ongoingRouteFromDb = routeRepository.getOngoingRoute()

                        if (ongoingRouteFromDb == null) {
                            val allRoutes = routeRepository.getAllRoutes()
                            ongoingRouteFromDb = allRoutes.firstOrNull { it.endTimeMillis == null }
                        }

                        if (ongoingRouteFromDb == null) {
                            Log.w("Attendance", "No ongoing route found in local DB. Ending trip from server only.")

                            // Switch to Main thread for UI operations
                            withContext(Dispatchers.Main) {
                                viewModel.endTripWithApi(
                                    tripId = onGoingTripId,
                                    salesExecutiveId = salesExecutiveId,
                                    latitude = latitude,
                                    longitude = longitude,
                                    onSuccess = { message ->
                                        Log.d("Attendance", "Trip ended via API: $message")
                                        Log.e("TAG", "endOngoingTripAndLogout: -------- VALUE :: ${viewModel.isTracking.value}", )
                                        clearLocalTripData()
//                                        viewModel.resetTrackingStateForAttendanceLogout()
                                        proceedWithAttendanceLogout()
                                    },
                                    onError = { errorMsg ->
                                        hideProgress()
//                                        viewModel.resetTrackingStateForAttendanceLogout()
                                        showErrorDialog(
                                            "Failed to End Trip",
                                            "Could not end trip: $errorMsg\n\nDo you want to logout attendance anyway?"
                                        ) {
                                            clearLocalTripData()
                                            proceedWithAttendanceLogout()
                                        }
                                    }
                                )
                            }
                            return@launch
                        }

                        Log.d("Attendance", "✅ Found ongoing route: ${ongoingRouteFromDb.id}")

                        // ============================================
                        // STEP 2: Get all stops created during this route
                        // ============================================
                        val stopsInRoute = stopRepository.getStopsInTimeRange(
                            ongoingRouteFromDb.startTimeMillis,
                            System.currentTimeMillis()
                        )
                        Log.d("Attendance", "Found ${stopsInRoute.size} stops for this route")

                        // ============================================
                        // STEP 3: Link stops to route if not already linked
                        // ============================================
                        stopsInRoute.forEach { stop ->
                            routeRepository.linkStopToRoute(ongoingRouteFromDb.id, stop.id)
                        }

                        // ============================================
                        // STEP 4: Update the existing route with end time
                        // ============================================
                        val currentTime = System.currentTimeMillis()
                        routeRepository.finalizeRoute(
                            routeId = ongoingRouteFromDb.id,
                            endTime = currentTime,
                            stopCount = stopsInRoute.size,
                            screenshotPath = null
                        )
                        Log.d("Attendance", "✅ Route ${ongoingRouteFromDb.id} updated with endTime: $currentTime")

                        // ============================================
                        // STEP 5: Clear local trip data
                        // ============================================
                        clearLocalTripData()

                        // ============================================
                        // STEP 6: Call end trip API on Main thread
                        // ============================================
                        withContext(Dispatchers.Main) {
                            viewModel.endTripWithApi(
                                tripId = onGoingTripId,
                                salesExecutiveId = salesExecutiveId,
                                latitude = latitude,
                                longitude = longitude,
                                onSuccess = { message ->
                                    Log.d("Attendance", "Trip API ended successfully: $message")
                                    proceedWithAttendanceLogout()
                                },
                                onError = { errorMsg ->
                                    hideProgress()
                                    showErrorDialog(
                                        "Warning",
                                        "Trip ended locally but API failed: $errorMsg\n\nContinue with attendance logout?"
                                    ) {
                                        proceedWithAttendanceLogout()
                                    }
                                }
                            )
                        }

                    } catch (e: Exception) {
                        Log.e("Attendance", "Error ending trip", e)
                        withContext(Dispatchers.Main) {
                            hideProgress()
                            showErrorDialog(
                                "Error Ending Trip",
                                "${e.message}\n\nDo you want to logout attendance anyway?"
                            ) {
                                clearLocalTripData()
                                proceedWithAttendanceLogout()
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("Attendance", "Failed to get location", exception)
                hideProgress()
                showErrorDialog(
                    "Location Error",
                    "Unable to get current location: ${exception.message}\n\nDo you want to logout attendance anyway?"
                ) {
                    clearLocalTripData()
                    proceedWithAttendanceLogout()
                }
            }
    }

    private fun clearLocalTripData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Clear trip data from SharedPreferences
//                preferenceManager.clearTripData()
                preferenceManager.setTripActive(false)

                // Clear any pending sync data
                stopPeriodicSync()

                Log.d("Attendance", "Local trip data cleared successfully")
            } catch (e: Exception) {
                Log.e("Attendance", "Error clearing local trip data: ${e.message}")
            }
        }
    }

    private fun stopPeriodicSync() {
        // Stop any periodic sync work manager or coroutine
        // Implement based on your sync mechanism
        Log.d("Attendance", "Stopping periodic sync")
    }

    private fun proceedWithAttendanceLogout() {
        // Call the attendance logout API
        currentLocation?.let { location ->
            viewModel.logoutWithApi(
                latitude = location.latitude,
                longitude = location.longitude
            )
        } ?: run {
            viewModel.logoutWithApi(
                latitude = 0.0,
                longitude = 0.0
            )
        }
    }




    private fun showErrorDialog(title: String, message: String, onRetry: (() -> Unit)? = null) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                onRetry?.invoke()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun handleLoginResponse(resource: Resource<AttendanceResponse>?) {
        when (resource) {
            is Resource.Loading -> {
                binding.btnLogin.isEnabled = false
                binding.btnLogin.text = "Logging in..."
                showProgress("Logging in...")
            }
            is Resource.Success -> {
                hideProgress()
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = "Login"

                if (resource.data?.success == 1) {
                    val data = resource.data.data
                    currentAttendanceId = data?.attendanceId ?: 0
                    workStartTime = System.currentTimeMillis()
                    isAttendanceLoggedIn = true
                    preferenceManager.setPreviousSessionDialogShown(false)  // Reset dialog shown flag
                    preferenceManager.setPreviousSessionHandled(false)
                    preferenceManager.setAttendanceLoggedIn(true)
                    preferenceManager.setAttendanceId(currentAttendanceId)
                    preferenceManager.setWorkStartTime(workStartTime)
                    preferenceManager.setLoginTime(System.currentTimeMillis())
                    preferenceManager.setLastAttendanceId(currentAttendanceId)
                    preferenceManager.setLastLoginDate(data?.date ?: getCurrentDate())
                    preferenceManager.setLastLoginTime(data?.loginTime ?: "")
                    preferenceManager.setLastLoginCoordinates(data?.loginCoordinates ?: "")
                    preferenceManager.setHasIncompletePreviousSession(false)
                    preferenceManager.clearPreviousSessionFlags()
                    loadLastLoginInfo()

                    // Show success with details
                    showSuccessDialog(
                        "Login Successful",
                        "Time: ${formatTime(data?.loginTime)}\n" +
                                "Location: ${data?.loginCoordinates}\n" +
                                "Date: ${formatDate(data?.date)}"
                    )

                    updateUIForState()
                    startWorkTimer()
                    updateStatistics()
                    displayLastLoginInfo()
                } else {
                    showErrorDialog("Login Failed", resource.data?.message ?: "Unknown error")
                }
                viewModel.clearLoginState()
            }
            is Resource.Error -> {
                hideProgress()
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = "Login"
                showErrorDialog("Login Failed", resource.message ?: "An error occurred")

//                sendAttendanceLogoutBroadcast()
                viewModel.clearLoginState()
            }
            else -> {}
        }
    }

    private fun resetAllState() {
        // Reset all state variables
        isAttendanceLoggedIn = false
        isOnBreak = false
        currentAttendanceId = 0
        breakCount = 0
        breakList.clear()
        workStartTime = 0
        breakStartTime = 0

        preferenceManager.clearAttendanceData()
        preferenceManager.setAttendanceLoggedIn(false)
        preferenceManager.setHasIncompletePreviousSession(false)

        // Clear previous session flags once
        preferenceManager.clearPreviousSessionFlags()

        // Cancel timers
        stopWorkTimer()
        stopBreakTimer()

        // Reset UI
        resetUIState()

        // Update UI based on state
        updateUIForState()
    }

    private fun handleBreakInResponse(resource: Resource<AttendanceResponse>?) {
        when (resource) {
            is Resource.Loading -> {
                binding.btnBreakIn.isEnabled = false
                binding.btnBreakIn.text = "Starting Break..."
                showProgress("Starting break...")
            }
            is Resource.Success -> {
                hideProgress()
                binding.btnBreakIn.isEnabled = true
                binding.btnBreakIn.text = "Break In"

                if (resource.data?.success == 1) {
                    val data = resource.data.data
                    isOnBreak = true
                    breakCount++
                    breakStartTime = System.currentTimeMillis()

                    // Save break data
                    val breakData = BreakData(
                        breakId = data?.breakId ?: 0,
                        breakInTime = data?.breakInTime ?: "",
                        breakInCoordinates = data?.breakInCoordinates ?: ""
                    )
                    breakList.add(breakData)

                    // Save to preferences
                    preferenceManager.setOnBreak(true)
                    preferenceManager.setBreakCount(breakCount)
                    preferenceManager.setBreakStartTime(breakStartTime)
                    preferenceManager.saveBreakData(breakData)

                    // Stop work timer, start break timer
                    stopWorkTimer()
                    startBreakTimer()

                    showSuccessDialog(
                        "Break Started",
                        "Break #$breakCount\n" +
                                "Time: ${formatTime(data?.breakInTime)}\n" +
                                "Location: ${data?.breakInCoordinates}"
                    )

                    updateUIForState()
                    updateStatistics()
                } else {
                    showErrorDialog("Break Failed", resource.data?.message ?: "Unknown error")
                }
                viewModel.clearBreakInState()
            }
            is Resource.Error -> {
                hideProgress()
                binding.btnBreakIn.isEnabled = true
                binding.btnBreakIn.text = "Break In"
                showErrorDialog("Break Failed", resource.message ?: "An error occurred")
                viewModel.clearBreakInState()
            }
            else -> {}
        }
    }

    private fun handleBreakOutResponse(resource: Resource<AttendanceResponse>?) {
        when (resource) {
            is Resource.Loading -> {
                binding.btnBreakOut.isEnabled = false
                binding.btnBreakOut.text = "Ending Break..."
                showProgress("Ending break...")
            }
            is Resource.Success -> {
                hideProgress()
                binding.btnBreakOut.isEnabled = true
                binding.btnBreakOut.text = "Break Out"

                if (resource.data?.success == 1) {
                    val data = resource.data.data
                    isOnBreak = false

                    // Update last break with out time
                    if (breakList.isNotEmpty()) {
                        val lastBreak = breakList.last()
                        lastBreak.breakOutTime = data?.breakOutTime
                        lastBreak.breakOutCoordinates = data?.breakOutCoordinates
                        lastBreak.breakDuration = data?.breakDuration
                        preferenceManager.updateBreakData(lastBreak)
                    }

                    // Save to preferences
                    preferenceManager.setOnBreak(false)
                    preferenceManager.setBreakEndTime(System.currentTimeMillis())

                    // Stop break timer, restart work timer
                    stopBreakTimer()
                    startWorkTimer()

                    showSuccessDialog(
                        "Break Ended",
                        "Break #$breakCount\n" +
                                "Duration: ${data?.breakDuration}\n" +
                                "End Time: ${formatTime(data?.breakOutTime)}"
                    )

                    updateUIForState()
                    updateStatistics()
                } else {
                    showErrorDialog("Break Failed", resource.data?.message ?: "Unknown error")
                }
                viewModel.clearBreakOutState()
            }
            is Resource.Error -> {
                hideProgress()
                binding.btnBreakOut.isEnabled = true
                binding.btnBreakOut.text = "Break Out"
                showErrorDialog("Break Failed", resource.message ?: "An error occurred")
                viewModel.clearBreakOutState()
            }
            else -> {}
        }
    }

    private fun handleLogoutResponse(resource: Resource<AttendanceResponse>?) {
        when (resource) {
            is Resource.Loading -> {
                binding.btnLogout.isEnabled = false
                binding.btnLogout.text = "Logging out..."
                showProgress("Logging out...")
            }
            is Resource.Success -> {
                hideProgress()
                binding.btnLogout.isEnabled = true
                binding.btnLogout.text = "Logout"

                if (resource.data?.success == 1) {
                    val data = resource.data.data

                    // Stop all timers
                    stopWorkTimer()
                    stopBreakTimer()

//                    viewModel.resetTrackingStateForAttendanceLogout()

                    setResult(RESULT_OK)
                    // Show summary
                    showLogoutSummaryDialog(data)

                    resetAllState()
                    // Display last login info (still visible after logout)
                    displayLastLoginInfo()

                } else {
                    showErrorDialog("Logout Failed", resource.data?.message ?: "Unknown error")
                }
                viewModel.clearLogoutState()
            }
            is Resource.Error -> {
                hideProgress()
                binding.btnLogout.isEnabled = true
                binding.btnLogout.text = "Logout"
                showErrorDialog("Logout Failed", resource.message ?: "An error occurred")
                viewModel.clearLogoutState()
            }
            else -> {}
        }
    }

    private fun resetUIState() {
        // Reset timer displays
        binding.tvWorkTime.text = "00:00:00"
        binding.tvBreakTime.text = "00:00"

        // Reset statistics displays
        binding.tvBreakCount.text = "0"
        binding.tvTotalBreakTime.text = "0 m"

        // Reset status indicator color (will be updated by updateUIForState)
        // No need to set color here as updateUIForState() will handle it

        // Reset any other UI elements if needed
        binding.progressOverlay.visibility = View.GONE
        binding.tvProgressMessage.visibility = View.GONE

        // Reset button states (will be updated by updateUIForState)
        binding.btnLogin.isEnabled = true
        binding.btnBreakIn.isEnabled = true
        binding.btnBreakOut.isEnabled = true
        binding.btnLogout.isEnabled = true

        // Reset button text
        binding.btnLogin.text = "Login"
        binding.btnBreakIn.text = "Break In"
        binding.btnBreakOut.text = "Break Out"
        binding.btnLogout.text = "Logout"
    }

    private fun startWorkTimer() {
        workTimerJob?.cancel()
        workTimerJob = lifecycleScope.launch {
            while (isAttendanceLoggedIn && !isOnBreak) {  // FIXED: Use isAttendanceLoggedIn
                val elapsed = System.currentTimeMillis() - workStartTime
                val hours = elapsed / 3600000
                val minutes = (elapsed % 3600000) / 60000
                val seconds = (elapsed % 60000) / 1000

                binding.tvWorkTime.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                delay(1000)
            }
        }
    }

    private fun stopWorkTimer() {
        workTimerJob?.cancel()
        workTimerJob = null
    }
    private fun startBreakTimer() {
        breakTimerJob?.cancel()
        breakTimerJob = lifecycleScope.launch {
            while (isOnBreak) {
                val elapsed = System.currentTimeMillis() - breakStartTime
                val minutes = elapsed / 60000
                val seconds = (elapsed % 60000) / 1000

                binding.tvBreakTime.text = String.format("%02d:%02d", minutes, seconds)
                delay(1000)
            }
        }
    }
    private fun stopBreakTimer() {
        breakTimerJob?.cancel()
        breakTimerJob = null
        binding.tvBreakTime.text = "00:00"
    }

    private fun updateStatistics() {
        binding.tvBreakCount.text = breakCount.toString()
        val totalBreakMinutes = calculateTotalBreakTime()
        binding.tvTotalBreakTime.text = formatBreakTime(totalBreakMinutes)

        // FIXED: Use isAttendanceLoggedIn
        when {
            !isAttendanceLoggedIn -> binding.statusIndicator.setBackgroundColor(Color.RED)
            isOnBreak -> binding.statusIndicator.setBackgroundColor(Color.parseColor("#FF9800"))
            else -> binding.statusIndicator.setBackgroundColor(Color.GREEN)
        }
    }
    private fun calculateTotalBreakTime(): Long {
        var totalMinutes = 0L
        breakList.forEach { breakData ->
            if (breakData.breakDuration != null) {
                // Parse duration string like "15 minutes"
                val minutes = breakData.breakDuration?.replace("minutes", "")?.trim()?.toIntOrNull() ?: 0
                totalMinutes += minutes
            }
        }
        return totalMinutes
    }

    private fun showLoginConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm Login")
            .setMessage("Do you want to mark your attendance for today?")
            .setPositiveButton("Yes") { _, _ ->
                currentLocation?.let { location ->
                    viewModel.loginWithApi(
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                }
            }
            .setNegativeButton("No", null)
            .show()
    }


    private fun showBreakInConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Start Break")
            .setMessage("Do you want to start your break?")
            .setPositiveButton("Yes") { _, _ ->
                currentLocation?.let { location ->
                    viewModel.breakInWithApi(
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showBreakOutConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("End Break")
            .setMessage("Do you want to end your break?")
            .setPositiveButton("Yes") { _, _ ->
                currentLocation?.let { location ->
                    viewModel.breakOutWithApi(
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showLogoutConfirmationDialog() {
        val totalWorkTime = calculateTotalWorkTime()

        // First check if there's an ongoing trip
        lifecycleScope.launch(Dispatchers.IO) {
            var ongoingRoute = routeRepository.getOngoingRoute()

            if (ongoingRoute == null) {
                val allRoutes = routeRepository.getAllRoutes()
                ongoingRoute = allRoutes.firstOrNull { it.endTimeMillis == null }
            }

            val hasOngoingTrip = ongoingRoute != null

            withContext(Dispatchers.Main) {
                val message = if (hasOngoingTrip) {
                    "⚠️ You have an ongoing trip that hasn't been ended!\n\n" +
                            "Today's Summary:\n" +
                            "• Total Breaks: $breakCount\n" +
                            "• Total Break Time: ${binding.tvTotalBreakTime.text}\n" +
                            "• Total Work Time: $totalWorkTime\n\n" +
                            "Do you want to logout? You'll be prompted to end your trip."
                } else {
                    "Today's Summary:\n" +
                            "• Total Breaks: $breakCount\n" +
                            "• Total Break Time: ${binding.tvTotalBreakTime.text}\n" +
                            "• Total Work Time: $totalWorkTime\n\n" +
                            "Do you want to logout?"
                }

                MaterialAlertDialogBuilder(this@ActivityAttendance)
                    .setTitle("Confirm Logout")
                    .setMessage(message)
                    .setPositiveButton("Logout") { _, _ ->
                        if (hasOngoingTrip) {
                            checkOngoingTripBeforeLogout()
                        } else {
                            proceedWithAttendanceLogout()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun showBreakHistoryDialog() {
        if (breakList.isEmpty()) {
            showToast("No breaks taken today")
            return
        }

        val items = breakList.mapIndexed { index, breakData ->
            "Break ${index + 1}\n" +
                    "In: ${formatTime(breakData.breakInTime)}\n" +
                    "Out: ${formatTime(breakData.breakOutTime ?: "Ongoing")}\n" +
                    "Duration: ${breakData.breakDuration ?: "In Progress"}"
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Break History (Today)")
            .setItems(items) { _, _ ->
                // Show detailed break info
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showWorkTimeDetails() {
        // Check if user is logged in
        if (!isAttendanceLoggedIn) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Work Time Details")
                .setMessage("You are not logged in. Please login first to track your work time.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val workDuration = System.currentTimeMillis() - workStartTime
        val hours = workDuration / 3600000
        val minutes = (workDuration % 3600000) / 60000

        MaterialAlertDialogBuilder(this)
            .setTitle("Work Time Details")
            .setMessage(
                "Login Time: ${formatTime(preferenceManager.getLoginTime())}\n" +
                        "Current Work Duration: $hours hours $minutes minutes\n" +
                        "Total Breaks Taken: $breakCount\n" +
                        "Total Break Time: ${binding.tvTotalBreakTime.text}"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showCurrentBreakDetails() {
        val breakDuration = System.currentTimeMillis() - breakStartTime
        val minutes = breakDuration / 60000
        val seconds = (breakDuration % 60000) / 1000

        MaterialAlertDialogBuilder(this)
            .setTitle("Current Break")
            .setMessage(
                "Break #$breakCount\n" +
                        "Duration: $minutes minutes $seconds seconds\n" +
                        "Started at: ${formatTime(breakStartTime)}"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLogoutSummaryDialog(data: AttendanceData?) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Day Summary")
            .setCancelable(false)
            .setMessage(
                "📊 Today's Attendance Summary\n\n" +
                        "✅ Login: ${formatTime(data?.loginTime)}\n" +
                        "🚪 Logout: ${formatTime(data?.logoutTime)}\n" +
                        "☕ Total Breaks: $breakCount\n" +
                        "⏱️ Total Break Time: ${data?.totalBreakTime ?: "0 minutes"}\n" +
                        "💼 Total Working Time: ${data?.totalWorkingTime ?: "0 hours"}\n\n" +
                        "📍 Login Location: ${data?.loginCoordinates}\n" +
                        "📍 Logout Location: ${data?.logoutCoordinates}\n\n" +
                        "Have a great day! 🎉"
            )
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .show()
    }

    private fun showSuccessDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("✅ $title")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showErrorDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("❌ $title")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showProgress(message: String) {
        // Show progress dialog or loading indicator
        binding.progressOverlay.visibility = View.VISIBLE
        binding.tvProgressMessage.text = message
        binding.tvProgressMessage.visibility = View.VISIBLE
    }

    private fun hideProgress() {
        binding.progressOverlay.visibility = View.GONE
        binding.tvProgressMessage.visibility = View.GONE
    }

    private fun calculateTotalWorkTime(): String {
        val workDuration = System.currentTimeMillis() - workStartTime
        val totalBreakMinutes = calculateTotalBreakTime()
        val actualWorkDuration = workDuration - (totalBreakMinutes * 60000)

        val hours = actualWorkDuration / 3600000
        val minutes = (actualWorkDuration % 3600000) / 60000

        return "$hours hours $minutes minutes"
    }

    private fun formatTime(timeString: String?): String {
        if (timeString.isNullOrBlank()) return "N/A"
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            format.timeZone = TimeZone.getTimeZone("UTC")
            val date = format.parse(timeString)
            val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            timeString
        }
    }

    private fun formatTime(timestamp: Long): String {
        val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return format.format(Date(timestamp))
    }

    private fun formatDate(dateString: String?): String {
        if (dateString.isNullOrBlank()) return "N/A"
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            format.timeZone = TimeZone.getTimeZone("UTC")
            val date = format.parse(dateString)
            val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }

    private fun formatBreakTime(minutes: Long): String {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return if (hours > 0) "$hours h $remainingMinutes m" else "$remainingMinutes m"
    }


    private fun updateUIForState() {
        when {
            !isAttendanceLoggedIn -> {
                binding.btnLogin.visibility = View.VISIBLE
                binding.btnBreakIn.visibility = View.GONE
                binding.btnBreakOut.visibility = View.GONE
                binding.btnLogout.visibility = View.GONE
                binding.tvStatus.text = "Status: Not Logged In"
                binding.tvStatus.setTextColor(Color.RED)
                binding.tvWorkTime.text = "00:00:00"
                binding.tvBreakTime.text = "00:00"
            }
            isAttendanceLoggedIn && !isOnBreak -> {
                binding.btnLogin.visibility = View.GONE
                binding.btnBreakIn.visibility = View.VISIBLE
                binding.btnBreakOut.visibility = View.GONE
                binding.btnLogout.visibility = View.VISIBLE
                binding.tvStatus.text = "Status: On Duty"
                binding.tvStatus.setTextColor(Color.GREEN)
            }
            isAttendanceLoggedIn && isOnBreak -> {
                binding.btnLogin.visibility = View.GONE
                binding.btnBreakIn.visibility = View.GONE
                binding.btnBreakOut.visibility = View.VISIBLE
                binding.btnLogout.visibility = View.GONE
                binding.tvStatus.text = "Status: On Break"
                binding.tvStatus.setTextColor(Color.parseColor("#FF9800"))
            }
        }
    }


    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun enableEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.statusBarColor = Color.TRANSPARENT
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            window.statusBarColor = Color.TRANSPARENT
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }
    }


    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
    override fun onDestroy() {
        super.onDestroy()
    }
}