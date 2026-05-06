package com.example.salesstysetgps.models

// CreateLeadRequest.kt
import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class CreateLeadRequest(
    @SerializedName("current_location")
    val currentLocation: String,

    @SerializedName("date")
    val date: String,

    @SerializedName("executive_name")
    val executiveName: String,

    @SerializedName("area")
    val area: String,

    @SerializedName("selling_type")
    val sellingType: String,

    @SerializedName("demo")
    val demo: String,

    @SerializedName("name_of_shop")
    val nameOfShop: String,

    @SerializedName("contact_person")
    val contactPerson: String,

    @SerializedName("mobile_number")
    val mobileNumber: String,

    @SerializedName("email_id")
    val emailId: String,

    @SerializedName("remark")
    val remark: String,

    @SerializedName("seller_address")
    val sellerAddress: String,

    @SerializedName("visiting_card_image")
    val visitingCardImage: String,

    @SerializedName("onboarding_client_name")
    val onboardingClientName: String,

    @SerializedName("paid_amount")
    val paidAmount: Int,

    @SerializedName("notes")
    val notes: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("created_by")
    val createdBy: String,

    @SerializedName("sales_executive_id")
    val salesExecutiveId : String
) : Serializable

data class CreateLeadResponse(
    @SerializedName("success")
    val success: Int,

    @SerializedName("message")
    val message: String,

    @SerializedName("data")
    val data: CreateLeadData
) : Serializable

data class CreateLeadData(
    @SerializedName("id")
    val id: Int,

    @SerializedName("name_of_shop")
    val nameOfShop: String,

    @SerializedName("mobile_number")
    val mobileNumber: String,

    @SerializedName("status")
    val status: String
) : Serializable
