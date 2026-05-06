package com.example.salesstysetgps.domain.repository

import android.util.Log
import com.example.salesstysetgps.data.api.ApiService
import com.example.salesstysetgps.models.LeadsResponse
import com.example.salesstysetgps.models.PaginationRequest
import com.example.salesstysetgps.models.PresignedUrlResponse
import com.example.salesstysetgps.models.Resource
import com.example.salesstysetgps.util.PreferenceManager
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

// LeadRepository.kt
class LeadRepository(
    private val apiService: ApiService,
    private val preferenceManager: PreferenceManager
) {


    suspend fun fetchLeadsPage(
        page: Int,
        limit: Int,
        sellingType: String? = null,
        status: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        startDate: String?=null,
        endDate: String?=null
    ): Resource<LeadsResponse> {
        return try {
            val salesExecutiveId = preferenceManager.getSalesExecutiveId()
                ?: return Resource.Error("Sales Executive ID not found. Please login first.")

            val request = PaginationRequest(
                page = page.toString(),
                limit = limit.toString(),
                salesExecutiveId = salesExecutiveId.toString(),
                sellingType = sellingType,
                status = status,
                sortBy = sortBy,
                sortOrder = sortOrder,
                startDate = startDate,
                endDate = endDate
            )

            // Only include non-null parameters in the request body
            val response = withContext(Dispatchers.IO) {
                apiService.getSellLeads(request)
            }

            if (response.success == 1) {
                Resource.Success(response)
            } else {
                Resource.Error(response.message ?: "Failed to fetch leads")
            }
        } catch (e: Exception) {
            Resource.Error("Error: ${e.message ?: "Unknown error"}")
        }
    }


    suspend fun getPresignedUrl(
        bucketName: String,
        fieldLabels: String,
        objectKeys: String
    ): Resource<PresignedUrlResponse> {
        return try {
            val responseMap = withContext(Dispatchers.IO) {
                apiService.getPresignedUrlTnc(bucketName, fieldLabels, objectKeys)
            }

            Log.d("PresignedUrl", "Raw Response Map: $responseMap")

            val resultMsg = responseMap["resultMsg"] ?: ""

            // Find the dynamic key (any key that's not "resultMsg")
            var presignedUrl = ""
            var objectKey = ""

            for ((key, value) in responseMap) {
                if (key != "resultMsg") {
                    objectKey = key
                    presignedUrl = value
                    break
                }
            }

            Log.d("PresignedUrl", "Result Message: $resultMsg")
            Log.d("PresignedUrl", "Object Key: $objectKey")
            Log.d("PresignedUrl", "Presigned URL: $presignedUrl")

            val parsedResponse = PresignedUrlResponse(
                resultMsg = resultMsg,
                presignedUrl = presignedUrl,
                objectKey = objectKey
            )

            if (parsedResponse.presignedUrl.isNotEmpty()) {
                Resource.Success(parsedResponse)
            } else {
                Resource.Error(parsedResponse.resultMsg ?: "Failed to get presigned URL")
            }
        } catch (e: SocketTimeoutException) {
            Resource.Error("Connection timeout. Please try again.")
        } catch (e: HttpException) {
            Resource.Error(parseHttpError(e))
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message ?: "Please check your connection"}")
        } catch (e: Exception) {
            Log.e("PresignedUrl", "Error in getPresignedUrl", e)
            Resource.Error("Error: ${e.message ?: "Unknown error"}")
        }
    }


    suspend fun searchLeads(
        query: String,
        page: Int,
        limit: Int,
        sortBy: String? = null,
        sortOrder: String? = null,
        status: String?=null,
        startDate: String?=null,endDate: String?=null
    ): Resource<LeadsResponse> {
        return try {
            val salesExecutiveId = preferenceManager.getSalesExecutiveId()
                ?: return Resource.Error("Sales Executive ID not found. Please login first.")

            // Use the correct parameter names
            val request = PaginationRequest(
                page = page.toString(),
                limit = limit.toString(),
                salesExecutiveId = salesExecutiveId.toString(),
                search = query,  // Use 'search' instead of 'query'
                sellingType = "",  // Can be null
                status = status,  // Can be null
                sortBy = sortBy,
                sortOrder = sortOrder,
                startDate = startDate,
                endDate = endDate
            )

            val response = withContext(Dispatchers.IO) {
                apiService.getSellLeads(request)
            }

            if (response.success == 1) {
                Resource.Success(response)
            } else {
                Resource.Error(response.message ?: "Failed to fetch leads")
            }
        } catch (e: Exception) {
            Resource.Error("Error: ${e.message ?: "Unknown error"}")
        }
    }


    private fun parsePresignedUrlResponse(response: PresignedUrlResponse): PresignedUrlResponse {
        return try {
            val gson = Gson()
            val jsonString = gson.toJson(response)
            val jsonObject = JsonParser.parseString(jsonString).asJsonObject

            val resultMsg = jsonObject["resultMsg"]?.asString ?: ""

            // Remove resultMsg to get the dynamic URL entry
            val dynamicEntry = jsonObject.entrySet().firstOrNull { it.key != "resultMsg" }

            val presignedUrl = if (dynamicEntry != null) {
                dynamicEntry.value.asString
            } else {
                ""
            }

            val objectKey = if (dynamicEntry != null) {
                dynamicEntry.key
            } else {
                ""
            }

            Log.d("PresignedUrl", "Result Message: $resultMsg")
            Log.d("PresignedUrl", "Dynamic Key (Object Key): $objectKey")
            Log.d("PresignedUrl", "Presigned URL: $presignedUrl")

            PresignedUrlResponse(
                resultMsg = resultMsg,
                presignedUrl = presignedUrl,
                objectKey = objectKey
            )
        } catch (e: Exception) {
            Log.e("PresignedUrl", "Error parsing response", e)
            response
        }
    }


    private fun parseHttpError(exception: HttpException): String {
        return try {
            val errorBody = exception.response()?.errorBody()?.string()
            // Try to parse error message from response
            if (!errorBody.isNullOrEmpty()) {
                // You can parse JSON error response here
                "Server error: ${exception.code()}"
            } else {
                "Server error: ${exception.message()}"
            }
        } catch (e: Exception) {
            "Network error occurred"
        }
    }
}