package com.example.salesstysetgps.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.salesstysetgps.R

class SyncRecordAdapter(
    private var records: List<SyncRecordDisplay>
) : RecyclerView.Adapter<SyncRecordAdapter.SyncRecordViewHolder>() {

    class SyncRecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvApiName: TextView = itemView.findViewById(R.id.tvApiName)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvLastSyncedTime: TextView = itemView.findViewById(R.id.tvLastSyncedTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SyncRecordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sync_record, parent, false)
        return SyncRecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: SyncRecordViewHolder, position: Int) {
        val record = records[position]
        holder.tvApiName.text = record.apiName
        holder.tvLastSyncedTime.text = record.lastSyncedTime

        val statusText = when (record.status) {
            1 -> "SUCCESS"
            0 -> "FAILED"
            else -> "UNKNOWN"
        }
        holder.tvStatus.text = statusText
        val isSuccess = record.status == 1

        // Set background tint based on status
        val backgroundColor = if (isSuccess) {
            android.graphics.Color.parseColor("#4CAF50") // Green for success
        } else {
            android.graphics.Color.parseColor("#F44336") // Red for failure
        }
        holder.tvStatus.setBackgroundColor(backgroundColor)
        holder.tvStatus.setTextColor(android.graphics.Color.WHITE)
    }

    override fun getItemCount(): Int = records.size

    fun updateRecords(newRecords: List<SyncRecordDisplay>) {
        records = newRecords
        notifyDataSetChanged()
    }
}