package com.styset.sales.app.data

object SessionCache {
    @Volatile
    var places: List<StopPoint> = emptyList()
}


