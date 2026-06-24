package com.styset.sales.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.styset.sales.app.domain.repository.LeadDetailRepository
import com.styset.sales.app.models.LeadDetailResponse
import com.styset.sales.app.models.NotesData
import com.styset.sales.app.models.Resource
import com.styset.sales.app.ui.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// LeadDetailViewModel.kt
class LeadDetailViewModel(
    private val leadDetailRepository: LeadDetailRepository
) : ViewModel() {

    private val _leadDetailState = MutableStateFlow<Resource<LeadDetailResponse>>(Resource.Loading())
    val leadDetailState: StateFlow<Resource<LeadDetailResponse>> = _leadDetailState.asStateFlow()

    private val _presignedUrlState = MutableStateFlow<Resource<String>>(Resource.Loading())
    val presignedUrlState: StateFlow<Resource<String>> = _presignedUrlState.asStateFlow()

    private val _isImageLoading = MutableStateFlow(false)
    val isImageLoading: StateFlow<Boolean> = _isImageLoading.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()



    private val _addNoteState = MutableStateFlow<Resource<Note>?>(null)
    val addNoteState: StateFlow<Resource<Note>?> = _addNoteState.asStateFlow()

    private val _isAddingNote = MutableStateFlow(false)
    val isAddingNote: StateFlow<Boolean> = _isAddingNote.asStateFlow()



    private val _notesState = MutableStateFlow<Resource<NotesData>>(Resource.Loading())
    val notesState: StateFlow<Resource<NotesData>> = _notesState.asStateFlow()

    fun getNotes(saleLeadsId: Int) {
        viewModelScope.launch {
            _notesState.value = Resource.Loading()
            val result = leadDetailRepository.getNotesList(saleLeadsId)
            _notesState.value = result
        }
    }


    fun addNote(
        leadId: Int,
        notes: String,
        nextFollowUp: String? = null,
        location: String,
        onSuccess: (Note) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isAddingNote.value = true
            _addNoteState.value = Resource.Loading()

            val result = leadDetailRepository.addNote(leadId, notes, nextFollowUp,location)

            _addNoteState.value = result
            _isAddingNote.value = false

            when (result) {
                is Resource.Success -> {
                    result.data?.let { note ->
                        onSuccess(note)
                        // Refresh lead detail to get updated notes list
                        fetchLeadDetail(leadId)
                    }
                }
                is Resource.Error -> {
                    onError(result.message ?: "Failed to add note")
                }
                else -> {}
            }
        }
    }

    fun fetchLeadDetail(leadId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _leadDetailState.value = Resource.Loading()

            val result = leadDetailRepository.getLeadDetail(leadId)

            _leadDetailState.value = result
            _isLoading.value = false
        }
    }

    fun fetchPresignedUrl(
        bucketName: String,
        fieldName: String,
        objectKey: String,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isImageLoading.value = true
            _presignedUrlState.value = Resource.Loading()

            val result = leadDetailRepository.getPresignedUrl(bucketName, fieldName, objectKey)

            _presignedUrlState.value = result
            _isImageLoading.value = false

            when (result) {
                is Resource.Success -> {
                    result.data?.let { url ->
                        onSuccess(url)
                    }
                }
                is Resource.Error -> {
                    onError(result.message ?: "Failed to load image")
                }
                else -> {}
            }
        }
    }
}
// LeadDetailViewModelFactory.kt
class LeadDetailViewModelFactory(
    private val leadDetailRepository: LeadDetailRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LeadDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LeadDetailViewModel(leadDetailRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}