package com.example.salesstysetgps.models

import com.google.api.services.calendar.model.Event
import java.text.SimpleDateFormat
import java.util.*

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String?,
    val location: String?,
    val startTime: Date,
    val endTime: Date,
    val attendees: List<String>,
    val status: String,
    val sellerName: String,
    val sellerAddress: String?,
    val purpose: String?
) {
    fun getFormattedTime(): String {
        val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return "${format.format(startTime)} - ${format.format(endTime)}"
    }

    fun getFormattedDate(): String {
        val format = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return format.format(startTime)
    }

    companion object {
        fun fromGoogleEvent(event: Event): CalendarEvent {
            val sellerName = event.summary?.replace("Seller Visit - ", "") ?: "Unknown Seller"
            val purpose = event.description?.lines()?.firstOrNull { it.contains("Purpose:") }
                ?.replace("Purpose:", "")?.trim()


            return CalendarEvent(
                id = event.id ?: "",
                title = event.summary ?: "",
                description = event.description,
                location = event.location,
                startTime = Date(event.start?.dateTime?.value ?: System.currentTimeMillis()),
                endTime = Date(event.end?.dateTime?.value ?: System.currentTimeMillis()),
                attendees = event.attendees?.map { it.email ?: "" } ?: emptyList(),
                status = event.status ?: "confirmed",
                sellerName = sellerName,
                sellerAddress = event.location,
                purpose = purpose
            )
        }

        private fun extractSellerName(title: String, description: String): String {
            // Try to extract from title first
            if (title.contains("Seller Visit")) {
                val parts = title.split(" - ")
                if (parts.size > 1) return parts[1].trim()
            }
            return title
        }

        private fun extractCoordinates(location: String?): Pair<Double?, Double?> {
            if (location.isNullOrEmpty()) return Pair(null, null)

            // Extract from format "lat:28.6139,lng:77.2090"
            val latPattern = "lat:([0-9.-]+)".toRegex()
            val lngPattern = "lng:([0-9.-]+)".toRegex()

            val lat = latPattern.find(location)?.groupValues?.get(1)?.toDoubleOrNull()
            val lng = lngPattern.find(location)?.groupValues?.get(1)?.toDoubleOrNull()

            return Pair(lat, lng)
        }
        private fun extractPhoneNumber(description: String?): String? {
            if (description.isNullOrEmpty()) return null

            // Extract phone number pattern
            val phonePattern = "phone:([0-9+ -]+)".toRegex()
            return phonePattern.find(description)?.groupValues?.get(1)?.trim()
        }

        private fun extractPurpose(description: String?): String? {
            if (description.isNullOrEmpty()) return null

            // Extract purpose from description
            val lines = description.lines()
            return lines.firstOrNull { it.contains("Purpose:") }
                ?.replace("Purpose:", "")
                ?.trim()
        }
    }
}