package com.styset.sales.app

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

import android.Manifest
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.styset.sales.app.models.CalendarEvent
import com.styset.sales.app.services.GoogleCalendarService
import com.styset.sales.app.ui.calendar.ViewPagerAdapter
import com.styset.sales.app.workers.CalendarSyncWorker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.material.tabs.TabLayoutMediator
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.styset.sales.app.databinding.ActivityMain2Binding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity2 : AppCompatActivity() {
    private lateinit var calendarService: GoogleCalendarService
    private val TAG = "MainActivity2"
    private val eventsList = mutableListOf<CalendarEvent>()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private lateinit var binding: ActivityMain2Binding

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Sign-in result code: ${result.resultCode}")

        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                Log.d(TAG, "✅ Sign-in SUCCESS: ${account.displayName}")
                Toast.makeText(this, "Signed in as ${account.displayName}", Toast.LENGTH_SHORT).show()
                handleGoogleSignInSuccess(account)
//                updateUIForSignedIn(account)
//                syncCalendar()
            } catch (e: ApiException) {
                Log.e(TAG, "❌ Sign-in FAILED: ${e.statusCode} - ${e.message}")
                Toast.makeText(this, "Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Log.e(TAG, "❌ Sign-in CANCELLED (resultCode = ${result.resultCode})")
            Toast.makeText(this,
                "Sign-in cancelled. Make sure:\n" +
                        "1. Your email is added as test user\n" +
                        "2. Android Client ID has correct SHA-1\n" +
                        "3. Package name matches",
                Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMain2Binding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        calendarService = GoogleCalendarService(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        requestPermissions()
        checkSignInStatus()
        setupTabs()
        setupCalendarSync()

        // Check if already signed in
        checkGoogleSignIn()
    }

    private fun setupCalendarSync() {
        // Setup sync button
        binding.fabSync.setOnClickListener {
            if (checkGoogleSignIn()) {
                syncWithGoogleCalendar()
            } else {
                signInToGoogle()
            }
        }

        // Setup calendar account button
        binding.btnCalendarAccount.setOnClickListener {
            showCalendarAccountDialog()
        }
    }

    private fun checkGoogleSignIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        return account != null
    }

    private fun syncWithGoogleCalendar() {
        // Show syncing indicator
        binding.progressBar.visibility = View.VISIBLE

        // Setup periodic sync
        setupPeriodicCalendarSync()

        // Perform immediate sync
        performImmediateSync()
    }

    private fun updateUIForSignedInUser(account: GoogleSignInAccount) {
        binding.btnCalendarAccount.text = account.displayName?.firstOrNull()?.toString() ?: "G"
        Toast.makeText(
            this,
            "Syncing calendar for ${account.displayName}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun performImmediateSync() {
        val syncRequest = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this)
            .enqueue(syncRequest)

        // Observe sync result
        WorkManager.getInstance(this)
            .getWorkInfoByIdLiveData(syncRequest.id)
            .observe(this) { workInfo ->
                if (workInfo?.state == WorkInfo.State.SUCCEEDED) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Calendar synced successfully", Toast.LENGTH_SHORT).show()

                    // Refresh calendar view
//                    viewModel.loadSchedules()
                } else if (workInfo?.state == WorkInfo.State.FAILED) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Sync failed. Will retry later.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun startCalendarSync() {
        if (checkGoogleSignIn()) {
            syncWithGoogleCalendar()
        } else {
            // Auto sign-in if no account
            signInToGoogle()
        }
    }

    private fun setupPeriodicCalendarSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
            6, TimeUnit.HOURS // Sync every 6 hours
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1, TimeUnit.HOURS
            )
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                CalendarSyncWorker.Companion.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
    }

    private fun requestPermissions() {
        Dexter.withContext(this)
            .withPermissions(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.GET_ACCOUNTS
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    if (report.areAllPermissionsGranted()) {
                        startCalendarSync()
                        Toast.makeText(this@MainActivity2, "Permissions granted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity2, "Calendar permissions required", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: List<PermissionRequest>,
                    token: PermissionToken
                ) {
                    token.continuePermissionRequest()
                }
            })
            .check()
    }

    private fun showCalendarAccountDialog() {
        val accounts = GoogleSignIn.getLastSignedInAccount(this)
        val message = if (accounts != null) {
            "Connected to: ${accounts.displayName}\n${accounts.email}\n\nTap to change account"
        } else {
            "No account connected\nTap to sign in"
        }

        AlertDialog.Builder(this)
            .setTitle("Calendar Account")
            .setMessage(message)
            .setPositiveButton("Change Account") { _, _ ->
                signInToGoogle()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupTabs() {
        // Setup ViewPager2 with tabs
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = "Calendar"
                1 -> tab.text = "Today"
                2 -> tab.text = "Upcoming"
            }
        }.attach()
    }

    private fun checkSignInStatus() {
//        if (calendarService.isSignedIn()) {
//            val account = calendarService.getCurrentAccount()
//            updateUIForSignedIn(account)
//        } else {
//            updateUIForSignedOut()
//        }
    }

    private fun signInToGoogle() {
        val signInClient = calendarService.getGoogleSignInClient().signInIntent
        googleSignInLauncher.launch(signInClient)
    }

    // ✅ Back button click
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

//    private fun handleSignInSuccess(account: GoogleSignInAccount) {
//        Toast.makeText(this, "Signed in as ${account.displayName}", Toast.LENGTH_SHORT).show()
//        updateUIForSignedIn(account)
//        syncCalendar()
//    }
    private fun handleSignInError(error: ApiException) {
        Toast.makeText(this, "Sign-in failed: ${error.message}", Toast.LENGTH_LONG).show()
    }
//    private fun syncCalendar() {
//        if (!calendarService.isSignedIn()) {
//            Toast.makeText(this, "Please sign in first", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        textStatus.text = "Syncing calendar..."
//        btnSync.isEnabled = false
//
//        lifecycleScope.launch {
//            val account = calendarService.getCurrentAccount()
//            if (account != null) {
//                val calendar = Calendar.getInstance()
//                val startDate = calendar.time
//                calendar.add(Calendar.MONTH, 1)
//                val endDate = calendar.time
//
//                val events = calendarService.getCalendarEvents(account, startDate, endDate)
//
//                runOnUiThread {
//                    displayEvents(events)
//                    textStatus.text = "Found ${events.size} events"
//                    btnSync.isEnabled = true
//                }
//            }
//        }
//    }
//    private fun displayEvents(events: List<CalendarEvent>) {
//        eventsList.clear()
//        eventsList.addAll(events)
//
//        val adapter = CalendarEventAdapter(eventsList) { event ->
//            showEventDetails(event)
//        }
//        recyclerView.adapter = adapter
//    }
    private fun showEventDetails(event: CalendarEvent) {
        Toast.makeText(
            this,
            "${event.sellerName}\n${event.getFormattedTime()}",
            Toast.LENGTH_LONG
        ).show()
    }
    private fun handleGoogleSignInSuccess(account: GoogleSignInAccount) {
        Toast.makeText(this, "Signed in as ${account.displayName}", Toast.LENGTH_SHORT).show()
        syncWithGoogleCalendar()
        updateUIForSignedInUser(account)
    }
//    private fun updateUIForSignedIn(account: GoogleSignInAccount?) {
//        btnSignIn.text = "Signed in: ${account?.displayName?.take(15)}..."
//        btnSignIn.isEnabled = false
//        btnSync.isEnabled = true
//    }
//    private fun updateUIForSignedOut() {
//        btnSignIn.text = "Sign in with Google"
//        btnSignIn.isEnabled = true
//        btnSync.isEnabled = false
//        textStatus.text = "Not signed in"
//    }
}