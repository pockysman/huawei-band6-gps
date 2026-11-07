package com.band6.gps.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.band6.gps.model.GpsNavSnapshot
import java.io.OutputStream
import java.util.Locale
import kotlin.math.roundToInt

object WatchFaceGenerator {

    enum class CockpitTheme(val label: String, val accentHex: String) {
        TACTICAL_ORANGE("Garmin Tactical (Oranye)", "#FF6B2C"),
        CYBER_CYAN("Cyber Telemetry (Biru Cyan)", "#00E5FF"),
        NIGHTOPS_GREEN("NightOps Radar (Hijau)", "#3FB950")
    }

    /**
     * Membuat gambar Watch Face ukuran presisi Huawei Band 6 (194 x 368 piksel)
     * bertema GPS Cockpit Dashboard yang menyisakan ruang jam di bagian atas (y=48..152)
     * agar pas saat dipasang melalui menu Huawei Health -> Watch Faces -> Gallery!
     */
    fun renderWatchFaceBitmap(snapshot: GpsNavSnapshot, theme: CockpitTheme): Bitmap {
        val width = 194
        val height = 368
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val accentColor = Color.parseColor(theme.accentHex)

        // Latar belakang hitam AMOLED pekat
        val bgPaint = Paint().apply {
            color = Color.parseColor("#06090E")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Grid radar halus
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            alpha = 26
            strokeWidth = 1f
        }
        var y = 20f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += 24f
        }
        var x = 17f
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += 24f
        }

        // Bingkai luar layar Band 6
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            alpha = 150
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(RectF(4f, 4f, 190f, 364f), 26f, 26f, borderPaint)

        // Panel Pita Kompas Atas (y=10..38)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10161F")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(10f, 10f, 184f, 38f), 6f, 6f, cardPaint)
        canvas.drawRoundRect(RectF(10f, 10f, 184f, 38f), 6f, 6f, borderPaint)

        val monoSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 9.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val heading = snapshot.bearingDeg.roundToInt()
        val cardinal = snapshot.compassCardinal()
        canvas.drawText("290  315  [ $cardinal ${String.format(Locale.US, "%03d", heading)}° ]  045  090", 97f, 28f, monoSmall)

        // Segitiga penunjuk kompas di tengah atas
        val triPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.FILL
        }
        val path = Path().apply {
            moveTo(97f, 10f)
            lineTo(92f, 4f)
            lineTo(102f, 4f)
            close()
        }
        canvas.drawPath(path, triPaint)

        // Siku pembatas area Jam Bawaan Huawei Band 6 (y=50..152)
        // Area ini sengaja dikosongkan agar angka jam dari Huawei Health Gallery tidak tertutup!
        val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        // Kiri atas & kanan atas
        canvas.drawLine(12f, 52f, 28f, 52f, bracketPaint)
        canvas.drawLine(12f, 52f, 12f, 70f, bracketPaint)
        canvas.drawLine(166f, 52f, 182f, 52f, bracketPaint)
        canvas.drawLine(182f, 52f, 182f, 70f, bracketPaint)
        // Kiri bawah & kanan bawah area jam
        canvas.drawLine(12f, 150f, 28f, 150f, bracketPaint)
        canvas.drawLine(12f, 132f, 12f, 150f, bracketPaint)
        canvas.drawLine(166f, 150f, 182f, 150f, bracketPaint)
        canvas.drawLine(182f, 132f, 182f, 150f, bracketPaint)

        // Lingkaran Tachometer / Gauge Tengah (y=164..274)
        val gaugeRect = RectF(42f, 164f, 152f, 274f)
        canvas.drawOval(gaugeRect, cardPaint)
        val gaugeBgArc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#21262D")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        val gaugeActiveArc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawArc(RectF(46f, 168f, 148f, 270f), 135f, 270f, false, gaugeBgArc)
        canvas.drawArc(RectF(46f, 168f, 148f, 270f), 135f, 200f, false, gaugeActiveArc)

        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8B949E")
            textSize = 9f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val whiteBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText("GPS COCKPIT", 97f, 198f, mutedPaint)
        val maxSpdText = if (snapshot.maxSpeedKmh > 0f) {
            "MAX ${snapshot.maxSpeedKmh.roundToInt()} KM/J"
        } else {
            "NAV HUD PRO"
        }
        canvas.drawText(maxSpdText, 97f, 220f, whiteBoldPaint)
        canvas.drawText("SAT LOCK: READY", 97f, 240f, monoSmall)

        // 2 Kartu Telemetri Bawah (y=286..350)
        canvas.drawRoundRect(RectF(10f, 286f, 94f, 350f), 6f, 6f, cardPaint)
        canvas.drawRoundRect(RectF(10f, 286f, 94f, 350f), 6f, 6f, borderPaint)

        canvas.drawRoundRect(RectF(100f, 286f, 184f, 350f), 6f, 6f, cardPaint)
        canvas.drawRoundRect(RectF(100f, 286f, 184f, 350f), 6f, 6f, borderPaint)

        val leftAlignMuted = Paint(mutedPaint).apply { textAlign = Paint.Align.LEFT }
        val leftAlignAccent = Paint(monoSmall).apply {
            textAlign = Paint.Align.LEFT
            textSize = 10.5f
        }
        val leftAlignWhite = Paint(whiteBoldPaint).apply {
            textAlign = Paint.Align.LEFT
            textSize = 9.5f
        }

        canvas.drawText("ODOMETER", 16f, 300f, leftAlignMuted)
        canvas.drawText(snapshot.formattedTripDistance(), 16f, 318f, leftAlignAccent)
        canvas.drawText("ALT ${snapshot.altitudeMeters.roundToInt()}M", 16f, 338f, leftAlignWhite)

        canvas.drawText("TELEMETRY", 106f, 300f, leftAlignMuted)
        canvas.drawText("BAND 6 HD", 106f, 318f, leftAlignAccent)
        canvas.drawText("194x368 PX", 106f, 338f, leftAlignWhite)

        return bmp
    }

    /**
     * Menyimpan gambar 194x368 PNG langsung ke Galeri HP (Folder Pictures/Band6_GPS_WatchFaces)
     * agar pengguna tinggal memilihnya di Huawei Health -> Watch Faces -> Gallery.
     */
    fun saveWatchFaceToGallery(context: Context, snapshot: GpsNavSnapshot, theme: CockpitTheme): String? {
        val bitmap = renderWatchFaceBitmap(snapshot, theme)
        val filename = "Band6_GPS_${theme.name}_${System.currentTimeMillis() / 1000}.png"

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Band6_GPS_WatchFaces")
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null
        var out: OutputStream? = null
        return try {
            out = resolver.openOutputStream(uri)
            if (out != null) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            "Pictures/Band6_GPS_WatchFaces/$filename"
        } catch (_: Exception) {
            null
        } finally {
            out?.close()
        }
    }
}
