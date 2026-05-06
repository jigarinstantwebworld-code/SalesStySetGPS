package com.example.salesstysetgps.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.salesstysetgps.databinding.DialogDateFilterBottomSheetBinding
import com.example.salesstysetgps.models.FilterParams
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar

class DateFilterBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogDateFilterBottomSheetBinding? = null
    private val binding get() = _binding!!

    private var onFilterAppliedListener: ((String?, String?, String?) -> Unit)? = null
    private var selectedStatus: String? = null
    private var selectedStartDate: String? = null
    private var selectedEndDate: String? = null

    companion object {
        fun newInstance(status: String?, startDate: String?, endDate: String?): DateFilterBottomSheetDialogFragment {
            val fragment = DateFilterBottomSheetDialogFragment()
            val args = Bundle()
            args.putString("startDate", startDate)
            args.putString("endDate", endDate)
            args.putString("status", status)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogDateFilterBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load current filters from arguments
        selectedStatus = arguments?.getString("status")
        selectedStartDate = arguments?.getString("startDate")
        selectedEndDate = arguments?.getString("endDate")

        setupDatePickers()
        setupStatusSpinner()  // Add this back
        setupListeners()
        populateCurrentFilters()
    }

    private fun setupStatusSpinner() {
        val statusValues = listOf("", "ACTIVE", "INACTIVE")
        val statusNames = listOf("All", "Active", "Inactive")


    }

    private fun setupDatePickers() {
        binding.etStartDate.setOnClickListener {
            showDatePickerDialog(
                title = "Select Start Date",
                currentDate = selectedStartDate
            ) { date ->
                selectedStartDate = date
                binding.etStartDate.setText(formatDateForDisplay(date))
            }
        }

        binding.etEndDate.setOnClickListener {
            showDatePickerDialog(
                title = "Select End Date",
                currentDate = selectedEndDate
            ) { date ->
                selectedEndDate = date
                binding.etEndDate.setText(formatDateForDisplay(date))
            }
        }
    }

    private fun showDatePickerDialog(
        title: String,
        currentDate: String?,
        onDateSelected: (String) -> Unit
    ) {
        val calendar = Calendar.getInstance()

        // If there's a previously selected date, use that
        if (!currentDate.isNullOrEmpty()) {
            try {
                val parts = currentDate.split("-")
                if (parts.size == 3) {
                    calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                }
            } catch (e: Exception) {
                Log.e("DatePicker", "Error parsing date: $currentDate", e)
            }
        }

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
        datePickerDialog.setTitle(title)
        datePickerDialog.show()
    }



    private fun formatDate(year: Int, month: Int, dayOfMonth: Int): String {
        return String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
    }

    private fun formatDateForDisplay(date: String): String {
        return try {
            val parts = date.split("-")
            if (parts.size == 3) {
                "${parts[2]}/${parts[1]}/${parts[0]}"
            } else {
                date
            }
        } catch (e: Exception) {
            date
        }
    }

    private fun setupListeners() {
        binding.btnApplyFilters.setOnClickListener {
            // Validate date range
            if (selectedStartDate != null && selectedEndDate != null) {
                if (selectedStartDate!! > selectedEndDate!!) {
                    Toast.makeText(requireContext(), "Start date must be before end date", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }
            onFilterAppliedListener?.invoke(selectedStatus, selectedStartDate, selectedEndDate)
            dismiss()
        }

        binding.btnClearFilters.setOnClickListener {
            try {
                if (!isAdded || isRemoving) return@setOnClickListener

                // Clear ALL filters (including status)
//                selectedStatus = null
                selectedStartDate = null
                selectedEndDate = null

                // Update UI for all fields
//                binding.etStatus.setText("All")  // Clear status
                binding.etStartDate.setText("")   // Clear start date
                binding.etEndDate.setText("")     // Clear end date

                // Call listener with null values to clear filters
                onFilterAppliedListener?.invoke(selectedStatus, null, null)

                // Dismiss dialog safely
                if (isAdded && !isRemoving) {
                    dismiss()
                }
            } catch (e: Exception) {
                Log.e("FilterDialog", "Error clearing filters: ${e.message}", e)
            }
        }
    }

    private fun populateCurrentFilters() {
        // Populate status
//        binding.etStatus.setText(getStatusName(selectedStatus))

        // Populate dates
        if (!selectedStartDate.isNullOrEmpty()) {
            binding.etStartDate.setText(formatDateForDisplay(selectedStartDate!!))
        }
        if (!selectedEndDate.isNullOrEmpty()) {
            binding.etEndDate.setText(formatDateForDisplay(selectedEndDate!!))
        }
    }

    private fun getStatusName(status: String?): String {
        return when (status) {
            "ACTIVE" -> "Active"
            "INACTIVE" -> "Inactive"
            else -> "All"
        }
    }

    fun setOnFilterAppliedListener(listener: (String?, String?, String?) -> Unit) {
        onFilterAppliedListener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}