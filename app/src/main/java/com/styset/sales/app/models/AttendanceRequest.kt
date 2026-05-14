package com.styset.sales.app.models

import com.google.gson.annotations.SerializedName

data class AttendanceRequest(
    @SerializedName("salesExecutiveId")
    val salesExecutiveId: Int,

    @SerializedName("action")
    val action: String, // "LOGIN", "BREAK_IN", "BREAK_OUT", "LOGOUT"

    @SerializedName("coordinates")
    val coordinates: String, // Format: "latitude,longitude"

    @SerializedName("created_by")
    val createdBy: String? = "system" // Only for LOGIN action
)
