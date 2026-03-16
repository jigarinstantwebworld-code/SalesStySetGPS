package com.example.salesstysetgps.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.salesstysetgps.services.GoogleCalendarService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val calendarService = GoogleCalendarService(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override suspend fun doWork(): Result {
        return try {
            // Get current user's Google account
            val account = getGoogleAccount()
            if (account == null) {
                Log.d("CalendarSync", "No Google account found")
                return Result.failure()
            }

            // Sync last 30 days and next 30 days
            val startDate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -30)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
            }.time

            val endDate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, 30)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
            }.time

            // Fetch calendar events
            val events = calendarService.getCalendarEvents(account, startDate, endDate)

            // Convert to schedules and save to local database
//            events.forEach { event ->
//                val schedule = event.toSchedule()
////                saveToLocalDatabase(schedule)
//            }

            Log.d("CalendarSync", "Synced ${events.size} events")
            Result.success()

        } catch (e: Exception) {
            Log.e("CalendarSync", "Sync failed", e)
            Result.retry()
        }
    }

    private fun getGoogleAccount(): com.google.android.gms.auth.api.signin.GoogleSignInAccount? {
        return try {
            val signInClient = calendarService.getGoogleSignInClient()
            signInClient.silentSignIn().result
        } catch (e: Exception) {
            null
        }
    }

//    private fun saveToLocalDatabase(schedule: com.example.sellervisitapp.models.Schedule) {
//        // Save to Room database
//        // Implementation depends on your existing database
//    }

    companion object {
        const val WORK_NAME = "calendar_sync_work"
    }
}