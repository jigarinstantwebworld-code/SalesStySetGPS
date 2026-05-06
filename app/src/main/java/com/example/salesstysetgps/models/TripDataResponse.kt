package com.example.salesstysetgps.models

import com.google.gson.annotations.SerializedName

data class TripDataResponse(
    @SerializedName("trip_id")
    val tripId: Int,  // Note: Int, not String

    @SerializedName("start_time")
    val startTime: String,

    @SerializedName("start_coordinates")
    val startCoordinates: String,

    @SerializedName("status")
    val status: String
)
