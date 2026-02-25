package com.example.salesstysetgps.data

object SessionCache {
    @Volatile
    var places: List<StopPoint> = emptyList()
}


