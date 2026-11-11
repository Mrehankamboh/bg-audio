package com.officework.bgaudio.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.officework.bgaudio.data.Prefs
import com.officework.bgaudio.keep.KeepCoordinator

class PackageWatchService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        if (Prefs(this).enabled) {
            try {
                KeepAliveService.start(this)
            } catch (e: RuntimeException) {
                Prefs(this).lastPlayResult = "service start failed: ${e.message}"
                KeepCoordinator.publish(this)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()?.trim().orEmpty()
        if (pkg.isEmpty()) return
        KeepCoordinator.onForegroundChanged(this, pkg)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: PackageWatchService? = null
    }
}
