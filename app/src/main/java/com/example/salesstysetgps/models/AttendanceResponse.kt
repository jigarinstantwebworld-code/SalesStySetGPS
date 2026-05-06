package com.example.salesstysetgps.models

import com.google.gson.annotations.SerializedName

data class AttendanceResponse(
    @SerializedName("success")
    val success: Int,

    @SerializedName("message")
    val message: String,

    @SerializedName("data")
    val data: AttendanceData? = null
)

data class AttendanceData(
    @SerializedName("attendanceId")
    val attendanceId: Int? = null,

    @SerializedName("loginTime")
    val loginTime: String? = null,

    @SerializedName("logoutTime")
    val logoutTime: String? = null,

    @SerializedName("breakStartTime")
    val breakStartTime: String? = null,

    @SerializedName("breakEndTime")
    val breakEndTime: String? = null,

    @SerializedName("totalBreakMinutes")
    val totalBreakMinutes: Int? = null,

    @SerializedName("loginCoordinates")
    val loginCoordinates : String?=null,

    @SerializedName("date")
    val date : String?=null,

    @SerializedName("breakId")
    val breakId : Int?=null,

    @SerializedName("breakInTime")
    val breakInTime : String?=null,

    @SerializedName("breakInCoordinates")
    val breakInCoordinates : String?=null,

    @SerializedName("breakOutTime")
    val breakOutTime : String?=null,

    @SerializedName("breakOutCoordinates")
    val breakOutCoordinates : String?=null,

    @SerializedName("breakDuration")
    val breakDuration : String?=null,

    @SerializedName("totalBreakTime")
    val totalBreakTime : String?=null,

    @SerializedName("totalWorkingTime")
    val totalWorkingTime : String?=null,

    @SerializedName("logoutCoordinates")
    val logoutCoordinates : String?=null,


)