package com.styset.sales.app.models

import com.google.gson.annotations.SerializedName

data class EndTripResponse(
    @SerializedName("success")
    val success: Int,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("data")
    val data: EndTripDataResponse? = null
)

data class EndTripDataResponse(
    @SerializedName("trip_id")
    val tripId: Int,

    @SerializedName("end_time")
    val endTime: String,

    @SerializedName("end_coordinates")
    val endCoordinates: String,

    @SerializedName("modified_by")
    val modifiedBy: String
)
