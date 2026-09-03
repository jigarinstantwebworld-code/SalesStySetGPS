package com.styset.sales.app.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.styset.sales.app.R
import com.styset.sales.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LocationForegroundService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var repository: LocationRepository
    private var locationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repository = LocationRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_UPDATE_NOTIFICATION") {
            val text = intent.getStringExtra("EXTRA_TEXT") ?: getString(R.string.notification_text)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1, buildNotification(text))
            return START_STICKY
        }

        startForeground(1, buildNotification())
        
        if (locationJob == null || locationJob?.isCompleted == true) {
            locationJob = serviceScope.launch {
                repository.locationUpdates().collectLatest {
                    // Keeps location updates active continuously in background
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Ensure service stays alive and restarts if user swiped app from Recents
        val restartServiceIntent = Intent(applicationContext, LocationForegroundService::class.java)
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext, 1, restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as? android.app.AlarmManager
        alarmManager?.set(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = getString(R.string.notification_channel_id)
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(customText: String? = null): Notification {
        val channelId = getString(R.string.notification_channel_id)
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(customText ?: getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification_location)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}


