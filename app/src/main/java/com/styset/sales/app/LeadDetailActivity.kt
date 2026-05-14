package com.styset.sales.app

import android.app.DatePickerDialog
import android.app.ProgressDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton

import com.styset.sales.app.data.network.NetworkClient
import com.styset.sales.app.databinding.ActivityLeadDetailBinding
import com.styset.sales.app.domain.repository.LeadDetailRepository
import com.styset.sales.app.models.LeadDetailData
import com.styset.sales.app.models.NoteData
import com.styset.sales.app.models.NotesData
import com.styset.sales.app.models.Resource
import com.styset.sales.app.models.SalesExecutiveInfo
import com.styset.sales.app.presentation.viewmodel.LeadDetailViewModel
import com.styset.sales.app.presentation.viewmodel.LeadDetailViewModelFactory
import com.styset.sales.app.presentation.viewmodel.base.BaseActivity
import com.styset.sales.app.ui.LeadDetailAdapter
import com.styset.sales.app.util.PreferenceManager
import com.styset.sales.app.util.SnackbarUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// LeadDetailActivity.kt
class LeadDetailActivity : BaseActivity<ActivityLeadDetailBinding>() {

    private lateinit var viewModel: LeadDetailViewModel
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var adapter: LeadDetailAdapter

    private var cachedNotes: MutableList<NoteData> = mutableListOf()  // Cache notes locally

    private var hasAccessDenied = false  // Track access denied state

    private var leadId: Int = 0


    private var leadDetailData: LeadDetailData? = null
    private var notesData: NotesData? = null

    private var isManuallyAddingNote = false  // Add this flag


    override fun getViewBinding(): ActivityLeadDetailBinding = ActivityLeadDetailBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        leadId = intent.getIntExtra("lead_id",0)


        viewModel.fetchLeadDetail(leadId)

        viewModel.getNotes(leadId)

        lifecycleScope.launchWhenStarted {
            viewModel.notesState.collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        // Show loading
                    }
                    is Resource.Success -> {
                        resource.data?.let { data ->
                            notesData = data

                            cachedNotes.clear()
                            cachedNotes.addAll(data.notes)
                            hasAccessDenied = false
                            if (!isManuallyAddingNote) {
                                updateAdapterWithBothData()
                            }

                            // Log the data
                            Log.d("Notes", "Total notes: ${data.notes.size}")
                            Log.d("Lead", "Shop name: ${data.lead_info.name_of_shop}")
                            Log.d("Summary", "Pending follow-ups: ${data.summary.pending_follow_ups}")
                        }
                    }
                    is Resource.Error -> {
                        // Handle error
                        val errorMessage = resource.message ?: "Unknown error"
                        Log.e("Notes", "Error fetching notes: $errorMessage")

                        if (errorMessage.contains("access", ignoreCase = true)) {
                            hasAccessDenied = true
                            notesData = null
                            // Don't clear cached notes when access denied
                            if (!isManuallyAddingNote && cachedNotes.isEmpty()) {
                                updateAdapterWithBothData()
                            } else if (cachedNotes.isNotEmpty() && !isManuallyAddingNote) {
                                // Show cached notes even if API returns access denied
                                updateAdapterWithBothData()
                            }
                            showErrorSnackbar("You don't have permission to view notes for this lead")
                        } else if (errorMessage.contains("not found", ignoreCase = true)) {
                            notesData = null
                            if (!isManuallyAddingNote) {
                                updateAdapterWithBothData()
                            }
                            showErrorSnackbar("No notes found for this lead")
                        } else {
                            notesData = null
                            if (!isManuallyAddingNote) {
                                updateAdapterWithBothData()
                            }
                            showErrorSnackbar("Failed to load notes: $errorMessage")
                        }
                    }


                    else -> {}
                }
            }
        }

    }

    override fun setupViews() {
        preferenceManager = PreferenceManager.Companion.getInstance(this)
        setupViewModel()
        setupToolbar()
        setupRecyclerView()
    }

    override fun setupObservers() {
        observeStateFlow(viewModel.isLoading) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        observeStateFlow(viewModel.leadDetailState) { resource ->
            when (resource) {
                is Resource.Success -> {
                    resource.data?.let { response ->
                        if (response.success == 1) {
                            leadDetailData = response.data
                            if (!isManuallyAddingNote) {
                                updateAdapterWithBothData()
                            }
//
                        } else {
                            showErrorSnackbar(response.message)
                        }
                    }
                }
                is Resource.Error -> {
                    showErrorSnackbar(resource.message ?: "Failed to load lead details")
                }
                else -> {}
            }
        }
    }

    override fun loadData() {
//        viewModel.fetchLeadDetail(leadId)
    }

    private fun setupViewModel() {
        val apiService = NetworkClient.apiService
        val leadDetailRepository = LeadDetailRepository(apiService, preferenceManager)
        val viewModelFactory = LeadDetailViewModelFactory(leadDetailRepository)
        viewModel = ViewModelProvider(this, viewModelFactory)[LeadDetailViewModel::class.java]
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Lead Details"

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = LeadDetailAdapter(
            context = this,
            viewModel = viewModel,
            onImageClick = { imageUrl ->
//                showFullScreenImage(imageUrl)
            },
            onAddNoteClick = {
                showAddNoteDialog()  // Call Activity's method
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@LeadDetailActivity)
            adapter = this@LeadDetailActivity.adapter
            addItemDecoration(
                DividerItemDecoration(
                    this@LeadDetailActivity,
                    DividerItemDecoration.VERTICAL
                )
            )
        }
    }

    private fun showFullScreenImage(imageUrl: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Visiting Card")
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .create()

        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            Glide.with(this@LeadDetailActivity)
                .load(imageUrl)
                .placeholder(R.drawable.ic_stop)
                .error(R.drawable.ic_error)
                .into(this)
            setOnClickListener { dialog.dismiss() }
        }

        dialog.setView(imageView)
        dialog.show()

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.7).toInt()
        )
    }

    private fun showErrorSnackbar(message: String) {
        SnackbarUtils.showError(binding.root, message)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
    private fun updateAdapterWithBothData() {
        // Update when we have at least one of the data sources
        if (leadDetailData != null || notesData != null) {
            Log.d("LeadDetailActivity", "Updating adapter - Lead: ${leadDetailData != null}, Notes: ${notesData != null}")
            adapter.setData(
                lead = leadDetailData,  // Pass nullable directly
                notesData = notesData
            )
        } else {
            Log.d("LeadDetailActivity", "No data available to update adapter")
        }
    }

    private fun showAddNoteDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_note, null)
        val editTextNote = dialogView.findViewById<EditText>(R.id.editTextNote)
        val dateTimePicker = dialogView.findViewById<MaterialButton>(R.id.btnDateTime)
        var selectedDateTime: String? = null

        dateTimePicker.setOnClickListener {
            showDateTimePicker { dateTime ->
                selectedDateTime = dateTime
                dateTimePicker.text = formatDateTimeForDisplay(dateTime)
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add Note")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ -> }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val noteText = editTextNote.text.toString().trim()
            if (noteText.isNotEmpty()) {
                val progressDialog = ProgressDialog(this).apply {
                    setMessage("Adding note...")
                    setCancelable(false)
                    show()
                }

                // Set flag to TRUE before adding note
                isManuallyAddingNote = true

                val currentLeadId = leadDetailData?.id ?: notesData?.lead_info?.id ?: 0

                viewModel.addNote(
                    leadId = currentLeadId,
                    notes = noteText,
                    nextFollowUp = selectedDateTime,
                    onSuccess = { note ->
                        progressDialog.dismiss()
                        Toast.makeText(this, "Note added successfully", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()

                        // Add note to adapter directly
                        val executiveName = leadDetailData?.executiveName ?: "Executive"
                        val newNoteData = NoteData(
                            id = note.id!!,
                            notes = note.notes,
                            next_follow_up = note.next_follow_up,
                            created_date = note.created_date!!,
                            modified_date = note.modified_date!!,
                            created_by = note.sales_executive_id!!,
                            modified_by = note.sales_executive_id,
                            sales_executive = SalesExecutiveInfo(
                                id = note.salesExecutive!!.id,
                                name = executiveName
                            )
                        )

                        // Add to beginning of cache
                        cachedNotes.add(0, newNoteData)
                        hasAccessDenied = false

                        // Update adapter with cached notes
                        adapter.addNewNoteFromResponse(note)

                        // Reset flag after a delay to prevent observer from refreshing
                        Handler(Looper.getMainLooper()).postDelayed({
                            isManuallyAddingNote = false
                        }, 500)
                    },
                    onError = { error ->
                        progressDialog.dismiss()
                        Toast.makeText(this, "Failed to add note: $error", Toast.LENGTH_SHORT).show()
                        isManuallyAddingNote = false
                    }
                )
            } else {
                editTextNote.error = "Note cannot be empty"
            }
        }
    }

    private fun showDateTimePicker(onDateTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val timePicker = TimePickerDialog(
                    this,
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