package com.band6.gps.service

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.band6.gps.model.GpsNavRepository

class MapsNotificationListener : NotificationListenerService() {

    companion object {
        private const val GOOGLE_MAPS_PKG = "com.google.android.apps.maps"
        private const val WAZE_PKG = "com.waze"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg != GOOGLE_MAPS_PKG && pkg != WAZE_PKG) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()

        if (title.isEmpty() && text.isEmpty()) return

        // Pada Google Maps Navigation:
        // Title biasanya berisi jarak ke belokan (misal: "150 m" atau "Belok kiri")
        // Text biasanya berisi nama jalan / arah (misal: "Menuju Jl. Jend. Sudirman")
        // SubText biasanya berisi estimasi tiba / sisa jarak (misal: "14.30 • 3,5 km • 8 mnt")
        val combinedText = "$title $text".lowercase()
        val arrow = detectDirectionArrow(combinedText)

        val distancePart: String
        val streetPart: String

        if (title.matches(Regex("^[0-9.,]+\\s*(m|km|ft|mi).*$", RegexOption.IGNORE_CASE))) {
            distancePart = title
            streetPart = text.ifEmpty { "Ikuti jalur utama" }
        } else {
            distancePart = extractDistanceToken(combinedText) ?: "Navigasi"
            streetPart = if (text.isNotEmpty()) "$title - $text" else title
        }

        val previousState = GpsNavRepository.state
        val maneuverChanged = previousState.nextStreetName != streetPart ||
            previousState.directionArrow != arrow ||
            shouldAlertDistanceMilestone(previousState.distanceToTurn, distancePart)

        GpsNavRepository.update { curr ->
            curr.copy(
                isMapsActive = true,
                directionArrow = arrow,
                distanceToTurn = distancePart,
                nextStreetName = streetPart.take(60),
                etaSummary = subText.ifEmpty { "Rute Aktif" }
            )
        }

        // Minta ForegroundService menyegarkan HUD & mengirim getaran notifikasi jika belokan berubah
        val intent = Intent(this, BandGpsForegroundService::class.java).apply {
            action = BandGpsForegroundService.ACTION_MAPS_UPDATED
            putExtra(BandGpsForegroundService.EXTRA_TRIGGER_BAND_ALERT, maneuverChanged)
        }
        try {
            startService(intent)
        } catch (_: Exception) {
            // Abaikan jika service belum dinyalakan pengguna
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == GOOGLE_MAPS_PKG || pkg == WAZE_PKG) {
            GpsNavRepository.update { curr ->
                curr.copy(
                    isMapsActive = false,
                    directionArrow = "⬆️",
                    distanceToTurn = "-- m",
                    nextStreetName = "Navigasi selesai / tidak aktif",
                    etaSummary = "--:--"
                )
            }
        }
    }

    private fun detectDirectionArrow(raw: String): String {
        return when {
            raw.contains("putar balik") || raw.contains("u-turn") -> "↩️"
            raw.contains("agak ke kiri") || raw.contains("serong kiri") || raw.contains("slight left") || raw.contains("keep left") -> "↖️"
            raw.contains("agak ke kanan") || raw.contains("serong kanan") || raw.contains("slight right") || raw.contains("keep right") -> "↗️"
            raw.contains("kiri") || raw.contains("left") -> "⬅️"
            raw.contains("kanan") || raw.contains("right") -> "➡️"
            raw.contains("bundaran") || raw.contains("roundabout") -> "🔄"
            raw.contains("tujuan") || raw.contains("destination") || raw.contains("tiba") -> "🏁"
            else -> "⬆️"
        }
    }

    private fun extractDistanceToken(raw: String): String? {
        val match = Regex("(\\d+[.,]?\\d*\\s*(?:m|km))", RegexOption.IGNORE_CASE).find(raw)
        return match?.value
    }

    private fun shouldAlertDistanceMilestone(oldDist: String, newDist: String): Boolean {
        if (oldDist == newDist) return false
        val meters = parseMeters(newDist) ?: return false
        // Kirim getaran peringatan ke Huawei Band 6 saat jarak tepat menyentuh ambang kritis (200m, 100m, 50m, 20m)
        return meters in listOf(200, 150, 100, 50, 30, 15)
    }

    private fun parseMeters(distStr: String): Int? {
        val clean = distStr.lowercase().trim()
        if (clean.endsWith("km")) return null
        val num = clean.replace("m", "").trim().toIntOrNull()
        return num
    }
}
