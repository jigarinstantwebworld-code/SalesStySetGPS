package com.styset.sales.app.util

import android.content.Context
import android.content.SharedPreferences
import com.styset.sales.app.models.BreakData
import com.styset.sales.app.models.LoginData
import com.styset.sales.app.models.LoginResponse
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

//class PreferenceManager(context: Context) {
//
//    private val prefs: SharedPreferences = context.getSharedPreferences("trip_prefs", Context.MODE_PRIVATE)
//    private val gson = Gson()
//
//    companion object {
//        private const val KEY_TRIP_ID = "trip_id"
//        private const val KEY_TRIP_START_TIME = "trip_start_time"
//        private const val KEY_TRIP_STATUS = "trip_status"
//        private const val KEY_TRIP_START_COORDINATES = "trip_start_coordinates"
//        private const val KEY_SALES_EXECUTIVE_ID = "sales_executive_id"
//        private const val KEY_IS_TRIP_ACTIVE = "is_trip_active"
//        private const val KEY_CURRENT_TRIP_DATA = "current_trip_data"
//        private const val KEY_IS_LOGGED_IN_MAIN = "is_logged_in_main"
//        private const val KEY_USER_TOKEN = "user_token"
//        private const val KEY_USER_ID = "user_id"
//        private const val KEY_ACCESS_TOKEN = "access_token"
//        private const val KEY_REFRESH_TOKEN = "refresh_token"
//        private const val KEY_ID_TOKEN = "id_token"
//
//        @Volatile
//        private var INSTANCE: PreferenceManager? = null
//
//        fun getInstance(context: Context): PreferenceManager {
//            return INSTANCE ?: synchronized(this) {
//                INSTANCE ?: PreferenceManager(context.applicationContext).also { INSTANCE = it }
//            }
//        }
//    }
//
//    // Save trip ID
//    fun saveTripId(tripId: String) {
//        prefs.edit().putString(KEY_TRIP_ID, tripId).apply()
//    }
//
//
//    fun saveLoginData(response: LoginResponse) {
//        prefs.edit().apply {
//            putString(KEY_ACCESS_TOKEN, response.accessToken)
//            putString(KEY_REFRESH_TOKEN, response.refreshToken)
//            putString(KEY_ID_TOKEN, response.idToken)
//            putInt(KEY_SALES_EXECUTIVE_ID, response.userId)
//            putBoolean(KEY_IS_LOGGED_IN_MAIN, true)
//
//            apply()
//        }
//    }
//
//    fun getUserId(): Int? = prefs.getInt(KEY_USER_ID, -1).takeIf { it != -1 }
//    fun getUserToken(): String? = prefs.getString(KEY_USER_TOKEN, null)
//    // this is my main application login
//    fun isLoggedInMain(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN_MAIN, false)
//
//
//    fun getTripId(): String? {
//        return prefs.getString(KEY_TRIP_ID, null)
//    }
//
//    // Save trip start time
//    fun saveTripStartTime(startTime: String) {
//        prefs.edit().putString(KEY_TRIP_START_TIME, startTime).apply()
//    }
//
//    fun getTripStartTime(): String? {
//        return prefs.getString(KEY_TRIP_START_TIME, null)
//    }
//
//    // Save trip status
//    fun saveTripStatus(status: String) {
//        prefs.edit().putString(KEY_TRIP_STATUS, status).apply()
//    }
//
//    fun getTripStatus(): String? {
//        return prefs.getString(KEY_TRIP_STATUS, null)
//    }
//
//    // Save start coordinates
//    fun saveStartCoordinates(latitude: Double, longitude: Double) {
//        val coordinates = "$latitude,$longitude"
//        prefs.edit().putString(KEY_TRIP_START_COORDINATES, coordinates).apply()
//    }
//
//    fun getStartCoordinates(): String? {
//        return prefs.getString(KEY_TRIP_START_COORDINATES, null)
//    }
//
//    // Save sales executive ID
//    fun saveSalesExecutiveId(salesExecutiveId: String) {
//        prefs.edit().putString(KEY_SALES_EXECUTIVE_ID, salesExecutiveId).apply()
//    }
//
//    fun getSalesExecutiveId(): Int? {
//        return prefs.getInt(KEY_SALES_EXECUTIVE_ID, 0)
//    }
//
//    // Trip active status
//    fun setTripActive(isActive: Boolean) {
//        prefs.edit().putBoolean(KEY_IS_TRIP_ACTIVE, isActive).apply()
//    }
//
//    fun isTripActive(): Boolean {
//        return prefs.getBoolean(KEY_IS_TRIP_ACTIVE, false)
//    }
//
//    // Save complete trip data object
//    fun saveCurrentTrip(tripData: TripData) {
//        val json = gson.toJson(tripData)
//        prefs.edit().putString(KEY_CURRENT_TRIP_DATA, json).apply()
//    }
//
//    fun getCurrentTrip(): TripData? {
//        val json = prefs.getString(KEY_CURRENT_TRIP_DATA, null)
//        return if (json != null) {
//            gson.fromJson(json, TripData::class.java)
//        } else null
//    }
//
//    // Clear all trip data (when trip ends)
//    fun clearTripData() {
//        prefs.edit().remove(KEY_TRIP_ID).apply()
//        prefs.edit().remove(KEY_TRIP_START_TIME).apply()
//        prefs.edit().remove(KEY_TRIP_STATUS).apply()
//        prefs.edit().remove(KEY_TRIP_START_COORDINATES).apply()
//        prefs.edit().remove(KEY_IS_TRIP_ACTIVE).apply()
//        prefs.edit().remove(KEY_CURRENT_TRIP_DATA).apply()
//    }
//
//    fun isLoggedIn(): Boolean {
//        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
//        if (!isLoggedIn) return false
//
//        val loginUtcTimestamp = prefs.getLong("login_utc_timestamp", 0)
//        if (loginUtcTimestamp == 0L) return false
//
//        // Get UTC date for login timestamp
//        val loginUtcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
//            timeInMillis = loginUtcTimestamp
//        }
//        val loginDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
//            timeZone = TimeZone.getTimeZone("UTC")
//        }.format(loginUtcCalendar.time)
//
//        // Get today's UTC date
//        val todayUtcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
//        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
//            timeZone = TimeZone.getTimeZone("UTC")
//        }.format(todayUtcCalendar.time)
//
//        return loginDate == todayDate
//    }
//
//
//    fun clearAllData() {
//
//        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
//        val loginTimestamp = prefs.getLong("login_utc_timestamp", 0L)
//        val loginDate = prefs.getString("login_date", null)
//
//        prefs.edit().clear().apply()
//
//        prefs.edit().apply {
//            putBoolean("is_logged_in", isLoggedIn)
//            putLong("login_utc_timestamp", loginTimestamp)
//
//            loginDate?.let {
//                putString("login_date", it)
//            }
//
//            apply()
//        }
//    }
//
//
//    // this is attendence login
//    fun setLoggedIn(isLoggedIn: Boolean) {
//        prefs.edit().putBoolean("is_logged_in", isLoggedIn).apply()
//        if (isLoggedIn) {
//            // Store UTC timestamp of login
//            val utcTimestamp = System.currentTimeMillis() // This is already UTC
//            prefs.edit().putLong("login_utc_timestamp", utcTimestamp).apply()
//        } else {
//            prefs.edit().remove("login_utc_timestamp").apply()
//            prefs.edit().remove("login_date").apply()
//        }
//    }
//
//    fun isOnBreak(): Boolean {
//        return prefs.getBoolean("is_on_break", false)
//    }
//
//    fun setOnBreak(onBreak: Boolean) {
//        prefs.edit().putBoolean("is_on_break", onBreak).apply()
//    }
//
//
//    fun getAttendanceId(): Int = prefs.getInt("attendance_id", 0)
//    fun setAttendanceId(id: Int) = prefs.edit().putInt("attendance_id", id).apply()
//
//    fun getWorkStartTime(): Long = prefs.getLong("work_start_time", 0)
//    fun setWorkStartTime(time: Long) = prefs.edit().putLong("work_start_time", time).apply()
//
//    fun getBreakCount(): Int = prefs.getInt("break_count", 0)
//    fun setBreakCount(count: Int) = prefs.edit().putInt("break_count", count).apply()
//
//    fun saveBreakData(breakData: BreakData) {
//        val breaksJson = getBreaksList().toMutableList()
//        breaksJson.add(breakData)
//        val gson = Gson()
//        val json = gson.toJson(breaksJson)
//        prefs.edit().putString("breaks_list", json).apply()
//    }
//
//    fun getBreaksList(): List<BreakData> {
//        val json = prefs.getString("breaks_list", "[]")
//        val gson = Gson()
//        val type = object : TypeToken<List<BreakData>>() {}.type
//        return gson.fromJson(json, type)
//    }
//
//
//    fun getBreakStartTime(): Long {
//        return prefs.getLong("break_start_time", 0)
//    }
//
//    fun setBreakStartTime(time: Long) {
//        prefs.edit().putLong("break_start_time", time).apply()
//    }
//
//    fun setBreakEndTime(time: Long) {
//        prefs.edit().putLong("break_end_time", time).apply()
//    }
//
//    fun getLoginTime(): Long {
//        return prefs.getLong("login_time", 0)
//    }
//
//    fun updateBreakData(breakData: BreakData) {
//        val breaksList = getBreaksList().toMutableList()
//        val index = breaksList.indexOfFirst { it.breakId == breakData.breakId }
//        if (index != -1) {
//            breaksList[index] = breakData
//            val json = gson.toJson(breaksList)
//            prefs.edit().putString("breaks_list", json).apply()
//        }
//    }
//
//
//    fun clearAttendanceData() {
//        prefs.edit()
////            .remove("is_logged_in")
//            .remove("is_on_break")
//            .remove("attendance_id")
//            .remove("work_start_time")
//            .remove("break_count")
//            .remove("break_start_time")
//            .remove("break_end_time")
//            .remove("login_time")
//            .remove("logout_time")
//            .remove("breaks_list")
//            .apply()
//    }
//
//
//
//
//
//
//
//
//
//
//
//    // Add these methods to PreferenceManager class
//
//    fun getLastLoginDate(): String? = prefs.getString("last_login_date", null)
//    fun setLastLoginDate(date: String) = prefs.edit().putString("last_login_date", date).apply()
//
//    fun getLastLoginTime(): String? = prefs.getString("last_login_time", null)
//    fun setLastLoginTime(time: String) = prefs.edit().putString("last_login_time", time).apply()
//
//    fun getLastLoginCoordinates(): String? = prefs.getString("last_login_coordinates", null)
//    fun setLastLoginCoordinates(coordinates: String) = prefs.edit().putString("last_login_coordinates", coordinates).apply()
//
//    fun getLastAttendanceId(): Int = prefs.getInt("last_attendance_id", 0)
//    fun setLastAttendanceId(id: Int) = prefs.edit().putInt("last_attendance_id", id).apply()
//
//    fun setLoginTime(time: Long) = prefs.edit().putLong("login_time", time).apply()
//
//
//
//
//}



class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("trip_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        // ============= MAIN APP LOGIN KEYS =============
        private const val KEY_IS_LOGGED_IN_MAIN = "is_logged_in_main"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ID_TOKEN = "id_token"
        private const val KEY_SALES_EXECUTIVE_ID = "sales_executive_id"

        // ============= TRIP MANAGEMENT KEYS =============
        private const val KEY_TRIP_ID = "trip_id"
        private const val KEY_TRIP_START_TIME = "trip_start_time"
        private const val KEY_TRIP_STATUS = "trip_status"
        private const val KEY_TRIP_START_COORDINATES = "trip_start_coordinates"
        private const val KEY_IS_TRIP_ACTIVE = "is_trip_active"
        private const val KEY_CURRENT_TRIP_DATA = "current_trip_data"

        // ============= ATTENDANCE LOGIN KEYS =============
        private const val KEY_IS_ATTENDANCE_LOGGED_IN = "is_attendance_logged_in"
        private const val KEY_ATTENDANCE_ID = "attendance_id"
        private const val KEY_ATTENDANCE_LOGIN_UTC_TIMESTAMP = "attendance_login_utc_timestamp"
        private const val KEY_ATTENDANCE_LOGIN_DATE = "attendance_login_date"
        private const val KEY_IS_ON_BREAK = "is_on_break"
        private const val KEY_WORK_START_TIME = "work_start_time"
        private const val KEY_BREAK_COUNT = "break_count"
        private const val KEY_BREAK_START_TIME = "break_start_time"
        private const val KEY_BREAK_END_TIME = "break_end_time"
        private const val KEY_BREAKS_LIST = "breaks_list"
        private const val KEY_LOGIN_TIME = "login_time"
        private const val KEY_LOGOUT_TIME = "logout_time"

        // ============= LAST SESSION INFO (Persists after logout) =============
        private const val KEY_LAST_ATTENDANCE_ID = "last_attendance_id"
        private const val KEY_LAST_LOGIN_DATE = "last_login_date"
        private const val KEY_LAST_LOGIN_TIME = "last_login_time"
        private const val KEY_LAST_LOGIN_COORDINATES = "last_login_coordinates"

        private const val KEY_PREVIOUS_SESSION_DIALOG_SHOWN = "previous_session_dialog_shown"
        private const val KEY_PREVIOUS_SESSION_HANDLED = "previous_session_handled"

        private const val KEY_HAS_INCOMPLETE_PREVIOUS_SESSION = "has_incomplete_previous_session"

        @Volatile
        private var INSTANCE: PreferenceManager? = null

        fun getInstance(context: Context): PreferenceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferenceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ============= MAIN APP LOGIN METHODS =============

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

    fun isLoggedInMain(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN_MAIN, false)

    fun getSalesExecutiveId(): Int? = prefs.getInt(KEY_SALES_EXECUTIVE_ID, 0).takeIf { it != 0 }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)
    fun getIdToken(): String? = prefs.getString(KEY_ID_TOKEN, null)

    fun clearMainLoginData() {
        prefs.edit().apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_ID_TOKEN)
            remove(KEY_SALES_EXECUTIVE_ID)
            putBoolean(KEY_IS_LOGGED_IN_MAIN, false)
            apply()
        }
    }

    fun clearLastSessionData() {
        prefs.edit().apply {
            remove(KEY_LAST_ATTENDANCE_ID)
            remove(KEY_LAST_LOGIN_DATE)
            remove(KEY_LAST_LOGIN_TIME)
            remove(KEY_LAST_LOGIN_COORDINATES)
            apply()
        }
    }

    // ============= TRIP MANAGEMENT METHODS =============

    fun saveTripId(tripId: String) {
        prefs.edit().putString(KEY_TRIP_ID, tripId).apply()
    }

    fun getTripId(): String? {
        return prefs.getString(KEY_TRIP_ID, null)
    }

    fun saveTripStartTime(startTime: String) {
        prefs.edit().putString(KEY_TRIP_START_TIME, startTime).apply()
    }

    fun getTripStartTime(): String? {
        return prefs.getString(KEY_TRIP_START_TIME, null)
    }

    fun saveTripStatus(status: String) {
        prefs.edit().putString(KEY_TRIP_STATUS, status).apply()
    }

    fun getTripStatus(): String? {
        return prefs.getString(KEY_TRIP_STATUS, null)
    }

    fun saveStartCoordinates(latitude: Double, longitude: Double) {
        val coordinates = "$latitude,$longitude"
        prefs.edit().putString(KEY_TRIP_START_COORDINATES, coordinates).apply()
    }

    fun getStartCoordinates(): String? {
        return prefs.getString(KEY_TRIP_START_COORDINATES, null)
    }

    fun setTripActive(isActive: Boolean) {
        prefs.edit().putBoolean(KEY_IS_TRIP_ACTIVE, isActive).apply()
    }

    fun isTripActive(): Boolean {
        return prefs.getBoolean(KEY_IS_TRIP_ACTIVE, false)
    }

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

    fun clearTripData() {
        prefs.edit().apply {
            remove(KEY_TRIP_ID)
            remove(KEY_TRIP_START_TIME)
            remove(KEY_TRIP_STATUS)
            remove(KEY_TRIP_START_COORDINATES)
            remove(KEY_IS_TRIP_ACTIVE)
            remove(KEY_CURRENT_TRIP_DATA)
            apply()
        }
    }

    // ============= ATTENDANCE LOGIN METHODS =============

    fun isAttendanceLoggedIn(): Boolean {
        val isLoggedIn = prefs.getBoolean(KEY_IS_ATTENDANCE_LOGGED_IN, false)
        if (!isLoggedIn) return false

        val loginUtcTimestamp = prefs.getLong(KEY_ATTENDANCE_LOGIN_UTC_TIMESTAMP, 0)
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

    fun setAttendanceLoggedIn(isLoggedIn: Boolean) {
        prefs.edit().putBoolean(KEY_IS_ATTENDANCE_LOGGED_IN, isLoggedIn).apply()
        if (isLoggedIn) {
            val utcTimestamp = System.currentTimeMillis()
            prefs.edit().putLong(KEY_ATTENDANCE_LOGIN_UTC_TIMESTAMP, utcTimestamp).apply()
        } else {
            prefs.edit().remove(KEY_ATTENDANCE_LOGIN_UTC_TIMESTAMP).apply()
        }
    }

    fun getAttendanceId(): Int = prefs.getInt(KEY_ATTENDANCE_ID, 0)
    fun setAttendanceId(id: Int) = prefs.edit().putInt(KEY_ATTENDANCE_ID, id).apply()

    fun isOnBreak(): Boolean = prefs.getBoolean(KEY_IS_ON_BREAK, false)
    fun setOnBreak(onBreak: Boolean) = prefs.edit().putBoolean(KEY_IS_ON_BREAK, onBreak).apply()

    fun getWorkStartTime(): Long = prefs.getLong(KEY_WORK_START_TIME, 0)
    fun setWorkStartTime(time: Long) = prefs.edit().putLong(KEY_WORK_START_TIME, time).apply()

    fun getBreakCount(): Int = prefs.getInt(KEY_BREAK_COUNT, 0)
    fun setBreakCount(count: Int) = prefs.edit().putInt(KEY_BREAK_COUNT, count).apply()

    fun getBreakStartTime(): Long = prefs.getLong(KEY_BREAK_START_TIME, 0)
    fun setBreakStartTime(time: Long) = prefs.edit().putLong(KEY_BREAK_START_TIME, time).apply()

    fun setBreakEndTime(time: Long) = prefs.edit().putLong(KEY_BREAK_END_TIME, time).apply()

    fun saveBreakData(breakData: BreakData) {
        val breaksJson = getBreaksList().toMutableList()
        breaksJson.add(breakData)
        val json = gson.toJson(breaksJson)
        prefs.edit().putString(KEY_BREAKS_LIST, json).apply()
    }

    fun getBreaksList(): List<BreakData> {
        val json = prefs.getString(KEY_BREAKS_LIST, "[]")
        val type = object : TypeToken<List<BreakData>>() {}.type
        return gson.fromJson(json, type)
    }

    fun updateBreakData(breakData: BreakData) {
        val breaksList = getBreaksList().toMutableList()
        val index = breaksList.indexOfFirst { it.breakId == breakData.breakId }
        if (index != -1) {
            breaksList[index] = breakData
            val json = gson.toJson(breaksList)
            prefs.edit().putString(KEY_BREAKS_LIST, json).apply()
        }
    }

    fun setLoginTime(time: Long) = prefs.edit().putLong(KEY_LOGIN_TIME, time).apply()
    fun getLoginTime(): Long = prefs.getLong(KEY_LOGIN_TIME, 0)

    fun setLogoutTime(time: Long) = prefs.edit().putLong(KEY_LOGOUT_TIME, time).apply()

    // ============= LAST SESSION INFO (Persists after logout) =============

    fun getLastAttendanceId(): Int = prefs.getInt(KEY_LAST_ATTENDANCE_ID, 0)
    fun setLastAttendanceId(id: Int) = prefs.edit().putInt(KEY_LAST_ATTENDANCE_ID, id).apply()

    fun getLastLoginDate(): String? = prefs.getString(KEY_LAST_LOGIN_DATE, null)
    fun setLastLoginDate(date: String) = prefs.edit().putString(KEY_LAST_LOGIN_DATE, date).apply()

    fun getLastLoginTime(): String? = prefs.getString(KEY_LAST_LOGIN_TIME, null)
    fun setLastLoginTime(time: String) = prefs.edit().putString(KEY_LAST_LOGIN_TIME, time).apply()

    fun getLastLoginCoordinates(): String? = prefs.getString(KEY_LAST_LOGIN_COORDINATES, null)
    fun setLastLoginCoordinates(coordinates: String) = prefs.edit().putString(KEY_LAST_LOGIN_COORDINATES, coordinates).apply()

    // ============= CLEAR METHODS =============

    fun clearAttendanceData() {
        prefs.edit().apply {
            remove(KEY_IS_ATTENDANCE_LOGGED_IN)
            remove(KEY_ATTENDANCE_ID)
            remove(KEY_ATTENDANCE_LOGIN_UTC_TIMESTAMP)
            remove(KEY_ATTENDANCE_LOGIN_DATE)
            remove(KEY_IS_ON_BREAK)
            remove(KEY_WORK_START_TIME)
            remove(KEY_BREAK_COUNT)
            remove(KEY_BREAK_START_TIME)
            remove(KEY_BREAK_END_TIME)
            remove(KEY_BREAKS_LIST)
            remove(KEY_LOGIN_TIME)
            remove(KEY_LOGOUT_TIME)
            apply()
        }
        // Don't clear last session info - it persists for display
    }

    // In PreferenceManager.kt - Add these constants


    // Add these methods
    fun isPreviousSessionDialogShown(): Boolean {
        return prefs.getBoolean(KEY_PREVIOUS_SESSION_DIALOG_SHOWN, false)
    }

    fun setPreviousSessionDialogShown(shown: Boolean) {
        prefs.edit().putBoolean(KEY_PREVIOUS_SESSION_DIALOG_SHOWN, shown).apply()
    }

    fun isPreviousSessionHandled(): Boolean {
        return prefs.getBoolean(KEY_PREVIOUS_SESSION_HANDLED, false)
    }

    fun setPreviousSessionHandled(handled: Boolean) {
        prefs.edit().putBoolean(KEY_PREVIOUS_SESSION_HANDLED, handled).apply()
    }

    fun hasIncompletePreviousSession(): Boolean {
        return prefs.getBoolean(KEY_HAS_INCOMPLETE_PREVIOUS_SESSION, false)
    }

    fun setHasIncompletePreviousSession(hasIncomplete: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_INCOMPLETE_PREVIOUS_SESSION, hasIncomplete).apply()
    }

    fun clearPreviousSessionFlags() {
        prefs.edit().apply {
            remove(KEY_PREVIOUS_SESSION_DIALOG_SHOWN)
            remove(KEY_PREVIOUS_SESSION_HANDLED)
            remove(KEY_HAS_INCOMPLETE_PREVIOUS_SESSION)
            apply()
        }
    }

    fun clearAllData() {
        prefs.edit().clear().apply()
    }
}

data class TripData(
    val tripId: String,
    val startTime: String,
    val status: String,
    val startCoordinates: String,
    val salesExecutiveId: String
)