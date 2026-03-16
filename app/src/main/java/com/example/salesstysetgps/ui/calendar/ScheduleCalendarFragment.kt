package com.example.salesstysetgps.ui.calendar

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CalendarView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.salesstysetgps.R
import com.example.salesstysetgps.VisitDetailActivity
import com.example.salesstysetgps.models.CalendarEvent
import com.example.salesstysetgps.services.GoogleCalendarService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


class ScheduleCalendarFragment : Fragment() {

    private lateinit var calendarView: CalendarView
    private lateinit var textSelectedDate: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var calendarService: GoogleCalendarService

    private var allEvents = listOf<CalendarEvent>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_schedule_calendar, container, false)

        calendarView = view.findViewById(R.id.calendarView)
        textSelectedDate = view.findViewById(R.id.textSelectedDate)
        recyclerView = view.findViewById(R.id.recyclerViewEvents)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendarService = GoogleCalendarService(requireContext())

        setupCalendar()
        setupRecyclerView()
        loadEvents()
    }

    private fun setupCalendar() {
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth)
            val selectedDate = dateFormat.format(calendar.time)
            textSelectedDate.text = displayFormat.format(calendar.time)

            // Filter events for selected date
            val filteredEvents = allEvents.filter { event ->
                event.getFormattedDate() == displayFormat.format(calendar.time)
            }
            updateRecyclerView(filteredEvents)
        }

        // Select today by default
        val today = Calendar.getInstance()
        calendarView.date = today.timeInMillis
        textSelectedDate.text = displayFormat.format(today.time)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun loadEvents() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account == null) {
            textSelectedDate.text = "Please sign in first"
            return
        }

        lifecycleScope.launch {
            val calendar = Calendar.getInstance()
            val startDate = calendar.time
            calendar.add(Calendar.MONTH, 1)
            val endDate = calendar.time

            allEvents = calendarService.getCalendarEvents(account, startDate, endDate)

            Log.e("TAG", "loadEvents: 0000000000000 ${allEvents.size}", )
            for (i in allEvents){
                Log.e("TAG", "loadEvents: ------${i.description}", )
            }

            // Show today's events
            val today = Calendar.getInstance()
            val todayEvents = allEvents.filter { event ->
                event.getFormattedDate() == displayFormat.format(today.time)
            }
            updateRecyclerView(todayEvents)

            textSelectedDate.text =
                "${displayFormat.format(today.time)} (${todayEvents.size} events)"
        }
    }

    private fun updateRecyclerView(events: List<CalendarEvent>) {
        val adapter = EventAdapter(events) { event ->
            // Handle event click
            android.widget.Toast.makeText(
                requireContext(),
                "Selected: ${event.sellerName}",
                android.widget.Toast.LENGTH_SHORT
            ).show()


//            val gson = com.google.gson.Gson()
//        val eventJson = gson.toJson(event)
//
//        val intent = Intent(requireContext(), VisitDetailActivity::class.java)
//        intent.putExtra("event_json", eventJson)
//        startActivity(intent)
        }
        recyclerView.adapter = adapter
    }
}