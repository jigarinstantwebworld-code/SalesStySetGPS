package com.example.salesstysetgps.models

import com.google.gson.annotations.SerializedName

data class StartTripRequest(
    @SerializedName("sales_executive_id")
    val salesExecutiveId: String,

    @SerializedName("start_time")
    val startTime: String,  // Format: "yyyy-MM-dd HH:mm:ss"

    @SerializedName("status")
    val status: String,  // "STARTED", "IN_PROGRESS", etc.

    @SerializedName("start_coordinates")
    val startCoordinates: String  // Format: "latitude,longitude"
)