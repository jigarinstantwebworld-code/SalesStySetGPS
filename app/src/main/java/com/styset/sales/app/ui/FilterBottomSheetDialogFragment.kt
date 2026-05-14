package com.styset.sales.app.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.styset.sales.app.models.FilterParams
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.styset.sales.app.databinding.DialogFilterBottomSheetBinding
import java.util.Calendar

class FilterBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogFilterBottomSheetBinding? = null
    private val binding get() = _binding!!

    private var onFilterAppliedListener: ((FilterParams) -> Unit)? = null
    private var currentFilters: FilterParams = FilterParams()

    // Track selected values separately
    private var selectedSortBy: String? = null
    private var selectedSortOrder: String? = null
    private var selectedStartDate: String? = null
    private var selectedEndDate: String? = null
    private var status: String? = null

    companion object {
        fun newInstance(filters: FilterParams): FilterBottomSheetDialogFragment {
            val fragment = FilterBottomSheetDialogFragment()
            val args = Bundle()
            args.putString("sortBy", filters.sortBy)
            args.putString("sortOrder", filters.sortOrder)
            args.putString("startDate", filters.startDate)
            args.putString("endDate", filters.endDate)
            args.putString("status", filters.status)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFilterBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load current filters from arguments
        selectedSortBy = arguments?.getString("sortBy")
        selectedSortOrder = arguments?.getString("sortOrder")
        selectedStartDate = arguments?.getString("startDate")
        selectedEndDate = arguments?.getString("endDate")
        status = arguments?.getString("status")

        // If sortBy is selected but sortOrder is null, set default sortOrder
        if (selectedSortBy != null && selectedSortOrder == null) {
            selectedSortOrder = "asc" // Default to ascending
        }

        currentFilters = FilterParams(
            sortBy = selectedSortBy,
            sortOrder = selectedSortOrder,
            startDate = selectedStartDate,
            endDate = selectedEndDate,
            status = status
        )

        setupDatePickers()
        setupSortingSpinners()
        setupListeners()
        populateCurrentFilters()
    }

    private fun setupDatePickers() {
        // Start Date picker
        binding.etStartDate.setOnClickListener {
            showDatePickerDialog { date ->
                selectedStartDate = date
                binding.etStartDate.setText(formatDateForDisplay(date))
                Log.d("FilterDialog", "Start Date selected: $selectedStartDate")
            }
        }

        // End Date picker
        binding.etEndDate.setOnClickListener {
            showDatePickerDialog { date ->
                selectedEndDate = date
                binding.etEndDate.setText(formatDateForDisplay(date))
                Log.d("FilterDialog", "End Date selected: $selectedEndDate")
            }
        }

        // Make date fields visible
        binding.etStartDate.visibility = View.VISIBLE
        binding.etEndDate.visibility = View.VISIBLE
        (binding.etStartDate.parent as? TextInputLayout)?.visibility = View.VISIBLE
        (binding.etEndDate.parent as? TextInputLayout)?.visibility = View.VISIBLE
    }

    private fun showDatePickerDialog(onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val formattedDate = formatDate(year, month, dayOfMonth)
                onDateSelected(formattedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun formatDate(year: Int, month: Int, dayOfMonth: Int): String {
        return String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
    }

    private fun formatDateForDisplay(date: String): String {
        return try {
            val parts = date.split("-")
            if (parts.size == 3) {
                "${parts[2]}/${parts[1]}/${parts[0]}" // dd/MM/yyyy format for display
            } else {
                date
            }
        } catch (e: Exception) {
            date
        }
    }

    private fun setupSortingSpinners() {
        // Sort By options - only the allowed fields
        val sortFields = listOf(
            "id",
            "name_of_shop",
            "executive_name",
            "date",
            "paid_amount",
            "status",
            "created_date",
            "modified_date"
        )
        val sortFieldNames = listOf(
            "ID",
            "Shop Name",
            "Executive Name",
            "Date",
            "Paid Amount",
            "Status",
            "Created Date",
            "Modified Date"
        )

        binding.etSortBy.setOnClickListener {
            showOptionsDialog(
                title = "Sort By",
                items = sortFieldNames,
                selectedItem = getSortFieldName(selectedSortBy)
            ) { selectedIndex ->
                selectedSortBy = sortFields[selectedIndex]
                binding.etSortBy.setText(sortFieldNames[selectedIndex])
                Log.d("FilterDialog", "Sort By selected: $selectedSortBy")

                // If sortOrder is still null, set a default
                if (selectedSortOrder == null) {
                    selectedSortOrder = "asc"
                    binding.etSortOrder.setText("Ascending")
                    Log.d("FilterDialog", "Set default sort order: asc")
                }
            }
        }

        // Sort Order options
        val sortOrders = listOf("asc", "desc")
        val sortOrderNames = listOf("Ascending", "Descending")

        binding.etSortOrder.setOnClickListener {
            showOptionsDialog(
                title = "Sort Order",
                items = sortOrderNames,
                selectedItem = getSortOrderName(selectedSortOrder)
            ) { selectedIndex ->
                selectedSortOrder = sortOrders[selectedIndex]
                binding.etSortOrder.setText(sortOrderNames[selectedIndex])
                Log.d("FilterDialog", "Sort Order selected: $selectedSortOrder")
            }
        }

        // Hide selling type and status if not needed
        binding.etSellingType.visibility = View.GONE
        binding.etStatus.visibility = View.GONE
        (binding.etSellingType.parent as? TextInputLayout)?.visibility = View.GONE
        (binding.etStatus.parent as? TextInputLayout)?.visibility = View.GONE
    }

    private fun showOptionsDialog(
        title: String,
        items: List<String>,
        selectedItem: String,
        onItemSelected: (Int) -> Unit
    ) {
        val selectedIndex = items.indexOf(selectedItem).takeIf { it >= 0 } ?: 0

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(items.toTypedArray(), selectedIndex) { dialog, which ->
                onItemSelected(which)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupListeners() {
        binding.btnApplyFilters.setOnClickListener {
            Log.d("FilterDialog", "Apply clicked - sortBy: $selectedSortBy, sortOrder: $selectedSortOrder, startDate: $selectedStartDate, endDate: $selectedEndDate")

            // Validate date range if both are selected
            if (selectedStartDate != null && selectedEndDate != null) {
                if (!isValidDateRange(selectedStartDate!!, selectedEndDate!!)) {
                    Toast.makeText(requireContext(), "Start date must be before or equal to end date", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // Create filters with selected values
            val filters = FilterParams(
                sortBy = selectedSortBy,
                sortOrder = selectedSortOrder,
                startDate = null,  // Don't clear dates in ViewModel
                endDate = null,    // Don't clear dates in ViewModel
                status = status
            )

            Log.d("FilterDialog", "Applying filters: $filters")
            onFilterAppliedListener?.invoke(filters)
            dismiss()
        }

        binding.btnClearFilters.setOnClickListener {
            selectedSortBy = null
            selectedSortOrder = null

            // Update UI
            binding.etSortBy.setText("")
            binding.etSortOrder.setText("")

            // Create filters with ONLY sort cleared (preserve dates and status)
            val filters = FilterParams(
                sortBy = null,
                sortOrder = null,
                startDate = null,  // Don't affect dates in ViewModel
                endDate = null,    // Don't affect dates in ViewModel
                status = null      // Don't affect status in ViewModel
            )

            Log.d("FilterDialog", "Clearing sort only: $filters")
            onFilterAppliedListener?.invoke(filters)
            dismiss()
        }
    }

    private fun isValidDateRange(startDate: String, endDate: String): Boolean {
        return try {
            val start = startDate.replace("/", "-")
            val end = endDate.replace("/", "-")
            start <= end
        } catch (e: Exception) {
            true
        }
    }

    private fun populateCurrentFilters() {
        binding.etSortBy.setText(getSortFieldName(selectedSortBy))
        binding.etSortOrder.setText(getSortOrderName(selectedSortOrder))

        if (!selectedStartDate.isNullOrEmpty()) {
            binding.etStartDate.setText(formatDateForDisplay(selectedStartDate!!))
        }
        if (!selectedEndDate.isNullOrEmpty()) {
            binding.etEndDate.setText(formatDateForDisplay(selectedEndDate!!))
        }

        Log.d("FilterDialog", "Populated - sortBy: $selectedSortBy, sortOrder: $selectedSortOrder, startDate: $selectedStartDate, endDate: $selectedEndDate")
    }

    private fun getSortFieldName(sortBy: String?): String {
        return when (sortBy) {
            "id" -> "ID"
            "name_of_shop" -> "Shop Name"
            "executive_name" -> "Executive Name"
            "date" -> "Date"
            "paid_amount" -> "Paid Amount"
            "status" -> "Status"
            "created_date" -> "Created Date"
            "modified_date" -> "Modified Date"
            else -> "Select Sort Field"
        }
    }

    private fun getSortOrderName(order: String?): String {
        return when (order) {
            "asc" -> "Ascending"
            "desc" -> "Descending"
            else -> "Select Order"
        }
    }

    fun setOnFilterAppliedListener(listener: (FilterParams) -> Unit) {
        onFilterAppliedListener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}