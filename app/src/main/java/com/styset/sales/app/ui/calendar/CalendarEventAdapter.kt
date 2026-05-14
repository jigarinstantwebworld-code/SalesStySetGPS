package com.styset.sales.app.ui.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.styset.sales.app.R
import com.styset.sales.app.models.CalendarEvent

class EventAdapter(
    private val events: List<CalendarEvent>,
    private val onItemClick: (CalendarEvent) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(events[position])
    }

    override fun getItemCount() = events.size

    class EventViewHolder(
        itemView: View,
        private val onItemClick: (CalendarEvent) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val textSellerName: TextView = itemView.findViewById(R.id.textSellerName)
        private val textDateTime: TextView = itemView.findViewById(R.id.textDateTime)
        private val textLocation: TextView = itemView.findViewById(R.id.textLocation)
        private val textPurpose: TextView = itemView.findViewById(R.id.textPurpose)

        fun bind(event: CalendarEvent) {
            textSellerName.text = event.sellerName
            textDateTime.text = "${event.getFormattedDate()} • ${event.getFormattedTime()}"
            textLocation.text = event.location ?: "No location"
            textPurpose.text = event.purpose ?: "No description"

            itemView.setOnClickListener {
                onItemClick(event)
            }


        }
    }
}