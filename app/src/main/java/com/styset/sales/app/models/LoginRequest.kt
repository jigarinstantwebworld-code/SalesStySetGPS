package com.styset.sales.app.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class LoginRequest(
    @SerializedName("email")
    val email: String,

    @SerializedName("password")
    val password: String
) : Serializable

data class LoginResponse(
    @SerializedName("access_token")
    val accessToken: String,

    @SerializedName("refresh_token")
    val refreshToken: String,

    @SerializedName("id_token")
    val idToken: String,

    @SerializedName("user_id")
    val userId: Int,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: String
) : Serializable

data class LoginData(
    @SerializedName("id")
    val id: Int,

    @SerializedName("name")
    val name: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("mobile_number")
    val mobileNumber: String,

    @SerializedName("role")
    val role: String,

    @SerializedName("token")
    val token: String
) : Serializable