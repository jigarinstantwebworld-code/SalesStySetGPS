package com.example.salesstysetgps.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfUtils {

    data class StopRow(
        val name: String?,
        val lat: Double,
        val lng: Double,
        val startMillis: Long,
        val endMillis: Long?,
        val durationMinutes: Long,
        val address: String? = null,
        val phone: String? = null,
        val imageUri: String? = null
    )

    private fun formatTime(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    fun generateSellerReport(
        context: Context,
        sellerName: String,
        rows: List<StopRow>
    ): String {
        val document = PdfDocument()
        var pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 @ 72dpi
        var page = document.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }

        var y = 40f
        canvas.drawText("Seller: $sellerName", 40f, y, titlePaint)
        y += 20f
        canvas.drawText("Generated: ${formatTime(System.currentTimeMillis())}", 40f, y, textPaint)
        y += 24f

        rows.forEach { r ->
            // Check if we need a new page
            if (y > 750f) {
                document.finishPage(page)
                pageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = 40f
            }

            // Stop name (bold)
            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val name = r.name ?: "(unnamed)"
            canvas.drawText(name, 40f, y, namePaint)
            y += 18f

            // Address
            if (!r.address.isNullOrBlank()) {
                canvas.drawText("Address: ${r.address}", 40f, y, textPaint)
                y += 16f
            }

            // Phone
            if (!r.phone.isNullOrBlank()) {
                canvas.drawText("Phone: ${r.phone}", 40f, y, textPaint)
                y += 16f
            }

            // Location: prefer address, fall back to coordinates
            val locationLine = if (!r.address.isNullOrBlank()) {
                "Location: ${r.address}"
            } else {
                String.format(Locale.getDefault(), "Location: %.5f, %.5f", r.lat, r.lng)
            }
            canvas.drawText(locationLine, 40f, y, textPaint)
            y += 16f

            val start = formatTime(r.startMillis)
            val end = r.endMillis?.let { formatTime(it) } ?: "—"
            canvas.drawText("Time: $start → $end (${r.durationMinutes} min)", 40f, y, textPaint)
            y += 20f

            // Seller image
            if (!r.imageUri.isNullOrBlank()) {
                try {
                    val bitmap = loadBitmapFromUri(context, r.imageUri)
                    if (bitmap != null) {
                        val imgWidth = 200f
                        val imgHeight = (bitmap.height * imgWidth / bitmap.width).coerceAtMost(150f)
                        if (y + imgHeight > 800f) {
                            document.finishPage(page)
                            pageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
                            page = document.startPage(pageInfo)
                            canvas = page.canvas
                            y = 40f
                        }
                        canvas.drawBitmap(bitmap, null, android.graphics.RectF(40f, y, 40f + imgWidth, y + imgHeight), null)
                        y += imgHeight + 10f
                    }
                } catch (e: Exception) {
                    // Image failed to load, skip it
                }
            }

            // Separator line
            y += 8f
            canvas.drawLine(40f, y, 555f, y, textPaint)
            y += 16f
        }

        // Summary
        if (y > 800f) {
            document.finishPage(page)
            pageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = 40f
        } else {
            y += 20f
        }
        val total = rows.sumOf { it.durationMinutes }
        canvas.drawText("Total minutes across stops: $total", 40f, y, titlePaint)

        document.finishPage(page)

        val fileName = "seller_report_${System.currentTimeMillis()}.pdf"

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
}


