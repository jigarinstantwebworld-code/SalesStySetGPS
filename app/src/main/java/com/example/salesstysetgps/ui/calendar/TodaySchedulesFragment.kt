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


class TodaySchedulesFragment : Fragment() {

    private lateinit var textTodayTitle: TextView
    private lateinit var textTodayCount: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var calendarService: GoogleCalendarService

    private val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_today_schedules, container, false)

        textTodayTitle = view.findViewById(R.id.textTodayTitle)
        textTodayCount = view.findViewById(R.id.textTodayCount)
        recyclerView = view.findViewById(R.id.recyclerViewToday)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendarService = GoogleCalendarService(requireContext())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        loadTodayEvents()
    }

    private fun loadTodayEvents() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account == null) {
            textTodayCount.text = "Please sign in first"
            return
        }

        lifecycleScope.launch {
            val calendar = Calendar.getInstance()
            val startDate = calendar.time
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            val endDate = calendar.time

            val allEvents = calendarService.getCalendarEvents(account, startDate, endDate)

            val today = Calendar.getInstance()
            val todayEvents = allEvents.filter { event ->
                event.getFormattedDate() == displayFormat.format(today.time)
            }

            textTodayCount.text = "${todayEvents.size} visit(s) today"

            val adapter = EventAdapter(todayEvents) { event ->
                android.widget.Toast.makeText(
                    requireContext(),
                    "${event.sellerName} at ${event.getFormattedTime()}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            recyclerView.adapter = adapter
        }
    }
}