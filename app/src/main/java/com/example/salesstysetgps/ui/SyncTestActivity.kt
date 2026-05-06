package com.example.salesstysetgps.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.salesstysetgps.R
import com.example.salesstysetgps.SyncWorker
import com.example.salesstysetgps.data.local.AppDatabase
import com.example.salesstysetgps.data.local.StopDao
import com.example.salesstysetgps.data.local.StopEntity
import com.example.salesstysetgps.data.local.SyncDao
import com.example.salesstysetgps.databinding.ActivitySyncTestBinding
import com.example.salesstysetgps.workers.SyncManager
import com.example.salesstysetgps.workers.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// SyncTestActivity.kt
class SyncTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySyncTestBinding
    private lateinit var syncManager: SyncManager
    private lateinit var stopDao: StopDao
    private lateinit var syncDao: SyncDao

    private var syncJob: Job? = null
    private lateinit var syncRecordAdapter: SyncRecordAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = AppDatabase.get(this)
        stopDao = db.stopDao()
        syncDao = db.syncDao()

        // Initialize sync manager with mock API for testing
        syncManager = SyncManager(this)

        setupRecyclerView()
        setupClickListeners()
        loadSyncHistory()
        setSupportActionBar(binding.toolbarPlaces)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setupRecyclerView() {
        syncRecordAdapter = SyncRecordAdapter(emptyList())
        binding.rvSyncHistory.layoutManager = LinearLayoutManager(this)
        binding.rvSyncHistory.adapter = syncRecordAdapter
    }

    private fun setupClickListeners() {
        binding.btnSync.setOnClickListener {
            performManualSync()
        }
    }

    private fun performManualSync() {
        // Cancel any ongoing sync
        syncJob?.cancel()

        // Show progress
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "🔄 Syncing data..."
        binding.btnSync.isEnabled = false

        // Start sync
        syncJob = lifecycleScope.launch {
            val result = executeSync() // Renamed to avoid conflict
            handleSyncResult(result)
        }
    }

    private suspend fun executeSync(): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                val syncManager = SyncManager(this@SyncTestActivity)
                syncManager.performSyncDirect()
            } catch (e: Exception) {
                SyncResult(0, e.message ?: "Unknown error", 0)
            }
        }
    }

    private fun loadSyncHistory() {
        lifecycleScope.launch {
//            val syncRecords = syncDao.getAllSyncRecords()
            val lastWeekStart = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            val syncRecords = syncDao.getLastWeekSyncRecords(lastWeekStart)

            val dateFormat = SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.getDefault())

            val syncHistory = syncRecords.map { record ->
                SyncRecordDisplay(
                    apiName = record.apiName ?: "Sync Record",
                    lastSyncedTime = dateFormat.format(Date(record.lastSyncedTime)),
                    status = record.status
                )
            }

            syncRecordAdapter.updateRecords(syncHistory)

            // Optional: Show message if empty
            if (syncHistory.isEmpty()) {
                Toast.makeText(this@SyncTestActivity, "No sync records found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSyncResult(result: SyncResult) {
        binding.progressBar.visibility = View.GONE
        binding.btnSync.isEnabled = true

        if (result.success == 1) {
            binding.tvStatus.text = "✅ ${result.message}"
            Toast.makeText(this, "Synced ${result.syncedCount} records", Toast.LENGTH_SHORT).show()
//            refreshData()
            loadSyncHistory()
        } else {
            loadSyncHistory()
            binding.tvStatus.text = "❌ ${result.message}"
            Toast.makeText(this, "Sync failed: ${result.message}", Toast.LENGTH_LONG).show()
        }
    }


    private fun addMoreTestData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val existingStops = stopDao.getAllStops()
            val nextId = (existingStops.maxOfOrNull { it.id } ?: 2000000) + 1

            val newStops = listOf(
                StopEntity(
                    id = nextId,
                    name = "Test Stop ${nextId - 2000000}",
                    locationLabel = "Test Location ${nextId - 2000000}",
                    address = "Test Address ${nextId - 2000000}",
                    phone = "9999999999",
                    imageUri = null,
                    lat = 23.0225 + ((nextId - 2000000) * 0.001),
                    lng = 72.5714 + ((nextId - 2000000) * 0.001),
                    startWallTimeMillis = System.currentTimeMillis(),
                    endWallTimeMillis = System.currentTimeMillis() + 600000,
                    durationElapsedMinutes = 10,
                    startElapsedRealtimeMillis = System.currentTimeMillis(),
                    endElapsedRealtimeMillis = System.currentTimeMillis() + 600000,
                    letter = ('A' + ((nextId - 2000000 - 1).toInt())).toString()
                )
            )

            newStops.forEach { stop ->
                stopDao.upsert(stop)
                Log.d("TEST", "Added: ${stop.name}")
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@SyncTestActivity, "Added new test stop!", Toast.LENGTH_SHORT).show()

            }
        }
    }

    private fun clearTestData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val testStops = stopDao.getAllStops().filter { it.id > 2000000 }
            testStops.forEach { stop ->
                stopDao.deleteStop(stop)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@SyncTestActivity, "Cleared ${testStops.size} test stops", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearSyncHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            syncDao.deleteAllSyncRecords()

            withContext(Dispatchers.Main) {
                Toast.makeText(this@SyncTestActivity, "Sync history cleared", Toast.LENGTH_SHORT).show()
                loadSyncHistory()
            }
        }
    }
}