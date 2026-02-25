package com.example.salesstysetgps.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.Context
import android.provider.MediaStore
import android.provider.Settings
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
import android.widget.ImageView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.salesstysetgps.util.PdfUtils
import com.example.salesstysetgps.data.SessionCache
import java.io.File
import kotlin.getValue

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
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
    private val prefs by lazy { getSharedPreferences("salesstysetgps_prefs", Context.MODE_PRIVATE) }
    private val prefAskedPermissionsKey = "asked_permissions_once"

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }
            if (granted) {
                // Permissions granted: do NOT auto-start tracking. Just prepare map.
                prepareMapAfterPermissions()
                Toast.makeText(this, "Permissions granted. Tap Start to begin.", Toast.LENGTH_SHORT).show()
            } else {
                showPermissionRequiredDialog()
            }
        }

    private val writePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) exportPdfInternal() else Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
        }

    private val resolutionLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult

            if (startAfterResolution) {
                // User pressed Start and we were waiting for GPS enable.
                startAfterResolution = false
                viewModel.startTracking()
                prepareMapAfterPermissions()
                updateButtons()
            } else {
                // GPS enabled but we are not starting automatically.
                prepareMapAfterPermissions()
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

        // Edge-to-edge content
//        window.setDecorFitsSystemWindows(false)

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

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Ask permissions on the very first launch only (no Start logic).
        maybeRequestPermissionsOnFirstLaunch()

        binding.btnStart.setOnClickListener {
            if (hasAllPermissions()) {

                // Is GPS enabled? If not → Shows system dialog to enable GPS.
                ensureLocationEnabledOrResolve(startOnResolution = true) {
                    viewModel.startTracking()
                    prepareMapAfterPermissions()
                    updateButtons()
                }
            } else {
                requestAllPermissions()
            }
        }
        binding.btnStop.setOnClickListener { viewModel.stopTracking(); updateButtons() }
        binding.btnReset.setOnClickListener { confirmReset() }
        binding.btnPlaces.setOnClickListener {
            startActivity(Intent(this, PlacesActivity::class.java))
        }
        binding.btnExportPdf.setOnClickListener { exportPdf() }

        lifecycleScope.launch {
            viewModel.isTracking.collectLatest { running ->
                binding.tvStatus.setText(if (running) R.string.tracking_status_running else R.string.tracking_status_stopped)
                updateButtons()
            }
        }

        // Update per-stop timer and session timer every second
        lifecycleScope.launch {
            while (true) {
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
                kotlinx.coroutines.delay(1000)
            }
        }

        // Draws polyline on map ,Updates as user moves,Animates camera to latest position
        lifecycleScope.launch {
            viewModel.routePoints.collectLatest { points ->
                val map = googleMap ?: return@collectLatest
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
                    if (latLngs.isNotEmpty()) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLngs.last(), 16f))
                    }
                }
            }
        }

        // When user stays in one place (e.g., shop) for a certain time: ,Red marker added
        lifecycleScope.launch {
            viewModel.stopPoints.collectLatest { stops ->
                val map = googleMap ?: return@collectLatest
                clearMapStops()
                stops.forEach { stop ->
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(stop.center)
                            .title(stop.name ?: "Stop")
                            .snippet("Time: ${stop.timeSpentMinutes} min")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    )
                    if (marker != null) stopMarkers.add(stop.id to marker)
                }
            }
        }

        updateButtons()
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
            // Not first launch: don't auto-prompt again, but block app if still missing.
            if (!hasAllPermissions()) showPermissionRequiredDialog() else prepareMapAfterPermissions()
        }
    }

    private fun prepareMapAfterPermissions() {
        pendingCenterOnMyLocation = true

        // If map is ready, enable MyLocation and center the camera now
        enableMyLocation()
        applyMapPadding()
        centerMapOnCurrentLocationIfPossible()
    }

    private fun showPermissionRequiredDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permissions required")
            .setMessage("Without location permission you can't use this application.\n\nPlease grant permission to continue.")
            .setCancelable(false)
            .setPositiveButton("Open Settings") { d, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                } catch (_: Exception) { }
                d.dismiss()
            }
            .setNegativeButton("Exit") { d, _ ->
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

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset all routes?")
            .setMessage("This will clear the current polyline, stops, and timers.")
            .setPositiveButton("Reset") { d, _ ->
                viewModel.resetSession()
                clearMapStops()
                routePolyline?.remove()
                routePolyline = null
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
        MaterialAlertDialogBuilder(this)
            .setTitle("Enable Location")
            .setMessage("Location is turned off. Please enable it to start tracking.")
            .setPositiveButton("Open Settings") { d, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (_: Exception) { }
                d.dismiss()
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }

    override fun onResume() {
        super.onResume()
        // If user comes back from Settings and permissions are granted, prepare the map.
        if (hasAllPermissions()) {
            prepareMapAfterPermissions()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true
        enableMyLocation()
        applyMapPadding()
        if (pendingCenterOnMyLocation) {
            centerMapOnCurrentLocationIfPossible()
        }

        // Marker click → show dialog: seller name, address, phone, photo from camera
        map.setOnMarkerClickListener { marker ->
            val entry = stopMarkers.firstOrNull { it.second == marker } ?: return@setOnMarkerClickListener false
            val stop = viewModel.stopPoints.value.firstOrNull { it.id == entry.first } ?: return@setOnMarkerClickListener false

            pendingPhotoUri = null
            currentStopDialogPhotoView = null

            val form = layoutInflater.inflate(R.layout.dialog_stop_form, null)
            val etName = form.findViewById<EditText>(R.id.etName)
            val etAddress = form.findViewById<EditText>(R.id.etAddress)
            val etPhone = form.findViewById<EditText>(R.id.etPhone)
            val imgView = ImageView(this).apply {
                adjustViewBounds = true
                maxHeight = 400
            }
            currentStopDialogPhotoView = imgView
            etName.setText(stop.name ?: "")
            etAddress.setText(stop.address ?: "")
            etPhone.setText(stop.phone ?: "")
            stop.imageUri?.let { imgView.setImageURI(Uri.parse(it)) }

            val container = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                addView(form)
                addView(com.google.android.material.button.MaterialButton(context).apply {
                    text = "Capture seller image"
                    setOnClickListener {
                        val photoFile = File.createTempFile("seller_", ".jpg", cacheDir)
                        val uri = FileProvider.getUriForFile(this@MainActivity, "${packageName}.fileprovider", photoFile)
                        pendingPhotoUri = uri
                        takePictureLauncher.launch(uri)
                    }
                })
                addView(imgView)
            }

            val locationTitle = if (!stop.address.isNullOrBlank()) {
                stop.address
            } else {
                String.format(
                    "Lat: %.5f  Lng: %.5f",
                    stop.center.latitude,
                    stop.center.longitude
                )
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("Stop details — $locationTitle  •  ${stop.timeSpentMinutes} min")
                .setView(container)
                .setPositiveButton("Save") { d, _ ->
                    val name = etName.text?.toString()?.trim().orEmpty()
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Seller name is required", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    val imageUri = pendingPhotoUri?.toString() ?: stop.imageUri
                    viewModel.updateStopDetails(stop.id, name, etAddress.text?.toString()?.trim().orEmpty().ifEmpty { null }, etPhone.text?.toString()?.trim().orEmpty().ifEmpty { null }, imageUri)
                    marker.title = name
                    marker.showInfoWindow()
                    currentStopDialogPhotoView = null
                    pendingPhotoUri = null
                    d.dismiss()
                }
                .setNegativeButton("Close") { d, _ ->
                    currentStopDialogPhotoView = null
                    pendingPhotoUri = null
                    d.dismiss()
                }
                .setOnDismissListener {
                    currentStopDialogPhotoView = null
                    pendingPhotoUri = null
                }
                .show()
            true
        }

        // Apply padding for My Location button and map UI
        applyMapPadding()
    }

    private fun centerMapOnCurrentLocationIfPossible() {
        val map = googleMap ?: return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return

        // Prefer last known location for speed; fall back to a fresh location if needed.
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
        }
    }

    private fun hasAllPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val notif = if (Build.VERSION.SDK_INT >= 33) ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true
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

    private fun showPlacesDialog() {
        val places = viewModel.getCompletedStopsMergedByName()
        if (places.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("No Places Visited")
                .setMessage("You haven't visited any places yet. Stay in one location for ${1}+ minutes to create a stop.")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_places, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_places)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = PlacesAdapter(places)

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Close") { d, _ -> d.dismiss() }
            .show()
    }

    private fun exportPdf() {
        if (Build.VERSION.SDK_INT < 29) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        exportPdfInternal()
    }

    private fun exportPdfInternal() {
        val places = viewModel.getCompletedStopsMergedByName()
        if (places.isEmpty()) {
            Toast.makeText(this, "No completed stops to export", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this)
        input.hint = "Seller name"
        input.setText(places.firstOrNull { !it.name.isNullOrBlank() }?.name ?: "")
        MaterialAlertDialogBuilder(this)
            .setTitle("Export PDF")
            .setMessage("Enter seller name for the report header")
            .setView(input)
            .setPositiveButton("Export") { d, _ ->
                val seller = input.text?.toString()?.trim()?.ifEmpty { "Seller" }
                val rows = places.map {
                    PdfUtils.StopRow(
                        name = it.name,
                        lat = it.center.latitude,
                        lng = it.center.longitude,
                        startMillis = it.startTimeMillis,
                        endMillis = it.endTimeMillis,
                        durationMinutes = it.timeSpentMinutes,
                        address = it.address,
                        phone = it.phone,
                        imageUri = it.imageUri
                    )
                }
                val pathOrName = seller?.let { PdfUtils.generateSellerReport(this, it, rows) }
                Toast.makeText(this, "PDF saved: $pathOrName", Toast.LENGTH_LONG).show()
                d.dismiss()
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }
}


