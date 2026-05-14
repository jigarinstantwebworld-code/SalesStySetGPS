package com.styset.sales.app.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Lead(
    @SerializedName("id")
    val id: Int,

    @SerializedName("name_of_shop")
    val nameOfShop: String,

    @SerializedName("contact_person")
    val contactPerson: String,

    @SerializedName("mobile_number")
    val mobileNumber: String,

    @SerializedName("executive_name")
    val executiveName: String,

    @SerializedName("area")
    val area: String,

    @SerializedName("selling_type")
    val sellingType: String,

    @SerializedName("date")
    val date: String,

    @SerializedName("paid_amount")
    val paidAmount: Int,

    @SerializedName("status")
    val status: String,

    @SerializedName("created_date")
    val createdDate: String
)

data class LeadsResponse(
    @SerializedName("success")
    val success: Int,

    @SerializedName("message")
    val message: String,

    @SerializedName("mode")
    val mode: String,

    @SerializedName("data")
    val data: LeadsData
) : Serializable

data class LeadsData(
    @SerializedName("leads")
    val leads: List<Lead>,

    @SerializedName("pagination")
    val pagination: Pagination,

    @SerializedName("sales_executive")
    val salesExecutive: LeadsData?
) : Serializable

data class Pagination(
    @SerializedName("total_items")
    val totalItems: Int,

    @SerializedName("total_pages")
    val totalPages: Int,

    @SerializedName("current_page")
    val currentPage: Int,

    @SerializedName("items_per_page")
    val itemsPerPage: Int,

    @SerializedName("has_next_page")
    val hasNextPage: Boolean
)

data class PaginatedLeads(
    val leads: List<Lead>,
    val currentPage: Int,
    val totalPages: Int,
    val totalItems: Int,
    val isLastPage: Boolean
)

// LeadModel.kt
data class LeadModel(
    val id: Int,
    val nameOfShop: String? = null,  // Make nullable
    val contactPerson: String? = null,  // Make nullable
    val mobileNumber: String? = null,  // Make nullable
    val area: String? = null,  // Make nullable
    val executiveName: String? = null,
    val sellingType: String? = null,
    val date: String? = null,
    val paidAmount: Int? = null,
    val status: String? = null,
    val createdDate: String? = null
)

// Extension function to convert Lead to LeadModel
fun Lead.toLeadModel(): LeadModel {
    return LeadModel(
        id = this.id,
        nameOfShop = this.nameOfShop ?: "Unknown Shop",  // Provide default value
        contactPerson = this.contactPerson ?: "Unknown",
        mobileNumber = this.mobileNumber ?: "",
        area = this.area ?: "",
        executiveName = this.executiveName,
        sellingType = this.sellingType,
        date = this.date,
        paidAmount = this.paidAmount,
        status = this.status,
        createdDate = this.createdDate
    )
}

data class LeadSelection(
    val lead: LeadModel,
    var isSelected: Boolean = false
)