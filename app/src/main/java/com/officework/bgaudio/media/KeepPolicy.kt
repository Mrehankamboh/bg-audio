package com.officework.bgaudio.media

/**
 * Decides whether to send play() after the user leaves an app.
 * State numbers match android.media.session.PlaybackState.
 * A missing session is left alone so a swiped-away app is not reopened.
 */
object KeepPolicy {
    const val LAST_ATTEMPT = 5

    const val STATE_NONE = 0
    const val STATE_STOPPED = 1
    const val STATE_PAUSED = 2
    const val STATE_PLAYING = 3
    const val STATE_BUFFERING = 6
    const val ACTION_PLAY = 4L

    enum class Presence {
        GONE,
        PLAYING,
        PAUSED_CAN_PLAY,
        PAUSED_NO_ACTION,
        OTHER
    }

    data class Snap(val state: Int?, val actions: Long)

    data class Decision(val sendPlay: Boolean, val message: String, val warn: Boolean)

    fun presence(snaps: List<Snap>): Presence {
        if (snaps.isEmpty()) return Presence.GONE
        if (snaps.any { it.state == STATE_PLAYING || it.state == STATE_BUFFERING }) {
            return Presence.PLAYING
        }
        val paused = snaps.filter { it.state == STATE_PAUSED }
        if (paused.isNotEmpty()) {
            return if (paused.any { it.actions and ACTION_PLAY != 0L }) {
                Presence.PAUSED_CAN_PLAY
            } else {
                Presence.PAUSED_NO_ACTION
            }
        }
        return Presence.OTHER
    }

    fun stateLabel(snaps: List<Snap>): String {
        if (snaps.isEmpty()) return "gone"
        return snaps.joinToString(",") { stateName(it.state) }
    }

    fun decide(presence: Presence, stateLabel: String, pkg: String, attempt: Int): Decision {
        val last = attempt >= LAST_ATTEMPT
        return when (presence) {
            Presence.PLAYING -> Decision(false, "playing: $pkg", false)
            Presence.PAUSED_CAN_PLAY ->
                if (last) Decision(false, "not supported: $pkg", true)
                else Decision(true, "play() sent: $pkg", false)
            Presence.PAUSED_NO_ACTION ->
                if (last) Decision(false, "not supported: $pkg", true)
                else Decision(false, "waiting: $pkg", false)
            Presence.GONE ->
                if (last) Decision(false, "session gone: $pkg", false)
                else Decision(false, "waiting for session: $pkg", false)
            Presence.OTHER ->
                if (last) Decision(false, "left as $stateLabel: $pkg", false)
                else Decision(false, "waiting ($stateLabel): $pkg", false)
        }
    }

    fun stateName(state: Int?): String = when (state) {
        null, STATE_NONE -> "none"
        STATE_STOPPED -> "stopped"
        STATE_PAUSED -> "paused"
        STATE_PLAYING -> "playing"
        STATE_BUFFERING -> "buffering"
        else -> "state_$state"
    }
}
