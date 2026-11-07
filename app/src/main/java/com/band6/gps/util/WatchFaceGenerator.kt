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
        c.drawLine(9f, 181f, 185f, 181f, inkLine)

        // Lintasan trapesium
        val trap = Path().apply { moveTo(9f, 286f); lineTo(36f, 187f); lineTo(150f, 187f); lineTo(185f, 286f); close() }
        c.drawPath(trap, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 120, 190, 176) })
        c.drawPath(trap, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; color = LCD_INK })
        val scale = Path().apply {
            moveTo(22f, 280f); lineTo(60f, 280f); lineTo(88f, 200f); lineTo(81f, 200f); lineTo(55f, 272f); lineTo(25f, 272f); close()
        }
        c.drawPath(scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f; color = LCD_INK })
        text("300", 7f, 62f, 204f); text("250", 7f, 54f, 223f); text("50 100 150", 6.5f, 20f, 267f)
        c.drawRect(30f, 240f, 52f, 247f, ink); c.drawRect(36f, 235f, 46f, 240f, ink); c.drawRect(27f, 242f, 30f, 245f, ink)
        c.drawCircle(34f, 248f, 3f, ink); c.drawCircle(48f, 248f, 3f, ink)
        c.drawLine(98f, 192f, 98f, 280f, inkLine)

        // Data GPS dari aplikasi
        text("MAX km/j", 7f, 103f, 199f)
        drawLcdNumber(c, snapshot.maxSpeedKmh.roundToInt().toString(), 180f, 202f, 9f, 13f, 2f, 22)
        c.drawLine(103f, 220f, 180f, 220f, inkLine)
        val odoKm = String.format(Locale.US, "%.1f", snapshot.tripDistanceMeters / 1000.0)
        text("ODO km", 7f, 103f, 233f)
        drawLcdNumber(c, odoKm.replace(".", ""), 180f, 236f, 9f, 13f, 2f, 22)
        c.drawRect(180f - (9f + 1.8f) * 1 - 2.5f, 247f, 180f - (9f + 1.8f) * 1 - 0.5f, 249f, ink) // titik desimal
        c.drawLine(103f, 254f, 180f, 254f, inkLine)
        text("ALT m", 7f, 103f, 267f)
        drawLcdNumber(c, snapshot.altitudeMeters.roundToInt().toString(), 180f, 270f, 9f, 13f, 2f, 22)

        // Panel GPS HUD
        val panel = RectF(10f, 294f, 184f, 356f)
        c.drawRoundRect(panel, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LCD_INK; alpha = 28 })
        c.drawRoundRect(panel, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f; color = LCD_INK })
        for (fy in 0 until 3) for (fx in 0 until 4) if ((fx + fy) % 2 == 0) {
            c.drawRect(18f + fx * 4, 302f + fy * 4, 22f + fx * 4, 306f + fy * 4, ink)
        }
        text("GPS HUD", 13f, 40f, 313f)
        text("NAV  SPEED  ODO", 7f, 41f, 324f, alpha = 150)
        c.drawLine(16f, 330f, 178f, 330f, inkLine)
        text("Live di menu MUSIK", 8f, 66f, 345f)
        val tri = Path().apply {
            moveTo(26f, 336f); lineTo(19f, 341f); lineTo(26f, 346f); close()
            moveTo(36f, 336f); lineTo(43f, 341f); lineTo(36f, 346f); close()
            moveTo(50f, 336f); lineTo(57f, 341f); lineTo(50f, 346f); close()
        }
        c.drawPath(tri, ink)
        c.drawRect(17f, 336f, 19f, 346f, ink); c.drawRect(57f, 336f, 59f, 346f, ink)
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
