package com.example.salesstysetgps.ui

import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.salesstysetgps.data.StopRepository
import com.example.salesstysetgps.data.network.NetworkClient
import com.example.salesstysetgps.databinding.ActivityAttendanceBinding
import com.example.salesstysetgps.location.RouteRepository
import com.example.salesstysetgps.models.AttendanceData
import com.example.salesstysetgps.models.AttendanceResponse
import com.example.salesstysetgps.models.BreakData
import com.example.salesstysetgps.models.Resource
import com.example.salesstysetgps.presentation.viewmodel.base.BaseActivity
import com.example.salesstysetgps.repository.TripRepository
import com.example.salesstysetgps.util.PreferenceManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.getValue

class ActivityAttendance : BaseActivity<ActivityAttendanceBinding>() {

//    private lateinit var viewModel: AttendanceViewModel
private val viewModel: TrackingViewModel by viewModels {
    val preferenceManager = PreferenceManager.getInstance(this@ActivityAttendance)
    val apiService = NetworkClient.apiService
    val tripRepository = TripRepository(apiService, preferenceManager)
    TrackingViewModelFactory(this@ActivityAttendance.application, tripRepository, preferenceManager)
}
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var stopRepository: StopRepository
    private lateinit var routeRepository: RouteRepository

    private var currentLocation: LatLng? = null
    private var isLoggedIn = false
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



    override fun getViewBinding(): ActivityAttendanceBinding {
        return ActivityAttendanceBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        enableEdgeToEdge()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        stopRepository = StopRepository(this)
        routeRepository = RouteRepository(this)

        preferenceManager = PreferenceManager(this)


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
        isLoggedIn = preferenceManager.isLoggedIn()
        isOnBreak = preferenceManager.isOnBreak()

        updateUIForState()


        setupWindowInsets()
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
//            performLogout()
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
                    isLoggedIn = true
                    currentAttendanceId = data?.attendanceId ?: 0
                    workStartTime = System.currentTimeMillis()

                    // Save to preferences
                    preferenceManager.setLoggedIn(true)
                    preferenceManager.setAttendanceId(currentAttendanceId)
                    preferenceManager.setWorkStartTime(workStartTime)

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
                viewModel.clearLoginState()
            }
            else -> {}
        }
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

                    // Show summary
                    showLogoutSummaryDialog(data)

                    // Clear attendance data
                    preferenceManager.clearAttendanceData()
                    isLoggedIn = false
                    isOnBreak = false

                    updateUIForState()
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

    private fun startWorkTimer() {
        workTimerJob?.cancel()
        workTimerJob = lifecycleScope.launch {
            while (isLoggedIn && !isOnBreak) {
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
        // Update break count
        binding.tvBreakCount.text = breakCount.toString()

        // Calculate total break time from API data or local
        val totalBreakMinutes = calculateTotalBreakTime()
        binding.tvTotalBreakTime.text = formatBreakTime(totalBreakMinutes)

        // Update status color
        when {
            !isLoggedIn -> binding.statusIndicator.setBackgroundColor(Color.RED)
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
        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm Logout")
            .setMessage(
                "Today's Summary:\n" +
                        "• Total Breaks: $breakCount\n" +
                        "• Total Break Time: ${binding.tvTotalBreakTime.text}\n" +
                        "• Total Work Time: $totalWorkTime\n\n" +
                        "Do you want to logout?"
            )
            .setPositiveButton("Logout") { _, _ ->
                currentLocation?.let { location ->
                    viewModel.logoutWithApi(
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        if (!isLoggedIn) {
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
            !isLoggedIn -> {
                // Not logged in - only show login button
                binding.btnLogin.visibility = View.VISIBLE
                binding.btnBreakIn.visibility = View.GONE
                binding.btnBreakOut.visibility = View.GONE
                binding.btnLogout.visibility = View.GONE
                binding.tvStatus.text = "Status: Not Attendence Logged In"
                binding.tvStatus.setTextColor(Color.RED)
            }

            isLoggedIn && !isOnBreak -> {
                // Logged in but on duty - show break in and logout
                binding.btnLogin.visibility = View.GONE
                binding.btnBreakIn.visibility = View.VISIBLE
                binding.btnBreakOut.visibility = View.GONE
                binding.btnLogout.visibility = View.VISIBLE
                binding.tvStatus.text = "Status: On Duty"
                binding.tvStatus.setTextColor(Color.GREEN)
            }

            isLoggedIn && isOnBreak -> {
                // On break - show break out only
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