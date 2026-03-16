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

        // Observe routes and their stops
        // In PlacesActivity.kt - Update your observer

        lifecycleScope.launch {
            routeRepository.observeAllRoutes().collectLatest { routes ->
                Log.d("LETTER_FLOW", "=== Processing ${routes.size} routes ===")

                val routesWithStops = mutableListOf<RouteWithStops>()

                for (route in routes) {

                    Log.d("LETTER_FLOW", "Getting stops for route ${route.id}")
                    val stops = routeRepository.getStopsForRoute(route.id)

                    val shouldExpand = stops.isEmpty()
                    // Log stops BEFORE any processing
                    Log.d("LETTER_FLOW", "Raw stops from repository for route ${route.id}:")
                    stops.forEach { stop ->
                        Log.d("LETTER_FLOW", "  RAW - ID: ${stop.id}, Letter: '${stop.letter}', Name: '${stop.name}'")
                    }

                    // If you're using mergeByName, log after merging
                    val mergedStops = mergeByName(stops)
                    Log.d("LETTER_FLOW", "After mergeByName:")
                    mergedStops.forEach { stop ->
                        Log.d("LETTER_FLOW", "  MERGED - ID: ${stop.id}, Letter: '${stop.letter}'")
                    }

                    routesWithStops.add(RouteWithStops(route, mergedStops,isExpanded = shouldExpand))
                }

                allRoutes = routesWithStops.sortedByDescending { it.route.startTimeMillis }
                applyPreset(currentPreset)
            }
        }




        // Default filter = Today
        chipToday.isChecked = true
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
        // First, log original stops with their letters
        stops.forEach { stop ->
            Log.d("MERGE_DEBUG", "Original - Stop ${stop.id}: name='${stop.name}', letter='${stop.letter}'")
        }

        val named = stops.filter { !it.name.isNullOrBlank() }
            .groupBy { it.name!!.trim() }
            .map { (name, list) ->
                val totalMinutes = list.sumOf { it.timeSpentMinutes }
                val first = list.minByOrNull { it.startTimeMillis }!!
                val last = list.maxByOrNull { it.endTimeMillis ?: it.startTimeMillis }!!
                val stopWithDetails = list.firstOrNull {
                    !it.locationLabel.isNullOrBlank() || !it.address.isNullOrBlank() || !it.phone.isNullOrBlank() || !it.imageUri.isNullOrBlank()
                } ?: first

                // ✅ IMPORTANT: When merging stops with same name, we need to decide which letter to keep
                // Usually keep the letter from the first stop in the group
                val mergedLetter = if (list.size > 1) {
                    // Multiple stops with same name - keep the earliest letter
                    list.minByOrNull { it.startTimeMillis }?.letter
                } else {

                    first.letter
                }

                Log.d("MERGE_DEBUG", "Merging group '$name' with ${list.size} stops, keeping letter: $mergedLetter")

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
                    letter = mergedLetter  // ✅ PRESERVE ORIGINAL LETTER
                )
            }

        val unnamed = stops.filter { it.name.isNullOrBlank() }
            .map { stop ->
                // ✅ For unnamed stops, ALWAYS preserve original letter
                if (stop.letter.isNullOrBlank()) {
                    // Only assign a new letter if it doesn't have one
                    val newLetter = ('A'.plus(stops.indexOf(stop))).toString()
                    Log.d("MERGE_DEBUG", "Stop ${stop.id} has no letter, assigning: $newLetter")
                    stop.copy(letter = newLetter)
                } else {
                    Log.d("MERGE_DEBUG", "Stop ${stop.id} preserving original letter: ${stop.letter}")
                    stop  // Keep original letter
                }
            }

        val result = (named + unnamed).sortedBy { it.startTimeMillis }

        // Log final results
        result.forEachIndexed { index, stop ->
            Log.d("MERGE_DEBUG", "Final - Stop ${stop.id}: letter='${stop.letter}', position=$index")
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

    /*fun toggleRouteExpansion(clickedRoute: RouteWithStops) {
        // Find the position in the current list
        val position = allRoutes.indexOfFirst { it.route.id == clickedRoute.route.id }

        if (position != -1) {
            // Create a mutable copy of the list
            val updatedRoutes = allRoutes.toMutableList()
            // Toggle the expansion state
            val routeToUpdate = updatedRoutes[position]
            updatedRoutes[position] = routeToUpdate.copy(isExpanded = !routeToUpdate.isExpanded)
            // Update the original list
            allRoutes = updatedRoutes
            adapter.submitList(allRoutes)  // This will refresh everything
            Log.d("EXPAND_DEBUG", "Toggled route ${clickedRoute.route.id} to ${!routeToUpdate.isExpanded}")
        }
    }*/

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

    // New Adapter for Routes with Expandable Stops
    // New Adapter for Routes with Expandable Stops
    private class RouteAdapter(
        private val context: Context,
        private val onRouteClick: (RouteWithStops) -> Unit,
        private val routeRepository: RouteRepository,  // Add this
        private val coroutineScope: CoroutineScope
    ) : RecyclerView.Adapter<RouteAdapter.ViewHolder>() {




        private var isTrackingActive: Boolean = false
        private var routes: List<RouteWithStops> = emptyList()
        private val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        private val fullDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        private val snapshotHelper = MapSnapshotHelper(context) // You'll need to pass context


        fun setTrackingState(isTracking: Boolean) {
            this.isTrackingActive = isTracking
            notifyDataSetChanged()
        }


        fun submitList(newRoutes: List<RouteWithStops>) {
            routes = newRoutes
            notifyDataSetChanged()
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            // Route header views
            val tvRouteDate: TextView = view.findViewById(R.id.tvRouteDate)
            val tvRouteTime: TextView = view.findViewById(R.id.tvRouteTime)
            val tvRouteSummary: TextView = view.findViewById(R.id.tvRouteSummary)
            val ivExpandIcon: ImageView = view.findViewById(R.id.ivExpandIcon)

            // Stops container
            val stopsContainer: LinearLayout = view.findViewById(R.id.stopsContainer)

            // ✅ ADD THIS - Route header layout for clicking
            val routeHeader: LinearLayout = view.findViewById(R.id.routeHeader)

            // ✅ ADD THIS - Route preview container
            val routePreviewContainer: LinearLayout = view.findViewById(R.id.routePreviewContainer)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_route_with_stops, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val routeWithStops = routes[position]
            val route = routeWithStops.route
            val stops = routeWithStops.stops
            val context = holder.itemView.context

            // Format route header
            val routeDate = fullDateFormat.format(route.startTimeMillis)
            val startTime = dateFormat.format(route.startTimeMillis)
            val endTime = route.endTimeMillis?.let { dateFormat.format(it) } ?: "Ongoing"

            holder.tvRouteDate.text = routeDate
            holder.tvRouteTime.text = "$startTime - $endTime"

            val totalMinutes = stops.sumOf { it.timeSpentMinutes }
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            val durationText = if (hours > 0) {
                "${hours}h ${minutes}m"
            } else {
                "${minutes}m"
            }

            holder.tvRouteSummary.text = "${stops.size} stops • $durationText total"

            // Set expand/collapse icon
            holder.ivExpandIcon.setImageResource(
                if (routeWithStops.isExpanded)
                    android.R.drawable.arrow_up_float
                else
                    android.R.drawable.arrow_down_float
            )



            // Clear and populate stops container
            holder.stopsContainer.removeAllViews()
            holder.routePreviewContainer.removeAllViews()

            if (routeWithStops.isExpanded) {
                holder.stopsContainer.visibility = View.VISIBLE
                val snapshotView = LayoutInflater.from(context).inflate(R.layout.item_route_snapshot, holder.routePreviewContainer, true)

                val ivRouteSnapshot: ImageView = snapshotView.findViewById(R.id.ivRouteSnapshot)
                val tvRouteStats: TextView = snapshotView.findViewById(R.id.tvRouteStats)
                val tvNoScreenshot: TextView = snapshotView.findViewById(R.id.tvNoScreenshot)
                val progressBar: ProgressBar = snapshotView.findViewById(R.id.progressBar)
                loadRouteDistance(route.id) { distance ->
                    tvRouteStats.text = "Total distance: ~${String.format("%.2f", distance)} km"
                }

                progressBar.visibility = View.VISIBLE
                if (!route.screenshotPath.isNullOrEmpty()) {
                    val file = File(route.screenshotPath)
                    if (file.exists()) {
                        Glide.with(context)
                            .load(file)
                            .error(R.drawable.circle_background)
                            .into(ivRouteSnapshot)
                        ivRouteSnapshot.visibility = View.VISIBLE
                        tvNoScreenshot.visibility = View.GONE
                        progressBar.visibility = View.GONE
                    } else {
                        ivRouteSnapshot.visibility = View.GONE
                        tvNoScreenshot.visibility = View.VISIBLE
                        tvNoScreenshot.text = "Screenshot not available"
                    }
                } else {
                    progressBar.visibility = View.GONE
                    ivRouteSnapshot.visibility = View.GONE
                    tvNoScreenshot.visibility = View.VISIBLE
                    tvNoScreenshot.text = "No screenshot saved"
                }

                stops.forEachIndexed { index, stop ->
                    val stopView = LayoutInflater.from(context)
                        .inflate(R.layout.item_stop_in_route, holder.stopsContainer, false)

                    val tvStopLetter = stopView.findViewById<TextView>(R.id.tvStopLetter)
                    val tvStopName = stopView.findViewById<TextView>(R.id.tvStopName)
                    val tvStopTime = stopView.findViewById<TextView>(R.id.tvStopTime)
                    val tvStopDuration = stopView.findViewById<TextView>(R.id.tvStopDuration)
                    val tvStopAddress = stopView.findViewById<TextView>(R.id.tvStopAddress)

                    val letterToShow = if (!stop.letter.isNullOrBlank()) {
                        stop.letter
                    } else {
                        ('A'.plus(index)).toString() // Fallback
                    }
                    tvStopLetter.text = letterToShow

                    // Stop name
                    tvStopName.text = stop.name ?: "Unnamed Stop"

                    // Time range
                    val stopStart = dateFormat.format(stop.startTimeMillis)
                    val stopEnd = stop.endTimeMillis?.let { dateFormat.format(it) } ?: ""
                    tvStopTime.text = "$stopStart - $stopEnd"

                    // Duration
                    tvStopDuration.text = "${stop.timeSpentMinutes} min"

                    // Address if available
                    if (!stop.address.isNullOrBlank()) {
                        tvStopAddress.text = stop.address
                        tvStopAddress.visibility = View.VISIBLE
                    } else {
                        tvStopAddress.visibility = View.GONE
                    }

                    // Click on stop to view on map
                    stopView.setOnClickListener {
                        val isOngoingRoute = route.endTimeMillis == null

                        if (isTrackingActive && isOngoingRoute) {
                            Toast.makeText(
                                context,
                                "Cannot view stops while route is in progress",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }

                        Log.d("CLICK_DEBUG", "Stop clicked: ${stop.id} - ${stop.name}")
                        val intent = Intent(context, MainActivity::class.java).apply {
                            putExtra("center_lat", stop.center.latitude)
                            putExtra("center_lng", stop.center.longitude)
                            putExtra("stop_id", stop.id)
                            putExtra("from_places", true)
                            putExtra("show_full_route", true)
                            putExtra("route_id", route.id)
                        }
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        context.startActivity(intent)
                    }

                    holder.stopsContainer.addView(stopView)
                }
            } else {
                holder.stopsContainer.visibility = View.GONE
            }

            // ✅ FIXED: Click on route header to expand/collapse
            holder.routeHeader.setOnClickListener {
                val isOngoingRoute = route.endTimeMillis == null

                if (isTrackingActive && isOngoingRoute) {
                    // Show a toast or snackbar indicating route is in progress
                    Toast.makeText(
                        context,
                        "This route is still in progress. Complete tracking to view details.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                if (routeWithStops.stops.isEmpty()) {
                    // No stops - go directly to MainActivity to show route
                    Log.d("ROUTE_CLICK", "No stops, showing full route")
                    val intent = Intent(context, MainActivity::class.java).apply {
                        putExtra("show_full_route", true)
                        putExtra("route_id", route.id)
                        putExtra("from_places", true)
                        putExtra("has_stops", false)  // Flag indicating no stops
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    context.startActivity(intent)
                } else {
                    // Has stops - expand/collapse as normal
                    onRouteClick(routeWithStops)
                }
            }

            holder.ivExpandIcon.setOnClickListener {
                onRouteClick(routeWithStops)
            }

            // Make sure the whole item doesn't interfere
            holder.itemView.setOnClickListener(null)
        }

        override fun getItemCount(): Int = routes.size



        private fun calculateDistance(points: List<RoutePointEntity>): Double {
            if (points.size < 2) return 0.0

            var totalDistance = 0.0
            for (i in 0 until points.size - 1) {
                val p1 = LatLng(points[i].latitude, points[i].longitude)
                val p2 = LatLng(points[i + 1].latitude, points[i + 1].longitude)
                totalDistance += distanceBetween(p1, p2)
            }
            return totalDistance / 1000.0 // Convert to km
        }

        private fun calculateBounds(latLngs: List<LatLng>): LatLngBounds {
            val builder = LatLngBounds.builder()
            latLngs.forEach { builder.include(it) }
            return builder.build()
        }

        private fun distanceBetween(p1: LatLng, p2: LatLng): Double {
            val R = 6371e3 // metres
            val φ1 = Math.toRadians(p1.latitude)
            val φ2 = Math.toRadians(p2.latitude)
            val Δφ = Math.toRadians(p2.latitude - p1.latitude)
            val Δλ = Math.toRadians(p2.longitude - p1.longitude)

            val a = sin(Δφ / 2) * sin(Δφ / 2) +
                    cos(φ1) * cos(φ2) *
                    sin(Δλ / 2) * sin(Δλ / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))

            return R * c
        }

        private fun calculateRouteDistance(points: List<RoutePointEntity>): Double {
            if (points.size < 2) return 0.0

            var totalDistance = 0.0
            for (i in 0 until points.size - 1) {
                val p1 = LatLng(points[i].latitude, points[i].longitude)
                val p2 = LatLng(points[i + 1].latitude, points[i + 1].longitude)
                totalDistance += distanceBetween(p1, p2)
            }
            return totalDistance / 1000.0 // Convert to kilometers
        }

        private fun loadRouteDistance(routeId: Long, callback: (Double) -> Unit) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val routePoints = routeRepository.getRoutePoints(routeId)
                    val distance = calculateRouteDistance(routePoints)

                    withContext(Dispatchers.Main) {
                        callback(distance)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        callback(0.0)
                    }
                }
            }
        }




    }
}
