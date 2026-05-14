package com.styset.sales.app.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.styset.sales.app.data.local.MapSnapshotHelper
import com.styset.sales.app.data.local.RoutePointEntity
import com.styset.sales.app.location.RouteRepository
import com.google.android.gms.maps.model.LatLng
import com.styset.sales.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RouteAdapter(
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
        // ✅ CRITICAL FIX: Remove duplicate routes by ID
        val uniqueRoutes = newRoutes.distinctBy { it.route.id }

        if (newRoutes.size != uniqueRoutes.size) {
            Log.w("PLACES_ADAPTER", "Removed ${newRoutes.size - uniqueRoutes.size} duplicate routes from adapter")
        }

        // ✅ Also deduplicate stops within each route
        val cleanedRoutes = uniqueRoutes.map { routeWithStops ->
            val uniqueStops = routeWithStops.stops.distinctBy { stop ->
                // Create a unique key for each stop
                if (!stop.name.isNullOrBlank()) {
                    "${stop.name}_${stop.startTimeMillis / 60000}" // Name + minute slot
                } else {
                    "${stop.letter}_${stop.startTimeMillis / 60000}" // Letter + minute slot
                }
            }

            if (uniqueStops.size != routeWithStops.stops.size) {
                Log.w("PLACES_ADAPTER", "Route ${routeWithStops.route.id}: Removed ${routeWithStops.stops.size - uniqueStops.size} duplicate stops")
            }

            routeWithStops.copy(stops = uniqueStops)
        }

        routes = cleanedRoutes

        // ✅ Force full refresh to ensure UI updates correctly
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
//                        intent. addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)

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