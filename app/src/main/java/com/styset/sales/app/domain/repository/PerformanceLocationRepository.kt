package com.styset.sales.app.domain.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Process
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

data class PerformanceReport(
    val sessionDurationMs: Long,
    val totalLocationsReceived: Int,
    val avgLocationPerMinute: Float,

    // Battery Metrics
    val batteryDrainPercent: Float,
    val batteryDrainMAh: Float,
    val estimatedPowerPerHourMAh: Float,
    val batteryTemperature: Float,
    val batteryVoltage: Int,

    // CPU & Memory Metrics
    val avgCpuUsagePercent: Float,
    val maxCpuUsagePercent: Float,
    val avgMemoryUsageMB: Float,
    val maxMemoryUsageMB: Float,
    val memoryLeakSuspicion: Boolean,
    val gcCount: Int,

    // Location Update Efficiency
    val actualUpdateIntervalMs: Long,
    val targetUpdateIntervalMs: Long,
    val updateEfficiencyPercent: Int,
    val missedUpdates: Int,
    val delayedUpdates: Int,

    // Network/Provider Metrics
    val gpsFixTimeAvgMs: Long,
    val providerType: String,
    val locationAccuracyAvgMeters: Float,

    // App Health Metrics
    val totalWakeLocks: Int,
    val anrRiskScore: Float,
    val frameDrops: Int,
    val backgroundWorkTimeMs: Long,
    val mainThreadBlockingCount: Int,

    // Recommendations
    val recommendations: List<String>
)

data class MetricSnapshot(
    val timestamp: Long,
    val cpuUsage: Float,
    val memoryMB: Float,
    val batteryLevel: Int,
    val batteryTemp: Float,
    val batteryVoltage: Int,
    val locationReceived: Boolean
)

data class BatteryInfo(
    val level: Int,
    val temperature: Float,
    val voltage: Int,
    val isCharging: Boolean,
    val health: Int
)

class PerformanceLocationRepository(
    private val appContext: Context
) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    // Performance tracking
    private val performanceHistory = ConcurrentLinkedQueue<MetricSnapshot>()
    private val mutex = Mutex()

    // Battery tracking
    private var startBatteryLevel = 0
    private var startBatteryMAh = 0f

    // CPU & Memory tracking
    private var totalCpuUsage = 0f
    private var maxCpuUsage = 0f
    private var cpuSamples = 0

    private var totalMemoryUsage = 0f
    private var maxMemoryUsage = 0f
    private var memorySamples = 0
    private var totalGcCount = 0

    // Location efficiency tracking
    private var expectedLocationCount = 0
    private var actualLocationCount = 0
    private var delayedLocationCount = 0
    private var totalGpsFixTime = 0L
    private var gpsFixSamples = 0
    private var mainThreadBlockingCount = 0

    // App health tracking
    private var wakeLockCount = 0
    private var frameDropCount = 0
    private var backgroundWorkDuration = 0L

    // Session tracking
    private var sessionStartTime = 0L
    private var lastLocationTime = 0L
    private var lastSnapshotTime = 0L
    private var lastCpuTime = 0L
    private var lastProcessCpuTime = 0L

    private val performanceCheckInterval = 2000L // Check every 2 seconds
    private var performanceJob: Job? = null
    private var locationCollectionJob: Job? = null

    private val targetIntervalMs = 10_000L // Target 10 seconds between updates

    // Track location accuracies
    private val locationAccuracies = mutableListOf<Float>()

    private fun buildRequest(): LocationRequest =
        LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, targetIntervalMs)
            .setMinUpdateIntervalMillis(5_000L)
            .setMinUpdateDistanceMeters(5f)
            .build()

    @SuppressLint("MissingPermission")
    fun locationUpdates(): Flow<Location> = callbackFlow {
        val request = buildRequest()
        val callback = object : LocationCallback() {
            private var lastLocationTimestamp = 0L

            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    val now = System.currentTimeMillis()

                    // Track location accuracy
                    locationAccuracies.add(location.accuracy)
                    if (locationAccuracies.size > 100) locationAccuracies.removeAt(0)

                    // Check for delays
                    if (lastLocationTimestamp > 0) {
                        val actualInterval = now - lastLocationTimestamp
                        if (actualInterval > targetIntervalMs + 2000) {
                            delayedLocationCount++
                        }
                        if (actualInterval > targetIntervalMs + 5000) {
                            mainThreadBlockingCount++
                        }
                    }

                    // Track GPS fix time (time from location request to fix)
                    val fixTime = now - location.time
                    if (fixTime > 0 && fixTime < 5000) {
                        totalGpsFixTime += fixTime
                        gpsFixSamples++
                    }

                    actualLocationCount++
                    lastLocationTimestamp = now
                    lastLocationTime = now

                    trySend(location).isSuccess
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    Log.w("PerformanceRepo", "Location temporarily unavailable")
                }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, appContext.mainLooper)

        // Start periodic performance monitoring
        startPerformanceMonitoring()

        awaitClose {
            stopPerformanceMonitoring()
            fusedClient.removeLocationUpdates(callback)
        }
    }

    private fun startPerformanceMonitoring() {
        performanceJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                capturePerformanceSnapshot()
                delay(performanceCheckInterval)
            }
        }
    }

    private fun stopPerformanceMonitoring() {
        performanceJob?.cancel()
    }

    private suspend fun capturePerformanceSnapshot() {
        mutex.withLock {
            val now = System.currentTimeMillis()

            // Only capture if session is active
            if (sessionStartTime == 0L) return

            val cpuUsage = getCurrentCpuUsage()
            val memoryUsage = getCurrentMemoryUsageMB()
            val batteryInfo = getBatteryInfo()

            totalCpuUsage += cpuUsage
            maxCpuUsage = maxOf(maxCpuUsage, cpuUsage)
            cpuSamples++

            totalMemoryUsage += memoryUsage
            maxMemoryUsage = maxOf(maxMemoryUsage, memoryUsage)
            memorySamples++

            // Track GC count
            totalGcCount += Debug.getRuntimeStat("art.gc.collections_count")?.toIntOrNull() ?: 0

            // Calculate expected locations based on time
            val sessionDuration = now - sessionStartTime
            expectedLocationCount = (sessionDuration / targetIntervalMs).toInt() + 1

            performanceHistory.add(
                MetricSnapshot(
                    timestamp = now,
                    cpuUsage = cpuUsage,
                    memoryMB = memoryUsage,
                    batteryLevel = batteryInfo.level,
                    batteryTemp = batteryInfo.temperature,
                    batteryVoltage = batteryInfo.voltage,
                    locationReceived = false
                )
            )

            lastSnapshotTime = now
        }
    }

    private fun getCurrentCpuUsage(): Float {
        try {
            val pid = Process.myPid()

            // Read process CPU time from /proc/[pid]/stat
            val processStatFile = File("/proc/$pid/stat")
            if (!processStatFile.exists()) return 5f

            val processStatContent = processStatFile.readText()
            val processStatParts = processStatContent.trim().split(" ")
            if (processStatParts.size < 15) return 5f

            val utime = processStatParts[13].toLongOrNull() ?: 0
            val stime = processStatParts[14].toLongOrNull() ?: 0
            val totalProcessTime = utime + stime

            // Read total CPU time from /proc/stat
            val totalStatFile = File("/proc/stat")
            val totalStatContent = totalStatFile.readLines().firstOrNull() ?: return 5f
            val totalStatParts = totalStatContent.trim().split(Regex("\\s+"))
            if (totalStatParts.size < 8) return 5f

            var totalCpuTime = 0L
            for (i in 1..7) {
                totalCpuTime += totalStatParts.getOrNull(i)?.toLongOrNull() ?: 0
            }

            val now = System.currentTimeMillis()
            if (lastCpuTime > 0 && lastProcessCpuTime > 0) {
                val processDelta = totalProcessTime - lastProcessCpuTime
                val totalDelta = totalCpuTime - lastCpuTime
                if (totalDelta > 0) {
                    val usage = (processDelta.toFloat() / totalDelta) * 100
                    lastCpuTime = totalCpuTime
                    lastProcessCpuTime = totalProcessTime
                    return usage.coerceIn(0f, 100f)
                }
            }

            lastCpuTime = totalCpuTime
            lastProcessCpuTime = totalProcessTime
            return 0f

        } catch (e: Exception) {
            Log.e("PerformanceRepo", "Error reading CPU stats", e)
            return 5f
        }
    }

    private fun getCurrentMemoryUsageMB(): Float {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        return usedMemory / (1024f * 1024f)
    }

    private fun getBatteryInfo(): BatteryInfo {
        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        // Get battery level
        val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } else {
            val intent = appContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        }

        // Get temperature (works on all versions)
        val temperature = try {
            val intent =
                appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        } catch (e: Exception) {
            25f // Default room temperature
        }

        // Get voltage
        val voltage = try {
            val intent =
                appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        } catch (e: Exception) {
            3800 // Default 3.8V
        }

        // Check if charging
        val isCharging = try {
            val intent =
                appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            false
        }

        // Get battery health
        val health = try {
            val intent =
                appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
                ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        } catch (e: Exception) {
            BatteryManager.BATTERY_HEALTH_UNKNOWN
        }

        return BatteryInfo(level, temperature, voltage, isCharging, health)
    }

    suspend fun startSession() {
        mutex.withLock {
            sessionStartTime = System.currentTimeMillis()
            lastLocationTime = sessionStartTime

            // Get initial battery
            startBatteryLevel = getBatteryInfo().level
            startBatteryMAh = getBatteryCapacityMAh()

            // Reset all metrics
            performanceHistory.clear()
            totalCpuUsage = 0f
            maxCpuUsage = 0f
            cpuSamples = 0
            totalMemoryUsage = 0f
            maxMemoryUsage = 0f
            memorySamples = 0
            totalGcCount = 0
            actualLocationCount = 0
            delayedLocationCount = 0
            totalGpsFixTime = 0L
            gpsFixSamples = 0
            wakeLockCount = 0
            frameDropCount = 0
            backgroundWorkDuration = 0L
            mainThreadBlockingCount = 0
            locationAccuracies.clear()
            expectedLocationCount = 0
        }
    }

    suspend fun endSessionAndGenerateReport(): PerformanceReport {
        return mutex.withLock {
            val sessionEndTime = System.currentTimeMillis()
            val sessionDuration = sessionEndTime - sessionStartTime
            val sessionDurationHours = sessionDuration / (1000f * 60f * 60f)

            // Calculate final battery drain
            val endBatteryInfo = getBatteryInfo()
            val batteryDrainPercent =
                (startBatteryLevel - endBatteryInfo.level).toFloat().coerceAtLeast(0f)
            val batteryDrainMAh = calculateBatteryDrainMAh(batteryDrainPercent)

            // Calculate averages
            val avgCpu = if (cpuSamples > 0) totalCpuUsage / cpuSamples else 0f
            val avgMemory = if (memorySamples > 0) totalMemoryUsage / memorySamples else 0f
            val avgGcCount = totalGcCount

            val avgGpsFix = if (gpsFixSamples > 0) totalGpsFixTime / gpsFixSamples else 0L

            val locationsPerMinute = if (sessionDuration > 0) {
                (actualLocationCount.toFloat() / (sessionDuration / 60000f))
            } else 0f

            val updateEfficiency = if (expectedLocationCount > 0) {
                ((actualLocationCount.toFloat() / expectedLocationCount) * 100).toInt()
                    .coerceIn(0, 100)
            } else 100

            val missedUpdates = (expectedLocationCount - actualLocationCount).coerceAtLeast(0)

            val avgAccuracy = if (locationAccuracies.isNotEmpty()) {
                locationAccuracies.average().toFloat()
            } else 0f

            // Check for memory leaks (memory consistently increasing)
            val memoryLeakSuspicion = detectMemoryLeak()

            // Calculate ANR risk
            val anrRisk = calculateAnrRisk(avgCpu, frameDropCount, sessionDuration)

            // Generate recommendations
            val recommendations = generateRecommendations(
                updateEfficiency = updateEfficiency,
                avgCpu = avgCpu,
                avgMemory = avgMemory,
                batteryDrainPerHour = if (sessionDurationHours > 0) batteryDrainMAh / sessionDurationHours else 0f,
                memoryLeakSuspicion = memoryLeakSuspicion,
                delayedUpdates = delayedLocationCount,
                avgAccuracy = avgAccuracy,
                isCharging = endBatteryInfo.isCharging
            )

            PerformanceReport(
                sessionDurationMs = sessionDuration,
                totalLocationsReceived = actualLocationCount,
                avgLocationPerMinute = locationsPerMinute,
                batteryDrainPercent = batteryDrainPercent,
                batteryDrainMAh = batteryDrainMAh,
                estimatedPowerPerHourMAh = if (sessionDurationHours > 0) batteryDrainMAh / sessionDurationHours else 0f,
                batteryTemperature = endBatteryInfo.temperature,
                batteryVoltage = endBatteryInfo.voltage,
                avgCpuUsagePercent = avgCpu,
                maxCpuUsagePercent = maxCpuUsage,
                avgMemoryUsageMB = avgMemory,
                maxMemoryUsageMB = maxMemoryUsage,
                memoryLeakSuspicion = memoryLeakSuspicion,
                gcCount = avgGcCount,
                actualUpdateIntervalMs = if (actualLocationCount > 1) sessionDuration / actualLocationCount else targetIntervalMs,
                targetUpdateIntervalMs = targetIntervalMs,
                updateEfficiencyPercent = updateEfficiency,
                missedUpdates = missedUpdates,
                delayedUpdates = delayedLocationCount,
                gpsFixTimeAvgMs = avgGpsFix,
                providerType = "FusedLocationProvider (BALANCED)",
                locationAccuracyAvgMeters = avgAccuracy,
                totalWakeLocks = wakeLockCount,
                anrRiskScore = anrRisk,
                frameDrops = frameDropCount,
                backgroundWorkTimeMs = backgroundWorkDuration,
                mainThreadBlockingCount = mainThreadBlockingCount,
                recommendations = recommendations
            )
        }
    }

    private fun getBatteryCapacityMAh(): Float {
        return try {
            val batteryManager =
                appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val capacity =
                    batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                if (capacity > 0 && capacity != Integer.MAX_VALUE) {
                    capacity / 1000f
                } else {
                    // Fallback: Estimate based on device
                    estimateBatteryCapacity()
                }
            } else {
                estimateBatteryCapacity()
            }
        } catch (e: Exception) {
            3000f // Default 3000mAh
        }
    }

    private fun estimateBatteryCapacity(): Float {
        // Common battery capacities for different devices
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()

        return when {
            model.contains("pixel") -> 4000f
            model.contains("samsung") -> 4500f
            model.contains("oneplus") -> 4500f
            model.contains("xiaomi") -> 5000f
            manufacturer.contains("xiaomi") -> 5000f
            else -> 4000f
        }
    }

    private fun calculateBatteryDrainMAh(drainPercent: Float): Float {
        val capacity = getBatteryCapacityMAh()
        return (capacity * drainPercent / 100)
    }

    private fun detectMemoryLeak(): Boolean {
        if (memorySamples < 10) return false

        // Check if memory is consistently increasing
        val snapshots = performanceHistory.toList()
        if (snapshots.size < 20) return false

        val firstHalf = snapshots.take(snapshots.size / 2)
        val secondHalf = snapshots.takeLast(snapshots.size / 2)

        val avgFirstHalf = firstHalf.map { it.memoryMB }.average()
        val avgSecondHalf = secondHalf.map { it.memoryMB }.average()

        // Also check if memory keeps growing without dropping
        val isGrowing = avgSecondHalf > avgFirstHalf * 1.2

        // Check if memory hasn't dropped significantly
        val minMemory = snapshots.minByOrNull { it.memoryMB }?.memoryMB ?: 0f
        val maxMemory = snapshots.maxByOrNull { it.memoryMB }?.memoryMB ?: 0f
        val noRecovery = (maxMemory - minMemory) > 50 && avgSecondHalf > minMemory + 30

        return isGrowing || noRecovery
    }

    private fun calculateAnrRisk(avgCpu: Float, frameDrops: Int, durationMs: Long): Float {
        val durationSec = durationMs / 1000f
        val frameDropsPerMinute = if (durationSec > 0) frameDrops / (durationSec / 60f) else 0f

        var risk = 0f
        if (avgCpu > 50) risk += 30f
        if (avgCpu > 75) risk += 30f
        if (frameDropsPerMinute > 10) risk += 20f
        if (frameDropsPerMinute > 30) risk += 20f
        if (mainThreadBlockingCount > 5) risk += 10f

        return risk.coerceIn(0f, 100f)
    }

    private fun generateRecommendations(
        updateEfficiency: Int,
        avgCpu: Float,
        avgMemory: Float,
        batteryDrainPerHour: Float,
        memoryLeakSuspicion: Boolean,
        delayedUpdates: Int,
        avgAccuracy: Float,
        isCharging: Boolean
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (updateEfficiency < 70) {
            recommendations.add("⚠️ Low update efficiency (${updateEfficiency}%). Consider increasing minUpdateInterval to 10-15 seconds to reduce missed updates.")
        }

        if (avgCpu > 30) {
            recommendations.add(
                "⚡ High CPU usage (${
                    String.format(
                        "%.1f",
                        avgCpu
                    )
                }%). Move heavy processing to background threads using withContext(Dispatchers.IO)."
            )
        }

        if (avgMemory > 100) {
            recommendations.add(
                "💾 High memory usage (${
                    String.format(
                        "%.1f",
                        avgMemory
                    )
                } MB). Clear location history periodically or use bounded collections."
            )
        }

        if (memoryLeakSuspicion) {
            recommendations.add("🐛 MEMORY LEAK DETECTED! Ensure LocationCallback is properly removed in awaitClose {}")
        }

        if (batteryDrainPerHour > 200 && !isCharging) {
            recommendations.add(
                "🔋 High battery drain (${
                    String.format(
                        "%.1f",
                        batteryDrainPerHour
                    )
                } mAh/h). Consider using PRIORITY_LOW_POWER when app is in background."
            )
        }

        if (delayedUpdates > 10) {
            recommendations.add("⏰ $delayedUpdates delayed updates. Move all location processing off the main thread.")
        }

        if (avgAccuracy > 30) {
            recommendations.add(
                "📍 Poor GPS accuracy (${
                    String.format(
                        "%.1f",
                        avgAccuracy
                    )
                }m). Consider using PRIORITY_HIGH_ACCURACY for better results."
            )
        } else if (avgAccuracy < 10 && batteryDrainPerHour < 100) {
            recommendations.add("✅ Current settings are well optimized for your use case!")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("✅ Excellent performance! Your app is well optimized for battery and CPU usage.")
        }

        recommendations.add("💡 Pro tip: Use WorkManager for periodic location updates when app is in background")

        return recommendations
    }

    suspend fun reset() {
        mutex.withLock {
            sessionStartTime = 0L
            performanceHistory.clear()
            locationAccuracies.clear()
        }
    }
}