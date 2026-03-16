package com.example.salesstysetgps.services

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.example.salesstysetgps.R
import com.example.salesstysetgps.models.CalendarEvent
import com.google.api.client.http.javanet.NetHttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*


class GoogleCalendarService(private val context: Context) {

    companion object {
        private const val TAG = "GoogleCalendarService"
    }

    private val calendarScopes = listOf(
        CalendarScopes.CALENDAR_READONLY,
        CalendarScopes.CALENDAR_EVENTS
    )

    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(context.getString(R.string.google_server_client_id))
            .requestScopes(Scope(CalendarScopes.CALENDAR_READONLY))
            .requestScopes(Scope(CalendarScopes.CALENDAR_EVENTS))
            .build()

        return GoogleSignIn.getClient(context, gso)
    }

    fun isSignedIn(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }

    fun getCurrentAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    suspend fun getCalendarEvents(
        account: GoogleSignInAccount,
        startTime: Date,
        endTime: Date,
        query: String = "Seller Visit"
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching calendar events for account: ${account.email}")

            // Create credential using the Google account
            val credential = GoogleAccountCredential.usingOAuth2(
                context, calendarScopes
            ).setSelectedAccount(account.account)

            // Use NetHttpTransport instead of AndroidHttp
            val calendarService = Calendar.Builder(
                NetHttpTransport(),  // Changed from AndroidHttp.newCompatibleTransport()
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("Seller Visit App")
                .build()

            // Execute query to get events
            val events = calendarService.events().list("primary")
                .setTimeMin(com.google.api.client.util.DateTime(startTime))
                .setTimeMax(com.google.api.client.util.DateTime(endTime))
//                .setQ(query)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute()

            Log.d(TAG, "Found ${events.items?.size ?: 0} events")

            events.items?.mapNotNull { event ->
                try {
                    Log.e(TAG, "getCalendarEvents: -----------EVENT -${event.size}", )
                    CalendarEvent.fromGoogleEvent(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing event: ${event.id}", e)
                    null
                }
            } ?: emptyList()

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching calendar events", e)
            emptyList()
        }
    }

    suspend fun addEventNote(eventId: String, note: String) {
        withContext(Dispatchers.IO) {
            try {
                // Implementation to add note to Google Calendar event
                // This requires write permissions
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun updateEventStatus(eventId: String, status: String, notes: String) {
        withContext(Dispatchers.IO) {
            try {
                // Update event description with status
                // This requires write permissions
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}