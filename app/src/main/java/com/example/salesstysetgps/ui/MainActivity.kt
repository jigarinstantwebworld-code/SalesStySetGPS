package com.example.salesstysetgps.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.salesstysetgps.R
import com.example.salesstysetgps.databinding.ActivityMainBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.EditText
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.Insets
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.salesstysetgps.data.LocationPoint
import com.example.salesstysetgps.data.StopPoint
import com.example.salesstysetgps.util.PdfUtils
import com.example.salesstysetgps.util.GpxUtils
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.getValue

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private var selectedMapType = GoogleMap.MAP_TYPE_NORMAL
    private lateinit var viewModel: TrackingViewModel
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
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

    private val prefs by lazy { getSharedPreferences("salesstysetgps_prefs", Context.MODE_PRIVATE) }
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

    companion object {
        private const val LOCATION_REQUEST_CODE = 1001
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }
            if (granted) {
                prepareMapAfterPermissions()
                Toast.makeText(this, "Permissions granted. Tap Start to begin.", Toast.LENGTH_SHORT).show()
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

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingPhotoUri?.let { uri -> currentStopDialogPhotoView?.setImageURI(uri) }
        } else {
            pendingPhotoUri = null
        }
    }

    @SuppressLint("PotentialBehaviorOverride")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Restore saved state
        savedInstanceState?.let {
            isStartMarkerSet = it.getBoolean("isStartMarkerSet", false)
            pendingStartLocation = it.getParcelable("pendingStartLocation")
            isWaitingForMap = it.getBoolean("isWaitingForMap", false)
        }

        // Check if we should center on a specific place (coming from PlacesActivity)
        intent?.let {
            val lat = it.getDoubleExtra("center_lat", Double.NaN)
            val lng = it.getDoubleExtra("center_lng", Double.NaN)
            if (!lat.isNaN() && !lng.isNaN()) {
                binding.btnExportPdf.visibility = View.GONE
                pendingCenterOnPlace = LatLng(lat, lng)
            }
            pendingStopId = it.getLongExtra("stop_id",0L)
        }

        // Apply insets to keep map UI within safe areas
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemBarInsets = sys
            val card = findViewById<View>(R.id.controlsCard)
            card?.setPadding(card.paddingLeft, card.paddingTop, card.paddingRight, sys.bottom + 16)
            applyMapPadding()
            insets
        }

        viewModel = ViewModelProvider(this)[TrackingViewModel::class.java]

        // Observe start location from ViewModel
        observeStartLocation()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Ask permissions on the very first launch only
        maybeRequestPermissionsOnFirstLaunch()

        setupClickListeners()
        setupObservers()
        updateButtons()
    }

    private fun setupClickListeners() {
        binding.btnStart.setOnClickListener {
            if (!hasAllPermissions()) {
                requestAllPermissions()
                return@setOnClickListener
            }



            dismissAllDialogs()

            // Check if GPS is enabled
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

//            if (!isGpsEnabled) {
//                showGpsRequiredDialog("GPS is turned off in settings")
//                return@setOnClickListener
//            }

            ensureLocationEnabledOrResolve(startOnResolution = true) {
                viewModel.startTracking()
                prepareMapAfterPermissions()
                updateButtons()
                startGpsMonitoring()
                startLocationUpdates()
            }

            if (!isGpsEnabled) {
                Toast.makeText(this, "Please enable GPS to start tracking", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnStop.setOnClickListener {
            // IMPORTANT: Stop monitoring FIRST
            stopGpsMonitoring()
            stopLocationUpdates()

            // Then stop tracking
            viewModel.stopTracking()
            updateButtons()

            // Dismiss any dialogs
            dismissGpsDialog()

            Toast.makeText(this, "Tracking stopped. All visits saved.", Toast.LENGTH_SHORT).show()
        }

        binding.btnReset.setOnClickListener { confirmReset() }
        binding.btnPlaces.setOnClickListener {
            startActivity(Intent(this, PlacesActivity::class.java))
        }
        binding.btnExportPdf.setOnClickListener { exportPdf() }
        binding.btnExportGpx.setOnClickListener { exportGpx() }

        binding.btnMapType.setOnClickListener {
            showMapTypeDialog()
        }
    }

    private fun showMapTypeDialog() {

        val mapTypes = arrayOf(
            "Roadmap",
            "Satellite",
            "Hybrid",
            "Terrain"
        )

        val mapTypeValues = arrayOf(
            GoogleMap.MAP_TYPE_NORMAL,
            GoogleMap.MAP_TYPE_SATELLITE,
            GoogleMap.MAP_TYPE_HYBRID,
            GoogleMap.MAP_TYPE_TERRAIN
        )

        val selectedIndex = mapTypeValues.indexOf(selectedMapType)

        AlertDialog.Builder(this)
            .setTitle("Choose Map Type")
            .setSingleChoiceItems(mapTypes, selectedIndex) { dialog, which ->

                selectedMapType = mapTypeValues[which]

                googleMap?.mapType = selectedMapType

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun setupObservers() {
        // Tracking state observer
        lifecycleScope.launch {
            viewModel.isTracking.collectLatest { running ->
                binding.tvStatus.setText(if (running) R.string.tracking_status_running else R.string.tracking_status_stopped)
                updateButtons()
            }
        }

        // Timer updates
        lifecycleScope.launch {
            while (true) {
                updateTimers()
                delay(1000)
            }
        }

        // Route points observer - draws polyline
        lifecycleScope.launch {
            viewModel.routePoints.collectLatest { points ->
                updatePolyline(points)
            }
        }

        // Stop points observer - adds red markers
        lifecycleScope.launch {
            viewModel.stopPoints.collectLatest { stops ->
                updateStopMarkers(stops)
            }
        }
    }

    private fun isGpsEnabled(): Boolean {
        return try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

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

    private fun updatePolyline(points: List<LocationPoint>) {
        val map = googleMap ?: return
        if (points.isNotEmpty()) {
            val latLngs = points.map { it.latLng }
            if (routePolyline == null) {
                routePolyline = map.addPolyline(
                    PolylineOptions()
                        .color(ContextCompat.getColor(this@MainActivity, R.color.polyline_color))
                        .width(10f)
                        .addAll(latLngs)
                )
            } else {
                routePolyline?.points = latLngs
            }
            if (latLngs.isNotEmpty() && viewModel.isTracking.value) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLngs.last(), 16f))
            }
        }
    }

//    private fun updateStopMarkers(stops: List<StopPoint>) {
//        val map = googleMap ?: return
//        clearMapStops()
//        stops.forEach { stop ->
//            val marker = map.addMarker(
//                MarkerOptions()
//                    .position(stop.center)
//                    .title(stop.name ?: "Stop")
//                    .snippet("Time: ${stop.timeSpentMinutes} min")
//                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
//            )
//            if (marker != null) stopMarkers.add(stop.id to marker)
//        }
//    }

    private fun updateStopMarkers(stops: List<StopPoint>) {
        val map = googleMap ?: return
        clearMapStops()

        // Get stops with sequence letters (only completed stops)
//        val completedStops = stops.filter { it.endTimeMillis != null }
//
//        val stopsWithLetters = completedStops
//            .sortedBy { it.startTimeMillis }
//            .mapIndexed { index, stop ->
//                val letter = ('A' + index).toString()
//                letter to stop
//            }

//        stopsWithLetters.forEachIndexed { index, (letter, stop) ->
//            val color = getColorForIndex(index)
//
//            Log.e("TAG", "LETTER NAME:  ${letter}", )
//            // Create marker with letter
//            val markerIcon = createMarkerWithLetter(letter, color)
//
//            val marker = map.addMarker(
//                MarkerOptions()
//                    .position(stop.center)
//                    .title("Stop $letter: ${stop.name ?: "Unnamed"}")
//                    .snippet("Duration: ${stop.timeSpentMinutes} min")
//                    .icon(markerIcon)
//            )
//
//            if (marker != null) {
//                stopMarkers.add(stop.id to marker)
//
//                // Store the letter in marker tag for later use
//                marker.tag = Pair(letter, stop.id)
//            }
//        }

//        val allStops = stops.sortedBy { it.startTimeMillis }
//        Log.d("MarkerDebug", "=== Updating ${allStops.size} Stops ===")

//        val completedStops = stops
//            .filter { it.endTimeMillis != null }
//            .sortedBy { it.startTimeMillis }

        val stopsWithLetters = viewModel.getStopsWithSequence()

        if (stopsWithLetters.isEmpty()) {
            Log.d("MarkerDebug", "No stops to display")
            return
        }

        stopsWithLetters.forEachIndexed { index, (letter, stop) ->

            val color = getColorForIndex(index)

            val startTimeStr =
                SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                    .format(Date(stop.startTimeMillis))

            Log.d(
                "MarkerDebug",
                "Stop ${stop.id} → Letter $letter (start=$startTimeStr)"
            )

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
                marker.tag = Pair(letter, stop.id)
            }
        }
    }

    private fun indexOf(stop: StopPoint, stopsWithLetters: List<Pair<String, StopPoint>>): Int {
        return stopsWithLetters.indexOfFirst { it.second.id == stop.id }
    }

    private fun getColorForIndex(index: Int): Int {
        return when (index % 6) {
            0 -> android.graphics.Color.parseColor("#E53935") // Red
            1 -> android.graphics.Color.parseColor("#1E88E5") // Blue
            2 -> android.graphics.Color.parseColor("#43A047") // Green
            3 -> android.graphics.Color.parseColor("#FB8C00") // Orange
            4 -> android.graphics.Color.parseColor("#8E24AA") // Purple
            5 -> android.graphics.Color.parseColor("#00ACC1") // Cyan
            else -> android.graphics.Color.parseColor("#757575") // Grey
        }
    }

    // Create marker with letter
    private fun createMarkerWithLetter(letter: String, color: Int): BitmapDescriptor {
        val bitmap = createLetterBitmap(letter, color)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

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

    private fun showPermissionRequiredDialog() {
        if (isLocationDialogShowing) return
        isLocationDialogShowing = true

        MaterialAlertDialogBuilder(this)
            .setTitle("📱 Permissions Required")
            .setMessage("Without location permission you cannot use this application.\n\nPlease grant permission to continue.")
            .setCancelable(false)
            .setPositiveButton("Open Settings") { d, _ ->
                isLocationDialogShowing = false
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                } catch (_: Exception) { }
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
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                } catch (_: Exception) { }
                d.dismiss()
            }
            .setNegativeButton("Exit App") { d, _ ->
                isLocationDialogShowing = false
                d.dismiss()
                finishAffinity()
            }
            .show()
    }

    private fun updateButtons() {
        val running = viewModel.isTracking.value
        binding.btnStart.visibility = if (running) View.GONE else View.VISIBLE
        binding.btnStop.visibility = if (running) View.VISIBLE else View.GONE
        binding.btnReset.visibility = View.VISIBLE
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
        pendingStartLocation = savedInstanceState.getParcelable("pendingStartLocation")
        isWaitingForMap = savedInstanceState.getBoolean("isWaitingForMap", false)
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

                // Remove start marker
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

    private fun clearMapStops() {
        stopMarkers.forEach { it.second.remove() }
        stopMarkers.clear()
    }

    private fun ensureLocationEnabledOrResolve(startOnResolution: Boolean, onEnabled: () -> Unit) {
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(request)
            .setAlwaysShow(true)
            .build()
        val client = LocationServices.getSettingsClient(this)
        client.checkLocationSettings(settingsRequest)
            .addOnSuccessListener { onEnabled() }
            .addOnFailureListener { ex ->
                if (ex is ResolvableApiException) {
                    try {
                        startAfterResolution = startOnResolution
                        isSystemDialogShowing = true
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

    private fun showEnableLocationDialog() {
        if (isLocationDialogShowing) return
        isLocationDialogShowing = true

        MaterialAlertDialogBuilder(this)
            .setTitle("📍 Location Required")
            .setMessage("Location services are turned off. This app needs location access to track your sales visits.\n\nWithout location, you cannot use the app.")
            .setCancelable(false)
            .setPositiveButton("Open Settings") { d, _ ->
                isLocationDialogShowing = false
                try {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (_: Exception) {
                    Toast.makeText(this, "Cannot open settings", Toast.LENGTH_SHORT).show()
                }
                d.dismiss()
            }
            .setNegativeButton("Exit App") { d, _ ->
                isLocationDialogShowing = false
                d.dismiss()
                finishAffinity()
            }
            .setOnDismissListener {
                isLocationDialogShowing = false
            }
            .show()
    }

    override fun onResume() {
        super.onResume()

        // Reset dialog flags when app resumes
        dismissAllDialogs()

        if (hasAllPermissions()) {
            prepareMapAfterPermissions()
            checkLocationSettings()
            if (viewModel.isTracking.value) {
                startGpsMonitoring()
                startLocationUpdates()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopGpsMonitoring()
        stopLocationUpdates()
        dismissLocationDialogs()
        dismissGpsDialog()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == LOCATION_REQUEST_CODE) {
            isLocationDialogShowing = false
            when (resultCode) {
                RESULT_OK -> {
                    Toast.makeText(this, "Location enabled. You can now start tracking.", Toast.LENGTH_SHORT).show()
                    prepareMapAfterPermissions()
                    checkLocationSettings()
                }
                RESULT_CANCELED -> {
                    showEnableLocationDialog()
                }
            }
        }
    }

    @SuppressLint("PotentialBehaviorOverride")
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
        map.isTrafficEnabled = false // Disable traffic to reduce clutter
        map.isBuildingsEnabled = true // Show 3D buildings
        map.isIndoorEnabled = true // Show indoor maps if available
        enableMyLocation()
        applyMapPadding()
        checkLocationSettings()

        // Handle pending start location if any
        if (pendingStartLocation != null && !isStartMarkerSet) {
            Log.d("StartMarker", "📌 Adding pending start marker from onMapReady")
            addStartMarkerToMap(pendingStartLocation!!)
            pendingStartLocation = null
            isWaitingForMap = false
        }

        pendingCenterOnPlace?.let { latLng ->
            val stop = viewModel.stopPoints.value.firstOrNull { it.id == pendingStopId }
            val marker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(stop?.name ?: "Seller Shop")
            )

            marker?.tag = stop

            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(latLng, 16f),
                object : GoogleMap.CancelableCallback {
                    override fun onFinish() {
                        map.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {

                            override fun getInfoWindow(marker: Marker): View? = null

                            override fun getInfoContents(marker: Marker): View {

                                val view = layoutInflater.inflate(R.layout.map_info_window, null)

                                val stop = marker.tag as? StopPoint

                                view.findViewById<TextView>(R.id.title).text = "Seller Name :${stop?.name ?: "Seller Shop"}"
                                view.findViewById<TextView>(R.id.location).text = "Location :${stop?.locationLabel}"
                                view.findViewById<TextView>(R.id.address).text = "Address :${stop?.address}"
                                view.findViewById<TextView>(R.id.phone).text = "📞 ${stop?.phone}"
                                view.findViewById<TextView>(R.id.time).text = "⏱ ${stop?.timeSpentMinutes} min"

                                return view
                            }
                        })
                        marker?.showInfoWindow()
                    }

                    override fun onCancel() {}
                }
            )
        }

        // Handle camera positioning ONLY if no selected place
        if (pendingCenterOnPlace == null) {
            val points = viewModel.routePoints.value

            if (points.isNotEmpty()) {
                zoomToFullRoute()
            } else if (pendingCenterOnMyLocation) {
                centerMapOnCurrentLocationIfPossible()
            }
        }

        setupMarkerClickListener(map)
    }

    private fun checkLocationSettings() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build()
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

    private fun showLocationRequiredDialog(exception: ResolvableApiException) {
        if (userDeclinedLocation) {
            showEnableLocationDialog() // Show your custom dialog directly
            return
        }
        if (isLocationDialogShowing) return

        isLocationDialogShowing = true
        isSystemDialogShowing = true


        try {
//            exception.startResolutionForResult(this, LOCATION_REQUEST_CODE)
            val intent = IntentSenderRequest.Builder(exception.resolution).build()
            resolutionLauncher.launch(intent)
        } catch (e: Exception) {
            isSystemDialogShowing = false
            isLocationDialogShowing = false
            showEnableLocationDialog()
        }
    }

    // ================ GPS MONITORING METHODS ================

    private fun startGpsMonitoring() {
        if (!viewModel.isTracking.value) {
//            Log.d("GpsDebug", "⏸️ Not starting GPS monitoring - tracking is off")
            return
        }

        stopGpsMonitoring()
        hasLocationFix = false
        hasShownNoFixDialog = false
        isWaitingForFirstFix = true
        lastLocationTime = System.currentTimeMillis()

        Log.d("GpsDebug", "▶️ Starting GPS monitoring")


        lifecycleScope.launch {
            delay(10000) // Wait 10 seconds
            if (!hasLocationFix && viewModel.isTracking.value && isWaitingForFirstFix) {
                // Still no fix after 10 seconds - show dialog
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


    private fun checkGpsAndLocationStatus() {
        // Only check if tracking is active
        if (!viewModel.isTracking.value) {
            dismissAllDialogs()
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val currentTime = System.currentTimeMillis()
        val timeSinceStart = currentTime - lastLocationTime

//        Log.d("GpsDebug", "📡 GPS Status Check:")
//        Log.d("GpsDebug", "   GPS Enabled: $isGpsEnabled")
//        Log.d("GpsDebug", "   Has Location Fix: $hasLocationFix")
//        Log.d("GpsDebug", "   Time since start: ${timeSinceStart / 1000}s")
//        Log.d("GpsDebug", "   Tracking: ${viewModel.isTracking.value}")
//        Log.d("GpsDebug", "   Dialog Showing: $isGpsDialogShowing")
//        Log.d("GpsDebug", "   Has Shown Dialog: $hasShownGpsDialog")

        when {
            // =========================================================
            // CASE 1: GPS IS DISABLED - Show dialog immediately
            // =========================================================
            !isGpsEnabled -> {
//                if (!isGpsDialogShowing) {
                    showGpsRequiredDialog("GPS is turned off in settings")
//                }
            }

            // =========================================================
            // CASE 2: GPS IS ENABLED AND WE HAVE FIX - All good
            // =========================================================
            hasLocationFix -> {
                // We have location - dismiss any dialogs and reset flags
                if (isGpsDialogShowing) {
                    dismissAllDialogs()
                }
                hasShownGpsDialog = false
                hasShownGpsFailureDialog = false
            }

            // =========================================================
            // CASE 3: GPS IS ENABLED BUT NO FIX YET - SILENT WAITING
            // =========================================================
            else -> {
                // GPS is enabled, just waiting for fix
                // DO NOT SHOW ANY DIALOG HERE - This was your problem!

                // Just log the waiting status
//                Log.d("GpsDebug", "⏳ GPS enabled, waiting for fix... ${timeSinceStart / 1000}s elapsed")

                // Optional: Show a non-intrusive snackbar after 15 seconds
                if (timeSinceStart > 15000 && !hasShownGpsDialog) {
                    showGpsWaitingSnackbar()
                    hasShownGpsDialog = true
                }

                // NO DIALOG IS SHOWN HERE
            }
        }
    }

    private fun showGpsWaitingSnackbar() {
        Snackbar.make(
            binding.root,
            "📡 Acquiring GPS signal...",
            Snackbar.LENGTH_INDEFINITE
        ).setAction("OK") {
            // Just dismiss
        }.show()
    }


    private fun showNoFixDialog() {
        // Don't show if we already have fix or tracking stopped
        if (hasLocationFix || !viewModel.isTracking.value || hasShownNoFixDialog) return

        hasShownNoFixDialog = true
//        Log.d("GpsDebug", "📢 Showing no GPS fix dialog")

        MaterialAlertDialogBuilder(this)
            .setTitle("📡 Waiting for GPS")
            .setMessage("Still waiting for GPS signal.\n\nMake sure you're in an open area.")
            .setCancelable(false)
            .setPositiveButton("OK") { d, _ ->
                hasShownNoFixDialog = false
                d.dismiss()
            }
            .setNegativeButton("Stop Tracking") { d, _ ->
                hasShownNoFixDialog = false
                viewModel.stopTracking()
                updateButtons()
                stopGpsMonitoring()
                d.dismiss()
            }
            .show()
    }

    private fun showGpsSearchingDialog(secondsLeft: Long) {
        // Don't show if already showing a dialog
        if (isGpsDialogShowing) return

        isGpsDialogShowing = true
        Log.d("GpsDebug", "📢 Showing GPS searching dialog")

        MaterialAlertDialogBuilder(this)
            .setTitle("📡 Acquiring GPS")
            .setMessage("Waiting for GPS fix...\n\nThis may take ${secondsLeft} seconds.\n\nPlease move to an open area if this takes too long.")
            .setCancelable(false)
            .setPositiveButton("OK") { d, _ ->
                isGpsDialogShowing = false
                d.dismiss()
            }
            .setOnDismissListener {
                isGpsDialogShowing = false
            }
            .show()
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



    private fun dismissAllDialogs() {
        isGpsDialogShowing = false
        hasShownGpsDialog = false
        hasShownGpsFailureDialog = false
        hasShownNoFixDialog = false
        isLocationDialogShowing = false
//        Log.d("GpsDebug", "🗑️ All dialogs dismissed and flags reset")
    }

    private fun showGpsRequiredDialog(message: String) {
        // Don't show if tracking is off
        if (!viewModel.isTracking.value) {
//            Log.d("GpsDebug", "📢 Not showing GPS dialog - tracking is off")
            return
        }

        // Don't show if already showing
        if (isGpsDialogShowing) {
//            Log.d("GpsDebug", "📢 Dialog flag was true, resetting it")
            isGpsDialogShowing = false
        }

//        isGpsDialogShowing = true
//        Log.d("GpsDebug", "📢 Showing GPS required dialog: $message")

        Handler(Looper.getMainLooper()).postDelayed({
            if (!viewModel.isTracking.value) return@postDelayed

            isGpsDialogShowing = true
//            Log.d("GpsDebug", "📢 Showing GPS required dialog: $message")

            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("📡 GPS Disabled")
                .setMessage(message + "\n\nTracking will pause until GPS is enabled.")
                .setCancelable(false)
                .setPositiveButton("Open Settings") { d, _ ->
                    isGpsDialogShowing = false
                    hasShownGpsDialog = false
                    try {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "Cannot open settings", Toast.LENGTH_SHORT).show()
                    }
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
                .setOnDismissListener {
                    hasShownGpsDialog = false
                    isGpsDialogShowing = false
//                    Log.d("GpsDebug", "📢 GPS dialog dismissed")
                }
                .show()
        }, 100) // 100ms delay
    }


    private fun dismissLocationDialogs() {
        isLocationDialogShowing = false
    }

    private fun dismissGpsDialog() {
        isGpsDialogShowing = false
        hasShownGpsDialog = false

    }

    // ================ LOCATION UPDATES METHODS ================

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                // Location received - GPS is working
                hasLocationFix = true
                lastLocationTime = System.currentTimeMillis()
                dismissAllDialogs()
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                if (!locationAvailability.isLocationAvailable) {
                    // Location temporarily unavailable
                    Log.d("GpsDebug", "⚠️ Location temporarily unavailable")
                    hasLocationFix = false
                } else {
                    Log.d("GpsDebug", "✅ Location available")
                }
            }
        }

        if (hasLocationPermission()) {
            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback!!,
                    Looper.getMainLooper()
                )
            } catch (e: Exception) {
                Log.e("LocationDebug", "Error requesting location updates", e)
            }
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
        hasLocationFix = false
    }

    // ================ MAP & MARKER METHODS ================

    private fun setupMarkerClickListener(map: GoogleMap) {
        map.setOnMarkerClickListener { marker ->
            val entry = stopMarkers.firstOrNull { it.second == marker } ?: return@setOnMarkerClickListener false
            val stop = viewModel.stopPoints.value.firstOrNull { it.id == entry.first } ?: return@setOnMarkerClickListener false

            pendingPhotoUri = null
            currentStopDialogPhotoView = null
            showStopDetailsDialog(stop, marker)
            true
        }
    }

//    private fun showStopDetailsDialog(stop: StopPoint, marker: Marker) {
//        val form = layoutInflater.inflate(R.layout.dialog_stop_form, null)
//        val etName = form.findViewById<EditText>(R.id.etName)
//        val tvLocation = form.findViewById<TextView>(R.id.tvLocation)
//        val etAddress = form.findViewById<EditText>(R.id.etAddress)
//        val etPhone = form.findViewById<EditText>(R.id.etPhone)
//        val tvDuration = form.findViewById<TextView>(R.id.tvDuration)
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
//            addView(form)
//            addView(MaterialButton(context).apply {
//                text = "Capture seller image"
//                setOnClickListener {
//                    val photoFile = File.createTempFile("seller_", ".jpg", cacheDir)
//                    val uri = FileProvider.getUriForFile(this@MainActivity, "${packageName}.fileprovider", photoFile)
//                    pendingPhotoUri = uri
//                    takePictureLauncher.launch(uri)
//                }
//            })
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
//                marker.title = name
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

    private fun showStopDetailsDialog(stop: StopPoint, marker: Marker) {
        val form = layoutInflater.inflate(R.layout.dialog_stop_form, null)
        val etName = form.findViewById<EditText>(R.id.etName)
        val tvLocation = form.findViewById<TextView>(R.id.tvLocation)
        val etAddress = form.findViewById<EditText>(R.id.etAddress)
        val etPhone = form.findViewById<EditText>(R.id.etPhone)
        val tvDuration = form.findViewById<TextView>(R.id.tvDuration)

        // Get stop letter
        val stopLetter = viewModel.getStopLetter(stop.id) ?: "?"
        val color = getColorForIndex((stopLetter[0] - 'A'))

        // Create letter bitmap directly (not from marker)
        val letterBitmap = createLetterBitmap(stopLetter, color)

        val letterImageView = ImageView(this).apply {
            setImageBitmap(letterBitmap)  // Now using bitmap directly
            layoutParams = ViewGroup.LayoutParams(100, 100)
            setPadding(0, 8, 0, 8)
        }

        val imgView = ImageView(this).apply {
            adjustViewBounds = true
            maxHeight = 400
        }

        currentStopDialogPhotoView = imgView
        etName.setText(stop.name ?: "")

        val locationText = if (!stop.locationLabel.isNullOrBlank()) {
            stop.locationLabel
        } else {
            String.format("Lat: %.5f  Lng: %.5f", stop.center.latitude, stop.center.longitude)
        }

        tvLocation.text = "Location: $locationText"
        tvDuration.text = "Visit Duration: ${stop.timeSpentMinutes} min"
        etAddress.setText(stop.address ?: "")
        etPhone.setText(stop.phone ?: "")
        stop.imageUri?.let { imgView.setImageURI(Uri.parse(it)) }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            // Add letter badge at the top with name
            val headerLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(16, 16, 16, 16)

                addView(letterImageView)

                val textLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 0, 0, 0)

                    val letterText = TextView(context).apply {
                        text = "Stop $stopLetter"
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

            // Add a divider
            val divider = View(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1
                )
                setBackgroundColor(android.graphics.Color.LTGRAY)
            }
            addView(divider)

            addView(form)

            // Capture button
            addView(MaterialButton(context).apply {
                text = "Capture seller image"
                setOnClickListener {
                    try {
                        val photoFile = File.createTempFile("seller_", ".jpg", cacheDir)
                        val uri = FileProvider.getUriForFile(
                            this@MainActivity,
                            "${packageName}.fileprovider",
                            photoFile
                        )
                        pendingPhotoUri = uri
                        takePictureLauncher.launch(uri)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            })

            addView(imgView)
        }

        val titleView = TextView(this).apply {
            text = "Seller Shop Details"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(40, 30, 40, 10)
        }

        MaterialAlertDialogBuilder(this)
            .setCustomTitle(titleView)
            .setView(container)
            .setPositiveButton("Save") { d, _ ->
                val name = etName.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Seller name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val imageUri = pendingPhotoUri?.toString() ?: stop.imageUri
                viewModel.updateStopDetails(
                    stop.id,
                    name,
                    etAddress.text?.toString()?.trim().orEmpty().ifEmpty { null },
                    etPhone.text?.toString()?.trim().orEmpty().ifEmpty { null },
                    imageUri
                )
                marker.title = "Stop $stopLetter: $name"
                marker.showInfoWindow()
                clearPendingPhoto()
                d.dismiss()
            }
            .setNegativeButton("Close") { d, _ ->
                clearPendingPhoto()
                d.dismiss()
            }
            .setOnDismissListener { clearPendingPhoto() }
            .show()
            .window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
    }

    // Add this function to MainActivity
    private fun createLetterBitmap(letter: String, colors: Int): Bitmap {
        val size = 80
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw circle
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = colors
        }
        canvas.drawCircle(size/2f, size/2f, size/2f - 4, circlePaint)

        // Draw border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = android.graphics.Color.WHITE
            strokeWidth = 4f
        }
        canvas.drawCircle(size/2f, size/2f, size/2f - 4, borderPaint)

        // Draw letter
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val xPos = size / 2f
        val yPos = size / 2f - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(letter, xPos, yPos, textPaint)

        return bitmap
    }

    private fun clearPendingPhoto() {
        currentStopDialogPhotoView = null
        pendingPhotoUri = null
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

    private fun centerMapOnCurrentLocationIfPossible() {
        val map = googleMap ?: return
        if (!hasLocationPermission()) return

        fusedLocationClient.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    pendingCenterOnMyLocation = false
                    val me = LatLng(loc.latitude, loc.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 16f))
                } else {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { cur ->
                            if (cur != null) {
                                pendingCenterOnMyLocation = false
                                val me = LatLng(cur.latitude, cur.longitude)
                                map.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 16f))
                            }
                        }
                }
            }
    }

    private fun enableMyLocation() {
        val map = googleMap ?: return
        if (hasLocationPermission()) {
            map.isMyLocationEnabled = true
            Log.d("LocationDebug", "✅ Blue dot enabled")
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAllPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val notif = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
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

    private fun applyMapPadding() {
        val m = googleMap ?: return
        val ins = systemBarInsets
        if (ins != null) {
            m.setPadding(16, ins.top + 24, 24, ins.bottom + 24)
        } else {
            m.setPadding(16, 24, 24, 24)
        }
    }

    // ================ START MARKER METHODS ================

    private fun observeStartLocation() {
        if (isObservingStartLocation) return
        isObservingStartLocation = true

        lifecycleScope.launch {
            viewModel.startLocation.collectLatest { startLatLng ->
                if (startLatLng != null) {
                    Log.d("StartMarker", "🎯 Start location received: $startLatLng")
                    handleStartLocation(startLatLng)
                } else {
                    Log.d("StartMarker", "🗑️ Start location cleared")
                    removeStartMarker()
                }
            }
        }
    }

    private fun handleStartLocation(latLng: LatLng) {
        // If map is ready, add marker immediately
        if (googleMap != null) {
            Log.d("StartMarker", "🗺️ Map ready, adding marker now")
            addStartMarkerToMap(latLng)
        } else {
            // Store for when map is ready
            Log.d("StartMarker", "⚠️ Map not ready, storing for later")
            pendingStartLocation = latLng
            isWaitingForMap = true
        }
    }

    private fun addStartMarkerToMap(latLng: LatLng) {
        val map = googleMap ?: run {
            Log.e("StartMarker", "❌ Cannot add marker - map is null")
            return
        }

        // Check if same marker already exists
        if (isStartMarkerSet && startMarker != null) {
            val currentPos = startMarker?.position
            if (currentPos != null &&
                currentPos.latitude == latLng.latitude &&
                currentPos.longitude == latLng.longitude) {
                Log.d("StartMarker", "⏭️ Same start point already exists, keeping it")
                return
            }
        }

        // Remove existing marker if any
        if (isStartMarkerSet) {
            Log.d("StartMarker", "🔄 Removing existing start marker")
            startMarker?.remove()
            startMarker = null
        }

        val markerIcon = try {
            vectorToBitmap(R.drawable.ic_start_arrow)
        } catch (e: Exception) {
            Log.e("StartMarker", "Failed to convert vector: ${e.message}")
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
        }

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
            Log.d("StartMarker", "✅ Start marker added successfully at: $latLng")
            startMarker?.showInfoWindow()

            // Only animate camera if not tracking or no route points
            if (!viewModel.isTracking.value || viewModel.routePoints.value.isEmpty()) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
            }
        } else {
            Log.e("StartMarker", "❌ Failed to add start marker")
        }
    }

    private fun removeStartMarker() {
        startMarker?.remove()
        startMarker = null
        isStartMarkerSet = false
        pendingStartLocation = null
        isWaitingForMap = false
        Log.d("StartMarker", "🗑️ Start marker removed")
    }

    private fun vectorToBitmap(@DrawableRes vectorResId: Int): BitmapDescriptor {
        return try {
            val vectorDrawable = ContextCompat.getDrawable(this, vectorResId)
            if (vectorDrawable == null) {
                Log.e("StartMarker", "Vector drawable not found")
                return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
            }

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
            Log.e("StartMarker", "Error converting vector: ${e.message}")
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
        }
    }

    // ================ EXPORT METHODS ================

    private fun exportPdf() {
        if (Build.VERSION.SDK_INT < 29) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pendingExportAction = { exportPdfInternal() }
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        exportPdfInternal()
    }

//    private fun exportPdfInternal() {
//        val places = viewModel.getCompletedStopsMergedByName()
//        if (places.isEmpty()) {
//            Toast.makeText(this, "No completed stops to export", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        places.forEachIndexed { index, stop ->
//            Log.d("PDFExport", "Stop $index: ${stop.name}, Minutes: ${stop.timeSpentMinutes}")
//        }
//
//        val input = EditText(this)
//        input.hint = "Seller name"
//        input.setText(places.firstOrNull { !it.name.isNullOrBlank() }?.name ?: "")
//
//        MaterialAlertDialogBuilder(this)
//            .setTitle("Export PDF")
//            .setMessage("Enter seller name for the report header")
//            .setView(input)
//            .setPositiveButton("Export") { d, _ ->
//                val seller = input.text?.toString()?.trim()?.ifEmpty { "Seller" }
//
//                captureRouteScreenshot { bitmap ->
//                    var screenshotPath: String? = null
//
//                    if (bitmap != null) {
//                        screenshotPath = saveBitmapToFile(bitmap)
//                        Log.d("PDFExport", "Map screenshot saved to: $screenshotPath")
//                    }
//
//                    val rows = places.sortedBy { it.startTimeMillis }.map {
//                        PdfUtils.StopRow(
//                            name = it.name,
//                            lat = it.center.latitude,
//                            lng = it.center.longitude,
//                            startMillis = it.startTimeMillis,
//                            endMillis = it.endTimeMillis,
//                            durationMinutes = it.timeSpentMinutes,
//                            locationLabel = it.locationLabel,
//                            address = it.address,
//                            phone = it.phone,
//                            imageUri = it.imageUri,
//                            routeMapUri = screenshotPath
//                        )
//                    }
//
//                    val pathOrName = seller?.let { PdfUtils.generateSellerReport(this, it, rows) }
//                    Toast.makeText(this, "PDF saved: $pathOrName", Toast.LENGTH_LONG).show()
//
//                    // Clean up temp file
//                    screenshotPath?.let { path ->
//                        try { File(path).delete() } catch (e: Exception) { }
//                    }
//                }
//                d.dismiss()
//            }
//            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
//            .show()
//    }

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
                        // Find the letter for this stop
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
                        try { File(path).delete() } catch (e: Exception) { }
                    }
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }

    private fun exportGpx() {
        if (Build.VERSION.SDK_INT < 29) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pendingExportAction = { exportGpxInternal() }
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        exportGpxInternal()
    }

    private fun exportGpxInternal() {
        lifecycleScope.launch {
            val stops = viewModel.getCompletedStops().sortedBy { it.startTimeMillis }
            if (stops.isEmpty()) {
                Toast.makeText(this@MainActivity, "No route to export because you don't have any stop point", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val start = stops.first().startTimeMillis
            val end = stops.last().endTimeMillis ?: stops.last().startTimeMillis

            val routePoints = viewModel.getRouteBetween(start, end)
            if (routePoints.isEmpty()) {
                Toast.makeText(this@MainActivity, "No route points in DB", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val nameOrPath = GpxUtils.exportRouteGpx(this@MainActivity, routePoints)
            Toast.makeText(this@MainActivity, "GPX saved: $nameOrPath", Toast.LENGTH_LONG).show()
        }
    }

//    private fun captureRouteScreenshot(callback: (Bitmap?) -> Unit) {
//        val map = googleMap ?: run {
//            callback(null)
//            return
//        }
//
//        val routePoints = viewModel.routePoints.value
//        val stopPoints = viewModel.stopPoints.value
//
//        if (routePoints.isEmpty() && stopPoints.isEmpty()) {
//            map.snapshot { callback(it) }
//            return
//        }
//
//        Toast.makeText(this, "Capturing route map...", Toast.LENGTH_SHORT).show()
//
//        val builder = LatLngBounds.Builder()
//        routePoints.forEach { builder.include(it.latLng) }
//        stopPoints.forEach { builder.include(it.center) }
//        val bounds = builder.build()
//
//        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100), object : GoogleMap.CancelableCallback {
//            override fun onFinish() {
//                Handler(Looper.getMainLooper()).postDelayed({
//                    map.snapshot { callback(it) }
//                }, 800)
//            }
//            override fun onCancel() {
//                map.snapshot { callback(it) }
//            }
//        })
//    }


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

        // Add extra padding to show surrounding area (for labels)
        val padding = 150 // Increased padding to show more context

        // Animate camera with callback
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, padding),
            object : GoogleMap.CancelableCallback {
                override fun onFinish() {
                    // Wait longer for map tiles and labels to load
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Take snapshot with maximum detail
                        map.snapshot { bitmap ->
                            if (bitmap != null) {
                                // Enhance bitmap if needed
                                val enhancedBitmap = enhanceMapSnapshot(bitmap)
                                callback(enhancedBitmap)
                            } else {
                                callback(null)
                            }
                        }
                    }, 2000) // 2 second delay for labels to render
                }

                override fun onCancel() {
                    map.snapshot { callback(it) }
                }
            }
        )
    }

    private fun enhanceMapSnapshot(original: Bitmap): Bitmap {
        try {
            // Create a copy that can be modified
            val enhanced = original.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(enhanced)

            // Slightly increase contrast for better label readability
            val paint = Paint()
            paint.colorFilter = ColorMatrixColorFilter(
                ColorMatrix().apply {
                    setSaturation(1.1f) // Slightly increase saturation
                    setScale(1.1f, 1.1f, 1.1f, 1.0f) // Increase contrast
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
}



//class MainActivity : AppCompatActivity(), OnMapReadyCallback {
//
//    private lateinit var binding: ActivityMainBinding
//    private lateinit var viewModel: TrackingViewModel
//    private var googleMap: GoogleMap? = null
//    private lateinit var fusedLocationClient: FusedLocationProviderClient
//    private var routePolyline: Polyline? = null
//    private val stopMarkers = mutableListOf<Pair<Long, Marker>>()
//    private var startAfterResolution: Boolean = false
//    private var systemBarInsets: Insets? = null
//    private var pendingPhotoUri: Uri? = null
//    private var currentStopDialogPhotoView: ImageView? = null
//    private var pendingCenterOnMyLocation: Boolean = false
//    private var pendingCenterOnPlace: LatLng? = null
//
//    // Start marker related properties
//    private var startMarker: Marker? = null
//    private var isObservingStartLocation = false
//    private var isStartMarkerSet = false
//    private var pendingStartLocation: LatLng? = null
//    private var isWaitingForMap = false  // NEW: Track if we're waiting for map
//
//    private val prefs by lazy { getSharedPreferences("salesstysetgps_prefs", Context.MODE_PRIVATE) }
//    private val prefAskedPermissionsKey = "asked_permissions_once"
//    private var pendingExportAction: (() -> Unit)? = null
//
//    private val requestPermissionsLauncher =
//        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
//            val granted = result.values.all { it }
//            if (granted) {
//                prepareMapAfterPermissions()
//                Toast.makeText(this, "Permissions granted. Tap Start to begin.", Toast.LENGTH_SHORT).show()
//            } else {
//                showPermissionRequiredDialog()
//            }
//        }
//
//    private val writePermissionLauncher =
//        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
//            if (granted) {
//                pendingExportAction?.invoke()
//            } else {
//                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//    private val resolutionLauncher =
//        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
//            if (result.resultCode != RESULT_OK) return@registerForActivityResult
//
//            if (startAfterResolution) {
//                startAfterResolution = false
//                viewModel.startTracking()
//                prepareMapAfterPermissions()
//                updateButtons()
//            } else {
//                prepareMapAfterPermissions()
//            }
//        }
//
//    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
//        if (success) {
//            pendingPhotoUri?.let { uri -> currentStopDialogPhotoView?.setImageURI(uri) }
//        } else {
//            pendingPhotoUri = null
//        }
//    }
//
//    @SuppressLint("PotentialBehaviorOverride")
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
//
//        // Restore saved state
//        savedInstanceState?.let {
//            isStartMarkerSet = it.getBoolean("isStartMarkerSet", false)
//            pendingStartLocation = it.getParcelable("pendingStartLocation")
//            isWaitingForMap = it.getBoolean("isWaitingForMap", false)
//        }
//
//        // Check if we should center on a specific place (coming from PlacesActivity)
//        intent?.let {
//            val lat = it.getDoubleExtra("center_lat", Double.NaN)
//            val lng = it.getDoubleExtra("center_lng", Double.NaN)
//            if (!lat.isNaN() && !lng.isNaN()) {
//                pendingCenterOnPlace = LatLng(lat, lng)
//            }
//        }
//
//        // Apply insets to keep map UI within safe areas
//        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
//            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            systemBarInsets = sys
//            val card = findViewById<View>(R.id.controlsCard)
//            card?.setPadding(card.paddingLeft, card.paddingTop, card.paddingRight, sys.bottom + 16)
//            applyMapPadding()
//            insets
//        }
//
//        viewModel = ViewModelProvider(this)[TrackingViewModel::class.java]
//
//        // Observe start location from ViewModel
//        observeStartLocation()
//
//        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
//        mapFragment.getMapAsync(this)
//
//        // Ask permissions on the very first launch only
//        maybeRequestPermissionsOnFirstLaunch()
//
//        setupClickListeners()
//        setupObservers()
//        updateButtons()
//    }
//
//    private fun setupClickListeners() {
//        binding.btnStart.setOnClickListener {
//            if (hasAllPermissions()) {
//                ensureLocationEnabledOrResolve(startOnResolution = true) {
//                    viewModel.startTracking()
//                    prepareMapAfterPermissions()
//                    updateButtons()
//                }
//            } else {
//                requestAllPermissions()
//            }
//        }
//
//        binding.btnStop.setOnClickListener {
//            viewModel.stopTracking()
//            updateButtons()
//        }
//
//        binding.btnReset.setOnClickListener { confirmReset() }
//        binding.btnPlaces.setOnClickListener {
//            startActivity(Intent(this, PlacesActivity::class.java))
//        }
//        binding.btnExportPdf.setOnClickListener { exportPdf() }
//        binding.btnExportGpx.setOnClickListener { exportGpx() }
//    }
//
//    private fun setupObservers() {
//        // Tracking state observer
//        lifecycleScope.launch {
//            viewModel.isTracking.collectLatest { running ->
//                binding.tvStatus.setText(if (running) R.string.tracking_status_running else R.string.tracking_status_stopped)
//                updateButtons()
//            }
//        }
//
//        // Timer updates
//        lifecycleScope.launch {
//            while (true) {
//                updateTimers()
//                delay(1000)
//            }
//        }
//
//        // Route points observer - draws polyline
//        lifecycleScope.launch {
//            viewModel.routePoints.collectLatest { points ->
//                updatePolyline(points)
//            }
//        }
//
//        // Stop points observer - adds red markers
//        lifecycleScope.launch {
//            viewModel.stopPoints.collectLatest { stops ->
//                updateStopMarkers(stops)
//            }
//        }
//    }
//
//    private suspend fun updateTimers() {
//        // Current stop timer
//        val currentStopTime = viewModel.getCurrentStopTime()
//        if (currentStopTime > 0) {
//            val seconds = (System.currentTimeMillis() / 1000) % 60
//            binding.tvTimer.text = "Timer: ${currentStopTime}:${String.format("%02d", seconds)}"
//            binding.tvTimer.visibility = View.VISIBLE
//        } else {
//            binding.tvTimer.visibility = View.GONE
//        }
//
//        // Session timer
//        val sessionSeconds = viewModel.getSessionElapsedSeconds()
//        if (sessionSeconds > 0) {
//            val mins = sessionSeconds / 60
//            val secs = sessionSeconds % 60
//            binding.tvStatus.text = getString(
//                if (viewModel.isTracking.value) R.string.tracking_status_running else R.string.tracking_status_stopped
//            ) + "  •  Session: ${mins}:${String.format("%02d", secs)}"
//        }
//    }
//
//    private fun updatePolyline(points: List<LocationPoint>) {
//        val map = googleMap ?: return
//        if (points.isNotEmpty()) {
//            val latLngs = points.map { it.latLng }
//            if (routePolyline == null) {
//                routePolyline = map.addPolyline(
//                    PolylineOptions()
//                        .color(ContextCompat.getColor(this@MainActivity, R.color.polyline_color))
//                        .width(10f)
//                        .addAll(latLngs)
//                )
//            } else {
//                routePolyline?.points = latLngs
//            }
//            if (latLngs.isNotEmpty() && viewModel.isTracking.value) {
//                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLngs.last(), 16f))
//            }
//        }
//    }
//
//    private fun updateStopMarkers(stops: List<StopPoint>) {
//        val map = googleMap ?: return
//        clearMapStops()
//        stops.forEach { stop ->
//            val marker = map.addMarker(
//                MarkerOptions()
//                    .position(stop.center)
//                    .title(stop.name ?: "Stop")
//                    .snippet("Time: ${stop.timeSpentMinutes} min")
//                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
//            )
//            if (marker != null) stopMarkers.add(stop.id to marker)
//        }
//    }
//
//    private fun maybeRequestPermissionsOnFirstLaunch() {
//        val asked = prefs.getBoolean(prefAskedPermissionsKey, false)
//        if (!asked) {
//            prefs.edit().putBoolean(prefAskedPermissionsKey, true).apply()
//            if (!hasAllPermissions()) {
//                requestAllPermissions()
//            } else {
//                prepareMapAfterPermissions()
//            }
//        } else {
//            if (!hasAllPermissions()) showPermissionRequiredDialog() else prepareMapAfterPermissions()
//        }
//    }
//
//    private fun prepareMapAfterPermissions() {
//        pendingCenterOnMyLocation = true
//        enableMyLocation()
//        applyMapPadding()
//        centerMapOnCurrentLocationIfPossible()
//    }
//
//    private fun showPermissionRequiredDialog() {
//        MaterialAlertDialogBuilder(this)
//            .setTitle("Permissions required")
//            .setMessage("Without location permission you can't use this application.\n\nPlease grant permission to continue.")
//            .setCancelable(false)
//            .setPositiveButton("Open Settings") { d, _ ->
//                try {
//                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
//                        data = Uri.fromParts("package", packageName, null)
//                    }
//                    startActivity(intent)
//                } catch (_: Exception) { }
//                d.dismiss()
//            }
//            .setNegativeButton("Exit") { d, _ ->
//                d.dismiss()
//                finishAffinity()
//            }
//            .show()
//    }
//
//    private fun updateButtons() {
//        val running = viewModel.isTracking.value
//        binding.btnStart.visibility = if (running) View.GONE else View.VISIBLE
//        binding.btnStop.visibility = if (running) View.VISIBLE else View.GONE
//        binding.btnReset.visibility = View.VISIBLE
//    }
//
//    override fun onSaveInstanceState(outState: Bundle) {
//        super.onSaveInstanceState(outState)
//        outState.putBoolean("isStartMarkerSet", isStartMarkerSet)
//        outState.putParcelable("pendingStartLocation", pendingStartLocation)
//        outState.putBoolean("isWaitingForMap", isWaitingForMap)
//    }
//
//    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
//        super.onRestoreInstanceState(savedInstanceState)
//        isStartMarkerSet = savedInstanceState.getBoolean("isStartMarkerSet", false)
//        pendingStartLocation = savedInstanceState.getParcelable("pendingStartLocation")
//        isWaitingForMap = savedInstanceState.getBoolean("isWaitingForMap", false)
//    }
//
//    private fun confirmReset() {
//        MaterialAlertDialogBuilder(this)
//            .setTitle("Reset all routes?")
//            .setMessage("This will clear the current polyline, stops, and timers.")
//            .setPositiveButton("Reset") { d, _ ->
//                viewModel.resetSession()
//                clearMapStops()
//                routePolyline?.remove()
//                routePolyline = null
//
//                // Remove start marker
//                startMarker?.remove()
//                startMarker = null
//                isStartMarkerSet = false
//                pendingStartLocation = null
//                isWaitingForMap = false
//
//                updateButtons()
//                d.dismiss()
//            }
//            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
//            .show()
//    }
//
//    private fun clearMapStops() {
//        stopMarkers.forEach { it.second.remove() }
//        stopMarkers.clear()
//    }
//
//    private fun ensureLocationEnabledOrResolve(startOnResolution: Boolean, onEnabled: () -> Unit) {
//        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L)
//            .setMinUpdateIntervalMillis(5_000L)
//            .setMinUpdateDistanceMeters(5f)
//            .build()
//        val settingsRequest = LocationSettingsRequest.Builder()
//            .addLocationRequest(request)
//            .setAlwaysShow(true)
//            .build()
//        val client = LocationServices.getSettingsClient(this)
//        client.checkLocationSettings(settingsRequest)
//            .addOnSuccessListener { onEnabled() }
//            .addOnFailureListener { ex ->
//                if (ex is ResolvableApiException) {
//                    try {
//                        startAfterResolution = startOnResolution
//                        val intentSender = IntentSenderRequest.Builder(ex.resolution).build()
//                        resolutionLauncher.launch(intentSender)
//                    } catch (_: Exception) {
//                        showEnableLocationDialog()
//                    }
//                } else {
//                    showEnableLocationDialog()
//                }
//            }
//    }
//
//    private fun showEnableLocationDialog() {
//        MaterialAlertDialogBuilder(this)
//            .setTitle("Enable Location")
//            .setMessage("Location is turned off. Please enable it to start tracking.")
//            .setPositiveButton("Open Settings") { d, _ ->
//                try {
//                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
//                } catch (_: Exception) { }
//                d.dismiss()
//            }
//            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
//            .show()
//    }
//
//    override fun onResume() {
//        super.onResume()
//        if (hasAllPermissions()) {
//            prepareMapAfterPermissions()
//        }
//    }
//
//    @SuppressLint("PotentialBehaviorOverride")
//    override fun onMapReady(map: GoogleMap) {
//        googleMap = map
//        Log.d("StartMarker", "🗺️ Map is ready")
//
//        // Configure map
//        map.uiSettings.isZoomControlsEnabled = true
//        map.uiSettings.isMyLocationButtonEnabled = true
//        map.uiSettings.isCompassEnabled = true
//
//        enableMyLocation()
//        applyMapPadding()
//        checkLocationSettings()
//
//        // Handle pending start location if any
//        if (pendingStartLocation != null && !isStartMarkerSet) {
//            Log.d("StartMarker", "📌 Adding pending start marker from onMapReady")
//            addStartMarkerToMap(pendingStartLocation!!)
//            pendingStartLocation = null
//            isWaitingForMap = false
//        }
//
//        // Handle camera positioning
//        if (pendingCenterOnPlace != null) {
//            val points = viewModel.routePoints.value
//            if (points.isNotEmpty()) {
//                zoomToFullRoute()
//            } else {
//                map.animateCamera(CameraUpdateFactory.newLatLngZoom(pendingCenterOnPlace!!, 16f))
//            }
//        } else if (pendingCenterOnMyLocation) {
//            centerMapOnCurrentLocationIfPossible()
//        }
//
//        setupMarkerClickListener(map)
//    }
//
//    private fun checkLocationSettings() {
//        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build()
//        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
//        val client = LocationServices.getSettingsClient(this)
//
//        client.checkLocationSettings(builder.build())
//            .addOnSuccessListener {
//                Log.d("LocationDebug", "✅ Location settings are OK")
//                enableMyLocation()
//            }
//            .addOnFailureListener { exception ->
//                if (exception is ResolvableApiException) {
//                    try {
//                        exception.startResolutionForResult(this, 1001)
//                    } catch (e: Exception) {
//                        Log.e("LocationDebug", "Error showing location dialog", e)
//                    }
//                }
//            }
//    }
//
//    private fun setupMarkerClickListener(map: GoogleMap) {
//        map.setOnMarkerClickListener { marker ->
//            val entry = stopMarkers.firstOrNull { it.second == marker } ?: return@setOnMarkerClickListener false
//            val stop = viewModel.stopPoints.value.firstOrNull { it.id == entry.first } ?: return@setOnMarkerClickListener false
//
//            pendingPhotoUri = null
//            currentStopDialogPhotoView = null
//
//            showStopDetailsDialog(stop, marker)
//            true
//        }
//    }
//
//    private fun showStopDetailsDialog(stop: StopPoint, marker: Marker) {
//        val form = layoutInflater.inflate(R.layout.dialog_stop_form, null)
//        val etName = form.findViewById<EditText>(R.id.etName)
//        val tvLocation = form.findViewById<TextView>(R.id.tvLocation)
//        val etAddress = form.findViewById<EditText>(R.id.etAddress)
//        val etPhone = form.findViewById<EditText>(R.id.etPhone)
//        val tvDuration = form.findViewById<TextView>(R.id.tvDuration)
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
//        tvLocation.text = "Location :$locationText"
//        tvDuration.text = "Visit Duration: ${stop.timeSpentMinutes} min"
//        etAddress.setText(stop.address ?: "")
//        etPhone.setText(stop.phone ?: "")
//        stop.imageUri?.let { imgView.setImageURI(Uri.parse(it)) }
//
//        val container = LinearLayout(this).apply {
//            orientation = LinearLayout.VERTICAL
//            addView(form)
//            addView(MaterialButton(context).apply {
//                text = "Capture seller image"
//                setOnClickListener {
//                    val photoFile = File.createTempFile("seller_", ".jpg", cacheDir)
//                    val uri = FileProvider.getUriForFile(this@MainActivity, "${packageName}.fileprovider", photoFile)
//                    pendingPhotoUri = uri
//                    takePictureLauncher.launch(uri)
//                }
//            })
//            addView(imgView)
//        }
//
//        val titleView = TextView(this).apply {
//            text = "Seller Shop details"
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
//                marker.title = name
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
//
//    private fun clearPendingPhoto() {
//        currentStopDialogPhotoView = null
//        pendingPhotoUri = null
//    }
//
//    private fun zoomToFullRoute() {
//        val map = googleMap ?: return
//        val points = viewModel.routePoints.value
//        if (points.isEmpty()) return
//
//        val builder = LatLngBounds.Builder()
//        points.forEach { builder.include(it.latLng) }
//        val bounds = builder.build()
//        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
//    }
//
//    private fun centerMapOnCurrentLocationIfPossible() {
//        val map = googleMap ?: return
//        if (!hasLocationPermission()) return
//
//        fusedLocationClient.lastLocation
//            .addOnSuccessListener { loc ->
//                if (loc != null) {
//                    pendingCenterOnMyLocation = false
//                    val me = LatLng(loc.latitude, loc.longitude)
//                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 16f))
//                } else {
//                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
//                        .addOnSuccessListener { cur ->
//                            if (cur != null) {
//                                pendingCenterOnMyLocation = false
//                                val me = LatLng(cur.latitude, cur.longitude)
//                                map.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 16f))
//                            }
//                        }
//                }
//            }
//    }
//
//    private fun enableMyLocation() {
//        val map = googleMap ?: return
//        if (hasLocationPermission()) {
//            map.isMyLocationEnabled = true
//            Log.d("LocationDebug", "✅ Blue dot enabled")
//        }
//    }
//
//    private fun hasLocationPermission(): Boolean {
//        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
//                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
//    }
//
//    private fun hasAllPermissions(): Boolean {
//        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
//        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
//        val notif = if (Build.VERSION.SDK_INT >= 33) {
//            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
//        } else true
//        return fine && coarse && notif
//    }
//
//    private fun requestAllPermissions() {
//        val list = mutableListOf(
//            Manifest.permission.ACCESS_FINE_LOCATION,
//            Manifest.permission.ACCESS_COARSE_LOCATION
//        )
//        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
//        requestPermissionsLauncher.launch(list.toTypedArray())
//    }
//
//    private fun applyMapPadding() {
//        val m = googleMap ?: return
//        val ins = systemBarInsets
//        if (ins != null) {
//            m.setPadding(16, ins.top + 24, 24, ins.bottom + 24)
//        } else {
//            m.setPadding(16, 24, 24, 24)
//        }
//    }
//
//    // ================ START MARKER METHODS ================
//
//    private fun observeStartLocation() {
//        if (isObservingStartLocation) return
//        isObservingStartLocation = true
//
//        lifecycleScope.launch {
//            viewModel.startLocation.collectLatest { startLatLng ->
//                if (startLatLng != null) {
//                    Log.d("StartMarker", "🎯 Start location received: $startLatLng")
////                    handleStartLocation(startLatLng)
//
//                    if (isStartMarkerSet && startMarker != null) {
//                        val currentPos = startMarker?.position
//                        if (currentPos != null &&
//                            currentPos.latitude == startLatLng.latitude &&
//                            currentPos.longitude == startLatLng.longitude) {
//                            Log.d("StartMarker", "⏭️ Same start point already exists, keeping it")
//                            return@collectLatest
//                        }
//                    }
//                } else {
//                    Log.d("StartMarker", "🗑️ Start location cleared")
//                    removeStartMarker()
//                }
//            }
//        }
//    }
//
//    private fun handleStartLocation(latLng: LatLng) {
//        // If map is ready, add marker immediately
//        if (googleMap != null) {
//            Log.d("StartMarker", "🗺️ Map ready, adding marker now")
//            addStartMarkerToMap(latLng)
//        } else {
//            // Store for when map is ready
//            Log.d("StartMarker", "⚠️ Map not ready, storing for later")
//            pendingStartLocation = latLng
//            isWaitingForMap = true
//        }
//    }
//
//    private fun addStartMarkerToMap(latLng: LatLng) {
//        val map = googleMap ?: run {
//            Log.e("StartMarker", "❌ Cannot add marker - map is null")
//            return
//        }
//
//        // Remove existing marker if any
//        if (isStartMarkerSet) {
//            Log.d("StartMarker", "🔄 Removing existing start marker")
//            startMarker?.remove()
//            startMarker = null
//        }
//
//        val markerIcon = try {
//            vectorToBitmap(R.drawable.ic_start_arrow)
//        } catch (e: Exception) {
//            Log.e("StartMarker", "Failed to convert vector: ${e.message}")
//            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
//        }
//
//        startMarker = map.addMarker(
//            MarkerOptions()
//                .position(latLng)
//                .title("Start Point")
//                .snippet("Journey started here")
//                .icon(markerIcon)
//                .anchor(0.5f, 0.5f)
//        )
//
//        if (startMarker != null) {
//            isStartMarkerSet = true
//            isWaitingForMap = false
//            Log.d("StartMarker", "✅ Start marker added successfully at: $latLng")
//            startMarker?.showInfoWindow()
//
//            // Only animate camera if not tracking or no route points
//            if (!viewModel.isTracking.value || viewModel.routePoints.value.isEmpty()) {
//                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
//            }
//        } else {
//            Log.e("StartMarker", "❌ Failed to add start marker")
//        }
//    }
//
//    private fun removeStartMarker() {
//        startMarker?.remove()
//        startMarker = null
//        isStartMarkerSet = false
//        pendingStartLocation = null
//        isWaitingForMap = false
//        Log.d("StartMarker", "🗑️ Start marker removed")
//    }
//
//    private fun vectorToBitmap(@DrawableRes vectorResId: Int): BitmapDescriptor {
//        return try {
//            val vectorDrawable = ContextCompat.getDrawable(this, vectorResId)
//            if (vectorDrawable == null) {
//                Log.e("StartMarker", "Vector drawable not found")
//                return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
//            }
//
//            val bitmap = Bitmap.createBitmap(
//                vectorDrawable.intrinsicWidth.takeIf { it > 0 } ?: 96,
//                vectorDrawable.intrinsicHeight.takeIf { it > 0 } ?: 96,
//                Bitmap.Config.ARGB_8888
//            )
//
//            val canvas = Canvas(bitmap)
//            vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
//            vectorDrawable.draw(canvas)
//            BitmapDescriptorFactory.fromBitmap(bitmap)
//        } catch (e: Exception) {
//            Log.e("StartMarker", "Error converting vector: ${e.message}")
//            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
//        }
//    }
//
//    // ================ EXPORT METHODS ================
//
//    private fun exportPdf() {
//        if (Build.VERSION.SDK_INT < 29) {
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//                pendingExportAction = { exportPdfInternal() }
//                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
//                return
//            }
//        }
//        exportPdfInternal()
//    }
//
//    private fun exportPdfInternal() {
//        val places = viewModel.getCompletedStopsMergedByName()
//        if (places.isEmpty()) {
//            Toast.makeText(this, "No completed stops to export", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        places.forEachIndexed { index, stop ->
//            Log.d("PDFExport", "Stop $index: ${stop.name}, Minutes: ${stop.timeSpentMinutes}")
//        }
//
//        val input = EditText(this)
//        input.hint = "Seller name"
//        input.setText(places.firstOrNull { !it.name.isNullOrBlank() }?.name ?: "")
//
//        MaterialAlertDialogBuilder(this)
//            .setTitle("Export PDF")
//            .setMessage("Enter seller name for the report header")
//            .setView(input)
//            .setPositiveButton("Export") { d, _ ->
//                val seller = input.text?.toString()?.trim()?.ifEmpty { "Seller" }
//
//                captureRouteScreenshot { bitmap ->
//                    var screenshotPath: String? = null
//
//                    if (bitmap != null) {
//                        screenshotPath = saveBitmapToFile(bitmap)
//                        Log.d("PDFExport", "Map screenshot saved to: $screenshotPath")
//                    }
//
//                    val rows = places.sortedBy { it.startTimeMillis }.map {
//                        PdfUtils.StopRow(
//                            name = it.name,
//                            lat = it.center.latitude,
//                            lng = it.center.longitude,
//                            startMillis = it.startTimeMillis,
//                            endMillis = it.endTimeMillis,
//                            durationMinutes = it.timeSpentMinutes,
//                            locationLabel = it.locationLabel,
//                            address = it.address,
//                            phone = it.phone,
//                            imageUri = it.imageUri,
//                            routeMapUri = screenshotPath
//                        )
//                    }
//
//                    val pathOrName = seller?.let { PdfUtils.generateSellerReport(this, it, rows) }
//                    Toast.makeText(this, "PDF saved: $pathOrName", Toast.LENGTH_LONG).show()
//
//                    // Clean up temp file
//                    screenshotPath?.let { path ->
//                        try { File(path).delete() } catch (e: Exception) { }
//                    }
//                }
//                d.dismiss()
//            }
//            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
//            .show()
//    }
//
//    private fun exportGpx() {
//        if (Build.VERSION.SDK_INT < 29) {
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//                pendingExportAction = { exportGpxInternal() }
//                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
//                return
//            }
//        }
//        exportGpxInternal()
//    }
//
//    private fun exportGpxInternal() {
//        lifecycleScope.launch {
//            val stops = viewModel.getCompletedStops().sortedBy { it.startTimeMillis }
//            if (stops.isEmpty()) {
//                Toast.makeText(this@MainActivity, "No route to export", Toast.LENGTH_SHORT).show()
//                return@launch
//            }
//            val start = stops.first().startTimeMillis
//            val end = stops.last().endTimeMillis ?: stops.last().startTimeMillis
//
//            val routePoints = viewModel.getRouteBetween(start, end)
//            if (routePoints.isEmpty()) {
//                Toast.makeText(this@MainActivity, "No route points in DB", Toast.LENGTH_SHORT).show()
//                return@launch
//            }
//
//            val nameOrPath = GpxUtils.exportRouteGpx(this@MainActivity, routePoints)
//            Toast.makeText(this@MainActivity, "GPX saved: $nameOrPath", Toast.LENGTH_LONG).show()
//        }
//    }
//
//    private fun captureRouteScreenshot(callback: (Bitmap?) -> Unit) {
//        val map = googleMap ?: run {
//            callback(null)
//            return
//        }
//
//        val routePoints = viewModel.routePoints.value
//        val stopPoints = viewModel.stopPoints.value
//
//        if (routePoints.isEmpty() && stopPoints.isEmpty()) {
//            map.snapshot { callback(it) }
//            return
//        }
//
//        Toast.makeText(this, "Capturing route map...", Toast.LENGTH_SHORT).show()
//
//        val builder = LatLngBounds.Builder()
//        routePoints.forEach { builder.include(it.latLng) }
//        stopPoints.forEach { builder.include(it.center) }
//        val bounds = builder.build()
//
//        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100), object : GoogleMap.CancelableCallback {
//            override fun onFinish() {
//                Handler(Looper.getMainLooper()).postDelayed({
//                    map.snapshot { callback(it) }
//                }, 800)
//            }
//            override fun onCancel() {
//                map.snapshot { callback(it) }
//            }
//        })
//    }
//
//    private fun saveBitmapToFile(bitmap: Bitmap): String? {
//        return try {
//            val file = File(cacheDir, "route_map_${System.currentTimeMillis()}.png")
//            FileOutputStream(file).use { out ->
//                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
//            }
//            file.absolutePath
//        } catch (e: Exception) {
//            Log.e("MapScreenshot", "Failed to save bitmap", e)
//            null
//        }
//    }
//}