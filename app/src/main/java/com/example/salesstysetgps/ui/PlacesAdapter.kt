package com.example.salesstysetgps.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.salesstysetgps.R
import com.example.salesstysetgps.data.StopPoint

class PlacesAdapter(private val places: List<StopPoint>) : 
    RecyclerView.Adapter<PlacesAdapter.PlaceViewHolder>() {

    class PlaceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tv_place_name)
        val timeText: TextView = view.findViewById(R.id.tv_place_time)
        val coordsText: TextView = view.findViewById(R.id.tv_place_coords)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_place, parent, false)
        return PlaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        val place = places[position]
        holder.nameText.text = place.name ?: "Unnamed Place"
        holder.timeText.text = "Time spent: ${place.timeSpentMinutes} min"
        holder.coordsText.text = if (!place.address.isNullOrBlank()) {
            "Location: ${place.address}"
        } else {
            "Location: ${
                String.format(
                    "%.6f, %.6f",
                    place.center.latitude,
                    place.center.longitude
                )
            }"
        }
    }

    override fun getItemCount() = places.size
}
