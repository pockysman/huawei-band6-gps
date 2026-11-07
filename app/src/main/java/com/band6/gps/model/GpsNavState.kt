package com.band6.gps.model

import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt

enum class BandHudPage(val title: String) {
    COMBINED_NAV_SPEED("Mode 1: Navigasi + Speed"),
    SPEEDOMETER_ODO("Mode 2: Speedometer & Jarak"),
    GPS_COORDINATES("Mode 3: Koordinat & Kompas")
}

data class GpsNavSnapshot(
    val isServiceRunning: Boolean = false,
    val speedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val bearingDeg: Float = 0f,
    val altitudeMeters: Double = 0.0,
    val accuracyMeters: Float = 0f,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val tripDistanceMeters: Double = 0.0,
    val isMapsActive: Boolean = false,
    val directionArrow: String = "⬆️",
    val distanceToTurn: String = "-- m",
    val nextStreetName: String = "Menunggu rute Google Maps...",
    val etaSummary: String = "--:--",
    val activeHudPage: BandHudPage = BandHudPage.COMBINED_NAV_SPEED,
    val pushTurnAlertsEnabled: Boolean = true,
    val speedLimitAlertKmh: Int = 80
) {
    fun compassCardinal(): String {
        val dirs = arrayOf("U", "TL", "T", "TG", "S", "BD", "B", "BL")
        val normalized = ((bearingDeg % 360f) + 360f) % 360f
        val idx = ((normalized + 22.5f) / 45f).toInt() % 8
        return dirs[idx]
    }

    fun formattedTripDistance(): String {
        return if (tripDistanceMeters < 1000.0) {
            "${tripDistanceMeters.roundToInt()} m"
        } else {
            String.format(Locale.US, "%.2f km", tripDistanceMeters / 1000.0)
        }
    }

    /**
     * Menghasilkan baris utama (Judul) untuk layar Kontrol Musik Huawei Band 6
     */
    fun buildBandHudTitle(): String {
        val spd = speedKmh.roundToInt()
        return when (activeHudPage) {
            BandHudPage.COMBINED_NAV_SPEED -> {
                if (isMapsActive) {
                    "$directionArrow $distanceToTurn • $spd km/j"
                } else {
                    "⚡ $spd km/j • 🧭 ${compassCardinal()} (${bearingDeg.roundToInt()}°)"
                }
            }
            BandHudPage.SPEEDOMETER_ODO -> {
                "⚡ $spd km/j (Maks ${maxSpeedKmh.roundToInt()})"
            }
            BandHudPage.GPS_COORDINATES -> {
                String.format(Locale.US, "📍 %.5f, %.5f", latitude, longitude)
            }
        }
    }

    /**
     * Menghasilkan baris kedua (Artis/Subjudul) untuk layar Kontrol Musik Huawei Band 6
     */
    fun buildBandHudSubtitle(): String {
        val spd = speedKmh.roundToInt()
        return when (activeHudPage) {
            BandHudPage.COMBINED_NAV_SPEED -> {
                if (isMapsActive) {
                    "$nextStreetName ($etaSummary)"
                } else {
                    "📏 ${formattedTripDistance()} | ⛰️ ${altitudeMeters.roundToInt()}mdpl"
                }
            }
            BandHudPage.SPEEDOMETER_ODO -> {
                "📏 ${formattedTripDistance()} • 🧭 ${compassCardinal()} • ⛰️ ${altitudeMeters.roundToInt()}m"
            }
            BandHudPage.GPS_COORDINATES -> {
                "🎯 ±${accuracyMeters.roundToInt()}m • ⚡ $spd km/j • 🧭 ${compassCardinal()}"
            }
        }
    }
}

object GpsNavRepository {
    @Volatile
    var state: GpsNavSnapshot = GpsNavSnapshot()
        private set

    private val listeners = CopyOnWriteArrayList<(GpsNavSnapshot) -> Unit>()

    fun update(transform: (GpsNavSnapshot) -> GpsNavSnapshot) {
        synchronized(this) {
            state = transform(state)
        }
        val current = state
        for (listener in listeners) {
            listener(current)
        }
    }

    fun addListener(listener: (GpsNavSnapshot) -> Unit) {
        listeners.add(listener)
        listener(state)
    }

    fun removeListener(listener: (GpsNavSnapshot) -> Unit) {
        listeners.remove(listener)
    }

    fun nextHudPage() {
        val pages = BandHudPage.entries
        update { curr ->
            val nextIdx = (curr.activeHudPage.ordinal + 1) % pages.size
            curr.copy(activeHudPage = pages[nextIdx])
        }
    }

    fun previousHudPage() {
        val pages = BandHudPage.entries
        update { curr ->
            val prevIdx = (curr.activeHudPage.ordinal - 1 + pages.size) % pages.size
            curr.copy(activeHudPage = pages[prevIdx])
        }
    }

    fun resetTripStats() {
        update { curr ->
            curr.copy(tripDistanceMeters = 0.0, maxSpeedKmh = 0f)
        }
    }
}
