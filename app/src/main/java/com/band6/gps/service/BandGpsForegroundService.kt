package com.band6.gps.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.band6.gps.MainActivity
import com.band6.gps.model.GpsNavRepository
import com.band6.gps.model.GpsNavSnapshot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Service pemantau GPS & pengirim Smart Notification HUD langsung ke Huawei Band 6.
 * Menggunakan kanal notifikasi bergetar prioritas tinggi yang diteruskan otomatis oleh Huawei Health
 * ke layar Huawei Band 6 tanpa membajak kontrol musik (Spotify/YouTube tetap bebas berputar di HP).
 */
class BandGpsForegroundService : Service(), LocationListener {

    companion object {
        const val ACTION_START = "com.band6.gps.ACTION_START"
        const val ACTION_STOP = "com.band6.gps.ACTION_STOP"
        const val ACTION_MAPS_UPDATED = "com.band6.gps.ACTION_MAPS_UPDATED"
        const val ACTION_PUSH_TEST_ALERT = "com.band6.gps.ACTION_PUSH_TEST_ALERT"
        const val EXTRA_TRIGGER_BAND_ALERT = "extra_trigger_band_alert"

        private const val CHANNEL_FOREGROUND_ID = "band6_gps_service_channel"
        private const val CHANNEL_BAND_ALERT_ID = "band6_gps_wrist_alert_channel"
        private const val FG_NOTIFICATION_ID = 1001
        private const val BAND_ALERT_BASE_ID = 2000
    }

    private lateinit var notificationManager: NotificationManager
    private lateinit var locationManager: LocationManager
    private var lastLocation: Location? = null
    private var lastWristAlertTimeMs: Long = 0L
    private var alertRollingId: Int = BAND_ALERT_BASE_ID

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopGpsAndService()
                return START_NOT_STICKY
            }
            ACTION_MAPS_UPDATED -> {
                val triggerAlert = intent.getBooleanExtra(EXTRA_TRIGGER_BAND_ALERT, false)
                refreshHudAndNotifications(triggerWristAlert = triggerAlert)
                return START_STICKY
            }
            ACTION_PUSH_TEST_ALERT -> {
                val snap = GpsNavRepository.state
                pushWristPopupNotification(
                    title = snap.buildNotificationAlertTitle(),
                    body = snap.buildNotificationAlertBody(),
                    force = true
                )
                refreshHudAndNotifications(triggerWristAlert = false)
                return START_STICKY
            }
            else -> {
                startForegroundWithCompatibility()
                startLocationUpdates()
                GpsNavRepository.update { it.copy(isServiceRunning = true) }
                refreshHudAndNotifications(triggerWristAlert = false)
                return START_STICKY
            }
        }
    }

    private fun startForegroundWithCompatibility() {
        val notification = buildForegroundNotification(GpsNavRepository.state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FG_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(FG_NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L, // Update setiap 1 detik
                    0f,
                    this
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    3000L,
                    0f,
                    this
                )
            }
        } catch (_: SecurityException) {
            // Izin GPS belum diberikan
        }
    }

    override fun onLocationChanged(location: Location) {
        val rawSpeedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        // Filter noise GPS saat diam (< 2.5 km/jam dianggap 0 km/jam)
        val cleanSpeedKmh = if (rawSpeedKmh < 2.5f) 0f else rawSpeedKmh

        var deltaMeters = 0.0
        val prev = lastLocation
        if (prev != null && location.accuracy <= 25f && cleanSpeedKmh >= 2.5f) {
            val d = prev.distanceTo(location).toDouble()
            if (d in 1.0..150.0) {
                deltaMeters = d
            }
        }
        lastLocation = location

        var exceededSpeedLimit = false
        GpsNavRepository.update { curr ->
            val newMax = max(curr.maxSpeedKmh, cleanSpeedKmh)
            exceededSpeedLimit = cleanSpeedKmh.roundToInt() >= curr.speedLimitAlertKmh &&
                curr.speedKmh.roundToInt() < curr.speedLimitAlertKmh

            curr.copy(
                speedKmh = cleanSpeedKmh,
                maxSpeedKmh = newMax,
                bearingDeg = if (location.hasBearing()) location.bearing else curr.bearingDeg,
                altitudeMeters = if (location.hasAltitude()) location.altitude else curr.altitudeMeters,
                accuracyMeters = location.accuracy,
                latitude = location.latitude,
                longitude = location.longitude,
                tripDistanceMeters = curr.tripDistanceMeters + deltaMeters
            )
        }

        if (exceededSpeedLimit) {
            val snap = GpsNavRepository.state
            pushWristPopupNotification(
                title = "⚠️ Batas Kecepatan: ${snap.speedKmh.roundToInt()} km/j!",
                body = "Melebihi batas aman ${snap.speedLimitAlertKmh} km/j • ODO: ${snap.formattedTripDistance()}",
                force = true
            )
        }

        refreshHudAndNotifications(triggerWristAlert = false)
    }

    private fun refreshHudAndNotifications(triggerWristAlert: Boolean) {
        val snap = GpsNavRepository.state
        // Perbarui status service di status bar ponsel (senyap)
        notificationManager.notify(FG_NOTIFICATION_ID, buildForegroundNotification(snap))

        // Kirim pop-up bergetar ke pergelangan tangan jika ada belokan atau kondisi dipicu
        if (triggerWristAlert && snap.pushTurnAlertsEnabled) {
            pushWristPopupNotification(
                title = snap.buildNotificationAlertTitle(),
                body = snap.buildNotificationAlertBody(),
                force = false
            )
        }
    }

    /**
     * Mengirim notifikasi bergetar prioritas tinggi (CATEGORY_NAVIGATION)
     * yang akan diteruskan oleh Huawei Health ke layar Huawei Band 6 secara instan.
     */
    fun pushWristPopupNotification(title: String, body: String, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        // Cooldown minimal 4 detik antar notifikasi otomatis agar tidak membanjiri gelang
        if (!force && (now - lastWristAlertTimeMs) < 4000L) {
            return
        }
        lastWristAlertTimeMs = now

        // Huawei Health membutuhkan notifikasi baru yang jelas. Menggilir ID (2000..2002)
        // dan membatalkan ID sebelumnya menjamin event 'NotificationPosted' selalu tertangkap
        // oleh Huawei Health Bluetooth Relay.
        val prevId = alertRollingId
        alertRollingId = if (alertRollingId >= BAND_ALERT_BASE_ID + 2) BAND_ALERT_BASE_ID else alertRollingId + 1
        notificationManager.cancel(prevId)

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            alertRollingId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val vibrationPattern = longArrayOf(0, 300, 120, 300)

        val alertNotification = NotificationCompat.Builder(this, CHANNEL_BAND_ALERT_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(vibrationPattern)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setTimeoutAfter(20_000L) // Bersihkan otomatis setelah 20 detik jika tidak di-swipe
            .build()

        notificationManager.notify(alertRollingId, alertNotification)
    }

    private fun buildForegroundNotification(snap: GpsNavSnapshot): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_FOREGROUND_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Band 6 GPS HUD • ${snap.speedKmh.roundToInt()} km/j")
            .setContentText(
                if (snap.isMapsActive) "${snap.directionArrow} ${snap.distanceToTurn} - ${snap.nextStreetName}"
                else "GPS Terhubung • ODO: ${snap.formattedTripDistance()}"
            )
            .setSubText(snap.activeHudPage.title)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val fgChannel = NotificationChannel(
                CHANNEL_FOREGROUND_ID,
                "Status Layanan GPS HUD (Senyap)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjaga GPS tetap aktif di latar belakang saat berkendara"
                setShowBadge(false)
            }

            val wristAlertChannel = NotificationChannel(
                CHANNEL_BAND_ALERT_ID,
                "Notifikasi Belokan & Speed ke Huawei Band 6",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Meneruskan pop-up arah & peringatan kecepatan ke layar Huawei Band 6"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 120, 300)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(fgChannel)
            notificationManager.createNotificationChannel(wristAlertChannel)
        }
    }

    private fun stopGpsAndService() {
        try {
            locationManager.removeUpdates(this)
        } catch (_: Exception) {
        }
        GpsNavRepository.update { it.copy(isServiceRunning = false, speedKmh = 0f) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopGpsAndService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
