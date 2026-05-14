package com.styset.sales.app.repository


import android.util.Log
import com.styset.sales.app.data.api.ApiService
import com.styset.sales.app.models.AttendanceRequest
import com.styset.sales.app.models.AttendanceResponse
import com.styset.sales.app.models.EndTripRequest
import com.styset.sales.app.models.EndTripResponse
import com.styset.sales.app.models.LeadsResponse
import com.styset.sales.app.models.PaginationRequest
import com.styset.sales.app.models.Resource
import com.styset.sales.app.models.StartTripRequest
import com.styset.sales.app.models.StartTripResponse
import com.styset.sales.app.util.PreferenceManager
import com.styset.sales.app.util.TripData
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TripRepository(
    private val apiService: ApiService,
    private val preferenceManager: PreferenceManager
) {

    fun startTrip(
        salesExecutiveId: String,
        startTime: String,
        status: String,
        latitude: Double,
        longitude: Double
    ): Flow<Resource<StartTripResponse>> = flow {
        emit(Resource.Loading())
        try {
            val coordinates = "$latitude,$longitude"
            val request = StartTripRequest(salesExecutiveId, startTime, status, coordinates)

            val response = withContext(Dispatchers.IO) {
                apiService.startTrip(request)
            }

            // Success case
            if (response.success == 1 && response.data != null) {
                preferenceManager.saveTripId(response.data.tripId.toString())
                preferenceManager.saveTripStartTime(response.data.startTime)
                preferenceManager.saveTripStatus(response.data.status)
                preferenceManager.saveStartCoordinates(latitude, longitude)
//                preferenceManager.saveSalesExecutiveId(salesExecutiveId)
                preferenceManager.setTripActive(true)

                preferenceManager.saveCurrentTrip(
                    TripData(
                        tripId = response.data.tripId.toString(),
                        startTime = response.data.startTime,
                        status = response.data.status,
                        startCoordinates = response.data.startCoordinates,
                        salesExecutiveId = salesExecutiveId
                    )
                )
                emit(Resource.Success(response))
            } else {
                // API returned error (success = 0)
//                emit(Resource.Error(response.message ?: "Failed to start trip"))

                if (response.message?.contains("already has an ongoing trip") == true) {
                    emit(
                        Resource.Error(
                        message = response.message,
                        onGoingTripId = response.onGoingTripId
                    ))
                } else {
                    emit(Resource.Error(message = response.message ?: "Failed to start trip"))
                }
            }

        } catch (e: SocketTimeoutException) {
            emit(Resource.Error("Connection timeout. Please try again."))
        } catch (e: HttpException) {
            // Handle HTTP errors (404, 500, etc.)
//            val errorMessage = parseHttpError(e)
//            emit(Resource.Error(errorMessage))

            val errorMsg = parseHttpError(e)
            val ongoingTripId = extractOngoingTripIdFromError(e)
            emit(Resource.Error(errorMsg, onGoingTripId = ongoingTripId))
        } catch (e: IOException) {
            emit(Resource.Error("Network error: ${e.message ?: "Please check your connection"}"))
        } catch (e: Exception) {
            emit(Resource.Error("Error: ${e.message ?: "Unknown error"}"))
        }
    }

    private fun extractOngoingTripIdFromError(exception: HttpException): Int? {
        return try {
            val errorBody = exception.response()?.errorBody()?.string()
            if (!errorBody.isNullOrEmpty()) {
                val jsonObject = JSONObject(errorBody)
                if (jsonObject.has("ongoing_trip_id")) {
                    jsonObject.getInt("ongoing_trip_id")
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun endTrip(
        salesExecutiveId: String,
        tripId: Int,
        latitude: Double,
        longitude: Double,
    ): Flow<Resource<EndTripResponse>> = flow {
        emit(Resource.Loading())
        try {
            val dateFormat = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                Locale.getDefault()
            )
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val endTime = dateFormat.format(Date())
            val endCoordinates = "$latitude,$longitude"

            val request = EndTripRequest(salesExecutiveId, tripId, endTime, endCoordinates)

            val response = withContext(Dispatchers.IO) {
                apiService.endTrip(request)
            }

            if (response.success == 1 && response.data != null) {
                // Clear trip data from preferences
                preferenceManager.clearTripData()
                emit(Resource.Success(response))
            } else {
                emit(Resource.Error(response.message ?: "Failed to end trip"))
            }

        } catch (e: SocketTimeoutException) {
            emit(Resource.Error("Connection timeout. Please try again."))
        } catch (e: HttpException) {
            val errorMessage = parseHttpError(e)
            emit(Resource.Error(errorMessage))
        } catch (e: IOException) {
            emit(Resource.Error("Network error: ${e.message ?: "Please check your connection"}"))
        } catch (e: Exception) {
            emit(Resource.Error("Error: ${e.message ?: "Unknown error"}"))
        }
    }

    fun manageAttendance(
        action: String, // "LOGIN", "BREAK_IN", "BREAK_OUT", "LOGOUT"
        latitude: Double,
        longitude: Double,
        createdBy: String? = null
    ): Flow<Resource<AttendanceResponse>> = flow {
        emit(Resource.Loading())

        try {
            val salesExecutiveId = preferenceManager.getSalesExecutiveId()

            Log.e("TAG", "manageAttendance: ----------${salesExecutiveId}", )
//
            if (salesExecutiveId == null) {
                emit(Resource.Error("Sales Executive ID not found. Please login first."))
                return@flow
            }


            val coordinates = "$latitude,$longitude"

            val request = AttendanceRequest(
                salesExecutiveId = salesExecutiveId,
                action = action,
                coordinates = coordinates,
                createdBy = if (action == "LOGIN") (createdBy ?: "system") else null
            )

            val response = withContext(Dispatchers.IO) {
                apiService.manageAttendance(request)
            }

            if (response.success==1) {
                emit(Resource.Success(response))
            } else {
                emit(Resource.Error(response.message))
            }

        } catch (e: SocketTimeoutException) {
            emit(Resource.Error("Connection timeout. Please try again."))
        } catch (e: HttpException) {
            val errorMessage = parseHttpError(e)
            emit(Resource.Error(errorMessage))
        } catch (e: IOException) {
            emit(Resource.Error("Network error: ${e.message ?: "Please check your connection"}"))
        } catch (e: Exception) {
            emit(Resource.Error("Error: ${e.message ?: "Unknown error"}"))
        }
    }


    suspend fun fetchTodaysLeads(page:String,limit: String,currentDate: String): Resource<LeadsResponse> {
        return try {
            val salesExecutiveId = preferenceManager.getSalesExecutiveId()

            if (salesExecutiveId == null) {
                return Resource.Error("Sales Executive ID not found. Please login first.")
            }

            // Get today's date in YYYY-MM-DD format
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            // You can add query parameter for date filter if your API supports it
            // Otherwise fetch first page with date filter
            val request = PaginationRequest(
                page,
                limit,
                salesExecutiveId.toString(),
                startDate = currentDate,
                endDate = currentDate
            )
            val response = withContext(Dispatchers.IO) {
                apiService.getSellLeads(request)
            }

            Log.d("LeadRepository", "Fetched ${response.data.leads.size} leads")

            if (response.success == 1) {
                Resource.Success(response)
            } else {
                Resource.Error(response.message ?: "Failed to fetch leads")
            }
        } catch (e: SocketTimeoutException) {
            Resource.Error("Connection timeout. Please try again.")
        } catch (e: HttpException) {
            Resource.Error(parseHttpError(e))
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message ?: "Please check your connection"}")
        } catch (e: Exception) {
            Resource.Error("Error: ${e.message ?: "Unknown error"}")
        }
    }

    private fun parseHttpError(e: HttpException): String {
        return try {
            val errorBody = e.response()?.errorBody()?.string()
            if (!errorBody.isNullOrEmpty()) {
                val gson = Gson()
                val jsonObject = gson.fromJson(errorBody, JsonObject::class.java)
                val message = jsonObject.get("message")?.asString
                if (!message.isNullOrEmpty()) {
                    return message // Return server's custom message
                }
            }
            // Fallback to status code message
            when (e.code()) {
                400 -> "Bad request. Please check your input."
                403 -> "Access forbidden."
                401 -> "Unauthorized. Please login again."
                404 -> "API endpoint not found. Please check the URL."
                500 -> "Server error. Please try again later."
                else -> "HTTP Error: ${e.code()}"
            }
        } catch (ex: Exception) {
            "Network error: ${e.message}"
        }
    }
}