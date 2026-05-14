package com.styset.sales.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.styset.sales.app.R
import com.styset.sales.app.databinding.ItemLeadBinding
import com.styset.sales.app.models.Lead
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class LeadAdapter(
    val context: Context,
    private var leads: List<Lead>,
    private val onItemClick: ((Lead) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_LOADING = 1
    }

    private var isLoadingFooterVisible = false

    override fun getItemViewType(position: Int): Int {
        return if (isLoadingFooterVisible && position == leads.size) {
            TYPE_LOADING
        } else {
            TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_LOADING -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_loading, parent, false)
                LoadingViewHolder(view)
            }

            else -> {
                val binding = ItemLeadBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                LeadViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is LeadViewHolder -> holder.bind(leads[position])
            is LoadingViewHolder -> holder.bind()
        }
    }

    override fun getItemCount(): Int {
        return if (isLoadingFooterVisible) leads.size + 1 else leads.size
    }

    fun updateLeads(newLeads: List<Lead>) {
        leads = newLeads
        notifyDataSetChanged()
    }

    fun showLoadingFooter(show: Boolean) {
        if (isLoadingFooterVisible != show) {
            isLoadingFooterVisible = show
            if (show) {
                notifyItemInserted(leads.size)
            } else {
                notifyItemRemoved(leads.size)
            }
        }
    }

    inner class LeadViewHolder(val binding: ItemLeadBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(lead: Lead) {
            binding.apply {
                tvStoreType.text = "🏪 ${lead.nameOfShop ?: "N/A"}"
                tvSellerName.text = "👤 ${lead.contactPerson ?: "N/A"}"
                tvLocation.text = "📍 ${lead.area ?: "N/A"}"
                tvAddress.text = "👔 ${lead.executiveName ?: "N/A"}"
                tvPhone.text = "${lead.mobileNumber ?: "N/A"}"

                try {
                    val status = lead.status?.uppercase() ?: "N/A"
                    tvStatus.text = status

                    when (status) {
                        "ACTIVE" -> {
                            leftBar.setBackgroundResource(R.drawable.bg_status_active)
                            tvAmount.setTextColor(
                                ContextCompat.getColor(
                                    itemView.context,
                                    R.color.green_dark
                                )
                            )
                            tvStatus.setBackgroundResource(R.drawable.bg_status_active)
                            tvStatus.setTextColor(
                                ContextCompat.getColor(
                                    itemView.context,
                                    R.color.green_dark
                                )
                            )
                        }

                        "INACTIVE" -> {
                            leftBar.setBackgroundResource(R.drawable.bg_status_closed)
                            tvAmount.setTextColor(
                                ContextCompat.getColor(
                                    itemView.context,
                                    R.color.red_dark
                                )
                            )
                            tvStatus.setBackgroundResource(R.drawable.bg_status_closed)
                            tvStatus.setTextColor(
                                ContextCompat.getColor(
                                    itemView.context,
                                    R.color.red_dark
                                )
                            )
                        }

                        else -> {
                            tvStatus.setBackgroundResource(R.drawable.bg_status_active)
                            tvStatus.setTextColor(
                                ContextCompat.getColor(
                                    itemView.context,
                                    R.color.green_dark
                                )
                            )
                        }
                    }


                    // Format date
                    val formattedDate = formatDate(lead.date)
                    val amount = "${lead.paidAmount ?: "N/A"}"
                    if (amount.toInt()!=0){
                        tvAmountMain.visibility = View.VISIBLE
                        tvAmount.visibility = View.VISIBLE
                        tvAmount.text = "₹${amount}"
                    }

                    tvDate.text = "📅 Date: $formattedDate"
                } catch (e: Exception) {
                    // Views might not exist, ignore
                }
                binding.btnCall.setOnClickListener {
                    openDialer(lead.mobileNumber)
                }
                binding.btnWhatsApp.setOnClickListener {
                    Toast.makeText(context, "Later if required", Toast.LENGTH_SHORT).show()
                }

                root.setOnClickListener {
                    onItemClick?.invoke(lead)
                }
            }
        }
    }

    inner class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        private val tvLoadingMessage: TextView = itemView.findViewById(R.id.tvLoadingMessage)

        fun bind() {
            // You can customize the loading message or animation here
            progressBar.isIndeterminate = true
            tvLoadingMessage.text = "Loading more leads..."

            // Optional: Add animation
            progressBar.alpha = 0f
            progressBar.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        }

        // Optional: Update loading message
        fun updateMessage(message: String) {
            tvLoadingMessage.text = message
        }
    }

    private fun openDialer(mobileNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$mobileNumber")
        context.startActivity(intent)
    }

    private fun formatDate(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return "N/A"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }
}