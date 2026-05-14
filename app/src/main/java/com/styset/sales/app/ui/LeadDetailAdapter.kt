package com.styset.sales.app.ui

// LeadDetailAdapter.kt
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.styset.sales.app.R
import com.styset.sales.app.models.LeadDetailData
import com.styset.sales.app.models.NoteData
import com.styset.sales.app.models.NotesData
import com.styset.sales.app.models.SalesExecutiveInfo
import com.styset.sales.app.presentation.viewmodel.LeadDetailViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class LeadDetailAdapter(
    private val context: Context,
    private val viewModel: LeadDetailViewModel,
    private val onImageClick: (String) -> Unit,
    private val onAddNoteClick: () -> Unit  // Add this callback
) : RecyclerView.Adapter<LeadDetailAdapter.ViewHolder>() {

    private val items = mutableListOf<DetailItem>()
    private var leadId: Int = -1

    enum class ItemType(val type: Int) {
        SECTION_HEADER(0),
        FIELD(1),
        IMAGE(2),
        ACTION_BUTTON(3),
        NOTE(4),
        ADD_NOTE_BUTTON(5)
    }

    sealed class DetailItem {
        data class SectionHeader(val title: String,
                                 var isExpandable: Boolean = false,
                                 var isExpanded: Boolean = false
            ) : DetailItem()
        data class Field(
            val label: String,
            var value: String,
            val isClickable: Boolean = false,
            val clickAction: (() -> Unit)? = null
        ) : DetailItem()

        data class Image(val url: String, val objectKey: String,
                         var isExpanded: Boolean = false
        ) : DetailItem()
        data class ActionButton(val text: String, val action: () -> Unit) : DetailItem()
        data class NoteItem(val note: NoteData) : DetailItem()  // Use NoteData from API

        data class AddNoteButton(val action: () -> Unit) : DetailItem()
    }

    // Change this method signature to accept nullable lead
    fun setData(lead: LeadDetailData?, notesData: NotesData?) {
        items.clear()

        // If both are null, return early
        if (lead == null && notesData == null) {
            Log.d("LeadDetailAdapter", "No data to display")
            return
        }

        // Set leadId if lead is available
        if (lead != null) {
            leadId = lead.id
        } else if (notesData != null) {
            leadId = notesData.lead_info.id
        }

        // Add Lead Information Section (only if lead is available)
        if (lead != null) {
            // Shop Information Section
            items.add(DetailItem.SectionHeader("Shop Information"))
            if (lead.nameOfShop?.isNotEmpty() ==true){
                items.add(DetailItem.Field("Shop Name", lead.nameOfShop))
            }else{
                items.add(DetailItem.Field("Shop Name", "No shop name"))
            }
            items.add(DetailItem.Field("Contact Person", lead.contactPerson))
            items.add(DetailItem.Field("Mobile Number", lead.mobileNumber, true) {
                openDialer(lead.mobileNumber)
            })
            if (!lead.emailId.isNullOrEmpty()){
                items.add(DetailItem.Field("Email ID", lead.emailId ?: "-", true) {
                    sendEmail(lead.emailId)
                })
            }

            items.add(DetailItem.Field("Area", lead.area))
            if (!lead.sellerAddress.isNullOrEmpty()){
                items.add(DetailItem.Field("Seller Address", lead.sellerAddress ?: "-"))
            }

            // Lead Details Section
            items.add(DetailItem.SectionHeader("Lead Details"))
            items.add(DetailItem.Field("Selling Type", getSellingTypeName(lead.sellingType)))
            if (!lead.demo.isNullOrEmpty()){
                items.add(DetailItem.Field("Demo", lead.demo ?: "-"))
            }
            if (!lead.remark.isNullOrEmpty()){
                items.add(DetailItem.Field("Remark", lead.remark ?: "-"))
            }
            if (lead.paidAmount !=0){
                items.add(DetailItem.Field("Paid Amount", "₹${lead.paidAmount}"))
            }
            items.add(DetailItem.Field("Status", lead.status))
        } else if (notesData != null) {
            // Use lead_info from notes API when lead detail is not available
            val leadInfo = notesData.lead_info
            items.add(DetailItem.SectionHeader("Shop Information"))
            items.add(DetailItem.Field("Shop Name", leadInfo.name_of_shop))
            items.add(DetailItem.Field("Contact Person", leadInfo.contact_person))
            items.add(DetailItem.Field("Mobile Number", leadInfo.mobile_number, true) {
                openDialer(leadInfo.mobile_number)
            })
            items.add(DetailItem.Field("Area", leadInfo.area))
            items.add(DetailItem.Field("Status", leadInfo.status))
        }

        // Notes Section
        items.add(DetailItem.SectionHeader("Notes & Follow-ups"))

        // Display notes summary if available
        if (notesData != null) {
            // Check if notesData has valid notes (not empty and not error)
            if (notesData.notes.isNotEmpty()) {
                // Log the summary data
                Log.d("LeadDetailAdapter", "=== Notes Summary ===")
                Log.d("LeadDetailAdapter", "Total notes: ${notesData.notes.size}")
                Log.d("LeadDetailAdapter", "Lead: ${notesData.lead_info.name_of_shop}")
                Log.d(
                    "LeadDetailAdapter",
                    "Pending follow-ups: ${notesData.summary.pending_follow_ups}"
                )

                // Add summary as a field
                items.add(
                    DetailItem.Field(
                        "Summary",
                        "Total: ${notesData.summary.total_notes} | Pending: ${notesData.summary.pending_follow_ups}",
                        false, null
                    )
                )

                // Add notes
                notesData.notes.forEach { note ->
                    items.add(DetailItem.NoteItem(note))
                }
            } else {
                // Show no notes message with a different style
                items.add(
                    DetailItem.Field(
                        "No Notes",
                        "No notes found for this lead",
                        false, null
                    )
                )
            }
        } else {
            // Show error message when notesData is null (access denied or fetch failed)
            items.add(
                DetailItem.Field(
                    "No Access",
                    "You don't have access to this lead's notes",
                    false, null
                )
            )
        }

        // Add button to add new note (only if user has access)
        // You can add a condition here based on whether notesData is error or not
        items.add(DetailItem.AddNoteButton {
            onAddNoteClick.invoke()
        })

        // Add Visiting Card, Dates, and Executive Information (only if lead is available)
        if (lead != null) {
            // Visiting Card Image
            lead.visitingCardImage?.let { imageUrl ->
                val objectKey = extractObjectKeyFromUrl(imageUrl)
//                items.add(DetailItem.SectionHeader("Visiting Card"))
//                items.add(
//                    DetailItem.Field(
//                        label = "Visiting Card",
//                        value = "View Image",
//                        isClickable = true
//                    )
//                )
//                items.add(DetailItem.Image(imageUrl, objectKey,false))
                items.add(DetailItem.SectionHeader("Visiting Card", isExpandable = true,
                    isExpanded = false))
                items.add(DetailItem.Image(imageUrl, objectKey, false))
            }

            // Dates Section
            items.add(DetailItem.SectionHeader("Dates Information"))
            items.add(DetailItem.Field("Date", formatDate(lead.date)))
            items.add(DetailItem.Field("Created Date", formatDateTime(lead.createdDate)))
            items.add(DetailItem.Field("Modified Date", formatDateTime(lead.modifiedDate)))

            // Executive Information Section
            items.add(DetailItem.SectionHeader("Executive Information"))
            if (!lead.executiveName.isNullOrEmpty()){
                items.add(DetailItem.Field("Executive Name", lead.executiveName))
            }
        }

        notifyDataSetChanged()
    }


    fun addNewNoteFromResponse(newNote: Note) {
        // Convert new note
        val executiveName = getExecutiveName()
        val newNoteData = NoteData(
            id = newNote.id!!,
            notes = newNote.notes,
            next_follow_up = newNote.next_follow_up,
            created_date = newNote.created_date!!,
            modified_date = newNote.modified_date!!,
            created_by = newNote.sales_executive_id!!,
            modified_by = newNote.sales_executive_id,
            sales_executive = SalesExecutiveInfo(
                id = newNote.salesExecutive!!.id,
                name = executiveName
            )
        )

        // Find the notes section and add button
        var notesSectionStart = -1
        var addButtonIndex = -1

        for (i in items.indices) {
            when (items[i]) {
                is DetailItem.SectionHeader -> {
                    if ((items[i] as DetailItem.SectionHeader).title == "Notes & Follow-ups") {
                        notesSectionStart = i
                    }
                }

                is DetailItem.AddNoteButton -> {
                    if (notesSectionStart != -1) {
                        addButtonIndex = i
                    }
                }

                else -> {}
            }
        }

        if (notesSectionStart != -1 && addButtonIndex != -1) {
            // Collect existing notes (if any)
            val existingNotes = mutableListOf<NoteData>()
            var currentIndex = notesSectionStart + 1

            while (currentIndex < addButtonIndex) {
                when (val item = items[currentIndex]) {
                    is DetailItem.NoteItem -> {
                        existingNotes.add(item.note)
                    }

                    else -> {}
                }
                currentIndex++
            }

            // Remove ALL items between section header and add button
            // This removes Summary, No Notes, No Access, and all notes
            while (items.size > notesSectionStart + 1 && items[notesSectionStart + 1] !is DetailItem.AddNoteButton) {
                items.removeAt(notesSectionStart + 1)
                addButtonIndex--
            }

            // Add new note at the beginning
            existingNotes.add(0, newNoteData)

            // Calculate pending follow-ups
            val pendingCount = existingNotes.count { note ->
                note.next_follow_up?.let { followUp ->
                    try {
                        val inputFormat =
                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
                        val followUpDate = inputFormat.parse(followUp)
                        followUpDate?.after(Date()) == true
                    } catch (e: Exception) {
                        false
                    }
                } ?: false
            }

            // Add summary
            val summaryField = DetailItem.Field(
                "Summary",
                "Total: ${existingNotes.size} | Pending: $pendingCount",
                false, null
            )
            items.add(notesSectionStart + 1, summaryField)
            addButtonIndex++

            // Add all notes
            var insertPosition = addButtonIndex
            existingNotes.forEach { noteData ->
                items.add(insertPosition, DetailItem.NoteItem(noteData))
                insertPosition++
            }

            notifyDataSetChanged()

            // Scroll to the first note (newest)
            try {
                (context as? RecyclerView)?.smoothScrollToPosition(notesSectionStart + 2)
            } catch (e: Exception) {
                // Ignore
            }

            Log.d("AddNote", "Note added successfully. Total notes: ${existingNotes.size}")
        }
    }


    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is DetailItem.SectionHeader -> ItemType.SECTION_HEADER.type
            is DetailItem.Field -> ItemType.FIELD.type
            is DetailItem.Image -> ItemType.IMAGE.type
            is DetailItem.ActionButton -> ItemType.ACTION_BUTTON.type
            is DetailItem.NoteItem -> ItemType.NOTE.type
            is DetailItem.AddNoteButton -> ItemType.ADD_NOTE_BUTTON.type
        }
    }


    private fun getExecutiveName(): String {
        // Try to get executive name from lead detail
        for (item in items) {
            if (item is DetailItem.Field && item.label == "Executive Name") {
                return item.value
            }
        }
        return "Executive"
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ItemType.SECTION_HEADER.type -> {
                val view = inflater.inflate(R.layout.item_lead_detail_section, parent, false)
                SectionHeaderViewHolder(view,this)
            }

            ItemType.FIELD.type -> {
                val view = inflater.inflate(R.layout.item_lead_detail_field, parent, false)
                FieldViewHolder(view,this)
            }

            ItemType.IMAGE.type -> {
                val view = inflater.inflate(R.layout.item_lead_detail_image, parent, false)
                ImageViewHolder(view, viewModel, onImageClick)
            }

            ItemType.NOTE.type -> {
                val view = inflater.inflate(R.layout.item_lead_detail_note, parent, false)
                NoteViewHolder(view)
            }

            ItemType.ADD_NOTE_BUTTON.type -> {
                val view = inflater.inflate(R.layout.item_lead_detail_add_note, parent, false)
                AddNoteViewHolder(view)
            }

            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (holder) {
            is SectionHeaderViewHolder -> holder.bind(items[position] as DetailItem.SectionHeader)
            is FieldViewHolder -> holder.bind(items[position] as DetailItem.Field)
            is ImageViewHolder -> holder.bind(items[position] as DetailItem.Image)
            is NoteViewHolder -> holder.bind(items[position] as DetailItem.NoteItem)
            is AddNoteViewHolder -> holder.bind(items[position] as DetailItem.AddNoteButton)
        }
    }

    override fun getItemCount(): Int = items.size

    // Section Header ViewHolder
    class SectionHeaderViewHolder(
        itemView: View,
        private val adapter: LeadDetailAdapter
    ) : ViewHolder(itemView) {

        private val tvTitle: TextView = itemView.findViewById(R.id.tvSectionTitle)

        fun bind(item: DetailItem.SectionHeader) {
//            tvTitle.text = item.title
            tvTitle.text = if (item.isExpandable) {
                if (item.isExpanded) "${item.title} (Hide Image)"
                else "${item.title} (View Image)"
            } else {
                item.title
            }

            if (item.isExpandable) {

                itemView.setOnClickListener {

                    val position = adapterPosition
                    if (position == RecyclerView.NO_POSITION) return@setOnClickListener

                    val imageItem = adapter.items.getOrNull(position + 1)
                            as? DetailItem.Image

                    imageItem?.let {
                        it.isExpanded = !it.isExpanded
                        item.isExpanded = it.isExpanded

                        adapter.notifyItemChanged(position)
                        adapter.notifyItemChanged(position + 1)
                    }
                }

            } else {
                itemView.setOnClickListener(null)
            }
        }
    }

    // Field ViewHolder
    class FieldViewHolder(
        itemView: View,
        private val adapter: LeadDetailAdapter
    ) : ViewHolder(itemView) {

        private val tvLabel: TextView = itemView.findViewById(R.id.tvLabel)
        private val tvValue: TextView = itemView.findViewById(R.id.tvValue)

        fun bind(item: DetailItem.Field) {
            tvLabel.text = item.label
            tvValue.text = item.value.ifEmpty { "-" }

            if (item.isClickable) {

                itemView.setOnClickListener {

                    if (item.label == "Visiting Card") {


                        val imageItem = adapter.items.getOrNull(position + 1)
                                as? DetailItem.Image


                        imageItem?.let {
                            it.isExpanded = !it.isExpanded
                            item.value = if (it.isExpanded) "Hide Image" else "View Image"
                            adapter.notifyItemChanged(position)       // update text
                            adapter.notifyItemChanged(position + 1)
                        }

                    } else {
                        item.clickAction?.invoke()
                    }
                }
            } else {
                itemView.setOnClickListener(null)
            }
        }
    }

    // Note ViewHolder - Updated with all note details
    class NoteViewHolder(itemView: View) : ViewHolder(itemView) {
        private val tvNoteText: TextView = itemView.findViewById(R.id.tvNoteText)
        private val tvNoteMeta: TextView = itemView.findViewById(R.id.tvNoteMeta)
        private val tvCreatedBy: TextView = itemView.findViewById(R.id.tvCreatedBy)
        private val tvFollowUp: TextView = itemView.findViewById(R.id.tvFollowUp)

        fun bind(item: DetailItem.NoteItem) {
            // Set note text
            tvNoteText.text = item.note.notes

            // Format and set created date
            val createdDate = formatDate(item.note.created_date)
            tvNoteMeta.text = "Created By: $createdDate"

            // Set created by information
            tvCreatedBy.text = "Modify By: ${item.note.sales_executive.name}"
            tvCreatedBy.visibility = View.VISIBLE

            // Log note data
            Log.d("NoteViewHolder", "Note: ${item.note.notes}")
            Log.d("NoteViewHolder", "Created by: ${item.note.sales_executive.name}")
            Log.d("NoteViewHolder", "Created date: $createdDate")

            // Handle follow-up date
            if (!item.note.next_follow_up.isNullOrEmpty()) {
                val followUpDate = formatDate(item.note.next_follow_up)
                tvFollowUp.text = "📅 Next Follow-up: $followUpDate"
                tvFollowUp.visibility = View.VISIBLE

                Log.d("NoteViewHolder", "Follow-up date: $followUpDate")
            } else {
                tvFollowUp.visibility = View.GONE
            }
        }

        private fun formatDate(dateString: String?): String {
            if (dateString.isNullOrEmpty()) return ""
            return try {
                val inputFormat =
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                inputFormat.timeZone = TimeZone.getTimeZone("UTC")
                val outputFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                outputFormat.timeZone = TimeZone.getDefault()
                val date = inputFormat.parse(dateString)
                outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                dateString
            }
        }
    }

    // Add Note Button ViewHolder
    class AddNoteViewHolder(itemView: View) : ViewHolder(itemView) {
        private val btnAddNote: Button = itemView.findViewById(R.id.btnAddNote)

        fun bind(item: DetailItem.AddNoteButton) {
            btnAddNote.setOnClickListener {
                item.action.invoke()
            }
        }
    }

    // Image ViewHolder (your existing code)
    class ImageViewHolder(
        itemView: View,
        private val viewModel: LeadDetailViewModel,
        private val onImageClick: (String) -> Unit
    ) : ViewHolder(itemView) {
        private val ivImage: ImageView = itemView.findViewById(R.id.ivImage)
        private val imageContainer: FrameLayout = itemView.findViewById(R.id.imageContainer)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        private val btnLoadImage: Button = itemView.findViewById(R.id.btnLoadImage)

        private var currentItem: DetailItem.Image? = null
        private var currentPresignedUrl: String? = null

        fun bind(item: DetailItem.Image) {
            imageContainer.visibility =
                if (item.isExpanded) View.VISIBLE else View.GONE

            // If collapsed → do nothing
            if (!item.isExpanded) return

            // Already loaded → skip reload
            if (currentPresignedUrl != null) return

            progressBar.visibility = View.VISIBLE
            val cleanUrl = item.url.substringBefore("?")
            val bucketName = extractBucketNameFromUrl(cleanUrl)
            val fieldName = extractFieldNameFromUrl(cleanUrl)

            viewModel.fetchPresignedUrl(
                bucketName = bucketName!!,
                fieldName = fieldName!!,
                objectKey = item.objectKey,
                onSuccess = { presignedUrl ->

                    currentPresignedUrl = presignedUrl

                    Glide.with(itemView.context)
                        .load(presignedUrl)
                        .into(ivImage)

                    progressBar.visibility = View.GONE

                    ivImage.setOnClickListener {
                        onImageClick(presignedUrl)
                    }
                },
                onError = {
                    progressBar.visibility = View.GONE
                }
            )
        }

        private fun loadImage(item: DetailItem.Image) {
            if (currentPresignedUrl != null) {
                ivImage.setOnClickListener {
                    onImageClick(currentPresignedUrl!!)
                }
                btnLoadImage.visibility = View.GONE
                return
            }

            imageContainer.visibility =
                if (item.isExpanded) View.VISIBLE else View.GONE

            if (!item.isExpanded) return

            progressBar.visibility = View.VISIBLE
            btnLoadImage.isEnabled = false

            val cleanUrl = item.url.substringBefore("?")
            val bucketName = extractBucketNameFromUrl(cleanUrl)
            val fieldName = extractFieldNameFromUrl(cleanUrl)

            viewModel.fetchPresignedUrl(
                bucketName = bucketName!!,
                fieldName = fieldName!!,
                objectKey = item.objectKey,
                onSuccess = { presignedUrl ->
                    currentPresignedUrl = presignedUrl
                    imageContainer.visibility = View.VISIBLE
                    Glide.with(itemView.context)
                        .load(presignedUrl)
                        .placeholder(R.drawable.ic_start)
                        .error(R.drawable.ic_error)
                        .listener(object : RequestListener<Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable?>,
                                isFirstResource: Boolean
                            ): Boolean {
                                progressBar.visibility = View.GONE
                                btnLoadImage.isEnabled = true
                                btnLoadImage.text = "Retry"
                                return false
                            }

                            override fun onResourceReady(
                                resource: Drawable,
                                model: Any,
                                target: Target<Drawable?>?,
                                dataSource: DataSource,
                                isFirstResource: Boolean
                            ): Boolean {
                                progressBar.visibility = View.GONE
                                btnLoadImage.isEnabled = true
                                btnLoadImage.visibility = View.GONE
                                ivImage.setOnClickListener {
                                    onImageClick(presignedUrl)
                                }
                                return false
                            }
                        })
                        .into(ivImage)
                },
                onError = { error ->
                    progressBar.visibility = View.GONE
                    btnLoadImage.isEnabled = true
                    btnLoadImage.text = "Retry"
                    Toast.makeText(
                        itemView.context,
                        "Failed to load image: $error",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        private fun extractBucketNameFromUrl(url: String): String {
            return try {
                url.substringAfter("https://")
                    .substringBefore(".s3.")
            } catch (e: Exception) {
                Log.e("ImageViewHolder", "Error extracting bucket name", e)
                ""
            }
        }


        private fun extractFieldNameFromUrl(url: String): String {
            return try {
                val fileName = url.substringAfterLast("/")
                    .substringBefore("?")

                fileName.substringBeforeLast(".")
            } catch (e: Exception) {
                Log.e("ImageViewHolder", "Error extracting field name", e)
                ""
            }
        }
    }

    sealed class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    // Helper methods
    private fun openDialer(mobileNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$mobileNumber")
        context.startActivity(intent)
    }

    private fun sendEmail(email: String?) {
        email?.let {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("mailto:$it")
            context.startActivity(intent)
        }
    }

    private fun getSellingTypeName(type: String): String {
        return when (type.lowercase(Locale.getDefault())) {
            "a1" -> "Type A1 - Premium"
            "a2" -> "Type A2 - Standard"
            "a3" -> "Type A3 - Basic"
            "a4" -> "Type A4 - Economy"
            else -> type
        }
    }

    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }

    private fun formatDateTime(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }

    private fun extractObjectKeyFromUrl(url: String): String {
        return try {
            url.substringAfter(".amazonaws.com/")
                .substringBefore("?")
        } catch (e: Exception) {
            Log.e("ImageViewHolder", "Error extracting object key", e)
            ""
        }
    }

    private fun showDateTimePicker(onDateTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val timePicker = TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        val formattedDateTime = String.format(
                            "%04d-%02d-%02dT%02d:%02d:00.000Z",
                            year, month + 1, dayOfMonth, hourOfDay, minute
                        )
                        onDateTimeSelected(formattedDateTime)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                )
                timePicker.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun formatDateTimeForDisplay(dateTime: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            val date = inputFormat.parse(dateTime)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateTime
        }
    }
}


data class Note(
    val id: Int? = null,
    val sale_leads_id: Int? = null,
    val notes: String,
    val next_follow_up: String? = null,  // ISO date format
    val sales_executive_id: Int? = null,
    val created_date: String? = null,
    val modified_date: String? = null,
    val salesExecutive: SalesExecutive? = null,
    val sellLeads: SellLeads? = null
)

data class SalesExecutive(
    val id: Int
)

data class SellLeads(
    val id: Int,
    val name_of_shop: String,
    val mobile_number: String,
    val contact_person: String,
    val status: String
)

// Request model for adding note
data class AddNoteRequest(
    val sale_leads_id: Int,
    val notes: String,
    val next_follow_up: String? = null,
    val sales_executive_id: Int
)

// Response model
data class AddNoteResponse(
    val statusCode: Int,
    val headers: Map<String, String>? = null,
    val body: AddNoteBody
)

data class AddNoteBody(
    val success: Int,
    val message: String,
    val data: Note
)
