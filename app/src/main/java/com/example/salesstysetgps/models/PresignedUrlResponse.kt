package com.example.salesstysetgps.models

import com.google.gson.annotations.SerializedName

data class PresignedUrlResponse(
    @SerializedName("resultMsg")
    val resultMsg: String,

    // The dynamic key will be handled in repository
    val presignedUrl: String = "",
    val objectKey: String = ""
)
data class PresignedUrlData(
    @SerializedName("url")
    val url: String,

    @SerializedName("fields")
    val fields: Map<String, String>? = null,

    @SerializedName("object_key")
    val objectKey: String
)
