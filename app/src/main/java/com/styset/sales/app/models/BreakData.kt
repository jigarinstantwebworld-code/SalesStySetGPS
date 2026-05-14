package com.styset.sales.app.models

import java.io.Serializable

data class BreakData(
    val breakId: Int,
    val breakInTime: String,
    val breakInCoordinates: String,
    var breakOutTime: String? = null,
    var breakOutCoordinates: String? = null,
    var breakDuration: String? = null
) : Serializable
