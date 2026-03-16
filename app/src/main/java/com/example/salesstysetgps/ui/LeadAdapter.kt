package com.example.salesstysetgps.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.salesstysetgps.databinding.ItemLeadBinding
import com.example.salesstysetgps.models.Lead

class LeadAdapter(private val list: List<Lead>) :
    RecyclerView.Adapter<LeadAdapter.LeadViewHolder>() {

    inner class LeadViewHolder(val binding: ItemLeadBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeadViewHolder {
        val binding = ItemLeadBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LeadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LeadViewHolder, position: Int) {
        val item = list[position]

        holder.binding.tvStoreType.text = "Type: ${item.storeType}"
        holder.binding.tvSellerName.text = "Seller: ${item.sellerName}"
        holder.binding.tvLocation.text = "Location: ${item.location}"
        holder.binding.tvAddress.text = "Address: ${item.address}"
        holder.binding.tvPhone.text = "Phone: ${item.phoneNumber}"
    }

    override fun getItemCount(): Int = list.size
}