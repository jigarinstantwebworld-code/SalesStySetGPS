package com.styset.sales.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.styset.sales.app.data.LocationPoint
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxUtils {

    fun exportRouteGpx(context: Context, points: List<LocationPoint>): String {
        if (points.isEmpty()) {
            throw IllegalArgumentException("No route points to export")
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val builder = StringBuilder()
        builder.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        builder.append(
            """<gpx version="1.1" creator="SalesStySetGPS" xmlns="http://www.topografix.com/GPX/1/1">"""
        ).append('\n')
        builder.append("<trk><name>Route ${Date()}</name><trkseg>\n")

        points.forEach { p ->
            val time = sdf.format(Date(p.timestampMillis))
            builder.append(
                """<trkpt lat="${p.latLng.latitude}" lon="${p.latLng.longitude}"><time>$time</time></trkpt>"""
            ).append('\n')
        }

        builder.append("</trkseg></trk></gpx>")

        val fileName = "route_${System.currentTimeMillis()}.gpx"

        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/gpx+xml")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, contentValues)
                ?: throw IllegalStateException("Unable to create GPX file in Downloads")
            resolver.openOutputStream(itemUri).use { out ->
                out?.write(builder.toString().toByteArray(Charsets.UTF_8))
            }
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)
            return fileName
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { out ->
                out.write(builder.toString().toByteArray(Charsets.UTF_8))
            }
            return file.absolutePath
        }
    }
}

