package com.styset.sales.app.data.network

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.styset.sales.app.BuildConfig
import com.styset.sales.app.data.api.ApiService
import com.styset.sales.app.workers.StopRequestData
import com.styset.sales.app.workers.StopSyncRequest
import com.styset.sales.app.workers.SyncResponse
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

object NetworkClient {

    private const val BASE_URL = "https://bt09kmb8yb.execute-api.us-east-1.amazonaws.com/shopnowee/"

    private val defaultGson = GsonBuilder()
        .create()


    private val placesApiGson  = GsonBuilder()
        .registerTypeAdapter(StopRequestData::class.java, object : JsonSerializer<StopRequestData> {
            override fun serialize(
                src: StopRequestData,
                typeOfSrc: java.lang.reflect.Type,
                context: JsonSerializationContext
            ): JsonElement {
                val jsonObject = JsonObject()

                src.id?.let { jsonObject.addProperty("id", it) }
                src.mappingId?.let { jsonObject.addProperty("mapping_id", it) }
                jsonObject.addProperty("name", src.name)
                jsonObject.addProperty("coordinates", src.coordinates)
                jsonObject.addProperty("startTime", src.startTime)

                // ✅ ALWAYS add endTime field, even if null
                if (src.endTime == null) {
                    jsonObject.add("endTime", JsonNull.INSTANCE)
                } else {
                    jsonObject.addProperty("endTime", src.endTime)
                }

                if (src.durationMinutes == null) {
                    jsonObject.add("durationMinutes", JsonNull.INSTANCE)
                } else {
                    jsonObject.addProperty("durationMinutes", src.durationMinutes)
                }

//                jsonObject.addProperty("durationMinutes", src.durationMinutes)

                // Handle nullable fields
                if (src.address == null) {
                    jsonObject.add("address", JsonNull.INSTANCE)
                } else {
                    jsonObject.addProperty("address", src.address)
                }

                if (src.phone == null) {
                    jsonObject.add("phone", JsonNull.INSTANCE)
                } else {
                    jsonObject.addProperty("phone", src.phone)
                }

                jsonObject.addProperty("salesExecutiveId", src.salesExecutiveId)
                jsonObject.addProperty("tripId", src.tripId)

                return jsonObject
            }
        })
        .serializeNulls()
        .create()

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
//            level = if (BuildConfig.DEBUG) {
//                HttpLoggingInterceptor.Level.BODY
//            } else {
//                HttpLoggingInterceptor.Level.NONE
//            }
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }


    private val placesApiRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl((BuildConfig.BASE_URL))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(placesApiGson))
            .build()
    }
    private val defaultRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(defaultGson))
            .build()
    }


    val apiService: ApiService by lazy {
        defaultRetrofit.create(ApiService::class.java)
    }
    val placesApiService: PlacesApiService by lazy {
        placesApiRetrofit.create(PlacesApiService::class.java)
    }
}
interface PlacesApiService {

    @POST("salesexecutive-places-add")
    suspend fun syncStops(@Body request: StopSyncRequest): SyncResponse

}