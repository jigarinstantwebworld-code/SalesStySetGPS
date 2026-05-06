package com.example.salesstysetgps.data.api


import com.example.salesstysetgps.models.AttendanceRequest
import com.example.salesstysetgps.models.AttendanceResponse
import com.example.salesstysetgps.models.CreateLeadRequest
import com.example.salesstysetgps.models.CreateLeadResponse
import com.example.salesstysetgps.models.CreatePostRequest
import com.example.salesstysetgps.models.EndTripRequest
import com.example.salesstysetgps.models.EndTripResponse
import com.example.salesstysetgps.models.LeadDetailRequest
import com.example.salesstysetgps.models.LeadDetailResponse
import com.example.salesstysetgps.models.LeadsResponse
import com.example.salesstysetgps.models.LoginRequest
import com.example.salesstysetgps.models.LoginResponse
import com.example.salesstysetgps.models.NotesListRequest
import com.example.salesstysetgps.models.NotesListResponse
import com.example.salesstysetgps.models.PaginatedLeads
import com.example.salesstysetgps.models.PaginationRequest
import com.example.salesstysetgps.models.Post
import com.example.salesstysetgps.models.PresignedUrlResponse
import com.example.salesstysetgps.models.StartTripRequest
import com.example.salesstysetgps.models.StartTripResponse
import com.example.salesstysetgps.ui.AddNoteRequest
import com.example.salesstysetgps.ui.AddNoteResponse
import com.example.salesstysetgps.workers.StopSyncRequest
import com.example.salesstysetgps.workers.SyncResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("posts")
    suspend fun getPosts(): List<Post>

    @GET("posts/{id}")
    suspend fun getPost(@Path("id") id: Int): Post

    @POST("posts")
    suspend fun createPost(@Body request: CreatePostRequest): Post

    @PUT("posts/{id}")
    suspend fun updatePost(@Path("id") id: Int, @Body request: CreatePostRequest): Post

    @DELETE("posts/{id}")
    suspend fun deletePost(@Path("id") id: Int): Response<Unit>

    @POST("salesexecutive-trip-add-update")
    suspend fun startTrip(@Body request: StartTripRequest): StartTripResponse

    @POST("salesexecutive-trip-add-update")
    suspend fun endTrip(@Body request: EndTripRequest): EndTripResponse

    @POST("salesexecutive-attendance")
    suspend fun manageAttendance(@Body request: AttendanceRequest?): AttendanceResponse

    @POST("salesexecutive-get-sale-leads")
    suspend fun getSellLeads(
        @Body request: PaginationRequest
    ): LeadsResponse


    @POST("salesexecutive-sale-leads-add-update")
    suspend fun createSellLead(
        @Body request: CreateLeadRequest
    ): CreateLeadResponse



    @GET("put-presigned-url")
    suspend fun getPresignedUrlTnc(
        @Query("bucket_name") bucketName: String,
        @Query("field_labels") fieldLabels: String,
        @Query("object_keys") objectKeys: String
    ): Map<String, String>




    @POST("salesexecutive-places-add")
    suspend fun syncStops(
        @Body request: StopSyncRequest
    ): SyncResponse

    @POST("admin-vendor-login")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse

    @POST("salesexecutive-get-sale-leads")
    suspend fun getLeadDetail(
        @Body request: LeadDetailRequest
    ): LeadDetailResponse

    @GET("get-presigned-url")
    suspend fun getPresignedUrlForImage(
        @Query("bucket_name") bucketName: String,
        @Query("field_labels") fieldName: String,
        @Query("object_keys") objectKey: String
    ): Map<String, String>


    @POST("salesexecutive-get-sale-leads")
    suspend fun searchLeads(
        @Body request: LeadDetailRequest
    ): LeadsResponse


    @POST("salesexecutive-sale-leads-notes-add")  // Your endpoint
    suspend fun addNote(@Body request: AddNoteRequest): Response<AddNoteResponse>


    @POST("salesexecutive-sale-leads-notes-list-detail")
    suspend fun getNotesList(@Body request: NotesListRequest): Response<NotesListResponse>

}


data class StopSyncData(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val startTime: Long,
    val endTime: Long?,
    val duration: Long,
    val letter: String
)