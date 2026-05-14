package com.styset.sales.app.models

import com.google.gson.annotations.SerializedName

data class StartTripResponse(
    @SerializedName("success")
    val success: Int,  // Changed from Boolean to Int (1 for success, 0 for failure)

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("ongoing_trip_id")
    val onGoingTripId : Int?=null,

    @SerializedName("data")
    val data: TripDataResponse? = null
)
