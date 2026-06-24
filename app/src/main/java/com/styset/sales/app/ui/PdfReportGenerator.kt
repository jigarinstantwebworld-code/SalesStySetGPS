package com.styset.sales.app.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.styset.sales.app.util.PdfUtils
import com.styset.sales.app.util.PdfUtils.generateQRCodeBitmap
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class RouteSegment(
    val fromStop: String?,
    val toStop: String?,
    val distance: Double,
    val duration: Long,
    val startTime: Long,
    val endTime: Long
)

class EnhancedPdfReportGenerator(private val context: Context) {

    private val TOP_MARGIN = 50
    private val PAGE_WIDTH = 595
    private val PAGE_HEIGHT = 842
    private val LEFT_MARGIN = 40
    private val RIGHT_MARGIN = 40

    private val BOTTOM_MARGIN = 50
    private val CONTENT_WIDTH = PAGE_WIDTH - LEFT_MARGIN - RIGHT_MARGIN

    // Colors
    private val COLOR_PRIMARY = Color.parseColor("#6200EE")
    private val COLOR_GRAY_LIGHT = Color.parseColor("#F5F5F5")
    private val COLOR_GRAY_MEDIUM = Color.parseColor("#E0E0E0")
    private val COLOR_GRAY_DARK = Color.parseColor("#757575")


//    // all working good 27/05
//    fun generateDailyReport(
//        reportData: DailyReportData,
//        onComplete: (File?) -> Unit
//    ) {
//        try {
//            Log.d("PDF_DEBUG", "========== START PDF GENERATION ==========")
//            Log.d("PDF_DEBUG", "Routes count: ${reportData.routes.size}")
//
//            val document = PdfDocument()
//            var yPos = TOP_MARGIN
//            var currentPage = 1
//
//            var page = createNewPage(document, currentPage)
//            var canvas = page.canvas
//            yPos = TOP_MARGIN
//
//            // ========== HEADER AND SUMMARY (Top) ==========
//            yPos = drawEnhancedHeader(canvas, reportData, yPos)
//            yPos += 10
//            yPos = drawSummaryCards(canvas, reportData, yPos)
//            yPos += 20
//
//            // ========== DRAW ALL ROUTES FIRST ==========
//            for ((index, routeWithDetails) in reportData.routes.withIndex()) {
//                Log.d("PDF_DEBUG", "========== Drawing Route ${index + 1} ==========")
//                Log.d("PDF_DEBUG", "Stops count: ${routeWithDetails.stops.size}, Segments count: ${routeWithDetails.segments.size}")
//
//                var stopIndex = 0
//                var segmentIndex = 0
//                var headerDrawnOnCurrentPage = false
//                var routeComplete = false
//                var maxPagesPerRoute = 50
//                var pageCountForRoute = 0
//                var retryCount = 0
//                val MAX_RETRIES = 3
//
//                while (!routeComplete && pageCountForRoute < maxPagesPerRoute) {
//                    pageCountForRoute++
//
//                    val result = drawEnhancedRoute(
//                        canvas, routeWithDetails, index + 1, yPos,
//                        stopIndex, segmentIndex, headerDrawnOnCurrentPage
//                    )
//
//                    val newYPos = result.first
//                    val newStopIndex = result.second
//                    val newSegmentIndex = result.third
//                    val newHeaderDrawn = result.fourth
//
//                    // Check if progress was made
//                    val stopProgress = newStopIndex > stopIndex
//                    val segmentProgress = newSegmentIndex > segmentIndex
//                    val madeProgress = stopProgress || segmentProgress
//
//                    if (madeProgress) {
//                        // ✅ PROGRESS MADE - Continue on same page
//                        yPos = newYPos
//                        stopIndex = newStopIndex
//                        segmentIndex = newSegmentIndex
//                        headerDrawnOnCurrentPage = newHeaderDrawn
//                        retryCount = 0
//
//                        // Check if route is complete
//                        val allStopsDone = stopIndex >= routeWithDetails.stops.size
//                        val allSegmentsDone = segmentIndex >= routeWithDetails.segments.size
//
//                        if (allStopsDone && allSegmentsDone) {
//                            // Check if map was drawn (indices beyond bounds)
//                            if (stopIndex > routeWithDetails.stops.size &&
//                                segmentIndex > routeWithDetails.segments.size) {
//                                routeComplete = true
//                                Log.d("PDF_DEBUG", "Route ${index + 1} completed with map drawn")
//                            } else if (stopIndex == routeWithDetails.stops.size &&
//                                segmentIndex == routeWithDetails.segments.size) {
//                                // All data drawn, map not yet attempted
//                                // Continue loop to trigger map drawing
//                                Log.d("PDF_DEBUG", "All stops and segments drawn, attempting map...")
//                            } else {
//                                routeComplete = true
//                                Log.d("PDF_DEBUG", "Route ${index + 1} completed")
//                            }
//                        }
//                    } else {
//                        // ❌ NO PROGRESS - Need new page
//                        retryCount++
//
//                        if (retryCount >= MAX_RETRIES) {
//                            Log.e("PDF_DEBUG", "Route ${index + 1} stuck at stopIndex=$stopIndex, segmentIndex=$segmentIndex")
//
//                            // Force completion
//                            if (stopIndex >= routeWithDetails.stops.size &&
//                                segmentIndex >= routeWithDetails.segments.size) {
//                                routeComplete = true
//                                Log.w("PDF_DEBUG", "Route ${index + 1} completed without map")
//                            } else {
//                                routeComplete = true
//                                Log.e("PDF_DEBUG", "Route ${index + 1} incomplete - forcing completion")
//                            }
//                            break
//                        }
//
//                        // Create new page and retry
//                        Log.d("PDF_DEBUG", "No progress, creating new page for route ${index + 1}")
//                        document.finishPage(page)
//                        currentPage++
//                        page = createNewPage(document, currentPage)
//                        canvas = page.canvas
//                        yPos = TOP_MARGIN
//                        headerDrawnOnCurrentPage = false
//                        // Keep stopIndex and segmentIndex the same
//                    }
//                }
//
//                Log.d("PDF_DEBUG", "Route ${index + 1} completed")
//                yPos += 15
//
//                // Force new page for next route if needed
//                if (index < reportData.routes.size - 1) {
//                    if (yPos + 150 > PAGE_HEIGHT - BOTTOM_MARGIN) {
//                        Log.d("PDF_DEBUG", "Creating new page for next route")
//                        document.finishPage(page)
//                        currentPage++
//                        page = createNewPage(document, currentPage)
//                        canvas = page.canvas
//                        yPos = TOP_MARGIN
//                    }
//                }
//            }
//
//            // ========== DRAW SYNC HISTORY AT THE END ==========
//            val syncHistoryHeight = calculateSyncHistoryHeight(reportData)
//
//            if (yPos + syncHistoryHeight > PAGE_HEIGHT - BOTTOM_MARGIN) {
//                Log.d("PDF_DEBUG", "Need new page for sync history")
//                document.finishPage(page)
//                currentPage++
//                page = createNewPage(document, currentPage)
//                canvas = page.canvas
//                yPos = TOP_MARGIN
//            }
//
//            // Add separator line before sync history (if not at top of page)
//            if (yPos > TOP_MARGIN + 20) {
//                val separatorPaint = Paint().apply {
//                    color = COLOR_GRAY_MEDIUM
//                    strokeWidth = 1f
//                }
//                canvas.drawLine(LEFT_MARGIN.toFloat(), yPos.toFloat(),
//                    (PAGE_WIDTH - RIGHT_MARGIN).toFloat(), yPos.toFloat(), separatorPaint)
//                yPos += 15
//            }
//
//            // Draw sync history at the end
//            yPos = drawSyncHistoryAtBottom(canvas, reportData, yPos)
//
//            document.finishPage(page)
//
//            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
//            val fileName = "Sales_Report_${reportData.date.replace(" ", "_")}.pdf"
//            val file = File(downloadsDir, fileName)
//
//            Log.d("PDF_DEBUG", "Saving to: ${file.absolutePath}")
//            document.writeTo(FileOutputStream(file))
//            document.close()
//
//            Log.d("PDF_DEBUG", "PDF generation SUCCESS! Total pages: $currentPage")
//            onComplete(file)
//
//        } catch (e: Exception) {
//            Log.e("PDF_DEBUG", "PDF generation FAILED: ${e.message}", e)
//            e.printStackTrace()
//            onComplete(null)
//        }
//    }

    fun generateDailyReport(
        context: Context,
        reportData: DailyReportData,
        onComplete: (Uri?) -> Unit
    ) {

        try {

            Log.d("PDF_DEBUG", "========== START PDF GENERATION ==========")
            Log.d("PDF_DEBUG", "Routes count: ${reportData.routes.size}")

            val document = PdfDocument()

            var yPos = TOP_MARGIN
            var currentPage = 1

            var page = createNewPage(document, currentPage)
            var canvas = page.canvas

            yPos = TOP_MARGIN

            // ================= HEADER =================

            yPos = drawEnhancedHeader(canvas, reportData, yPos)
            yPos += 10

            yPos = drawSummaryCards(canvas, reportData, yPos)
            yPos += 20

            // ================= ROUTES =================

            for ((index, routeWithDetails) in reportData.routes.withIndex()) {

                Log.d(
                    "PDF_DEBUG",
                    "========== Drawing Route ${index + 1} =========="
                )

                Log.d(
                    "PDF_DEBUG",
                    "Stops count: ${routeWithDetails.stops.size}, Segments count: ${routeWithDetails.segments.size}"
                )

                var stopIndex = 0
                var segmentIndex = 0

                var headerDrawnOnCurrentPage = false
                var routeComplete = false

                val maxPagesPerRoute = 50

                var pageCountForRoute = 0
                var retryCount = 0

                val MAX_RETRIES = 3

                while (!routeComplete && pageCountForRoute < maxPagesPerRoute) {

                    pageCountForRoute++

                    val result = drawEnhancedRoute(
                        canvas,
                        routeWithDetails,
                        index + 1,
                        yPos,
                        stopIndex,
                        segmentIndex,
                        headerDrawnOnCurrentPage
                    )

                    val newYPos = result.first
                    val newStopIndex = result.second
                    val newSegmentIndex = result.third
                    val newHeaderDrawn = result.fourth

                    val stopProgress = newStopIndex > stopIndex
                    val segmentProgress = newSegmentIndex > segmentIndex

                    val madeProgress = stopProgress || segmentProgress

                    if (madeProgress) {

                        // ================= PROGRESS MADE =================

                        yPos = newYPos

                        stopIndex = newStopIndex
                        segmentIndex = newSegmentIndex

                        headerDrawnOnCurrentPage = newHeaderDrawn

                        retryCount = 0

                        val allStopsDone =
                            stopIndex >= routeWithDetails.stops.size

                        val allSegmentsDone =
                            segmentIndex >= routeWithDetails.segments.size

                        if (allStopsDone && allSegmentsDone) {

                            if (
                                stopIndex > routeWithDetails.stops.size &&
                                segmentIndex > routeWithDetails.segments.size
                            ) {

                                routeComplete = true

                                Log.d(
                                    "PDF_DEBUG",
                                    "Route ${index + 1} completed with map drawn"
                                )

                            } else if (
                                stopIndex == routeWithDetails.stops.size &&
                                segmentIndex == routeWithDetails.segments.size
                            ) {

                                Log.d(
                                    "PDF_DEBUG",
                                    "All stops and segments drawn, attempting map..."
                                )

                            } else {

                                routeComplete = true

                                Log.d(
                                    "PDF_DEBUG",
                                    "Route ${index + 1} completed"
                                )
                            }
                        }

                    } else {

                        // ================= NO PROGRESS =================

                        retryCount++

                        if (retryCount >= MAX_RETRIES) {

                            Log.e(
                                "PDF_DEBUG",
                                "Route ${index + 1} stuck at stopIndex=$stopIndex, segmentIndex=$segmentIndex"
                            )

                            if (
                                stopIndex >= routeWithDetails.stops.size &&
                                segmentIndex >= routeWithDetails.segments.size
                            ) {

                                routeComplete = true

                                Log.w(
                                    "PDF_DEBUG",
                                    "Route ${index + 1} completed without map"
                                )

                            } else {

                                routeComplete = true

                                Log.e(
                                    "PDF_DEBUG",
                                    "Route ${index + 1} incomplete - forcing completion"
                                )
                            }

                            break
                        }

                        // ================= CREATE NEW PAGE =================

                        Log.d(
                            "PDF_DEBUG",
                            "No progress, creating new page for route ${index + 1}"
                        )

                        document.finishPage(page)

                        currentPage++

                        page = createNewPage(document, currentPage)

                        canvas = page.canvas

                        yPos = TOP_MARGIN

                        headerDrawnOnCurrentPage = false
                    }
                }

                Log.d(
                    "PDF_DEBUG",
                    "Route ${index + 1} completed"
                )

                yPos += 15

                // ================= NEXT PAGE =================

                if (index < reportData.routes.size - 1) {

                    if (yPos + 150 > PAGE_HEIGHT - BOTTOM_MARGIN) {

                        Log.d(
                            "PDF_DEBUG",
                            "Creating new page for next route"
                        )

                        document.finishPage(page)

                        currentPage++

                        page = createNewPage(document, currentPage)

                        canvas = page.canvas

                        yPos = TOP_MARGIN
                    }
                }
            }

            // ================= SYNC HISTORY =================

            val syncHistoryHeight =
                calculateSyncHistoryHeight(reportData)

            if (yPos + syncHistoryHeight > PAGE_HEIGHT - BOTTOM_MARGIN) {

                Log.d(
                    "PDF_DEBUG",
                    "Need new page for sync history"
                )

                document.finishPage(page)

                currentPage++

                page = createNewPage(document, currentPage)

                canvas = page.canvas

                yPos = TOP_MARGIN
            }

            // ================= SEPARATOR =================

            if (yPos > TOP_MARGIN + 20) {

                val separatorPaint = Paint().apply {
                    color = COLOR_GRAY_MEDIUM
                    strokeWidth = 1f
                }

                canvas.drawLine(
                    LEFT_MARGIN.toFloat(),
                    yPos.toFloat(),
                    (PAGE_WIDTH - RIGHT_MARGIN).toFloat(),
                    yPos.toFloat(),
                    separatorPaint
                )

                yPos += 15
            }

            // ================= DRAW SYNC HISTORY =================

            yPos = drawSyncHistoryAtBottom(
                canvas,
                reportData,
                yPos
            )

            // ================= FINISH LAST PAGE =================

            document.finishPage(page)

            // ================= SAVE PDF USING MEDIASTORE =================

            val fileName =
                "Sales_Report_${reportData.date.replace(" ", "_")}.pdf"

            val contentValues = ContentValues().apply {

                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    fileName
                )

                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    "application/pdf"
                )

                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS
                )
            }


            val resolver = context.contentResolver

            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

            // Delete old file if exists
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=?"

            val selectionArgs = arrayOf(fileName)

            resolver.delete(
                collection,
                selection,
                selectionArgs
            )
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {

                resolver.openOutputStream(uri)?.use { outputStream ->

                    document.writeTo(outputStream)

                }

                document.close()

                Log.d(
                    "PDF_DEBUG",
                    "PDF generation SUCCESS! Total pages: $currentPage"
                )

                Log.d(
                    "PDF_DEBUG",
                    "PDF saved successfully"
                )

                onComplete(uri)

            } else {

                Log.e(
                    "PDF_DEBUG",
                    "Failed to create MediaStore URI"
                )

                document.close()

                onComplete(null)
            }

        } catch (e: Exception) {

            Log.e(
                "PDF_DEBUG",
                "PDF generation FAILED: ${e.message}",
                e
            )

            e.printStackTrace()

            onComplete(null)
        }
    }
    private fun calculateSyncHistoryHeight(reportData: DailyReportData): Int {
        if (reportData.syncHistory.isEmpty()) return 0
        return 15 + 30 + (reportData.syncHistory.size * 20) + 15 + 10
    }


    private fun drawSyncHistoryAtBottom(canvas: Canvas, reportData: DailyReportData, startY: Int): Int {
        var yPos = startY

        if (reportData.syncHistory.isEmpty()) {
            return yPos
        }

        // Compact header
        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PRIMARY
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("📡 SYNC HISTORY", LEFT_MARGIN + 10f, yPos.toFloat(), subHeaderPaint)
        yPos += 15

        // Check if we have space for the table (reduced height for compact view)
        val tableHeight = 50 // Much smaller height for single record
        if (yPos + tableHeight > PAGE_HEIGHT - BOTTOM_MARGIN) {
            return yPos
        }

        // Draw table header (compact)
        val tableHeaderBg = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.FILL
        }
        canvas.drawRect(
            LEFT_MARGIN.toFloat(), (yPos - 3).toFloat(),
            (PAGE_WIDTH - RIGHT_MARGIN).toFloat(), (yPos + 18).toFloat(),
            tableHeaderBg
        )

        // Column widths (adjusted for compact view)
        val colApiName = LEFT_MARGIN + 10
        val colSyncTime = LEFT_MARGIN + 120
        val colStatus = LEFT_MARGIN + 320

        val boldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        canvas.drawText("API Name", colApiName.toFloat(), yPos + 8f, boldTextPaint)
        canvas.drawText("Last Synced Time", colSyncTime.toFloat(), yPos + 8f, boldTextPaint)
        canvas.drawText("Status", colStatus.toFloat(), yPos + 8f, boldTextPaint)
        yPos += 18

        val linePaint = Paint().apply {
            color = COLOR_GRAY_MEDIUM
            strokeWidth = 1f
        }
        canvas.drawLine(LEFT_MARGIN.toFloat(), yPos.toFloat(),
            (PAGE_WIDTH - RIGHT_MARGIN).toFloat(), yPos.toFloat(), linePaint)
        yPos += 5

        // Draw only the first (latest) sync record
        val syncRecord = reportData.syncHistory.first()

        // Row background
        val rowBg = Paint().apply {
            color = Color.parseColor("#F9F9F9")
            style = Paint.Style.FILL
        }
        canvas.drawRect(
            LEFT_MARGIN.toFloat(), (yPos - 3).toFloat(),
            (PAGE_WIDTH - RIGHT_MARGIN).toFloat(), (yPos + 20).toFloat(),
            rowBg
        )

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GRAY_DARK
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        canvas.drawText(syncRecord.apiName, colApiName.toFloat(), yPos + 10f, textPaint)
        canvas.drawText(syncRecord.lastSyncedTime, colSyncTime.toFloat(), yPos + 10f, textPaint)

        val statusText = if (syncRecord.status == 1) "✅ Success" else "❌ Failed"
        val statusColor = if (syncRecord.status == 1) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = statusColor
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(statusText, colStatus.toFloat(), yPos + 10f, statusPaint)

        yPos += 22

        // Add a small note that it's the latest sync
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GRAY_DARK
            textSize = 7f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        canvas.drawText("Latest sync record", LEFT_MARGIN + 10f, yPos.toFloat(), notePaint)

        return yPos + 10
    }



    private fun createNewPage(document: PdfDocument, pageNum: Int): PdfDocument.Page {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        return document.startPage(pageInfo)
    }


    private fun drawEnhancedHeader(canvas: Canvas, reportData: DailyReportData, startY: Int): Int {
        var yPos = startY

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PRIMARY
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GRAY_DARK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        // Draw title with underline
        canvas.drawText("DAILY SALES REPORT", (PAGE_WIDTH / 2).toFloat(), yPos.toFloat(), titlePaint)
        yPos += 30

        canvas.drawText(reportData.date, (PAGE_WIDTH / 2).toFloat(), yPos.toFloat(), datePaint)
        yPos += 20

        // Decorative line
        val linePaint = Paint().apply {
            color = COLOR_PRIMARY
            strokeWidth = 3f
        }
        canvas.drawLine((PAGE_WIDTH / 2 - 100).toFloat(), yPos.toFloat(),
            (PAGE_WIDTH / 2 + 100).toFloat(), yPos.toFloat(), linePaint)

        return yPos + 20
    }

    private fun drawSummaryCards(canvas: Canvas, reportData: DailyReportData, startY: Int): Int {
        var yPos = startY
        val cardWidth = CONTENT_WIDTH / 2 - 10
        val cardHeight = 70

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GRAY_LIGHT
            style = Paint.Style.FILL
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GRAY_MEDIUM
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PRIMARY
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GRAY_DARK
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        // Card 1: Total Routes
        canvas.drawRoundRect(
            LEFT_MARGIN.toFloat(), yPos.toFloat(),
            (LEFT_MARGIN + cardWidth).toFloat(), (yPos + cardHeight).toFloat(),
            10f, 10f, cardPaint
        )
        canvas.drawRoundRect(
            LEFT_MARGIN.toFloat(), yPos.toFloat(),
            (LEFT_MARGIN + cardWidth).toFloat(), (yPos + cardHeight).toFloat(),
            10f, 10f, borderPaint
        )
        canvas.drawText(
            "${reportData.totalRoutes}",
            (LEFT_MARGIN + cardWidth / 2).toFloat(),
            (yPos + 35).toFloat(),
            numberPaint
        )
        canvas.drawText(
            "TOTAL ROUTES",
            (LEFT_MARGIN + cardWidth / 2).toFloat(),
            (yPos + 55).toFloat(),
            labelPaint
        )

        // Card 2: Total Stops
        canvas.drawRoundRect(
            (LEFT_MARGIN + cardWidth + 20).toFloat(), yPos.toFloat(),
            (LEFT_MARGIN + cardWidth * 2 + 20).toFloat(), (yPos + cardHeight).toFloat(),
            10f, 10f, cardPaint
        )
        canvas.drawRoundRect(
            (LEFT_MARGIN + cardWidth + 20).toFloat(), yPos.toFloat(),
            (LEFT_MARGIN + cardWidth * 2 + 20).toFloat(), (yPos + cardHeight).toFloat(),
            10f, 10f, borderPaint
        )
        canvas.drawText(
            "${reportData.totalStops}",
            (LEFT_MARGIN + cardWidth + 20 + cardWidth / 2).toFloat(),
            (yPos + 35).toFloat(),
            numberPaint
        )
        canvas.drawText(
            "TOTAL STOPS",
            (LEFT_MARGIN + cardWidth + 20 + cardWidth / 2).toFloat(),
            (yPos + 55).toFloat(),
            labelPaint
        )

        yPos += cardHeight + 10

        // Card 3: Total Duration
        canvas.drawRoundRect(
            LEFT_MARGIN.toFloat(), yPos.toFloat(),
            (LEFT_MARGIN + cardWidth).toFloat(), (yPos + cardHeight).toFloat(),
            10f, 10f, cardPaint
        )
        canvas.drawRoundRect(
            LEFT_MARGIN.toFloat(), yPos.toFloat(),
            (LEFT_MARGIN + cardWidth).toFloat(), (yPos + cardHeight).toFloat(),
            10f, 10f, borderPaint
        )
        val hours = reportData.totalDuration / 60
        val minutes = reportData.totalDuration % 60
        canvas.drawText(
            "${hours}h ${minutes}m",
            (LEFT_MARGIN + cardWidth / 2).toFloat(),
            (yPos + 35).toFloat(),
            numberPaint
        )
        canvas.drawText(
            "TOTAL DURATION",
            (LEFT_MARGIN + cardWidth / 2).toFloat(),
            (yPos + 55).toFloat(),
            labelPaint
        )

        // Card 4: Total Distance
        canvas.drawRoundRect(
            (LEFT_MARGIN + cardWidth + 20).toFloat(), yPos.toFloat(),
            (LEFT_MARGIN + cardWidth * 2 + 20).toFloat(), (yPos + cardHeight).toFloat(),
            10f, 10f, cardPaint
        )
        canvas.drawRoundRect(
            (LEFT_MARGIN + cardWidth + 20).toFloat(), yPos.toFloat(),
            (LEFT_MARGIN + cardWidth * 2 + 20).toFloat(), (yPos + cardHeight).toFloat(),
            10f, 10f, borderPaint
        )
        canvas.drawText(
            String.format("%.1f km", reportData.totalDistance),
            (LEFT_MARGIN + cardWidth + 20 + cardWidth / 2).toFloat(),
            (yPos + 35).toFloat(),
            numberPaint
        )
        canvas.drawText(
            "TOTAL DISTANCE",
            (LEFT_MARGIN + cardWidth + 20 + cardWidth / 2).toFloat(),
            (yPos + 55).toFloat(),
            labelPaint
        )

        return yPos + cardHeight + 20
    }


    private fun drawEnhancedRoute(
        canvas: Canvas,
        routeWithDetails: RouteWithDetails,
        routeNum: Int,
        startY: Int,
        startStopIndex: Int = 0,
        startSegmentIndex: Int = 0,
        headerAlreadyDrawn: Boolean = false

    ): Quadruple<Int, Int, Int, Boolean> {
        var yPos = startY
        var currentStopIndex = startStopIndex
        var currentSegmentIndex = startSegmentIndex
        var headerDrawn = headerAlreadyDrawn

        val route = routeWithDetails.route
        val stops = routeWithDetails.stops
        val segments = routeWithDetails.segments

        // Column positions
        val colLetter = LEFT_MARGIN + 15
        val colImage = LEFT_MARGIN + 45
        val colName = LEFT_MARGIN + 100
        val colTime = LEFT_MARGIN + 250
        val colDuration = LEFT_MARGIN + 350
        val colDistance = LEFT_MARGIN + 430

        // ========== ROUTE HEADER ==========
        if (!headerDrawn) {
            val headerHeight = 70
            if (yPos + headerHeight > PAGE_HEIGHT - BOTTOM_MARGIN) {
                return Quadruple(yPos, currentStopIndex, currentSegmentIndex, headerDrawn)
            }

            val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_PRIMARY
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(
                LEFT_MARGIN.toFloat(), (yPos - 5).toFloat(),
                (PAGE_WIDTH - RIGHT_MARGIN).toFloat(), (yPos + 45).toFloat(),
                8f, 8f, headerBgPaint
            )

            val whiteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val routeTitle = if (startStopIndex > 0 || startSegmentIndex > 0) {
                "ROUTE $routeNum (Continued)"
            } else {
                "ROUTE $routeNum"
            }
            canvas.drawText(routeTitle, LEFT_MARGIN + 15f, yPos + 20f, whiteTextPaint)

            val startTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(route.startTimeMillis))
            val endTime = route.endTimeMillis?.let {
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it))
            } ?: "Ongoing"
            canvas.drawText("$startTime - $endTime", LEFT_MARGIN + 150f, yPos + 20f, whiteTextPaint)

            val durationSecs = route.durationSeconds ?: 0
            canvas.drawText("${durationSecs / 60}m ${durationSecs % 60}s",
                LEFT_MARGIN + 350f, yPos + 20f, whiteTextPaint)

            canvas.drawText(String.format("%.2f km", routeWithDetails.routeDistance),
                LEFT_MARGIN + 450f, yPos + 20f, whiteTextPaint)

            yPos += 70
            headerDrawn = true
        }

        // ========== STOPS SECTION ==========
        if (stops.isNotEmpty() && currentStopIndex < stops.size) {
            // Stops header (only when starting stops section on this page)
            if (currentStopIndex == 0) {
                val stopsHeaderHeight = 40
                if (yPos + stopsHeaderHeight > PAGE_HEIGHT - BOTTOM_MARGIN - 100) {
                    return Quadruple(yPos, currentStopIndex, currentSegmentIndex, headerDrawn)
                }

                val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 14f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("📍 STOPS", LEFT_MARGIN + 10f, yPos.toFloat(), subHeaderPaint)
                yPos += 20

                // Table header
                val tableHeaderBg = Paint().apply {
                    color = Color.parseColor("#E0E0E0")
                    style = Paint.Style.FILL
                }
                canvas.drawRect(
                    LEFT_MARGIN.toFloat(), (yPos - 5).toFloat(),
                    (PAGE_WIDTH - RIGHT_MARGIN).toFloat(), (yPos + 20).toFloat(),
                    tableHeaderBg
                )

                val boldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 11f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("Stop", colLetter.toFloat(), yPos + 8f, boldTextPaint)
                canvas.drawText("Image", colImage.toFloat(), yPos + 8f, boldTextPaint)
                canvas.drawText("Location", colName.toFloat(), yPos + 8f, boldTextPaint)
                canvas.drawText("Time", colTime.toFloat(), yPos + 8f, boldTextPaint)
                canvas.drawText("Duration", colDuration.toFloat(), yPos + 8f, boldTextPaint)
                canvas.drawText("Travel", colDistance.toFloat(), yPos + 8f, boldTextPaint)
                yPos += 20

                val linePaint = Paint().apply {
                    color = COLOR_GRAY_MEDIUM
                    strokeWidth = 1f
                }
                canvas.drawLine(LEFT_MARGIN.toFloat(), yPos.toFloat(),
                    (PAGE_WIDTH - RIGHT_MARGIN).toFloat(), yPos.toFloat(), linePaint)
                yPos += 8
            }

            // Draw stops rows
            val stopRowHeight = 45
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_GRAY_DARK
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_GRAY_DARK
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            while (currentStopIndex < stops.size) {
                if (yPos + stopRowHeight > PAGE_HEIGHT - BOTTOM_MARGIN - 50) {
                    return Quadruple(yPos, currentStopIndex, currentSegmentIndex, headerDrawn)
                }

                val stopWithDetails = stops[currentStopIndex]
                val stop = stopWithDetails.stop
                val index = currentStopIndex

                // Alternate row background
                if (index % 2 == 0) {
                    val rowBg = Paint().apply {
                        color = Color.parseColor("#F9F9F9")
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(
                        LEFT_MARGIN.toFloat(), (yPos - 3).toFloat(),
                        (PAGE_WIDTH - RIGHT_MARGIN).toFloat(), (yPos + 45).toFloat(),
                        rowBg
                    )
                }

                // Stop letter circle
                val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = getColorForIndex(index)
                    style = Paint.Style.FILL
                }
                canvas.drawCircle((colLetter + 8).toFloat(), (yPos + 8).toFloat(), 8f, circlePaint)

                val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 8f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(stopWithDetails.letter, (colLetter + 8).toFloat(), (yPos + 11).toFloat(), whitePaint)

                // Image placeholder
                val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.LTGRAY
                    style = Paint.Style.FILL
                }
                canvas.drawRect(
                    colImage.toFloat(), yPos.toFloat(),
                    (colImage + 35).toFloat(), (yPos + 35).toFloat(),
                    placeholderPaint
                )

                // Stop details
                var stopName = stop.name ?: "Unnamed"
                if (stopName.length > 15) stopName = stopName.substring(0, 12) + "..."
                canvas.drawText(stopName, colName.toFloat(), yPos + 18f, textPaint)

                val stopStart = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(stop.startTimeMillis))
                val stopEnd = stop.endTimeMillis?.let {
                    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it))
                } ?: ""
                canvas.drawText("$stopStart-$stopEnd", colTime.toFloat(), yPos + 18f, textPaint)

                canvas.drawText("${stop.timeSpentMinutes} min", colDuration.toFloat(), yPos + 18f, textPaint)

                if (index > 0) {
                    canvas.drawText("${stopWithDetails.timeFromPrev ?: 0} min", colDistance.toFloat(), yPos + 18f, smallPaint)
                } else {
                    canvas.drawText("Start", colDistance.toFloat(), yPos + 18f, smallPaint)
                }

                yPos += 45
                currentStopIndex++
            }

            // Add spacing after stops are done
            yPos += 15
        } else if (stops.isEmpty()) {
            val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FF9800")
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("⚠️ No stops recorded for this route", LEFT_MARGIN + 20f, yPos.toFloat(), infoPaint)
            yPos += 30
        }

        // ========== JOURNEY SEGMENTS SECTION - MOVED OUTSIDE STOPS BLOCK ==========
        if (segments.isNotEmpty() && currentSegmentIndex < segments.size) {
            // Segments header (only when starting segments section on this page)
            if (currentSegmentIndex == 0) {
                if (yPos + 30 > PAGE_HEIGHT - BOTTOM_MARGIN) {
                    return Quadruple(yPos, currentStopIndex, currentSegmentIndex, headerDrawn)
                }
                val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 14f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("🔄 JOURNEY SEGMENTS", LEFT_MARGIN + 10f, yPos.toFloat(), subHeaderPaint)
                yPos += 20
            }

            val segmentHeight = 55
            while (currentSegmentIndex < segments.size) {
                if (yPos + segmentHeight > PAGE_HEIGHT - BOTTOM_MARGIN) {
                    return Quadruple(yPos, currentStopIndex, currentSegmentIndex, headerDrawn)
                }

                val segment = segments[currentSegmentIndex]
                val idx = currentSegmentIndex

                // Card background
                val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (idx % 2 == 0) Color.parseColor("#F8F9FA") else Color.WHITE
                    style = Paint.Style.FILL
                }
                val cardLeft = LEFT_MARGIN + 5
                val cardRight = PAGE_WIDTH - RIGHT_MARGIN - 5
                val rectF = RectF(cardLeft.toFloat(), (yPos - 5).toFloat(), cardRight.toFloat(), (yPos + 45).toFloat())
                canvas.drawRoundRect(rectF, 8f, 8f, cardBgPaint)

                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#E0E0E0")
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                }
                canvas.drawRoundRect(rectF, 8f, 8f, borderPaint)

                val fromLetter = segment.fromStop?.substringBefore(".") ?: "A"
                val toLetter = segment.toStop?.substringBefore(".") ?: "B"

                val fromCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = getColorForIndex(idx)
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(cardLeft + 20f, yPos + 8f, 10f, fromCirclePaint)

                val whiteText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 9f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(fromLetter, cardLeft + 20f, yPos + 11f, whiteText)

                val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#6200EE")
                    textSize = 14f
                }
                canvas.drawText("→", cardLeft + 25f, yPos + 10f, arrowPaint)

                canvas.drawCircle(cardLeft + 70f, yPos + 8f, 10f, fromCirclePaint)
                canvas.drawText(toLetter, cardLeft + 70f, yPos + 11f, whiteText)

                // Journey details box
                val detailsBoxRight = cardRight - 10
                val detailsBoxLeft = cardRight - 150
                val detailsBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#F0E6FF")
                    style = Paint.Style.FILL
                }
                val detailsRect = RectF(detailsBoxLeft.toFloat(), yPos.toFloat(),
                    detailsBoxRight.toFloat(), (yPos + 35).toFloat())
                canvas.drawRoundRect(detailsRect, 6f, 6f, detailsBgPaint)

                val distancePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#6200EE")
                    textSize = 11f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("📏 ${String.format("%.2f", segment.distance)} km",
                    detailsBoxLeft + 10f, yPos + 15f, distancePaint)
                canvas.drawText("⏱ ${segment.duration} min",
                    detailsBoxLeft + 10f, yPos + 30f, distancePaint)

                yPos += 55
                currentSegmentIndex++
            }

            // Add spacing after segments are done
            yPos += 15
        }

        // ========== MAP SECTION ==========
        val allStopsDone = currentStopIndex >= stops.size
        val allSegmentsDone = currentSegmentIndex >= segments.size

        if (allStopsDone && allSegmentsDone) {
            val mapAlreadyDrawn = currentStopIndex > stops.size && currentSegmentIndex > segments.size

            if (!mapAlreadyDrawn) {
                val mapHeaderHeight = 30
                val mapImageMaxHeight = 200
                val qrSize = 50
                val totalMapHeight = mapHeaderHeight + mapImageMaxHeight + 20

                // Check space
                if (yPos + totalMapHeight > PAGE_HEIGHT - BOTTOM_MARGIN) {
                    return Quadruple(yPos, currentStopIndex, currentSegmentIndex, headerDrawn)
                }

                // Draw map header
                val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 14f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("🗺️ ROUTE MAP", LEFT_MARGIN + 10f, yPos.toFloat(), subHeaderPaint)
                yPos += 20

                // Try to draw map screenshot
                var mapDrawn = false

                if (!route.screenshotPath.isNullOrEmpty()) {

                    try {

                        val file = File(route.screenshotPath)

                        if (file.exists() &&
                            file.length() > 0 &&
                            file.length() < 5_000_000
                        ) {

                            val options = BitmapFactory.Options().apply {
                                inSampleSize = 2
                            }

                            val originalBitmap =
                                BitmapFactory.decodeFile(route.screenshotPath, options)

                            if (originalBitmap != null && !originalBitmap.isRecycled) {

                                val qrSize = 180
                                val maxHeight = 180
                                val maxWidth = CONTENT_WIDTH - 80 - qrSize - 20

                                var targetWidth = originalBitmap.width
                                var targetHeight = originalBitmap.height

                                if (targetHeight > maxHeight) {

                                    val scale =
                                        maxHeight.toFloat() / targetHeight

                                    targetWidth =
                                        (targetWidth * scale).toInt()

                                    targetHeight = maxHeight
                                }

                                if (targetWidth > maxWidth) {

                                    val scale =
                                        maxWidth.toFloat() / targetWidth

                                    targetHeight =
                                        (targetHeight * scale).toInt()

                                    targetWidth = maxWidth
                                }

                                val scaledBitmap = Bitmap.createScaledBitmap(
                                    originalBitmap,
                                    targetWidth,
                                    targetHeight,
                                    true
                                )

                                originalBitmap.recycle()

                                // IMPORTANT:
                                // Keep URL SHORT if possible
                                val mapsUrl =
                                    buildGoogleMapsUrlForRoute(routeWithDetails)

                                val qrBitmap =
                                    generateQRCodeBitmap(
                                        mapsUrl,
                                        qrSize
                                    )

                                val qrX = LEFT_MARGIN + 10

                                // QR draw paint
                                val qrPaint = Paint().apply {
                                    isFilterBitmap = false
                                    isAntiAlias = false
                                    isDither = false
                                }

                                if (qrBitmap != null) {

                                    canvas.drawBitmap(
                                        qrBitmap,
                                        qrX.toFloat(),
                                        yPos.toFloat(),
                                        qrPaint
                                    )

                                    // Border
                                    val borderPaint = Paint().apply {
                                        color = COLOR_GRAY_MEDIUM
                                        style = Paint.Style.STROKE
                                        strokeWidth = 1f
                                    }

                                    canvas.drawRect(
                                        qrX.toFloat() - 1,
                                        yPos.toFloat() - 1,
                                        (qrX + qrSize + 1).toFloat(),
                                        (yPos + qrSize + 1).toFloat(),
                                        borderPaint
                                    )

                                    // Scan text
                                    val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        color = COLOR_GRAY_DARK
                                        textSize = 12f
                                        textAlign = Paint.Align.CENTER
                                    }

                                    canvas.drawText(
                                        "SCAN",
                                        (qrX + qrSize / 2).toFloat(),
                                        (yPos + qrSize + 18).toFloat(),
                                        scanPaint
                                    )
                                }

                                // Draw Map Image
                                val imageX = qrX + qrSize + 20

                                canvas.drawBitmap(
                                    scaledBitmap,
                                    imageX.toFloat(),
                                    yPos.toFloat(),
                                    null
                                )

                                yPos += maxOf(targetHeight, qrSize) + 35

                                mapDrawn = true

                                scaledBitmap.recycle()
                                qrBitmap?.recycle()

                                Log.d(
                                    "PDF_DEBUG",
                                    "Map drawn successfully for route $routeNum"
                                )
                            }

                        } else {

                            Log.w(
                                "PDF_DEBUG",
                                "Screenshot file missing or invalid: ${route.screenshotPath}"
                            )
                        }

                    } catch (e: Exception) {

                        Log.e(
                            "PDF_MAP",
                            "Map error for route ${routeNum}: ${e.message}"
                        )
                    }
                }

                if (!mapDrawn) {
                    // Draw placeholder
                    val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = COLOR_GRAY_LIGHT
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(
                        (LEFT_MARGIN + 60).toFloat(), yPos.toFloat(),
                        (PAGE_WIDTH - RIGHT_MARGIN - 20).toFloat(), (yPos + 150).toFloat(),
                        placeholderPaint
                    )

                    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = COLOR_GRAY_DARK
                        textSize = 12f
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText(
                        "No map preview available",
                        (PAGE_WIDTH / 2).toFloat(),
                        yPos + 75f,
                        textPaint
                    )
                    yPos += 170
                }

                // Separator line
                val dashPaint = Paint().apply {
                    color = COLOR_GRAY_MEDIUM
                    strokeWidth = 1f
                    pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
                }
                yPos += 10
                canvas.drawLine(LEFT_MARGIN.toFloat(), yPos.toFloat(),
                    (PAGE_WIDTH - RIGHT_MARGIN).toFloat(), yPos.toFloat(), dashPaint)
                yPos += 15

                // Mark as complete by setting indices beyond array bounds
                currentStopIndex = stops.size + 1
                currentSegmentIndex = segments.size + 1
            }
        }

        return Quadruple(yPos, currentStopIndex, currentSegmentIndex, headerDrawn)
    }

    private fun drawSyncHistory(canvas: Canvas, reportData: DailyReportData, startY: Int): Int {
        var yPos = startY

        if (reportData.syncHistory.isEmpty()) {
            return yPos
        }

        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GRAY_DARK
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val boldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val linePaint = Paint().apply {
            color = COLOR_GRAY_MEDIUM
            strokeWidth = 1f
        }

        // Check if we have space
        val tableHeight = 40 + (reportData.syncHistory.size * 20)
        if (yPos + tableHeight > PAGE_HEIGHT - BOTTOM_MARGIN) {
            return yPos  // Will draw on next page
        }

        // Draw section header
        canvas.drawText("📡 SYNC HISTORY", LEFT_MARGIN + 10f, yPos.toFloat(), subHeaderPaint)
        yPos += 15

        // Draw table header
        val tableHeaderBg = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.FILL
        }
        canvas.drawRect(
            LEFT_MARGIN.toFloat(), (yPos - 3).toFloat(),
            (PAGE_WIDTH - RIGHT_MARGIN).toFloat(), (yPos + 15).toFloat(),
            tableHeaderBg
        )

        // Column widths
        val colApiName = LEFT_MARGIN + 10
        val colSyncTime = LEFT_MARGIN + 150
        val colStatus = LEFT_MARGIN + 350

        canvas.drawText("API Name", colApiName.toFloat(), yPos + 8f, boldTextPaint)
        canvas.drawText("Last Synced Time", colSyncTime.toFloat(), yPos + 8f, boldTextPaint)
        canvas.drawText("Status", colStatus.toFloat(), yPos + 8f, boldTextPaint)
        yPos += 15

        canvas.drawLine(LEFT_MARGIN.toFloat(), yPos.toFloat(),
            (PAGE_WIDTH - RIGHT_MARGIN).toFloat(), yPos.toFloat(), linePaint)
        yPos += 5

        // Draw each sync record
        for ((index, syncRecord) in reportData.syncHistory.withIndex()) {
            if (yPos + 20 > PAGE_HEIGHT - BOTTOM_MARGIN) {
                return yPos  // Need new page
            }

            // Alternate row background
            if (index % 2 == 0) {
                val rowBg = Paint().apply {
                    color = Color.parseColor("#F9F9F9")
                    style = Paint.Style.FILL
                }
                canvas.drawRect(
                    LEFT_MARGIN.toFloat(), (yPos - 3).toFloat(),
                    (PAGE_WIDTH - RIGHT_MARGIN).toFloat(), (yPos + 15).toFloat(),
                    rowBg
                )
            }

            canvas.drawText(syncRecord.apiName, colApiName.toFloat(), yPos + 8f, textPaint)
            canvas.drawText(syncRecord.lastSyncedTime, colSyncTime.toFloat(), yPos + 8f, textPaint)

            // Draw status badge
            val statusText = if (syncRecord.status == 1) "✅ Success" else "❌ Failed"
            val statusColor = if (syncRecord.status == 1) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
            val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = statusColor
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(statusText, colStatus.toFloat(), yPos + 8f, statusPaint)

            yPos += 18
        }

        yPos += 10
        return yPos
    }

    private fun getColorForIndex(index: Int): Int {
        return when (index % 6) {
            0 -> Color.parseColor("#6200EE") // Purple
            1 -> Color.parseColor("#1E88E5") // Blue
            2 -> Color.parseColor("#43A047") // Green
            3 -> Color.parseColor("#FB8C00") // Orange
            4 -> Color.parseColor("#E53935") // Red
            5 -> Color.parseColor("#00ACC1") // Cyan
            else -> Color.parseColor("#757575") // Grey
        }
    }

    private fun buildGoogleMapsUrlForRoute(routeWithDetails: RouteWithDetails): String {
        val stops = routeWithDetails.stops.map { it.stop }
        val routePoints = routeWithDetails.routePoints

        return if (stops.size >= 2) {
            // Multiple stops - create directions with all stops
            val baseUrl = "https://www.google.com/maps/dir/"
            val waypoints = stops.joinToString("/") { stop ->
                "${stop.center.latitude},${stop.center.longitude}"
            }
            "$baseUrl$waypoints"
        } else if (stops.size == 1 && routePoints.size >= 2) {
            // Single stop - show Start → Stop → End
            Log.e("TAG", "buildGoogleMapsUrlForRoute:------D ", )
            val start = routePoints.first()
            val stop = stops.first()
            val end = routePoints.last()
            "https://www.google.com/maps/dir/${start.latitude},${start.longitude}/${stop.center.latitude},${stop.center.longitude}/${end.latitude},${end.longitude}"
        } else if (routePoints.size >= 2) {
            // No stops but have route points - show start and end
            val start = routePoints.first()
            val end = routePoints.last()
            "https://www.google.com/maps/dir/${start.latitude},${start.longitude}/${end.latitude},${end.longitude}"
        } else if (routePoints.isNotEmpty()) {
            // Only one point - show that location
            val point = routePoints.first()
            "https://www.google.com/maps/search/?api=1&query=${point.latitude},${point.longitude}"
        } else {
            // Fallback - use route start time or default
            "https://www.google.com/maps"
        }
    }
}

// Add this helper class
data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)