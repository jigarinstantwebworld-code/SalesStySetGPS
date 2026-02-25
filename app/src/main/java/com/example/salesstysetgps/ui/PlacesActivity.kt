package com.example.salesstysetgps.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.salesstysetgps.R
import com.example.salesstysetgps.data.StopPoint
import com.example.salesstysetgps.data.StopRepository
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone
import java.util.Locale

class PlacesActivity : AppCompatActivity() {

    private enum class FilterPreset {
        TODAY,
        YESTERDAY,
        LAST_3_DAYS,
        LAST_WEEK,
        LAST_MONTH,
        CUSTOM
    }

    private lateinit var adapter: PlacesCardAdapter
    private lateinit var stopRepository: StopRepository
    private var allPlaces: List<StopPoint> = emptyList()
    private var lastNonCustomPreset: FilterPreset = FilterPreset.TODAY
    private var currentPreset: FilterPreset = FilterPreset.TODAY
    private var customRangeStartMillis: Long? = null
    private var customRangeEndMillis: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_places)

        stopRepository = StopRepository(applicationContext)

        val toolbar = findViewById<Toolbar>(R.id.toolbarPlaces)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { finish() }

        val tvFilterSummary = findViewById<TextView>(R.id.tvFilterSummary)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)

        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupFilters)
        val chipToday = findViewById<Chip>(R.id.chipToday)
        val chipYesterday = findViewById<Chip>(R.id.chipYesterday)
        val chipLast3 = findViewById<Chip>(R.id.chipLast3Days)
        val chipLastWeek = findViewById<Chip>(R.id.chipLastWeek)
        val chipLastMonth = findViewById<Chip>(R.id.chipLastMonth)
        val chipCustom = findViewById<Chip>(R.id.chipCustom)

        val recycler = findViewById<RecyclerView>(R.id.recyclerPlaces)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = PlacesCardAdapter()
        recycler.adapter = adapter

        fun applyFilteredList(preset: FilterPreset, startMillis: Long, endMillis: Long, summaryLabel: String) {
            val filtered = allPlaces.filter { stop ->
                val stopStart = stop.startTimeMillis
                val stopEnd = stop.endTimeMillis ?: stop.startTimeMillis
                stopStart <= endMillis && stopEnd >= startMillis
            }
            adapter.submitList(filtered)
            tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            tvFilterSummary.text = "$summaryLabel  •  ${filtered.size}"
        }

        fun applyPreset(preset: FilterPreset) {
            currentPreset = preset
            val now = System.currentTimeMillis()
            val (start, end, label) = when (preset) {
                FilterPreset.TODAY -> {
                    val s = startOfDayMillis(now)
                    Triple(s, endOfDayMillis(now), getString(R.string.filter_today))
                }
                FilterPreset.YESTERDAY -> {
                    val day = addDays(now, -1)
                    Triple(startOfDayMillis(day), endOfDayMillis(day), getString(R.string.filter_yesterday))
                }
                FilterPreset.LAST_3_DAYS -> {
                    val s = startOfDayMillis(addDays(now, -2))
                    Triple(s, endOfDayMillis(now), getString(R.string.filter_last_3_days))
                }
                FilterPreset.LAST_WEEK -> {
                    val s = startOfDayMillis(addDays(now, -6))
                    Triple(s, endOfDayMillis(now), getString(R.string.filter_last_week))
                }
                FilterPreset.LAST_MONTH -> {
                    val s = startOfDayMillis(addDays(now, -29))
                    Triple(s, endOfDayMillis(now), getString(R.string.filter_last_month))
                }
                FilterPreset.CUSTOM -> {
                    val s = customRangeStartMillis
                    val e = customRangeEndMillis
                    if (s != null && e != null) {
                        val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        val label = "${getString(R.string.filter_custom)}: ${df.format(s)} → ${df.format(e)}"
                        Triple(s, e, label)
                    } else {
                        // No custom range selected yet; default to today.
                        val s0 = startOfDayMillis(now)
                        Triple(s0, endOfDayMillis(now), getString(R.string.filter_today))
                    }
                }
            }
            applyFilteredList(preset, start, end, label)
        }

        fun openCustomRangePicker() {
            val picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(getString(R.string.filter_custom))
                .build()

            picker.addOnPositiveButtonClickListener { selection ->
                val startUtc = selection.first
                val endUtc = selection.second
                if (startUtc != null && endUtc != null) {
                    val startLocal = localStartOfDayFromUtcMidnight(startUtc)
                    val endLocal = localEndOfDayFromUtcMidnight(endUtc)
                    customRangeStartMillis = startLocal
                    customRangeEndMillis = endLocal
                    chipCustom.text = getString(R.string.filter_custom)
                    applyPreset(FilterPreset.CUSTOM)
                }
            }
            picker.addOnNegativeButtonClickListener {
                // Revert to previous non-custom preset if user cancels
                when (lastNonCustomPreset) {
                    FilterPreset.TODAY -> chipToday.isChecked = true
                    FilterPreset.YESTERDAY -> chipYesterday.isChecked = true
                    FilterPreset.LAST_3_DAYS -> chipLast3.isChecked = true
                    FilterPreset.LAST_WEEK -> chipLastWeek.isChecked = true
                    FilterPreset.LAST_MONTH -> chipLastMonth.isChecked = true
                    FilterPreset.CUSTOM -> chipCustom.isChecked = true
                }
            }

            picker.show(supportFragmentManager, "custom_range_picker")
        }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            when (id) {
                R.id.chipToday -> {
                    lastNonCustomPreset = FilterPreset.TODAY
                    applyPreset(FilterPreset.TODAY)
                }
                R.id.chipYesterday -> {
                    lastNonCustomPreset = FilterPreset.YESTERDAY
                    applyPreset(FilterPreset.YESTERDAY)
                }
                R.id.chipLast3Days -> {
                    lastNonCustomPreset = FilterPreset.LAST_3_DAYS
                    applyPreset(FilterPreset.LAST_3_DAYS)
                }
                R.id.chipLastWeek -> {
                    lastNonCustomPreset = FilterPreset.LAST_WEEK
                    applyPreset(FilterPreset.LAST_WEEK)
                }
                R.id.chipLastMonth -> {
                    lastNonCustomPreset = FilterPreset.LAST_MONTH
                    applyPreset(FilterPreset.LAST_MONTH)
                }
                R.id.chipCustom -> {
                    openCustomRangePicker()
                    // We'll apply after selection; until then keep current list.
                }
            }
        }
        // Observe live stops from DB so Places always uses up-to-date data
        lifecycleScope.launch {
            stopRepository.observeStops().collectLatest { list ->
                val completed = list.filter { it.endTimeMillis != null }
                allPlaces = mergeByName(completed).sortedByDescending { it.startTimeMillis }
                applyPreset(currentPreset)
            }
        }

        // Default filter = Today
        chipToday.isChecked = true
    }

    // Same merge logic as TrackingViewModel.getCompletedStopsMergedByName,
    // so Places sees exactly the same visits data.
    private fun mergeByName(stops: List<StopPoint>): List<StopPoint> {
        val named = stops.filter { !it.name.isNullOrBlank() }
            .groupBy { it.name!!.trim() }
            .map { (name, list) ->
                val totalMinutes = list.sumOf { it.timeSpentMinutes }
                val first = list.minByOrNull { it.startTimeMillis }!!
                val last = list.maxByOrNull { it.endTimeMillis ?: it.startTimeMillis }!!
                val stopWithDetails = list.firstOrNull {
                    !it.address.isNullOrBlank() || !it.phone.isNullOrBlank() || !it.imageUri.isNullOrBlank()
                } ?: first
                StopPoint(
                    id = first.id,
                    center = first.center,
                    startTimeMillis = first.startTimeMillis,
                    endTimeMillis = last.endTimeMillis,
                    name = name,
                    address = stopWithDetails.address,
                    phone = stopWithDetails.phone,
                    imageUri = stopWithDetails.imageUri,
                    timeSpentMinutes = totalMinutes
                )
            }
        val unnamed = stops.filter { it.name.isNullOrBlank() }
        return named + unnamed
    }

    private fun startOfDayMillis(timeMillis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = timeMillis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun endOfDayMillis(timeMillis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = timeMillis
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        c.set(Calendar.MILLISECOND, 999)
        return c.timeInMillis
    }

    private fun addDays(timeMillis: Long, days: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = timeMillis
        c.add(Calendar.DAY_OF_YEAR, days)
        return c.timeInMillis
    }

    // MaterialDatePicker returns UTC midnights. Convert those to local start/end of day.
    private fun localStartOfDayFromUtcMidnight(utcMidnightMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.timeInMillis = utcMidnightMillis
        val year = utc.get(Calendar.YEAR)
        val month = utc.get(Calendar.MONTH)
        val day = utc.get(Calendar.DAY_OF_MONTH)

        val local = Calendar.getInstance()
        local.set(Calendar.YEAR, year)
        local.set(Calendar.MONTH, month)
        local.set(Calendar.DAY_OF_MONTH, day)
        local.set(Calendar.HOUR_OF_DAY, 0)
        local.set(Calendar.MINUTE, 0)
        local.set(Calendar.SECOND, 0)
        local.set(Calendar.MILLISECOND, 0)
        return local.timeInMillis
    }

    private fun localEndOfDayFromUtcMidnight(utcMidnightMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.timeInMillis = utcMidnightMillis
        val year = utc.get(Calendar.YEAR)
        val month = utc.get(Calendar.MONTH)
        val day = utc.get(Calendar.DAY_OF_MONTH)

        val local = Calendar.getInstance()
        local.set(Calendar.YEAR, year)
        local.set(Calendar.MONTH, month)
        local.set(Calendar.DAY_OF_MONTH, day)
        local.set(Calendar.HOUR_OF_DAY, 23)
        local.set(Calendar.MINUTE, 59)
        local.set(Calendar.SECOND, 59)
        local.set(Calendar.MILLISECOND, 999)
        return local.timeInMillis
    }

    private class PlacesCardAdapter : RecyclerView.Adapter<PlacesCardAdapter.VH>() {
        private var items: List<StopPoint> = emptyList()
        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        fun submitList(newItems: List<StopPoint>) {
            items = newItems
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvName)
            val tvAddress: TextView = view.findViewById(R.id.tvAddress)
            val tvPhone: TextView = view.findViewById(R.id.tvPhone)
            val imgSeller: ImageView = view.findViewById(R.id.imgSeller)
            val tvCoords: TextView = view.findViewById(R.id.tvCoords)
            val tvDuration: TextView = view.findViewById(R.id.tvDuration)
            val tvWindow: TextView = view.findViewById(R.id.tvWindow)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_place_card, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val it = items[position]
            holder.tvName.text = it.name ?: "(unnamed)"
            
            // Address
            if (!it.address.isNullOrBlank()) {
                holder.tvAddress.text = it.address
                holder.tvAddress.visibility = View.VISIBLE
            } else {
                holder.tvAddress.visibility = View.GONE
            }
            
            // Phone
            if (!it.phone.isNullOrBlank()) {
                holder.tvPhone.text = "Phone: ${it.phone}"
                holder.tvPhone.visibility = View.VISIBLE
            } else {
                holder.tvPhone.visibility = View.GONE
            }
            
            // Seller image
            if (!it.imageUri.isNullOrBlank()) {
                try {
                    holder.imgSeller.setImageURI(Uri.parse(it.imageUri))
                    holder.imgSeller.visibility = View.VISIBLE
                } catch (e: Exception) {
                    holder.imgSeller.visibility = View.GONE
                }
            } else {
                holder.imgSeller.visibility = View.GONE
            }

            // Location line: prefer address, fall back to coordinates
            holder.tvCoords.text = if (!it.address.isNullOrBlank()) {
                "Location: ${it.address}"
            } else {
                String.format(
                    Locale.getDefault(),
                    "Location: %.5f, %.5f",
                    it.center.latitude,
                    it.center.longitude
                )
            }
            holder.tvDuration.text = "Time spent: ${it.timeSpentMinutes} min"
            val start = sdf.format(it.startTimeMillis)
            val end = it.endTimeMillis?.let { e -> sdf.format(e) } ?: "—"
            holder.tvWindow.text = "$start  →  $end"
        }

        override fun getItemCount(): Int = items.size
    }
}


