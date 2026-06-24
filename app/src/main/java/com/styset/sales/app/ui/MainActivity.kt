package com.styset.sales.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.Insets
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.styset.sales.app.MainActivity2
import com.styset.sales.app.SyncWorker
import com.styset.sales.app.data.LocationPoint
import com.styset.sales.app.data.ReportRepository
import com.styset.sales.app.data.StopPoint
import com.styset.sales.app.data.StopRepository
import com.styset.sales.app.data.api.ApiService
import com.styset.sales.app.data.local.RoutePointEntity
import com.styset.sales.app.data.network.NetworkClient
import com.styset.sales.app.domain.repository.LeadRepository
import com.styset.sales.app.domain.repository.PerformanceLocationRepository
import com.styset.sales.app.domain.repository.PerformanceReport
import com.styset.sales.app.location.RouteRepository
import com.styset.sales.app.models.CreateLeadRequest
import com.styset.sales.app.models.LeadModel
import com.styset.sales.app.models.Resource
import com.styset.sales.app.models.StartTripResponse
import com.styset.sales.app.presentation.viewmodel.LeadViewModel
import com.styset.sales.app.presentation.viewmodel.LeadViewModelFactory
import com.styset.sales.app.presentation.viewmodel.base.BaseActivity
import com.styset.sales.app.repository.TripRepository
import com.styset.sales.app.util.PdfUtils
import com.styset.sales.app.util.PreferenceManager
import com.styset.sales.app.workers.SyncManager
import com.styset.sales.app.workers.SyncResult
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.styset.sales.app.BuildConfig
import com.styset.sales.app.R
import com.styset.sales.app.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class MainActivity : BaseActivity<ActivityMainBinding>(), OnMapReadyCallback {

    private lateinit var attendanceLauncher: ActivityResultLauncher<Intent>

    private lateinit var stopRepository: StopRepository
    private lateinit var playUpdateManager: PlayUpdateManager
    private lateinit var updateCard: MaterialCardView
    private lateinit var performanceRepo: PerformanceLocationRepository

    private var isFromPlaces = false

    // Add this at the top of MainActivity with other variables
    private var isViewOnlyMode = false


    private lateinit var etName: EditText
    private lateinit var tvLocation: TextView
    private lateinit var etAddress: EditText
    private lateinit var etPhone: EditText
    private lateinit var tvDuration: TextView
    private lateinit var etContactPerson: EditText
    private lateinit var etEmailId: EditText
    private lateinit var etArea: EditText
    private lateinit var etSellerAddress: EditText
    private lateinit var etCurrentLocation: EditText
    private lateinit var etExecutiveName: EditText
    private lateinit var etDate: EditText
    private lateinit var rgSellingType: RadioGroup
    private lateinit var etDemo: RadioGroup
    private lateinit var etRemark: EditText
    private lateinit var etVisitingCardImage: EditText
    private lateinit var ivImagePreview: ImageView
    private lateinit var etOnboardingClientName: EditText
    private lateinit var etPaidAmount: EditText
    private lateinit var etNotes: EditText
    private lateinit var etLeads: TextInputEditText
    private lateinit var btnCaptureSellerImage: MaterialButton
    private lateinit var ivSellerImagePreview: ImageView
    private var fullScreenDialog: AlertDialog? = null

    // Variable for seller image
    private var currentSellerImageUri: Uri? = null
    private var uploadedImageUrl: String? = null

    private lateinit var apiService: ApiService

    private var sellerImage: String = ""

    private var uploadCall: Call? = null

    lateinit var fileMain: File

    private var capturedImageUri: Uri? = null
    private var compressedImageFile: File? = null

    private lateinit var currentPhotoPath: String

    private lateinit var preferenceManager: PreferenceManager

    private lateinit var syncManager: SyncManager

    private var syncJob: Job? = null
    private var isSyncScheduled = false

    private var isPeriodicSyncStarted = false

    private var startTripProgressDialog: AlertDialog? = null
    private lateinit var routeRepository: RouteRepository

    //    private lateinit var viewModel: TrackingViewModel
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var pendingRouteId: Long? = null
    private var pendingRouteHasStops: Boolean = true
    private var pendingShowFullRoute: Boolean = false
    private var selectedMapType = GoogleMap.MAP_TYPE_NORMAL
    private var googleMap: GoogleMap? = null
    private var routePolyline: Polyline? = null
    private val stopMarkers = mutableListOf<Pair<Long, Marker>>()
    private var startAfterResolution: Boolean = false
    private var systemBarInsets: Insets? = null
    private var pendingPhotoUri: Uri? = null
    private var currentStopDialogPhotoView: ImageView? = null
    private var pendingCenterOnMyLocation: Boolean = false
    private var pendingCenterOnPlace: LatLng? = null

    // Start marker related properties
    private var startMarker: Marker? = null
    private var isObservingStartLocation = false
    private var isStartMarkerSet = false

    var pendingStopId: Long? = null
    private var pendingStartLocation: LatLng? = null
    private var isWaitingForMap = false

    // Location monitoring properties
    private var locationCallback: LocationCallback? = null
    private var isLocationDialogShowing = false
    private var isGpsDialogShowing = false
    private val gpsCheckInterval = 5000L // 5 seconds
    private var gpsCheckJob: Job? = null

    private val prefs by lazy { getSharedPreferences("salesstysetgps_prefs", MODE_PRIVATE) }
    private val prefAskedPermissionsKey = "asked_permissions_once"
    private var pendingExportAction: (() -> Unit)? = null

    private var hasLocationFix = false

    private var hasShownNoFixDialog = false // Track if we've shown the dialog

    private var isWaitingForFirstFix = false

    private var hasShownGpsDialog = false
    private var hasShownGpsFailureDialog = false
    private var lastLocationTime = 0L
    private val locationTimeoutMs = 30000L
    private var userDeclinedLocation = false
    private var isSystemDialogShowing = false
    private val viewModel: TrackingViewModel by viewModels {
        val preferenceManager = PreferenceManager.Companion.getInstance(this@MainActivity)
        val apiService = NetworkClient.apiService
        val tripRepository = TripRepository(apiService, preferenceManager)
        TrackingViewModelFactory(this@MainActivity.application, tripRepository, preferenceManager)
    }

    private val leadViewModel: LeadViewModel by viewModels {
        val apiService = NetworkClient.apiService
        val preferenceManager = PreferenceManager.Companion.getInstance(this)
        val leadRepository = LeadRepository(apiService, preferenceManager)
        LeadViewModelFactory(leadRepository)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            showErrorSnackbar("Camera permission is required to capture visiting card image")
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            capturedImageUri?.let { uri ->
                val file = File(currentPhotoPath)

                if (!file.exists()) {
                    Log.e("Camera", "File not found: ${file.absolutePath}")
                    showErrorSnackbar("Failed to capture image")
                    return@let
                }
                ivImagePreview.visibility = View.VISIBLE
                showInfoSnackbar("Getting upload URL...")
                ivImagePreview.setImageURI(uri)

                val originalFile = copyUriToTempFile(this, uri)

                val compressedFile = compressImageFromFile(
                    context = this,
                    inputFile = originalFile,
                    quality = 70
                )

                fileMain = compressedFile

                // Compress image
                lifecycleScope.launch {
                    compressedImageFile = withContext(Dispatchers.IO) {
                        compressImageFromFile(this@MainActivity, file, 70)
                    }

                    val compressedFile = compressImageFromFile(
                        context = this@MainActivity,
                        inputFile = file,
                        quality = 70
                    )
                    fileMain = compressImageFromFile(this@MainActivity, file, 70)
                    val folderName = "input"
                    val objectKey =
                        "$folderName/${preferenceManager.getSalesExecutiveId()}/${compressedFile.name}"

                    getPresignedUrlAndUpload(
                        BuildConfig.BUCKET_NAME,
//                        "salesexecutivestg",// salesexecutiveprd
                        compressedFile.nameWithoutExtension,
                        objectKey
                    )
                }
            }
        } else {
            showErrorSnackbar("Failed to capture image")

        }
    }

    private fun openCamera() {
        try {
            val photoFile = createImageFile()
            photoFile?.let {
                currentPhotoPath = it.absolutePath
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    it
                )
                capturedImageUri = uri
                takePictureLauncher.launch(uri)
            }
        } catch (e: Exception) {
            showErrorSnackbar("Error opening camera: ${e.message}")
        }
    }

    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "visiting_card_${timeStamp}_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun resetTripUIAfterAttendanceLogout() {
        viewModel.resetTrackingStateForAttendanceLogout()
        // Reset trip button states
        updateButtons()

        // Stop location updates if running
        stopLocationUpdates()
        stopGpsMonitoring()

        binding.tvStatus.text = getString(R.string.tracking_status_stopped)
        binding.tvTimer.visibility = View.GONE

        // Clear map route if any
        clearMapRoute()

        // Reset trip session in ViewModel
        viewModel.resetSession()

        // Show toast to inform user
        Toast.makeText(this, "Attendance logged out. Trip session reset.", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val LOCATION_REQUEST_CODE = 1001
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }
            if (granted) {
                prepareMapAfterPermissions()
                Toast.makeText(this, "Permissions granted. Tap Start to begin.", Toast.LENGTH_SHORT)
                    .show()
            } else {
                // Check if user checked "Never ask again"
                if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    showLocationPermissionPermanentlyDeniedDialog()
                } else {
                    showPermissionRequiredDialog()
                }
            }
        }

    private val writePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingExportAction?.invoke()
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private val resolutionLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            isLocationDialogShowing = false
            isSystemDialogShowing = false
            when (result.resultCode) {
                RESULT_OK -> {
                    // User enabled location
                    Log.d("LocationDebug", "✅ User enabled location")
                    userDeclinedLocation = false

                    if (startAfterResolution) {
                        startAfterResolution = false
                        viewModel.startTracking()
                        prepareMapAfterPermissions()
                        updateButtons()
                        startGpsMonitoring()
                        startLocationUpdates()
                    }
                }

                RESULT_CANCELED -> {
                    // User clicked "No thanks"
                    Log.d("LocationDebug", "❌ User declined location")
                    userDeclinedLocation = true
                    // Show your custom dialog
                    showEnableLocationDialog()
                }
            }
        }

//    private val takePictureLauncher =
//        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
//            if (success) {
//                pendingPhotoUri?.let { uri ->
//                    Log.d("CAMERA", "✅ Image captured: $uri")
//
//                    // Verify the image was saved
//                    try {
//                        val inputStream = contentResolver.openInputStream(uri)
//                        if (inputStream != null) {
//                            val size = inputStream.available()
//                            Log.d("CAMERA", "Image size: $size bytes")
//
//                            // Try to decode to verify it's valid
//                            val bitmap = BitmapFactory.decodeStream(inputStream)
//                            if (bitmap != null) {
//                                Log.d("CAMERA", "✅ Valid image: ${bitmap.width}x${bitmap.height}")
//                                bitmap.recycle()
//                            } else {
//                                Log.e("CAMERA", "❌ Failed to decode image")
//                            }
//                            inputStream.close()
//                        }
//                    } catch (e: Exception) {
//                        Log.e("CAMERA", "Error verifying image: ${e.message}")
//                    }
//
//                    // Display in ImageView
//                    currentStopDialogPhotoView?.setImageURI(uri)
//                }
//            } else {
//                Log.e("CAMERA", "❌ Camera capture failed")
//                pendingPhotoUri = null
//            }
//        }


    // ==================== BASE ACTIVITY OVERRIDES ====================

    override fun getViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        enableEdgeToEdge()
        setupWindowInsets()

        apiService = NetworkClient.apiService
        preferenceManager = PreferenceManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        stopRepository = StopRepository(this)
        performanceRepo = PerformanceLocationRepository(this)
        routeRepository = RouteRepository(this)
        syncManager = SyncManager(this)

        attendanceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d("MainActivity", "Attendance logout detected, resetting trip UI")
                Log.e("TAG", "setupViews: -===========${viewModel.isTracking.value}" )
                resetTripUIAfterAttendanceLogout()
            }
        }
        setupInAppUpdates()

        // Restore saved state from intent
        intent?.let {
            pendingShowFullRoute = it.getBooleanExtra("show_full_route", false)
            pendingRouteId = it.getLongExtra("route_id", 0L).takeIf { id -> id != 0L }
            pendingRouteHasStops = it.getBooleanExtra("has_stops", true)
            pendingStopId = it.getLongExtra("stop_id", 0L).takeIf { id -> id != 0L }
            isFromPlaces = it.getBooleanExtra("from_places", false)

            isViewOnlyMode = isFromPlaces


            val lat = it.getDoubleExtra("center_lat", Double.NaN)
            val lng = it.getDoubleExtra("center_lng", Double.NaN)
            if (!lat.isNaN() && !lng.isNaN()) {
                pendingCenterOnPlace = LatLng(lat, lng)
            }
        }

        // Setup map fragment
//        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
//        mapFragment.getMapAsync(this)
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_container)
        if (mapFragment == null) {
            // Add fragment programmatically if not found
            val fragment = SupportMapFragment.newInstance()
            supportFragmentManager.beginTransaction()
                .replace(R.id.map_container, fragment)
                .commit()
            fragment.getMapAsync(this)
        } else {
            (mapFragment as SupportMapFragment).getMapAsync(this)
        }

//        viewModel = ViewModelProvider(this)[TrackingViewModel::class.java]
        val preferenceManager = PreferenceManager.Companion.getInstance(this)
        val apiService = NetworkClient.apiService
        val tripRepository = TripRepository(apiService, preferenceManager)
        val factory = TrackingViewModelFactory(
            this@MainActivity.application,
            tripRepository,
            preferenceManager
        )

        Log.d("TimerDebug", "ViewModel initialized")

        updateButtonsForViewMode()
        // Ask permissions on the very first launch only
        maybeRequestPermissionsOnFirstLaunch()
        setupNavigationDrawer()
    }

    private fun updateButtonsForViewMode() {
        if (isViewOnlyMode) {
            // Coming from Places - hide all action buttons
            binding.btnStart.visibility = View.GONE
            binding.btnStop.visibility = View.GONE
            binding.btnReset.visibility = View.GONE
        } else {
            // Normal mode - show buttons based on tracking state
            val running = viewModel.isTracking.value
            binding.btnStart.visibility = if (running) View.GONE else View.VISIBLE
            binding.btnStop.visibility = if (running) View.VISIBLE else View.GONE
            binding.btnReset.visibility = View.VISIBLE
        }
    }

    private fun startAutoSync() {
        // Start periodic sync every 15 minutes
//        syncScheduler.startPeriodicSync()

        // Also trigger an immediate sync when app opens
        lifecycleScope.launch {
            delay(3000) // Wait 3 seconds after app opens
            performManualSync()
        }

        Log.d("MainActivity", "✅ Auto sync enabled (every 15 minutes)")
    }

    private suspend fun performManualSync(): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                val syncManager = SyncManager(this@MainActivity)
                syncManager.performSyncDirect()
            } catch (e: Exception) {
                SyncResult(0, e.message ?: "Unknown error", 0)
            }
        }
    }

    // In your Activity or Fragment
    fun triggerImmediateSync() {
        val workManager = WorkManager.getInstance(this)

        // Create one-time work request
        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("sync_work_one_time")
            .build()

        // Observe the work info
        workManager.getWorkInfoByIdLiveData(oneTimeWorkRequest.id)
            .observe(this) { workInfo ->
                workInfo?.let {
                    when (it.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            // ✅ GET API RESPONSE DATA
                            val syncedCount = it.outputData.getInt("syncedCount", 0)
                            val message = it.outputData.getString("message") ?: ""
                            val serverTime = it.outputData.getLong("serverTime", 0)
                            val details = it.outputData.getString("details") ?: ""

                            // ✅ SHOW API RESPONSE IN TOAST
                            if (syncedCount == 0) {
                                Toast.makeText(
                                    this,
                                    "✅ API Response: No new stops to sync\n$message",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    "✅ API Response: $syncedCount stops synced\n$message",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            // Log full API response
                            Log.d("Sync", "========== API RESPONSE ==========")
                            Log.d("Sync", "Synced Count: $syncedCount")
                            Log.d("Sync", "Message: $message")
                            Log.d("Sync", "Server Time: $serverTime")
                            Log.d("Sync", "Details: $details")
                            Log.d("Sync", "==================================")
                        }

                        WorkInfo.State.FAILED -> {
                            val message = it.outputData.getString("message") ?: "Sync failed"
                            Log.e("TAG", "triggerImmediateSync: -----------${message}")

                            // ✅ SHOW ERROR RESPONSE
                            Toast.makeText(
                                this,
                                "❌ API Response: $message",
                                Toast.LENGTH_LONG
                            ).show()

                            Log.d("Sync", "API Error: $message")
                        }

                        WorkInfo.State.RUNNING -> {
                            Toast.makeText(this, "⏳ Syncing...", Toast.LENGTH_SHORT).show()
                        }

                        else -> {}
                    }
                }
            }

        // Enqueue the work
        workManager.enqueue(oneTimeWorkRequest)
    }

    override fun setupObservers() {
        // Observe tracking state
        observeStateFlow(viewModel.isTracking) { running ->
            binding.tvStatus.setText(if (running) R.string.tracking_status_running else R.string.tracking_status_stopped)
            if (running) {
                binding.tvTimer.visibility = View.VISIBLE
            } else {
                binding.tvTimer.visibility = View.GONE
            }
            updateButtons()
        }

        // Route points observer - draws polyline
        observeStateFlow(viewModel.routePoints) { points ->
            Log.d("RouteObserver", "Points updated: ${points.size} points") // Debug log
            updatePolyline(points)
        }

        // Stop points observer - adds markers
        observeStateFlow(viewModel.stopPoints) { stops ->
            updateStopMarkers(stops)
        }

        lifecycleScope.launch {
            while (true) {
                updateTimers()
                delay(1000)
            }
        }


        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tripStartState.collect { resource ->
                    handleTripStartResponse(resource)
                }
            }
        }

        observeStateFlow(viewModel.tripEndState) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    Log.d("TripAPI", "Ending trip...")
                }

                is Resource.Success -> {
                    resource.data?.let { response ->
                        if (response.success == 1) {
                            response.data?.let { endData ->
                                Log.d("TripAPI", "Trip ended successfully:")
                                Log.d("TripAPI", "  Trip ID: ${endData.tripId}")
                                Log.d("TripAPI", "  End Time: ${endData.endTime}")
                                Log.d("TripAPI", "  End Coordinates: ${endData.endCoordinates}")
                                Log.d("TripAPI", "  Modified By: ${endData.modifiedBy}")
                            }
                        }
                    }
                }

                is Resource.Error -> {
                    Log.e("TripAPI", "End error: ${resource.message}")
                }

                null -> { /* Initial state */
                }

                else -> {}
            }
        }


        // Observe start location
        observeStartLocation()
    }

    private fun loadAndDisplayAllStops() {
        lifecycleScope.launch {
            try {
                // Get all stops from database using StopDao
                val allStops = stopRepository.getAllStops() // You need to add this method to StopRepository

                if (allStops.isNotEmpty()) {
                    Log.d("STOPS", "📍 Loading ${allStops.size} stops from database")

                    // Convert StopEntity to StopPoint for display
                    val stopPoints = allStops.mapNotNull { stopEntity ->
                        stopEntity.lat.let { lat ->
                            stopEntity.lng.let { lng ->
                                StopPoint(
                                    id = stopEntity.id,
                                    center = LatLng(lat, lng),
                                    name = stopEntity.name,
                                    startTimeMillis = stopEntity.startWallTimeMillis,
                                    endTimeMillis = stopEntity.endWallTimeMillis,
                                    letter = stopEntity.letter,
                                    address = stopEntity.address,
                                    phone = stopEntity.phone,
                                    imageUri = stopEntity.imageUri,
                                    locationLabel = stopEntity.locationLabel,
                                    tripId = preferenceManager.getTripId(),
                                    salesExecutiveId = preferenceManager.getSalesExecutiveId().toString()
                                )
                            }
                        }
                    }

                    // Add markers for all stops
                    addAllStopMarkersToMap(stopPoints)
                } else {
                    Log.d("STOPS", "📭 No stops found in database")
                }
            } catch (e: Exception) {
                Log.e("STOPS", "Error loading stops from database", e)
            }
        }
    }

    private fun addAllStopMarkersToMap(stops: List<StopPoint>) {
        val map = googleMap ?: return

        // Clear existing stop markers that are not part of current tracking
        // Be careful not to clear markers that are part of ongoing tracking
        if (!viewModel.isTracking.value) {
            clearMapStops()
        }

        stops.forEachIndexed { index, stop ->
            val letter = stop.letter ?: "?"
            val color = getColorForIndex(index)
            val markerIcon = createMarkerWithLetter(letter, color)

            val marker = map.addMarker(
                MarkerOptions()
                    .position(stop.center)
                    .title("Stop $letter: ${stop.name ?: "Unnamed"}")
                    .snippet("Duration: ${stop.timeSpentMinutes} min")
                    .icon(markerIcon)
            )

            marker?.let {
                stopMarkers.add(stop.id to it)
                it.tag = StopMarkerData("STOP", stopPoint = stop)
            }
        }

        Log.d("STOPS", "✅ Added ${stops.size} stop markers to map")
    }

    private fun showStartTripProgress() {
        startTripProgressDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Starting Trip")
            .setMessage("Please wait...")
            .setCancelable(false)
            .show()
    }

    private fun dismissStartTripProgress() {
        startTripProgressDialog?.dismiss()
        startTripProgressDialog = null
    }

    private fun handleTripStartResponse(resource: Resource<StartTripResponse>?) {
        when (resource) {
            is Resource.Loading -> {
                showStartTripProgress()
            }

            is Resource.Success -> {
                dismissStartTripProgress()
                binding.btnStart.isEnabled = true
                binding.btnStart.text = "Start"

                resource.data?.let { response ->
                    if (response.success == 1) {
                        Toast.makeText(
                            this,
                            "Trip started! ID: ${response.data?.tripId}",
                            Toast.LENGTH_LONG
                        ).show()

                        binding.btnStop.isEnabled = true
                        binding.btnStop.text = "Stop"
                        startActualTracking()
                        lifecycleScope.launch {
                            performanceRepo.startSession()
                        }
                        viewModel.clearTripStartState()
                    } else {
                        Toast.makeText(
                            this,
                            response.message ?: "Failed to start trip",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            is Resource.Error -> {
                dismissStartTripProgress()
//                binding.btnStart.isEnabled = true
//                binding.btnStart.text = "Start"
//                Toast.makeText(this, "Failed to start trip: ${resource.message}", Toast.LENGTH_LONG)
//                    .show()
                val errorMessage = resource.message ?: ""
                Log.e(
                    "TAG",
                    "handleTripStartResponse: ON GOING TRIP ID :: ${resource.onGoingTripId}",
                )
                if (errorMessage.contains("already has an ongoing trip")) {
                    if (resource.onGoingTripId != null) {
                        showOngoingTripDialog(resource.onGoingTripId, errorMessage)
                    } else {
                        Toast.makeText(this, "Trip Id Null", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Failed to start trip: $errorMessage", Toast.LENGTH_LONG)
                        .show()
                }
            }

            null -> { /* Initial state */
            }

            else -> {}
        }
    }

    private fun showPerformanceReport(report: PerformanceReport) {
        val formattedReport = formatPerformanceReport(report)

        AlertDialog.Builder(this)
            .setTitle("📊 Trip Performance Report")
            .setMessage(formattedReport)
            .setPositiveButton("OK") { _, _ -> }
            .setNeutralButton("Share") { _, _ ->
                shareReport(formattedReport)
            }
            .show()
    }

    private fun formatPerformanceReport(report: PerformanceReport): String {
        val durationMinutes = report.sessionDurationMs / 60000
        val score = calculateScore(report)

        return buildString {
            appendLine("📊 TRIP PERFORMANCE REPORT")
            appendLine("=".repeat(40))
            appendLine()
            appendLine("⏱️ Trip Duration: ${durationMinutes} minutes")
            appendLine("📍 Locations Tracked: ${report.totalLocationsReceived}")
            appendLine("📡 Update Frequency: ${report.actualUpdateIntervalMs / 1000}s")
            appendLine()

            appendLine("🔋 BATTERY USAGE")
            appendLine("-".repeat(25))
            appendLine("  Drain: ${String.format("%.1f", report.batteryDrainPercent)}%")
            appendLine("  Consumed: ${String.format("%.1f", report.batteryDrainMAh)} mAh")
            appendLine("  Rate: ${String.format("%.1f", report.estimatedPowerPerHourMAh)} mAh/hour")
            appendLine("  Temp: ${String.format("%.1f", report.batteryTemperature)}°C")
            appendLine()

            appendLine("💻 SYSTEM RESOURCES")
            appendLine("-".repeat(25))
            appendLine("  CPU Avg: ${String.format("%.1f", report.avgCpuUsagePercent)}%")
            appendLine("  CPU Max: ${String.format("%.1f", report.maxCpuUsagePercent)}%")
            appendLine("  Memory: ${String.format("%.1f", report.avgMemoryUsageMB)} MB")
            if (report.memoryLeakSuspicion) {
                appendLine("  ⚠️ Memory Leak Detected!")
            }
            appendLine()

            appendLine("🎯 GPS EFFICIENCY")
            appendLine("-".repeat(25))
            appendLine("  Target: ${report.targetUpdateIntervalMs / 1000}s")
            appendLine("  Actual: ${report.actualUpdateIntervalMs / 1000}s")
            appendLine("  Efficiency: ${report.updateEfficiencyPercent}%")
            appendLine("  Missed Updates: ${report.missedUpdates}")
            appendLine("  Delayed: ${report.delayedUpdates}")
            appendLine("  GPS Fix Time: ${report.gpsFixTimeAvgMs}ms")
            appendLine()

            appendLine("💡 OPTIMIZATION TIPS")
            appendLine("-".repeat(25))
            report.recommendations.take(3).forEach { rec ->
                appendLine("  • $rec")
            }
            appendLine()

            appendLine("📈 PERFORMANCE SCORE: $score/100")
            when {
                score >= 90 -> appendLine("  🎉 Excellent! Your app is very efficient.")
                score >= 70 -> appendLine("  👍 Good, but can be optimized further.")
                score >= 50 -> appendLine("  ⚠️ Needs optimization for better battery life.")
                else -> appendLine("  🔴 Poor performance. Review recommendations.")
            }
        }
    }


    private fun calculateScore(report: PerformanceReport): Int {
        var score = 100

        // Battery penalty (up to 35 points)
        val batteryPenalty = (report.estimatedPowerPerHourMAh / 6).toInt().coerceIn(0, 35)
        score -= batteryPenalty

        // CPU penalty (up to 25 points)
        val cpuPenalty = (report.avgCpuUsagePercent / 2.5).toInt().coerceIn(0, 25)
        score -= cpuPenalty

        // Memory penalty (up to 20 points)
        val memoryPenalty = (report.avgMemoryUsageMB / 12).toInt().coerceIn(0, 20)
        score -= memoryPenalty

        // Efficiency penalty (up to 20 points)
        val efficiencyPenalty = (100 - report.updateEfficiencyPercent).coerceIn(0, 20)
        score -= efficiencyPenalty

        // Memory leak penalty
        if (report.memoryLeakSuspicion) score -= 15

        return score.coerceIn(0, 100)
    }

    private fun shareReport(report: String) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, report)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share Performance Report"))
    }

    private fun continueExistingTrip(onGoingTripId: Int) {
        // Show loading
        val progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Continuing Trip")
            .setMessage("Loading your existing trip data...")
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            try {
                // ============================================
                // STEP 1: Find the ongoing route in local DB
                // ============================================
                var ongoingRoute = routeRepository.getOngoingRoute()

                // If not found, try to find ANY route without end time
                if (ongoingRoute == null) {
                    val allRoutes = routeRepository.getAllRoutes()
                    ongoingRoute = allRoutes.firstOrNull { it.endTimeMillis == null }
                }

                if (ongoingRoute == null) {
                    Log.e("TripContinue", "No ongoing route found in database")
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@MainActivity,
                        "No active route found to continue",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                Log.d(
                    "TripContinue",
                    "✅ Found ongoing route: ${ongoingRoute.id}, startTime: ${ongoingRoute.startTimeMillis}"
                )

                // ============================================
                // STEP 2: Save the trip ID to preferences
                // ============================================
//                preferenceManager.saveTripId(onGoingTripId.toString())
//                preferenceManager.setTripActive(true)

                // ============================================
                // STEP 3: Continue with existing route (NO NEW ROUTE CREATION)
                // ============================================
                viewModel.continueExistingTrip(ongoingRoute.id)

                progressDialog.dismiss()

                // Update UI
                updateButtons()
                startGpsMonitoring()
                startLocationUpdates()


            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e("TripContinue", "Error continuing trip", e)
                showErrorDialog("Error continuing trip: ${e.message}")
            }
        }
    }

    private fun showOngoingTripDialog(onGoingTripId: Int, errorMessage: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Ongoing Trip Found")
            .setMessage("Sales executive already has an ongoing trip. What would you like to do?")
            .setPositiveButton("Continue Trip") { _, _ ->
                preferenceManager.saveTripId(onGoingTripId.toString())
//                startActualTracking()
//                viewModel.clearTripStartState()
                continueExistingTrip(onGoingTripId)
            }
            .setNegativeButton("End Trip") { _, _ ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            val latitude = location.latitude
                            val longitude = location.longitude

                            if (onGoingTripId != null) {
                                binding.btnStop.isEnabled = false
                                binding.btnStop.text = "Ending Trip..."

                                // First, stop tracking and take screenshot (WAIT FOR IT)
                                fusedLocationClient.lastLocation
                                    .addOnSuccessListener { location ->
                                        if (location != null) {
                                            val latitude = location.latitude
                                            val longitude = location.longitude

                                            lifecycleScope.launch {
                                                try {
                                                    // ============================================
                                                    // STEP 1: Find the ongoing route in local DB
                                                    // ============================================
                                                    var ongoingRoute = routeRepository.getOngoingRoute()

                                                    // If not found, try to find ANY route without end time
                                                    if (ongoingRoute == null) {
                                                        val allRoutes =
                                                            routeRepository.getAllRoutes()
                                                        ongoingRoute = allRoutes.firstOrNull { it.endTimeMillis == null }
                                                    }

                                                    if (ongoingRoute == null) {

                                                        Log.w(
                                                            "TripEnd",
                                                            "No ongoing route found in local DB. Ending trip from server only."
                                                        )
                                                        Toast.makeText( this@MainActivity, "No active route found to end", Toast.LENGTH_SHORT ).show()

                                                        viewModel.endTripWithApi(
                                                            tripId = onGoingTripId,
                                                            salesExecutiveId = preferenceManager.getSalesExecutiveId().toString(),
                                                            latitude = latitude,
                                                            longitude = longitude,
                                                            onSuccess = { message ->

                                                                updateButtons()
                                                                dismissGpsDialog()
                                                                stopLocationUpdates()
                                                                stopGpsMonitoring()
                                                                clearMapRoute()
                                                                viewModel.resetSession()

                                                                Toast.makeText(
                                                                    this@MainActivity,
                                                                    "Trip ended successfully",
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                            },
                                                            onError = { errorMsg ->
                                                                showErrorDialog(errorMsg)
                                                            }
                                                        )

                                                        return@launch
                                                    }

                                                    Log.d(
                                                        "TripEnd",
                                                        "✅ Found ongoing route: ${ongoingRoute.id}, startTime: ${ongoingRoute.startTimeMillis}"
                                                    )

                                                    // ============================================
                                                    // STEP 2: Get all stops created during this route
                                                    // ============================================
                                                    val stopsInRoute =
                                                        stopRepository.getStopsInTimeRange(
                                                            ongoingRoute.startTimeMillis,
                                                            System.currentTimeMillis()
                                                        )
                                                    Log.d(
                                                        "TripEnd",
                                                        "Found ${stopsInRoute.size} stops for this route"
                                                    )

                                                    // ============================================
                                                    // STEP 3: Link stops to route if not already linked
                                                    // ============================================
                                                    stopsInRoute.forEach { stop ->
                                                        routeRepository.linkStopToRoute(
                                                            ongoingRoute.id,
                                                            stop.id
                                                        )
                                                    }

                                                    // ============================================
                                                    // STEP 4: Take screenshot if map is available
                                                    // ============================================
                                                    var screenshotPath: String? = null
                                                    if (googleMap != null) {
                                                        screenshotPath =
                                                            takeMapScreenshotForExistingRoute(
                                                                ongoingRoute.id,
                                                                googleMap!!
                                                            )
                                                    }

                                                    // ============================================
                                                    // STEP 5: Update the existing route with end time
                                                    // ============================================
                                                    val currentTime = System.currentTimeMillis()
                                                    routeRepository.finalizeRoute(
                                                        routeId = ongoingRoute.id,
                                                        endTime = currentTime,
                                                        stopCount = stopsInRoute.size,
                                                        screenshotPath = screenshotPath
                                                    )
                                                    Log.d(
                                                        "TripEnd",
                                                        "✅ Route ${ongoingRoute.id} updated with endTime: $currentTime"
                                                    )

                                                    // ============================================
                                                    // STEP 6: Sync pending stops to server
                                                    // ============================================
                                                    val syncResult =
                                                        syncManager.performImmediateSyncAndAwait()
                                                    if (syncResult.success == 1) {
                                                        Log.d(
                                                            "TripEnd",
                                                            "✅ All stops synced successfully"
                                                        )
                                                    } else {
                                                        Log.w(
                                                            "TripEnd",
                                                            "⚠️ Sync had issues: ${syncResult.message}"
                                                        )
                                                    }

                                                    // ============================================
                                                    // STEP 7: Stop periodic sync
                                                    // ============================================
                                                    stopPeriodicSync()

                                                    // ============================================
                                                    // STEP 8: Call end trip API
                                                    // ============================================
                                                    viewModel.endTripWithApi(
                                                        tripId = onGoingTripId,
                                                        salesExecutiveId = preferenceManager.getSalesExecutiveId()
                                                            .toString(),
                                                        latitude = latitude,
                                                        longitude = longitude,
                                                        onSuccess = { message ->

                                                            updateButtons()
                                                            dismissGpsDialog()
                                                            stopLocationUpdates()
                                                            stopGpsMonitoring()
                                                            clearMapRoute()
                                                            viewModel.resetSession()

                                                            Toast.makeText(
                                                                this@MainActivity,
                                                                "Trip ended successfully. All visits saved.",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        },
                                                        onError = { errorMsg ->
                                                            updateButtons()
                                                            dismissGpsDialog()
                                                            showErrorDialog("Trip ended but API failed: $errorMsg")
                                                        }
                                                    )

                                                } catch (e: Exception) {

                                                    Log.e("TripEnd", "Error ending trip", e)
                                                    showErrorDialog("Error ending trip: ${e.message}")
                                                }
                                            }
                                        } else {
                                            Toast.makeText(
                                                this,
                                                "Unable to get current location",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                    .addOnFailureListener {

                                        Toast.makeText(
                                            this,
                                            "Failed to get location",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            } else {
                                Toast.makeText(this, "No active trip found", Toast.LENGTH_SHORT)
                                    .show()
                                // Still stop local tracking
                                stopGpsMonitoring()
                                stopLocationUpdates()
                                viewModel.stopTracking(googleMap)
                                updateButtons()
                                dismissGpsDialog()
                            }
                        } else {
                            Toast.makeText(
                                this,
                                "Unable to get current location",
                                Toast.LENGTH_LONG
                            ).show()
                            // Still stop tracking even without location
                            stopGpsMonitoring()
                            stopLocationUpdates()
                            viewModel.stopTracking(googleMap)
                            updateButtons()
                            dismissGpsDialog()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT)
                            .show()
                        // Still stop tracking even without location
                        stopGpsMonitoring()
                        stopLocationUpdates()
                        viewModel.stopTracking(googleMap)
                        updateButtons()
                        dismissGpsDialog()
                    }
            }
            .setCancelable(false)
            .show()
    }


    private fun endOngoingTripAndUpdateRoute(ongoingTripId: Int) {
        val progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Ending Trip")
            .setMessage("Processing route data...")
            .setCancelable(false)
            .show()

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude

                    lifecycleScope.launch {
                        try {
                            // ============================================
                            // STEP 1: Find the ongoing route in local DB
                            // ============================================
                            var ongoingRoute = routeRepository.getOngoingRoute()

                            // If not found, try to find ANY route without end time
                            if (ongoingRoute == null) {
                                val allRoutes = routeRepository.getAllRoutes()
                                ongoingRoute = allRoutes.firstOrNull { it.endTimeMillis == null }
                            }

                            if (ongoingRoute == null) {
                                Log.e("TripEnd", "No ongoing route found in database")
                                progressDialog.dismiss()
                                Toast.makeText(
                                    this@MainActivity,
                                    "No active route found to end",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }

                            Log.d(
                                "TripEnd",
                                "✅ Found ongoing route: ${ongoingRoute.id}, startTime: ${ongoingRoute.startTimeMillis}"
                            )

                            // ============================================
                            // STEP 2: Get all stops created during this route
                            // ============================================
                            val stopsInRoute = stopRepository.getStopsInTimeRange(
                                ongoingRoute.startTimeMillis,
                                System.currentTimeMillis()
                            )
                            Log.d("TripEnd", "Found ${stopsInRoute.size} stops for this route")

                            // ============================================
                            // STEP 3: Link stops to route if not already linked
                            // ============================================
                            stopsInRoute.forEach { stop ->
                                routeRepository.linkStopToRoute(ongoingRoute.id, stop.id)
                            }

                            // ============================================
                            // STEP 4: Take screenshot if map is available
                            // ============================================
                            var screenshotPath: String? = null
                            if (googleMap != null) {
                                screenshotPath =
                                    takeMapScreenshotForExistingRoute(ongoingRoute.id, googleMap!!)
                            }

                            // ============================================
                            // STEP 5: Update the existing route with end time
                            // ============================================
                            val currentTime = System.currentTimeMillis()
                            routeRepository.finalizeRoute(
                                routeId = ongoingRoute.id,
                                endTime = currentTime,
                                stopCount = stopsInRoute.size,
                                screenshotPath = screenshotPath
                            )
                            Log.d(
                                "TripEnd",
                                "✅ Route ${ongoingRoute.id} updated with endTime: $currentTime"
                            )

                            // ============================================
                            // STEP 6: Sync pending stops to server
                            // ============================================
                            val syncResult = syncManager.performImmediateSyncAndAwait()
                            if (syncResult.success == 1) {
                                Log.d("TripEnd", "✅ All stops synced successfully")
                            } else {
                                Log.w("TripEnd", "⚠️ Sync had issues: ${syncResult.message}")
                            }

                            // ============================================
                            // STEP 7: Stop periodic sync
                            // ============================================
                            stopPeriodicSync()

                            // ============================================
                            // STEP 8: Call end trip API
                            // ============================================
                            viewModel.endTripWithApi(
                                tripId = ongoingTripId,
                                salesExecutiveId = preferenceManager.getSalesExecutiveId()
                                    .toString(),
                                latitude = latitude,
                                longitude = longitude,
                                onSuccess = { message ->
                                    progressDialog.dismiss()
                                    updateButtons()
                                    dismissGpsDialog()
                                    stopLocationUpdates()
                                    stopGpsMonitoring()
                                    clearMapRoute()
                                    viewModel.resetSession()

                                    Toast.makeText(
                                        this@MainActivity,
                                        "Trip ended successfully. All visits saved.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                onError = { errorMsg ->
                                    progressDialog.dismiss()
                                    updateButtons()
                                    dismissGpsDialog()
                                    showErrorDialog("Trip ended but API failed: $errorMsg")
                                }
                            )

                        } catch (e: Exception) {
                            progressDialog.dismiss()
                            Log.e("TripEnd", "Error ending trip", e)
                            showErrorDialog("Error ending trip: ${e.message}")
                        }
                    }
                } else {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Unable to get current location", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                progressDialog.dismiss()
                Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
            }
    }

    private suspend fun takeMapScreenshotForExistingRoute(
        routeId: Long,
        googleMap: GoogleMap
    ): String? {
        return withContext(Dispatchers.Main) {
            try {
                val currentPosition = googleMap.cameraPosition

                // Get route points
                val routePoints = withContext(Dispatchers.IO) {
                    routeRepository.getRoutePoints(routeId)
                }

                if (routePoints.isEmpty()) {
                    Log.d("Screenshot", "No route points found")
                    return@withContext null
                }

                // Build bounds
                val builder = LatLngBounds.Builder()
                routePoints.forEach { point ->
                    builder.include(LatLng(point.latitude, point.longitude))
                }

                // Add stops
                val stops = withContext(Dispatchers.IO) {
                    routeRepository.getStopsForRoute(routeId)
                }
                stops.forEach { stop ->
                    builder.include(stop.center)
                }

                val bounds = builder.build()
                val padding = 150

                // Use suspendCoroutine to handle callback
                return@withContext suspendCancellableCoroutine { continuation ->
                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(bounds, padding),
                        object : GoogleMap.CancelableCallback {
                            override fun onFinish() {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    googleMap.snapshot { bitmap ->
                                        if (bitmap != null) {
                                            CoroutineScope(Dispatchers.IO).launch {
                                                try {
                                                    val dir = File(filesDir, "route_screenshots")
                                                    if (!dir.exists()) dir.mkdirs()

                                                    val file = File(dir, "route_$routeId.png")
                                                    FileOutputStream(file).use { out ->
                                                        val scaledBitmap =
                                                            Bitmap.createScaledBitmap(
                                                                bitmap,
                                                                bitmap.width * 2,
                                                                bitmap.height * 2,
                                                                true
                                                            )
                                                        scaledBitmap.compress(
                                                            Bitmap.CompressFormat.PNG,
                                                            100,
                                                            out
                                                        )
                                                        scaledBitmap.recycle()
                                                    }

                                                    routeRepository.updateRouteScreenshot(
                                                        routeId,
                                                        file.absolutePath
                                                    )
                                                    continuation.resume(file.absolutePath)
                                                } catch (e: Exception) {
                                                    Log.e("Screenshot", "Error", e)
                                                    continuation.resume(null)
                                                } finally {
                                                    withContext(Dispatchers.Main) {
                                                        googleMap.animateCamera(
                                                            CameraUpdateFactory.newCameraPosition(
                                                                currentPosition
                                                            ),
                                                            300,
                                                            null
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            continuation.resume(null)
                                        }
                                    }
                                }, 500)
                            }

                            override fun onCancel() {
                                continuation.resume(null)
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("Screenshot", "Error: ${e.message}")
                return@withContext null
            }
        }
    }


    private fun startActualTracking() {
        ensureLocationEnabledOrResolve(startOnResolution = true) {
            viewModel.startTracking()
            prepareMapAfterPermissions()
            updateButtons()
            startGpsMonitoring()
            startLocationUpdates()
        }

        if (!isGpsEnabled()) {
            Toast.makeText(this, "Please enable GPS to start tracking", Toast.LENGTH_LONG).show()
        }
    }

    override fun setupListeners() {
        binding.btnStart.setOnClickListener {
//            startActivity(Intent(this, SyncTestActivity::class.java))
            if (!hasAllPermissions()) {
                requestAllPermissions()
                return@setOnClickListener
            }



            dismissAllDialogs()

            // Get current location
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val latitude = location.latitude
                        val longitude = location.longitude
                        // Call API to start trip - this will trigger the observer
                        viewModel.startTripWithApi(
                            salesExecutiveId = preferenceManager.getSalesExecutiveId().toString(),
                            latitude = latitude,
                            longitude = longitude,
                            onSuccess = { tripId ->
                                updateButtons()
                                startPeriodicSyncEvery15Minutes()
//                                startActualTracking()
                            }
                        )
                    } else {
                        Toast.makeText(this, "Unable to get current location", Toast.LENGTH_LONG)
                            .show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
                }
        }


//        binding.btnStop.setOnClickListener {
//            stopGpsMonitoring()
//            stopLocationUpdates()
//            viewModel.stopTracking(googleMap)
//            updateButtons()
//            dismissGpsDialog()
//            Toast.makeText(this, "Tracking stopped. All visits saved.", Toast.LENGTH_SHORT).show()
//        }
        binding.btnStop.setOnClickListener {
            cancelScheduledSync()
            // Show confirmation dialog
            MaterialAlertDialogBuilder(this)
                .setTitle("End Trip")
                .setMessage("Are you sure you want to end this trip?")
                .setPositiveButton("Yes") { dialog, _ ->
                    dialog.dismiss()

                    // Show progress dialog while processing
                    val progressDialog = MaterialAlertDialogBuilder(this)
                        .setTitle("Ending Trip")
                        .setMessage("Processing route and saving data...")
                        .setCancelable(false)
                        .show()

                    // Get current location for end coordinates
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                val latitude = location.latitude
                                val longitude = location.longitude
                                val tripId = viewModel.getCurrentTripId()

                                if (tripId != null) {
                                    binding.btnStop.isEnabled = false
                                    binding.btnStop.text = "Ending Trip..."

                                    // First, stop tracking and take screenshot (WAIT FOR IT)
                                    lifecycleScope.launch {
                                        try {
                                            // Step 1: Stop tracking and get screenshot path
                                            val screenshotPath =
                                                viewModel.stopTracking(googleMap).await()
//
                                            Log.d("TripEnd", "Screenshot saved at: $screenshotPath")

                                            val workManager =
                                                WorkManager.getInstance(this@MainActivity)
                                            syncManager.performImmediateSync()
                                            val syncResult =
                                                syncManager.performImmediateSyncAndAwait() // Wait for sync to complete


                                            if (syncResult.success == 1) {
                                                Log.e("TripEnd", "✅ All stops synced successfully")
                                            } else {
                                                Log.e(
                                                    "TripEnd",
                                                    "⚠️ Sync had issues: ${syncResult.message}"
                                                )
                                                // Continue with end trip even if sync fails
                                            }

                                            workManager.cancelUniqueWork("trip_periodic_sync")

                                            // Step 2: Stop location updates and GPS monitoring
                                            stopLocationUpdates()
                                            stopGpsMonitoring()

                                            // Step 3: Call end trip API
                                            viewModel.endTripWithApi(
                                                salesExecutiveId = preferenceManager.getSalesExecutiveId()
                                                    .toString(),
                                                tripId = tripId,
                                                latitude = latitude,
                                                longitude = longitude,
                                                onSuccess = {
                                                    progressDialog.dismiss()
                                                    binding.btnStop.isEnabled = true
                                                    binding.btnStop.text = "Stop"
//                                                    clearMapRoute()
                                                    updateButtons()
                                                    dismissGpsDialog()
                                                    endTrip()
                                                    stopPeriodicSync()
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "Trip ended successfully. All visits saved.",
                                                        Toast.LENGTH_LONG
                                                    ).show()

                                                },
                                                onError = { errorMessage ->
                                                    progressDialog.dismiss()
                                                    binding.btnStop.isEnabled = true
                                                    binding.btnStop.text = "Stop"
                                                    updateButtons()
                                                    dismissGpsDialog()

                                                    // Show error dialog but tracking is already stopped
                                                    showErrorDialog("Trip ended but API failed: $errorMessage")
                                                }
                                            )
                                        } catch (e: Exception) {
                                            progressDialog.dismiss()
                                            binding.btnStop.isEnabled = true
                                            binding.btnStop.text = "Stop"
                                            updateButtons()
                                            dismissGpsDialog()
                                            Log.e("TripEnd", "Error ending trip", e)
                                            showErrorDialog("Error ending trip: ${e.message}")
                                        }
                                    }
                                } else {
                                    progressDialog.dismiss()
                                    Toast.makeText(this, "No active trip found", Toast.LENGTH_SHORT)
                                        .show()
                                    // Still stop local tracking
                                    stopGpsMonitoring()
                                    stopLocationUpdates()
                                    viewModel.stopTracking(googleMap)
                                    updateButtons()
                                    dismissGpsDialog()
                                }
                            } else {
                                progressDialog.dismiss()
                                Toast.makeText(
                                    this,
                                    "Unable to get current location",
                                    Toast.LENGTH_LONG
                                ).show()
                                // Still stop tracking even without location
                                stopGpsMonitoring()
                                stopLocationUpdates()
                                viewModel.stopTracking(googleMap)
                                updateButtons()
                                dismissGpsDialog()
                            }
                        }
                        .addOnFailureListener {
                            progressDialog.dismiss()
                            Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT)
                                .show()
                            // Still stop tracking even without location
                            stopGpsMonitoring()
                            stopLocationUpdates()
                            viewModel.stopTracking(googleMap)
                            updateButtons()
                            dismissGpsDialog()
                        }
                }
                .setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        binding.btnReset.setOnClickListener {
            confirmReset()
        }

    }

    private fun endTrip() {
        // Generate performance report before ending trip
        lifecycleScope.launch {
            val report = performanceRepo.endSessionAndGenerateReport()
            // Show report to user
            showPerformanceReport(report)
        }
    }

    private fun stopPeriodicSync() {
        val workManager = WorkManager.getInstance(this)

        // Cancel the unique periodic work
        workManager.cancelUniqueWork("trip_periodic_sync")

        // Also cancel any pending one-time syncs
        workManager.cancelAllWorkByTag("sync_work")
        workManager.cancelAllWorkByTag("sync_work_one_time")

        Log.d("Sync", "⏹️ Periodic sync stopped successfully")
        isPeriodicSyncStarted = false
        syncJob?.cancel()
    }

    private fun startPeriodicSyncEvery15Minutes() {
        if (isPeriodicSyncStarted) {
            Log.d("Sync", "Periodic sync already running")
            return
        }

        val workManager = WorkManager.getInstance(this)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInitialDelay(15, TimeUnit.MINUTES)  // ✅ First sync after 15 minutes
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag("periodic_sync_15min")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "trip_periodic_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )

        isPeriodicSyncStarted = true
        Log.d("Sync", "✅ Periodic sync scheduled to run every 15 minutes (first in 15 min)")
    }

    private fun test() {
        if (isPeriodicSyncStarted) {
            Log.d("Sync", "Periodic sync already running")
            return
        }

        isPeriodicSyncStarted = true

        // FOR TESTING: Use coroutine scope with delay
        syncJob = lifecycleScope.launch {
            while (isPeriodicSyncStarted) {
                delay(10 * 1000L) // 10 seconds for testing
                Log.d("Sync", "⏰ Testing: 10 seconds elapsed, starting sync...")

                // Perform sync directly
                val result = performManualSync()

                if (result.success == 1) {
                    Log.d("Sync", "✅ Test sync completed: ${result.syncedCount} records")
                    Toast.makeText(
                        this@MainActivity,
                        "Sync: ${result.syncedCount} stops",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.e("Sync", "❌ Test sync failed: ${result.message}")
                }
            }
        }

        Log.d("Sync", "✅ Test sync scheduled to run every 10 seconds")
    }


    private fun cancelScheduledSync() {
        syncJob?.cancel()
        syncJob = null
        isSyncScheduled = false
        Log.d("SyncScheduler", "⏹️ Scheduled sync cancelled")
    }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun loadData() {
        // Start timer updates with lifecycle awareness
        Log.d("TimerDebug", "loadData() called - Starting timer updates")

        // Start timer updates with lifecycle awareness
        lifecycleScope.launch {
            Log.d("TimerDebug", "Timer coroutine started")
            while (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                withContext(Dispatchers.Main) {
                    updateTimers()
                }
                delay(1000)
            }

            Log.d("TimerDebug", "Timer coroutine ended")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isStartMarkerSet", isStartMarkerSet)
        outState.putParcelable("pendingStartLocation", pendingStartLocation)
        outState.putBoolean("isWaitingForMap", isWaitingForMap)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        isStartMarkerSet = savedInstanceState.getBoolean("isStartMarkerSet", false)
        @Suppress("DEPRECATION")
        pendingStartLocation = savedInstanceState.getParcelable("pendingStartLocation")
        isWaitingForMap = savedInstanceState.getBoolean("isWaitingForMap", false)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        playUpdateManager.onActivityResult(requestCode, resultCode, data)
    }


    override fun onResume() {
        super.onResume()
        binding.navView.setCheckedItem(R.id.nav_home)
        dismissAllDialogs()
        viewModel.clearTripStartState()
        playUpdateManager.onResume()
        Log.e("MainActivity", "========= BroadcastReceiver REGISTERED in onResume =========")

//        if (!isViewOnlyMode) {
//            lifecycleScope.launch {
//                // Check if there's an ongoing trip that needs to be displayed
//                viewModel.restoreTrackingStateFromDatabase()
//
//                if (viewModel.isTracking.value) {
//                    // Restore the ongoing route on map
//                    val ongoingRoute = routeRepository.getOngoingRoute()
//                    if (ongoingRoute != null) {
//                        refreshOngoingRouteOnMap()
//                        startGpsMonitoring()
//                        startLocationUpdates()
//                    }
//                }
//            }
//        }

        if (hasAllPermissions()) {
            prepareMapAfterPermissions()
            checkLocationSettings()
            if (viewModel.isTracking.value) {
                startGpsMonitoring()
                startLocationUpdates()
//                lifecycleScope.launch {
//                    restoreOngoingRouteAndExitRouteMode()
//                }

            }
        }
    }

    private fun restoreOngoingRouteAndExitRouteMode() {
        Log.d("RouteRestore", "🔄 Restoring ongoing route...")

        // Show loading indicator
        val progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Loading")
            .setMessage("Restoring your ongoing trip...")
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            try {
                // ✅ FIRST: Restore tracking state from database
                viewModel.restoreTrackingStateFromDatabase()

                // Find the ongoing route (don't rely on isTracking)
                var ongoingRoute = routeRepository.getOngoingRoute()

                if (ongoingRoute == null) {
                    val allRoutes = routeRepository.getAllRoutes()
                    ongoingRoute = allRoutes.firstOrNull { it.endTimeMillis == null }
                }

                if (ongoingRoute != null) {
                    Log.d("RouteRestore", "✅ Found ongoing route: ${ongoingRoute.id}")

                    // Clear all current markers and polylines
                    routePolyline?.remove()
                    clearMapStops()
                    startMarker?.remove()
                    startMarker = null
                    isStartMarkerSet = false

                    // Load and display ongoing route
                    val routePoints = routeRepository.getRoutePoints(ongoingRoute.id)
                    if (routePoints.isNotEmpty()) {
                        // Draw route polyline
                        drawRoutePolyline(routePoints)
                        addRouteStartEndMarkers(routePoints)

                        // Load and add stops
                        val stops = routeRepository.getStopsForRoute(ongoingRoute.id)
                        if (stops.isNotEmpty()) {
                            addRouteStopMarkers(stops)
                        }

                        // Restore start marker from route points
                        val startPoint = routePoints.first()
                        addStartMarkerToMap(LatLng(startPoint.latitude, startPoint.longitude))

                        // Zoom to show the route
                        zoomToRouteBounds(routePoints)

                        // ✅ Update UI elements for tracking
                        updateButtons()
                        binding.tvStatus.text = "🟢 Tracking  •  Session: ${formatElapsedTime()}"

                        Toast.makeText(
                            this@MainActivity,
                            "Back to ongoing trip",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Log.d("RouteRestore", "No ongoing route found")
                    centerMapOnCurrentLocation()
                }

                // Reset the from_places flag and update toolbar
                isFromPlaces = false
                supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
                supportActionBar?.title = "Sales Visit Tracker"

                // Clear pending route display flags
                pendingShowFullRoute = false
                pendingRouteId = null

                // Start GPS monitoring if tracking is now active
                if (viewModel.isTracking.value) {
                    startGpsMonitoring()
                    startLocationUpdates()
                }

                progressDialog.dismiss()

            } catch (e: Exception) {
                Log.e("RouteRestore", "Error restoring route", e)
                progressDialog.dismiss()
                showErrorDialog("Error restoring route: ${e.message}")
            }
        }
    }

    // Helper to format elapsed time
    private fun formatElapsedTime(): String {
        val sessionSeconds = viewModel.getSessionElapsedSeconds()
        val mins = sessionSeconds / 60
        val secs = sessionSeconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

    override fun onPause() {
        super.onPause()
        stopGpsMonitoring()
        stopLocationUpdates()
        dismissLocationDialogs()
        dismissGpsDialog()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopGpsMonitoring()
        playUpdateManager.onDestroy()
        stopLocationUpdates()
    }

    // ==================== TIMER METHODS ====================

    private suspend fun updateTimers() {
        // Current stop timer
        val currentStopTime = viewModel.getCurrentStopTime()
        if (currentStopTime > 0) {
            val seconds = (System.currentTimeMillis() / 1000) % 60
            binding.tvTimer.text = "Timer: ${currentStopTime}:${String.format("%02d", seconds)}"
            binding.tvTimer.visibility = View.VISIBLE
        } else {
            binding.tvTimer.visibility = View.GONE
        }

        // Session timer
        val sessionSeconds = viewModel.getSessionElapsedSeconds()
        if (sessionSeconds > 0) {
            val mins = sessionSeconds / 60
            val secs = sessionSeconds % 60
            binding.tvStatus.text = getString(
                if (viewModel.isTracking.value) R.string.tracking_status_running else R.string.tracking_status_stopped
            ) + "  •  Session: ${mins}:${String.format("%02d", secs)}"
        }
    }


    // ==================== MAP CALLBACK ====================
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        selectedMapType = GoogleMap.MAP_TYPE_NORMAL
        googleMap?.mapType = selectedMapType
        Log.d("StartMarker", "🗺️ Map is ready")

        // Configure map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = true
        map.uiSettings.isCompassEnabled = true
        map.uiSettings.isMapToolbarEnabled = true
        map.uiSettings.isIndoorLevelPickerEnabled = true

        // Enable all map features for rich snapshot
        map.isTrafficEnabled = false
        map.isBuildingsEnabled = true
        map.isIndoorEnabled = true
        map.setPadding(0, 0, 0, 0)

        enableMyLocation()
        applyMapPadding()
        checkLocationSettings()
        setupMarkerClickListener(map)

        // Handle pending start location if any
        if (pendingStartLocation != null && !isStartMarkerSet) {
            Log.d("StartMarker", "📌 Adding pending start marker from onMapReady")
            addStartMarkerToMap(pendingStartLocation!!)
            pendingStartLocation = null
            isWaitingForMap = false
        }

        // 🔥 NEW: Load and display all stops from database
        // Only load all stops if not in view-only mode from Places
        if (!isViewOnlyMode) {
            loadAndDisplayAllStops()
        }

        Log.d("MAP_DEBUG", "=== Map Ready State ===")
        Log.d("MAP_DEBUG", "pendingShowFullRoute: $pendingShowFullRoute")
        Log.d("MAP_DEBUG", "pendingRouteId: $pendingRouteId")
        Log.d("MAP_DEBUG", "pendingStopId: $pendingStopId")
        Log.d("MAP_DEBUG", "pendingRouteHasStops: $pendingRouteHasStops")
        Log.d("MAP_DEBUG", "isTracking: ${viewModel.isTracking.value}")
        Log.d("MAP_DEBUG", "routePoints size: ${viewModel.routePoints.value.size}")
        Log.d("MAP_DEBUG", "pendingCenterOnMyLocation: $pendingCenterOnMyLocation")

        // Handle stop from PlacesActivity
        handlePendingStopFromPlaces()
        when {
            // Priority 1: Show full route if requested
            pendingShowFullRoute && pendingRouteId != null && pendingRouteId != 0L -> {
                Log.d("MAP_DEBUG", "🎯 Priority 1: Loading full route")
                loadAndDisplayFullRoute(pendingRouteId!!, pendingRouteHasStops ?: true)
            }
            // Priority 2: Show single stop from Places
            pendingStopId != null && pendingStopId != 0L -> {
                Log.d("MAP_DEBUG", "🎯 Priority 2: Showing stop from Places")
                handlePendingStopFromPlaces()
            }
            // Priority 3: Show active tracking route
            viewModel.isTracking.value && viewModel.routePoints.value.isNotEmpty() -> {
                Log.d("MAP_DEBUG", "🎯 Priority 3: Showing active tracking route")
                zoomToFullRoute()
            }
            // Priority 4: Normal map initialization
            else -> {
                Log.d("LOCATION", "📍 Centering on current location (first open)")
                centerMapOnCurrentLocation()
            }
        }

        setupMarkerClickListener(map)
    }

    // ==================== MAP & MARKER METHODS ====================

    private fun setupMarkerClickListener(map: GoogleMap) {
        map.setOnMarkerClickListener { marker ->
            when (val tag = marker.tag) {
                is StopMarkerData -> {
                    when (tag.type) {
                        "START" -> {
                            showStartEndInfoDialog("Journey Start", tag.time ?: "Unknown")
                            true
                        }

                        "END" -> {
                            showStartEndInfoDialog("Journey End", tag.time ?: "Unknown")
                            true
                        }

                        "STOP" -> {
                            marker.hideInfoWindow()
                            tag.stopPoint?.let { stop ->
                                showStopDetailsDialog(stop, marker)
                            }
                            true
                        }

                        else -> false
                    }
                }

                else -> false
            }
        }
    }

    private fun updatePolyline(points: List<LocationPoint>) {
        val map = googleMap ?: return
        if (points.isNotEmpty()) {
            val latLngs = points.map { it.latLng }
            if (routePolyline == null) {
                routePolyline = map.addPolyline(
                    PolylineOptions()
                        .color(ContextCompat.getColor(this, R.color.polyline_color))
                        .width(10f)
                        .addAll(latLngs)
                )
            } else {
                routePolyline?.points = latLngs
            }
            if (latLngs.isNotEmpty() && viewModel.isTracking.value) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLngs.last(), 16f))
            }
        } else {
            routePolyline?.remove()
            routePolyline = null
        }
    }

    private fun updateStopMarkers(stops: List<StopPoint>) {
        val map = googleMap ?: return
        clearMapStops()

        val stopsWithLetters = viewModel.getStopsWithSequence()
        Log.e("TAG", "updateStopMarkers: ------------${stopsWithLetters.size}")
        if (stopsWithLetters.isEmpty()) return

        stopsWithLetters.forEachIndexed { index, (letter, stop) ->
            val color = getColorForIndex(index)
            val markerIcon = createMarkerWithLetter(letter, color)

            val marker = map.addMarker(
                MarkerOptions()
                    .position(stop.center)
                    .title("Stop $letter: ${stop.name ?: "Unnamed"}")
                    .snippet("Duration: ${stop.timeSpentMinutes} min")
                    .icon(markerIcon)
            )

            if (marker != null) {
                stopMarkers.add(stop.id to marker)
                marker.tag = StopMarkerData("STOP", stopPoint = stop)
            }
        }
    }

    private fun addStartMarkerToMap(latLng: LatLng) {
        val map = googleMap ?: return

        if (isStartMarkerSet && startMarker != null) {
            val currentPos = startMarker?.position
            if (currentPos != null && currentPos.latitude == latLng.latitude && currentPos.longitude == latLng.longitude) {
                return
            }
        }

        if (isStartMarkerSet) {
            startMarker?.remove()
            startMarker = null
        }

        val markerIcon = vectorToBitmap(R.drawable.ic_start_arrow)
        startMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Start Point")
                .snippet("Journey started here")
                .icon(markerIcon)
                .anchor(0.5f, 0.5f)
        )

        if (startMarker != null) {
            isStartMarkerSet = true
            isWaitingForMap = false
            startMarker?.showInfoWindow()

            if (!viewModel.isTracking.value || viewModel.routePoints.value.isEmpty()) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
            }
        }
    }

    private fun observeStartLocation() {
        if (isObservingStartLocation) return
        isObservingStartLocation = true

        lifecycleScope.launch {
            viewModel.startLocation.collectLatest { startLatLng ->
                if (startLatLng != null) {
                    handleStartLocation(startLatLng)
                } else {
                    removeStartMarker()
                }
            }
        }
    }

    private fun handleStartLocation(latLng: LatLng) {
        if (googleMap != null) {
            addStartMarkerToMap(latLng)
        } else {
            pendingStartLocation = latLng
            isWaitingForMap = true
        }
    }

    private fun removeStartMarker() {
        startMarker?.remove()
        startMarker = null
        isStartMarkerSet = false
        pendingStartLocation = null
        isWaitingForMap = false
    }

    // ==================== ROUTE & STOP HANDLING ====================

    private fun handlePendingStopFromPlaces() {
        val map = googleMap ?: return
        val stopId = pendingStopId ?: return

        if (stopId == 0L) {
            Log.d("PLACES_DEBUG", "Invalid stop ID: 0, ignoring")
            pendingStopId = null
            return
        }

        val lat = intent?.getDoubleExtra("center_lat", Double.NaN) ?: Double.NaN
        val lng = intent?.getDoubleExtra("center_lng", Double.NaN) ?: Double.NaN

        if (!lat.isNaN() && !lng.isNaN()) {
            Log.d("PLACES_DEBUG", "Handling stop from Places:")
            Log.d("PLACES_DEBUG", "  Stop ID: $stopId")
            Log.d("PLACES_DEBUG", "  Stop found in ViewModel: false")
            val latLng = LatLng(lat, lng)
            loadStopFromDatabase(stopId, latLng, map)
        }
    }

    private fun loadStopFromDatabase(stopId: Long, latLng: LatLng, map: GoogleMap) {
        lifecycleScope.launch {
            try {
                // Fetch stop from database via ViewModel
                val stop = viewModel.getStopById(stopId)

                withContext(Dispatchers.Main) {
                    if (stop != null) {
                        Log.d("PLACES_DEBUG", "✅ Stop loaded from database:")
                        Log.d("PLACES_DEBUG", "  Stop name: ${stop.name}")
                        Log.d("PLACES_DEBUG", "  Stop letter: ${stop.letter}")

                        // Add marker and show info window
                        addMarkerForStop(stop, latLng, map)
                    } else {
                        Log.e("PLACES_DEBUG", "❌ Stop not found in database: $stopId")
                        Toast.makeText(
                            this@MainActivity,
                            "Stop details not found",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Fallback: show marker without details
                        addSimpleMarker(latLng, map)
                    }
                }
            } catch (e: Exception) {
                Log.e("PLACES_DEBUG", "Error loading stop from database", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error loading stop", Toast.LENGTH_SHORT)
                        .show()
                    addSimpleMarker(latLng, map)
                }
            }
        }
    }

    private fun addMarkerForStop(stop: StopPoint, latLng: LatLng, map: GoogleMap) {
        val stopLetter = stop.letter ?: viewModel.getStopLetter(stop.id) ?: "?"
        val index = if (stopLetter != "?" && stopLetter.isNotEmpty()) stopLetter[0] - 'A' else 0
        val color = getColorForIndex(index)
        val markerIcon = createMarkerWithLetter(stopLetter, color)

        val marker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Stop $stopLetter: ${stop.name ?: "Seller Shop"}")
                .icon(markerIcon)
        )

        marker?.tag = StopMarkerData("STOP", stopPoint = stop)
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18f))
        showStopDetailsDialog(stop, marker!!)
    }

    private fun addSimpleMarker(latLng: LatLng, map: GoogleMap) {
        val marker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Seller Shop")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
    }

    private fun loadAndDisplayFullRoute(routeId: Long, hasStops: Boolean = true) {
        val map = googleMap ?: return

        Toast.makeText(
            this,
            if (hasStops) "🔄 Loading route with stops..." else "🔄 Loading driving route...",
            Toast.LENGTH_SHORT
        ).show()

        // ✅ Set view only mode when loading route from Places
        isViewOnlyMode = true
        isFromPlaces = true
        updateButtonsForViewMode()

        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back_white)
        supportActionBar?.title = "Route Details"

        lifecycleScope.launch {
            try {
                val routePoints = routeRepository.getRoutePoints(routeId)
                withContext(Dispatchers.Main) {
                    if (routePoints.isNotEmpty()) {
                        routePolyline?.remove()
                        clearMapStops()
                        drawRoutePolyline(routePoints)
                        addRouteStartEndMarkers(routePoints)

                        if (hasStops) {
                            val stops = routeRepository.getStopsForRoute(routeId)
                            addRouteStopMarkers(stops)
                        }

                        zoomToRouteBounds(routePoints)

                        if (pendingStopId != null && pendingStopId != 0L) {
                            val stops = routeRepository.getStopsForRoute(routeId)
//                            highlightSpecificStop(pendingStopId!!, stops)
                        }

                        Toast.makeText(
                            this@MainActivity,
                            if (hasStops) "✅ Route with stops loaded" else "✅ Driving route loaded",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "No route points found",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ROUTE_DEBUG", "Error loading route", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error loading route", Toast.LENGTH_SHORT)
                        .show()
                }
            } finally {
                pendingShowFullRoute = false
                pendingRouteId = null
            }
        }
    }


    private fun drawRoutePolyline(routePoints: List<RoutePointEntity>) {
        val map = googleMap ?: return
        val latLngs = routePoints.map { LatLng(it.latitude, it.longitude) }
        routePolyline = map.addPolyline(
            PolylineOptions()
                .color(ContextCompat.getColor(this, R.color.polyline_color))
                .width(10f)
                .addAll(latLngs)
        )
    }

    private fun addRouteStartEndMarkers(routePoints: List<RoutePointEntity>) {
        val map = googleMap ?: return
        if (routePoints.isEmpty()) return

        val startPoint = routePoints.first()
        val endPoint = routePoints.last()
        val startLatLng = LatLng(startPoint.latitude, startPoint.longitude)
        val endLatLng = LatLng(endPoint.latitude, endPoint.longitude)

        val startTime = SimpleDateFormat(
            "hh:mm a",
            Locale.getDefault()
        ).format(Date(routePoints.first().timestamp))
        val endTime = SimpleDateFormat(
            "hh:mm a",
            Locale.getDefault()
        ).format(Date(routePoints.last().timestamp))

        val startIcon = vectorToBitmap(R.drawable.ic_start)
        val startMarker = map.addMarker(
            MarkerOptions().position(startLatLng).title("Start Point")
                .snippet("Started at $startTime").icon(startIcon).anchor(0.5f, 0.5f)
        )
        val endIcon = vectorToBitmap(R.drawable.ic_stop)
        val endMarker = map.addMarker(
            MarkerOptions().position(endLatLng).title("End Point").snippet("Ended at $endTime")
                .icon(endIcon).anchor(0.5f, 0.5f)
        )

        startMarker?.let { stopMarkers.add(-1L to it); it.tag = StopMarkerData("START", startTime) }
        endMarker?.let { stopMarkers.add(-2L to it); it.tag = StopMarkerData("END", endTime) }
    }

    private fun addRouteStopMarkers(stops: List<StopPoint>) {
        val map = googleMap ?: return
        val sortedStops = stops.sortedBy { it.startTimeMillis }

        sortedStops.forEachIndexed { index, stop ->
//            val letter = stop.letter ?: ('A'.plus(index)).toString()
            val letter = stop.letter ?: ('A' + index).toString()
            val color = getColorForIndex(index)
            val markerIcon = createMarkerWithLetter(letter, color)

            val marker = map.addMarker(
                MarkerOptions()
                    .position(stop.center)
                    .title("Stop $letter: ${stop.name ?: "Unnamed"}")
                    .snippet("Duration: ${stop.timeSpentMinutes} min")
                    .icon(markerIcon)
            )

            marker?.let {
                stopMarkers.add(stop.id to it)
                it.tag = StopMarkerData("STOP", stopPoint = stop)
            }
        }
    }

    private fun zoomToRouteBounds(routePoints: List<RoutePointEntity>) {
        val map = googleMap ?: return
        if (routePoints.isEmpty()) return

        val builder = LatLngBounds.Builder()
        routePoints.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
        stopMarkers.forEach { builder.include(it.second.position) }

        val bounds = builder.build()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
    }

    private fun zoomToFullRoute() {
        val map = googleMap ?: return
        val points = viewModel.routePoints.value
        if (points.isEmpty()) return

        val builder = LatLngBounds.Builder()
        points.forEach { builder.include(it.latLng) }
        val bounds = builder.build()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
    }

    // ==================== DIALOG METHODS ====================

//    private fun showStopDetailsDialog(stop: StopPoint, marker: Marker) {
//        val form = layoutInflater.inflate(R.layout.dialog_stop_form, null)
//        val etName = form.findViewById<EditText>(R.id.etName)
//        val tvLocation = form.findViewById<TextView>(R.id.tvLocation)
//        val etAddress = form.findViewById<EditText>(R.id.etAddress)
//        val etPhone = form.findViewById<EditText>(R.id.etPhone)
//        val tvDuration = form.findViewById<TextView>(R.id.tvDuration)
//
//        // Get stop letter
//        val stopLetter = viewModel.getStopLetter(stop.id) ?: "?"
//        val color = getColorForIndex((stopLetter[0] - 'A'))
//
//        // Create letter bitmap directly (not from marker)
//        val letterBitmap = createLetterBitmap(stopLetter, color)
//
//        val letterImageView = ImageView(this).apply {
//            setImageBitmap(letterBitmap)  // Now using bitmap directly
//            layoutParams = ViewGroup.LayoutParams(100, 100)
//            setPadding(0, 8, 0, 8)
//        }
//
//        val imgView = ImageView(this).apply {
//            adjustViewBounds = true
//            maxHeight = 400
//        }
//
//        currentStopDialogPhotoView = imgView
//        etName.setText(stop.name ?: "")
//
//        val locationText = if (!stop.locationLabel.isNullOrBlank()) {
//            stop.locationLabel
//        } else {
//            String.format("Lat: %.5f  Lng: %.5f", stop.center.latitude, stop.center.longitude)
//        }
//
//        tvLocation.text = "Location: $locationText"
//        tvDuration.text = "Visit Duration: ${stop.timeSpentMinutes} min"
//        etAddress.setText(stop.address ?: "")
//        etPhone.setText(stop.phone ?: "")
//        stop.imageUri?.let { imgView.setImageURI(Uri.parse(it)) }
//
//        val container = LinearLayout(this).apply {
//            orientation = LinearLayout.VERTICAL
//
//            // Add letter badge at the top with name
//            val headerLayout = LinearLayout(context).apply {
//                orientation = LinearLayout.HORIZONTAL
//                gravity = android.view.Gravity.CENTER_VERTICAL
//                setPadding(16, 16, 16, 16)
//
//                addView(letterImageView)
//
//                val textLayout = LinearLayout(context).apply {
//                    orientation = LinearLayout.VERTICAL
//                    setPadding(16, 0, 0, 0)
//
//                    val letterText = TextView(context).apply {
//                        text = "Stop $stopLetter"
//                        textSize = 18f
//                        setTypeface(typeface, Typeface.BOLD)
//                    }
//                    addView(letterText)
//
//                    val durationText = TextView(context).apply {
//                        text = "Duration: ${stop.timeSpentMinutes} minutes"
//                        textSize = 14f
//                        setPadding(0, 4, 0, 0)
//                    }
//                    addView(durationText)
//                }
//                addView(textLayout)
//            }
//            addView(headerLayout)
//
//            // Add a divider
//            val divider = View(context).apply {
//                layoutParams = ViewGroup.LayoutParams(
//                    ViewGroup.LayoutParams.MATCH_PARENT,
//                    1
//                )
//                setBackgroundColor(android.graphics.Color.LTGRAY)
//            }
//            addView(divider)
//
//            addView(form)
//
//            // Capture button
//            addView(MaterialButton(context).apply {
//                text = "Capture seller image"
//                setOnClickListener {
//                    try {
//                        // Create permanent directory for seller images
//                        val sellerImagesDir = File(filesDir, "seller_images")
//                        if (!sellerImagesDir.exists()) {
//                            sellerImagesDir.mkdirs()
//                            Log.d("CAMERA", "Created directory: ${sellerImagesDir.absolutePath}")
//                        }
//
//                        // Create file with timestamp
//                        val fileName = "seller_${System.currentTimeMillis()}.jpg"
//                        val photoFile = File(sellerImagesDir, fileName)
//
//                        Log.d("CAMERA", "Saving to: ${photoFile.absolutePath}")
//
//                        val uri = FileProvider.getUriForFile(
//                            this@MainActivity,
//                            "${packageName}.fileprovider",
//                            photoFile
//                        )
//                        pendingPhotoUri = uri
//                        takePictureLauncher.launch(uri)
//                    } catch (e: Exception) {
//                        Log.e("CAMERA", "Error: ${e.message}")
//                        Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
//                    }
//                }
//            })
//
//            addView(imgView)
//        }
//
//        val titleView = TextView(this).apply {
//            text = "Seller Shop Details"
//            textSize = 20f
//            setTypeface(typeface, Typeface.BOLD)
//            setPadding(40, 30, 40, 10)
//        }
//
//        MaterialAlertDialogBuilder(this)
//            .setCustomTitle(titleView)
//            .setView(container)
//            .setPositiveButton("Save") { d, _ ->
//                val name = etName.text?.toString()?.trim().orEmpty()
//                if (name.isEmpty()) {
//                    Toast.makeText(this, "Seller name is required", Toast.LENGTH_SHORT).show()
//                    return@setPositiveButton
//                }
//                val imageUri = pendingPhotoUri?.toString() ?: stop.imageUri
//                viewModel.updateStopDetails(
//                    stop.id,
//                    name,
//                    etAddress.text?.toString()?.trim().orEmpty().ifEmpty { null },
//                    etPhone.text?.toString()?.trim().orEmpty().ifEmpty { null },
//                    imageUri
//                )
//                marker.title = "Stop $stopLetter: $name"
//                marker.showInfoWindow()
//                clearPendingPhoto()
//                d.dismiss()
//            }
//            .setNegativeButton("Close") { d, _ ->
//                clearPendingPhoto()
//                d.dismiss()
//            }
//            .setOnDismissListener { clearPendingPhoto() }
//            .show()
//            .window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
//    }

//    private fun showStopDetailsDialog(stop: StopPoint, marker: Marker) {
//        // Show loading
//        val loadingDialog = MaterialAlertDialogBuilder(this)
//            .setMessage("Loading leads...")
//            .setCancelable(false)
//            .create()
//        loadingDialog.show()
//
//        // Fetch today's leads
//        viewModel.fetchTodayLeads("1","10") { leads ->
//            loadingDialog.dismiss()
//
//            val form = layoutInflater.inflate(R.layout.dialog_stop_form, null)
//            val etName = form.findViewById<EditText>(R.id.etName)
//            val tvLocation = form.findViewById<TextView>(R.id.tvLocation)
//            val etAddress = form.findViewById<EditText>(R.id.etAddress)
//            val etPhone = form.findViewById<EditText>(R.id.etPhone)
//            val tvDuration = form.findViewById<TextView>(R.id.tvDuration)
//            val etLeads = form.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etLeads)
////            val progressLeads = form.findViewById<ProgressBar>(R.id.progressLeads)
//
//            // Get stop letter
//            val stopLetter = viewModel.getStopLetter(stop.id) ?: "?"
//            val color = getColorForIndex((stopLetter[0] - 'A'))
//
//            // Create letter bitmap
//            val letterBitmap = createLetterBitmap(stopLetter, color)
//
//            val letterImageView = ImageView(this).apply {
//                setImageBitmap(letterBitmap)
//                layoutParams = ViewGroup.LayoutParams(100, 100)
//                setPadding(0, 8, 0, 8)
//            }
//
//            val imgView = ImageView(this).apply {
//                adjustViewBounds = true
//                maxHeight = 400
//            }
//
//            currentStopDialogPhotoView = imgView
//            etName.setText(stop.name ?: "")
//
//            val locationText = if (!stop.locationLabel.isNullOrBlank()) {
//                stop.locationLabel
//            } else {
//                String.format("Lat: %.5f  Lng: %.5f", stop.center.latitude, stop.center.longitude)
//            }
//
//            tvLocation.text = "Location: $locationText"
//            tvDuration.text = "Visit Duration: ${stop.timeSpentMinutes} min"
//            etAddress.setText(stop.address ?: "")
//            etPhone.setText(stop.phone ?: "")
//            stop.imageUri?.let { imgView.setImageURI(Uri.parse(it)) }
//
//            // Variable to store selected leads
//            var selectedLeads = mutableListOf<LeadModel>()
//
//            etLeads.setOnClickListener {
//                if (leads.isNotEmpty()) {
//                    val leadModels = leads.map { it.lead }
//                    LeadSelectionDialog(
//                        context = this,
//                        leads = leadModels,  // Now passing List<LeadModel>
//                        preSelectedLeadIds = selectedLeads.map { it.id }
//                    ) { selected ->
//                        selectedLeads = selected.toMutableList()
//                        val leadNames = selected.joinToString(", ") { it.nameOfShop }
//                        etLeads.setText(if (leadNames.isEmpty()) "Select leads" else leadNames)
//                    }.show()
//                } else {
//                    Toast.makeText(this, "No leads available", Toast.LENGTH_SHORT).show()
//                }
//            }
//
//            val container = LinearLayout(this).apply {
//                orientation = LinearLayout.VERTICAL
//
//                // Header layout
//                val headerLayout = LinearLayout(context).apply {
//                    orientation = LinearLayout.HORIZONTAL
//                    gravity = android.view.Gravity.CENTER_VERTICAL
//                    setPadding(16, 16, 16, 16)
//
//                    addView(letterImageView)
//
//                    val textLayout = LinearLayout(context).apply {
//                        orientation = LinearLayout.VERTICAL
//                        setPadding(16, 0, 0, 0)
//
//                        val letterText = TextView(context).apply {
//                            text = "Stop $stopLetter"
//                            textSize = 18f
//                            setTypeface(typeface, Typeface.BOLD)
//                        }
//                        addView(letterText)
//
//                        val durationText = TextView(context).apply {
//                            text = "Duration: ${stop.timeSpentMinutes} minutes"
//                            textSize = 14f
//                            setPadding(0, 4, 0, 0)
//                        }
//                        addView(durationText)
//                    }
//                    addView(textLayout)
//                }
//                addView(headerLayout)
//
//                // Divider
//                val divider = View(context).apply {
//                    layoutParams = ViewGroup.LayoutParams(
//                        ViewGroup.LayoutParams.MATCH_PARENT,
//                        1
//                    )
//                    setBackgroundColor(android.graphics.Color.LTGRAY)
//                }
//                addView(divider)
//
//                addView(form)
//
//                // Capture button
//                addView(MaterialButton(context).apply {
//                    text = "Capture seller image"
//                    setOnClickListener {
//                        try {
//                            val sellerImagesDir = File(filesDir, "seller_images")
//                            if (!sellerImagesDir.exists()) {
//                                sellerImagesDir.mkdirs()
//                                Log.d("CAMERA", "Created directory: ${sellerImagesDir.absolutePath}")
//                            }
//
//                            val fileName = "seller_${System.currentTimeMillis()}.jpg"
//                            val photoFile = File(sellerImagesDir, fileName)
//
//                            Log.d("CAMERA", "Saving to: ${photoFile.absolutePath}")
//
//                            val uri = FileProvider.getUriForFile(
//                                this@MainActivity,
//                                "${packageName}.fileprovider",
//                                photoFile
//                            )
//                            pendingPhotoUri = uri
//                            takePictureLauncher.launch(uri)
//                        } catch (e: Exception) {
//                            Log.e("CAMERA", "Error: ${e.message}")
//                            Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT)
//                                .show()
//                        }
//                    }
//                })
//
//                addView(imgView)
//            }
//
//            val titleView = TextView(this).apply {
//                text = "Seller Shop Details"
//                textSize = 20f
//                setTypeface(typeface, Typeface.BOLD)
//                setPadding(40, 30, 40, 10)
//            }
//
//            MaterialAlertDialogBuilder(this)
//                .setCustomTitle(titleView)
//                .setView(container)
//                .setPositiveButton("Save") { d, _ ->
//                    val name = etName.text?.toString()?.trim().orEmpty()
//                    if (name.isEmpty()) {
//                        Toast.makeText(this, "Seller name is required", Toast.LENGTH_SHORT).show()
//                        return@setPositiveButton
//                    }
//                    val imageUri = pendingPhotoUri?.toString() ?: stop.imageUri
//                    val leadIds = selectedLeads.map { it.id }
//
//                    Log.e("TAG", "Selected Lead IDs: ${leadIds.joinToString(", ")}")
//
//                    viewModel.updateStopDetails(
//                        stop.id,
//                        name,
//                        etAddress.text?.toString()?.trim().orEmpty().ifEmpty { null },
//                        etPhone.text?.toString()?.trim().orEmpty().ifEmpty { null },
//                        imageUri,
//                    )
//                    marker.title = "Stop $stopLetter: $name"
//                    marker.showInfoWindow()
//                    clearPendingPhoto()
//                    d.dismiss()
//                }
//                .setNegativeButton("Close") { d, _ ->
//                    clearPendingPhoto()
//                    d.dismiss()
//                }
//                .setOnDismissListener { clearPendingPhoto() }
//                .show()
//                .window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
//        }
//    }

//    private fun showStopDetailsDialog(stop: StopPoint, marker: Marker) {
//        // Show loading
//        val loadingDialog = MaterialAlertDialogBuilder(this)
//            .setMessage("Loading leads...")
//            .setCancelable(false)
//            .create()
//        loadingDialog.show()
//
//        // Fetch today's leads
//        viewModel.fetchTodayLeads("1", "10") { leads ->
//            loadingDialog.dismiss()
//
//            val form = layoutInflater.inflate(R.layout.dialog_stop_form, null)
//
//            // All fields from create lead screen
//             etName = form.findViewById(R.id.etName)
//             tvLocation = form.findViewById(R.id.tvLocation)
//             etAddress = form.findViewById(R.id.etAddress)
//             etPhone = form.findViewById(R.id.etPhone)
//             tvDuration = form.findViewById(R.id.tvDuration)
//             etContactPerson = form.findViewById(R.id.etContactPerson)
//             etEmailId = form.findViewById(R.id.etEmailId)
//             etArea = form.findViewById<EditText>(R.id.etArea)
//             etSellerAddress = form.findViewById<EditText>(R.id.etSellerAddress)
//             etCurrentLocation = form.findViewById<EditText>(R.id.etCurrentLocation)
//             etExecutiveName = form.findViewById<EditText>(R.id.etExecutiveName)
//             etDate = form.findViewById<EditText>(R.id.etDate)
//             rgSellingType = form.findViewById<RadioGroup>(R.id.rgSellingType)
//             etDemo = form.findViewById<EditText>(R.id.etDemo)
//             etRemark = form.findViewById<EditText>(R.id.etRemark)
//             etVisitingCardImage = form.findViewById<EditText>(R.id.etVisitingCardImage)
//             ivImagePreview = form.findViewById<ImageView>(R.id.ivImagePreview)
//             etOnboardingClientName = form.findViewById<EditText>(R.id.etOnboardingClientName)
//             etPaidAmount = form.findViewById<EditText>(R.id.etPaidAmount)
//             etNotes = form.findViewById<EditText>(R.id.etNotes)
//             etLeads = form.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etLeads)
//             btnCaptureSellerImage = form.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCaptureSellerImage)
//             ivSellerImagePreview = form.findViewById<ImageView>(R.id.ivSellerImagePreview)
//
//            etDate.isFocusable = false
//            etDate.isCursorVisible = false
//
//            // Set current date
//            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
//            etDate.setText(dateFormat.format(Date()))
//
//            // Get stop letter
//            val stopLetter = viewModel.getStopLetter(stop.id) ?: "?"
//            val color = getColorForIndex((stopLetter[0] - 'A'))
//
//            // Create letter bitmap
//            val letterBitmap = createLetterBitmap(stopLetter, color)
//
//            val letterImageView = ImageView(this).apply {
//                setImageBitmap(letterBitmap)
//                layoutParams = ViewGroup.LayoutParams(100, 100)
//                setPadding(0, 8, 0, 8)
//            }
//
//            // Variable for seller image
////            var currentSellerImageUri: Uri? = null
////            var uploadedImageUrl: String? = null
//
//            // Set existing values
//            etName.setText(stop.name ?: "")
//
//            val locationText = if (!stop.locationLabel.isNullOrBlank()) {
//                stop.locationLabel
//            } else {
//                String.format("Lat: %.5f  Lng: %.5f", stop.center.latitude, stop.center.longitude)
//            }
//
//            tvLocation.text = "Location: $locationText"
//            tvDuration.text = "Visit Duration: ${stop.timeSpentMinutes} min"
//            etAddress.setText(stop.address ?: "")
//            etPhone.setText(stop.phone ?: "")
//
//            // Set current location if available
//            etCurrentLocation.setText(locationText)
//
//            // Variable to store selected leads
//            var selectedLeads = mutableListOf<LeadModel>()
//
//            etLeads.setOnClickListener {
//                if (leads.isNotEmpty()) {
//                    val leadModels = leads.map { it.lead }
//                    LeadSelectionDialog(
//                        context = this,
//                        leads = leadModels,
//                        preSelectedLeadIds = selectedLeads.map { it.id }
//                    ) { selected ->
//                        selectedLeads = selected.toMutableList()
//                        val leadNames = selected.joinToString(", ") { it.nameOfShop.toString() }
//                        etLeads.setText(if (leadNames.isEmpty()) "Select leads" else leadNames)
//                    }.show()
//                } else {
//                    Toast.makeText(this, "No leads available", Toast.LENGTH_SHORT).show()
//                }
//            }
//
//            // Handle visiting card image click
//            etVisitingCardImage.setOnClickListener {
//                openImagePicker()
//            }
//
//
//
//            val container = LinearLayout(this).apply {
//                orientation = LinearLayout.VERTICAL
//
//                // Header layout
//                val headerLayout = LinearLayout(context).apply {
//                    orientation = LinearLayout.HORIZONTAL
//                    gravity = android.view.Gravity.CENTER_VERTICAL
//                    setPadding(16, 16, 16, 16)
//
//                    addView(letterImageView)
//
//                    val textLayout = LinearLayout(context).apply {
//                        orientation = LinearLayout.VERTICAL
//                        setPadding(16, 0, 0, 0)
//
//                        val letterText = TextView(context).apply {
//                            text = "Stop $stopLetter"
//                            textSize = 18f
//                            setTypeface(typeface, Typeface.BOLD)
//                        }
//                        addView(letterText)
//
//                        val durationText = TextView(context).apply {
//                            text = "Duration: ${stop.timeSpentMinutes} minutes"
//                            textSize = 14f
//                            setPadding(0, 4, 0, 0)
//                        }
//                        addView(durationText)
//                    }
//                    addView(textLayout)
//                }
//                addView(headerLayout)
//
//                // Divider
//                val divider = View(context).apply {
//                    layoutParams = ViewGroup.LayoutParams(
//                        ViewGroup.LayoutParams.MATCH_PARENT,
//                        1
//                    )
//                    setBackgroundColor(android.graphics.Color.LTGRAY)
//                }
//                addView(divider)
//
//                addView(form)
//            }
//
//            val titleView = TextView(this).apply {
//                text = "Seller Shop Details"
//                textSize = 20f
//                setTypeface(typeface, Typeface.BOLD)
//                setPadding(40, 30, 40, 10)
//            }
//
//            fullScreenDialog = MaterialAlertDialogBuilder(this)
//                .setCustomTitle(titleView)
//                .setView(container)
//                .setPositiveButton("Save") { d, _ ->
////                    setupMobileNumberValidation()
//                    if (validateInputs()){
//                        createLead()
//                        val savingDialog = MaterialAlertDialogBuilder(this)
//                            .setMessage("Saving details...")
//                            .setCancelable(false)
//                            .create()
//                        savingDialog.show()
//                    }
//                }
//                .setNegativeButton("Close") { d, _ ->
//                    d.dismiss()
//                }
//                .create()
//
//
//            fullScreenDialog?.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
//            fullScreenDialog?.show()
//        }
//    }


    private fun showStopDetailsDialog(stop: StopPoint, marker: Marker) {
        // Show loading
        val loadingDialog = MaterialAlertDialogBuilder(this)
            .setMessage("Loading leads...")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        // Fetch today's leads
        viewModel.fetchTodayLeads("1", "10") { leads ->
            loadingDialog.dismiss()

            val form = layoutInflater.inflate(R.layout.dialog_stop_form, null)

            // All fields from create lead screen
            etName = form.findViewById(R.id.etName)
            tvLocation = form.findViewById(R.id.tvLocation)
            etAddress = form.findViewById(R.id.etAddress)
            etPhone = form.findViewById(R.id.etPhone)
            tvDuration = form.findViewById(R.id.tvDuration)
            etContactPerson = form.findViewById(R.id.etContactPerson)
            etEmailId = form.findViewById(R.id.etEmailId)
            etArea = form.findViewById(R.id.etArea)
            etSellerAddress = form.findViewById(R.id.etSellerAddress)
            etCurrentLocation = form.findViewById(R.id.etCurrentLocation)
            etExecutiveName = form.findViewById(R.id.etExecutiveName)
            etDate = form.findViewById(R.id.etDate)
            rgSellingType = form.findViewById(R.id.rgSellingType)
            etDemo = form.findViewById(R.id.rgDemo)
            etRemark = form.findViewById(R.id.etRemark)
            etVisitingCardImage = form.findViewById(R.id.etVisitingCardImage)
            ivImagePreview = form.findViewById(R.id.ivImagePreview)
            etOnboardingClientName = form.findViewById(R.id.etOnboardingClientName)
            etPaidAmount = form.findViewById(R.id.etPaidAmount)
            etNotes = form.findViewById(R.id.etNotes)
            etLeads = form.findViewById(R.id.etLeads)
//            btnCaptureSellerImage = form.findViewById(R.id.btnCaptureSellerImage)
            ivSellerImagePreview = form.findViewById(R.id.ivSellerImagePreview)

            etDate.isFocusable = false
            etDate.isCursorVisible = false

            // Set current date
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            etDate.setText(dateFormat.format(Date()))

            // Setup mobile number validation
            setupMobileNumberValidation()

            // Get stop letter
            val stopLetter = viewModel.getStopLetter(stop.id) ?: "?"
            val color = getColorForIndex((stopLetter[0] - 'A'))

            // Create letter bitmap
            val letterBitmap = createLetterBitmap(stop.letter!!, color)

            val letterImageView = ImageView(this).apply {
                setImageBitmap(letterBitmap)
                layoutParams = ViewGroup.LayoutParams(100, 100)
                setPadding(0, 8, 0, 8)
            }

            // Set existing values
            etName.setText(stop.name ?: "")

            val locationText = if (!stop.locationLabel.isNullOrBlank()) {
                stop.locationLabel
            } else {
                String.format("Lat: %.5f  Lng: %.5f", stop.center.latitude, stop.center.longitude)
            }

            tvLocation.text = "Location: $locationText"
            tvDuration.text = "Visit Duration: ${stop.timeSpentMinutes} min"
            etAddress.setText(stop.address ?: "")

            // Set phone number with +91 prefix if needed
            stop.phone?.let { phone ->
                if (phone.startsWith("+91")) {
                    etPhone.setText(phone)
                } else {
                    etPhone.setText("+91$phone")
                }
            }

            // Set current location if available
            etCurrentLocation.setText(locationText)

            // Variable to store selected leads
            var selectedLeads = mutableListOf<LeadModel>()

            etLeads.setOnClickListener {
                if (leads.isNotEmpty()) {
                    val leadModels = leads.map { it.lead }
                    LeadSelectionDialog(
                        context = this,
                        leads = leadModels,
                        preSelectedLeadIds = selectedLeads.map { it.id }
                    ) { selected ->
                        selectedLeads = selected.toMutableList()
                        val leadNames = selected.joinToString(", ") { it.nameOfShop.toString() }
                        etLeads.setText(if (leadNames.isEmpty()) "Select leads" else leadNames)
                    }.show()
                } else {
                    Toast.makeText(this, "No leads available", Toast.LENGTH_SHORT).show()
                }
            }

            // Handle seller image capture
//            btnCaptureSellerImage.setOnClickListener {
//                captureSellerImage()
//            }

            // Handle visiting card image click
            etVisitingCardImage.setOnClickListener {
                openImagePicker()
            }

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL

                // Header layout
                val headerLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(16, 16, 16, 16)

                    addView(letterImageView)

                    val textLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(16, 0, 0, 0)

                        val letterText = TextView(context).apply {
                            text = "Stop ${stop.letter}"
                            textSize = 18f
                            setTypeface(typeface, Typeface.BOLD)
                        }
                        addView(letterText)

                        val durationText = TextView(context).apply {
                            text = "Duration: ${stop.timeSpentMinutes} minutes"
                            textSize = 14f
                            setPadding(0, 4, 0, 0)
                        }
                        addView(durationText)
                    }
                    addView(textLayout)
                }
                addView(headerLayout)

                // Divider
                val divider = View(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1
                    )
                    setBackgroundColor(Color.LTGRAY)
                }
                addView(divider)

                addView(form)
            }

            val titleView = TextView(this).apply {
                text = "Seller Shop Details"
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(40, 30, 40, 10)
            }

            // Create dialog with custom button handling
            fullScreenDialog = MaterialAlertDialogBuilder(this)
                .setCustomTitle(titleView)
                .setView(container)
                .setPositiveButton("Save", null) // Set null initially
                .setNegativeButton("Close") { d, _ ->
                    d.dismiss()
                }
                .create()

            // Override the positive button behavior
            fullScreenDialog?.setOnShowListener {
                val positiveButton = fullScreenDialog?.getButton(AlertDialog.BUTTON_POSITIVE)
                positiveButton?.setOnClickListener {
                    // Validate inputs
                    if (validateInputs()) {
                        // Show saving progress
                        val savingDialog = MaterialAlertDialogBuilder(this)
                            .setMessage("Saving details...")
                            .setCancelable(false)
                            .create()
                        savingDialog.show()

                        // Call createLead with dialogs
                        createLeadWithDialogs(
                            fullScreenDialog!!,
                            savingDialog,
                            stop,
                            marker,
                            selectedLeads
                        )
                    }
                    // If validation fails, dialog stays open automatically
                }
            }

            fullScreenDialog?.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
            fullScreenDialog?.setCanceledOnTouchOutside(false) // Prevent dismiss on outside touch
            fullScreenDialog?.show()
        }
    }

    private fun createLeadWithDialogs(
        mainDialog: AlertDialog,
        savingDialog: AlertDialog,
        stop: StopPoint,
        marker: Marker,
        selectedLeads: List<LeadModel>
    ) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (preferenceManager.getSalesExecutiveId() == null) {
            savingDialog.dismiss()
            showErrorSnackbar("Sales Executive ID not found. Please login first.")
            return
        }

        val mobileNumber = etPhone.text.toString().replace("+91", "")

        val demoValue = when (etDemo.checkedRadioButtonId) {
            R.id.rbYes -> "YES"
            R.id.rbNo -> "NO"
            else -> "" // or handle validation
        }
        val request = CreateLeadRequest(
            currentLocation = tvLocation.text.toString().trim(),
            date = etDate.text.toString().trim().ifEmpty { currentDate },
            executiveName = etExecutiveName.text.toString().trim(),
            area = etArea.text.toString().trim(),
            sellingType = getSelectedSellingType(),
            demo = demoValue,
            nameOfShop = etName.text.toString().trim(),
            contactPerson = etContactPerson.text.toString().trim(),
            mobileNumber = mobileNumber,
            emailId = etEmailId.text.toString().trim(),
            remark = etRemark.text.toString().trim(),
            sellerAddress = etSellerAddress.text.toString().trim(),
            visitingCardImage = sellerImage,
            onboardingClientName = etOnboardingClientName.text.toString().trim(),
            paidAmount = etPaidAmount.text.toString().trim().toIntOrNull() ?: 0,
            notes = etNotes.text.toString().trim(),
            status = "ACTIVE",
            createdBy = preferenceManager.getSalesExecutiveId().toString(),
            salesExecutiveId = preferenceManager.getSalesExecutiveId().toString()
        )

        lifecycleScope.launch {
            try {
                val response = apiService.createSellLead(request)
                if (response.success == 1) {
                    // Update stop details
                    updateStopDetails(stop, marker, selectedLeads)
                    savingDialog.dismiss()
                    mainDialog.dismiss()
                    showSuccessSnackbar(response.message)
//                    clearForm()
                } else {
                    savingDialog.dismiss()
                    showErrorSnackbar(response.message)
                    // Dialog stays open
                }
            } catch (e: Exception) {
                savingDialog.dismiss()
                showErrorSnackbar("Error: ${e.message}")
                // Dialog stays open
            }
        }
    }

    private fun updateStopDetails(
        stop: StopPoint,
        marker: Marker,
        selectedLeads: List<LeadModel>
    ) {
        val name = etName.text?.toString()?.trim().orEmpty()
        val leadIds = selectedLeads.map { it.id.toString() }

        // Update stop in ViewModel
        viewModel.updateStopDetails(
            stop.id,
            name,
            etAddress.text?.toString()?.trim().orEmpty().ifEmpty { null },
            etPhone.text?.toString()?.trim().orEmpty().ifEmpty { null },
            sellerImage.ifEmpty { null }
        )

        // Update marker
        val stopLetter = viewModel.getStopLetter(stop.id) ?: "?"
        marker.title = "Stop $stopLetter: $name"
        marker.showInfoWindow()
    }

    private fun getSelectedSellingType(): String {
        return when (rgSellingType.checkedRadioButtonId) {
            R.id.rbA1 -> "A1"
            R.id.rbA2 -> "A2"
            R.id.rbA3 -> "A3"
            R.id.rbA4 -> "A4"
            else -> ""
        }
    }

    private fun setupMobileNumberValidation() {
        etPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.toString().startsWith("+91")) {
                    etPhone.removeTextChangedListener(this)
                    etPhone.setText("+91")
                    etPhone.text?.let { etPhone.setSelection(it.length) }
                    etPhone.addTextChangedListener(this)
                }
            }

            override fun afterTextChanged(s: Editable?) {
                val number = s.toString()
                if (number.length > 13) {
                    etPhone.setText(number.substring(0, 13))
                    etPhone.setSelection(13)
                }

                val digitsAfterCode = number.replace("+91", "")
                if (digitsAfterCode.length == 10) {
                    etPhone.error = null
                } else if (digitsAfterCode.isNotEmpty()) {
                    etPhone.error = "Please enter 10 digits after +91"
                }
            }
        })
    }


    private fun showStartEndInfoDialog(title: String, time: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage("Time: $time\n\nThis is where your journey ${if (title.contains("Start")) "began" else "ended"}.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset all routes?")
            .setMessage("This will clear the current polyline, stops, and timers.")
            .setPositiveButton("Reset") { d, _ ->
                viewModel.resetSession()
                clearMapStops()
                routePolyline?.remove()
                routePolyline = null
                startMarker?.remove()
                startMarker = null
                isStartMarkerSet = false
                pendingStartLocation = null
                isWaitingForMap = false
                updateButtons()
                d.dismiss()
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }

    // ==================== PERMISSION & LOCATION METHODS ====================

    private fun maybeRequestPermissionsOnFirstLaunch() {
        val asked = prefs.getBoolean(prefAskedPermissionsKey, false)
        if (!asked) {
            prefs.edit().putBoolean(prefAskedPermissionsKey, true).apply()
            if (!hasAllPermissions()) {
                requestAllPermissions()
            } else {
                prepareMapAfterPermissions()
            }
        } else {
            if (!hasAllPermissions()) {
                showPermissionRequiredDialog()
            } else {
                prepareMapAfterPermissions()
            }
        }
    }

    private fun prepareMapAfterPermissions() {
        pendingCenterOnMyLocation = true
        enableMyLocation()
        applyMapPadding()
        centerMapOnCurrentLocationIfPossible()
    }

    private fun centerMapOnCurrentLocation() {
        val map = googleMap ?: return
        if (!hasLocationPermission()) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val latLng = LatLng(location.latitude, location.longitude)
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
            } else {
                requestCurrentLocation()
            }
        }.addOnFailureListener { requestCurrentLocation() }
    }

    private fun requestCurrentLocation() {
        val map = googleMap ?: return
        if (hasLocationPermission()) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    } else {
                        showDefaultMapView()
                    }
                }
                .addOnFailureListener { showDefaultMapView() }
        } else {
            showDefaultMapView()
        }
    }

    private fun showDefaultMapView() {
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.0, 0.0), 2f))
    }

    private fun centerMapOnCurrentLocationIfPossible() {
        val map = googleMap ?: return
        if (!hasLocationPermission()) return

        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                pendingCenterOnMyLocation = false
                val me = LatLng(loc.latitude, loc.longitude)
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 16f))
            }
        }
    }

    private fun enableMyLocation() {
        googleMap?.isMyLocationEnabled = hasLocationPermission()
    }

    private fun applyMapPadding() {
        val map = googleMap ?: return
        val insets = systemBarInsets
        if (insets != null) {
            map.setPadding(16, insets.top + 24, 24, insets.bottom + 24)
        } else {
            map.setPadding(16, 24, 24, 24)
        }
    }

    private fun checkLocationSettings() {
        val locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(this)

        client.checkLocationSettings(builder.build())
            .addOnSuccessListener {
                Log.d("LocationDebug", "✅ Location settings are OK")
                enableMyLocation()
                dismissLocationDialogs()
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    showLocationRequiredDialog(exception)
                } else {
                    showEnableLocationDialog()
                }
            }
    }

    private fun ensureLocationEnabledOrResolve(startOnResolution: Boolean, onEnabled: () -> Unit) {
        val locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000)
                .setMinUpdateDistanceMeters(5f)
                .build()

        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
            .build()

        val client = LocationServices.getSettingsClient(this)
        client.checkLocationSettings(settingsRequest)
            .addOnSuccessListener { onEnabled() }
            .addOnFailureListener { ex ->
                if (ex is ResolvableApiException) {
                    try {
                        startAfterResolution = startOnResolution
                        val intentSender = IntentSenderRequest.Builder(ex.resolution).build()
                        resolutionLauncher.launch(intentSender)
                    } catch (_: Exception) {
                        showEnableLocationDialog()
                    }
                } else {
                    showEnableLocationDialog()
                }
            }
    }

    private fun isGpsEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    // ==================== LOCATION UPDATES ====================

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                hasLocationFix = true
                lastLocationTime = System.currentTimeMillis()
                dismissAllDialogs()
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                hasLocationFix = locationAvailability.isLocationAvailable
            }
        }

        if (hasLocationPermission()) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
        hasLocationFix = false
    }

    // ==================== GPS MONITORING ====================

    private fun startGpsMonitoring() {
        if (!viewModel.isTracking.value) return

        stopGpsMonitoring()
        hasLocationFix = false
        hasShownNoFixDialog = false
        isWaitingForFirstFix = true
        lastLocationTime = System.currentTimeMillis()

        lifecycleScope.launch {
            delay(10000)
            if (!hasLocationFix && viewModel.isTracking.value && isWaitingForFirstFix) {
                showNoFixDialog()
            }
        }

        gpsCheckJob = lifecycleScope.launch {
            while (true) {
                checkGpsAndLocationStatus()
                delay(gpsCheckInterval)
            }
        }
    }

    private fun stopGpsMonitoring() {
        gpsCheckJob?.cancel()
        gpsCheckJob = null
        isGpsDialogShowing = false
        hasShownGpsDialog = false
        hasShownGpsFailureDialog = false
        hasShownNoFixDialog = false
        isWaitingForFirstFix = false
    }

    private fun checkGpsAndLocationStatus() {
        if (!viewModel.isTracking.value) {
            dismissAllDialogs()
            return
        }

        val isGpsEnabled = isGpsEnabled()
        val timeSinceStart = System.currentTimeMillis() - lastLocationTime

        when {
            !isGpsEnabled -> showGpsRequiredDialog("GPS is turned off in settings")
            hasLocationFix -> {
                if (isGpsDialogShowing) dismissAllDialogs()
                hasShownGpsDialog = false
            }

            else -> {
                if (timeSinceStart > 15000 && !hasShownGpsDialog) {
                    showGpsWaitingSnackbar()
                    hasShownGpsDialog = true
                }
            }
        }
    }

    private fun showGpsWaitingSnackbar() {
        Snackbar.make(binding.root, "📡 Acquiring GPS signal...", Snackbar.LENGTH_INDEFINITE)
            .setAction("OK") { }.show()
    }

    private fun showNoFixDialog() {
        if (hasLocationFix || !viewModel.isTracking.value || hasShownNoFixDialog) return
        hasShownNoFixDialog = true

        MaterialAlertDialogBuilder(this)
            .setTitle("📡 Waiting for GPS")
            .setMessage("Still waiting for GPS signal.\n\nMake sure you're in an open area.")
            .setCancelable(false)
            .setPositiveButton("OK") { d, _ -> hasShownNoFixDialog = false; d.dismiss() }
            .setNegativeButton("Stop Tracking") { d, _ ->
                hasShownNoFixDialog = false
                viewModel.stopTracking()
                updateButtons()
                stopGpsMonitoring()
                d.dismiss()
            }
            .show()
    }

    private fun showGpsRequiredDialog(message: String) {
        if (!viewModel.isTracking.value) return
        if (isGpsDialogShowing) return

        isGpsDialogShowing = true
        MaterialAlertDialogBuilder(this)
            .setTitle("📡 GPS Disabled")
            .setMessage("$message\n\nTracking will pause until GPS is enabled.")
            .setCancelable(false)
            .setPositiveButton("Open Settings") { d, _ ->
                isGpsDialogShowing = false
                hasShownGpsDialog = false
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                d.dismiss()
            }
            .setNegativeButton("Stop Tracking") { d, _ ->
                isGpsDialogShowing = false
                hasShownGpsDialog = false
                viewModel.stopTracking()
                updateButtons()
                stopGpsMonitoring()
                d.dismiss()
            }
            .setOnDismissListener { isGpsDialogShowing = false }
            .show()
    }

    // ==================== DIALOG MANAGEMENT ====================

    private fun showLocationRequiredDialog(exception: ResolvableApiException) {
        if (userDeclinedLocation) {
            showEnableLocationDialog()
            return
        }
        if (isLocationDialogShowing) return

        isLocationDialogShowing = true
        try {
            val intentSender = IntentSenderRequest.Builder(exception.resolution).build()
            resolutionLauncher.launch(intentSender)
        } catch (e: Exception) {
            isLocationDialogShowing = false
            showEnableLocationDialog()
        }
    }

    private fun showEnableLocationDialog() {
        if (isLocationDialogShowing) return
        isLocationDialogShowing = true

        MaterialAlertDialogBuilder(this)
            .setTitle("📍 Location Required")
            .setMessage("Location services are turned off. This app needs location access to track your sales visits.\n\nWithout location, you cannot use the app.")
            .setCancelable(false)
            .setPositiveButton("Open Settings") { d, _ ->
                isLocationDialogShowing = false
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                d.dismiss()
            }
            .setNegativeButton("Exit App") { d, _ ->
                isLocationDialogShowing = false
                d.dismiss()
                finishAffinity()
            }
            .setOnDismissListener { isLocationDialogShowing = false }
            .show()
    }

    private fun showPermissionRequiredDialog() {
        if (isLocationDialogShowing) return
        isLocationDialogShowing = true

        MaterialAlertDialogBuilder(this)
            .setTitle("📱 Permissions Required")
            .setMessage("Without location permission you cannot use this application.\n\nPlease grant permission to continue.")
            .setCancelable(false)
            .setPositiveButton("Open Settings") { d, _ ->
                isLocationDialogShowing = false
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                })
                d.dismiss()
            }
            .setNegativeButton("Exit App") { d, _ ->
                isLocationDialogShowing = false
                d.dismiss()
                finishAffinity()
            }
            .show()
    }

    private fun showLocationPermissionPermanentlyDeniedDialog() {
        if (isLocationDialogShowing) return
        isLocationDialogShowing = true

        MaterialAlertDialogBuilder(this)
            .setTitle("🔒 Permission Permanently Denied")
            .setMessage("Location permission is permanently denied. Please enable it in app settings to use this app.")
            .setCancelable(false)
            .setPositiveButton("Open Settings") { d, _ ->
                isLocationDialogShowing = false
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                })
                d.dismiss()
            }
            .setNegativeButton("Exit App") { d, _ ->
                isLocationDialogShowing = false
                d.dismiss()
                finishAffinity()
            }
            .show()
    }

    private fun dismissAllDialogs() {
        isGpsDialogShowing = false
        hasShownGpsDialog = false
        hasShownNoFixDialog = false
        isLocationDialogShowing = false
    }

    private fun dismissLocationDialogs() {
        isLocationDialogShowing = false
    }

    private fun dismissGpsDialog() {
        isGpsDialogShowing = false
        hasShownGpsDialog = false
    }

    // ==================== UTILITY METHODS ====================

    private fun updateButtons() {
        val running = viewModel.isTracking.value
        binding.btnStart.visibility = if (running) View.GONE else View.VISIBLE
        binding.btnStop.visibility = if (running) View.VISIBLE else View.GONE
        binding.btnReset.visibility = View.VISIBLE
    }

    private fun clearMapStops() {
        stopMarkers.forEach { it.second.remove() }
        stopMarkers.clear()
    }

    private fun clearPendingPhoto() {
        currentStopDialogPhotoView = null
        pendingPhotoUri = null
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAllPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val notif = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        return fine && coarse && notif
    }

    private fun requestAllPermissions() {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissionsLauncher.launch(list.toTypedArray())
    }

    private fun getColorForIndex(index: Int): Int {
        return when (index % 6) {
            0 -> Color.parseColor("#E53935")
            1 -> Color.parseColor("#1E88E5")
            2 -> Color.parseColor("#43A047")
            3 -> Color.parseColor("#FB8C00")
            4 -> Color.parseColor("#8E24AA")
            5 -> Color.parseColor("#00ACC1")
            else -> Color.parseColor("#757575")
        }
    }

    private fun createMarkerWithLetter(letter: String, color: Int): BitmapDescriptor {
        return BitmapDescriptorFactory.fromBitmap(createLetterBitmap(letter, color))
    }

    private fun createLetterBitmap(letter: String, colors: Int): Bitmap {
        val size = 80
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = colors
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, circlePaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = 4f
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, borderPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val xPos = size / 2f
        val yPos = size / 2f - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(letter, xPos, yPos, textPaint)

        return bitmap
    }

    private fun vectorToBitmap(@DrawableRes vectorResId: Int): BitmapDescriptor {
        return try {
            val vectorDrawable = ContextCompat.getDrawable(this, vectorResId)
            if (vectorDrawable == null) return BitmapDescriptorFactory.defaultMarker(
                BitmapDescriptorFactory.HUE_GREEN
            )

            val bitmap = Bitmap.createBitmap(
                vectorDrawable.intrinsicWidth.takeIf { it > 0 } ?: 96,
                vectorDrawable.intrinsicHeight.takeIf { it > 0 } ?: 96,
                Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(bitmap)
            vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
            vectorDrawable.draw(canvas)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        } catch (e: Exception) {
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
        }
    }

    // ==================== EDGE TO EDGE & INSETS ====================

    private fun enableEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT

            val insetsController = WindowInsetsControllerCompat(window, window.decorView)

            // Get current theme's status bar icon style
            val typedValue = TypedValue()
//            theme.resolveAttribute(R.attr.windowLightStatusBar, typedValue, true)
            val isLightStatusBar =
                typedValue.type == TypedValue.TYPE_INT_BOOLEAN && typedValue.data != 0

            insetsController.isAppearanceLightStatusBars = isLightStatusBar
            insetsController.isAppearanceLightNavigationBars = isLightStatusBar
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.navView.setPadding(0, systemBars.top, 0, systemBars.bottom)
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val card = findViewById<View>(R.id.controlsCard)
            card?.setPadding(
                card.paddingLeft,
                card.paddingTop,
                card.paddingRight,
                systemBarInsets?.bottom?.plus(16) ?: 16
            )
            applyMapPadding()
            insets
        }
    }

    // ==================== NAVIGATION DRAWER ====================

    private fun setupNavigationDrawer() {
        setSupportActionBar(binding.toolbar)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)

            // ✅ Set different behavior for view only mode
            if (isViewOnlyMode) {
                setHomeAsUpIndicator(R.drawable.ic_back_white)
                title = "Route Details"
            } else {
                setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
                title = "Sales Visit Tracker"
            }
        }

        // Store current selected item
        var currentSelectedNavItem = R.id.nav_home

        // Set default selected item
        binding.navView.setCheckedItem(currentSelectedNavItem)

        binding.drawerLayout.setDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: View) {}
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    currentSelectedNavItem = R.id.nav_home
                    binding.drawerLayout.closeDrawers()
                    true
                }

                R.id.nav_places -> {
                    currentSelectedNavItem = R.id.nav_places
                    binding.drawerLayout.closeDrawers()
                    startActivity(Intent(this, PlacesActivity::class.java))
                    true
                }


                R.id.nav_sync -> {
                    currentSelectedNavItem = R.id.nav_sync
                    binding.drawerLayout.closeDrawers()
                    startActivity(Intent(this, SyncTestActivity::class.java))
                    true
                }

                R.id.nav_calendar -> {
                    currentSelectedNavItem = R.id.nav_calendar
                    binding.drawerLayout.closeDrawers()
                    startActivity(Intent(this, MainActivity2::class.java))
                    true
                }

                R.id.nav_export_pdf -> {
                    currentSelectedNavItem = R.id.nav_export_pdf
                    binding.drawerLayout.closeDrawers()
                    showDatePickerForReport()
                    binding.navView.setCheckedItem(R.id.nav_home)
                    currentSelectedNavItem = R.id.nav_home
                    true
                }

                R.id.nav_lead -> {
                    currentSelectedNavItem = R.id.nav_lead
                    binding.drawerLayout.closeDrawers()

                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            val intent = Intent(this, LeadActivity::class.java).apply {
                                putExtra("latitude", location.latitude)
                                putExtra("longitude", location.longitude)
                                putExtra("has_location", true)
                            }
                            startActivity(intent)
                        } else {
                            val intent = Intent(this, LeadActivity::class.java).apply {
                                putExtra("has_location", false)
                            }
                            startActivity(intent)
                            Toast.makeText(
                                this,
                                "Unable to get current location",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }.addOnFailureListener {
                        val intent = Intent(this, LeadActivity::class.java).apply {
                            putExtra("has_location", false)
                        }
                        startActivity(intent)
                        Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
                    }
                    true
                }

                R.id.nav_api -> {
                    currentSelectedNavItem = R.id.nav_api
                    binding.drawerLayout.closeDrawers()
                    startActivity(Intent(this, ApiActivity::class.java))
                    true
                }

                R.id.nav_attendance -> {
                    currentSelectedNavItem = R.id.nav_attendance
                    binding.drawerLayout.closeDrawers()

                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            val intent = Intent(this, ActivityAttendance::class.java).apply {
                                putExtra("latitude", location.latitude)
                                putExtra("longitude", location.longitude)
                                putExtra("has_location", true)
                            }
                            attendanceLauncher.launch(intent)
                        } else {
                            val intent = Intent(this, ActivityAttendance::class.java).apply {
                                putExtra("has_location", false)
                            }
                            startActivity(intent)
                            Toast.makeText(
                                this,
                                "Unable to get current location",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }.addOnFailureListener {
                        val intent = Intent(this, ActivityAttendance::class.java).apply {
                            putExtra("has_location", false)
                        }
                        startActivity(intent)
                        Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
                    }
                    true
                }

                R.id.nav_log_out -> {
                    binding.drawerLayout.closeDrawers()
                    val previousSelectedItem = currentSelectedNavItem
                    showLogoutDialog(previousSelectedItem)
                    true
                }

                else -> false
            }
        }

        // ✅ Handle toolbar click based on view mode
        /*binding.toolbar.setNavigationOnClickListener {
            if (isViewOnlyMode) {
                // Coming from Places - restore ongoing trip and exit view mode
                restoreOngoingTripAndExitViewMode()
            } else {
                // Normal behavior - open drawer
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }*/


        binding.toolbar.setNavigationOnClickListener {
            if (isViewOnlyMode) {
                // Coming from Places - check if there's an ongoing trip
                lifecycleScope.launch {
                    val ongoingRoute = routeRepository.getOngoingRoute()
                    val hasOngoingTrip = ongoingRoute != null && ongoingRoute.endTimeMillis == null

                    if (hasOngoingTrip) {
                        // There is an ongoing trip - restore it
                        Log.d("RouteRestore", "Ongoing trip exists, restoring...")
                        restoreOngoingTripAndExitViewMode()
                    } else {
                        // No ongoing trip, just exit view mode
                        Log.d("RouteRestore", "No ongoing trip, just exiting view mode")
                        exitViewModeOnly()
                    }
                }
            } else {
                // Normal behavior - open drawer
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }
    }
    private fun exitViewModeOnly() {
        Log.d("RouteRestore", "Exiting view mode (no ongoing trip)")

        // Exit view mode
        isViewOnlyMode = false
        isFromPlaces = false

        // Update toolbar
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
        supportActionBar?.title = "Sales Visit Tracker"

        // Show action buttons
        updateButtonsForViewMode()

        // Clear any pending route display flags
        pendingShowFullRoute = false
        pendingRouteId = null
        pendingStopId = null

        // Clear map from the viewed route
        routePolyline?.remove()
        routePolyline = null
        clearMapStops()
        startMarker?.remove()
        startMarker = null

        // Center on current location if permissions are granted
        if (hasAllPermissions()) {
            centerMapOnCurrentLocation()
        }

        Toast.makeText(this, "Back to main screen", Toast.LENGTH_SHORT).show()
    }


    private fun restoreOngoingTripAndExitViewMode() {
        Log.d("RouteRestore", "🔄 Restoring ongoing route and exiting view mode...")

        val progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Loading")
            .setMessage("Restoring your ongoing trip...")
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            try {
                // Step 1: Get ongoing route from database
                var ongoingRoute = routeRepository.getOngoingRoute()
                if (ongoingRoute == null) {
                    val allRoutes = routeRepository.getAllRoutes()
                    ongoingRoute = allRoutes.firstOrNull { it.endTimeMillis == null }
                }

                if (ongoingRoute == null) {
                    Log.e("RouteRestore", "No ongoing route found")
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "No ongoing trip found", Toast.LENGTH_SHORT)
                        .show()
                    return@launch
                }

                Log.d("RouteRestore", "✅ Found ongoing route: ${ongoingRoute.id}")
                Log.d("RouteRestore", "Route original start time: ${ongoingRoute.startTimeMillis}")

                // 🔥 CRITICAL: Completely reset and restart everything
                withContext(Dispatchers.Main) {
                    // Clear map
                    routePolyline?.remove()
                    routePolyline = null
                    clearMapStops()
                    startMarker?.remove()
                    startMarker = null
                    googleMap?.clear()
                    enableMyLocation()
                }

                // Get all points from database
                val routePoints = routeRepository.getRoutePoints(ongoingRoute.id)
                Log.d("RouteRestore", "Loading ${routePoints.size} existing points")

                // Get all stops
                val stops = routeRepository.getStopsForRoute(ongoingRoute.id)
                Log.d("RouteRestore", "Loading ${stops.size} stops")

                // 🔥 COMPLETELY RESET VIEW MODEL
                viewModel.resetSession()

                // 🔥 Add all points directly to sessionState
                routePoints.forEach { point ->
                    val locationPoint = LocationPoint(
                        LatLng(point.latitude, point.longitude),
                        point.timestamp
                    )
                    viewModel.addLocationPointDirectly(locationPoint)
                }

                // Add stops
                stops.forEach { stop ->
                    viewModel.addStopPointDirectly(stop)
                }

                // 🔥 CRITICAL: Set the original start time before restarting tracking
                viewModel.setOriginalStartTime(ongoingRoute.startTimeMillis)

                // 🔥 Set current route ID
                viewModel.setCurrentRouteId(ongoingRoute.id)

                // 🔥 Force restart the tracking service
                viewModel.stopTrackingForRestore()

                // Small delay
                delay(500)

                // 🔥 Restart tracking with the existing route ID AND original start time
                viewModel.continueExistingTripWithOriginalTime(
                    ongoingRoute.id,
                    ongoingRoute.startTimeMillis
                )

                // Wait for tracking to initialize
                delay(500)

                // Draw on map
                withContext(Dispatchers.Main) {
                    val points = viewModel.routePoints.value
                    if (points.isNotEmpty()) {
                        val latLngs = points.map { it.latLng }
                        routePolyline = googleMap?.addPolyline(
                            PolylineOptions()
                                .color(Color.parseColor("#FF2196F3"))
                                .width(18f)
                                .addAll(latLngs)
                        )
                        Log.d("RouteRestore", "Drew ${points.size} points on map")

                        // Zoom to bounds
                        try {
                            val bounds = LatLngBounds.Builder()
                            latLngs.forEach { bounds.include(it) }
                            googleMap?.animateCamera(
                                CameraUpdateFactory.newLatLngBounds(
                                    bounds.build(),
                                    100
                                )
                            )
                        } catch (e: Exception) {
                            Log.e("RouteRestore", "Zoom error: ${e.message}")
                        }
                    }

                    // Add stop markers
                    addRouteStopMarkers(stops)

                    // Add start marker
                    if (routePoints.isNotEmpty()) {
                        addStartMarkerToMap(
                            LatLng(
                                routePoints.first().latitude,
                                routePoints.first().longitude
                            )
                        )
                    }

                    updateButtons()

                    // 🔥 Calculate and display correct elapsed time using original start time
                    val elapsedSeconds = viewModel.getSessionElapsedSeconds()
                    val minutes = elapsedSeconds / 60
                    val seconds = elapsedSeconds % 60
                    binding.tvStatus.text =
                        "🟢 Tracking  •  Session: $minutes:${String.format("%02d", seconds)}"
                    Log.d(
                        "RouteRestore",
                        "Session elapsed time: $elapsedSeconds seconds ($minutes minutes $seconds seconds)"
                    )
                }

                // Exit view mode
                isViewOnlyMode = false
                isFromPlaces = false

                withContext(Dispatchers.Main) {
                    supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
                    supportActionBar?.title = "Sales Visit Tracker"
                    updateButtonsForViewMode()
                    startGpsMonitoring()
                    startLocationUpdates()
                }

                progressDialog.dismiss()
                Toast.makeText(
                    this@MainActivity,
                    "Back to ongoing trip - Tracking resumed",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Log.e("RouteRestore", "Error", e)
                progressDialog.dismiss()
                showErrorDialog("Error: ${e.message}")
            }
        }
    }

    private fun showLogoutDialog(previousSelectedItem: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setIcon(R.drawable.ic_logout)
            .setPositiveButton("Yes, Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                binding.navView.setCheckedItem(previousSelectedItem)
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun performLogout() {

        val hasActiveAttendance = preferenceManager.isAttendanceLoggedIn()

        val message = if (hasActiveAttendance) {
            "You have an active attendance session.\n\nLogging out of the app will NOT close your attendance session. Your last login time will still be visible.\n\nDo you want to proceed?"
        } else {
            "Are you sure you want to logout?"
        }

        // Show loading
        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm Logout")
            .setMessage(message)
            .setPositiveButton("Logout") { _, _ ->
//                performActualLogout()
                // Clear preferences in background
                lifecycleScope.launch(Dispatchers.IO) {
                    // Clear all preferences
//            preferenceManager.clearAllData()
                    preferenceManager.clearMainLoginData()

                    clearCache()
                    viewModel.cancelOngoingRequests()

                    // Switch to main thread for navigation
                    withContext(Dispatchers.Main) {
                        // Navigate to login screen
                        val intent = Intent(this@MainActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()


    }

    private fun clearCache() {
        try {
            // Clear Glide cache
            Glide.get(this).clearDiskCache()
            Glide.get(this).clearMemory()

            // Clear app cache directory
            val cacheDir = cacheDir
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.deleteRecursively()
            }

            // Clear seller images directory
            val sellerImagesDir = File(filesDir, "seller_images")
            if (sellerImagesDir.exists()) {
                sellerImagesDir.deleteRecursively()
            }

            Log.d("Logout", "Cache cleared successfully")
        } catch (e: Exception) {
            Log.e("Logout", "Error clearing cache", e)
        }
    }


    // ==================== EXPORT METHODS ====================

    private fun showDatePickerForReport() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day, 0, 0, 0)
                generateDailyReport(calendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun generateDailyReport(dateMillis: Long) {
        val reportRepo = ReportRepository(this)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("📊 Generating Daily Report")
            .setMessage("Fetching route data and calculating distances...")
            .setCancelable(false)
            .show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val reportData = reportRepo.getDailyReport(dateMillis)
                if (reportData.routes.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        Toast.makeText(
                            this@MainActivity,
                            "No routes found for this date",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    dialog.setMessage("Creating PDF with enhanced formatting...")
                }

                /*EnhancedPdfReportGenerator(this@MainActivity).generateDailyReport(reportData) { file ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        dialog.dismiss()
                        if (file != null) {
                            Toast.makeText(
                                this@MainActivity,
                                "✅ Report saved: ${file.name}",
                                Toast.LENGTH_LONG
                            ).show()
                            openPdfFile(file)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "❌ Failed to generate report",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }*/

                EnhancedPdfReportGenerator(this@MainActivity).generateDailyReport(
                    this@MainActivity,
                    reportData
                ) { uri ->

                    lifecycleScope.launch(Dispatchers.Main) {

                        dialog.dismiss()

                        if (uri != null) {

                            Toast.makeText(
                                this@MainActivity,
                                "✅ Report saved successfully in Downloads",
                                Toast.LENGTH_LONG
                            ).show()

                            openPdfFile(uri)

                        } else {

                            Toast.makeText(
                                this@MainActivity,
                                "❌ Failed to generate report",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }


//    private fun openPdfFile(file: File) {
//        try {
//            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
//            val intent = Intent(Intent.ACTION_VIEW).apply {
//                setDataAndType(uri, "application/pdf")
//                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//            }
//            startActivity(intent)
//        } catch (e: Exception) {
//            Toast.makeText(this, "No PDF viewer found", Toast.LENGTH_SHORT).show()
//        }
//    }

    private fun openPdfFile(uri: Uri) {

        try {

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")

                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NO_HISTORY
            }
            startActivity(intent)
        } catch (e: Exception) {

            Toast.makeText(
                this,
                "No PDF viewer app found",
                Toast.LENGTH_SHORT
            ).show()

            e.printStackTrace()
        }
    }

    private fun exportPdfInternal() {
        val places = viewModel.getCompletedStopsMergedByName()
        if (places.isEmpty()) {
            Toast.makeText(this, "No completed stops to export", Toast.LENGTH_SHORT).show()
            return
        }

        places.forEachIndexed { index, stop ->
            Log.d("PDFExport", "Stop $index: ${stop.name}, Minutes: ${stop.timeSpentMinutes}")
        }

        // Get stops with sequence letters
        val stopsWithLetters = viewModel.getStopsWithSequence()

        val input = EditText(this)
        input.hint = "Seller name"
        input.setText(places.firstOrNull { !it.name.isNullOrBlank() }?.name ?: "")

        MaterialAlertDialogBuilder(this)
            .setTitle("Export PDF")
            .setMessage("Enter seller name for the report header")
            .setView(input)
            .setPositiveButton("Export") { d, _ ->
                val seller = input.text?.toString()?.trim()?.ifEmpty { "Seller" }

                captureRouteScreenshot { bitmap ->
                    var screenshotPath: String? = null

                    if (bitmap != null) {
                        screenshotPath = saveBitmapToFile(bitmap)
                        Log.d("PDFExport", "Map screenshot saved to: $screenshotPath")
                    }

                    val rows = places.sortedBy { it.startTimeMillis }.map { stop ->
                        val letter = stopsWithLetters.firstOrNull { it.second.id == stop.id }?.first
                        PdfUtils.StopRow(
                            name = stop.name,
                            lat = stop.center.latitude,
                            lng = stop.center.longitude,
                            startMillis = stop.startTimeMillis,
                            endMillis = stop.endTimeMillis,
                            durationMinutes = stop.timeSpentMinutes,
                            locationLabel = stop.locationLabel,
                            address = stop.address,
                            phone = stop.phone,
                            imageUri = stop.imageUri,
                            routeMapUri = screenshotPath,
                            sequenceLetter = letter  // Add the letter
                        )
                    }

                    val pathOrName = seller?.let { PdfUtils.generateSellerReport(this, it, rows) }
                    Toast.makeText(this, "PDF saved: $pathOrName", Toast.LENGTH_LONG).show()

                    // Clean up temp file
                    screenshotPath?.let { path ->
                        try {
                            File(path).delete()
                        } catch (e: Exception) {
                        }
                    }
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }

    private fun captureRouteScreenshot(callback: (Bitmap?) -> Unit) {
        val map = googleMap ?: run {
            callback(null)
            return
        }

        val routePoints = viewModel.routePoints.value
        val stopPoints = viewModel.stopPoints.value

        if (routePoints.isEmpty() && stopPoints.isEmpty()) {
            map.snapshot { callback(it) }
            return
        }

        Toast.makeText(this, "📸 Capturing detailed route map...", Toast.LENGTH_SHORT).show()

        // Build bounds including all points with extra padding
        val builder = LatLngBounds.Builder()

        // Add all route points
        routePoints.forEach { builder.include(it.latLng) }

        // Add all stop points
        stopPoints.forEach { builder.include(it.center) }

        // Add start point if available
        viewModel.startLocation.value?.let { builder.include(it) }

        val bounds = builder.build()
        val padding = 150

        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, padding),
            object : GoogleMap.CancelableCallback {
                override fun onFinish() {
                    Handler(Looper.getMainLooper()).postDelayed({
                        map.snapshot { bitmap ->
                            callback(if (bitmap != null) enhanceMapSnapshot(bitmap) else null)
                        }
                    }, 2000)
                }

                override fun onCancel() {
                    map.snapshot { callback(it) }
                }
            }
        )
    }

    private fun enhanceMapSnapshot(original: Bitmap): Bitmap {
        try {
            val enhanced = original.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(enhanced)
            val paint = Paint()
            paint.colorFilter = ColorMatrixColorFilter(
                ColorMatrix().apply {
                    setSaturation(1.1f)
                    setScale(1.1f, 1.1f, 1.1f, 1.0f)
                }
            )
            canvas.drawBitmap(enhanced, 0f, 0f, paint)
            return enhanced
        } catch (e: Exception) {
            return original
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap): String? {
        return try {
            val file = File(cacheDir, "route_map_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("MapScreenshot", "Failed to save bitmap", e)
            null
        }
    }

    private fun clearMapRoute() {
        // Clear the polyline from map
        routePolyline?.remove()
        routePolyline = null


        // Clear start marker
        startMarker?.remove()
        startMarker = null
        isStartMarkerSet = false

        // Clear route points in ViewModel
//        viewModel.resetSession()

        Log.d("MapClear", "✅ Map route and markers cleared")
    }

    private fun showSuccessSnackbar(message: String) {
        showSnackbar(message, Snackbar.LENGTH_SHORT, CreateLeadActivity.SnackbarType.SUCCESS)
    }

    private fun showSnackbar(
        message: String,
        duration: Int = Snackbar.LENGTH_SHORT,
        type: CreateLeadActivity.SnackbarType = CreateLeadActivity.SnackbarType.INFO
    ) {
        try {
            val snackbar = Snackbar.make(binding.root, message, duration)

            when (type) {
                CreateLeadActivity.SnackbarType.SUCCESS -> {
                    snackbar.setBackgroundTint(
                        ContextCompat.getColor(
                            this,
                            R.color.md_theme_light_primary
                        )
                    )
                    snackbar.setTextColor(ContextCompat.getColor(this, R.color.white))
                }

                CreateLeadActivity.SnackbarType.ERROR -> {
                    snackbar.setBackgroundTint(
                        ContextCompat.getColor(
                            this,
                            R.color.md_theme_light_error
                        )
                    )
                    snackbar.setTextColor(ContextCompat.getColor(this, R.color.white))
                    snackbar.setAction("Dismiss") { snackbar.dismiss() }
                    snackbar.setActionTextColor(ContextCompat.getColor(this, R.color.white))
                }

                CreateLeadActivity.SnackbarType.WARNING -> {
                    snackbar.setBackgroundTint(
                        ContextCompat.getColor(
                            this,
                            R.color.snackbar_warning_bg
                        )
                    )
                    snackbar.setTextColor(ContextCompat.getColor(this, R.color.snackbar_text_color))
                }

                CreateLeadActivity.SnackbarType.INFO -> {
                    snackbar.setBackgroundTint(
                        ContextCompat.getColor(
                            this,
                            R.color.snackbar_info_bg
                        )
                    )
                    snackbar.setTextColor(ContextCompat.getColor(this, R.color.snackbar_text_color))
                }
            }

            snackbar.show()
        } catch (e: Exception) {
            // Fallback to Toast if CoordinatorLayout is not available
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showErrorSnackbar(message: String) {
        showSnackbar(message, Snackbar.LENGTH_LONG, CreateLeadActivity.SnackbarType.ERROR)
    }

    fun copyUriToTempFile(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Unable to open URI")

        val fileName = "original_${System.currentTimeMillis()}.jpg"
        val tempFile = File(context.cacheDir, fileName)

        FileOutputStream(tempFile).use { output ->
            inputStream.copyTo(output)
        }

        inputStream.close()
        return tempFile
    }

    fun compressImageFromFile(
        context: Context,
        inputFile: File,
        quality: Int
    ): File {
        val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath)

        val compressedFile = File(
            context.cacheDir,
            "compressed_${System.currentTimeMillis()}.jpg"
        )

        FileOutputStream(compressedFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
        }

        return compressedFile
    }

    private fun getPresignedUrlAndUpload(
        bucketName: String,
        fieldLabel: String,
        objectKey: String
    ) {
        lifecycleScope.launch {
            try {
                leadViewModel.getPresignedUrl(bucketName, fieldLabel, objectKey, { response ->
                    // This is PresignedUrlResponse object, not separate parameters
                    val presignedUrl = response.presignedUrl
                    val objectKey = response.objectKey

                    Log.d("TAG", "Presigned URL: $presignedUrl")
                    Log.d("TAG", "Object Key: $objectKey")
                    uploadFileToS3(response.presignedUrl, fileMain)
                })

            } catch (e: Exception) {
                Log.e("TAG", "Error getting presigned URL", e)
                showErrorSnackbar("Error: ${e.message}")

            }
        }
    }

    fun uploadFileToS3(presignedUrl: String, file: File) {
        val client = OkHttpClient()

        // Create a RequestBody with the file to upload
        val requestBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())

        // Create the PUT request to the pre-signed URL
        val request = Request.Builder()
            .url(presignedUrl)
            .addHeader("Content-Type", "application/json")
            .put(requestBody)
            .build()


        uploadCall = client.newCall(request)

        // Execute the request
        uploadCall?.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (call.isCanceled()) return // ✅ ignore cancel
                val responseBodyString = response.body?.string().orEmpty()
                this@MainActivity.runOnUiThread {
                    if (response.isSuccessful) {
                        Log.i(
                            "Upload", """
                                                            File uploaded successfully.
                                                            Code: ${response.code}
                                                            Message: ${response.message}
                                                            Headers: ${response.headers}
                                                            Body: $responseBodyString
                                                        """.trimIndent()
                        )
                        val signedUrl = response.request.url.toString()
                        val fullUrl = signedUrl

                        val cleanUrl = fullUrl.substringBefore("?")
                        sellerImage = cleanUrl
                        println("Signed URL: $cleanUrl")
//                        binding.progressBar.visibility = View.GONE
                        showSuccessSnackbar("Image uploaded successfully")
                    } else {
//                        binding.progressBar.visibility = View.GONE
//                        binding.btnCreateLead.isEnabled = true
                        Log.e("Upload", "File upload failed: ${response.message}")
                        Log.e("Upload", "File upload failed: ${response.code}")
                        Log.e("Upload", "File upload failed: ${response.body}")
                        showErrorSnackbar("Upload failed: ${response.code}")
                    }
                }

            }

            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) {
                    Log.d("Upload", "Upload cancelled by user")
                    return // ✅ VERY IMPORTANT
                }

                Log.e("Upload", "Failed to upload file", e)
            }
        })

    }

    private fun openImagePicker() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }



    private fun validateInputs(): Boolean {
        var isValid = true
        var firstErrorField: View? = null

        // Helper function to handle validation errors
        fun setError(view: View?, message: String) {
            isValid = false
            when (view) {
                is TextInputEditText -> {
                    view.error = message
                    // Request focus on the first error
                    if (firstErrorField == null) {
                        firstErrorField = view
                        view.requestFocus()
                    }
                }

                else -> {
                    if (firstErrorField == null) firstErrorField = view
                }
            }
        }

        // Validate Name of Shop
        if (etName.text.toString().trim().isEmpty()) {
            setError(etName, "Shop name is required")
        } else {
            etName.error = null
        }

        // Validate Contact Person
        if (etContactPerson.text.toString().trim().isEmpty()) {
            setError(etContactPerson, "Contact person is required")
        } else {
            etContactPerson.error = null
        }

        // Validate Mobile Number
        val mobileNumber = etPhone.text.toString()
        if (!validateMobileNumber(mobileNumber)) {
            setError(etPhone, "Please enter valid 10-digit mobile number")
        } else {
            etPhone.error = null
        }

        // Validate Email (required field)
        val email = etEmailId.text.toString().trim()
        if (email.isEmpty()) {
            setError(etEmailId, "Email ID is required")
        } else if (!validateEmail(email)) {
            setError(etEmailId, "Please enter valid email address (e.g., user@example.com)")
        } else {
            etEmailId.error = null
        }

        // Validate Area
        if (etArea.text.toString().trim().isEmpty()) {
            setError(etArea, "Area is required")
        } else {
            etArea.error = null
        }

        if (etSellerAddress.text.toString().trim().isEmpty()) {
            setError(etSellerAddress, "Seller address is required")
        } else {
            etSellerAddress.error = null
        }

        if (etExecutiveName.text.toString().trim().isEmpty()) {
            setError(etExecutiveName, "Executive name is required")
        } else {
            etExecutiveName.error = null
        }

        // Validate Selling Type
        val sellingType = getSelectedSellingType(rgSellingType)
        if (sellingType.isEmpty()) {
            setError(rgSellingType, "Please select selling type")
//            showErrorSnackbar("Please select selling type")
            Toast.makeText(this, "Please select selling type", Toast.LENGTH_SHORT).show()
        }



        // Validate Seller Image
//        if (sellerImage.isEmpty()) {
//            setError(etVisitingCardImage, "Please capture seller image")
////            showErrorSnackbar("Please capture seller image")
//            Toast.makeText(this, "Please capture seller image", Toast.LENGTH_SHORT).show()
//        }

        // Validate Onboarding Client Name
//        if (etOnboardingClientName.text.toString().trim().isEmpty()) {
//            setError(etOnboardingClientName, "Please enter onboarding client name")
//        } else {
//            etOnboardingClientName.error = null
//        }

        // Validate Paid Amount
//        if (etPaidAmount.text.toString().trim().isEmpty()) {
//            setError(etPaidAmount, "Please enter paid amount")
//        } else {
//            etPaidAmount.error = null
//        }



        // Show single error message for first validation error
        if (!isValid) {
            val errorMessage = when (firstErrorField) {
                etName -> "Shop name is required"
                etContactPerson -> "Contact person is required"
                etPhone -> "Please enter valid 10-digit mobile number"
                etEmailId -> if (email.isEmpty()) "Email ID is required" else "Please enter valid email address"
                etArea -> "Area is required"
//                etDemo -> "Please enter demo"
//                etRemark -> "Please enter remark"
//                etOnboardingClientName -> "Please enter onboarding client name"
//                etPaidAmount -> "Please enter paid amount"
//                etNotes -> "Please enter notes"
                else -> "Please fill all required fields"
            }

            // Show only one toast/snackbar instead of multiple
            showErrorSnackbar(errorMessage)

            // Request focus on the first error field if exists
            firstErrorField?.let {
                it.requestFocus()
                // For EditText fields, show keyboard
                if (it is TextInputEditText) {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }

        return isValid
    }

    private fun validateMobileNumber(mobileNumber: String): Boolean {
        // Remove all whitespace
        val cleaned = mobileNumber.trim().replace("\\s".toRegex(), "")

        // Check if it starts with +91
        if (cleaned.startsWith("+91")) {
            // After +91, there should be exactly 10 digits
            val digitsAfterCode = cleaned.substring(3)
            return digitsAfterCode.length == 10 && digitsAfterCode.all { it.isDigit() }
        }
        // Check if it starts with 91 (without +)
        else if (cleaned.startsWith("91") && cleaned.length == 12) {
            // 91 followed by 10 digits
            return cleaned.substring(2).all { it.isDigit() }
        }
        // Check if it's exactly 10 digits (local number)
        else if (cleaned.length == 10 && cleaned.all { it.isDigit() }) {
            return true
        }
        // Check if it's 12 digits starting with 91
        else if (cleaned.length == 12 && cleaned.startsWith("91") && cleaned.all { it.isDigit() }) {
            return true
        }

        return false
    }

    private fun validateEmail(email: String): Boolean {
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@(.+)$")
        return emailRegex.matches(email)
    }

    private fun getSelectedSellingType(radioGroup: RadioGroup): String {
        return when (radioGroup.checkedRadioButtonId) {
            R.id.rbA1 -> "A1"
            R.id.rbA2 -> "A2"
            R.id.rbA3 -> "A3"
            R.id.rbA4 -> "A4"
            else -> ""
        }
    }

    private fun showInfoSnackbar(message: String) {
        showSnackbar(message, Snackbar.LENGTH_SHORT, CreateLeadActivity.SnackbarType.INFO)
    }
    private fun setupInAppUpdates() {
        // Get the update card directly from the inflated layout (no need to inflate again)
        updateCard = binding.updateCardContainer.updateCard

        playUpdateManager = PlayUpdateManager(
            activity = this,
            lifecycleScope = lifecycleScope,
            updateCard = updateCard
        )

        // Start periodic update checks
        playUpdateManager.startPeriodicCheck()

        // Check for update immediately
        lifecycleScope.launch {
            playUpdateManager.checkForUpdate()
        }
    }


}

// Data class for marker tags
data class StopMarkerData(
    val type: String, // "START", "END", or "STOP"
    val time: String? = null,
    val stopPoint: StopPoint? = null
)