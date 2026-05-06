package com.example.salesstysetgps.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.location.Location.distanceBetween
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.example.salesstysetgps.R
import com.example.salesstysetgps.data.StopPoint
import com.example.salesstysetgps.data.StopRepository
import com.example.salesstysetgps.data.local.MapSnapshotHelper
import com.example.salesstysetgps.data.local.RouteEntity
import com.example.salesstysetgps.data.local.RoutePointEntity
import com.example.salesstysetgps.location.RouteRepository
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone
import java.util.Locale
import kotlin.collections.map
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.load.DataSource
import java.util.Date
import kotlin.collections.filter

class PlacesActivity : AppCompatActivity() {

    private enum class FilterPreset {
        TODAY,
        YESTERDAY,
        LAST_3_DAYS,
        LAST_WEEK,
        LAST_MONTH,
        CUSTOM
    }

    private lateinit var adapter: RouteAdapter  // Changed from PlacesCardAdapter
    private lateinit var stopRepository: StopRepository
    private lateinit var routeRepository: RouteRepository
    private var allRoutes: List<RouteWithStops> = emptyList()
    private var lastNonCustomPreset: FilterPreset = FilterPreset.TODAY
    private var currentPreset: FilterPreset = FilterPreset.TODAY
    private var customRangeStartMillis: Long? = null
    private var customRangeEndMillis: Long? = null

    // Data class to hold a route with its stops


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_places)

        stopRepository = StopRepository(applicationContext)
        routeRepository = RouteRepository(applicationContext) // Add this

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
        adapter = RouteAdapter(
            this,
            onRouteClick = { clickedRoute ->
                toggleRouteExpansion(clickedRoute)
            },
            routeRepository = routeRepository,
            coroutineScope = lifecycleScope     ,
        )
        recycler.adapter = adapter

        val initialTrackingState = intent.getBooleanExtra("is_tracking", false)
        adapter.setTrackingState(initialTrackingState)

        fun applyFilteredList(preset: FilterPreset, startMillis: Long, endMillis: Long, summaryLabel: String) {
            // Filter routes that started within this time range
            val filteredRoutes = allRoutes.filter { routeWithStops ->
                val routeStart = routeWithStops.route.startTimeMillis
                routeStart in startMillis..endMillis
            }

            // Then, apply expansion logic to the filtered routes
//            val filteredWithExpansion = filteredRoutes.map { routeWithStops ->
//                if (routeWithStops.stops.isEmpty()) {
//                    routeWithStops.copy(isExpanded = true)  // Expand if no stops
//                } else {
//                    routeWithStops.copy(isExpanded = false) // Collapse if has stops
//                }
//            }

            val filtered = allRoutes.filter { routeWithStops ->
                val routeStart = routeWithStops.route.startTimeMillis
                routeStart in startMillis..endMillis
            }.map { routeWithStops ->
                if (routeWithStops.stops.isEmpty()) {
                    routeWithStops.copy(isExpanded = true)
                } else {
                    routeWithStops.copy(isExpanded = false)
                }
            }
            adapter.submitList(filtered)
            val totalStops = filtered.sumOf { it.stops.size }
            tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            tvFilterSummary.text = "$summaryLabel  •  ${filtered.size} routes, $totalStops stops"
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

        lifecycleScope.launch {
            // ✅ CHANGE: Use collect instead of collectLatest to prevent mid-processing cancellation
            routeRepository.observeAllRoutes().collect { routes ->
                Log.d("PLACES_DEBUG", "=== Processing ${routes.size} routes (COLLECT mode) ===")

                val routesWithStops = mutableListOf<RouteWithStops>()

                // ✅ Remove duplicate routes by ID (in case database returns duplicates)
                val uniqueRoutes = routes.distinctBy { it.id }

                if (routes.size != uniqueRoutes.size) {
                    Log.w("PLACES_DEBUG", "Removed ${routes.size - uniqueRoutes.size} duplicate routes")
                }

                for (route in uniqueRoutes) {
                    Log.d("PLACES_DEBUG", "Getting stops for route ${route.id}")
                    val stops = routeRepository.getStopsForRoute(route.id)

                    // ✅ DEDUPLICATE STOPS FIRST (CRITICAL FIX)
                    val deduplicatedStops = deduplicateStops(stops)

                    if (stops.size != deduplicatedStops.size) {
                        Log.w("PLACES_DEBUG", "Route ${route.id}: Removed ${stops.size - deduplicatedStops.size} duplicate stops")
                    }

                    val shouldExpand = deduplicatedStops.isEmpty()

                    // Log for debugging
                    Log.d("PLACES_DEBUG", "Raw stops (${stops.size}):")
                    stops.forEach { stop ->
                        Log.d("PLACES_DEBUG", "  RAW - ID: ${stop.id}, Letter: '${stop.letter}', Name: '${stop.name}', Time: ${stop.startTimeMillis}")
                    }

                    Log.d("PLACES_DEBUG", "After deduplication (${deduplicatedStops.size}):")
                    deduplicatedStops.forEach { stop ->
                        Log.d("PLACES_DEBUG", "  DEDUPED - ID: ${stop.id}, Letter: '${stop.letter}'")
                    }

                    // Then merge by name
                    val mergedStops = mergeByName(deduplicatedStops)

                    Log.d("PLACES_DEBUG", "After mergeByName (${mergedStops.size}):")
                    mergedStops.forEach { stop ->
                        Log.d("PLACES_DEBUG", "  MERGED - ID: ${stop.id}, Letter: '${stop.letter}'")
                    }

                    routesWithStops.add(RouteWithStops(route, mergedStops, isExpanded = shouldExpand))
                }

                // ✅ Sort and store
                val sortedRoutes = routesWithStops.sortedByDescending { it.route.startTimeMillis }
                allRoutes = sortedRoutes

                // ✅ Apply filter
                applyPreset(currentPreset)
            }
        }




        // Default filter = Today
        chipToday.isChecked = true
    }

    private fun deduplicateStops(stops: List<StopPoint>): List<StopPoint> {
        if (stops.size <= 1) return stops

        val uniqueStops = mutableListOf<StopPoint>()
        val seenIds = mutableSetOf<Long>()  // ✅ Track by ID

        // ✅ FIRST: Remove duplicates by ID
        val uniqueById = stops.filter { stop ->
            if (seenIds.contains(stop.id)) {
                Log.d("PLACES_DEBUG", "🗑️ Removing duplicate by ID: ${stop.id}")
                false
            } else {
                seenIds.add(stop.id)
                true
            }
        }

        if (uniqueById.size != stops.size) {
            Log.w("PLACES_DEBUG", "Removed ${stops.size - uniqueById.size} duplicates by ID")
        }

        // ✅ SECOND: Remove duplicates by time + location (within 2 seconds)
        val finalStops = mutableListOf<StopPoint>()
        val seenTimeLocation = mutableSetOf<String>()

        for (stop in uniqueById.sortedBy { it.startTimeMillis }) {
            // Create a key based on time (rounded to 2 seconds) and location (rounded to 4 decimal places)
            val timeSlot = stop.startTimeMillis / 1000  // Second precision
            val lat = String.format("%.4f", stop.center.latitude)
            val lng = String.format("%.4f", stop.center.longitude)
            val key = "${stop.letter}_${timeSlot}_${lat}_${lng}"

            if (!seenTimeLocation.contains(key)) {
                seenTimeLocation.add(key)
                finalStops.add(stop)
            } else {
                Log.d("PLACES_DEBUG", "🗑️ Removing duplicate by time/location: ID=${stop.id}, Letter=${stop.letter}")
            }
        }

        Log.d("PLACES_DEBUG", "Final stops after deduplication: ${finalStops.size} (original: ${stops.size})")

        return finalStops
    }


    private fun getCurrentFilterRange(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        return when (currentPreset) {
            FilterPreset.TODAY -> {
                calendar.timeInMillis = now
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                Pair(start, calendar.timeInMillis)
            }
            FilterPreset.YESTERDAY -> {
                calendar.timeInMillis = now
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                Pair(start, calendar.timeInMillis)
            }
            FilterPreset.LAST_3_DAYS -> {
                calendar.timeInMillis = now
                calendar.add(Calendar.DAY_OF_YEAR, -2)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis

                calendar.timeInMillis = now
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                Pair(start, calendar.timeInMillis)
            }
            FilterPreset.LAST_WEEK -> {
                calendar.timeInMillis = now
                calendar.add(Calendar.DAY_OF_YEAR, -6)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis

                calendar.timeInMillis = now
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                Pair(start, calendar.timeInMillis)
            }
            FilterPreset.LAST_MONTH -> {
                calendar.timeInMillis = now
                calendar.add(Calendar.DAY_OF_YEAR, -29)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis

                calendar.timeInMillis = now
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                Pair(start, calendar.timeInMillis)
            }
            FilterPreset.CUSTOM -> {
                Pair(customRangeStartMillis ?: 0, customRangeEndMillis ?: 0)
            }
        }
    }

    // Same merge logic but for stops within a route
    private fun mergeByName(stops: List<StopPoint>): List<StopPoint> {
        if (stops.isEmpty()) return stops

        // ✅ First, ensure no duplicate IDs
        val uniqueById = stops.distinctBy { it.id }

        // Handle named stops (merge by name)
        val named = uniqueById.filter { !it.name.isNullOrBlank() }
            .groupBy { it.name!!.trim() }
            .map { (name, list) ->
                val totalMinutes = list.sumOf { it.timeSpentMinutes }
                val first = list.minByOrNull { it.startTimeMillis }!!
                val last = list.maxByOrNull { it.endTimeMillis ?: it.startTimeMillis }!!
                val stopWithDetails = list.firstOrNull {
                    !it.locationLabel.isNullOrBlank() || !it.address.isNullOrBlank() ||
                            !it.phone.isNullOrBlank() || !it.imageUri.isNullOrBlank()
                } ?: first

                StopPoint(
                    id = first.id,
                    center = first.center,
                    startTimeMillis = first.startTimeMillis,
                    endTimeMillis = last.endTimeMillis,
                    name = name,
                    locationLabel = stopWithDetails.locationLabel,
                    address = stopWithDetails.address,
                    phone = stopWithDetails.phone,
                    imageUri = stopWithDetails.imageUri,
                    timeSpentMinutes = totalMinutes,
                    letter = first.letter  // Keep earliest letter
                )
            }

        // ✅ FIXED: Handle unnamed stops with duplicate prevention
        val unnamed = uniqueById.filter { it.name.isNullOrBlank() }
            .groupBy { it.letter }  // Group by letter first
            .flatMap { (letter, sameLetterStops) ->
                if (sameLetterStops.size > 1) {
                    // Multiple stops with same letter - keep the earliest one
                    Log.w("PLACES_DEBUG", "Found ${sameLetterStops.size} stops with letter '$letter', keeping earliest")
                    val earliest = sameLetterStops.minByOrNull { it.startTimeMillis }
                    listOfNotNull(earliest)
                } else {
                    sameLetterStops
                }
            }
            .mapIndexed { index, stop ->
                if (stop.letter.isNullOrBlank()) {
                    val newLetter = ('A'.plus(named.size + index)).toString()
                    stop.copy(letter = newLetter)
                } else {
                    // Check for letter conflict with named stops
                    val letterInUse = named.any { it.letter == stop.letter }
                    if (letterInUse) {
                        val newLetter = ('A'.plus(named.size + index)).toString()
                        Log.d("PLACES_DEBUG", "Letter conflict for stop ${stop.id}: ${stop.letter} → $newLetter")
                        stop.copy(letter = newLetter)
                    } else {
                        stop
                    }
                }
            }

        val result = (named + unnamed).sortedBy { it.startTimeMillis }

        // ✅ Final check: Ensure all letters are unique
        val letterGroups = result.groupBy { it.letter }
        val duplicates = letterGroups.filter { it.value.size > 1 }
        if (duplicates.isNotEmpty()) {
            Log.e("PLACES_DEBUG", "⚠️ Duplicate letters found after merge: ${duplicates.keys}")
            // Reassign letters to fix
            return result.mapIndexed { index, stop ->
                stop.copy(letter = ('A'.plus(index)).toString())
            }
        }

        return result
    }

    // Date helper functions (keep your existing ones)
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


    fun toggleRouteExpansion(clickedRoute: RouteWithStops) {
        val position = allRoutes.indexOfFirst { it.route.id == clickedRoute.route.id }

        if (position != -1) {
            // Update the master list
            val updatedMasterList = allRoutes.toMutableList()
            updatedMasterList[position] = updatedMasterList[position].copy(
                isExpanded = !updatedMasterList[position].isExpanded
            )
            allRoutes = updatedMasterList

            // Re-apply current filter to get updated display list
            val (startMillis, endMillis) = getCurrentFilterRange()

            val filteredForDisplay = allRoutes.filter { route ->
                route.route.startTimeMillis in startMillis..endMillis
            }.map { route ->
                if (route.stops.isEmpty()) {
                    route.copy(isExpanded = route.isExpanded) // Preserve expansion for no-stop routes
                } else {
                    route
                }
            }

            adapter.submitList(filteredForDisplay)

            Log.d("EXPAND_DEBUG", "Toggled route ${clickedRoute.route.id} and refreshed filter")
        }
    }


}
