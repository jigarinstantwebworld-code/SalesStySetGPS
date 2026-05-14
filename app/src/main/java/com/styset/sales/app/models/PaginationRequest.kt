package com.styset.sales.app.models

import com.google.gson.annotations.SerializedName


data class PaginationRequest(
    @SerializedName("page")
    val page: String,

    @SerializedName("limit")
    val limit: String,

    @SerializedName("sales_executive_id")
    val salesExecutiveId: String,

    @SerializedName("search")
    val search: String? = null,

    @SerializedName("selling_type")
    val sellingType: String? = null,

    @SerializedName("status")
    val status: String? = null,

    @SerializedName("sortBy")
    val sortBy: String? = null,

    @SerializedName("sortOrder")
    val sortOrder: String? = null,

    @SerializedName("from")
    val startDate: String? = null,

    @SerializedName("to")
    val endDate: String? = null,

) {
    constructor(
        page: Int,
        limit: Int,
        salesExecutiveId: String,
        search: String? = null,
        sellingType: String? = null,
        status: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null
    ) : this(
        page = page.toString(),
        limit = limit.toString(),
        salesExecutiveId = salesExecutiveId,
        search = search,
        sellingType = sellingType,
        status = status,
        sortBy = sortBy,
        sortOrder = sortOrder
    )
}
