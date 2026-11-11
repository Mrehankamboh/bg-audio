package com.officework.bgaudio.update

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.officework.bgaudio.R
import com.officework.bgaudio.data.Prefs
import com.officework.bgaudio.ui.MainActivity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object AppUpdate {
    const val EXTRA_INSTALL = "install_update"
    private const val TAG = "BgAudio"
    private const val LATEST = "https://api.github.com/repos/Mrehankamboh/bg-audio/releases/latest"
    private const val CHANNEL = "updates"
    private const val NOTIF_ID = 42
    private const val CHECK_GAP_MS = 30 * 60 * 1000L

    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var running = false
    private var askedSources = false
    private var offeredInstall = false

    fun check(context: Context) {
        val app = context.applicationContext
        if (running) return
        running = true
        executor.execute {
            try {
                runCheck(app)
            } catch (e: Exception) {
                Log.d(TAG, "update check failed: ${e.message}")
            } finally {
                running = false
            }
        }
    }

    fun offerInstall(activity: Activity, force: Boolean) {
        val next = installIntent(activity) ?: return
        val sources = next.action == Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
        if (sources) {
            if (!force && askedSources) return
            askedSources = true
        } else {
            if (!force && offeredInstall) return
            offeredInstall = true
        }
        try {
            activity.startActivity(next)
        } catch (e: RuntimeException) {
            Log.d(TAG, "update install failed: ${e.message}")
        }
    }

    private fun runCheck(context: Context) {
        val prefs = Prefs(context)
        val now = System.currentTimeMillis()
        val cached = updateFile(context)
        if (cached.isFile && now - prefs.lastUpdateCheck < CHECK_GAP_MS) {
            if (pendingVersion(context, cached) > localVersion(context)) notifyReady(context)
            return
        }
        val json = httpGet(LATEST)
        val remote = UpdatePolicy.versionCode(UpdatePolicy.tagName(json)) ?: return
        if (remote <= localVersion(context)) {
            prefs.lastUpdateCheck = now
            if (cached.isFile) cached.delete()
            cancelReady(context)
            return
        }
        val url = UpdatePolicy.apkUrl(json) ?: return
        if (!UpdatePolicy.allowedReleaseUrl(url)) return
        cached.parentFile?.mkdirs()
        download(url, cached)
        prefs.lastUpdateCheck = now
        notifyReady(context)
    }

    private fun installIntent(context: Context): Intent? {
        val file = updateFile(context)
        if (pendingVersion(context, file) <= localVersion(context)) return null
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            return Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun pendingVersion(context: Context, file: File): Long {
        if (!file.isFile) return 0L
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return 0L
        return versionCode(info)
    }

    private fun localVersion(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return versionCode(info)
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    private fun updateFile(context: Context): File = File(context.cacheDir, "updates/bg-audio.apk")

    private fun httpGet(url: String): String {
        val conn = open(url)
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        return read(conn)
    }

    private fun download(url: String, dest: File) {
        val conn = open(url)
        conn.setRequestProperty("Accept", "application/octet-stream")
        conn.connect()
        val host = conn.url.host.orEmpty()
        if (!UpdatePolicy.allowedDownloadHost(host)) {
            conn.disconnect()
            throw IllegalStateException("blocked host")
        }
        if (conn.responseCode !in 200..299) {
            val code = conn.responseCode
            conn.disconnect()
            throw IllegalStateException("http $code")
        }
        val tmp = File(dest.parentFile, dest.name + ".part")
        conn.inputStream.use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
        conn.disconnect()
        if (dest.exists() && !dest.delete()) {
            tmp.delete()
            throw IllegalStateException("could not replace update")
        }
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 20000
        conn.readTimeout = 60000
        conn.setRequestProperty("User-Agent", "BgAudio")
        return conn
    }

    private fun read(conn: HttpURLConnection): String {
        conn.connect()
        val host = conn.url.host.orEmpty()
        if (host != "api.github.com") {
            conn.disconnect()
            throw IllegalStateException("blocked host")
        }
        if (conn.responseCode !in 200..299) {
            val code = conn.responseCode
            conn.disconnect()
            throw IllegalStateException("http $code")
        }
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return text
    }

    private fun notifyReady(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            val channel = NotificationChannel(CHANNEL, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            context,
            3,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_INSTALL, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(context.getString(R.string.update_title))
            .setContentText(context.getString(R.string.update_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIF_ID, notification)
    }

    private fun cancelReady(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }
}
