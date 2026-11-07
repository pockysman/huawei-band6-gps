package com.band6.gps

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.band6.gps.model.GpsNavRepository
import com.band6.gps.model.GpsNavSnapshot
import com.band6.gps.service.BandGpsForegroundService
import com.band6.gps.service.MapsNotificationListener
import com.band6.gps.util.WatchFaceGenerator
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    // Palette — Direction A: Casio GD-8 x MoTeC Pit Console
    private val colChassis = Color.parseColor("#0E100F")
    private val colPanel = Color.parseColor("#151817")
    private val colRecessed = Color.parseColor("#090B0A")
    private val colKey = Color.parseColor("#1C201E")
    private val colHairline = Color.parseColor("#282D2A")
    private val colBorderStrong = Color.parseColor("#3A413D")
    private val colLcdSurface = Color.parseColor("#C9D1A8")
    private val colLcdTeal = Color.parseColor("#88BFB2")
    private val colLcdInk = Color.parseColor("#1C241E")
    private val colTextPrimary = Color.parseColor("#ECEFE9")
    private val colTextSecondary = Color.parseColor("#9AA39D")
    private val colTextMuted = Color.parseColor("#646D67")
    private val colCrimson = Color.parseColor("#C83E2B")
    private val colBrass = Color.parseColor("#D4AF37")
    private val colSignalOk = Color.parseColor("#47A76A")

    private lateinit var viewBridgeLed: View
    private lateinit var tvBridgeState: TextView
    private lateinit var viewMapsLed: View
    private lateinit var tvMapsPermissionState: TextView

    private lateinit var tvVelocityValue: TextView
    private lateinit var tvVelocityBar: TextView
    private lateinit var tvPeakValue: TextView
    private lateinit var tvOdoValue: TextView
    private lateinit var tvAltValue: TextView
    private lateinit var tvCompassValue: TextView
    private lateinit var tvCoordsValue: TextView

    private lateinit var tvHudPageMeta: TextView
    private lateinit var tvLcdModeTag: TextView
    private lateinit var tvLcdEtaTag: TextView
    private lateinit var tvBandHudTitle: TextView
    private lateinit var tvBandHudSubtitle: TextView
    private lateinit var tvLcdTrapezoidRight: TextView

    private lateinit var btnToggleService: Button
    private lateinit var ivWatchFacePreview: ImageView
    private val themeButtons = mutableListOf<Button>()

    private var selectedThemeIndex = 0
    private val themes = WatchFaceGenerator.CockpitTheme.entries

    private val stateListener: (GpsNavSnapshot) -> Unit = { snapshot ->
        runOnUiThread { renderSnapshot(snapshot) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildConsoleLayout())
        requestNeededPermissions()
    }

    override fun onStart() {
        super.onStart()
        GpsNavRepository.addListener(stateListener)
        updateNotificationListenerState()
    }

    override fun onStop() {
        GpsNavRepository.removeListener(stateListener)
        super.onStop()
    }

    private fun buildConsoleLayout(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(colChassis)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = boxBg(colPanel, colBorderStrong, dp(1), 0)
            val margin = dp(12)
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(margin, dp(16), margin, dp(28))
            }
        }

        // 1. TOP HARDWARE MASTHEAD & DIAGNOSTIC STRIP
        shell.addView(buildMasthead())
        shell.addView(hairlineH())

        // 2. 1PX HAIRLINE TELEMETRY MATRIX (2x2)
        shell.addView(buildTelemetryMatrix())
        shell.addView(hairlineH())

        // 3. SECTION 01: WRIST MEDIASESSION HUD VIEWPORT (CASIO GD-8 STN-LCD)
        shell.addView(buildWristHudSection())
        shell.addView(hairlineH())

        // 4. SECTION 02: HWHD07 WATCH FACE STUDIO (194x368)
        shell.addView(buildWatchFaceStudioSection())
        shell.addView(hairlineH())

        // 5. PROTOCOL FOOTER
        shell.addView(buildFooter())

        scroll.addView(shell)
        return scroll
    }

    private fun buildMasthead(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colRecessed)
            setPadding(dp(16), dp(14), dp(16), dp(14))

            val topRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val badge = TextView(this@MainActivity).apply {
                text = "GD-6"
                setTextColor(colLcdInk)
                setBackgroundColor(colLcdSurface)
                typeface = Typeface.MONOSPACE
                textSize = 11f
                setPadding(dp(6), dp(2), dp(6), dp(2))
            }

            val title = TextView(this@MainActivity).apply {
                text = "HUAWEI BAND 6 TELEMETRY CONSOLE"
                setTextColor(colTextPrimary)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 14.5f
                setPadding(dp(10), 0, 0, 0)
            }

            topRow.addView(badge)
            topRow.addView(title)

            // Diagnostic status row
            val diagRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, 0)
            }

            // Bridge Status Indicator
            val bridgeBox = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            viewBridgeLed = View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(7), dp(7))
                setBackgroundColor(colBrass)
            }
            tvBridgeState = TextView(this@MainActivity).apply {
                text = "BRIDGE: STANDBY"
                setTextColor(colTextSecondary)
                typeface = Typeface.MONOSPACE
                textSize = 11f
                setPadding(dp(6), 0, 0, 0)
            }
            bridgeBox.addView(viewBridgeLed)
            bridgeBox.addView(tvBridgeState)

            // Maps Listener Permission Status (Clickable inline)
            val mapsBox = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
            viewMapsLed = View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(7), dp(7))
                setBackgroundColor(colCrimson)
            }
            tvMapsPermissionState = TextView(this@MainActivity).apply {
                text = "MAPS: TAP TO BIND"
                setTextColor(colBrass)
                typeface = Typeface.MONOSPACE
                textSize = 11f
                setPadding(dp(6), 0, 0, 0)
            }
            mapsBox.addView(viewMapsLed)
            mapsBox.addView(tvMapsPermissionState)

            diagRow.addView(bridgeBox)
            diagRow.addView(mapsBox)

            addView(topRow)
            addView(diagRow)
        }
    }

    private fun buildTelemetryMatrix(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Row 1: Ground Velocity + Peak Record
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val cellVelocity = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f)

            addView(monoLabel("GROUND VELOCITY • GPS 1HZ", colTextMuted, 10f))
            tvVelocityValue = TextView(this@MainActivity).apply {
                text = "0 km/h"
                setTextColor(colTextPrimary)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 30f
                setPadding(0, dp(4), 0, dp(4))
            }
            tvVelocityBar = monoLabel("[░░░░░░░░] LIM 80 KM/H", colBrass, 10.5f)
            addView(tvVelocityValue)
            addView(tvVelocityBar)
        }

        val cellPeak = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener {
                GpsNavRepository.resetTripStats()
                notifyServiceRefresh(false)
                Toast.makeText(this@MainActivity, "Odometer & Peak velocity zeroed", Toast.LENGTH_SHORT).show()
            }

            addView(monoLabel("PEAK RECORD", colTextMuted, 10f))
            tvPeakValue = TextView(this@MainActivity).apply {
                text = "0 km/h"
                setTextColor(colTextPrimary)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 20f
                setPadding(0, dp(8), 0, dp(6))
            }
            addView(tvPeakValue)
            addView(monoLabel("[TAP TO ZERO TRIP]", colTextSecondary, 10f))
        }

        row1.addView(cellVelocity)
        row1.addView(hairlineV())
        row1.addView(cellPeak)

        // Row 2: Trip Odometer + Compass Heading
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val cellOdo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f)

            addView(monoLabel("TRIP ODOMETER", colTextMuted, 10f))
            tvOdoValue = TextView(this@MainActivity).apply {
                text = "0 m"
                setTextColor(colTextPrimary)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 20f
                setPadding(0, dp(6), 0, dp(4))
            }
            tvAltValue = monoLabel("ALT 0 m ASL", colTextSecondary, 10.5f)
            addView(tvOdoValue)
            addView(tvAltValue)
        }

        val cellCompass = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

            addView(monoLabel("HEADING • TRUE DEG", colTextMuted, 10f))
            tvCompassValue = TextView(this@MainActivity).apply {
                text = "U 000°"
                setTextColor(colTextPrimary)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 20f
                setPadding(0, dp(6), 0, dp(4))
            }
            tvCoordsValue = monoLabel("0.00000, 0.00000", colTextSecondary, 10f)
            addView(tvCompassValue)
            addView(tvCoordsValue)
        }

        row2.addView(cellOdo)
        row2.addView(hairlineV())
        row2.addView(cellCompass)

        container.addView(row1)
        container.addView(hairlineH())
        container.addView(row2)
        return container
    }

    private fun buildWristHudSection(): LinearLayout {
        val sec = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
        }

        // Section Header (01 / WRIST MEDIASESSION HUD VIEWPORT)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        val idx = monoLabel("01 / ", colBrass, 11f)
        val title = TextView(this).apply {
            text = "WRIST MEDIASESSION HUD VIEWPORT"
            setTextColor(colTextPrimary)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvHudPageMeta = monoLabel("MODE 1/4", colTextMuted, 10.5f)
        headerRow.addView(idx)
        headerRow.addView(title)
        headerRow.addView(tvHudPageMeta)
        sec.addView(headerRow)

        // Metallic Bezel Frame housing the Reflective STN-LCD Screen
        val bezel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = boxBg(colRecessed, Color.parseColor("#9FA6A2"), dp(2), dp(2))
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }

        val bezelHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(8))
        }
        bezelHeader.addView(monoLabel("RACING TELEMETRY", colTextSecondary, 10f).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        bezelHeader.addView(monoLabel("CAR RACE GD-6", colBrass, 10f))
        bezel.addView(bezelHeader)

        // Reflective Olive STN-LCD Screen (#C9D1A8)
        val lcdScreen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = boxBg(colLcdSurface, colLcdInk, dp(2), dp(2))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        val lcdTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(6))
        }
        tvLcdModeTag = monoLabel("MODE 01: NAV+SPD", colLcdInk, 10.5f).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvLcdEtaTag = monoLabel("STANDBY", colLcdInk, 10.5f)
        lcdTopRow.addView(tvLcdModeTag)
        lcdTopRow.addView(tvLcdEtaTag)

        val lcdDivider1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(Color.argb(90, 28, 36, 30))
        }

        tvBandHudTitle = TextView(this).apply {
            text = "0 km/j • U (0°)"
            setTextColor(colLcdInk)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 18.5f
            setPadding(0, dp(10), 0, dp(4))
        }

        tvBandHudSubtitle = TextView(this).apply {
            text = "Menunggu rute Google Maps..."
            setTextColor(colLcdInk)
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setPadding(0, 0, 0, dp(10))
        }

        // Bottom Teal Trapezoid Strip inside the LCD
        val tealStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = boxBg(colLcdTeal, colLcdInk, dp(1), 0)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        tealStrip.addView(monoLabel("ALM 50 100 150 250 300", colLcdInk, 10f).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        tvLcdTrapezoidRight = monoLabel("SIG 0.0 km", colLcdInk, 10f)
        tealStrip.addView(tvLcdTrapezoidRight)

        lcdScreen.addView(lcdTopRow)
        lcdScreen.addView(lcdDivider1)
        lcdScreen.addView(tvBandHudTitle)
        lcdScreen.addView(tvBandHudSubtitle)
        lcdScreen.addView(tealStrip)
        bezel.addView(lcdScreen)
        sec.addView(bezel)

        // Tactile 3-Key Switch Bank (< LEFT / PULSE / RIGHT >)
        val keyRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        val btnPrev = mechButton("< PREV MODE", colKey, colBorderStrong, colTextPrimary).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(6) }
            setOnClickListener {
                GpsNavRepository.previousHudPage()
                notifyServiceRefresh(false)
            }
        }
        val btnPulse = mechButton("PULSE WRIST", colCrimson, Color.parseColor("#E0533F"), Color.WHITE).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(6) }
            setOnClickListener { triggerTestAlertOnWrist() }
        }
        val btnNext = mechButton("NEXT MODE >", colKey, colBorderStrong, colTextPrimary).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            setOnClickListener {
                GpsNavRepository.nextHudPage()
                notifyServiceRefresh(false)
            }
        }
        keyRow1.addView(btnPrev)
        keyRow1.addView(btnPulse)
        keyRow1.addView(btnNext)
        sec.addView(keyRow1)

        // Primary Service Toggle + Turn Simulator Row
        val keyRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        btnToggleService = mechButton("ENGAGE HUD BRIDGE", colCrimson, Color.parseColor("#E0533F"), Color.WHITE).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1.3f).apply { rightMargin = dp(6) }
            setOnClickListener { toggleGpsService() }
        }
        val btnSimTurn = mechButton("SIMULATE MANEUVER", colKey, colBrass, colBrass).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
            setOnClickListener { simulateTurnAndVibrateWrist() }
        }
        keyRow2.addView(btnToggleService)
        keyRow2.addView(btnSimTurn)
        sec.addView(keyRow2)

        return sec
    }

    private fun buildWatchFaceStudioSection(): LinearLayout {
        val sec = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121514"))
            setPadding(dp(16), dp(18), dp(16), dp(18))
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        headerRow.addView(monoLabel("02 / ", colBrass, 11f))
        headerRow.addView(TextView(this).apply {
            text = "HWHD07 WATCH FACE STUDIO"
            setTextColor(colTextPrimary)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        headerRow.addView(monoLabel("194×368 PX", colTextMuted, 10.5f))
        sec.addView(headerRow)

        // Split Layout: Left 194x368 Canvas Preview + Right Controls
        val splitRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        ivWatchFacePreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(122), dp(231)).apply {
                rightMargin = dp(14)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = boxBg(colRecessed, colBorderStrong, dp(1), 0)
        }

        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        themeButtons.clear()
        val shortLabels = arrayOf(
            "01 / Car Race LCD",
            "02 / Garmin Tactical",
            "03 / Cyber Telemetry",
            "04 / NightOps Radar"
        )
        for (i in themes.indices) {
            val btn = Button(this).apply {
                text = shortLabels.getOrElse(i) { themes[i].label }
                typeface = Typeface.MONOSPACE
                textSize = 11f
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                isAllCaps = false
                setPadding(dp(10), 0, dp(10), 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(36)
                ).apply { bottomMargin = dp(4) }
                setOnClickListener {
                    selectedThemeIndex = i
                    refreshWatchFacePreview(GpsNavRepository.state)
                }
            }
            themeButtons.add(btn)
            rightCol.addView(btn)
        }

        val btnSaveGallery = mechButton("SAVE 194×368 PNG", colCrimson, Color.parseColor("#E0533F"), Color.WHITE).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
            ).apply { topMargin = dp(6) }
            setOnClickListener {
                val savedPath = WatchFaceGenerator.saveWatchFaceToGallery(
                    this@MainActivity,
                    GpsNavRepository.state,
                    themes[selectedThemeIndex]
                )
                if (savedPath != null) {
                    Toast.makeText(
                        this@MainActivity,
                        "Saved to $savedPath (Apply via Huawei Health -> Watch Faces -> Gallery)",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to write PNG", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnExportHwt = mechButton("EXPORT .HWT BINARY", colKey, colBrass, colBrass).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
            ).apply { topMargin = dp(6) }
            setOnClickListener {
                val path = WatchFaceGenerator.exportBundledHwtToDownloads(this@MainActivity)
                if (path != null) {
                    Toast.makeText(
                        this@MainActivity,
                        "Exported HWHD07 binary to $path",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to export .HWT", Toast.LENGTH_SHORT).show()
                }
            }
        }

        rightCol.addView(btnSaveGallery)
        rightCol.addView(btnExportHwt)

        splitRow.addView(ivWatchFacePreview)
        splitRow.addView(rightCol)
        sec.addView(splitRow)

        val specNote = monoLabel(
            "GALLERY MODE: Saves 194×368 PNG with your Peak KM/H & Trip Odometer stamped.\n" +
                "BINARY MODE: Exports CarRace_GD6_Band6.hwt (HWHD07 v2.1.1) to Downloads.",
            colTextMuted,
            10f
        ).apply { setPadding(0, dp(12), 0, 0) }
        sec.addView(specNote)

        return sec
    }

    private fun buildFooter(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colRecessed)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addView(monoLabel("TRANSPORT: ANDROID MEDIASESSION + NOTIFICATION LISTENER BRIDGE", colTextMuted, 9.5f))
        }
    }

    private fun renderSnapshot(snap: GpsNavSnapshot) {
        val cleanTitle = stripEmojis(snap.buildBandHudTitle())
        val cleanSub = stripEmojis(snap.buildBandHudSubtitle())

        tvVelocityValue.text = "${snap.speedKmh.roundToInt()} km/h"
        tvVelocityBar.text = "${snap.visualSpeedBar(snap.speedLimitAlertKmh)} LIM ${snap.speedLimitAlertKmh} KM/H"
        tvPeakValue.text = "${snap.maxSpeedKmh.roundToInt()} km/h"
        tvOdoValue.text = snap.formattedTripDistance()
        tvAltValue.text = "ALT ${snap.altitudeMeters.roundToInt()} m ASL"
        tvCompassValue.text = "${snap.compassCardinal()} ${String.format(Locale.US, "%03d", snap.bearingDeg.roundToInt())}°"
        tvCoordsValue.text = String.format(Locale.US, "%.5f, %.5f", snap.latitude, snap.longitude)

        val pageOrd = snap.activeHudPage.ordinal + 1
        tvHudPageMeta.text = "PAGE $pageOrd/4"
        tvLcdModeTag.text = snap.activeHudPage.title.uppercase(Locale.US)
        tvLcdEtaTag.text = if (snap.isMapsActive) "ETA ${snap.etaSummary}" else "GPS STANDBY"
        tvBandHudTitle.text = cleanTitle
        tvBandHudSubtitle.text = cleanSub
        tvLcdTrapezoidRight.text = "SIG ${snap.formattedTripDistance()}"

        if (snap.isServiceRunning) {
            viewBridgeLed.setBackgroundColor(colSignalOk)
            tvBridgeState.text = "BRIDGE: ACTIVE"
            tvBridgeState.setTextColor(colTextPrimary)
            btnToggleService.text = "TERMINATE BRIDGE"
            btnToggleService.background = boxBg(colKey, colCrimson, dp(1), dp(2))
            btnToggleService.setTextColor(colCrimson)
        } else {
            viewBridgeLed.setBackgroundColor(colBrass)
            tvBridgeState.text = "BRIDGE: STANDBY"
            tvBridgeState.setTextColor(colTextSecondary)
            btnToggleService.text = "ENGAGE HUD BRIDGE"
            btnToggleService.background = boxBg(colCrimson, Color.parseColor("#E0533F"), dp(1), dp(2))
            btnToggleService.setTextColor(Color.WHITE)
        }

        refreshWatchFacePreview(snap)
    }

    private fun stripEmojis(raw: String): String {
        return raw
            .replace("⬅️", "[L]")
            .replace("➡️", "[R]")
            .replace("⬆️", "[STR]")
            .replace("↩️", "[U-TURN]")
            .replace("↖️", "[SL-L]")
            .replace("↗️", "[SL-R]")
            .replace("🔄", "[RND]")
            .replace("🏁", "[FIN]")
            .replace("⚡", "")
            .replace("🧭", "")
            .replace("📏", "ODO ")
            .replace("⛰️", "ALT ")
            .replace("📍", "")
            .replace("🎯", "ACC ")
            .trim()
    }

    private fun refreshWatchFacePreview(snap: GpsNavSnapshot) {
        for (i in themeButtons.indices) {
            val active = (i == selectedThemeIndex)
            themeButtons[i].background = boxBg(
                if (active) colKey else colRecessed,
                if (active) colBrass else colHairline,
                dp(1),
                0
            )
            themeButtons[i].setTextColor(if (active) colTextPrimary else colTextSecondary)
        }
        val bmp = WatchFaceGenerator.renderWatchFaceBitmap(snap, themes[selectedThemeIndex])
        ivWatchFacePreview.setImageBitmap(bmp)
    }

    private fun toggleGpsService() {
        val isRunning = GpsNavRepository.state.isServiceRunning
        val intent = Intent(this, BandGpsForegroundService::class.java).apply {
            action = if (isRunning) BandGpsForegroundService.ACTION_STOP else BandGpsForegroundService.ACTION_START
        }
        if (isRunning) {
            startService(intent)
        } else {
            ContextCompat.startForegroundService(this, intent)
            Toast.makeText(this, "HUD Bridge engaged. Open Music Control on Huawei Band 6.", Toast.LENGTH_LONG).show()
        }
    }

    private fun simulateTurnAndVibrateWrist() {
        val sampleTurns = listOf(
            Triple("⬅️", "120 m", "Belok kiri ke Jl. Jend. Sudirman"),
            Triple("➡️", "50 m", "Belok kanan ke Jl. MH Thamrin"),
            Triple("↩️", "200 m", "Putar balik di Bundaran HI"),
            Triple("🏁", "30 m", "Tujuan berada di sisi kiri")
        )
        val pick = sampleTurns.random()
        GpsNavRepository.update { curr ->
            curr.copy(
                isMapsActive = true,
                directionArrow = pick.first,
                distanceToTurn = pick.second,
                nextStreetName = pick.third,
                etaSummary = "15:20 • 2.4 km",
                speedKmh = if (curr.speedKmh < 5f) 54f else curr.speedKmh,
                maxSpeedKmh = if (curr.maxSpeedKmh < 54f) 68f else curr.maxSpeedKmh,
                bearingDeg = 315f
            )
        }
        triggerTestAlertOnWrist()
    }

    private fun triggerTestAlertOnWrist() {
        val intent = Intent(this, BandGpsForegroundService::class.java).apply {
            action = BandGpsForegroundService.ACTION_PUSH_TEST_ALERT
        }
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "Dispatched telemetry alert to Huawei Band 6", Toast.LENGTH_SHORT).show()
    }

    private fun notifyServiceRefresh(triggerWristAlert: Boolean) {
        if (!GpsNavRepository.state.isServiceRunning) return
        val intent = Intent(this, BandGpsForegroundService::class.java).apply {
            action = BandGpsForegroundService.ACTION_MAPS_UPDATED
            putExtra(BandGpsForegroundService.EXTRA_TRIGGER_BAND_ALERT, triggerWristAlert)
        }
        startService(intent)
    }

    private fun updateNotificationListenerState() {
        val cn = ComponentName(this, MapsNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
        val enabled = flat.contains(cn.flattenToString())
        if (enabled) {
            viewMapsLed.setBackgroundColor(colSignalOk)
            tvMapsPermissionState.text = "MAPS LISTENER: BOUND"
            tvMapsPermissionState.setTextColor(colTextSecondary)
        } else {
            viewMapsLed.setBackgroundColor(colCrimson)
            tvMapsPermissionState.text = "MAPS: TAP TO BIND"
            tvMapsPermissionState.setTextColor(colBrass)
        }
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }

    private fun monoLabel(textStr: String, color: Int, sizeSp: Float): TextView {
        return TextView(this).apply {
            text = textStr
            setTextColor(color)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = sizeSp
        }
    }

    private fun mechButton(label: String, fillCol: Int, strokeCol: Int, textCol: Int): Button {
        return Button(this).apply {
            text = label
            setTextColor(textCol)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 11f
            isAllCaps = false
            background = boxBg(fillCol, strokeCol, dp(1), dp(2))
            setPadding(dp(8), 0, dp(8), 0)
        }
    }

    private fun hairlineH(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(colHairline)
        }
    }

    private fun hairlineV(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(colHairline)
        }
    }

    private fun boxBg(fillCol: Int, strokeCol: Int, strokePx: Int, radiusPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillCol)
            setStroke(strokePx, strokeCol)
            cornerRadius = radiusPx.toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }
}
