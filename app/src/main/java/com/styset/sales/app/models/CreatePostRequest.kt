package com.styset.sales.app.models

data class CreatePostRequest(
    val title: String,
    val body: String,
    val userId: Int
)
