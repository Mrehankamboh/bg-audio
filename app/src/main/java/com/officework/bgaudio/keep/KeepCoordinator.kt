package com.officework.bgaudio.keep

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.inputmethod.InputMethodManager
import com.officework.bgaudio.data.Prefs
import com.officework.bgaudio.media.KeepPolicy
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

    fun cancel() {
        generation++
    }

    fun onForegroundChanged(context: Context, packageName: String) {
        val app = context.applicationContext
        if (packageName in ignored || isIme(app, packageName)) return

        val prefs = Prefs(app)
        val prev = prefs.lastForeground
        if (packageName == prev) return
        val leftAllowed = prefs.enabled && prev.isNotBlank() && prefs.isAllowed(prev)
        prefs.lastForeground = packageName
        if (leftAllowed || prefs.warnPackage == packageName) prefs.warnPackage = ""
        publish(app)
        if (leftAllowed) schedule(app, prev)
    }

    fun publish(context: Context) {
        val app = context.applicationContext
        app.sendBroadcast(Intent(ACTION_STATUS).setPackage(app.packageName))
    }

    private fun schedule(context: Context, pkg: String) {
        val gen = ++generation
        val delays = longArrayOf(0L, 400L, 1000L, 1800L, 3200L, 4500L)
        delays.forEachIndexed { attempt, delay ->
            handler.postDelayed({
                if (gen != generation) return@postDelayed
                attemptKeep(context, pkg, attempt, gen)
            }, delay)
        }
    }

    private fun attemptKeep(context: Context, pkg: String, attempt: Int, gen: Int) {
        if (gen != generation) return
        val prefs = Prefs(context)
        if (!prefs.enabled || !prefs.isAllowed(pkg)) return
        if (prefs.lastForeground == pkg) return

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
                    val paused = state.state == KeepPolicy.STATE_PAUSED
                    val canPlay = state.actions and KeepPolicy.ACTION_PLAY != 0L
                    if (!paused || !canPlay) continue
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
