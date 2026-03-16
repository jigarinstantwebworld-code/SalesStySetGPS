package com.example.salesstysetgps.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.salesstysetgps.R
import com.example.salesstysetgps.services.GoogleCalendarService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


class UpcomingSchedulesFragment : Fragment() {

    private lateinit var textUpcomingTitle: TextView
    private lateinit var textUpcomingCount: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var calendarService: GoogleCalendarService

    private val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_upcoming_schedules, container, false)

        textUpcomingTitle = view.findViewById(R.id.textUpcomingTitle)
        textUpcomingCount = view.findViewById(R.id.textUpcomingCount)
        recyclerView = view.findViewById(R.id.recyclerViewUpcoming)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendarService = GoogleCalendarService(requireContext())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        loadUpcomingEvents()
    }

    private fun loadUpcomingEvents() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account == null) {
            textUpcomingCount.text = "Please sign in first"
            return
        }

        lifecycleScope.launch {
            val calendar = Calendar.getInstance()
            val startDate = calendar.time
            calendar.add(Calendar.MONTH, 1)
            val endDate = calendar.time

            val allEvents = calendarService.getCalendarEvents(account, startDate, endDate)

            val today = Calendar.getInstance()
            val todayStr = displayFormat.format(today.time)

            val upcomingEvents = allEvents.filter { event ->
                event.getFormattedDate() > todayStr
            }.sortedBy { it.startTime }

            textUpcomingCount.text = "${upcomingEvents.size} upcoming visit(s)"

            val adapter = EventAdapter(upcomingEvents) { event ->
                android.widget.Toast.makeText(
                    requireContext(),
                    "${event.sellerName} on ${event.getFormattedDate()} at ${event.getFormattedTime()}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            recyclerView.adapter = adapter
        }
    }
}