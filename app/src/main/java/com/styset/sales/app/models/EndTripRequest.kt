package com.styset.sales.app.models

import com.google.gson.annotations.SerializedName

// Request for ending trip
data class EndTripRequest(

    @SerializedName("salesExecutiveId")
    val salesExecutiveId : String,
    @SerializedName("trip_id")
    val tripId: Int,

    @SerializedName("end_time")
    val endTime: String,

    @SerializedName("end_coordinates")
    val endCoordinates: String,
)