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
        CAR_RACE_LCD("Retro Car Race LCD", "#1C241E"),
        TACTICAL_ORANGE("Garmin Tactical (Oranye)", "#FF6B2C"),
        CYBER_CYAN("Cyber Telemetry (Biru Cyan)", "#00E5FF"),
        NIGHTOPS_GREEN("NightOps Radar (Hijau)", "#3FB950")
    }

    private val LCD_INK = Color.parseColor("#1C241E")

    private fun sevenSegPaths(x0: Float, y0: Float, w: Float, h: Float, t: Float): Map<Char, Path> {
        val mid = y0 + h / 2f
        val gap = t * 0.18f
        val xl = x0 + t / 2 + gap
        val xr = x0 + w - t / 2 - gap
        fun hSeg(yc: Float) = Path().apply {
            moveTo(xl, yc); lineTo(xl + t / 2, yc - t / 2); lineTo(xr - t / 2, yc - t / 2)
            lineTo(xr, yc); lineTo(xr - t / 2, yc + t / 2); lineTo(xl + t / 2, yc + t / 2); close()
        }
        fun vSeg(xc: Float, yt: Float, yb: Float) = Path().apply {
            moveTo(xc, yt); lineTo(xc + t / 2, yt + t / 2); lineTo(xc + t / 2, yb - t / 2)
            lineTo(xc, yb); lineTo(xc - t / 2, yb - t / 2); lineTo(xc - t / 2, yt + t / 2); close()
        }
        return mapOf(
            'a' to hSeg(y0 + t / 2), 'g' to hSeg(mid), 'd' to hSeg(y0 + h - t / 2),
            'f' to vSeg(x0 + t / 2, y0 + t / 2 + gap, mid - gap),
            'b' to vSeg(x0 + w - t / 2, y0 + t / 2 + gap, mid - gap),
            'e' to vSeg(x0 + t / 2, mid + gap, y0 + h - t / 2 - gap),
            'c' to vSeg(x0 + w - t / 2, mid + gap, y0 + h - t / 2 - gap)
        )
    }

    private val SEGMAP = arrayOf("abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgedc", "abc", "abcdefg", "abcdfg")

    /** Menggambar angka 7-segmen rata kanan (gaya LCD), segmen mati tampil samar. */
    private fun drawLcdNumber(
        canvas: Canvas, text: String, rightX: Float, top: Float,
        digitW: Float, digitH: Float, t: Float, ghostAlpha: Int
    ) {
        val on = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LCD_INK }
        val ghost = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LCD_INK; alpha = ghostAlpha }
        val step = digitW + t * 0.9f
        var x = rightX - step * text.length
        for (ch in text) {
            val segs = sevenSegPaths(x, top, digitW, digitH, t)
            val lit = if (ch.isDigit()) SEGMAP[ch - '0'] else if (ch == '-') "g" else ""
            for ((k, p) in segs) {
                if (lit.indexOf(k) >= 0) canvas.drawPath(p, on) else if (ghostAlpha > 0) canvas.drawPath(p, ghost)
            }
            x += step
        }
    }

    /**
     * Tema "Retro Car Race LCD" — sama persis dengan file CarRace_GD6_Band6.hwt,
     * tetapi panel lintasan diisi data GPS dari aplikasi (MAX km/j, ODO, ALT).
     * Area jam (y 36..180) dibiarkan kosong untuk jam bawaan Huawei Health Gallery.
     */
    private fun renderCarRaceLcd(snapshot: GpsNavSnapshot): Bitmap {
        val bmp = Bitmap.createBitmap(194, 368, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#0A0A0B"))

        // Bezel perak
        val silver = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, 368f,
                intArrayOf(Color.rgb(240, 242, 244), Color.rgb(178, 183, 188), Color.rgb(222, 225, 228), Color.rgb(150, 155, 160)),
                floatArrayOf(0f, 0.45f, 0.7f, 1f), android.graphics.Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(RectF(1.5f, 1.5f, 192.5f, 366.5f), 16f, 16f, silver)

        // Layar LCD
        val lcd = RectF(5f, 5f, 189f, 363f)
        val lcdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f, 5f, 0f, 363f,
                intArrayOf(Color.rgb(205, 212, 166), Color.rgb(196, 210, 176), Color.rgb(178, 204, 186)),
                floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(lcd, 7f, 7f, lcdPaint)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = LCD_INK; alpha = 120 }
        c.drawRoundRect(lcd, 7f, 7f, edge)

        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LCD_INK }
        val inkLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LCD_INK; alpha = 90; strokeWidth = 1f }
        fun text(s: String, size: Float, x: Float, y: Float, alpha: Int = 255, align: Paint.Align = Paint.Align.LEFT) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = LCD_INK; this.alpha = alpha; textSize = size; textAlign = align
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            c.drawText(s, x, y, p)
        }

        // Baris status: arah kompas & kecepatan live
        text("${snapshot.compassCardinal()} ${snapshot.bearingDeg.roundToInt()}°", 11f, 10f, 23f)
        text("${snapshot.speedKmh.roundToInt()} km/j", 11f, 184f, 23f, align = Paint.Align.RIGHT)
        c.drawLine(9f, 31f, 185f, 31f, inkLine)

        // Anotasi area jam (jam digambar oleh Huawei Health)
        text("TIME", 8f, 155f, 54f)
        text("ALM", 8f, 11f, 54f)

        // Kotak Telemetri Persegi Panjang yang Rapi (x 10..184, y 180..282)
        val teleBox = RectF(10f, 180f, 184f, 282f)
        c.drawRoundRect(teleBox, 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LCD_INK; alpha = 22 })
        c.drawRoundRect(teleBox, 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = LCD_INK })

        // Kolom Kiri (x 10..86): Bar Kecepatan Retro + Mobil Balap
        c.drawLine(86f, 180f, 86f, 282f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LCD_INK; strokeWidth = 1.2f })
        text("RPM / SPD", 6.5f, 16f, 193f)
        val barWidths = intArrayOf(54, 46, 38, 30, 22, 14)
        for (bi in barWidths.indices) {
            val by = 200f + bi * 8f
            val bw = barWidths[bi].toFloat()
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = LCD_INK
                alpha = if (bi < 2) 45 else 255
            }
            c.drawRect(16f, by, 16f + bw, by + 5f, barPaint)
        }
        // Mobil balap di kiri bawah kotak telemetri
        c.drawRect(24f, 260f, 56f, 268f, ink)
        c.drawRect(32f, 254f, 46f, 260f, ink)
        c.drawRect(19f, 262f, 24f, 266f, ink)
        c.drawCircle(30f, 270f, 4f, ink)
        c.drawCircle(49f, 270f, 4f, ink)

        // Kolom Kanan (x 86..184): 3 Baris Rapi (MAX km/j, ODO km, ALT m)
        c.drawLine(86f, 214f, 184f, 214f, inkLine)
        c.drawLine(86f, 248f, 184f, 248f, inkLine)

        text("MAX", 7.5f, 91f, 201f)
        drawLcdNumber(c, snapshot.maxSpeedKmh.roundToInt().toString().padStart(5, ' '), 176f, 190f, 7f, 13f, 1.8f, 26)

        val odoKm = String.format(Locale.US, "%.1f", snapshot.tripDistanceMeters / 1000.0)
        text("ODO", 7.5f, 91f, 235f)
        drawLcdNumber(c, odoKm.replace(".", "").padStart(5, ' '), 176f, 224f, 7f, 13f, 1.8f, 26)

        text("ALT", 7.5f, 91f, 269f)
        drawLcdNumber(c, snapshot.altitudeMeters.roundToInt().toString().padStart(5, ' '), 176f, 258f, 7f, 13f, 1.8f, 26)

        // Trapesium Teal Ikonik Casio GD-8 di Bagian Bawah (x 10..184, y 290..356)
        val trap = Path().apply { moveTo(10f, 356f); lineTo(32f, 290f); lineTo(162f, 290f); lineTo(184f, 356f); close() }
        c.drawPath(trap, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(155, 116, 188, 174) })
        c.drawPath(trap, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = LCD_INK })

        val scale = Path().apply {
            moveTo(22f, 350f); lineTo(68f, 350f); lineTo(90f, 298f); lineTo(83f, 298f); lineTo(63f, 343f); lineTo(25f, 343f); close()
        }
        c.drawPath(scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = LCD_INK })
        text("300", 7f, 63f, 304f)
        text("250", 7f, 55f, 320f)
        text("ALM", 7.5f, 30f, 322f)
        text("50 100 150", 6.5f, 22f, 338f)

        text("GPS HUD", 9.5f, 96f, 310f)
        text("SIG", 7f, 146f, 325f)
        val kmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LCD_INK; textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
        }
        c.drawText("km", 142f, 338f, kmPaint)
        c.drawRect(74f, 347f, 172f, 350f, ink)
        return bmp
    }

    /**
     * Membuat gambar Watch Face ukuran presisi Huawei Band 6 (194 x 368 piksel)
     * bertema GPS Cockpit Dashboard yang menyisakan ruang jam di bagian atas (y=48..152)
     * agar pas saat dipasang melalui menu Huawei Health -> Watch Faces -> Gallery!
     */
    fun renderWatchFaceBitmap(snapshot: GpsNavSnapshot, theme: CockpitTheme): Bitmap {
        if (theme == CockpitTheme.CAR_RACE_LCD) return renderCarRaceLcd(snapshot)
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

    /**
     * Mengekspor file CarRace_GD6_Band6.hwt (format biner HWHD07 v2.1.1)
     * yang sudah dibundel di dalam aplikasi langsung ke folder Download HP.
     */
    fun exportBundledHwtToDownloads(context: Context): String? {
        val filename = "CarRace_GD6_Band6.hwt"
        return try {
            val bytes = context.assets.open(filename).use { it.readBytes() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                "Download/$filename"
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val outFile = java.io.File(dir, filename)
                outFile.writeBytes(bytes)
                outFile.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }
}
