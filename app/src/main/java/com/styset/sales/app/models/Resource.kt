package com.styset.sales.app.models

// Resource.kt
sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null,
    val onGoingTripId: Int? = null  // Add this field
) {
    class Idle<T> : Resource<T>()
    class Loading<T> : Resource<T>()
    class Success<T>(data: T) : Resource<T>(data = data)
    class Error<T>(
        message: String,
        onGoingTripId: Int? = null
    ) : Resource<T>(message = message, onGoingTripId = onGoingTripId)
}