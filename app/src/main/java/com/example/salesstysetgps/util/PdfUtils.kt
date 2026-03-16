package com.example.salesstysetgps.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.graphics.toColorInt

object PdfUtils {

    data class StopRow(
        val name: String?,
        val lat: Double,
        val lng: Double,
        val startMillis: Long,
        val endMillis: Long?,
        val durationMinutes: Long,
        val locationLabel: String? = null,
        val address: String? = null,
        val phone: String? = null,
        val imageUri: String? = null,
        val routeMapUri: String? = null,
        val sequenceLetter: String? = null
    )

    private fun formatTime(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    private fun formatTimeShort(millis: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    /*fun generateSellerReport(
        context: Context,
        sellerName: String,
        rows: List<StopRow>
    ): String {
        val document = PdfDocument()

        // =====================================================================
        // PAGE 1: HEADER, MAP, AND LEGEND
        // =====================================================================
        var pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 @ 72dpi
        var page = document.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        // Define paints
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.parseColor("#2C3E50")
        }

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.parseColor("#34495E")
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            color = android.graphics.Color.parseColor("#2C3E50")
        }

        val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            color = android.graphics.Color.parseColor("#7F8C8D")
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = android.graphics.Color.parseColor("#BDC3C7")
        }

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
        }

        // Helper function to calculate distance between two stops
        fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): String {
            val results = FloatArray(1)
            Log.e("TAG", "calculateDistance: --------- Start Lat :${lat1}   Start long :${lng1} ---- End Lat :${lng2} ---- End Long :${lng2}", )
            Location.distanceBetween(lat1, lng1, lat2, lng2, results)
            val km = results[0] / 1000
            return String.format("%.1f", km)
        }

        // Ensure rows are in chronological order
        val orderedRows = rows.sortedBy { it.startMillis }

        var y = 40f

        // =========================================================
        // PAGE 1: HEADER
        // =========================================================

        // Draw title with underline
        canvas.drawText("SALES ROUTE REPORT", 40f, y, titlePaint)
        y += 5f
        canvas.drawLine(40f, y, 280f, y, linePaint)
        y += 25f

        // Seller info
        canvas.drawText("Seller: $sellerName", 40f, y, headerPaint)
        y += 18f
        canvas.drawText("Generated: ${formatTime(System.currentTimeMillis())}", 40f, y, textPaint)
        y += 25f

        // =========================================================
        // PAGE 1: MAP (Full Width)
        // =========================================================

        if (orderedRows.isNotEmpty() && orderedRows.first().routeMapUri != null) {
            try {
                val mapBitmap = loadMapBitmapFromFile(orderedRows.first().routeMapUri!!)
                if (mapBitmap != null) {
                    val maxWidth = 515f
                    val scale = maxWidth / mapBitmap.width
                    val scaledWidth = mapBitmap.width * scale
                    val scaledHeight = mapBitmap.height * scale

                    val leftMargin = (595f - scaledWidth) / 2f

                    canvas.drawRect(leftMargin - 1f, y - 1f, leftMargin + scaledWidth + 1f, y + scaledHeight + 1f,
                        Paint().apply { color = android.graphics.Color.LTGRAY })
                    canvas.drawBitmap(mapBitmap, null,
                        android.graphics.RectF(leftMargin, y, leftMargin + scaledWidth, y + scaledHeight), null)

                    y += scaledHeight + 20f
                }
            } catch (e: Exception) {
                Log.e("PdfUtils", "Failed to draw map", e)
                canvas.drawText("(Route map could not be loaded)", 40f, y, textPaint)
                y += 20f
            }
        } else {
            canvas.drawRect(40f, y, 555f, y + 200f,
                Paint().apply { color = android.graphics.Color.LTGRAY })
            canvas.drawText("Map Preview", 280f, y + 100f, textPaint)
            y += 220f
        }

        // =========================================================
        // PAGE 1: STOP LETTERS LEGEND
        // =========================================================

        canvas.drawText("Stop Letters on Map:", 40f, y, headerPaint)
        y += 25f

        // Draw legend in columns
        orderedRows.forEachIndexed { index, row ->
            val stopLetter = row.sequenceLetter ?: ('A' + index).toString()

            val col = index % 2
            val rowIndex = index / 2
            val legendX = 60f + (col * 250f)
            val legendY = y + (rowIndex * 25f)

            circlePaint.color = getColorForIndex(index)
            canvas.drawCircle(legendX, legendY - 4f, 8f, circlePaint)
            canvas.drawText(stopLetter, legendX, legendY, letterPaint)

            val name = row.name ?: "Stop $stopLetter"
            canvas.drawText("$stopLetter: $name", legendX + 15f, legendY, textPaint)
        }

        y += ((orderedRows.size / 2) + 1) * 25f + 10f
        canvas.drawText("Route includes ${orderedRows.size} stop(s)", 40f, y, textPaint)

        // Add separator line at bottom of page 1
        y += 15f
        canvas.drawLine(40f, y, 555f, y, linePaint)

        document.finishPage(page)

        // =====================================================================
        // PAGE 2: STOPS DETAILS AND SUMMARY
        // =====================================================================

        pageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
        page = document.startPage(pageInfo)
        canvas = page.canvas
        y = 40f

        // =========================================================
        // PAGE 2: STOPS DETAILS SECTION
        // =========================================================

        canvas.drawText("STOPS DETAILS", 40f, y, headerPaint)
        y += 5f
        canvas.drawLine(40f, y, 150f, y, linePaint)
        y += 15f

        // Color Guide
        canvas.drawText("Color Guide:", 40f, y, smallTextPaint)
        orderedRows.forEachIndexed { index, row ->
            val stopLetter = row.sequenceLetter ?: ('A' + index).toString()
            val legendX = 120f + (index * 60f)

            circlePaint.color = getColorForIndex(index)
            canvas.drawCircle(legendX, y - 4f, 6f, circlePaint)
            canvas.drawText(stopLetter, legendX + 8f, y, smallTextPaint)
        }
        y += 20f

        orderedRows.forEachIndexed { index, row ->
            val stopLetter = row.sequenceLetter ?: ('A' + index).toString()
            val colors = getColorForIndex(index)

            // Stop header with colored circle
            canvas.drawCircle(45f, y + 8f, 12f, circlePaint.apply { color = colors })
            canvas.drawText(stopLetter, 45f, y + 12f,
                letterPaint.apply { textSize = 12f; textAlign = Paint.Align.CENTER })

            // STOP title
            val stopTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = android.graphics.Color.parseColor("#2C3E50")
            }
            canvas.drawText("STOP $stopLetter", 70f, y + 12f, stopTitlePaint)
            y += 40f

            // Business name
            val businessName = row.name ?: "Unnamed Stop"
            canvas.drawText("🏢 SellerName:  $businessName", 55f, y, textPaint.apply { textSize = 12f })
            y += 18f

            // Address
            if (!row.address.isNullOrBlank()) {
                canvas.drawText("📍 Address: ${row.address}", 55f, y, textPaint)
                y += 16f
            }

            // Phone
            if (!row.phone.isNullOrBlank()) {
                canvas.drawText("📞 Phone: ${row.phone}", 55f, y, textPaint)
                y += 16f
            }

            // Location
            val locationLine = if (!row.locationLabel.isNullOrBlank()) {
                row.locationLabel
            } else {
                String.format(Locale.getDefault(), "%.5f, %.5f", row.lat, row.lng)
            }
            canvas.drawText("📍 Location: $locationLine", 55f, y, textPaint)
            y += 16f

            // Time and Duration
            val start = formatTimeShort(row.startMillis)
            val end = row.endMillis?.let { formatTimeShort(it) } ?: "—"
            canvas.drawText("⏱️ $start → $end  |  ${row.durationMinutes} min", 55f, y, textPaint)
            y += 18f

            // Distance from previous stop
            if (index > 0) {
                val prevStop = orderedRows[index - 1]
                val distance = calculateDistance(
                    prevStop.lat, prevStop.lng,
                    row.lat, row.lng
                )
                val timeDiff = (row.startMillis - prevStop.startMillis) / (60 * 1000)
                canvas.drawText("   ↳ $distance km from Stop ${prevStop.sequenceLetter ?: ('A' + (index-1)).toString()}",
                    70f, y - 4f, smallTextPaint.apply { color = android.graphics.Color.parseColor("#FF000000") })
            } else {
                canvas.drawText("   ↳ Start Point", 70f, y - 4f, smallTextPaint)
            }
            y += 4f

            // Seller image (small)
            if (!row.imageUri.isNullOrBlank()) {
                try {
                    val bitmap = loadBitmapFromUri(context, row.imageUri)
                    if (bitmap != null) {
                        val imgWidth = 80f
                        val imgHeight = (bitmap.height * imgWidth / bitmap.width).coerceAtMost(60f)

                        canvas.drawRect(54f, y - 1f, 56f + imgWidth + 1f, y + imgHeight + 1f,
                            Paint().apply { color = android.graphics.Color.LTGRAY })
                        canvas.drawBitmap(bitmap, null,
                            android.graphics.RectF(55f, y, 55f + imgWidth, y + imgHeight), null)

                        y += imgHeight + 15f
                    }
                } catch (e: Exception) {
                    // Skip image
                }
            }

            // Divider between stops
            if (index < orderedRows.size - 1) {
                canvas.drawLine(40f, y, 555f, y, linePaint)
                y += 20f
            }
        }


        y += 40f

        // =========================================================
        // PAGE 2: JOURNEY OVERVIEW CARD
        // =========================================================

        val firstStopTime = orderedRows.firstOrNull()?.startMillis
        val lastStopTime = orderedRows.lastOrNull()?.endMillis ?: orderedRows.lastOrNull()?.startMillis
        val journeyDuration = if (firstStopTime != null && lastStopTime != null) {
            (lastStopTime - firstStopTime) / (60 * 1000)
        } else 0

        var totalDistance = 0.0
        for (i in 1 until orderedRows.size) {
            val prev = orderedRows[i - 1]
            val curr = orderedRows[i]
            val dist = FloatArray(1)
            Location.distanceBetween(prev.lat, prev.lng, curr.lat, curr.lng, dist)
            totalDistance += dist[0]
        }

// Draw card background with shadow effect
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#F8F9FA")
            setShadowLayer(6f, 0f, 3f, android.graphics.Color.parseColor("#33000000"))
        }
        canvas.drawRoundRect(40f, y, 555f, y + 95f, 12f, 12f, cardPaint)

// Draw card border
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = android.graphics.Color.parseColor("#E0E0E0")
        }
        canvas.drawRoundRect(40f, y, 555f, y + 95f, 12f, 12f, cardBorderPaint)

// Card content
        y += 20f

// Title with icon
        canvas.drawText("📊 JOURNEY OVERVIEW", 55f, y, headerPaint.apply { textSize = 16f })

// Draw horizontal line under title
        canvas.drawLine(55f, y + 5f, 200f, y + 5f, linePaint)

        y += 25f

// First row: First Stop and Last Stop
        canvas.drawText("First Stop:", 55f, y, textPaint.apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
        canvas.drawText(formatTimeShort(orderedRows.first().startMillis), 130f, y, textPaint)

        canvas.drawText("Last Stop:", 280f, y, textPaint.apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
        canvas.drawText(formatTimeShort(orderedRows.last().endMillis ?: orderedRows.last().startMillis),
            350f, y, textPaint)

        y += 22f

        // Second row: Total Journey and Total Distance
//        canvas.drawText("Total Journey:", 55f, y, textPaint.apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
//        canvas.drawText("$journeyDuration minutes", 150f, y, textPaint)

        canvas.drawText("Total Distance:", 280f, y, textPaint.apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
        canvas.drawText("${String.format("%.1f", totalDistance/1000)} km", 370f, y, textPaint)

        y += 25f

        // Add a small note if there's only one stop
        if (orderedRows.size == 1) {
            canvas.drawText("⚠️ Only one stop in this route", 55f, y, smallTextPaint.apply { color = android.graphics.Color.parseColor("#E53935") })
            y += 15f
        }

        y += 30f


        // =========================================================
// STOP SEQUENCE (Better Aligned)
// =========================================================

        // =========================================================
// STOP SEQUENCE (Reduced Spacing)
// =========================================================

        canvas.drawText("STOP SEQUENCE", 40f, y, headerPaint)
        y += 5f
        canvas.drawLine(40f, y, 150f, y, linePaint)
        y += 25f

// Calculate positions with reduced spacing
        val totalStop = orderedRows.size
// Reduce spacing from 90f to 50f for closer arrows
        val startX = 60f
        val spacing = 50f // Reduced from 90f to 50f

// First row: Stop letters with arrows
        orderedRows.forEachIndexed { index, row ->
            val stopLetter = row.sequenceLetter ?: ('A' + index).toString()

            val currentX = startX + (index * spacing)

            // Draw colored circle for stop
            circlePaint.color = getColorForIndex(index)
            canvas.drawCircle(currentX, y - 8f, 12f, circlePaint)

            // Draw stop letter
            canvas.drawText(stopLetter, currentX, y - 4f,
                letterPaint.apply { textSize = 12f; textAlign = Paint.Align.CENTER })

            // Draw arrow between stops (except after last) - closer now
            if (index < orderedRows.size - 1) {
                val arrowX = currentX + 20f // Reduced from 35f to 25f
                canvas.drawText("→", arrowX, y - 8f,
                    textPaint.apply { textSize = 14f; color = android.graphics.Color.parseColor("#7F8C8D") })
            }
        }

        y += 18f // Reduced from 20f to 18f

// Second row: Durations with arrows
        orderedRows.forEachIndexed { index, row ->
            val stopLetter = row.sequenceLetter ?: ('A' + index).toString()
            val currentX = startX + (index * spacing)

            val minutes = if (row.durationMinutes > 1000) row.durationMinutes / (60 * 1000) else row.durationMinutes

            // Draw duration
            canvas.drawText("${minutes}min", currentX - 12f, y,
                smallTextPaint.apply { textSize = 10f })

            // Draw arrow between durations (except after last)
            if (index < orderedRows.size - 1) {
                val arrowX = currentX + 20f // Reduced from 30f to 20f
                canvas.drawText("→", arrowX, y,
                    smallTextPaint.apply { textSize = 12f; color = android.graphics.Color.parseColor("#7F8C8D") })
            }
        }

        y += 25f

        // =========================================================
        // PAGE 2: VISIT SUMMARY TABLE (Enhanced)
        // =========================================================

        canvas.drawText("VISIT SUMMARY", 40f, y, headerPaint)
        y += 5f
        canvas.drawLine(40f, y, 150f, y, linePaint)
        y += 20f

        // Table headers
        val tableLeft = 40f
        val tableTop = y

        // Header background
        canvas.drawRect(tableLeft, tableTop, tableLeft + 515f, tableTop + 22f,
            Paint().apply { color = android.graphics.Color.parseColor("#34495E") })

        // Header text
        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.WHITE
        }
        canvas.drawText("Stop", tableLeft + 15f, tableTop + 15f, headerTextPaint)
        canvas.drawText("Name", tableLeft + 60f, tableTop + 15f, headerTextPaint)
        canvas.drawText("Dur.", tableLeft + 150f, tableTop + 15f, headerTextPaint)
        canvas.drawText("Time", tableLeft + 200f, tableTop + 15f, headerTextPaint)
        canvas.drawText("From Last", tableLeft + 280f, tableTop + 15f, headerTextPaint)

        y += 22f

        // Table rows
        orderedRows.forEachIndexed { index, row ->
            val stopLetter = row.sequenceLetter ?: ('A' + index).toString()
            val rowTop = y
            val rowBottom = y + 20f

            if (index % 2 == 0) {
                canvas.drawRect(tableLeft, rowTop, tableLeft + 515f, rowBottom,
                    Paint().apply { color = android.graphics.Color.parseColor("#F8F9F9") })
            }

            circlePaint.color = getColorForIndex(index)
            canvas.drawCircle(tableLeft + 20f, rowTop + 10f, 7f, circlePaint)
            canvas.drawText(stopLetter, tableLeft + 20f, rowTop + 13f,
                letterPaint.apply { textSize = 8f; textAlign = Paint.Align.CENTER })

            canvas.drawText(row.name?.take(12) ?: "—", tableLeft + 60f, rowTop + 13f, textPaint)

            val minutes = if (row.durationMinutes > 1000) row.durationMinutes / (60 * 1000) else row.durationMinutes
            canvas.drawText("$minutes min", tableLeft + 150f, rowTop + 13f, textPaint)

            canvas.drawText(formatTimeShort(row.startMillis), tableLeft + 200f, rowTop + 13f, textPaint)

            // From Last column
            if (index == 0) {
                canvas.drawText("Start", tableLeft + 280f, rowTop + 13f, smallTextPaint)
            } else {
                val prevStop = orderedRows[index - 1]
                val distance = calculateDistance(prevStop.lat, prevStop.lng, row.lat, row.lng)
                val timeDiff = (row.startMillis - prevStop.startMillis) / (60 * 1000)
                canvas.drawText("${distance}km ${timeDiff}min", tableLeft + 280f, rowTop + 13f, smallTextPaint)
            }

            y += 20f
        }

        // Table borders
        canvas.drawRect(tableLeft, tableTop, tableLeft + 515f, y, linePaint)
        canvas.drawLine(tableLeft + 50f, tableTop, tableLeft + 50f, y, linePaint)
        canvas.drawLine(tableLeft + 140f, tableTop, tableLeft + 140f, y, linePaint)
        canvas.drawLine(tableLeft + 190f, tableTop, tableLeft + 190f, y, linePaint)
        canvas.drawLine(tableLeft + 270f, tableTop, tableLeft + 270f, y, linePaint)

        y += 30f

        // =========================================================
        // PAGE 2: STATISTICS (Enhanced)
        // =========================================================

        val totalStops = orderedRows.size
        val totalStoppedMinutes = orderedRows.sumOf {
            if (it.durationMinutes > 1000) it.durationMinutes / (60 * 1000) else it.durationMinutes
        }
        val avgMinutes = if (totalStops > 0) totalStoppedMinutes / totalStops else 0
//        val avgSpeed = if (journeyDuration > 0) (totalDistance/1000) / (journeyDuration/60) else 0.0

        canvas.drawText("Statistics:", 40f, y, headerPaint)
        y += 18f
        canvas.drawText("• Total stops: $totalStops", 40f, y, textPaint)
        y += 16f
        canvas.drawText("• Total stopped time: $totalStoppedMinutes minutes", 40f, y, textPaint)
        y += 16f
        canvas.drawText("• Total journey: $journeyDuration minutes", 40f, y, textPaint)
        y += 16f
        canvas.drawText("• Total distance: ${String.format("%.1f", totalDistance/1000)} km", 40f, y, textPaint)
//        y += 16f
//        canvas.drawText("• Average speed: ${String.format("%.1f", avgSpeed)} km/h", 40f, y, textPaint)
        y += 25f

        canvas.drawText("Thank you for using Sales Route Tracker", 40f, y,
            textPaint.apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })

        document.finishPage(page)

        val fileName = "seller_report_${System.currentTimeMillis()}.pdf"
        return savePdfToStorage(context, document, fileName)
    }*/


    fun generateSellerReport(
        context: Context,
        sellerName: String,
        rows: List<StopRow>
    ): String {
        val document = PdfDocument()

        // =====================================================================
        // PAGE 1: HEADER, MAP, AND LEGEND
        // =====================================================================
        var pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 @ 72dpi
        var page = document.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        // Define paints
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.parseColor("#2C3E50")
        }

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.parseColor("#34495E")
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            color = android.graphics.Color.parseColor("#2C3E50")
        }

        val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            color = android.graphics.Color.parseColor("#7F8C8D")
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = android.graphics.Color.parseColor("#BDC3C7")
        }

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
        }

        // Helper function to calculate distance between two stops
        fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): String {
            val results = FloatArray(1)
            Log.e(
                "TAG",
                "calculateDistance: --------- Start Lat :${lat1}   Start long :${lng1} ---- End Lat :${lng2} ---- End Long :${lng2}"
            )
            Location.distanceBetween(lat1, lng1, lat2, lng2, results)
            val km = results[0] / 1000
            return String.format("%.1f", km)
        }

        // Ensure rows are in chronological order
        val orderedRows = rows.sortedBy { it.startMillis }

        var y = 40f

        // =========================================================
        // PAGE 1: HEADER
        // =========================================================

        // Draw title with underline
        canvas.drawText("SALES ROUTE REPORT", 40f, y, titlePaint)
        y += 5f
        canvas.drawLine(40f, y, 280f, y, linePaint)
        y += 25f

        // Seller info
        canvas.drawText("Seller: $sellerName", 40f, y, headerPaint)
        y += 18f
        canvas.drawText("Generated: ${formatTime(System.currentTimeMillis())}", 40f, y, textPaint)
        y += 25f

        // =========================================================
        // PAGE 1: MAP (Full Width)
        // =========================================================

        if (orderedRows.isNotEmpty() && orderedRows.first().routeMapUri != null) {
            try {
                val mapBitmap = loadMapBitmapFromFile(orderedRows.first().routeMapUri!!)
                if (mapBitmap != null) {
                    val maxWidth = 515f
                    val scale = maxWidth / mapBitmap.width
                    val scaledWidth = mapBitmap.width * scale
                    val scaledHeight = mapBitmap.height * scale

                    val leftMargin = (595f - scaledWidth) / 2f

                    canvas.drawRect(leftMargin - 1f, y - 1f, leftMargin + scaledWidth + 1f, y + scaledHeight + 1f,
                        Paint().apply { color = android.graphics.Color.LTGRAY })
                    canvas.drawBitmap(mapBitmap, null,
                        android.graphics.RectF(leftMargin, y, leftMargin + scaledWidth, y + scaledHeight), null)

                    y += scaledHeight + 20f
                }
            } catch (e: Exception) {
                Log.e("PdfUtils", "Failed to draw map", e)
                canvas.drawText("(Route map could not be loaded)", 40f, y, textPaint)
                y += 20f
            }
        } else {
            canvas.drawRect(40f, y, 555f, y + 200f,
                Paint().apply { color = android.graphics.Color.LTGRAY })
            canvas.drawText("Map Preview", 280f, y + 100f, textPaint)
            y += 220f
        }

        // =========================================================
        // PAGE 1: STOP LETTERS LEGEND
        // =========================================================

        canvas.drawText("Stop Letters on Map:", 40f, y, headerPaint)
        y += 25f

        // Draw legend in columns
        orderedRows.forEachIndexed { index, row ->
            val stopLetter = row.sequenceLetter ?: ('A' + index).toString()

            val col = index % 2
            val rowIndex = index / 2
            val legendX = 60f + (col * 250f)
            val legendY = y + (rowIndex * 25f)

            circlePaint.color = getColorForIndex(index)
            canvas.drawCircle(legendX, legendY - 4f, 8f, circlePaint)
            canvas.drawText(stopLetter, legendX, legendY, letterPaint)

            val name = row.name ?: "Stop $stopLetter"
            canvas.drawText("$stopLetter: $name", legendX + 15f, legendY, textPaint)
        }

        y += ((orderedRows.size / 2) + 1) * 25f + 10f
        canvas.drawText("Route includes ${orderedRows.size} stop(s)", 40f, y, textPaint)

        // Add separator line at bottom of page 1
        y += 15f
        canvas.drawLine(40f, y, 555f, y, linePaint)

        document.finishPage(page)

        // =====================================================================
        // PAGE 2: STOPS DETAILS AND SUMMARY
        // =====================================================================

        pageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
        page = document.startPage(pageInfo)
        canvas = page.canvas
        y = 40f

        // =========================================================
        // PAGE 2: STOPS DETAILS SECTION
        // =========================================================

        canvas.drawText("STOPS DETAILS", 40f, y, headerPaint)
        y += 5f
        canvas.drawLine(40f, y, 150f, y, linePaint)
        y += 15f

        // Color Guide
        canvas.drawText("Color Guide:", 40f, y, smallTextPaint)
        orderedRows.forEachIndexed { index, row ->
            val stopLetter = row.sequenceLetter ?: ('A' + index).toString()
            val legendX = 120f + (index * 60f)

            circlePaint.color = getColorForIndex(index)
            canvas.drawCircle(legendX, y - 4f, 6f, circlePaint)
            canvas.drawText(stopLetter, legendX + 8f, y, smallTextPaint)
        }
        y += 20f

        orderedRows.forEachIndexed { index, row ->
            // 🔴 PAGE BREAK CHECK: Before drawing each stop
            if (y > 700f) {
                document.finishPage(page)
                pageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = 40f

                // Reprint header on new page
                canvas.drawText("STOPS DETAILS (Continued)", 40f, y, headerPaint)
                y += 5f
                canvas.drawLine(40f, y, 200f, y, linePaint)
                y += 20f
            }

            val stopLetter = row.sequenceLetter ?: ('A' + index).toString()
            val colors = getColorForIndex(index)

            // Stop header with colored circle
            canvas.drawCircle(45f, y + 8f, 12f, circlePaint.apply { color = colors })
            canvas.drawText(stopLetter, 45f, y + 12f,
                letterPaint.apply { textSize = 12f; textAlign = Paint.Align.CENTER })

            // STOP title
            val stopTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = android.graphics.Color.parseColor("#2C3E50")
            }
            canvas.drawText("STOP $stopLetter", 70f, y + 12f, stopTitlePaint)
            y += 40f

            // Business name
            val businessName = row.name ?: "Unnamed Stop"
            canvas.drawText("🏢 SellerName:  $businessName", 55f, y, textPaint.apply { textSize = 12f })
            y += 18f

            // Address
            if (!row.address.isNullOrBlank()) {
                canvas.drawText("📍 Address: ${row.address}", 55f, y, textPaint)
                y += 16f
            }

            // Phone
            if (!row.phone.isNullOrBlank()) {
                canvas.drawText("📞 Phone: ${row.phone}", 55f, y, textPaint)
                y += 16f
            }

            // Location
            val locationLine = if (!row.locationLabel.isNullOrBlank()) {
                row.locationLabel
            } else {
                String.format(Locale.getDefault(), "%.5f, %.5f", row.lat, row.lng)
            }
            canvas.drawText("📍 Location: $locationLine", 55f, y, textPaint)
            y += 16f

            // Time and Duration
            val start = formatTimeShort(row.startMillis)
            val end = row.endMillis?.let { formatTimeShort(it) } ?: "—"
            canvas.drawText("⏱️ $start → $end  |  ${row.durationMinutes} min", 55f, y, textPaint)
            y += 18f

            // Distance from previous stop
            if (index > 0) {
                val prevStop = orderedRows[index - 1]
                val distance = calculateDistance(
                    prevStop.lat, prevStop.lng,
                    row.lat, row.lng
                )
                val timeDiff = (row.startMillis - prevStop.startMillis) / (60 * 1000)
                canvas.drawText("   ↳ $distance km from Stop ${prevStop.sequenceLetter ?: ('A' + (index-1)).toString()}",
                    70f, y - 4f, smallTextPaint.apply { color = android.graphics.Color.parseColor("#FF000000") })
            } else {
                canvas.drawText("   ↳ Start Point", 70f, y - 4f, smallTextPaint)
            }
            y += 4f

            // Seller image (small)
            if (!row.imageUri.isNullOrBlank()) {
                try {
                    val bitmap = loadBitmapFromUri(context, row.imageUri)
                    if (bitmap != null) {
                        val imgWidth = 80f
                        val imgHeight = (bitmap.height * imgWidth / bitmap.width).coerceAtMost(60f)

                        // 🔴 PAGE BREAK CHECK: Before drawing image
                        if (y + imgHeight > 750f) {
                            document.finishPage(page)
                            pageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
                            page = document.startPage(pageInfo)
                            canvas = page.canvas
                            y = 40f
                        }

                        canvas.drawRect(54f, y - 1f, 56f + imgWidth + 1f, y + imgHeight + 1f,
                            Paint().apply { color = android.graphics.Color.LTGRAY })
                        canvas.drawBitmap(bitmap, null,
                            android.graphics.RectF(55f, y, 55f + imgWidth, y + imgHeight), null)

                        y += imgHeight + 15f
                    }
                } catch (e: Exception) {
                    // Skip image
                }
            }

            // Divider between stops
            if (index < orderedRows.size - 1) {
                // 🔴 PAGE BREAK CHECK: Before drawing divider
                if (y + 20f > 750f) {
                    document.finishPage(page)
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = 40f
                } else {
                    canvas.drawLine(40f, y, 555f, y, linePaint)
                    y += 20f
                }
            }
        }

        y += 40f

        // 🔴 PAGE BREAK CHECK: Before JOURNEY OVERVIEW
        if (y > 650f) {
            document.finishPage(page)
            pageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = 40f
        }

        // =========================================================
        // PAGE 2/3: JOURNEY OVERVIEW CARD
        // =========================================================

        val firstStopTime = orderedRows.firstOrNull()?.startMillis
        val lastStopTime = orderedRows.lastOrNull()?.endMillis ?: orderedRows.lastOrNull()?.startMillis
        val journeyDuration = if (firstStopTime != null && lastStopTime != null) {
            (lastStopTime - firstStopTime) / (60 * 1000)
        } else 0

        var totalDistance = 0.0
        for (i in 1 until orderedRows.size) {
            val prev = orderedRows[i - 1]
            val curr = orderedRows[i]
            val dist = FloatArray(1)
            Location.distanceBetween(prev.lat, prev.lng, curr.lat, curr.lng, dist)
            totalDistance += dist[0]
        }

        // Draw card background with shadow effect
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#F8F9FA")
            setShadowLayer(6f, 0f, 3f, android.graphics.Color.parseColor("#33000000"))
        }
        canvas.drawRoundRect(40f, y, 555f, y + 95f, 12f, 12f, cardPaint)

        // Draw card border
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = android.graphics.Color.parseColor("#E0E0E0")
        }
        canvas.drawRoundRect(40f, y, 555f, y + 95f, 12f, 12f, cardBorderPaint)

        // Card content
        y += 20f

        // Title with icon
        canvas.drawText("📊 JOURNEY OVERVIEW", 55f, y, headerPaint.apply { textSize = 16f })

        // Draw horizontal line under title
        canvas.drawLine(55f, y + 5f, 200f, y + 5f, linePaint)

        y += 25f

        // First row: First Stop and Last Stop
        canvas.drawText("First Stop:", 55f, y, textPaint.apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
        canvas.drawText(formatTimeShort(orderedRows.first().startMillis), 130f, y, textPaint)

        canvas.drawText("Last Stop:", 280f, y, textPaint.apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
        canvas.drawText(formatTimeShort(orderedRows.last().endMillis ?: orderedRows.last().startMillis),
            350f, y, textPaint)

        y += 22f

        // Second row: Total Journey and Total Distance
        canvas.drawText("Total Distance:", 280f, y, textPaint.apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
        canvas.drawText("${String.format("%.1f", totalDistance/1000)} km", 370f, y, textPaint)

        y += 25f

        // Add a small note if there's only one stop
        if (orderedRows.size == 1) {
            canvas.drawText("⚠️ Only one stop in this route", 55f, y, smallTextPaint.apply { color = android.graphics.Color.parseColor("#E53935") })
            y += 15f
        }

        y += 30f

        // 🔴 PAGE BREAK CHECK: Before STOP SEQUENCE
        if (y > 700f) {
            document.finishPage(page)
            pageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = 40f
        }

        // =========================================================
        // STOP SEQUENCE (Reduced Spacing)
        // =========================================================

        canvas.drawText("STOP SEQUENCE", 40f, y, headerPaint)
        y += 5f
        canvas.drawLine(40f, y, 150f, y, linePaint)
        y += 25f

        // Calculate positions with reduced spacing
        val totalStop = orderedRows.size
        val startX = 60f
        val spacing = 50f

        // First row: Stop letters with arrows
        orderedRows.forEachIndexed { index, row ->
            val stopLetter = row.sequenceLetter ?: ('A' + index).toString()
            val currentX = startX + (index * spacing)

            // Draw colored circle for stop
            circlePaint.color = getColorForIndex(index)
            canvas.drawCircle(currentX, y - 8f, 12f, circlePaint)

            // Draw stop letter
            canvas.drawText(stopLetter, currentX, y - 4f,
                letterPaint.apply { textSize = 12f; textAlign = Paint.Align.CENTER })

            // Draw arrow between stops (except after last)
            if (index < orderedRows.size - 1) {
                val arrowX = currentX + 20f
                canvas.drawText("→", arrowX, y - 8f,
                    textPaint.apply { textSize = 14f; color = android.graphics.Color.parseColor("#7F8C8D") })
            }
        }

        y += 18f

        // Second row: Durations with arrows
        orderedRows.forEachIndexed { index, row ->
            val stopLetter = row.sequenceLetter ?: ('A' + index).toString()
            val currentX = startX + (index * spacing)

            val minutes = if (row.durationMinutes > 1000) row.durationMinutes / (60 * 1000) else row.durationMinutes

            // Draw duration
            canvas.drawText("${minutes}min", currentX - 12f, y,
                smallTextPaint.apply { textSize = 10f })

            // Draw arrow between durations (except after last)
            if (index < orderedRows.size - 1) {
                val arrowX = currentX + 20f
                canvas.drawText("→", arrowX, y,
                    smallTextPaint.apply { textSize = 12f; color = android.graphics.Color.parseColor("#7F8C8D") })
            }
        }

        y += 25f

        // 🔴 PAGE BREAK CHECK: Before VISIT SUMMARY
        if (y > 700f) {
            document.finishPage(page)
            pageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = 40f
        }

        // =========================================================
        // VISIT SUMMARY TABLE (Enhanced)
        // =========================================================

        canvas.drawText("VISIT SUMMARY", 40f, y, headerPaint)
        y += 5f
        canvas.drawLine(40f, y, 150f, y, linePaint)
        y += 20f

        // Table headers
        val tableLeft = 40f
        val tableTop = y

        // Header background
        canvas.drawRect(tableLeft, tableTop, tableLeft + 515f, tableTop + 22f,
            Paint().apply { color = android.graphics.Color.parseColor("#34495E") })

        // Header text
        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.WHITE
        }
        canvas.drawText("Stop", tableLeft + 15f, tableTop + 15f, headerTextPaint)
        canvas.drawText("Name", tableLeft + 60f, tableTop + 15f, headerTextPaint)
        canvas.drawText("Dur.", tableLeft + 150f, tableTop + 15f, headerTextPaint)
        canvas.drawText("Time", tableLeft + 200f, tableTop + 15f, headerTextPaint)
        canvas.drawText("From Last", tableLeft + 280f, tableTop + 15f, headerTextPaint)

        y += 22f

        // Table rows
        orderedRows.forEachIndexed { index, row ->
            val stopLetter = row.sequenceLetter ?: ('A' + index).toString()
            val rowTop = y
            val rowBottom = y + 20f

            if (index % 2 == 0) {
                canvas.drawRect(tableLeft, rowTop, tableLeft + 515f, rowBottom,
                    Paint().apply { color = android.graphics.Color.parseColor("#F8F9F9") })
            }

            circlePaint.color = getColorForIndex(index)
            canvas.drawCircle(tableLeft + 20f, rowTop + 10f, 7f, circlePaint)
            canvas.drawText(stopLetter, tableLeft + 20f, rowTop + 13f,
                letterPaint.apply { textSize = 8f; textAlign = Paint.Align.CENTER })

            canvas.drawText(row.name?.take(12) ?: "—", tableLeft + 60f, rowTop + 13f, textPaint)

            val minutes = if (row.durationMinutes > 1000) row.durationMinutes / (60 * 1000) else row.durationMinutes
            canvas.drawText("$minutes min", tableLeft + 150f, rowTop + 13f, textPaint)

            canvas.drawText(formatTimeShort(row.startMillis), tableLeft + 200f, rowTop + 13f, textPaint)

            // From Last column
            if (index == 0) {
                canvas.drawText("Start", tableLeft + 280f, rowTop + 13f, smallTextPaint)
            } else {
                val prevStop = orderedRows[index - 1]
                val distance = calculateDistance(prevStop.lat, prevStop.lng, row.lat, row.lng)
                val timeDiff = (row.startMillis - prevStop.startMillis) / (60 * 1000)
                canvas.drawText("${distance}km", tableLeft + 280f, rowTop + 13f, smallTextPaint)
            }

            y += 20f
        }

        // Table borders
        canvas.drawRect(tableLeft, tableTop, tableLeft + 515f, y, linePaint)
        canvas.drawLine(tableLeft + 50f, tableTop, tableLeft + 50f, y, linePaint)
        canvas.drawLine(tableLeft + 140f, tableTop, tableLeft + 140f, y, linePaint)
        canvas.drawLine(tableLeft + 190f, tableTop, tableLeft + 190f, y, linePaint)
        canvas.drawLine(tableLeft + 270f, tableTop, tableLeft + 270f, y, linePaint)

        y += 30f

        // 🔴 PAGE BREAK CHECK: Before STATISTICS
        if (y > 750f) {
            document.finishPage(page)
            pageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = 40f
        }

        // =========================================================
        // STATISTICS (Enhanced)
        // =========================================================

        val totalStops = orderedRows.size
        val totalStoppedMinutes = orderedRows.sumOf {
            if (it.durationMinutes > 1000) it.durationMinutes / (60 * 1000) else it.durationMinutes
        }
        val avgMinutes = if (totalStops > 0) totalStoppedMinutes / totalStops else 0

        canvas.drawText("Statistics:", 40f, y, headerPaint)
        y += 18f
        canvas.drawText("• Total stops: $totalStops", 40f, y, textPaint)
        y += 16f
        canvas.drawText("• Total stopped time: $totalStoppedMinutes minutes", 40f, y, textPaint)
        y += 16f
        canvas.drawText("• Total journey: $journeyDuration minutes", 40f, y, textPaint)
        y += 16f
        canvas.drawText("• Total distance: ${String.format("%.1f", totalDistance/1000)} km", 40f, y, textPaint)
        y += 25f

        canvas.drawText("Thank you for using Sales Route Tracker", 40f, y,
            textPaint.apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })


        y += 30f

        val linkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = android.graphics.Color.BLUE
            isUnderlineText = true
        }
        val routeUrl = buildGoogleMapsRouteUrl(orderedRows)

// Draw clickable link text
        canvas.drawText("🔗 View Full Route:", 40f, y, headerPaint)
        y += 20f

// Draw the URL (visually)
        canvas.drawText(routeUrl, 40f, y, linkPaint)
        y += 15f
        canvas.drawText("(Copy to open in Google Maps if viewing digitally)", 40f, y, smallTextPaint)
        y += 25f

// Add QR Code for easy scanning
        val qrBitmap = generateQRCodeBitmap(routeUrl)
        if (qrBitmap != null) {
            canvas.drawText("📱 Scan QR Code to open route:", 40f, y, textPaint)
            y += 20f

            val qrSize = 120f
            canvas.drawBitmap(qrBitmap, null, android.graphics.RectF(40f, y, 40f + qrSize, y + qrSize), null)
            y += qrSize + 15f
        }

        document.finishPage(page)

        val fileName = "seller_report_${System.currentTimeMillis()}.pdf"
        return savePdfToStorage(context, document, fileName)
    }

    private fun getColorForIndex(index: Int): Int {
        return when (index % 6) {
            0 -> "#E53935".toColorInt()
            1 -> "#1E88E5".toColorInt()
            2 -> "#43A047".toColorInt()
            3 -> "#FB8C00".toColorInt()
            4 -> "#8E24AA".toColorInt()
            5 -> "#00ACC1".toColorInt()
            else -> "#757575".toColorInt()
        }
    }

    fun generateQRCodeBitmap(url: String): Bitmap? {
        return try {
            val writer = MultiFormatWriter()
            val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, 200, 200)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    fun buildGoogleMapsRouteUrl(rows: List<StopRow>): String {
        if (rows.isEmpty()) return ""

        val baseUrl = "https://www.google.com/maps/dir/"
        val waypoints = StringBuilder()

        // Add start point (first stop)
        val first = rows.first()
        waypoints.append("${first.lat},${first.lng}")

        // Add all stops as waypoints
        for (i in 1 until rows.size) {
            val stop = rows[i]
            waypoints.append("/${stop.lat},${stop.lng}")
        }

        return baseUrl + waypoints.toString()
    }



    // Helper function to save PDF (extracted for clarity)
    private fun savePdfToStorage(context: Context, document: PdfDocument, fileName: String): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, contentValues)
                ?: throw IllegalStateException("Unable to create file in Downloads")
            resolver.openOutputStream(itemUri).use { out ->
                document.writeTo(out)
            }
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)
            document.close()
            return fileName
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { out -> document.writeTo(out) }
            document.close()
            return file.absolutePath
        }
    }

    private fun loadBitmapFromUri(context: Context, uriString: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadMapBitmapFromFile(uriString: String): Bitmap? {
        return try {
            val file = File(uriString)
            if (file.exists()) {
                BitmapFactory.decodeFile(uriString)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}


