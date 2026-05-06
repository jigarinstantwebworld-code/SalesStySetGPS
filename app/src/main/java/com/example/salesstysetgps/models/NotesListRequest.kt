package com.example.salesstysetgps.models

data class NotesListRequest(
    val from_date: String? = null,
    val to_date: String? = null,
    val sortBy: String = "latest_note_date",
    val sortOrder: String = "desc",
    val sale_leads_id: Int,
    val search: String? = null,
    val sales_executive_id: Int?=null
)

// Response models
data class NotesListResponse(
    val statusCode: Int,
    val headers: Map<String, String>? = null,
    val body: NotesResponseBody
)

data class NotesResponseBody(
    val success: Int,
    val message: String,
    val data: NotesData
)

data class NotesData(
    val lead_info: LeadInfo,
    val notes: List<NoteData>,
    val summary: NotesSummary,
//    val executive_info: ExecutiveInfo? = null,
    val date_range: DateRange
)

data class LeadInfo(
    val id: Int,
    val name_of_shop: String,
    val mobile_number: String,
    val contact_person: String,
    val status: String,
    val area: String,
    val executive_name: String,
    val sales_executive_id: Int
)

data class NoteData(
    val id: Int,
    val notes: String,
    val next_follow_up: String?,
    val created_date: String,
    val modified_date: String,
    val created_by: Int,
    val modified_by: Int,
    val sales_executive: SalesExecutiveInfo
)



data class SalesExecutiveInfo(
    val id: Int,
    val name: String
)

data class NotesSummary(
    val total_notes: Int,
    val total_follow_ups: Int,
    val pending_follow_ups: Int,
    val latest_note_date: String,
    val oldest_note_date: String
)



data class DateRange(
    val from: String,
    val to: String
)
