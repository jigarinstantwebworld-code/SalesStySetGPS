package com.example.salesstysetgps.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.salesstysetgps.R
import com.example.salesstysetgps.databinding.ActivityLeadBinding
import com.example.salesstysetgps.models.Lead

class LeadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeadBinding
    private lateinit var adapter: LeadAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding = ActivityLeadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ Setup Toolbar
        setSupportActionBar(binding.toolbar)

        // (Optional back button)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // ✅ Handle Edge-to-Edge properly
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->

            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Toolbar gets top padding
            binding.toolbar.setPadding(
                binding.toolbar.paddingLeft,
                systemBars.top,
                binding.toolbar.paddingRight,
                binding.toolbar.paddingBottom
            )

            // RecyclerView gets bottom padding
            binding.recyclerViewLeads.setPadding(
                binding.recyclerViewLeads.paddingLeft,
                binding.recyclerViewLeads.paddingTop,
                binding.recyclerViewLeads.paddingRight,
                systemBars.bottom
            )

            insets
        }

        setupRecycler()
    }

    private fun setupRecycler() {

        val leadList = listOf(
            Lead("Grocery Store", "Ramesh Patel", "Ahmedabad", "CG Road", "9876543210"),
            Lead("Clothing Shop", "Suresh Shah", "Surat", "Ring Road", "9123456780"),
            Lead("Electronics", "Amit Kumar", "Vadodara", "Alkapuri", "9988776655"),
            Lead("Medical Store", "Neha Mehta", "Rajkot", "Yagnik Road", "9090909090"),
            Lead("Super Market", "Kiran Desai", "Ahmedabad", "Satellite", "8888888888")
        )

        adapter = LeadAdapter(leadList)

        binding.recyclerViewLeads.apply {
            layoutManager = LinearLayoutManager(this@LeadActivity)
            adapter = this@LeadActivity.adapter
            clipToPadding = false
        }
    }

    // ✅ Back button click
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
