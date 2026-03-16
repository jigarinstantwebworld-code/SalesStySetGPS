package com.example.salesstysetgps.ui

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.example.salesstysetgps.data.StopPoint
import com.example.salesstysetgps.data.local.RouteEntity
import com.example.salesstysetgps.util.PdfUtils.StopRow
import com.example.salesstysetgps.util.PdfUtils.generateQRCodeBitmap
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


    fun generateDailyReport(
        reportData: DailyReportData,
        onComplete: (File?) -> Unit
    ) {
        try {
            val document = PdfDocument()
            var yPos = TOP_MARGIN
            var currentPage = 1

            // Create first page
            var page = createNewPage(document, currentPage)
            var canvas = page.canvas
            yPos = TOP_MARGIN

            // Draw header and summary
            yPos = drawEnhancedHeader(canvas, reportData, yPos)
            yPos += 10
            yPos = drawSummaryCards(canvas, reportData, yPos)
            yPos += 20

            // Draw routes
            for ((index, routeWithDetails) in reportData.routes.withIndex()) {

                val routeHeight = 200 // Approximate height for route content
                val qrCodeHeight = 150 // Height needed for QR code + header + text
                val totalNeeded = routeHeight + qrCodeHeight

                // Check if we need a new page
//                if (yPos > PAGE_HEIGHT - 400) {
//                    document.finishPage(page)
//                    currentPage++
//                    page = createNewPage(document, currentPage)
//                    canvas = page.canvas
//                    yPos = TOP_MARGIN
//                }
                if (yPos + totalNeeded > PAGE_HEIGHT - BOTTOM_MARGIN) {
                    Log.d("PDF_DEBUG", "Not enough space for route ${index + 1} + QR, starting new page")
                    document.finishPage(page)
                    currentPage++
                    page = createNewPage(document, currentPage)
                    canvas = page.canvas
                    yPos = TOP_MARGIN
                }

                // Draw the route content (this includes its own separator at the end)
                yPos = drawEnhancedRoute(canvas, routeWithDetails, index + 1, yPos)

                // Add small spacing between routes
                yPos += 5
            }

            document.finishPage(page)

            // Save PDF
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val fileName = "Sales_Report_${reportData.date.replace(" ", "_")}.pdf"
            val file = File(downloadsDir, fileName)
            document.writeTo(FileOutputStream(file))
            document.close()
            onComplete(file)

        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(null)
        }
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
        startY: Int
    ): Int {
        var yPos = startY

        val route = routeWithDetails.route
        val stops = routeWithDetails.stops
        val segments = routeWithDetails.segments

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PRIMARY
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GRAY_DARK
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val boldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val whiteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GRAY_DARK
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val linePaint = Paint().apply {
            color = COLOR_GRAY_MEDIUM
            strokeWidth = 1f
        }

        // ========== ROUTE HEADER WITH FULL BACKGROUND ==========
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PRIMARY
            style = Paint.Style.FILL
        }

        // Draw header background covering full width
        canvas.drawRoundRect(
            LEFT_MARGIN.toFloat(), (yPos - 5).toFloat(),
            (PAGE_WIDTH - RIGHT_MARGIN).toFloat(), (yPos + 45).toFloat(),
            8f, 8f, headerBgPaint
        )

        // Route number in white
        canvas.drawText("ROUTE $routeNum", LEFT_MARGIN + 15f, yPos + 20f, whiteTextPaint)

        // Time and duration in white on same line
        val startTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(route.startTimeMillis))
        val endTime = route.endTimeMillis?.let {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it))
        } ?: "Ongoing"

        whiteTextPaint.textSize = 11f
        canvas.drawText("$startTime - $endTime", LEFT_MARGIN + 150f, yPos + 20f, whiteTextPaint)

        // Duration
        val durationSecs = route.durationSeconds ?: 0
        canvas.drawText("${durationSecs / 60}m ${durationSecs % 60}s",
            LEFT_MARGIN + 350f, yPos + 20f, whiteTextPaint)

        // Distance
        canvas.drawText(String.format("%.2f km", routeWithDetails.routeDistance),
            LEFT_MARGIN + 450f, yPos + 20f, whiteTextPaint)

        yPos += 70
        // ========== END ROUTE HEADER ==========

        // Stops section
        if (stops.isNotEmpty()) {
            canvas.drawText("📍 STOPS", LEFT_MARGIN + 10f, yPos.toFloat(), subHeaderPaint)
            yPos += 20

            // Draw table header with background
            val tableHeaderBg = Paint().apply {
                color = Color.parseColor("#E0E0E0")
                style = Paint.Style.FILL
            }

            canvas.drawRect(
                LEFT_MARGIN.toFloat(), (yPos - 5).toFloat(),
                (PAGE_WIDTH - RIGHT_MARGIN).toFloat(), (yPos + 20).toFloat(),
                tableHeaderBg
            )

            // Table headers with fixed column widths
//            val colLetter = LEFT_MARGIN + 15
//            val colName = LEFT_MARGIN + 60
//            val colTime = LEFT_MARGIN + 200
//            val colDuration = LEFT_MARGIN + 300
//            val colDistance = LEFT_MARGIN + 380

            val colLetter = LEFT_MARGIN + 15      // Stop letter (A, B, C)
            val colImage = LEFT_MARGIN + 45       // Image column
            val colName = LEFT_MARGIN + 100       // Location name (after image)
            val colTime = LEFT_MARGIN + 250       // Time column
            val colDuration = LEFT_MARGIN + 350   // Duration column
            val colDistance = LEFT_MARGIN + 430   // Travel column

            canvas.drawText("Stop", colLetter.toFloat(), yPos + 8f, boldTextPaint)
            canvas.drawText("Image", colImage.toFloat(), yPos + 8f, boldTextPaint)
            canvas.drawText("Location", colName.toFloat(), yPos + 8f, boldTextPaint)
            canvas.drawText("Time", colTime.toFloat(), yPos + 8f, boldTextPaint)
            canvas.drawText("Duration", colDuration.toFloat(), yPos + 8f, boldTextPaint)
            canvas.drawText("Travel", colDistance.toFloat(), yPos + 8f, boldTextPaint)
            yPos += 20

            // Horizontal line
            canvas.drawLine(LEFT_MARGIN.toFloat(), yPos.toFloat(),
                (PAGE_WIDTH - RIGHT_MARGIN).toFloat(), yPos.toFloat(), linePaint)
            yPos += 8

//            stops.forEachIndexed { index, stopWithDetails ->
//                val stop = stopWithDetails.stop
//
//                // Alternate row background for better readability
//                if (index % 2 == 0) {
//                    val rowBg = Paint().apply {
//                        color = Color.parseColor("#F9F9F9")
//                        style = Paint.Style.FILL
//                    }
//                    canvas.drawRect(
//                        LEFT_MARGIN.toFloat(), (yPos - 3).toFloat(),
//                        (PAGE_WIDTH - RIGHT_MARGIN).toFloat(), (yPos + 15).toFloat(),
//                        rowBg
//                    )
//                }
//
//                // Stop letter with colored circle
//                val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//                    color = getColorForIndex(index)
//                    style = Paint.Style.FILL
//                }
//                canvas.drawCircle((colLetter + 8).toFloat(), (yPos + 5).toFloat(), 8f, circlePaint)
//
//                val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//                    color = Color.WHITE
//                    textSize = 8f
//                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
//                    textAlign = Paint.Align.CENTER
//                }
//                canvas.drawText(stopWithDetails.letter, (colLetter + 8).toFloat(), (yPos + 8).toFloat(), whitePaint)
//
//                // Stop name (truncate if too long)
//                var stopName = stop.name ?: "Unnamed"
//                if (stopName.length > 15) {
//                    stopName = stopName.substring(0, 12) + "..."
//                }
//                canvas.drawText(stopName, colName.toFloat(), yPos + 8f, textPaint)
//
//                // Time
//                val stopStart = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(stop.startTimeMillis))
//                val stopEnd = stop.endTimeMillis?.let {
//                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
//                } ?: ""
//                canvas.drawText("$stopStart-$stopEnd", colTime.toFloat(), yPos + 8f, textPaint)
//
//                // Duration
//                canvas.drawText("${stop.timeSpentMinutes} min", colDuration.toFloat(), yPos + 8f, textPaint)
//
//                // Travel info (distance and time from previous)
//                if (index > 0) {
//                    val travelText = "${stopWithDetails.timeFromPrev ?: 0} min / ${String.format("%.1f", stopWithDetails.distanceFromPrev ?: 0.0)} km"
//                    canvas.drawText(travelText, colDistance.toFloat(), yPos + 8f, smallPaint)
//                } else {
//                    canvas.drawText("Start", colDistance.toFloat(), yPos + 8f, smallPaint)
//                }
//
//                yPos += 18
//            }

            // In the stops section where you draw each stop
            stops.forEachIndexed { index, stopWithDetails ->
                val stop = stopWithDetails.stop

                // Alternate row background - increased height for image
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

                // Stop letter with colored circle
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

                // ===== SELLER IMAGE - NOW AT CORRECT COLUMN =====
                var imageDrawn = false
                if (!stop.imageUri.isNullOrBlank()) {
                    try {
                        val uri = Uri.parse(stop.imageUri)
                        Log.d("PDF_IMAGE", "Loading image from URI: $uri")

                        val inputStream = context.contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            val options = BitmapFactory.Options().apply {
                                inSampleSize = 2
                            }
                            val sellerBitmap = BitmapFactory.decodeStream(inputStream, null, options)
                            inputStream.close()

                            if (sellerBitmap != null) {
                                Log.d("PDF_IMAGE", "Image loaded: ${sellerBitmap.width}x${sellerBitmap.height}")

                                val imageSize = 35
                                val scaledImage = Bitmap.createScaledBitmap(sellerBitmap, imageSize, imageSize, true)

                                // Draw image at IMAGE column (colImage)
                                canvas.drawBitmap(scaledImage, colImage.toFloat(), yPos.toFloat(), null)

                                // Draw border
                                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = Color.BLACK
                                    style = Paint.Style.STROKE
                                    strokeWidth = 1f
                                }
                                canvas.drawRect(
                                    colImage.toFloat(), yPos.toFloat(),
                                    (colImage + imageSize).toFloat(), (yPos + imageSize).toFloat(),
                                    borderPaint
                                )

                                imageDrawn = true
                                scaledImage.recycle()
                                sellerBitmap.recycle()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PDF_IMAGE", "Error loading image: ${e.message}")
                    }
                }

                // If no image, draw placeholder at IMAGE column
                if (!imageDrawn) {
                    val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.LTGRAY
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(
                        colImage.toFloat(), yPos.toFloat(),
                        (colImage + 35).toFloat(), (yPos + 35).toFloat(),
                        placeholderPaint
                    )
                }

                // Stop name at NAME column (colName)
                var stopName = stop.name ?: "Unnamed"
                if (stopName.length > 15) {
                    stopName = stopName.substring(0, 12) + "..."
                }
                canvas.drawText(stopName, colName.toFloat(), yPos + 18f, textPaint)

                // Time at TIME column
                val stopStart = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(stop.startTimeMillis))
                val stopEnd = stop.endTimeMillis?.let {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
                } ?: ""
                canvas.drawText("$stopStart-$stopEnd", colTime.toFloat(), yPos + 18f, textPaint)

                // Duration at DURATION column
                canvas.drawText("${stop.timeSpentMinutes} min", colDuration.toFloat(), yPos + 18f, textPaint)

                // Travel info at TRAVEL column
                if (index > 0) {
                    val travelText = "${stopWithDetails.timeFromPrev ?: 0} min"
                    canvas.drawText(travelText, colDistance.toFloat(), yPos + 18f, smallPaint)
                } else {
                    canvas.drawText("Start", colDistance.toFloat(), yPos + 18f, smallPaint)
                }

                // Update yPos based on image height
                yPos += 45
            }

            yPos += 30

            // Journey segments between stops
            if (segments.isNotEmpty()) {
                canvas.drawText("🔄 JOURNEY SEGMENTS", LEFT_MARGIN + 10f, yPos.toFloat(), subHeaderPaint)
                yPos += 20

                segments.forEachIndexed { index, segment ->
                    // Alternate background for segments

                    val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (index % 2 == 0) Color.parseColor("#F8F9FA") else Color.WHITE
                        style = Paint.Style.FILL
                    }

                    val cardTop = yPos - 5
                    val cardBottom = yPos + 45
                    val cardLeft = LEFT_MARGIN + 5
                    val cardRight = PAGE_WIDTH - RIGHT_MARGIN - 5

                    // Draw card background with rounded corners
                    val rectF = RectF(cardLeft.toFloat(), cardTop.toFloat(), cardRight.toFloat(), cardBottom.toFloat())

                    canvas.drawRoundRect(rectF, 8f, 8f, cardBgPaint)

                    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#E0E0E0")
                        style = Paint.Style.STROKE
                        strokeWidth = 1f
                    }
                    canvas.drawRoundRect(rectF, 8f, 8f, borderPaint)


                    val fromStopName = segment.fromStop?.substringAfter(". ") ?: "Start"
                    val toStopName = segment.toStop?.substringAfter(". ") ?: "End"
                    val fromLetter = segment.fromStop?.substringBefore(".") ?: "A"
                    val toLetter = segment.toStop?.substringBefore(".") ?: "B"



                    val journeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#6200EE")
                        textSize = 12f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }

                    // Draw from stop with circle
                    val fromCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = getColorForIndex(index)
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

                    // Draw arrow
                    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#6200EE")
                        textSize = 14f
                    }
                    canvas.drawText("→", cardLeft + 25f, yPos + 10f, arrowPaint)

                    // Draw to stop with circle
                    canvas.drawCircle(cardLeft + 70f, yPos + 8f, 10f, fromCirclePaint)
                    canvas.drawText(toLetter, cardLeft + 70f, yPos + 11f, whiteText)

                    // Stop names
                    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 11f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    canvas.drawText(fromStopName, cardLeft + 100f, yPos + 6f, namePaint)
                    canvas.drawText(toStopName, cardLeft + 100f, yPos + 20f, namePaint)

                    // Journey details in a box
                    val detailsBoxLeft = cardRight - 150
                    val detailsBoxTop = yPos
                    val detailsBoxRight = cardRight - 10
                    val detailsBoxBottom = yPos + 35

                    val detailsBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#F0E6FF")
                        style = Paint.Style.FILL
                    }

                    val detailsRect = RectF(detailsBoxLeft.toFloat(), detailsBoxTop.toFloat(),
                        detailsBoxRight.toFloat(), detailsBoxBottom.toFloat())
                    canvas.drawRoundRect(detailsRect, 6f, 6f, detailsBgPaint)

                    // Distance
                    val distancePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#6200EE")
                        textSize = 11f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    canvas.drawText("📏 ${String.format("%.2f", segment.distance)} km",
                        detailsBoxLeft + 10f, yPos + 15f, distancePaint)

                    // Duration
                    canvas.drawText("⏱ ${segment.duration} min",
                        detailsBoxLeft + 10f, yPos + 30f, distancePaint)

                    yPos += 55 // Space for the card
                }
            }
        } else {
            canvas.drawText("  No stops recorded", LEFT_MARGIN + 20f, yPos.toFloat(), textPaint)
            yPos += 20
        }


//        if (!route.screenshotPath.isNullOrEmpty()) {
//            try {
//                val file = File(route.screenshotPath)
//                if (file.exists() && file.length() > 0) {
//                    yPos += 10
//                    canvas.drawText("🗺️ ROUTE MAP", LEFT_MARGIN + 10f, yPos.toFloat(), subHeaderPaint)
//                    yPos += 10
//
//
//                    // Load bitmap
//                    val originalBitmap = BitmapFactory.decodeFile(route.screenshotPath)
//
//                    if (originalBitmap != null) {
//                        // Calculate available space
//                        val maxWidth = CONTENT_WIDTH - 40
//                        val maxHeight = PAGE_HEIGHT - yPos - 50 // Leave space for footer
//
//                        Log.d("PDF_IMAGE", "Route ${routeNum}: Original ${originalBitmap.width}x${originalBitmap.height}")
//                        Log.d("PDF_IMAGE", "Max space: ${maxWidth}x${maxHeight}")
//
//                        // Calculate scale to fit ENTIRE image within available space
//                        val scale = minOf(
//                            maxWidth.toFloat() / originalBitmap.width,
//                            maxHeight.toFloat() / originalBitmap.height,
//                            1.0f // Don't upscale
//                        )
//
//                        val targetWidth = (originalBitmap.width * scale).toInt()
//                        val targetHeight = (originalBitmap.height * scale).toInt()
//
//                        Log.d("PDF_IMAGE", "Target size: ${targetWidth}x${targetHeight}")
//
//                        // Create scaled bitmap
//                        val scaledBitmap = Bitmap.createScaledBitmap(
//                            originalBitmap,
//                            targetWidth,
//                            targetHeight,
//                            true
//                        )
//
//                        // Center image horizontally
//                        val xPos = LEFT_MARGIN + (CONTENT_WIDTH - targetWidth) / 2
//
//                        // Draw white background
//                        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//                            color = Color.WHITE
//                            style = Paint.Style.FILL
//                        }
//
//                        canvas.drawRect(
//                            xPos.toFloat() - 2,
//                            yPos.toFloat() - 2,
//                            (xPos + targetWidth + 2).toFloat(),
//                            (yPos + targetHeight + 2).toFloat(),
//                            bgPaint
//                        )
//
//                        // Draw image
//                        canvas.drawBitmap(scaledBitmap, xPos.toFloat(), yPos.toFloat(), null)
//
//                        // Draw border
//                        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//                            color = COLOR_GRAY_MEDIUM
//                            style = Paint.Style.STROKE
//                            strokeWidth = 1f
//                        }
//
//                        canvas.drawRect(
//                            xPos.toFloat() - 1,
//                            yPos.toFloat() - 1,
//                            (xPos + targetWidth + 1).toFloat(),
//                            (yPos + targetHeight + 1).toFloat(),
//                            borderPaint
//                        )
//
//                        // Optional: Show dimensions (for debugging)
//                        val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//                            color = Color.argb(100, 100, 100, 100)
//                            textSize = 6f
//                            textAlign = Paint.Align.RIGHT
//                        }
//                        canvas.drawText(
//                            "${targetWidth}x${targetHeight}",
//                            (xPos + targetWidth - 5).toFloat(),
//                            (yPos + targetHeight - 5).toFloat(),
//                            dimPaint
//                        )
//
//                        yPos += targetHeight + 20
//
//                        scaledBitmap.recycle()
//                        originalBitmap.recycle()
//                    }
//                } else {
//                    canvas.drawText("  [Route map unavailable]", LEFT_MARGIN + 20f, yPos.toFloat(), textPaint)
//                    yPos += 15
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//                canvas.drawText("  [Error loading map]", LEFT_MARGIN + 20f, yPos.toFloat(), textPaint)
//                yPos += 15
//            }
//        }

        if (!route.screenshotPath.isNullOrEmpty()) {
            try {
                val file = File(route.screenshotPath)
                if (file.exists() && file.length() > 0) {
                    yPos += 10
                    canvas.drawText("🗺️ ROUTE MAP", LEFT_MARGIN + 10f, yPos.toFloat(), subHeaderPaint)
                    yPos += 10

                    // Load bitmap
                    val originalBitmap = BitmapFactory.decodeFile(route.screenshotPath)

                    if (originalBitmap != null) {
                        // ===== QR CODE GENERATION =====
                        val mapsUrl = buildGoogleMapsUrlForRoute(routeWithDetails)
                        var qrBitmap: Bitmap? = null
                        var scaledQr: Bitmap? = null
                        var qrSize = 0

                        if (mapsUrl.isNotEmpty()) {
                            qrBitmap = generateQRCodeBitmap(mapsUrl)
                            if (qrBitmap != null) {
                                qrSize = 70 // Smaller QR code to fit nicely
                                scaledQr = Bitmap.createScaledBitmap(qrBitmap, qrSize, qrSize, true)
                            }
                        }
                        // ===== END QR CODE GENERATION =====

                        // Calculate available space - now with QR code on left
                        val qrSpace = if (scaledQr != null) qrSize + 20 else 0 // Space for QR + padding
                        val maxWidth = CONTENT_WIDTH - 40 - qrSpace
                        val maxHeight = PAGE_HEIGHT - yPos - 50

                        Log.d("PDF_IMAGE", "Route ${routeNum}: Original ${originalBitmap.width}x${originalBitmap.height}")
                        Log.d("PDF_IMAGE", "Max space: ${maxWidth}x${maxHeight}")

                        // Calculate scale to fit ENTIRE image within available space
                        val scale = minOf(
                            maxWidth.toFloat() / originalBitmap.width,
                            maxHeight.toFloat() / originalBitmap.height,
                            1.0f // Don't upscale
                        )

                        val targetWidth = (originalBitmap.width * scale).toInt()
                        val targetHeight = (originalBitmap.height * scale).toInt()

                        Log.d("PDF_IMAGE", "Target size: ${targetWidth}x${targetHeight}")

                        // Create scaled bitmap
                        val scaledBitmap = Bitmap.createScaledBitmap(
                            originalBitmap,
                            targetWidth,
                            targetHeight,
                            true
                        )

                        // Calculate positions for side-by-side layout
                        val startX = LEFT_MARGIN

                        // Draw QR code on LEFT side if available
                        if (scaledQr != null) {
                            // Draw white background for QR
                            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.WHITE
                                style = Paint.Style.FILL
                            }
                            canvas.drawRect(
                                startX.toFloat() - 3,
                                yPos.toFloat() - 3,
                                (startX + qrSize + 3).toFloat(),
                                (yPos + qrSize + 3).toFloat(),
                                bgPaint
                            )

                            // Draw QR code
                            canvas.drawBitmap(scaledQr, startX.toFloat(), yPos.toFloat(), null)

                            // Draw border around QR
                            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = COLOR_GRAY_MEDIUM
                                style = Paint.Style.STROKE
                                strokeWidth = 1f
                            }
                            canvas.drawRect(
                                startX.toFloat() - 1,
                                yPos.toFloat() - 1,
                                (startX + qrSize + 1).toFloat(),
                                (yPos + qrSize + 1).toFloat(),
                                borderPaint
                            )

                            // Draw "SCAN" label
                            val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = COLOR_GRAY_DARK
                                textSize = 7f
                                textAlign = Paint.Align.CENTER
                            }
                            canvas.drawText(
                                "SCAN",
                                (startX + qrSize/2).toFloat(),
                                (yPos + qrSize + 10).toFloat(),
                                scanPaint
                            )
                        }

                        // Calculate image position (shifted right if QR exists)
                        val imageX = if (scaledQr != null) {
                            startX + qrSize + 20
                        } else {
                            LEFT_MARGIN + (CONTENT_WIDTH - targetWidth) / 2 // Center if no QR
                        }

                        // Draw white background for image
                        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.WHITE
                            style = Paint.Style.FILL
                        }
                        canvas.drawRect(
                            imageX.toFloat() - 2,
                            yPos.toFloat() - 2,
                            (imageX + targetWidth + 2).toFloat(),
                            (yPos + targetHeight + 2).toFloat(),
                            bgPaint
                        )

                        // Draw image
                        canvas.drawBitmap(scaledBitmap, imageX.toFloat(), yPos.toFloat(), null)

                        // Draw border
                        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = COLOR_GRAY_MEDIUM
                            style = Paint.Style.STROKE
                            strokeWidth = 1f
                        }
                        canvas.drawRect(
                            imageX.toFloat() - 1,
                            yPos.toFloat() - 1,
                            (imageX + targetWidth + 1).toFloat(),
                            (yPos + targetHeight + 1).toFloat(),
                            borderPaint
                        )

                        // Optional: Show dimensions (for debugging)
                        val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.argb(100, 100, 100, 100)
                            textSize = 6f
                            textAlign = Paint.Align.RIGHT
                        }
                        canvas.drawText(
                            "${targetWidth}x${targetHeight}",
                            (imageX + targetWidth - 5).toFloat(),
                            (yPos + targetHeight - 5).toFloat(),
                            dimPaint
                        )

                        // Update yPos based on the taller element
                        val imageHeight = targetHeight
                        val qrHeight = if (scaledQr != null) qrSize + 15 else 0 // QR + label
                        yPos += maxOf(imageHeight, qrHeight) + 25

                        // Clean up
                        scaledBitmap.recycle()
                        originalBitmap.recycle()
                        scaledQr?.recycle()
                        qrBitmap?.recycle()
                    }
                } else {
                    canvas.drawText("  [Route map unavailable]", LEFT_MARGIN + 20f, yPos.toFloat(), textPaint)
                    yPos += 15
                }
            } catch (e: Exception) {
                e.printStackTrace()
                canvas.drawText("  [Error loading map]", LEFT_MARGIN + 20f, yPos.toFloat(), textPaint)
                yPos += 15
            }
        }

        // Add separator between routes
        val dashPaint = Paint().apply {
            color = COLOR_GRAY_MEDIUM
            strokeWidth = 1f
            pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
        }
        yPos += 15
        canvas.drawLine(LEFT_MARGIN.toFloat(), yPos.toFloat(),
            (PAGE_WIDTH - RIGHT_MARGIN).toFloat(), yPos.toFloat(), dashPaint)

        return yPos /*+ 15*/
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