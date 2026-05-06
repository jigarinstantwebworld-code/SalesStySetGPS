package com.example.salesstysetgps.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.example.salesstysetgps.R
import com.example.salesstysetgps.models.LeadModel
import com.example.salesstysetgps.models.LeadSelection

// MultiSelectSpinnerAdapter.kt
class MultiSelectSpinnerAdapter(
    private val context: Context,
    private val items: List<LeadSelection>,
    private val onSelectionChanged: (List<LeadSelection>) -> Unit
) : RecyclerView.Adapter<MultiSelectSpinnerAdapter.ViewHolder>() {

    // Make this a mutable list that can be updated
    private val selectionStates = items.map { it.isSelected }.toMutableList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_lead_checkbox, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    fun getSelectedLeads(): List<LeadSelection> {
        return items.filterIndexed { index, _ -> selectionStates[index] }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.cbLead)
        private val tvLeadName: TextView = itemView.findViewById(R.id.tvLeadName)

        fun bind(leadSelection: LeadSelection, position: Int) {
            val lead = leadSelection.lead
            tvLeadName.text = lead.nameOfShop
            checkBox.isChecked = selectionStates[position]

            checkBox.setOnCheckedChangeListener(null)
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                selectionStates[position] = isChecked
                leadSelection.isSelected = isChecked
                onSelectionChanged(getSelectedLeads())
            }

            itemView.setOnClickListener {
                checkBox.isChecked = !checkBox.isChecked
            }
        }
    }
}

