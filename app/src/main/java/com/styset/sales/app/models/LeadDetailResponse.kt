package com.styset.sales.app.models

import com.google.gson.annotations.SerializedName

data class LeadDetailResponse(
    @SerializedName("statusCode")
    val statusCode: Int,

    @SerializedName("success")
    val success: Int,

    @SerializedName("message")
    val message: String,

    @SerializedName("mode")
    val mode: String,

    @SerializedName("data")
    val data: LeadDetailData,

    @SerializedName("date_filter")
    val dateFilter: DateFilter? = null
)

data class LeadDetailData(
    @SerializedName("id")
    val id: Int,

    @SerializedName("current_location")
    val currentLocation: String?,

    @SerializedName("date")
    val date: String,

    @SerializedName("executive_name")
    val executiveName: String,

    @SerializedName("area")
    val area: String,

    @SerializedName("selling_type")
    val sellingType: String,

    @SerializedName("demo")
    val demo: String?,

    @SerializedName("name_of_shop")
    val nameOfShop: String?=null,

    @SerializedName("contact_person")
    val contactPerson: String,

    @SerializedName("mobile_number")
    val mobileNumber: String,

    @SerializedName("email_id")
    val emailId: String?,

    @SerializedName("remark")
    val remark: String?,

    @SerializedName("seller_address")
    val sellerAddress: String?,

    @SerializedName("visiting_card_image")
    val visitingCardImage: String?,

    @SerializedName("onboarding_client_name")
    val onboardingClientName: String?,

    @SerializedName("paid_amount")
    val paidAmount: Int,

    @SerializedName("notes")
    val notes: String?,

    @SerializedName("status")
    val status: String,

    @SerializedName("created_date")
    val createdDate: String,

    @SerializedName("modified_date")
    val modifiedDate: String,

    @SerializedName("created_by")
    val createdBy: Int,

    @SerializedName("modified_by")
    val modifiedBy: Int,

    @SerializedName("sales_executive")
    val salesExecutive: SalesExecutiveData? = null
)

data class SalesExecutiveData(
    @SerializedName("id")
    val id: Int,

    @SerializedName("name")
    val name: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("phoneNumber")
    val phoneNumber: String? = null
)

data class DateFilter(
    @SerializedName("from")
    val from: String,

    @SerializedName("to")
    val to: String
)
