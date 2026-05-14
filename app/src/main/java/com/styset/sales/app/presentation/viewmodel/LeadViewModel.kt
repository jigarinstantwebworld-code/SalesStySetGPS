package com.styset.sales.app.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.styset.sales.app.domain.repository.LeadRepository
import com.styset.sales.app.models.Lead
import com.styset.sales.app.models.LeadStats
import com.styset.sales.app.models.PaginatedLeads
import com.styset.sales.app.models.PresignedUrlResponse
import com.styset.sales.app.models.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// LeadViewModel.kt
class LeadViewModel(
    private val leadRepository: LeadRepository
) : ViewModel() {

    // State flows for leads
    private val _leadsState = MutableStateFlow<Resource<PaginatedLeads>>(Resource.Loading())
    val leadsState: StateFlow<Resource<PaginatedLeads>> = _leadsState.asStateFlow()

    // Separate state for leads list
    private val _leadsList = MutableStateFlow<List<Lead>>(emptyList())
    val leadsList: StateFlow<List<Lead>> = _leadsList.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _presignedUrlState = MutableStateFlow<Resource<PresignedUrlResponse>>(Resource.Loading())
    val presignedUrlState: StateFlow<Resource<PresignedUrlResponse>> = _presignedUrlState.asStateFlow()

    // Search related states
    private val _searchResults = MutableStateFlow<Resource<PaginatedLeads>?>(null)
    val searchResults: StateFlow<Resource<PaginatedLeads>?> = _searchResults.asStateFlow()

    private val _currentSearchQuery = MutableStateFlow("")
    val currentSearchQuery: StateFlow<String> = _currentSearchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var currentPage = 1
    private var totalPages = 1
    private var isLastPage = false

    // Search pagination variables
    private var currentSearchPage = 1
    private var totalSearchPages = 1
    private var isLastSearchPage = false
    private var searchAllLeads = mutableListOf<Lead>()

    private val _leads = MutableStateFlow<List<Lead>>(emptyList())
    val leads: StateFlow<List<Lead>> = _leads.asStateFlow()

    private val allLeads = mutableListOf<Lead>()

    // Filter states
    private val _currentSortBy = MutableStateFlow<String?>(null)
    val currentSortBy: StateFlow<String?> = _currentSortBy.asStateFlow()

    private val _currentSortOrder = MutableStateFlow<String?>(null)
    val currentSortOrder: StateFlow<String?> = _currentSortOrder.asStateFlow()

    private val _currentStatus = MutableStateFlow<String?>(null)
    val currentStatus: StateFlow<String?> = _currentStatus.asStateFlow()

    private val _currentStartDate = MutableStateFlow<String?>(null)
    val currentStartDate: StateFlow<String?> = _currentStartDate.asStateFlow()

    private val _currentEndDate = MutableStateFlow<String?>(null)
    val currentEndDate: StateFlow<String?> = _currentEndDate.asStateFlow()

    private val _isFilterApplied = MutableStateFlow(false)
    val isFilterApplied: StateFlow<Boolean> = _isFilterApplied.asStateFlow()

    private val _leadStats = MutableStateFlow<LeadStats?>(null)
    val leadStats: StateFlow<LeadStats?> = _leadStats.asStateFlow()

    fun getPresignedUrl(
        bucketName: String,
        fieldLabels: String,
        objectKeys: String,
        onSuccess: (PresignedUrlResponse) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _presignedUrlState.value = Resource.Loading()

            try {
                val result = leadRepository.getPresignedUrl(bucketName, fieldLabels, objectKeys)
                _presignedUrlState.value = result

                when (result) {
                    is Resource.Success -> {
                        result.data?.let { response ->
                            Log.d("ViewModel", "Presigned URL: ${response.presignedUrl}")
                            Log.d("ViewModel", "Object Key: ${response.objectKey}")

                            if (response.presignedUrl.isNotEmpty()) {
                                onSuccess(response)
                            } else {
                                val error = response.resultMsg.ifEmpty { "Failed to get presigned URL" }
                                onError(error)
                            }
                        } ?: run {
                            onError("No data received")
                        }
                    }
                    is Resource.Error -> {
                        onError(result.message ?: "An error occurred")
                    }
                    else -> { /* Loading or Idle state */ }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                Log.e("ViewModel", "Error getting presigned URL", e)
                _presignedUrlState.value = Resource.Error(errorMsg)
                onError(errorMsg)
            }
        }
    }

    fun fetchLeads(
        sortBy: String? = null,
        sortOrder: String? = null,
        status: String? = null,
        startDate: String? = null,
        endDate: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            currentPage = 1
            isLastPage = false
            allLeads.clear()
            _currentSearchQuery.value = ""
            _isSearching.value = false

            // Update filter states
            _currentSortBy.value = sortBy
            _currentSortOrder.value = sortOrder
            _currentStatus.value = status
            _currentStartDate.value = startDate
            _currentEndDate.value = endDate
            _isFilterApplied.value = sortBy != null || status != null || startDate != null || endDate != null

            try {
                val response = leadRepository.fetchLeadsPage(
                    page = currentPage,
                    limit = PAGE_SIZE,
                    sortBy = sortBy,
                    sortOrder = sortOrder,
                    status = status,
                    startDate = startDate,
                    endDate = endDate
                )

                when (response) {
                    is Resource.Success -> {
                        response.data?.let { data ->
                            if (data.success == 1) {
                                allLeads.addAll(data.data.leads)
                                _leads.value = allLeads.toList()
                                totalPages = data.data.pagination.totalPages
                                isLastPage = currentPage >= totalPages

                                Log.d(
                                    "LeadViewModel",
                                    "Fetch success - Items: ${allLeads.size}, SortBy: $sortBy, SortOrder: $sortOrder, Status: $status, StartDate: $startDate, EndDate: $endDate"
                                )

                                val paginatedResult = PaginatedLeads(
                                    leads = allLeads.toList(),
                                    currentPage = currentPage,
                                    totalPages = totalPages,
                                    totalItems = data.data.pagination.totalItems,
                                    isLastPage = isLastPage
                                )
                                _leadsState.value = Resource.Success(paginatedResult)
                                _leadsList.value = allLeads.toList()
                            } else {
                                _leadsState.value = Resource.Error(data.message ?: "Failed to fetch leads")
                            }
                        }
                    }
                    is Resource.Error -> {
                        _leadsState.value = Resource.Error(response.message ?: "An error occurred")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _leadsState.value = Resource.Error(e.message ?: "Unknown error")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMore() {
        if (!isLastPage && !_isLoadingMore.value && !_isLoading.value) {
            viewModelScope.launch {
                _isLoadingMore.value = true

                try {
                    val nextPage = currentPage + 1
                    val response = leadRepository.fetchLeadsPage(
                        page = nextPage,
                        limit = PAGE_SIZE,
                        sortBy = _currentSortBy.value,
                        sortOrder = _currentSortOrder.value,
                        status = _currentStatus.value,
                        startDate = _currentStartDate.value,
                        endDate = _currentEndDate.value
                    )

                    when (response) {
                        is Resource.Success -> {
                            response.data?.let { data ->
                                if (data.success == 1) {
                                    allLeads.addAll(data.data.leads)
                                    _leads.value = allLeads.toList()
                                    currentPage = nextPage
                                    isLastPage = currentPage >= data.data.pagination.totalPages
                                    totalPages = data.data.pagination.totalPages

                                    val paginatedResult = PaginatedLeads(
                                        leads = allLeads.toList(),
                                        currentPage = currentPage,
                                        totalPages = totalPages,
                                        totalItems = data.data.pagination.totalItems,
                                        isLastPage = isLastPage
                                    )
                                    _leadsState.value = Resource.Success(paginatedResult)
                                    _leadsList.value = allLeads.toList()
                                } else {
                                    _leadsState.value = Resource.Error(data.message ?: "Failed to load more")
                                }
                            }
                        }
                        is Resource.Error -> {
                            _leadsState.value = Resource.Error(response.message ?: "Failed to load more")
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    Log.e("LeadViewModel", "Error loading more", e)
                    _leadsState.value = Resource.Error("Failed to load more: ${e.message}")
                } finally {
                    _isLoadingMore.value = false
                }
            }
        }
    }

    fun searchLeads(
        query: String,
        sortBy: String? = null,
        sortOrder: String? = null,
        status: String? = null,
        startDate: String? = null,
        endDate: String? = null
    ) {
        if (query.isEmpty()) {
            clearSearch()
            return
        }

        viewModelScope.launch {
            _isSearching.value = true
            _isLoading.value = true
            _currentSearchQuery.value = query
            currentSearchPage = 1
            isLastSearchPage = false
            searchAllLeads.clear()

            // Update filter states
            _currentSortBy.value = sortBy
            _currentSortOrder.value = sortOrder
            _currentStatus.value = status
            _currentStartDate.value = startDate
            _currentEndDate.value = endDate
            _isFilterApplied.value = sortBy != null || status != null || startDate != null || endDate != null

            try {
                val response = leadRepository.searchLeads(
                    query = query,
                    page = currentSearchPage,
                    limit = PAGE_SIZE,
                    sortBy = sortBy,
                    sortOrder = sortOrder,
                    status = status,
                    startDate = startDate,
                    endDate = endDate
                )


                when (response) {
                    is Resource.Success -> {
                        response.data?.let { data ->
                            if (data.success == 1) {
                                searchAllLeads.addAll(data.data.leads)
                                _leads.value = searchAllLeads.toList()
                                totalSearchPages = data.data.pagination.totalPages
                                isLastSearchPage = currentSearchPage >= totalSearchPages

                                val paginatedResult = PaginatedLeads(
                                    leads = searchAllLeads.toList(),
                                    currentPage = currentSearchPage,
                                    totalPages = totalSearchPages,
                                    totalItems = data.data.pagination.totalItems,
                                    isLastPage = isLastSearchPage
                                )
                                _searchResults.value = Resource.Success(paginatedResult)
                                _leadsState.value = Resource.Success(paginatedResult)
                                _leadsList.value = searchAllLeads.toList()
                            } else {
                                val errorMsg = data.message ?: "Failed to search leads"
                                _searchResults.value = Resource.Error(errorMsg)
                                _leadsState.value = Resource.Error(errorMsg)
                            }
                        }
                    }
                    is Resource.Error -> {
                        val errorMsg = response.message ?: "An error occurred while searching"
                        _searchResults.value = Resource.Error(errorMsg)
                        _leadsState.value = Resource.Error(errorMsg)
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                Log.e("LeadViewModel", "Error searching leads", e)
                _searchResults.value = Resource.Error(errorMsg)
                _leadsState.value = Resource.Error(errorMsg)
            } finally {
                _isLoading.value = false
                _isSearching.value = false
            }
        }
    }

    fun loadMoreSearch(query: String) {
        if (query.isEmpty()) return

        if (!isLastSearchPage && !_isLoadingMore.value && !_isLoading.value) {
            viewModelScope.launch {
                _isLoadingMore.value = true

                try {
                    val nextPage = currentSearchPage + 1
                    val response = leadRepository.searchLeads(
                        query = query,
                        page = nextPage,
                        limit = PAGE_SIZE,
                        sortBy = _currentSortBy.value,
                        sortOrder = _currentSortOrder.value,
                        status = _currentStatus.value,
                        startDate = _currentStartDate.value,
                        endDate = _currentEndDate.value
                    )

                    when (response) {
                        is Resource.Success -> {
                            response.data?.let { data ->
                                if (data.success == 1) {
                                    searchAllLeads.addAll(data.data.leads)
                                    _leads.value = searchAllLeads.toList()
                                    currentSearchPage = nextPage
                                    isLastSearchPage = currentSearchPage >= data.data.pagination.totalPages
                                    totalSearchPages = data.data.pagination.totalPages

                                    val paginatedResult = PaginatedLeads(
                                        leads = searchAllLeads.toList(),
                                        currentPage = currentSearchPage,
                                        totalPages = totalSearchPages,
                                        totalItems = data.data.pagination.totalItems,
                                        isLastPage = isLastSearchPage
                                    )
                                    _searchResults.value = Resource.Success(paginatedResult)
                                    _leadsState.value = Resource.Success(paginatedResult)
                                    _leadsList.value = searchAllLeads.toList()
                                } else {
                                    _leadsState.value = Resource.Error(data.message ?: "Failed to load more search results")
                                }
                            }
                        }
                        is Resource.Error -> {
                            _leadsState.value = Resource.Error(response.message ?: "Failed to load more search results")
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    Log.e("LeadViewModel", "Error loading more search results", e)
                    _leadsState.value = Resource.Error("Failed to load more: ${e.message}")
                } finally {
                    _isLoadingMore.value = false
                }
            }
        }
    }

    fun clearSearch() {
        if (_currentSearchQuery.value.isNotEmpty()) {
            _currentSearchQuery.value = ""
            _searchResults.value = null
            _isSearching.value = false
            fetchLeads(
                sortBy = _currentSortBy.value,
                sortOrder = _currentSortOrder.value,
                status = _currentStatus.value,
                startDate = _currentStartDate.value,
                endDate = _currentEndDate.value
            )
        }
    }

    fun refreshLeads() {
        if (_currentSearchQuery.value.isNotEmpty()) {
            searchLeads(
                query = _currentSearchQuery.value,
                sortBy = _currentSortBy.value,
                sortOrder = _currentSortOrder.value,
                status = _currentStatus.value,
                startDate = _currentStartDate.value,
                endDate = _currentEndDate.value
            )
        } else {
            fetchLeads(
                sortBy = _currentSortBy.value,
                sortOrder = _currentSortOrder.value,
                status = _currentStatus.value,
                startDate = _currentStartDate.value,
                endDate = _currentEndDate.value
            )
        }
    }

    fun getLeadById(leadId: Int): Lead? {
        return _leadsList.value.find { it.id == leadId }
    }

    fun applyFilters(
        sortBy: String? = null,
        sortOrder: String? = null,
        status: String? = null,
        startDate: String? = null,
        endDate: String? = null
    ) {
        // Update filter states
        if (sortBy !== null) {
            _currentSortBy.value = sortBy
        }

        if (sortOrder !== null) {
            _currentSortOrder.value = sortOrder
        }

        if (status !== null) {
            _currentStatus.value = status
        }

        if (startDate !== null) {
            _currentStartDate.value = startDate
        }

        if (endDate !== null) {
            _currentEndDate.value = endDate
        }

        // Update isFilterApplied based on all current filter values
        _isFilterApplied.value = _currentSortBy.value != null ||
                _currentStatus.value != null ||
                _currentStartDate.value != null ||
                _currentEndDate.value != null

        Log.d("LeadViewModel", "Applying filters - sortBy: ${_currentSortBy.value}, sortOrder: ${_currentSortOrder.value}, status: ${_currentStatus.value}, startDate: ${_currentStartDate.value}, endDate: ${_currentEndDate.value}")

        if (_currentSearchQuery.value.isNotEmpty()) {
            searchLeads(
                query = _currentSearchQuery.value,
                sortBy = sortBy,
                sortOrder = sortOrder,
                status = status,
                startDate = startDate,
                endDate = endDate
            )
        } else {
            fetchLeads(
                sortBy = sortBy,
                sortOrder = sortOrder,
                status = status,
                startDate = startDate,
                endDate = endDate
            )
        }
    }

    fun clearAllFilters() {
        _currentSortBy.value = null
        _currentSortOrder.value = null
        _currentStatus.value = null
        _currentStartDate.value = null
        _currentEndDate.value = null
        _isFilterApplied.value = false

        Log.d("LeadViewModel", "Clearing all filters")

        if (_currentSearchQuery.value.isNotEmpty()) {
            searchLeads(_currentSearchQuery.value)
        } else {
            fetchLeads()
        }
    }

    fun clearSorting() {
        _currentSortBy.value = null
        _currentSortOrder.value = null
        _isFilterApplied.value = _currentStatus.value != null ||
                _currentStartDate.value != null ||
                _currentEndDate.value != null

        if (_currentSearchQuery.value.isNotEmpty()) {
            searchLeads(
                query = _currentSearchQuery.value,
                status = _currentStatus.value,
                startDate = _currentStartDate.value,
                endDate = _currentEndDate.value
            )
        } else {
            fetchLeads(
                status = _currentStatus.value,
                startDate = _currentStartDate.value,
                endDate = _currentEndDate.value
            )
        }
    }

    fun getCurrentSortDisplay(): String {
        return when (_currentSortBy.value) {
            "paid_amount" -> "Paid Amount"
            "name_of_shop" -> "Shop Name"
            "date" -> "Date"
            "created_date" -> "Created Date"
            "modified_date" -> "Modified Date"
            "executive_name" -> "Executive Name"
            "status" -> "Status"
            "id" -> "ID"
            else -> "Default"
        }
    }

    fun getCurrentSortOrderDisplay(): String {
        return when (_currentSortOrder.value) {
            "asc" -> "Ascending"
            "desc" -> "Descending"
            else -> ""
        }
    }

    fun filterLeadsByType(sellingType: String): List<Lead> {
        return if (sellingType.isEmpty()) {
            _leadsList.value
        } else {
            _leadsList.value.filter { it.sellingType == sellingType }
        }
    }

    fun filterLeadsByStatus(status: String): List<Lead> {
        return if (status.isEmpty()) {
            _leadsList.value
        } else {
            _leadsList.value.filter { it.status == status }
        }
    }

    companion object {
        private const val PAGE_SIZE = 10
    }
}


class LeadViewModelFactory(
    private val leadRepository: LeadRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LeadViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LeadViewModel(leadRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}