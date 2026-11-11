package com.officework.bgaudio.service

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.officework.bgaudio.media.KeepPolicy

class SessionKeepService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        requestRebind(ComponentName(this, SessionKeepService::class.java))
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = Unit

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    fun controllersFor(packageName: String): List<MediaController> {
        val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(this, SessionKeepService::class.java)
        return manager.getActiveSessions(component).filter { it.packageName == packageName }
    }

    fun snapsFor(packageName: String): List<KeepPolicy.Snap> {
        return controllersFor(packageName).map { controller ->
            val state = controller.playbackState
            KeepPolicy.Snap(state?.state, state?.actions ?: 0L)
        }
    }

    companion object {
        @Volatile
        var instance: SessionKeepService? = null
    }
}
