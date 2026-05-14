package com.styset.sales.app.workers

import android.content.Context
import com.styset.sales.app.data.local.AppDatabase
import com.styset.sales.app.data.local.StopEntity
import java.util.Calendar

// TestDataHelper.kt
class TestDataHelper(private val context: Context) {

    private val stopDao = AppDatabase.Companion.get(context).stopDao()

    suspend fun insertTestStops() {
        val testStops = listOf(
            createStop(
                id = 1000001,
                name = "ABC Electronics",
                location = "Satellite Road",
                address = "123 Satellite Rd, Ahmedabad",
                phone = "+91 98765 43210",
                lat = 23.0225,
                lng = 72.5714,
                startTime = getTimeStamp(10, 0),   // 10:00 AM
                endTime = getTimeStamp(10, 10),    // 10:10 AM
                duration = 10,
                letter = "A"
            ),
            createStop(
                id = 1000002,
                name = "Mobile World",
                location = "Prahlad Nagar",
                address = "45 Prahlad Nagar, Ahmedabad",
                phone = "+91 98765 43211",
                lat = 23.0250,
                lng = 72.5250,
                startTime = getTimeStamp(10, 30),  // 10:30 AM
                endTime = getTimeStamp(10, 50),    // 10:50 AM
                duration = 20,
                letter = "B"
            ),
            createStop(
                id = 1000003,
                name = "Digital Solutions",
                location = "CG Road",
                address = "789 CG Road, Ahmedabad",
                phone = "+91 98765 43212",
                lat = 23.0280,
                lng = 72.5350,
                startTime = getTimeStamp(11, 30),  // 11:30 AM
                endTime = getTimeStamp(11, 45),    // 11:45 AM
                duration = 15,
                letter = "C"
            ),
            createStop(
                id = 1000004,
                name = "Tech Hub",
                location = "Vastrapur",
                address = "22 Vastrapur Lake Rd, Ahmedabad",
                phone = "+91 98765 43213",
                lat = 23.0350,
                lng = 72.5280,
                startTime = getTimeStamp(13, 0),   // 1:00 PM
                endTime = getTimeStamp(13, 15),    // 1:15 PM
                duration = 15,
                letter = "D"
            ),
            createStop(
                id = 1000005,
                name = "Gadget Zone",
                location = "Maninagar",
                address = "56 Maninagar Cross Rd, Ahmedabad",
                phone = "+91 98765 43214",
                lat = 23.0320,
                lng = 72.5400,
                startTime = getTimeStamp(15, 0),   // 3:00 PM
                endTime = getTimeStamp(15, 15),    // 3:15 PM
                duration = 15,
                letter = "E"
            ),
            createStop(
                id = 1000006,
                name = "Smart Electronics",
                location = "Navrangpura",
                address = "101 Navrangpura, Ahmedabad",
                phone = "+91 98765 43215",
                lat = 23.0300,
                lng = 72.5450,
                startTime = getTimeStamp(17, 0),   // 5:00 PM
                endTime = getTimeStamp(17, 15),    // 5:15 PM
                duration = 15,
                letter = "F"
            )
        )

        // Insert all stops
        testStops.forEach { stop ->
            stopDao.upsert(stop)
            println("✅ Inserted stop: ${stop.name} (ID: ${stop.id})")
        }
    }

    private fun createStop(
        id: Long,
        name: String,
        location: String,
        address: String,
        phone: String,
        lat: Double,
        lng: Double,
        startTime: Long,
        endTime: Long,
        duration: Long,
        letter: String
    ): StopEntity {
        return StopEntity(
            id = id,
            name = name,
            locationLabel = location,
            address = address,
            phone = phone,
            imageUri = null,
            lat = lat,
            lng = lng,
            startWallTimeMillis = startTime,
            endWallTimeMillis = endTime,
            durationElapsedMinutes = duration,
            startElapsedRealtimeMillis = startTime,
            endElapsedRealtimeMillis = endTime,
            letter = letter
        )
    }

    private fun getTimeStamp(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}