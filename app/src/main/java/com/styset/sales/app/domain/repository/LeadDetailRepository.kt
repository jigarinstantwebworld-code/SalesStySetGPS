package com.styset.sales.app.domain.repository

import android.util.Log
import com.styset.sales.app.data.api.ApiService
import com.styset.sales.app.models.LeadDetailRequest
import com.styset.sales.app.models.LeadDetailResponse
import com.styset.sales.app.models.NotesData
import com.styset.sales.app.models.NotesListRequest
import com.styset.sales.app.models.PresignedUrlResponse
import com.styset.sales.app.models.Resource
import com.styset.sales.app.ui.AddNoteRequest
import com.styset.sales.app.ui.Note
import com.styset.sales.app.util.PreferenceManager
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.collections.iterator

// LeadDetailRepository.kt
class LeadDetailRepository(
    private val apiService: ApiService,
    private val preferenceManager: PreferenceManager
) {


    suspend fun getNotesList(
        leadId: Int,
        fromDate: String? = null,
        toDate: String? = null,
        search: String? = null,
        sortBy: String = "latest_note_date",
        sortOrder: String = "desc"
    ): Resource<NotesData> {
        return try {
            val salesExecutiveId = preferenceManager.getSalesExecutiveId()

            if (salesExecutiveId == null) {
                return Resource.Error("Sales executive ID not found")
            }

            val request = NotesListRequest(
                from_date = fromDate,
                to_date = toDate,
                sortBy = sortBy,
                sortOrder = sortOrder,
                sale_leads_id = leadId,
                search = search,
                sales_executive_id = salesExecutiveId
            )

            val response = withContext(Dispatchers.IO) {
                apiService.getNotesList(request)
            }

            if (response.code() == 403) {
                val errorBody = response.errorBody()?.string()
                Log.e("LeadDetailRepository", "Access denied: $errorBody")
                return Resource.Error("You don't have access to this lead's notes")
            }

            // Check if response is successful
            if (response.isSuccessful) {
                val apiResponse = response.body()

                // Check the success flag inside the body
                if (apiResponse?.body?.success == 1) {
                    val notesData = apiResponse.body.data
                    if (notesData != null) {
                        Log.d("LeadDetailRepository", "Fetched ${notesData.notes.size} notes for lead $leadId")
                        Log.d("LeadDetailRepository", "Summary: Total notes=${notesData.summary.total_notes}, Pending follow-ups=${notesData.summary.pending_follow_ups}")
                        Resource.Success(notesData)
                    } else {
                        Resource.Error("Notes data not found")
                    }
                } else {
                    val errorMsg = apiResponse?.body?.message ?: "Failed to fetch notes"
                    Log.e("LeadDetailRepository", "API Error: $errorMsg")
                    Resource.Error(errorMsg)
                }
            } else {
                Log.e("LeadDetailRepository", "HTTP Error: ${response.code()}")
                Resource.Error("Server error: ${response.code()}")
            }
        } catch (e: SocketTimeoutException) {
            Log.e("LeadDetailRepository", "Timeout error", e)
            Resource.Error("Connection timeout. Please try again.")
        } catch (e: HttpException) {
            if (e.code() == 403) {
                Resource.Error("You don't have access to this lead's notes")
            } else {
                Resource.Error(parseHttpError(e))
            }
        } catch (e: IOException) {
            Log.e("LeadDetailRepository", "Network error", e)
            Resource.Error("Network error: ${e.message ?: "Please check your connection"}")
        } catch (e: Exception) {
            Log.e("LeadDetailRepository", "Error fetching notes", e)
            Resource.Error("Error: ${e.message ?: "Unknown error"}")
        }
    }



    suspend fun addNote(
        saleLeadsId: Int,
        notes: String,
        nextFollowUp: String? = null,
        locationAddress : String?=null
    ): Resource<Note> {
        return try {
            val salesExecutiveId = preferenceManager.getSalesExecutiveId()

            if (salesExecutiveId == null) {
                return Resource.Error("Sales executive ID not found")
            }

            val request = AddNoteRequest(
                sale_leads_id = saleLeadsId,
                notes = notes,
                next_follow_up = nextFollowUp,
                sales_executive_id = salesExecutiveId,
                current_location=locationAddress
            )

            val response = withContext(Dispatchers.IO) {
                apiService.addNote(request)
            }

            // FIX: Access nested body - response.body()?.body?.success
            if (response.isSuccessful && response.body()?.body?.success == 1) {
                val note = response.body()?.body?.data
                if (note != null) {
                    Log.d("LeadDetailRepository", "Note added successfully: ${note.id}")
                    Resource.Success(note)
                } else {
                    Resource.Error("Note data not found in response")
                }
            } else {
                val errorMsg = response.body()?.body?.message ?: "Failed to add note"
                Log.e("LeadDetailRepository", "Add note failed: $errorMsg")
                Resource.Error(errorMsg)
            }
        } catch (e: SocketTimeoutException) {
            Log.e("LeadDetailRepository", "Timeout error", e)
            Resource.Error("Connection timeout. Please try again.")
        } catch (e: HttpException) {
            Log.e("LeadDetailRepository", "HTTP error", e)
            Resource.Error(parseHttpError(e))
        } catch (e: IOException) {
            Log.e("LeadDetailRepository", "Network error", e)
            Resource.Error("Network error: ${e.message ?: "Please check your connection"}")
        } catch (e: Exception) {
            Log.e("LeadDetailRepository", "Error adding note", e)
            Resource.Error("Error: ${e.message ?: "Unknown error"}")
        }
    }

    suspend fun getLeadDetail(leadId: Int): Resource<LeadDetailResponse> {
        return try {
            val salesExecutiveId = preferenceManager.getSalesExecutiveId()



            val request = LeadDetailRequest(
                id = leadId, salesExecutiveId!!
            )

            val response = withContext(Dispatchers.IO) {
                apiService.getLeadDetail(request)
            }

            if (response.success == 1) {
                Resource.Success(response)
            } else {
                Resource.Error(response.message ?: "Failed to fetch lead details")
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

    suspend fun getPresignedUrl(
        bucketName: String,
        fieldName: String,
        objectKey: String
    ): Resource<String> {
        return try {
            Log.d("LeadDetailRepository", "Fetching presigned URL with:")
            Log.d("LeadDetailRepository", "  bucketName: $bucketName")
            Log.d("LeadDetailRepository", "  fieldName: $fieldName")
            Log.d("LeadDetailRepository", "  objectKey: $objectKey")

            val responseMap = withContext(Dispatchers.IO) {
                apiService.getPresignedUrlForImage(bucketName, fieldName, objectKey)
            }

            Log.d("LeadDetailRepository", "Response Map: $responseMap")

            val resultMsg = responseMap["resultMsg"]
            var presignedUrl = ""
            var dynamicKey = ""

            // Find the dynamic key (any key that's not "resultMsg")
            for ((key, value) in responseMap) {
                if (key != "resultMsg") {
                    dynamicKey = key
                    presignedUrl = value
                    Log.d("LeadDetailRepository", "Found presigned URL for key '$dynamicKey'")
                    break
                }
            }

            if (presignedUrl.isNotEmpty()) {
                Log.d("LeadDetailRepository", "Successfully extracted presigned URL")
                Resource.Success(presignedUrl)
            } else {
                Log.e("LeadDetailRepository", "No presigned URL found. ResultMsg: $resultMsg")
                Resource.Error(resultMsg ?: "Failed to get presigned URL")
            }
        } catch (e: SocketTimeoutException) {
            Resource.Error("Connection timeout. Please try again.")
        } catch (e: HttpException) {
            Resource.Error(parseHttpError(e))
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message ?: "Please check your connection"}")
        } catch (e: Exception) {
            Log.e("LeadDetailRepository", "Error getting presigned URL", e)
            Resource.Error("Error: ${e.message ?: "Unknown error"}")
        }
    }

    // LeadDetailRepository.kt
    private fun extractPresignedUrlFromResponse(response: PresignedUrlResponse): String {
        return try {
            val gson = Gson()
            val jsonString = gson.toJson(response)
            val jsonObject = JsonParser.parseString(jsonString).asJsonObject

            Log.d("PresignedUrl", "Full Response JSON: $jsonString")

            // Find the first entry that's not "resultMsg" (dynamic key)
            for (entry in jsonObject.entrySet()) {
                if (entry.key != "resultMsg") {
                    val url = entry.value.asString
                    Log.d("PresignedUrl", "Found presigned URL for key '${entry.key}': ${url.take(100)}...")
                    return url
                }
            }

            Log.e("PresignedUrl", "No presigned URL found in response")
            ""
        } catch (e: Exception) {
            Log.e("PresignedUrl", "Error extracting URL", e)
            ""
        }
    }

    private fun extractPresignedUrl(response: PresignedUrlResponse): String {
        // Parse the dynamic response to get the presigned URL
        val gson = Gson()
        val jsonString = gson.toJson(response)
        val jsonObject = JsonParser.parseString(jsonString).asJsonObject

        // Find the first entry that's not "resultMsg"
        val entry = jsonObject.entrySet().firstOrNull { it.key != "resultMsg" }
        return entry?.value?.asString ?: ""
    }


    private fun parseHttpError(exception: HttpException): String {
        return try {
            val errorBody = exception.response()?.errorBody()?.string()
            if (!errorBody.isNullOrEmpty()) {
                "Server error: ${exception.code()}"
            } else {
                "Server error: ${exception.message()}"
            }
        } catch (e: Exception) {
            "Network error occurred"
        }
    }
}