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
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.band6.gps.MainActivity
import com.band6.gps.model.GpsNavRepository
import com.band6.gps.model.GpsNavSnapshot
import kotlin.math.max
import kotlin.math.roundToInt

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
    private var mediaSession: MediaSessionCompat? = null
    private var lastLocation: Location? = null
    private var lastWristAlertTimeMs: Long = 0L
    private var alertRollingId: Int = BAND_ALERT_BASE_ID

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannels()
        initMediaSessionHud()
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
                pushWristPopupNotification(
                    title = "🧭 ${GpsNavRepository.state.buildBandHudTitle()}",
                    body = GpsNavRepository.state.buildBandHudSubtitle(),
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
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(FG_NOTIFICATION_ID, notification, type)
        } else {
            startForeground(FG_NOTIFICATION_ID, notification)
        }
    }

    /**
     * Inisialisasi MediaSession agar layar "Kontrol Musik" di Huawei Band 6
     * berubah menjadi layar Live HUD GPS tanpa getaran berulang, serta mendukung tombol Next/Prev di jam!
     */
    private fun initMediaSessionHud() {
        mediaSession = MediaSessionCompat(this, "HuaweiBand6GpsHud").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onSkipToNext() {
                    // Tombol NEXT di Huawei Band 6 ditekan -> Ganti ke halaman HUD berikutnya
                    GpsNavRepository.nextHudPage()
                    refreshHudAndNotifications(triggerWristAlert = false)
                }

                override fun onSkipToPrevious() {
                    // Tombol PREV di Huawei Band 6 ditekan -> Ganti ke halaman HUD sebelumnya
                    GpsNavRepository.previousHudPage()
                    refreshHudAndNotifications(triggerWristAlert = false)
                }

                override fun onPlay() {
                    // Tombol PLAY/PAUSE di Huawei Band 6 -> Kirim popup detail ke layar jam
                    pushWristPopupNotification(
                        title = GpsNavRepository.state.buildBandHudTitle(),
                        body = GpsNavRepository.state.buildBandHudSubtitle(),
                        force = true
                    )
                    updateMediaSessionMetadata(GpsNavRepository.state)
                }

                override fun onPause() {
                    onPlay()
                }
            })
            isActive = true
        }
        updateMediaSessionMetadata(GpsNavRepository.state)
    }

    private fun updateMediaSessionMetadata(snapshot: GpsNavSnapshot) {
        val session = mediaSession ?: return

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f, SystemClock.elapsedRealtime())
            .build()

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, snapshot.buildBandHudTitle())
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, snapshot.buildBandHudSubtitle())
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, snapshot.activeHudPage.title)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 3600_000L)
            .build()

        session.setPlaybackState(playbackState)
        session.setMetadata(metadata)
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
                body = "Melebihi batas ${snap.speedLimitAlertKmh} km/j • Jarak: ${snap.formattedTripDistance()}",
                force = false
            )
        }

        refreshHudAndNotifications(triggerWristAlert = false)
    }

    private fun refreshHudAndNotifications(triggerWristAlert: Boolean) {
        val snap = GpsNavRepository.state
        updateMediaSessionMetadata(snap)
        notificationManager.notify(FG_NOTIFICATION_ID, buildForegroundNotification(snap))

        if (triggerWristAlert && snap.pushTurnAlertsEnabled) {
            pushWristPopupNotification(
                title = "${snap.directionArrow} ${snap.distanceToTurn} (${snap.speedKmh.roundToInt()} km/j)",
                body = "${snap.nextStreetName} • ETA ${snap.etaSummary}",
                force = false
            )
        }
    }

    /**
     * Mengirim notifikasi standar Android yang akan diteruskan oleh Huawei Health
     * ke layar Huawei Band 6 lengkap dengan getaran di pergelangan tangan.
     */
    private fun pushWristPopupNotification(title: String, body: String, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        // Cooldown minimal 6 detik antar getaran otomatis agar tidak spam di Huawei Band 6
        if (!force && (now - lastWristAlertTimeMs) < 6000L) {
            return
        }
        lastWristAlertTimeMs = now

        // Huawei Health terkadang mengabaikan update notifikasi dengan ID yang sama persis,
        // sehingga kita menggilir ID notifikasi (2000..2002) dan menghapus yang lama.
        val prevId = alertRollingId
        alertRollingId = if (alertRollingId >= BAND_ALERT_BASE_ID + 2) BAND_ALERT_BASE_ID else alertRollingId + 1
        notificationManager.cancel(prevId)

        val alertNotification = NotificationCompat.Builder(this, CHANNEL_BAND_ALERT_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
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
            .setContentTitle(snap.buildBandHudTitle())
            .setContentText(snap.buildBandHudSubtitle())
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
                description = "Menjaga koneksi GPS & MediaSession HUD ke Huawei Band 6"
                setShowBadge(false)
            }

            val wristAlertChannel = NotificationChannel(
                CHANNEL_BAND_ALERT_ID,
                "Notifikasi Belokan & Speed ke Huawei Band 6",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Meneruskan instruksi arah & peringatan kecepatan ke layar Huawei Band 6"
                enableVibration(true)
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
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
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
