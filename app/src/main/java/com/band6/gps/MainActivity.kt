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
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var tvBandModeBadge: TextView
    private lateinit var tvBandHudTitle: TextView
    private lateinit var tvBandHudSubtitle: TextView
    private lateinit var tvLiveSpeed: TextView
    private lateinit var tvStatsDetail: TextView
    private lateinit var tvMapsStatus: TextView
    private lateinit var btnToggleService: Button
    private lateinit var btnNotificationAccess: Button
    private lateinit var ivWatchFacePreview: ImageView
    private lateinit var tvWatchFaceThemeLabel: TextView

    private var selectedThemeIndex = 0
    private val themes = WatchFaceGenerator.CockpitTheme.entries

    private val stateListener: (GpsNavSnapshot) -> Unit = { snapshot ->
        runOnUiThread { renderSnapshot(snapshot) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildDashboardView())
        requestNeededPermissions()
    }

    override fun onStart() {
        super.onStart()
        GpsNavRepository.addListener(stateListener)
        updateNotificationListenerButtonState()
    }

    override fun onStop() {
        GpsNavRepository.removeListener(stateListener)
        super.onStop()
    }

    private fun buildDashboardView(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D1117"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, dp(28), pad, dp(36))
        }

        // Header
        val headerTitle = TextView(this).apply {
            text = "HUAWEI BAND 6 • GPS HUD & WATCHFACE"
            setTextColor(Color.parseColor("#FF6B2C"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        }
        val headerSub = TextView(this).apply {
            text = "Navigasi Arah, Speedometer & Kustom UI"
            setTextColor(Color.WHITE)
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(18))
        }
        root.addView(headerTitle)
        root.addView(headerSub)

        // Simulasi Layar Huawei Band 6 (1.47" AMOLED Pill Preview)
        val bandCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedBg("#161B22", "#30363D", dp(24))
            val pad = dp(18)
            setPadding(pad, pad, pad, pad)
        }

        val previewLabel = TextView(this).apply {
            text = "PRATINJAU LIVE HUD DI HUAWEI BAND 6"
            setTextColor(Color.parseColor("#8B949E"))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        tvBandModeBadge = TextView(this).apply {
            text = "Mode 1: Navigasi + Speed"
            setTextColor(Color.parseColor("#58A6FF"))
            textSize = 12f
            setPadding(0, dp(6), 0, dp(10))
            gravity = Gravity.CENTER
        }

        // Layar Dalam Band 6
        val bandScreen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg("#05070A", "#FF6B2C", dp(22))
            val pad = dp(18)
            setPadding(pad, dp(20), pad, dp(20))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        tvBandHudTitle = TextView(this).apply {
            text = "⬆️ -- m • 0 km/j"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }

        tvBandHudSubtitle = TextView(this).apply {
            text = "Menunggu rute Google Maps..."
            setTextColor(Color.parseColor("#A5D6FF"))
            textSize = 13.5f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(14))
        }

        // Baris Tombol Kontrol Musik (Prev / Play / Next)
        val mediaBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val btnPrevPage = Button(this).apply {
            text = "⏮️ Mode"
            setOnClickListener {
                GpsNavRepository.previousHudPage()
                notifyServiceRefresh(false)
            }
        }
        val btnPushPopup = Button(this).apply {
            text = "⏯️ Kirim Getar"
            setOnClickListener {
                triggerTestAlertOnWrist()
            }
        }
        val btnNextPage = Button(this).apply {
            text = "Mode ⏭️"
            setOnClickListener {
                GpsNavRepository.nextHudPage()
                notifyServiceRefresh(false)
            }
        }

        mediaBtnRow.addView(btnPrevPage)
        mediaBtnRow.addView(btnPushPopup)
        mediaBtnRow.addView(btnNextPage)

        bandScreen.addView(tvBandHudTitle)
        bandScreen.addView(tvBandHudSubtitle)
        bandScreen.addView(mediaBtnRow)

        val bandHint = TextView(this).apply {
            text = "💡 Tekan Next/Prev di layar Musik Huawei Band 6 untuk mengganti 4 Mode Tampilan (termasuk Mode Visual Bar Tachometer & Pita Kompas)!"
            setTextColor(Color.parseColor("#8B949E"))
            textSize = 12f
            setPadding(0, dp(12), 0, 0)
        }

        bandCard.addView(previewLabel)
        bandCard.addView(tvBandModeBadge)
        bandCard.addView(bandScreen)
        bandCard.addView(bandHint)
        root.addView(bandCard)

        // Kartu Speedometer & Telemetri GPS
        val telemetryCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg("#161B22", "#30363D", dp(18))
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(16)
            layoutParams = lp
        }

        tvLiveSpeed = TextView(this).apply {
            text = "0 km/jam"
            setTextColor(Color.parseColor("#3FB950"))
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
        }

        tvStatsDetail = TextView(this).apply {
            text = "Maks: 0 km/j  •  Jarak: 0 m  •  Kompas: U (0°)"
            setTextColor(Color.parseColor("#C9D1D9"))
            textSize = 14f
            setPadding(0, dp(6), 0, dp(6))
        }

        tvMapsStatus = TextView(this).apply {
            text = "Status Google Maps: Menunggu navigasi aktif"
            setTextColor(Color.parseColor("#D29922"))
            textSize = 13f
        }

        telemetryCard.addView(tvLiveSpeed)
        telemetryCard.addView(tvStatsDetail)
        telemetryCard.addView(tvMapsStatus)
        root.addView(telemetryCard)

        // Tombol Utama: Mulai / Hentikan GPS HUD
        btnToggleService = Button(this).apply {
            text = "🚀 AKTIFKAN GPS HUD KE HUAWEI BAND 6"
            setBackgroundColor(Color.parseColor("#238636"))
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(18)
            layoutParams = lp
            setOnClickListener { toggleGpsService() }
        }
        root.addView(btnToggleService)

        // Tombol Izin Notification Listener (Untuk membaca arah Google Maps)
        btnNotificationAccess = Button(this).apply {
            text = "🔔 Berikan Izin Baca Rute Google Maps"
            setBackgroundColor(Color.parseColor("#1F6FEB"))
            setTextColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(10)
            layoutParams = lp
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        root.addView(btnNotificationAccess)

        // Tombol Simulasi Belokan & Reset Jarak
        val btnSimulateNav = Button(this).apply {
            text = "🧪 Simulasi Belokan Maps + Tes Getar Band 6"
            setBackgroundColor(Color.parseColor("#6E40C9"))
            setTextColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(10)
            layoutParams = lp
            setOnClickListener { simulateTurnAndVibrateWrist() }
        }
        root.addView(btnSimulateNav)

        // =========================================================================
        // KARTU CUSTOM WATCH FACE GENERATOR (CARA 2: DESAIN UI 194x368 PIXEL)
        // =========================================================================
        val wfCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedBg("#161B22", "#FF6B2C", dp(20))
            val pad = dp(18)
            setPadding(pad, pad, pad, pad)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(22)
            layoutParams = lp
        }

        val wfHeader = TextView(this).apply {
            text = "🎨 STUDIO CUSTOM WATCH FACE GPS (194×368)"
            setTextColor(Color.parseColor("#FF6B2C"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        tvWatchFaceThemeLabel = TextView(this).apply {
            text = themes[selectedThemeIndex].label
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(12))
            gravity = Gravity.CENTER
        }

        ivWatchFacePreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(145), dp(276))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val btnSwitchTheme = Button(this).apply {
            text = "🎨 Ganti Tema Warna Cockpit Watch Face"
            setBackgroundColor(Color.parseColor("#30363D"))
            setTextColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(12)
            layoutParams = lp
            setOnClickListener {
                selectedThemeIndex = (selectedThemeIndex + 1) % themes.size
                refreshWatchFacePreview(GpsNavRepository.state)
            }
        }

        val btnSaveWatchFace = Button(this).apply {
            text = "💾 SIMPAN WATCH FACE (194×368) KE GALERI HP"
            setBackgroundColor(Color.parseColor("#D29922"))
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(8)
            layoutParams = lp
            setOnClickListener {
                val savedPath = WatchFaceGenerator.saveWatchFaceToGallery(
                    this@MainActivity,
                    GpsNavRepository.state,
                    themes[selectedThemeIndex]
                )
                if (savedPath != null) {
                    Toast.makeText(
                        this@MainActivity,
                        "✅ Tersimpan di Galeri ($savedPath)! Buka Huawei Health -> Watch Faces -> Gallery untuk memasangnya.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this@MainActivity, "Gagal menyimpan gambar Watch Face", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val wfDesc = TextView(this).apply {
            text = "📌 Cara Pasang Tanpa Mod/Root:\n1. Klik 'Simpan Watch Face ke Galeri HP' di atas.\n2. Buka aplikasi Huawei Health -> Perangkat -> Watch Faces -> pilih 'Gallery' (Galeri).\n3. Pilih gambar 194×368 yang baru disimpan, atur posisi jam di bagian atas (di dalam siku tanda kurung), lalu klik Save!\n*(Untuk file .hwt penuh bagi pengguna Gadgetbridge/Huawei Health Mod juga sudah tersedia di folder watchface-hwt-kit di GitHub).*"
            setTextColor(Color.parseColor("#8B949E"))
            textSize = 12f
            setPadding(0, dp(10), 0, 0)
        }

        wfCard.addView(wfHeader)
        wfCard.addView(tvWatchFaceThemeLabel)
        wfCard.addView(ivWatchFacePreview)
        wfCard.addView(btnSwitchTheme)
        wfCard.addView(btnSaveWatchFace)
        wfCard.addView(wfDesc)
        root.addView(wfCard)

        val btnResetTrip = Button(this).apply {
            text = "🔄 Reset Odometer (Jarak & Kecepatan Maks)"
            setBackgroundColor(Color.parseColor("#21262D"))
            setTextColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(14)
            layoutParams = lp
            setOnClickListener {
                GpsNavRepository.resetTripStats()
                notifyServiceRefresh(false)
                Toast.makeText(this@MainActivity, "Jarak tempuh & kec. maks direset!", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(btnResetTrip)

        scroll.addView(root)
        return scroll
    }

    private fun renderSnapshot(snap: GpsNavSnapshot) {
        tvBandModeBadge.text = snap.activeHudPage.title
        tvBandHudTitle.text = snap.buildBandHudTitle()
        tvBandHudSubtitle.text = snap.buildBandHudSubtitle()

        tvLiveSpeed.text = "${snap.speedKmh.roundToInt()} km/jam"
        tvStatsDetail.text =
            "Maks: ${snap.maxSpeedKmh.roundToInt()} km/j  •  Jarak: ${snap.formattedTripDistance()}  •  Kompas: ${snap.compassCardinal()} (${snap.bearingDeg.roundToInt()}°)"

        tvMapsStatus.text = if (snap.isMapsActive) {
            "🟢 Rute Aktif: ${snap.directionArrow} ${snap.distanceToTurn} -> ${snap.nextStreetName}"
        } else {
            "🟡 Google Maps belum navigasi (Mode Speedometer & Kompas Aktif)"
        }

        if (snap.isServiceRunning) {
            btnToggleService.text = "⏹️ HENTIKAN LAYANAN GPS HUD"
            btnToggleService.setBackgroundColor(Color.parseColor("#DA3633"))
        } else {
            btnToggleService.text = "🚀 AKTIFKAN GPS HUD KE HUAWEI BAND 6"
            btnToggleService.setBackgroundColor(Color.parseColor("#238636"))
        }

        refreshWatchFacePreview(snap)
    }

    private fun refreshWatchFacePreview(snap: GpsNavSnapshot) {
        val theme = themes[selectedThemeIndex]
        tvWatchFaceThemeLabel.text = theme.label
        val bmp = WatchFaceGenerator.renderWatchFaceBitmap(snap, theme)
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
            Toast.makeText(this, "GPS HUD Aktif! Buka menu Musik / Notifikasi di Huawei Band 6.", Toast.LENGTH_LONG).show()
        }
    }

    private fun simulateTurnAndVibrateWrist() {
        val sampleTurns = listOf(
            Triple("⬅️", "120 m", "Belok kiri ke Jl. Jend. Sudirman"),
            Triple("➡️", "50 m", "Belok kanan ke Jl. MH Thamrin"),
            Triple("↩️", "200 m", "Putar balik di depan Bundaran HI"),
            Triple("🏁", "30 m", "Tujuan Anda berada di sebelah kiri")
        )
        val pick = sampleTurns.random()
        GpsNavRepository.update { curr ->
            curr.copy(
                isMapsActive = true,
                directionArrow = pick.first,
                distanceToTurn = pick.second,
                nextStreetName = pick.third,
                etaSummary = "15:20 • 2.4 km",
                speedKmh = if (curr.speedKmh < 5f) 52f else curr.speedKmh,
                maxSpeedKmh = if (curr.maxSpeedKmh < 52f) 68f else curr.maxSpeedKmh,
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
        Toast.makeText(this, "Mengirim instruksi GPS ke Huawei Band 6...", Toast.LENGTH_SHORT).show()
    }

    private fun notifyServiceRefresh(triggerWristAlert: Boolean) {
        if (!GpsNavRepository.state.isServiceRunning) return
        val intent = Intent(this, BandGpsForegroundService::class.java).apply {
            action = BandGpsForegroundService.ACTION_MAPS_UPDATED
            putExtra(BandGpsForegroundService.EXTRA_TRIGGER_BAND_ALERT, triggerWristAlert)
        }
        startService(intent)
    }

    private fun updateNotificationListenerButtonState() {
        val cn = ComponentName(this, MapsNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
        val enabled = flat.contains(cn.flattenToString())
        if (enabled) {
            btnNotificationAccess.text = "✅ Izin Baca Google Maps Sudah Aktif"
            btnNotificationAccess.setBackgroundColor(Color.parseColor("#21262D"))
        } else {
            btnNotificationAccess.text = "🔔 Berikan Izin Baca Rute Google Maps (Wajib)"
            btnNotificationAccess.setBackgroundColor(Color.parseColor("#1F6FEB"))
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

    private fun roundedBg(fillHex: String, strokeHex: String, cornerRadiusPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            setStroke(dp(1), Color.parseColor(strokeHex))
            cornerRadius = cornerRadiusPx.toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }
}
