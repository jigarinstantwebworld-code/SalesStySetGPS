package com.example.salesstysetgps.util

import android.content.Context
import android.content.SharedPreferences
import com.example.salesstysetgps.models.BreakData
import com.example.salesstysetgps.models.LoginData
import com.example.salesstysetgps.models.LoginResponse
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("trip_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_TRIP_ID = "trip_id"
        private const val KEY_TRIP_START_TIME = "trip_start_time"
        private const val KEY_TRIP_STATUS = "trip_status"
        private const val KEY_TRIP_START_COORDINATES = "trip_start_coordinates"
        private const val KEY_SALES_EXECUTIVE_ID = "sales_executive_id"
        private const val KEY_IS_TRIP_ACTIVE = "is_trip_active"
        private const val KEY_CURRENT_TRIP_DATA = "current_trip_data"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_IS_LOGGED_IN_MAIN = "is_logged_in_main"
        private const val KEY_USER_TOKEN = "user_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ID_TOKEN = "id_token"

        @Volatile
        private var INSTANCE: PreferenceManager? = null

        fun getInstance(context: Context): PreferenceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferenceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // Save trip ID
    fun saveTripId(tripId: String) {
        prefs.edit().putString(KEY_TRIP_ID, tripId).apply()
    }

    fun saveUserData(userData: LoginData) {
        prefs.edit().apply {
            putInt(KEY_USER_ID, userData.id)
            putString(KEY_USER_TOKEN, userData.token)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
    }

    fun saveLoginData(response: LoginResponse) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, response.accessToken)
            putString(KEY_REFRESH_TOKEN, response.refreshToken)
            putString(KEY_ID_TOKEN, response.idToken)
            putInt(KEY_SALES_EXECUTIVE_ID, response.userId)
            putBoolean(KEY_IS_LOGGED_IN_MAIN, true)

            apply()
        }
    }

    fun getUserId(): Int? = prefs.getInt(KEY_USER_ID, -1).takeIf { it != -1 }
    fun getUserToken(): String? = prefs.getString(KEY_USER_TOKEN, null)
    fun isLoggedInMain(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN_MAIN, false)


    fun getTripId(): String? {
        return prefs.getString(KEY_TRIP_ID, null)
    }

    // Save trip start time
    fun saveTripStartTime(startTime: String) {
        prefs.edit().putString(KEY_TRIP_START_TIME, startTime).apply()
    }

    fun getTripStartTime(): String? {
        return prefs.getString(KEY_TRIP_START_TIME, null)
    }

    // Save trip status
    fun saveTripStatus(status: String) {
        prefs.edit().putString(KEY_TRIP_STATUS, status).apply()
    }

    fun getTripStatus(): String? {
        return prefs.getString(KEY_TRIP_STATUS, null)
    }

    // Save start coordinates
    fun saveStartCoordinates(latitude: Double, longitude: Double) {
        val coordinates = "$latitude,$longitude"
        prefs.edit().putString(KEY_TRIP_START_COORDINATES, coordinates).apply()
    }

    fun getStartCoordinates(): String? {
        return prefs.getString(KEY_TRIP_START_COORDINATES, null)
    }

    // Save sales executive ID
    fun saveSalesExecutiveId(salesExecutiveId: String) {
        prefs.edit().putString(KEY_SALES_EXECUTIVE_ID, salesExecutiveId).apply()
    }

    fun getSalesExecutiveId(): Int? {
        return prefs.getInt(KEY_SALES_EXECUTIVE_ID, 0)
    }

    // Trip active status
    fun setTripActive(isActive: Boolean) {
        prefs.edit().putBoolean(KEY_IS_TRIP_ACTIVE, isActive).apply()
    }

    fun isTripActive(): Boolean {
        return prefs.getBoolean(KEY_IS_TRIP_ACTIVE, false)
    }

    // Save complete trip data object
    fun saveCurrentTrip(tripData: TripData) {
        val json = gson.toJson(tripData)
        prefs.edit().putString(KEY_CURRENT_TRIP_DATA, json).apply()
    }

    fun getCurrentTrip(): TripData? {
        val json = prefs.getString(KEY_CURRENT_TRIP_DATA, null)
        return if (json != null) {
            gson.fromJson(json, TripData::class.java)
        } else null
    }

    // Clear all trip data (when trip ends)
    fun clearTripData() {
        prefs.edit().remove(KEY_TRIP_ID).apply()
        prefs.edit().remove(KEY_TRIP_START_TIME).apply()
        prefs.edit().remove(KEY_TRIP_STATUS).apply()
        prefs.edit().remove(KEY_TRIP_START_COORDINATES).apply()
        prefs.edit().remove(KEY_IS_TRIP_ACTIVE).apply()
        prefs.edit().remove(KEY_CURRENT_TRIP_DATA).apply()
    }

    fun isLoggedIn(): Boolean {
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        if (!isLoggedIn) return false

        val loginUtcTimestamp = prefs.getLong("login_utc_timestamp", 0)
        if (loginUtcTimestamp == 0L) return false

        // Get UTC date for login timestamp
        val loginUtcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = loginUtcTimestamp
        }
        val loginDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(loginUtcCalendar.time)

        // Get today's UTC date
        val todayUtcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(todayUtcCalendar.time)

        return loginDate == todayDate
    }


    fun clearAllData() {
        prefs.edit().clear().apply()
    }


    fun setLoggedIn(isLoggedIn: Boolean) {
        prefs.edit().putBoolean("is_logged_in", isLoggedIn).apply()
        if (isLoggedIn) {
            // Store UTC timestamp of login
            val utcTimestamp = System.currentTimeMillis() // This is already UTC
            prefs.edit().putLong("login_utc_timestamp", utcTimestamp).apply()
        } else {
            prefs.edit().remove("login_utc_timestamp").apply()
            prefs.edit().remove("login_date").apply()
        }
    }

    fun isOnBreak(): Boolean {
        return prefs.getBoolean("is_on_break", false)
    }

    fun setOnBreak(onBreak: Boolean) {
        prefs.edit().putBoolean("is_on_break", onBreak).apply()
    }


    fun getAttendanceId(): Int = prefs.getInt("attendance_id", 0)
    fun setAttendanceId(id: Int) = prefs.edit().putInt("attendance_id", id).apply()

    fun getWorkStartTime(): Long = prefs.getLong("work_start_time", 0)
    fun setWorkStartTime(time: Long) = prefs.edit().putLong("work_start_time", time).apply()

    fun getBreakCount(): Int = prefs.getInt("break_count", 0)
    fun setBreakCount(count: Int) = prefs.edit().putInt("break_count", count).apply()

    fun saveBreakData(breakData: BreakData) {
        val breaksJson = getBreaksList().toMutableList()
        breaksJson.add(breakData)
        val gson = Gson()
        val json = gson.toJson(breaksJson)
        prefs.edit().putString("breaks_list", json).apply()
    }

    fun getBreaksList(): List<BreakData> {
        val json = prefs.getString("breaks_list", "[]")
        val gson = Gson()
        val type = object : TypeToken<List<BreakData>>() {}.type
        return gson.fromJson(json, type)
    }


    fun getBreakStartTime(): Long {
        return prefs.getLong("break_start_time", 0)
    }

    fun setBreakStartTime(time: Long) {
        prefs.edit().putLong("break_start_time", time).apply()
    }

    fun setBreakEndTime(time: Long) {
        prefs.edit().putLong("break_end_time", time).apply()
    }

    fun getLoginTime(): Long {
        return prefs.getLong("login_time", 0)
    }

    fun updateBreakData(breakData: BreakData) {
        val breaksList = getBreaksList().toMutableList()
        val index = breaksList.indexOfFirst { it.breakId == breakData.breakId }
        if (index != -1) {
            breaksList[index] = breakData
            val json = gson.toJson(breaksList)
            prefs.edit().putString("breaks_list", json).apply()
        }
    }


    fun clearAttendanceData() {
        prefs.edit()
            .remove("is_logged_in")
            .remove("is_on_break")
            .remove("attendance_id")
            .remove("work_start_time")
            .remove("break_count")
            .remove("break_start_time")
            .remove("break_end_time")
            .remove("login_time")
            .remove("logout_time")
            .remove("breaks_list")
            .apply()
    }




}

data class TripData(
    val tripId: String,
    val startTime: String,
    val status: String,
    val startCoordinates: String,
    val salesExecutiveId: String
)