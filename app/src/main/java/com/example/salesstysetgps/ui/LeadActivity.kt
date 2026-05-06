package com.example.salesstysetgps.ui

import android.content.Intent
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.salesstysetgps.LeadDetailActivity
import com.example.salesstysetgps.R
import com.example.salesstysetgps.data.network.NetworkClient
import com.example.salesstysetgps.databinding.ActivityLeadBinding
import com.example.salesstysetgps.domain.repository.LeadRepository
import com.example.salesstysetgps.models.FilterParams
import com.example.salesstysetgps.models.Lead
import com.example.salesstysetgps.models.PaginatedLeads
import com.example.salesstysetgps.models.Resource
import com.example.salesstysetgps.presentation.viewmodel.LeadViewModel
import com.example.salesstysetgps.presentation.viewmodel.LeadViewModelFactory
import com.example.salesstysetgps.presentation.viewmodel.base.BaseActivity
import com.example.salesstysetgps.util.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class LeadActivity : BaseActivity<ActivityLeadBinding>() {

    private lateinit var paginationScrollListener: PaginationScrollListener
    private var isLoadingMore = false
    private var searchJob: Job? = null

    private var hasActiveFilters = false
    private var currentSearchQuery: String = ""

    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    private var hasLocation: Boolean = false
    private var currentStatusFilter: String = ""

    companion object {
        private const val CREATE_LEAD_REQUEST_CODE = 1001
        private const val SEARCH_DEBOUNCE_DELAY_MS = 2000L // 3 seconds debounce
    }

    private val viewModel: LeadViewModel by viewModels {
        val apiService = NetworkClient.apiService
        val preferenceManager = PreferenceManager.getInstance(this)
        val leadRepository = LeadRepository(apiService , preferenceManager)
        LeadViewModelFactory(leadRepository)
    }
    private lateinit var adapter: LeadAdapter

    override fun getViewBinding(): ActivityLeadBinding = ActivityLeadBinding.inflate(layoutInflater)

    override fun setupViews() {
        setupToolbar()
        setupRecycler()
        setupSortButton()
        setupSearchView()
        setupFilterChips()
    }

    override fun setupObservers() {
        observeStateFlow(viewModel.leadsState) { resource ->
            handleLeadsResponse(resource)
        }

        observeStateFlow(viewModel.isLoading) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }

        observeStateFlow(viewModel.isLoadingMore) { loadingMore ->
            isLoadingMore = loadingMore
            paginationScrollListener.setLoading(loadingMore)

            if (loadingMore) {
                adapter.showLoadingFooter(true)
            } else {
                adapter.showLoadingFooter(false)
            }
        }

        observeStateFlow(viewModel.leads) { leads ->
            adapter.updateLeads(leads)
            updateEmptyView(leads.isEmpty())
        }

        // Observe search results
        observeStateFlow(viewModel.searchResults) { searchResults ->
            // Check if searchResults is Success and has data
            when (searchResults) {
                is Resource.Success -> {
                    val leads = searchResults.data?.leads ?: emptyList()
                    updateEmptyView(leads.isEmpty())
                }

                is Resource.Error -> {
                    // Handle error if needed
                    if (currentSearchQuery.isNotEmpty()) {
                        showError(searchResults.message ?: "Search failed")
                    }
                }

                else -> {
                    // Loading or idle state - do nothing
                }
            }
        }

        // Observe search query state
        observeStateFlow(viewModel.currentSearchQuery) { query ->
            currentSearchQuery = query
            if (query.isEmpty() && binding.searchEditText.text?.isNotEmpty() == true) {
                binding.searchEditText.setText("")
            }
        }



        lifecycleScope.launch {
            viewModel.currentStartDate.collect { startDate ->
                updateFilterButtonUI()
            }
        }

        lifecycleScope.launch {
            viewModel.currentEndDate.collect { endDate ->
                updateFilterButtonUI()
            }
        }



        setupSortingObserver()
    }

    override fun setupListeners() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            hideError()
//            clearSearch() // Clear search when refreshing
//            viewModel.refreshLeads(currentStatusFilter)

            hideError()

            // Refresh with current filters
            if (currentSearchQuery.isNotEmpty()) {
                viewModel.refreshLeads(

                )
            } else {
                viewModel.refreshLeads()
            }
        }

        binding.retryButton.setOnClickListener {
            if (currentSearchQuery.isNotEmpty()) {
                viewModel.searchLeads(currentSearchQuery)
            } else {
                viewModel.fetchLeads()
            }
        }

        binding.fabCreateLead.setOnClickListener {
            val intent = Intent(this, CreateLeadActivity::class.java)
            intent.putExtra("latitude", currentLatitude)
            intent.putExtra("longitude", currentLongitude)
            startActivityForResult(intent, CREATE_LEAD_REQUEST_CODE)
        }
    }

    private fun setupSearchView() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""

                // Cancel previous search job
                searchJob?.cancel()

                if (query.isEmpty()) {
                    // If search is empty, clear search and load all leads
                    clearSearch()
                } else {
                    // Start new debounced search
                    searchJob = lifecycleScope.launch {
                        delay(SEARCH_DEBOUNCE_DELAY_MS)
                        if (query == binding.searchEditText.text.toString().trim()) {
                            performSearch(query)
                        }
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Handle search action on keyboard
        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchEditText.text.toString().trim()
                if (query.isNotEmpty()) {
                    // Cancel pending debounce and search immediately
                    searchJob?.cancel()
                    performSearch(query)
                }
                true
            } else {
                false
            }
        }
    }

    private fun performSearch(query: String) {
        if (query.isNotEmpty()) {
            Log.d("LeadActivity", "Performing search: $query")
            currentSearchQuery = query

            // Reset pagination for new search
            paginationScrollListener.reset()

            // Pass current filters to search
            viewModel.searchLeads(
                query = query,
                sortBy = viewModel.currentSortBy.value,
                sortOrder = viewModel.currentSortOrder.value,
                status = viewModel.currentStatus.value,
                startDate = viewModel.currentStartDate.value,
                endDate = viewModel.currentEndDate.value
            )

            hideError()
        }
    }

    private fun clearSearch() {
        if (currentSearchQuery.isNotEmpty()) {
            currentSearchQuery = ""
            binding.searchEditText.setText("")

            // Clear search in ViewModel with current filters
            viewModel.clearSearch()

            hideError()
            paginationScrollListener.reset()
        }
    }

    private fun hideError() {
        binding.errorView.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefreshLayout.isRefreshing = false

        if (adapter.itemCount == 0) {
            // Show error view only if no data is loaded
            binding.errorView.visibility = View.VISIBLE
            binding.recyclerViewLeads.visibility = View.GONE
            binding.emptyView.visibility = View.GONE
            binding.errorMessage.text = message
        } else {
            // Show snackbar if data already exists
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun loadData() {
        viewModel.fetchLeads()
        currentLatitude = intent.getDoubleExtra("latitude", 0.0)
        currentLongitude = intent.getDoubleExtra("longitude", 0.0)
        hasLocation = intent.getBooleanExtra("has_location", false)

        if (hasLocation) {
            Log.d("LeadActivity", "Location received: $currentLatitude, $currentLongitude")
        } else {
            Log.d("LeadActivity", "No location available")
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sell Leads"

        // Handle Edge-to-Edge properly
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Toolbar gets top padding
            binding.toolbar.setPadding(
                binding.toolbar.paddingLeft,
                systemBars.top,
                binding.toolbar.paddingRight,
                binding.toolbar.paddingBottom
            )

            // Search layout gets top padding from toolbar
            binding.searchEditText.setPadding(
                binding.searchEditText.paddingLeft,
                systemBars.top,
                binding.searchEditText.paddingRight,
                binding.searchEditText.paddingBottom
            )

            // RecyclerView gets bottom padding
            binding.recyclerViewLeads.setPadding(
                binding.recyclerViewLeads.paddingLeft,
                binding.recyclerViewLeads.paddingTop,
                binding.recyclerViewLeads.paddingRight,
                systemBars.bottom
            )

            insets
        }
    }

    private fun setupSortButton() {
        binding.btnSort.setOnClickListener {
            showFilterBottomSheet()
        }
        binding.btnFilter.setOnClickListener {
            showDateFilterBottomSheet()
        }

    }

    private fun showDateFilterBottomSheet() {
        try {
            val currentStatus = currentStatusFilter
            val currentStartDate = viewModel.currentStartDate.value
            val currentEndDate = viewModel.currentEndDate.value

            Log.d("LeadActivity", "Current date filters - status: $currentStatus, startDate: $currentStartDate, endDate: $currentEndDate")

            val filterDialog = DateFilterBottomSheetDialogFragment.newInstance(
                startDate = currentStartDate,
                endDate = currentEndDate, status = currentStatusFilter
            )

            filterDialog.setOnFilterAppliedListener { status, startDate, endDate ->
                try {
                    Log.d("LeadActivity", "Date filters received - status: $status, startDate: $startDate, endDate: $endDate")


                    if (currentSearchQuery.isNotEmpty()) {
                        viewModel.searchLeads(
                            query = currentSearchQuery,
                            sortBy = viewModel.currentSortBy.value,
                            sortOrder = viewModel.currentSortOrder.value,
                            status = status,
                            startDate = startDate,
                            endDate = endDate
                        )
                    } else {
                        viewModel.fetchLeads(
                            sortBy = viewModel.currentSortBy.value,
                            sortOrder = viewModel.currentSortOrder.value,
                            status = status,
                            startDate = startDate,
                            endDate = endDate
                        )
                    }



                    // Update ViewModel filter states
//                    viewModel.applyFilters(
//                        sortBy = viewModel.currentSortBy.value,
//                        sortOrder = viewModel.currentSortOrder.value,
//                        status = status,
//                        startDate = startDate,
//                        endDate = endDate
//                    )

                    // Reset pagination
                    paginationScrollListener.reset()

                    // Apply filters


                    // Build filter message
                    val filterMessages = mutableListOf<String>()
                    status?.let {
                        val statusText = when (it) {
                            "ACTIVE" -> "Active"
                            "INACTIVE" -> "Inactive"
                            else -> it
                        }
                        filterMessages.add("Status: $statusText")
                    }

                    if (startDate != null && endDate != null) {
                        filterMessages.add("Date: $startDate to $endDate")
                    } else if (startDate != null) {
                        filterMessages.add("From: $startDate")
                    } else if (endDate != null) {
                        filterMessages.add("To: $endDate")
                    }

                    val message = if (filterMessages.isEmpty()) {
                        "All filters cleared"
                    } else {
                        "Applied: ${filterMessages.joinToString(", ")}"
                    }

                    // SAFELY show snackbar - check if binding and root are not null
                    try {
                        if (!isFinishing && !isDestroyed  && binding.root != null) {
                            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                        } else {
                            // Fallback to Toast if Snackbar can't be shown
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Log.e("LeadActivity", "Error showing snackbar: ${e.message}")
                        // Fallback to Toast
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("LeadActivity", "Error applying date filters: ${e.message}", e)
                }
            }

            // Check if fragment manager is available
            if (!isFinishing && !isDestroyed) {
                filterDialog.show(supportFragmentManager, "DateFilterBottomSheet")
            }
        } catch (e: Exception) {
            Log.e("LeadActivity", "Error showing date filter bottom sheet: ${e.message}", e)
        }
    }

    private fun setupRecycler() {
        adapter = LeadAdapter(this, emptyList()) { lead ->
            handleLeadClick(lead)
        }

        val layoutManager = LinearLayoutManager(this)
        binding.recyclerViewLeads.layoutManager = layoutManager
        binding.recyclerViewLeads.adapter = adapter

        paginationScrollListener = PaginationScrollListener(layoutManager) {
            Log.d("LeadActivity", "loadMore triggered from scroll")
            if (!isLoadingMore && !viewModel.isLoading.value) {
                if (currentSearchQuery.isNotEmpty()) {
                    viewModel.loadMoreSearch(currentSearchQuery)
                } else {
                    viewModel.loadMore()
                }
            }
        }
        binding.recyclerViewLeads.addOnScrollListener(paginationScrollListener)
    }

    private fun updateFilterButtonUI() {
        val hasDateFilter = viewModel.currentStartDate.value != null || viewModel.currentEndDate.value != null
        val hasSortFilter = viewModel.currentSortBy.value != null && viewModel.currentSortOrder.value != null

        Log.d("FilterButton", "hasAnyFilter: $hasDateFilter, hasDateFilter: $hasDateFilter, hasSortFilter: $hasSortFilter")

        if (hasDateFilter) {
            // Active filter style - Filled button
            binding.btnFilter.apply {
                // Set filled background
                setBackgroundColor(ContextCompat.getColor(this@LeadActivity, R.color.filter_active_bg))
                setTextColor(ContextCompat.getColor(this@LeadActivity, R.color.filter_active_text))
                icon = ContextCompat.getDrawable(this@LeadActivity, R.drawable.ic_filter_active)
                strokeWidth = 0  // Remove border

                // Show badge
                binding.filterBadge.visibility = View.VISIBLE

                // Update button text with filter count
                val filterCount = listOfNotNull(
                    if (hasDateFilter) "Date" else null,
                    if (hasSortFilter) "Sort" else null
                ).size
                text = if (filterCount > 0) "Filter ($filterCount)" else "Filter"
            }
        } else {
            // Inactive filter style - Outlined button
            binding.btnFilter.apply {
                // Reset to outlined style
                setBackgroundColor(Color.TRANSPARENT)  // Clear background
                setTextColor(ContextCompat.getColor(this@LeadActivity, R.color.gray))
                icon = ContextCompat.getDrawable(this@LeadActivity, R.drawable.ic_filter)
                strokeWidth = 1  // Add border
                strokeColor = ContextCompat.getColorStateList(this@LeadActivity, R.color.gray)

                // Hide badge
                binding.filterBadge.visibility = View.GONE

                // Reset button text
                text = "Filter"
            }
        }
    }

    private fun updateSortButtonUI() {
        val hasSortFilter = viewModel.currentSortBy.value != null && viewModel.currentSortOrder.value != null

        if (hasSortFilter) {
            // Active sort style
            binding.btnSort.apply {
                // Set filled background
                setBackgroundColor(ContextCompat.getColor(this@LeadActivity, R.color.filter_active_bg))
                setTextColor(ContextCompat.getColor(this@LeadActivity, R.color.white))
                strokeWidth = 0

                // Set icon with tint
                val iconDrawable = ContextCompat.getDrawable(this@LeadActivity, R.drawable.ic_sort)
                iconDrawable?.setTint(ContextCompat.getColor(this@LeadActivity, R.color.white))
                icon = iconDrawable

                // Update button text with sort info
                val sortByText = viewModel.getCurrentSortDisplay()
                val sortOrderText = viewModel.getCurrentSortOrderDisplay()

            }
        } else {
            // Inactive sort style (outlined)
            binding.btnSort.apply {
                setBackgroundColor(Color.TRANSPARENT)
                setTextColor(ContextCompat.getColor(this@LeadActivity, R.color.gray))
                strokeWidth = 1
                strokeColor = ContextCompat.getColorStateList(this@LeadActivity, R.color.gray)

                // Set icon with tint
                val iconDrawable = ContextCompat.getDrawable(this@LeadActivity, R.drawable.ic_sort)
                iconDrawable?.setTint(ContextCompat.getColor(this@LeadActivity, R.color.gray))
                icon = iconDrawable

                text = "Sort"
            }
        }
    }

    private fun handleLeadsResponse(resource: Resource<PaginatedLeads>) {
        when (resource) {
            is Resource.Success -> {
                resource.data?.let { paginatedData ->
                    paginationScrollListener.setLastPage(paginatedData.isLastPage)

                    paginationScrollListener.setPaginationInfo(
                        paginatedData.currentPage,
                        paginatedData.totalPages
                    )

                    if (paginatedData.isLastPage) {
                        adapter.showLoadingFooter(false)
                    }

                    // Update empty view based on leads and search query
                    val isEmpty = paginatedData.leads.isEmpty()
                    binding.totalLeadsCount.text = "${paginatedData.totalItems.toString()} Leads"
                    updateEmptyView(isEmpty)

                    Log.d(
                        "LeadActivity",
                        "Response - Page: ${paginatedData.currentPage}, TotalPages: ${paginatedData.totalPages}, IsLastPage: ${paginatedData.isLastPage}, Total items: ${paginatedData.totalItems}, Search Query: '$currentSearchQuery'"
                    )
                }
            }

            is Resource.Error -> {
                if (adapter.itemCount == 0) {
                    showError(resource.message ?: "An error occurred")
                } else {
                    Snackbar.make(binding.root, resource.message ?: "Error", Snackbar.LENGTH_LONG)
                        .show()
                }
                paginationScrollListener.setLoading(false)
                adapter.showLoadingFooter(false)
            }

            else -> { /* Loading and Idle states */
            }
        }
    }

    private fun showNoSearchResults() {
        val emptyTitle = binding.emptyView.findViewById<TextView>(R.id.emptyTitle)
        val emptyMessage = binding.emptyView.findViewById<TextView>(R.id.emptyMessage)

        emptyTitle?.text = "No Results Found"
        emptyMessage?.text = "No leads match your search: \"$currentSearchQuery\""

        binding.emptyView.visibility = View.VISIBLE
        binding.recyclerViewLeads.visibility = View.GONE
        binding.errorView.visibility = View.GONE
    }

    private fun resetEmptyView() {
        val emptyTitle = binding.emptyView.findViewById<TextView>(R.id.emptyTitle)
        val emptyMessage = binding.emptyView.findViewById<TextView>(R.id.emptyMessage)
        emptyTitle?.text = "No Leads Found"
        emptyMessage?.text = "Pull to refresh or check back later"
    }

    private fun handleLeadClick(lead: Lead) {
        val intent = Intent(this, LeadDetailActivity::class.java)
        intent.putExtra("lead_id", lead.id)
        startActivity(intent)
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        if (isEmpty) {
            if (currentSearchQuery.isNotEmpty()) {
                // Show search no results message
                showNoSearchResults()
            } else {
                // Show regular empty state
                resetEmptyView()
                binding.emptyView.visibility = View.VISIBLE
                binding.recyclerViewLeads.visibility = View.GONE
                binding.errorView.visibility = View.GONE
            }
        } else {
            // Has data - hide empty view
            binding.emptyView.visibility = View.GONE
            binding.recyclerViewLeads.visibility = View.VISIBLE
            binding.errorView.visibility = View.GONE
        }
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_LEAD_REQUEST_CODE && resultCode == RESULT_OK) {
            // Refresh leads list and clear search
            clearSearch()
            viewModel.refreshLeads(/*currentStatusFilter*/)
            Toast.makeText(this, "Lead created successfully", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
    }

    private fun setupSortingObserver() {
        lifecycleScope.launch {
            viewModel.currentSortBy.collect {
                updateSortButtonUI()
            }
        }

        lifecycleScope.launch {
            viewModel.currentSortOrder.collect {
                updateSortButtonUI()
            }
        }

        // Also observe sort by and order to show tooltip or badge
        lifecycleScope.launch {
            viewModel.currentSortBy.collect { sortBy ->
                if (sortBy != null) {
                    val sortText = when (sortBy) {
                        "paid_amount" -> "Paid Amount"
                        "name_of_shop" -> "Shop Name"
                        "date" -> "Date"
                        "created_date" -> "Created Date"
                        "modified_date" -> "Modified Date"
                        "executive_name" -> "Executive Name"
                        "status" -> "Status"
                        "id" -> "ID"
                        else -> sortBy
                    }
                    binding.btnSort.contentDescription = "Sorted by $sortText"
                } else {
                    binding.btnSort.contentDescription = "Sort leads"
                }
            }
        }
    }


    private fun showFilterBottomSheet() {
        // Get current filter values from ViewModel
        val currentSortBy = viewModel.currentSortBy.value
        val currentSortOrder = viewModel.currentSortOrder.value
        val currentStatus = currentStatusFilter
        val currentStartDate = viewModel.currentStartDate.value
        val currentEndDate = viewModel.currentEndDate.value

        Log.d("LeadActivity", "Current filters - sortBy: $currentSortBy, sortOrder: $currentSortOrder, status: $currentStatus, startDate: $currentStartDate, endDate: $currentEndDate")

        // Create filter params with current values
        val filterParams = FilterParams(
            sortBy = currentSortBy,
            sortOrder = currentSortOrder,
            status = currentStatus,
            startDate = null,
            endDate = null
        )

        val filterDialog = FilterBottomSheetDialogFragment.newInstance(filterParams)

        filterDialog.setOnFilterAppliedListener { filters ->
            Log.d("LeadActivity", "Filters received: $filters")

            // Reset to first page when applying filters
            paginationScrollListener.reset()

            // Apply all filters at once
            viewModel.applyFilters(
                sortBy = filters.sortBy,
                sortOrder = filters.sortOrder,
                status = viewModel.currentStatus.value,
                startDate = viewModel.currentStartDate.value, // Keep existing start date
                endDate = viewModel.currentEndDate.value      // Keep existing end date
            )

            // Build filter message
            val filterMessages = mutableListOf<String>()

            filters.status?.let { status ->
                val statusText = when (status) {
                    "ACTIVE" -> "Active"
                    "INACTIVE" -> "Inactive"
                    else -> status
                }
                filterMessages.add("Status: $statusText")
            }

            if (filters.startDate != null && filters.endDate != null) {
                filterMessages.add("Date: ${filters.startDate} to ${filters.endDate}")
            } else if (filters.startDate != null) {
                filterMessages.add("From: ${filters.startDate}")
            } else if (filters.endDate != null) {
                filterMessages.add("To: ${filters.endDate}")
            }

            filters.sortBy?.let { sortBy ->
                val sortByText = when (sortBy) {
                    "paid_amount" -> "Paid Amount"
                    "name_of_shop" -> "Shop Name"
                    "date" -> "Date"
                    "created_date" -> "Created Date"
                    "modified_date" -> "Modified Date"
                    "executive_name" -> "Executive Name"
                    "status" -> "Status"
                    "id" -> "ID"
                    else -> sortBy
                }
                val sortOrderText = when (filters.sortOrder) {
                    "asc" -> "Ascending"
                    "desc" -> "Descending"
                    else -> ""
                }
                filterMessages.add("Sort: $sortByText ($sortOrderText)")
            }

            val message = if (filterMessages.isEmpty()) {
                "All filters cleared"
            } else {
                "Applied: ${filterMessages.joinToString(", ")}"
            }

            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }

        filterDialog.show(supportFragmentManager, "FilterBottomSheet")
    }

    private fun setupFilterChips() {
        binding.chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            when (checkedId) {
                binding.chipAll.id -> {
                    currentStatusFilter = ""
                    applyFilter()
                }

                binding.chipActive.id -> {
                    currentStatusFilter = "ACTIVE"
                    applyFilter()
                }

                binding.chipInActive.id -> {
                    currentStatusFilter = "INACTIVE"
                    applyFilter()
                }
            }
        }
    }

    private fun applyFilter() {
        // Reset pagination
        paginationScrollListener.reset()

        // Apply filter
        if (currentSearchQuery.isNotEmpty()) {
            viewModel.searchLeads(currentSearchQuery, status = currentStatusFilter)
        } else {
            viewModel.fetchLeads(status = currentStatusFilter)
        }
    }


}


class PaginationScrollListener(
    private val layoutManager: LinearLayoutManager,
    private val loadMore: () -> Unit
) : RecyclerView.OnScrollListener() {

    private var isLoading = false
    private var isLastPage = false
    private var currentPage = 1
    private var totalPages = 1

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)

        val visibleItemCount = layoutManager.childCount
        val totalItemCount = layoutManager.itemCount
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

        // Check if we're at the end of the list
        val isAtEnd = visibleItemCount + firstVisibleItemPosition >= totalItemCount
        val shouldLoadMore = !isLoading && !isLastPage && isAtEnd && totalItemCount > 0

        if (shouldLoadMore) {
            loadMore()
        }
    }

    fun setLoading(loading: Boolean) {
        isLoading = loading
        Log.d("Pagination", "setLoading: $loading")
    }

    fun setLastPage(lastPage: Boolean) {
        isLastPage = lastPage
        Log.d("Pagination", "setLastPage: $lastPage")
    }

    fun setPaginationInfo(currentPage: Int, totalPages: Int) {
        this.currentPage = currentPage
        this.totalPages = totalPages
        this.isLastPage = currentPage >= totalPages
        Log.d(
            "Pagination",
            "setPaginationInfo - currentPage: $currentPage, totalPages: $totalPages, isLastPage: $isLastPage"
        )
    }


    fun reset() {
        isLoading = false
        isLastPage = false
        currentPage = 1
        Log.d("Pagination", "reset")
    }
}