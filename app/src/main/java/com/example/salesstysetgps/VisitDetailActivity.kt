package com.example.salesstysetgps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.example.salesstysetgps.models.CalendarEvent
import com.example.salesstysetgps.services.GoogleCalendarService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class VisitDetailActivity : AppCompatActivity() {

    private lateinit var event: CalendarEvent
    private lateinit var calendarService: GoogleCalendarService

    private lateinit var textSellerName: TextView
    private lateinit var textDateTime: TextView
    private lateinit var textLocation: TextView
    private lateinit var textCheckInTime: TextView
    private lateinit var textCheckOutTime: TextView
    private lateinit var editNotes: EditText
    private lateinit var btnCheckIn: MaterialButton
    private lateinit var btnCheckOut: MaterialButton
    private lateinit var btnAddPhoto: MaterialButton
    private lateinit var btnSubmit: MaterialButton
    private lateinit var photosContainer: LinearLayout

    private var checkInTime: String? = null
    private var checkOutTime: String? = null
    private val photos = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val photoUri = result.data?.data
            photoUri?.let { saveAndDisplayPhoto(it) }
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            getCurrentLocation()
        } else {
            Toast.makeText(this, "Location permission required for check-in", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_visit_detail)

        val eventJson = intent.getStringExtra("event_json")
        val gson = com.google.gson.Gson()
         event = gson.fromJson(eventJson, CalendarEvent::class.java)
        calendarService = GoogleCalendarService(this)

        initViews()
        displayEventInfo()
        setupClickListeners()
    }

    private fun initViews() {
        textSellerName = findViewById(R.id.textSellerName)
        textDateTime = findViewById(R.id.textDateTime)
        textLocation = findViewById(R.id.textLocation)
        textCheckInTime = findViewById(R.id.textCheckInTime)
        textCheckOutTime = findViewById(R.id.textCheckOutTime)
        editNotes = findViewById(R.id.editNotes)
        btnCheckIn = findViewById(R.id.btnCheckIn)
        btnCheckOut = findViewById(R.id.btnCheckOut)
        btnAddPhoto = findViewById(R.id.btnAddPhoto)
        btnSubmit = findViewById(R.id.btnSubmit)
        photosContainer = findViewById(R.id.photosContainer)
    }

    private fun displayEventInfo() {
        textSellerName.text = event.sellerName
        textDateTime.text = "${event.getFormattedDate()} • ${event.getFormattedTime()}"
        textLocation.text = event.location ?: "No location provided"
    }

    private fun setupClickListeners() {
        btnCheckIn.setOnClickListener { checkIn() }
        btnCheckOut.setOnClickListener { checkOut() }
        btnAddPhoto.setOnClickListener { addPhoto() }
        btnSubmit.setOnClickListener { submitVisit() }
    }

    private fun checkIn() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        getCurrentLocation()
    }

    private fun getCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                processCheckIn(location)
            } else {
                Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processCheckIn(location: Location) {
        checkInTime = dateFormat.format(Date())
        textCheckInTime.text = "Check-in: $checkInTime"
        textCheckInTime.text = "Check-in: $checkInTime at (${location.latitude}, ${location.longitude})"
        btnCheckIn.isEnabled = false
        btnCheckOut.isEnabled = true

        // Add note to Google Calendar
        addNoteToCalendar("Checked in at $checkInTime")

        Toast.makeText(this, "Checked in successfully!", Toast.LENGTH_SHORT).show()
    }

    private fun checkOut() {
        checkOutTime = dateFormat.format(Date())
        textCheckOutTime.text = "Check-out: $checkOutTime"
        btnCheckOut.isEnabled = false

        // Add note to Google Calendar
        addNoteToCalendar("Checked out at $checkOutTime")

        Toast.makeText(this, "Checked out successfully!", Toast.LENGTH_SHORT).show()
    }

    private fun addPhoto() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }

    private fun saveAndDisplayPhoto(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            // Save to internal storage
            val filename = "visit_${System.currentTimeMillis()}.jpg"
            val file = File(filesDir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            photos.add(file.absolutePath)

            // Display thumbnail
            val imageView = ImageView(this)
            imageView.layoutParams = LinearLayout.LayoutParams(200, 200)
            imageView.setImageBitmap(bitmap)
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.setPadding(4, 4, 4, 4)
            imageView.setOnClickListener {
                // Show full image
                Toast.makeText(this, "Photo saved", Toast.LENGTH_SHORT).show()
            }
            photosContainer.addView(imageView)

            Toast.makeText(this, "Photo added", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving photo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addNoteToCalendar(note: String) {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    calendarService.addEventNote(event.id, note)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun submitVisit() {
        val notes = editNotes.text.toString()
        val duration = calculateDuration()

        val summary = buildString {
            append("VISIT COMPLETED\n")
            append("Check-in: $checkInTime\n")
            append("Check-out: $checkOutTime\n")
            append("Duration: $duration\n")
            if (notes.isNotEmpty()) append("Notes: $notes\n")
            append("Photos: ${photos.size} photos taken")
        }

        addNoteToCalendar(summary)

        Toast.makeText(this, "Visit completed! Summary saved to calendar", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun calculateDuration(): String {
        if (checkInTime == null || checkOutTime == null) return "N/A"

        return try {
            val inTime = dateFormat.parse(checkInTime)
            val outTime = dateFormat.parse(checkOutTime)
            val diff = outTime.time - inTime.time
            val minutes = diff / (1000 * 60)
            "$minutes minutes"
        } catch (e: Exception) {
            "N/A"
        }
    }
}