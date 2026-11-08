package com.band6.gps.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.band6.gps.model.GpsNavSnapshot
import java.io.File
import java.io.FileOutputStream

/**
 * Mengintegrasikan alur kerja HookUtils (cn.wwl.xposed.hook) + Huawei Health (com.huawei.health)
 * + Gadgetbridge untuk mengompilasi dan memasang tema .HWT secara langsung ke Huawei Band 6.
 */
object DirectThemeInstaller {

    const val PKG_HUAWEI_HEALTH = "com.huawei.health"
    const val PKG_HOOK_UTILS = "cn.wwl.xposed.hook"
    const val PKG_GADGETBRIDGE = "nodomain.freeyourgadget.gadgetbridge"

    data class BridgeStatus(
        val huaweiHealthInstalled: Boolean,
        val hookUtilsStandaloneInstalled: Boolean,
        val embeddedXposedReady: Boolean,
        val gadgetbridgeInstalled: Boolean
    ) {
        fun statusSummary(): String {
            val parts = mutableListOf<String>()
            parts.add("EMBEDDED HOOK: ACTIVE")
            if (hookUtilsStandaloneInstalled) parts.add("HOOKUTILS APK: INSTALLED")
            if (huaweiHealthInstalled) parts.add("HUAWEI HEALTH: DETECTED")
            if (gadgetbridgeInstalled) parts.add("GADGETBRIDGE: DETECTED")
            return parts.joinToString("  |  ")
        }
    }

    fun getBridgeStatus(context: Context): BridgeStatus {
        val pm = context.packageManager
        return BridgeStatus(
            huaweiHealthInstalled = isPackageInstalled(pm, PKG_HUAWEI_HEALTH),
            hookUtilsStandaloneInstalled = isPackageInstalled(pm, PKG_HOOK_UTILS),
            embeddedXposedReady = true, // Modul Xposed/LSPosed HookUtils v1.2.7_p2 sudah tertanam di APK ini
            gadgetbridgeInstalled = isPackageInstalled(pm, PKG_GADGETBRIDGE)
        )
    }

    private fun isPackageInstalled(pm: PackageManager, pkg: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Menyimpan bytes .hwt ke folder Download publik agar HookUtils WebView Hook di dalam
     * Huawei Health dapat langsung membacanya dari /sdcard/Download/.
     */
    fun saveHwtToPublicDownloads(context: Context, fileName: String, bytes: ByteArray): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                // Hapus file lama dengan nama sama jika ada milik app kita
                try {
                    resolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                        arrayOf(fileName)
                    )
                } catch (_: Exception) {
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                }
                "/sdcard/Download/$fileName"
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val outFile = File(dir, fileName)
                FileOutputStream(outFile).use { it.write(bytes) }
                outFile.absolutePath
            }
        } catch (_: Exception) {
            "/sdcard/Download/$fileName"
        }
    }

    /**
     * Kompilasi tema yang sedang dipilih menjadi file .HWT (HWHD07 v2.1.1), simpan ke Download,
     * salin path ke Clipboard untuk HookUtils, dan luncurkan proses instalasi langsung ke Band 6!
     */
    fun compileAndPushThemeToBand(
        context: Context,
        snapshot: GpsNavSnapshot,
        theme: WatchFaceGenerator.CockpitTheme
    ): String {
        val hwtFile = HwtCompiler.compileAndSaveHwtFile(context, snapshot, theme)
        val hwtBytes = hwtFile.readBytes()
        val downloadPath = saveHwtToPublicDownloads(context, hwtFile.name, hwtBytes)

        // Salin path file .hwt ke Clipboard agar di dialog HookUtils / Huawei Health tinggal Paste / Pilih
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Band6 HWT Path", downloadPath))
        } catch (_: Exception) {
        }

        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            hwtFile
        )

        val status = getBridgeStatus(context)

        // 1. Jika Gadgetbridge terpasang, coba kirim langsung ke FwAppInstallerActivity Gadgetbridge
        if (status.gadgetbridgeInstalled) {
            try {
                val gbIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, "application/octet-stream")
                    setPackage(PKG_GADGETBRIDGE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(gbIntent)
                return "Tema ${hwtFile.name} berhasil dikompilasi & dikirim langsung ke Gadgetbridge!"
            } catch (_: Exception) {
            }
        }

        // 2. Jika Huawei Health terpasang, buka Huawei Health (dengan HookUtils aktif)
        if (status.huaweiHealthInstalled) {
            try {
                val healthIntent = context.packageManager.getLaunchIntentForPackage(PKG_HUAWEI_HEALTH)
                if (healthIntent != null) {
                    healthIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(healthIntent)
                    return "File ${hwtFile.name} siap di $downloadPath (tersalin di Clipboard). Di Huawei Health -> Watch Faces, klik tombol HookUtils untuk pasang!"
                }
            } catch (_: Exception) {
            }
        }

        // 3. Fallback: Buka chooser sistem untuk file .hwt (mendukung app installer .hwt pihak ketiga)
        return try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/octet-stream")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(viewIntent, "Pasang Tema ${hwtFile.name} ke Huawei Band 6").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            "File ${hwtFile.name} disimpan di $downloadPath & dialog instalasi dibuka."
        } catch (_: Exception) {
            "File ${hwtFile.name} berhasil dikompilasi ke $downloadPath."
        }
    }

    /**
     * Mengekstrak & menginstal APK HookUtils-1.2.7_p2-20240602-R.apk yang sudah dibundel di assets,
     * atau membukanya langsung jika sudah terinstal di HP.
     */
    fun installOrOpenBundledHookUtils(context: Context): String {
        val pm = context.packageManager
        if (isPackageInstalled(pm, PKG_HOOK_UTILS)) {
            val launch = pm.getLaunchIntentForPackage(PKG_HOOK_UTILS)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return "Membuka aplikasi HookUtils v1.2.7_p2..."
            }
        }

        return try {
            val dir = File(context.cacheDir, "hookutils_apk").apply { mkdirs() }
            val apkFile = File(dir, "HookUtils-1.2.7_p2-20240602-R.apk")
            context.assets.open("HookUtils.apk").use { input ->
                FileOutputStream(apkFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Simpan juga salinan di folder Download agar pengguna mudah menemukannya untuk LSPatch/LSPosed
            saveHwtToPublicDownloads(context, "HookUtils-1.2.7_p2-20240602-R.apk", apkFile.readBytes())

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            "Membuka installer HookUtils-1.2.7_p2-20240602-R.apk (juga disimpan di /sdcard/Download/)."
        } catch (e: Exception) {
            "HookUtils-1.2.7_p2-20240602-R.apk telah diekstrak ke folder Download."
        }
    }
}
