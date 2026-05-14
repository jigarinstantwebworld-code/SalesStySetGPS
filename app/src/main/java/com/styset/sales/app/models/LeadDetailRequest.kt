package com.styset.sales.app.models

import com.google.gson.annotations.SerializedName

data class LeadDetailRequest(
    @SerializedName("id")
    val id: Int,
    @SerializedName("sales_executive_id")
    val salesExecutiveId: Int,
)
