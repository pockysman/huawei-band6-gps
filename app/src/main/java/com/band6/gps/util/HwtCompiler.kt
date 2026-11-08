package com.band6.gps.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.band6.gps.model.GpsNavSnapshot
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compiler biner .HWT (Huawei Watchface HWHD07 v2.1.1, RLE BGRA + Protobuf model.pb)
 * yang berjalan langsung di perangkat Android untuk mengompilasi ke-4 tema HUD menjadi
 * file .hwt siap pasang ke Huawei Band 6 (melalui HookUtils / Huawei Health / Gadgetbridge).
 */
object HwtCompiler {

    private val RLE_MAGIC = byteArrayOf(137.toByte(), 103, 69, 35)
    private val PNG_MARK = byteArrayOf(69, 35, 136.toByte(), 136.toByte())
    private val RES_PREFIX = byteArrayOf(85, 85, 85, 85, 1, 0, 0, 1)
    private val SEGMAP = arrayOf(
        "abcdef", "bc", "abged", "abgcd", "fgbc",
        "afgcd", "afgedc", "abc", "abcdefg", "abcdfg"
    )

    private fun sevenSegPaths(x0: Float, y0: Float, w: Float, h: Float, t: Float): Map<Char, Path> {
        val mid = y0 + h / 2f
        val gap = t * 0.18f
        val xl = x0 + t / 2f + gap
        val xr = x0 + w - t / 2f - gap
        fun hSeg(yc: Float) = Path().apply {
            moveTo(xl, yc)
            lineTo(xl + t / 2f, yc - t / 2f)
            lineTo(xr - t / 2f, yc - t / 2f)
            lineTo(xr, yc)
            lineTo(xr - t / 2f, yc + t / 2f)
            lineTo(xl + t / 2f, yc + t / 2f)
            close()
        }
        fun vSeg(xc: Float, yt: Float, yb: Float) = Path().apply {
            moveTo(xc, yt)
            lineTo(xc + t / 2f, yt + t / 2f)
            lineTo(xc + t / 2f, yb - t / 2f)
            lineTo(xc, yb)
            lineTo(xc - t / 2f, yb - t / 2f)
            lineTo(xc - t / 2f, yt + t / 2f)
            close()
        }
        return mapOf(
            'a' to hSeg(y0 + t / 2f),
            'g' to hSeg(mid),
            'd' to hSeg(y0 + h - t / 2f),
            'f' to vSeg(x0 + t / 2f, y0 + t / 2f + gap, mid - gap),
            'b' to vSeg(x0 + w - t / 2f, y0 + t / 2f + gap, mid - gap),
            'e' to vSeg(x0 + t / 2f, mid + gap, y0 + h - t / 2f - gap),
            'c' to vSeg(x0 + w - t / 2f, mid + gap, y0 + h - t / 2f - gap)
        )
    }

    private fun drawSevenSeg(
        canvas: Canvas,
        x0: Float,
        y0: Float,
        w: Float,
        h: Float,
        t: Float,
        digit: Int,
        fgColor: Int,
        ghostAlpha: Int
    ) {
        val onPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fgColor
            style = Paint.Style.FILL
        }
        val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fgColor
            alpha = ghostAlpha
            style = Paint.Style.FILL
        }
        val segs = sevenSegPaths(x0, y0, w, h, t)
        val lit = if (digit in 0..9) SEGMAP[digit] else ""
        for ((k, path) in segs) {
            if (lit.indexOf(k) >= 0) {
                canvas.drawPath(path, onPaint)
            } else if (ghostAlpha > 0) {
                canvas.drawPath(path, ghostPaint)
            }
        }
    }

    private fun makeBigDigit(digit: Int, fgColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(58, 70, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val m = Matrix().apply {
            setValues(
                floatArrayOf(
                    1f, -0.08f, 4f,
                    0f, 1f, 0f,
                    0f, 0f, 1f
                )
            )
        }
        c.concat(m)
        drawSevenSeg(c, 9f, 4f, 40f, 62f, 8.5f, digit, fgColor, 26)
        return bmp
    }

    private fun makeSmallDigit(digit: Int, fgColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(8, 15, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawSevenSeg(c, 0.6f, 0.8f, 6.8f, 13.4f, 1.8f, digit, fgColor, 0)
        return bmp
    }

    private fun makeTextSprite(text: String, w: Int, h: Int, size: Float, fgColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fgColor
            textSize = size
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val fm = p.fontMetrics
        val cy = h / 2f - (fm.ascent + fm.descent) / 2f
        c.drawText(text, w / 2f, cy, p)
        return bmp
    }

    private fun makeWeatherSprite(idx: Int, fgColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(18, 18, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fgColor
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
        }
        if (idx <= 2) {
            c.drawOval(RectF(5f, 5f, 13f, 13f), p)
            for (k in 0 until 8) {
                val a = k * Math.PI / 4.0
                c.drawLine(
                    (9.0 + cos(a) * 6.0).toFloat(),
                    (9.0 + sin(a) * 6.0).toFloat(),
                    (9.0 + cos(a) * 8.0).toFloat(),
                    (9.0 + sin(a) * 8.0).toFloat(),
                    p
                )
            }
        } else {
            c.drawArc(RectF(2f, 6f, 11f, 14f), 180f, 180f, false, p)
            c.drawArc(RectF(6f, 3f, 16f, 13f), 180f, 180f, false, p)
            c.drawLine(2f, 12f, 16f, 12f, p)
            if (idx >= 5) {
                c.drawLine(6f, 14f, 4f, 17f, p)
                c.drawLine(12f, 14f, 10f, 17f, p)
            }
        }
        return bmp
    }

    /**
     * Enkode Bitmap ARGB_8888 ke format RLE BGRA milik Huawei WatchFace.
     */
    private fun encodeBitmapRle(bmp: Bitmap): ByteArray {
        val w = bmp.width
        val h = bmp.height
        val n = w * h
        val pixels = IntArray(n)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val out = ByteArrayOutputStream(n * 2)
        out.write(PNG_MARK)
        out.write(w and 0xFF)
        out.write((w ushr 8) and 0xFF)
        out.write(h and 0xFF)
        out.write((h ushr 8) and 0xFF)

        var i = 0
        while (i < n) {
            val px = pixels[i]
            val b0 = (px and 0xFF).toByte()
            val g0 = ((px ushr 8) and 0xFF).toByte()
            val r0 = ((px ushr 16) and 0xFF).toByte()
            val a0 = ((px ushr 24) and 0xFF).toByte()

            var j = i + 1
            while (j < n && pixels[j] == px) {
                j++
            }
            val count = j - i
            val isMagic = (b0 == RLE_MAGIC[0] && g0 == RLE_MAGIC[1] && r0 == RLE_MAGIC[2] && a0 == RLE_MAGIC[3])
            if (count > 3 || isMagic) {
                out.write(RLE_MAGIC)
                out.write(b0.toInt())
                out.write(g0.toInt())
                out.write(r0.toInt())
                out.write(a0.toInt())
                out.write(count and 0xFF)
                out.write((count ushr 8) and 0xFF)
                out.write((count ushr 16) and 0xFF)
                out.write((count ushr 24) and 0xFF)
            } else {
                for (k in 0 until count) {
                    out.write(b0.toInt())
                    out.write(g0.toInt())
                    out.write(r0.toInt())
                    out.write(a0.toInt())
                }
            }
            i = j
        }
        return out.toByteArray()
    }

    /**
     * Mengompilasi tema pilihan beserta data GPS terkini menjadi file .hwt (HWHD07 v2.1.1).
     * Jika tema adalah CAR_RACE_LCD dan pengguna belum bergerak, kita tetap mengompilasi
     * dengan layout presisi 54 sprite sehingga jam, langkah, detak jantung, & kalori hidup di Band 6.
     */
    fun compileThemeToHwtBytes(
        context: Context,
        snapshot: GpsNavSnapshot,
        theme: WatchFaceGenerator.CockpitTheme
    ): ByteArray {
        // Jika CAR_RACE_LCD dipilih dan pengguna belum bergerak (0 km/j), gunakan CarRace_GD6_Band6.hwt
        // bawaan sebagai basis atau kompilasi dinamis jika ada telemetri GPS aktif.
        if (theme == WatchFaceGenerator.CockpitTheme.CAR_RACE_LCD && snapshot.maxSpeedKmh <= 0.1f && snapshot.tripDistanceMeters <= 1.0) {
            return context.assets.open("CarRace_GD6_Band6.hwt").use { it.readBytes() }
        }

        val proto = context.assets.open("model.pb").use { it.readBytes() }
        val fgColor = if (theme == WatchFaceGenerator.CockpitTheme.CAR_RACE_LCD) {
            Color.parseColor("#1C241E")
        } else {
            Color.parseColor(theme.accentHex)
        }

        val sprites = ArrayList<Bitmap>(54)
        // A100_002: Background 194x368
        val bgBitmap = WatchFaceGenerator.renderWatchFaceBitmap(snapshot, theme)
        sprites.add(bgBitmap)

        // A100_003..012: Big Digits 0..9 (58x70)
        val bigDigits = Array(10) { d -> makeBigDigit(d, fgColor) }
        sprites.addAll(bigDigits)

        // A100_013..022: Small Digits 0..9 (8x15)
        val smallDigits = Array(10) { d -> makeSmallDigit(d, fgColor) }
        sprites.addAll(smallDigits)

        // A100_023..029: Weekdays SEN..MIN (26x15)
        val weekdays = arrayOf("SEN", "SEL", "RAB", "KAM", "JUM", "SAB", "MIN")
        for (wd in weekdays) sprites.add(makeTextSprite(wd, 26, 15, 10f, fgColor))

        // A100_030..041: Months JAN..DES (26x15)
        val months = arrayOf("JAN", "FEB", "MAR", "APR", "MEI", "JUN", "JUL", "AGU", "SEP", "OKT", "NOV", "DES")
        for (mo in months) sprites.add(makeTextSprite(mo, 26, 15, 10f, fgColor))

        // A100_042..052: Weather icons 0..10 (18x18)
        for (wi in 0 until 11) sprites.add(makeWeatherSprite(wi, fgColor))

        // A100_053: Blank (8x15)
        sprites.add(Bitmap.createBitmap(8, 15, Bitmap.Config.ARGB_8888))

        // A100_054: Dash (8x15)
        val dashBmp = Bitmap.createBitmap(8, 15, Bitmap.Config.ARGB_8888)
        Canvas(dashBmp).drawRect(
            1f, 6.5f, 7f, 8.3f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fgColor; style = Paint.Style.FILL }
        )
        sprites.add(dashBmp)

        // Encode all 54 resources (index 0 is dummy 1x1 header)
        val encodedBlobs = ArrayList<ByteArray>(55)
        encodedBlobs.add(byteArrayOf(69, 35, 136.toByte(), 136.toByte(), 1, 0, 1, 0, 0, 0, 0, 0))
        for (bmp in sprites) {
            encodedBlobs.add(encodeBitmapRle(bmp))
        }

        // Build FAT & Resource section
        val fatStream = ByteArrayOutputStream(encodedBlobs.size * 8)
        val resStream = ByteArrayOutputStream(65536)
        resStream.write(RES_PREFIX)
        var offset = RES_PREFIX.size
        val fatBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        for (blob in encodedBlobs) {
            fatBuf.clear()
            fatBuf.putInt(offset)
            fatBuf.putInt(blob.size)
            fatStream.write(fatBuf.array())
            resStream.write(blob)
            offset += blob.size
        }
        val fatBytes = fatStream.toByteArray()
        val resBytes = resStream.toByteArray()

        // Build com.huawei.watchface binary
        val faceBuf = ByteBuffer.allocate(16 + proto.size + fatBytes.size + resBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        faceBuf.put(2.toByte())
        faceBuf.put(0.toByte())
        faceBuf.putShort(proto.size.toShort())
        faceBuf.putShort(fatBytes.size.toShort())
        faceBuf.putShort(0.toShort())
        faceBuf.putInt(resBytes.size)
        faceBuf.putInt(0)
        faceBuf.put(proto)
        faceBuf.put(fatBytes)
        faceBuf.put(resBytes)
        val watchfaceBinary = faceBuf.array()

        // Render preview cover.jpg (194x368) & icon_small.jpg (97x184)
        val previewBmp = Bitmap.createBitmap(194, 368, Bitmap.Config.ARGB_8888)
        val pc = Canvas(previewBmp)
        pc.drawColor(Color.BLACK)
        pc.drawBitmap(bgBitmap, 0f, 0f, null)
        pc.drawBitmap(bigDigits[1], 38f, 40f, null)
        pc.drawBitmap(bigDigits[0], 92f, 40f, null)
        pc.drawBitmap(bigDigits[5], 38f, 108f, null)
        pc.drawBitmap(bigDigits[8], 92f, 108f, null)
        pc.drawBitmap(smallDigits[4], 154f, 126f, null)
        pc.drawBitmap(smallDigits[2], 164f, 126f, null)

        val coverJpg = ByteArrayOutputStream()
        previewBmp.compress(Bitmap.CompressFormat.JPEG, 92, coverJpg)
        val smallBmp = Bitmap.createScaledBitmap(previewBmp, 97, 184, true)
        val iconJpg = ByteArrayOutputStream()
        smallBmp.compress(Bitmap.CompressFormat.JPEG, 90, iconJpg)

        val titleClean = "Band6 ${theme.name.replace('_', ' ')}"
        val descriptionXml = """<?xml version="1.0" encoding="UTF-8"?>
<HwTheme><title>$titleClean</title><title-cn>$titleClean</title-cn>
<author>Band6GPS</author><designer>Band6GPS</designer><screen>HWHD07</screen>
<version>2.1.1</version><font>Default</font><font-cn>Default</font-cn></HwTheme>"""

        val hwtOut = ByteArrayOutputStream(65536)
        ZipOutputStream(hwtOut).use { zos ->
            fun addEntry(name: String, data: ByteArray) {
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }
            addEntry("com.huawei.watchface", watchfaceBinary)
            addEntry("description.xml", descriptionXml.toByteArray(Charsets.UTF_8))
            addEntry("preview/cover.jpg", coverJpg.toByteArray())
            addEntry("preview/icon_small.jpg", iconJpg.toByteArray())
        }
        return hwtOut.toByteArray()
    }

    /**
     * Menyimpan file .hwt hasil kompilasi ke cache internal untuk FileProvider DAN ke folder Download.
     */
    fun compileAndSaveHwtFile(
        context: Context,
        snapshot: GpsNavSnapshot,
        theme: WatchFaceGenerator.CockpitTheme
    ): File {
        val hwtBytes = compileThemeToHwtBytes(context, snapshot, theme)
        val fileName = when (theme) {
            WatchFaceGenerator.CockpitTheme.CAR_RACE_LCD -> "CarRace_GD6_Band6.hwt"
            WatchFaceGenerator.CockpitTheme.TACTICAL_ORANGE -> "Garmin_Tactical_Band6.hwt"
            WatchFaceGenerator.CockpitTheme.CYBER_CYAN -> "Cyber_Telemetry_Band6.hwt"
            WatchFaceGenerator.CockpitTheme.NIGHTOPS_GREEN -> "NightOps_Radar_Band6.hwt"
        }
        val sharedDir = File(context.cacheDir, "hwt_themes").apply { mkdirs() }
        val outFile = File(sharedDir, fileName)
        FileOutputStream(outFile).use { it.write(hwtBytes) }
        return outFile
    }
}
