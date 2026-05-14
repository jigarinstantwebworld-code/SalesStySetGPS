package com.styset.sales.app.workers

// MockApiService.kt - For testing without real server
class MockApiService {
    // Simulate API call with delay
//    suspend fun syncStops(stops: List<StopSyncData>): SyncResponse {
//        // Simulate network delay
//        delay(2000)
//
//        // Log what would be sent
//        println("📤 ===== SYNC REQUEST =====")
//        println("Sending ${stops.size} stops to server:")
//        stops.forEach { stop ->
//            println("   - ${stop.name} (${stop.letter}) at ${stop.latitude},${stop.longitude}")
//        }
//        println("==========================")
//
//        // Simulate success (90% success rate for testing)
//        val isSuccess = stops.isNotEmpty() && (stops.size % 3 != 0) // Every 3rd sync fails
//
//        return if (isSuccess) {
//            SyncResponse(
//                success = true,
//                message = "Successfully synced ${stops.size} stops",
//            )
//        } else {
//            SyncResponse(
//                success = false,
//                message = "Server error: Failed to process request",
//                syncedCount = 0
//            )
//        }
//    }
}