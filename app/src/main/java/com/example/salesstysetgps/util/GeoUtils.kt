package com.example.salesstysetgps.util

import android.location.Location
import com.google.android.gms.maps.model.LatLng

object GeoUtils {
    fun distanceMeters(a: LatLng, b: LatLng): Float {
        val res = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, res)
        return res[0]
    }
}


