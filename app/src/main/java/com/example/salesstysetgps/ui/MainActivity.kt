package com.example.salesstysetgps.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
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
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.salesstysetgps.MainActivity2
import com.example.salesstysetgps.data.LocationPoint
import com.example.salesstysetgps.data.ReportRepository
import com.example.salesstysetgps.data.StopPoint
import com.example.salesstysetgps.data.StopRepository
import com.example.salesstysetgps.data.local.RoutePointEntity
import com.example.salesstysetgps.location.RouteRepository
import com.example.salesstysetgps.util.PdfUtils
import com.example.salesstysetgps.util.GpxUtils
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.getValue

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding

    private lateinit var stopRepository: StopRepository

    private lateinit var routeRepository: RouteRepository

    private var pendingRouteId: Long? = null

    private var pendingRouteHasStops: Boolean = true
    private var pendingShowFullRoute: Boolean = false
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
            pendingPhotoUri?.let { uri ->
                Log.d("CAMERA", "✅ Image captured: $uri")

                // Verify the image was saved
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val size = inputStream.available()
                        Log.d("CAMERA", "Image size: $size bytes")

                        // Try to decode to verify it's valid
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            Log.d("CAMERA", "✅ Valid image: ${bitmap.width}x${bitmap.height}")
                            bitmap.recycle()
                        } else {
                            Log.e("CAMERA", "❌ Failed to decode image")
                        }
                        inputStream.close()
                    }
                } catch (e: Exception) {
                    Log.e("CAMERA", "Error verifying image: ${e.message}")
                }

                // Display in ImageView
                currentStopDialogPhotoView?.setImageURI(uri)
            }
        } else {
            Log.e("CAMERA", "❌ Camera capture failed")
            pendingPhotoUri = null
        }
    }

    @SuppressLint("PotentialBehaviorOverride")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupWindowInsets()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        stopRepository = StopRepository(this)

        routeRepository = RouteRepository(this) // Add this

        // Restore saved state
        savedInstanceState?.let {
            isStartMarkerSet = it.getBoolean("isStartMarkerSet", false)
            pendingStartLocation = it.getParcelable("pendingStartLocation")
            isWaitingForMap = it.getBoolean("isWaitingForMap", false)
        }

        // Check if we should center on a specific place (coming from PlacesActivity)
        intent?.let {
            pendingShowFullRoute = it.getBooleanExtra("show_full_route", false)
            pendingRouteId = it.getLongExtra("route_id", 0L).takeIf { it != 0L }
            pendingRouteHasStops = it.getBooleanExtra("has_stops", true)
            val lat = it.getDoubleExtra("center_lat", Double.NaN)
            val lng = it.getDoubleExtra("center_lng", Double.NaN)
            if (!lat.isNaN() && !lng.isNaN()) {
//                binding.btnExportPdf.visibility = View.GONE
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
        setupNavigationDrawer()
    }

    private fun enableEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+
            window.setDecorFitsSystemWindows(false)

            // Set status bar and navigation bar colors
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT

            // Set status bar icons to dark or light based on your theme
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = true // For light status bar icons
            insetsController.isAppearanceLightNavigationBars = true
        } else {
            // For older Android versions
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    private fun setupWindowInsets() {
        // Apply insets to the main container BUT preserve toolbar
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.navView.setPadding(
                0,
                systemBars.top,
                0,
                systemBars.bottom
            )
            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                systemBars.bottom
            )

            insets
        }
    }


    private fun handlePendingStopFromPlaces() {
        val map = googleMap ?: return


        // Check if we have a pending stop ID from intent
        val stopId = pendingStopId ?: return

        if (stopId == 0L) {
            Log.d("PLACES_DEBUG", "Invalid stop ID: 0, ignoring")
            pendingStopId = null
            return
        }

        val lat = intent?.getDoubleExtra("center_lat", Double.NaN) ?: Double.NaN
        val lng = intent?.getDoubleExtra("center_lng", Double.NaN) ?: Double.NaN

        if (!lat.isNaN() && !lng.isNaN()) {
            val latLng = LatLng(lat, lng)

            Log.d("PLACES_DEBUG", "Handling stop from Places:")
            Log.d("PLACES_DEBUG", "  Stop ID: $stopId")
            Log.d("PLACES_DEBUG", "  LatLng: $latLng")
            Log.d("PLACES_DEBUG", "  Stop found in ViewModel: false")

            // ✅ LOAD FROM DATABASE INSTEAD
            loadStopFromDatabase(stopId, latLng, map)
        }
    }

    private fun addMarkerForStop(stop: StopPoint, latLng: LatLng, map: GoogleMap) {
        // Get stop letter
        val stopLetter = stop.letter ?: viewModel.getStopLetter(stop.id) ?: "?"
        val index = if (stopLetter != "?" && stopLetter.isNotEmpty()) {
            stopLetter[0] - 'A'
        } else {
            0
        }
        val color = getColorForIndex(index)

        // Create marker with letter
        val markerIcon = createMarkerWithLetter(stopLetter, color)

        val marker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Stop $stopLetter: ${stop.name ?: "Seller Shop"}")
                .icon(markerIcon)
        )

        // Store stop in marker tag
//        marker?.tag = stop
        marker?.tag = StopMarkerData("STOP", stopPoint = stop)


        // Animate camera to the stop
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(latLng, 18f),
            object : GoogleMap.CancelableCallback {
                override fun onFinish() {
//                    setupInfoWindow(map)
//                    marker?.showInfoWindow()
                    showStopDetailsDialog(stop, marker!!)
                }

                override fun onCancel() {
//                    setupInfoWindow(map)
//                    marker?.showInfoWindow()
                    showStopDetailsDialog(stop, marker!!)
                }
            }
        )
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
                        Toast.makeText(this@MainActivity, "Stop details not found", Toast.LENGTH_SHORT).show()

                        // Fallback: show marker without details
                        addSimpleMarker(latLng, map)
                    }
                }
            } catch (e: Exception) {
                Log.e("PLACES_DEBUG", "Error loading stop from database", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error loading stop", Toast.LENGTH_SHORT).show()
                    addSimpleMarker(latLng, map)
                }
            }
        }
    }

    private fun addSimpleMarker(latLng: LatLng, map: GoogleMap) {
        val marker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Seller Shop")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )

        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(latLng, 16f),
            object : GoogleMap.CancelableCallback {
                override fun onFinish() {
//                    marker?.showInfoWindow()
                }
                override fun onCancel() {
//                    marker?.showInfoWindow()
                }
            }
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                binding.drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /*private fun setupInfoWindow(map: GoogleMap) {
        map.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? = null

            override fun getInfoContents(marker: Marker): View {
                val view = layoutInflater.inflate(R.layout.map_info_window, null)
                val stopData = marker.tag as? StopPoint

                view.findViewById<TextView>(R.id.title).text =
                    "Seller Name : ${stopData?.name ?: "Seller Shop"}"
                view.findViewById<TextView>(R.id.location).text =
                    "Location : ${stopData?.locationLabel ?: "N/A"}"
                view.findViewById<TextView>(R.id.address).text =
                    "Address : ${stopData?.address ?: "N/A"}"
                view.findViewById<TextView>(R.id.phone).text =
                    "📞 ${stopData?.phone ?: "N/A"}"
                view.findViewById<TextView>(R.id.time).text =
                    "⏱ ${stopData?.timeSpentMinutes ?: 0} min"

                return view
            }
        })
    }*/

    private fun setupInfoWindow(map: GoogleMap) {
        map.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? = null

            override fun getInfoContents(marker: Marker): View {
                val view = layoutInflater.inflate(R.layout.map_info_window, null)

                when (val tag = marker.tag) {
                    is StopMarkerData -> {
                        when (tag.type) {
                            "START" -> {
                                view.findViewById<TextView>(R.id.title).text = "🏁 Journey Start"
                                view.findViewById<TextView>(R.id.location).text = "Time: ${tag.time ?: "Unknown"}"
                                view.findViewById<TextView>(R.id.address).visibility = View.GONE
                                view.findViewById<TextView>(R.id.phone).visibility = View.GONE
                                view.findViewById<TextView>(R.id.time).visibility = View.GONE
                            }
                            "END" -> {
                                view.findViewById<TextView>(R.id.title).text = "🏁 Journey End"
                                view.findViewById<TextView>(R.id.location).text = "Time: ${tag.time ?: "Unknown"}"
                                view.findViewById<TextView>(R.id.address).visibility = View.GONE
                                view.findViewById<TextView>(R.id.phone).visibility = View.GONE
                                view.findViewById<TextView>(R.id.time).visibility = View.GONE
                            }
                            "STOP" -> {
                                val stop = tag.stopPoint
                                view.findViewById<TextView>(R.id.title).text = "Seller Name : ${stop?.name ?: "Seller Shop"}"
                                view.findViewById<TextView>(R.id.location).text = "Location : ${stop?.locationLabel ?: "N/A"}"
                                view.findViewById<TextView>(R.id.address).text = "Address : ${stop?.address ?: "N/A"}"
                                view.findViewById<TextView>(R.id.phone).text = "📞 ${stop?.phone ?: "N/A"}"
                                view.findViewById<TextView>(R.id.time).text = "⏱ ${stop?.timeSpentMinutes ?: 0} min"
                            }
                        }
                    }
                    else -> {
                        // Default
                        view.findViewById<TextView>(R.id.title).text = marker.title ?: "Location"
                        view.findViewById<TextView>(R.id.location).visibility = View.GONE
                    }
                }

                return view
            }
        })
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

            Log.e("TAG", "setupClickListeners: ---------- STOP CLICK ${googleMap}", )
            // Then stop tracking
            viewModel.stopTracking(googleMap)
            updateButtons()

            // Dismiss any dialogs
            dismissGpsDialog()

            Toast.makeText(this, "Tracking stopped. All visits saved.", Toast.LENGTH_SHORT).show()
        }

        binding.btnReset.setOnClickListener { confirmReset() }
//        binding.btnPlaces.setOnClickListener {
//            if (!viewModel.isTracking.value){
//                val intent = Intent(this, PlacesActivity::class.java).apply {
//                    putExtra("is_tracking", viewModel.isTracking.value)
//                }
//                startActivity(intent)
//            }else{
//                Toast.makeText(this, "Please complete the onGoing round", Toast.LENGTH_SHORT).show()
//            }
//
//        }

//        binding.btnExportPdf.setOnClickListener {
//            showDatePickerForReport()
//        }
//
//        binding.btnExportGpx.setOnClickListener { exportGpx() }
//
//        binding.btnMapType.setOnClickListener {
//            showMapTypeDialog()
//        }
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
//                marker.tag = Pair(letter, stop.id)
                marker.tag = StopMarkerData("STOP", stopPoint = stop)
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

        binding.navView.setCheckedItem(R.id.nav_home)
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
        map.setPadding(0, 0, 0, 0)
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

        Log.d("MAP_DEBUG", "=== Map Ready State ===")
        Log.d("MAP_DEBUG", "pendingShowFullRoute: $pendingShowFullRoute")
        Log.d("MAP_DEBUG", "pendingRouteId: $pendingRouteId")
        Log.d("MAP_DEBUG", "pendingStopId: $pendingStopId")
        Log.d("MAP_DEBUG", "pendingRouteHasStops: $pendingRouteHasStops")
        Log.d("MAP_DEBUG", "isTracking: ${viewModel.isTracking.value}")
        Log.d("MAP_DEBUG", "routePoints size: ${viewModel.routePoints.value.size}")
        Log.d("MAP_DEBUG", "pendingCenterOnMyLocation: $pendingCenterOnMyLocation")


        /*handlePendingStopFromPlaces()

        // Handle camera positioning ONLY if no selected place
        if (pendingCenterOnPlace == null) {
            val points = viewModel.routePoints.value

            if (points.isNotEmpty()) {
                zoomToFullRoute()
            } else if (pendingCenterOnMyLocation) {
                centerMapOnCurrentLocationIfPossible()
            }
        }*/

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
            viewModel.isTracking.value && viewModel.routePoints.value.isNotEmpty() -> {
                Log.d("MAP_DEBUG", "🎯 Priority 3: Showing active tracking route")
                zoomToFullRoute()
            }

            // Priority 3: Normal map initialization
            else -> {
                Log.d("LOCATION", "📍 Centering on current location (first open)")
                centerMapOnCurrentLocation()
            }
        }

        setupMarkerClickListener(map)
    }


    @SuppressLint("MissingPermission")
    private fun centerMapOnCurrentLocation() {
        val map = googleMap ?: return

        if (!hasLocationPermission()) {
            Log.d("LOCATION", "No location permission")
            return
        }

        // Try to get last known location first (faster)
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    Log.d("LOCATION", "📍 Using last location: $location")
                    val latLng = LatLng(location.latitude, location.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                } else {
                    // If no last location, request current location
                    Log.d("LOCATION", "No last location, requesting current")
                    requestCurrentLocation()
                }
            }
            .addOnFailureListener { e ->
                Log.e("LOCATION", "Failed to get last location", e)
                requestCurrentLocation()
            }
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation() {
        val map = googleMap ?: return

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        if (hasLocationPermission()) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d("LOCATION", "📍 Got current location: $location")
                        val latLng = LatLng(location.latitude, location.longitude)
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    } else {
                        // If still no location, show default view
                        Log.d("LOCATION", "Could not get location, showing default")
                        showDefaultMapView()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("LOCATION", "Failed to get current location", e)
                    showDefaultMapView()
                }
        } else {
            showDefaultMapView()
        }
    }
    private fun showDefaultMapView() {
        val map = googleMap ?: return
        // Default to a reasonable world view
        val defaultLocation = LatLng(20.0, 0.0)  // Center of Africa
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 2f))
        Log.d("LOCATION", "Showing default world view")
    }

    private fun loadAndDisplayFullRoute(routeId: Long, hasStops: Boolean = true) {
        val map = googleMap ?: return

        val message = if (hasStops) "🔄 Loading route with stops..."
        else "🔄 Loading driving route..."

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        Log.d("ROUTE_DEBUG", "Loading full route: $routeId (hasStops: $hasStops)")

        lifecycleScope.launch {
            try {
                // Load route points from database
                val routePoints = routeRepository.getRoutePoints(routeId)
                Log.d("ROUTE_DEBUG", "Loaded ${routePoints.size} route points")

                withContext(Dispatchers.Main) {
                    if (routePoints.isNotEmpty()) {
                        // Clear existing polylines and markers
                        routePolyline?.remove()
                        clearMapStops()

                        // Draw the route polyline
                        drawRoutePolyline(routePoints)

                        // Add START and END markers
                        addRouteStartEndMarkers(routePoints)

                        if (hasStops) {
                            // Load and add stops if they exist
                            val stops = routeRepository.getStopsForRoute(routeId)
                            Log.d("ROUTE_DEBUG", "Loaded ${stops.size} stops")
                            addRouteStopMarkers(stops)
                        } else {
                            // No stops to add, just show the route
                            Log.d("ROUTE_DEBUG", "No stops to display")
                        }

                        // Zoom to show entire route
                        zoomToRouteBounds(routePoints)

                        Toast.makeText(this@MainActivity,
                            if (hasStops) "✅ Route with stops loaded"
                            else "✅ Driving route loaded",
                            Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "No route points found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ROUTE_DEBUG", "Error loading route", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error loading route", Toast.LENGTH_SHORT).show()
                }
            } finally {
                // Clear pending flags
                pendingShowFullRoute = false
                pendingRouteId = null
            }
        }
    }

    private fun addRouteStartEndMarkers(routePoints: List<RoutePointEntity>) {
        val map = googleMap ?: return
        if (routePoints.isEmpty()) return

        // Get first and last points
        val startPoint = routePoints.first()
        val endPoint = routePoints.last()

        val startLatLng = LatLng(startPoint.latitude, startPoint.longitude)
        val endLatLng = LatLng(endPoint.latitude, endPoint.longitude)

        val startTime = if (routePoints.isNotEmpty()) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(routePoints.first().timestamp))
        } else {
            "Unknown"
        }

        val endTime = if (routePoints.size > 1) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(routePoints.last().timestamp))
        } else {
            "Unknown"
        }

        // Create start marker (green)
        val startIcon = vectorToBitmap(R.drawable.ic_start)
        val startMarker = map.addMarker(
            MarkerOptions()
                .position(startLatLng)
                .title("Start Point")
                .snippet("Started at $startTime")
                .icon(startIcon)
                .anchor(0.5f, 0.5f)
        )

        // Create end marker (red)
        val endIcon = vectorToBitmap(R.drawable.ic_stop)
        val endMarker = map.addMarker(
            MarkerOptions()
                .position(endLatLng)
                .title("End Point")
                .snippet("Ended at $endTime")
                .icon(endIcon)
                .anchor(0.5f, 0.5f)
        )

        // Store in stopMarkers list with special IDs (negative to distinguish from regular stops)
        if (startMarker != null) {
            stopMarkers.add(-1L to startMarker) // -1 for start
            startMarker.tag = StopMarkerData("START", startTime)
//            startMarker.tag = "START"
        }
        if (endMarker != null) {
            stopMarkers.add(-2L to endMarker) // -2 for end
            endMarker.tag = StopMarkerData("END", endTime)
        }

        Log.d("MARKER_DEBUG", "Added start marker at $startLatLng")
        Log.d("MARKER_DEBUG", "Added end marker at $endLatLng")
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

        Log.d("ROUTE_DEBUG", "Polyline drawn with ${latLngs.size} points")
    }

    private fun highlightSpecificStop(stopId: Long, stops: List<StopPoint>) {
        val map = googleMap ?: return

        // Find the stop
        val stop = stops.firstOrNull { it.id == stopId }
        if (stop == null) {
            Log.e("ROUTE_DEBUG", "Stop $stopId not found in route")
            return
        }

        // Find its marker
        val markerEntry = stopMarkers.firstOrNull { it.first == stopId }
        val marker = markerEntry?.second

        if (marker != null) {
            // Animate to this stop with zoom
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(stop.center, 18f),
                object : GoogleMap.CancelableCallback {
                    override fun onFinish() {
                        marker.showInfoWindow()

                        // Open details dialog after a delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            showStopDetailsDialog(stop, marker)
                        }, 500)
                    }

                    override fun onCancel() {
                        marker.showInfoWindow()
                    }
                }
            )
        } else {
            // Marker not found, just center on location
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(stop.center, 16f))
        }
    }

    private fun addRouteStopMarkers(stops: List<StopPoint>) {
        val map = googleMap ?: return

        // Sort stops by time
        val sortedStops = stops.sortedBy { it.startTimeMillis }

        sortedStops.forEachIndexed { index, stop ->
            val letter = stop.letter ?: ('A'.plus(index)).toString()
            val color = getColorForIndex(index)

            val markerIcon = createMarkerWithLetter(letter, color)

            val marker = map.addMarker(
                MarkerOptions()
                    .position(stop.center)
                    .title("Stop $letter: ${stop.name ?: "Unnamed"}")
                    .snippet("Duration: ${stop.timeSpentMinutes} min")
                    .icon(markerIcon)
            )

            /*if (marker != null) {
                stopMarkers.add(stop.id to marker)
                marker.tag = stop
            }*/
            if (marker != null) {
                stopMarkers.add(stop.id to marker)
                marker.tag = StopMarkerData("STOP", stopPoint = stop)
            }
        }

        Log.d("ROUTE_DEBUG", "Added ${stops.size} stop markers")
    }

    private fun zoomToRouteBounds(routePoints: List<RoutePointEntity>) {
        val map = googleMap ?: return

        if (routePoints.isEmpty()) return

        val builder = LatLngBounds.Builder()
        routePoints.forEach { builder.include(LatLng(it.latitude, it.longitude)) }

        // Also include all stops if available
        stopMarkers.forEach { builder.include(it.second.position) }

        val bounds = builder.build()

        // Add padding
        val padding = if (stopMarkers.isNotEmpty()) 100 else 80 // pixels
        val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)

        map.animateCamera(cameraUpdate)
        Log.d("ROUTE_DEBUG", "Camera zoomed to route bounds")
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

    @SuppressLint("MissingPermission")
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

    /*private fun setupMarkerClickListener(map: GoogleMap) {
        map.setOnMarkerClickListener { marker ->
            val entry = stopMarkers.firstOrNull { it.second == marker } ?: return@setOnMarkerClickListener false
            val stop = viewModel.stopPoints.value.firstOrNull { it.id == entry.first } ?: return@setOnMarkerClickListener false

            pendingPhotoUri = null
            currentStopDialogPhotoView = null
            showStopDetailsDialog(stop, marker)
            true
        }
    }*/

    private fun setupMarkerClickListener(map: GoogleMap) {
        map.setOnMarkerClickListener { marker ->
            when (val tag = marker.tag) {
                is StopMarkerData -> {
                    when (tag.type) {
                        "START" -> {
                            // Directly show start dialog
                            showStartEndInfoDialog("Journey Start", tag.time ?: "Unknown")
                            true // Return true to consume the event
                        }
                        "END" -> {
                            // Directly show end dialog
                            showStartEndInfoDialog("Journey End", tag.time ?: "Unknown")
                            true
                        }
                        "STOP" -> {
                            // Handle regular stop - directly show details dialog
                            tag.stopPoint?.let { stop ->
                                // Hide any default info window first
                                marker.hideInfoWindow()
                                // Show custom dialog
                                showStopDetailsDialog(stop, marker)
                            }
                            true
                        }
                        else -> false
                    }
                }
                else -> {
                    // For markers without our custom tag, let default behavior happen
                    false
                }
            }
        }
    }

    private fun showStartEndInfoDialog(title: String, time: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage("Time: $time\n\nThis is where your journey ${if (title.contains("Start")) "began" else "ended"}.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
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
                        // Create permanent directory for seller images
                        val sellerImagesDir = File(filesDir, "seller_images")
                        if (!sellerImagesDir.exists()) {
                            sellerImagesDir.mkdirs()
                            Log.d("CAMERA", "Created directory: ${sellerImagesDir.absolutePath}")
                        }

                        // Create file with timestamp
                        val fileName = "seller_${System.currentTimeMillis()}.jpg"
                        val photoFile = File(sellerImagesDir, fileName)

                        Log.d("CAMERA", "Saving to: ${photoFile.absolutePath}")

                        val uri = FileProvider.getUriForFile(
                            this@MainActivity,
                            "${packageName}.fileprovider",
                            photoFile
                        )
                        pendingPhotoUri = uri
                        takePictureLauncher.launch(uri)
                    } catch (e: Exception) {
                        Log.e("CAMERA", "Error: ${e.message}")
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

    @SuppressLint("MissingPermission")
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

    @SuppressLint("MissingPermission")
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
//        lifecycleScope.launch {
//            val stops = viewModel.getCompletedStops().sortedBy { it.startTimeMillis }
//            if (stops.isEmpty()) {
//                Toast.makeText(this@MainActivity, "No route to export because you don't have any stop point", Toast.LENGTH_SHORT).show()
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

    private fun showDatePickerForReport() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            calendar.set(selectedYear, selectedMonth, selectedDay, 0, 0, 0)
            generateDailyReport(calendar.timeInMillis)
        }, year, month, day).show()
    }

    private fun generateDailyReport(dateMillis: Long) {
        val reportRepo = ReportRepository(this)

        // Show loading dialog with progress
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("📊 Generating Daily Report")
            .setMessage("Fetching route data and calculating distances...")
            .setCancelable(false)
            .show()

        // Launch coroutine for database operations
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Fetch enhanced report data with stop distances
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

                // Log summary for debugging
                Log.d("REPORT_DEBUG", "📋 Generating report for ${reportData.date}")
                Log.d("REPORT_DEBUG", "   Routes: ${reportData.totalRoutes}")
                Log.d("REPORT_DEBUG", "   Stops: ${reportData.totalStops}")
                Log.d("REPORT_DEBUG", "   Duration: ${reportData.totalDuration} min")
                Log.d("REPORT_DEBUG", "   Distance: ${String.format("%.2f", reportData.totalDistance)} km")

                val places = viewModel.getCompletedStopsMergedByName()
                // Update dialog message
                withContext(Dispatchers.Main) {
                    dialog.setMessage("Creating PDF with enhanced formatting...")
                }

                // Generate enhanced PDF
                EnhancedPdfReportGenerator(this@MainActivity).generateDailyReport(reportData) { file ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            dialog.dismiss()

                            if (file != null) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "✅ Report saved: ${file.name} (${file.length() / 1024} KB)",
                                    Toast.LENGTH_LONG
                                ).show()

                                // Ask user if they want to open the PDF
//                                showOpenPdfDialog(file)
                                openPdfFile(file)
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "❌ Failed to generate report",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun openPdfFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No PDF viewer found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNavigationDrawer() {

        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_menu_white_24dp)
        if (drawable == null) {
            Log.e("NAV_DEBUG", "❌ ic_menu_white_24dp drawable not found!")
        } else {
            Log.d("NAV_DEBUG", "✅ Drawable found")
        }

        // Set up the hamburger icon to open drawer
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
            title = "Sales Visit Tracker"  // Add a title
        }

        binding.toolbar.invalidate()

        binding.toolbar.visibility = View.VISIBLE

        // Handle navigation item clicks
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {

                    binding.drawerLayout.closeDrawers()
                    binding.navView.setCheckedItem(R.id.nav_home)
                    // Already on home, just refresh map if needed
                    true
                }
                R.id.nav_places -> {
                    binding.drawerLayout.closeDrawers()
                    startActivity(Intent(this, PlacesActivity::class.java))
                    true
                }
                R.id.nav_calendar -> {
                    binding.drawerLayout.closeDrawers()
                    // Open MainActivity2 (Calendar Activity)
                    startActivity(Intent(this, MainActivity2::class.java))
                    true
                }
                R.id.nav_export_pdf -> {
                    binding.drawerLayout.closeDrawers()
                    // Open dialog
                    showDatePickerForReport()
                    // ✅ Re-enable checking and set Home
                    binding.navView.setCheckedItem(R.id.nav_home)
                    true
                }

                R.id.nav_lead -> {
                    binding.drawerLayout.closeDrawers()
                    startActivity(Intent(this, LeadActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }
}

data class StopMarkerData(
    val type: String, // "START", "END", or "STOP"
    val time: String? = null,
    val stopPoint: StopPoint? = null
)