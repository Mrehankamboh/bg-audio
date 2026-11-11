package com.officework.bgaudio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.officework.bgaudio.R
import com.officework.bgaudio.data.Prefs
import com.officework.bgaudio.keep.KeepCoordinator
import com.officework.bgaudio.ui.MainActivity
import com.officework.bgaudio.update.AppUpdate

class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            val channel = NotificationChannel(
                CHANNEL,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setSound(null, null)
            channel.enableVibration(false)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stopping = !Prefs(this).enabled || intent?.action == ACTION_STOP
        if (!enterForeground()) {
            Prefs(this).enabled = false
            KeepCoordinator.cancel()
            KeepCoordinator.publish(this)
            stopSelf()
            return START_NOT_STICKY
        }
        if (stopping) {
            Prefs(this).enabled = false
            KeepCoordinator.cancel()
            stopInForeground()
            stopSelf()
            return START_NOT_STICKY
        }
        AppUpdate.check(this)
        return START_STICKY
    }

    private fun enterForeground(): Boolean {
        return try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= 29) {
                val type = if (Build.VERSION.SDK_INT >= 34) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
                ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
            } else {
                startForeground(NOTIF_ID, notification)
            }
            true
        } catch (e: RuntimeException) {
            Prefs(this).lastPlayResult = "service start failed: ${e.message}"
            false
        }
    }

    private fun stopInForeground() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: RuntimeException) {
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, KeepAliveService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.stop), stop)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.officework.bgaudio.STOP"
        private const val CHANNEL = "keep_alive"
        private const val NOTIF_ID = 41

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
