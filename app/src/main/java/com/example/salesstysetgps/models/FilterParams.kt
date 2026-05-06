package com.example.salesstysetgps.models

data class FilterParams(
    val sortBy: String? = null,
    val sortOrder: String? = null,
    val status: String? = null,
    val startDate: String? = null,
    val endDate: String? = null
) {
    fun isEmpty(): Boolean {
        return sortBy.isNullOrEmpty() &&
                sortOrder.isNullOrEmpty() &&
                status.isNullOrEmpty() &&
                startDate.isNullOrEmpty() &&
                endDate.isNullOrEmpty()
    }

    override fun toString(): String {
        return "FilterParams(sortBy=$sortBy, sortOrder=$sortOrder, status=$status, startDate=$startDate, endDate=$endDate)"
    }
}