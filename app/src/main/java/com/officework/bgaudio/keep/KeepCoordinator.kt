package com.officework.bgaudio.keep

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.inputmethod.InputMethodManager
import com.officework.bgaudio.data.Prefs
import com.officework.bgaudio.media.KeepPolicy
import com.officework.bgaudio.overlay.PinBubble
import com.officework.bgaudio.service.PackageWatchService
import com.officework.bgaudio.service.SessionKeepService

object KeepCoordinator {
    const val ACTION_STATUS = "com.officework.bgaudio.STATUS"
    private const val TAG = "BgAudio"

    private val ignored = setOf(
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller"
    )

    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var watching: String? = null

    fun cancel() {
        generation++
    }

    fun onForegroundChanged(context: Context, packageName: String) {
        val app = context.applicationContext
        if (packageName in ignored || isIme(app, packageName)) return

        val prefs = Prefs(app)
        val prev = prefs.lastForeground
        if (packageName == prev) {
            PinBubble.sync(app)
            return
        }
        val leftAllowed = prefs.enabled && prev.isNotBlank() && prefs.isAllowed(prev)
        prefs.lastForeground = packageName
        if (leftAllowed || prefs.warnPackage == packageName) prefs.warnPackage = ""
        publish(app)
        if (!leftAllowed) return
        if (watching != null && prev != watching && !hasSession(app, prev)) return
        watching = prev
        schedule(app, prev)
    }

    fun pin(context: Context) {
        val app = context.applicationContext
        val service = PackageWatchService.instance
        val result = when {
            service == null -> "accessibility is off"
            service.toggleSplitScreen() -> "split-screen toggled"
            else -> "split-screen refused"
        }
        record(app, Prefs(app).lastSessionState.ifBlank { "—" }, result, warn = null)
    }

    fun publish(context: Context) {
        val app = context.applicationContext
        app.sendBroadcast(Intent(ACTION_STATUS).setPackage(app.packageName))
        PinBubble.sync(app)
    }

    private fun hasSession(context: Context, pkg: String): Boolean {
        val listener = SessionKeepService.instance ?: return false
        return try {
            listener.snapsFor(pkg).isNotEmpty()
        } catch (_: SecurityException) {
            false
        }
    }

    private fun schedule(context: Context, pkg: String) {
        val gen = ++generation
        for (attempt in 0..KeepPolicy.LAST_ATTEMPT) {
            val delay = if (attempt == 0) 0L else attempt * 1000L
            handler.postDelayed({
                if (gen != generation) return@postDelayed
                attemptKeep(context, pkg, attempt, gen)
            }, delay)
        }
    }

    private fun attemptKeep(context: Context, pkg: String, attempt: Int, gen: Int) {
        if (gen != generation) return
        val prefs = Prefs(context)
        if (!prefs.enabled || !prefs.isAllowed(pkg)) {
            if (watching == pkg) watching = null
            return
        }
        if (prefs.lastForeground == pkg) {
            if (watching == pkg) watching = null
            return
        }

        val listener = SessionKeepService.instance
        if (listener == null) {
            val last = attempt >= KeepPolicy.LAST_ATTEMPT
            record(
                context,
                session = "listener off",
                result = if (last) "notification listener is off" else "waiting for listener",
                warn = null
            )
            return
        }

        val snaps = try {
            listener.snapsFor(pkg)
        } catch (_: SecurityException) {
            record(context, "listener off", "notification listener is off", warn = null)
            return
        }
        val presence = KeepPolicy.presence(snaps)
        val label = KeepPolicy.stateLabel(snaps)
        val decision = KeepPolicy.decide(presence, label, pkg, attempt)
        var playError: String? = null
        if (decision.sendPlay) {
            try {
                for (controller in listener.controllersFor(pkg)) {
                    val state = controller.playbackState ?: continue
                    val playing = state.state == KeepPolicy.STATE_PLAYING ||
                        state.state == KeepPolicy.STATE_BUFFERING
                    val canPlay = state.actions and KeepPolicy.ACTION_PLAY != 0L
                    if (playing || !canPlay) continue
                    try {
                        controller.transportControls.play()
                    } catch (e: RuntimeException) {
                        playError = e.message
                    }
                }
            } catch (_: SecurityException) {
                record(context, label, "notification listener is off", warn = null)
                return
            }
        }
        val message = if (playError != null) "$pkg play() failed: $playError" else decision.message
        val warn = when {
            decision.warn -> pkg
            presence == KeepPolicy.Presence.PLAYING -> ""
            presence == KeepPolicy.Presence.GONE && attempt >= KeepPolicy.LAST_ATTEMPT -> ""
            else -> null
        }
        record(context, label, message, warn)
        if (attempt >= KeepPolicy.LAST_ATTEMPT && watching == pkg) watching = null
    }

    private fun record(context: Context, session: String, result: String, warn: String?) {
        Prefs(context).writeStatus(session, result, warn)
        Log.d(TAG, "session=$session result=$result")
        publish(context)
    }

    private fun isIme(context: Context, pkg: String): Boolean {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return false
        return imm.enabledInputMethodList.any { it.packageName == pkg }
    }
}
