package com.styset.sales.app.ui

import android.R.attr.maxHeight
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.styset.sales.app.models.LeadModel
import com.styset.sales.app.models.LeadSelection
import com.google.android.material.button.MaterialButton
import com.styset.sales.app.R


class LeadSelectionDialog(
    private val context: Context,
    private val leads: List<LeadModel>,
    private val preSelectedLeadIds: List<Int> = emptyList(),
    private val onLeadsSelected: (List<LeadModel>) -> Unit
) {

    fun show() {
        // Create a fresh copy of selections
        val leadSelections = leads.map { lead ->
            LeadSelection(
                lead = lead,
                isSelected = preSelectedLeadIds.contains(lead.id)
            )
        }.toMutableList()

        // Variable to track selections in real-time
        var currentSelectedLeads = leadSelections.filter { it.isSelected }.map { it.lead }.toMutableList()

        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }

        // Title
        val titleView = TextView(context).apply {
            text = "Select Leads"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(24, 24, 24, 16)
            setTextColor(context.getColor(R.color.md_theme_light_primary))
        }
        dialogView.addView(titleView)

        // Subtitle
        val subtitleView = TextView(context).apply {
            text = "Choose leads to associate with this stop"
            textSize = 14f
            setPadding(24, 0, 24, 16)
            setTextColor(context.getColor(R.color.black))
        }
        dialogView.addView(subtitleView)

        // RecyclerView
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = MultiSelectSpinnerAdapter(context, leadSelections) { selected ->
                // Update current selections immediately
                currentSelectedLeads = selected.map { it.lead }.toMutableList()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400
            )
        }
        dialogView.addView(recyclerView)

        // Buttons
        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
        }

        val cancelButton = MaterialButton(context).apply {
            text = "Cancel"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
            setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.transparent)))
            setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_primary))
            setOnClickListener {
                (tag as? AlertDialog)?.dismiss()
            }
        }

        val okButton = MaterialButton(context).apply {
            text = "OK"
            setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.md_theme_light_primary)))
            setTextColor(ContextCompat.getColor(context, R.color.white))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 8
            }
            setOnClickListener {
                // Pass the current selections to callback
                onLeadsSelected(currentSelectedLeads)
                (tag as? AlertDialog)?.dismiss()
            }
        }

        buttonLayout.addView(cancelButton)
        buttonLayout.addView(okButton)
        dialogView.addView(buttonLayout)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        cancelButton.tag = dialog
        okButton.tag = dialog

        dialog.show()

        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}