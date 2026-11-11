package com.officework.bgaudio.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepPolicyTest {
    private val pkg = "com.example.media"

    @Test
    fun missingSessionWaitsThenStaysGone() {
        val early = decide(emptyList(), 0)
        assertFalse(early.sendPlay)
        assertFalse(early.warn)
        assertEquals("waiting for session: $pkg", early.message)

        val last = decide(emptyList(), KeepPolicy.LAST_ATTEMPT)
        assertFalse(last.sendPlay)
        assertFalse(last.warn)
        assertEquals("session gone: $pkg", last.message)
    }

    @Test
    fun playingOrBufferingDoesNotSendPlay() {
        val playing = decide(listOf(snap(KeepPolicy.STATE_PLAYING, 0)), 0)
        assertFalse(playing.sendPlay)
        assertEquals("playing: $pkg", playing.message)

        val buffering = decide(listOf(snap(KeepPolicy.STATE_BUFFERING, 0)), 2)
        assertFalse(buffering.sendPlay)
        assertEquals("playing: $pkg", buffering.message)
    }

    @Test
    fun pausedWithPlayActionSendsUntilLastAttempt() {
        val early = decide(listOf(snap(KeepPolicy.STATE_PAUSED, KeepPolicy.ACTION_PLAY)), 0)
        assertTrue(early.sendPlay)
        assertFalse(early.warn)
        assertEquals("play() sent: $pkg", early.message)

        val last = decide(listOf(snap(KeepPolicy.STATE_PAUSED, 5L)), KeepPolicy.LAST_ATTEMPT)
        assertFalse(last.sendPlay)
        assertTrue(last.warn)
        assertEquals("not supported: $pkg", last.message)
    }

    @Test
    fun pausedWithoutPlayActionWarnsOnLastAttempt() {
        val last = decide(listOf(snap(KeepPolicy.STATE_PAUSED, 1L)), KeepPolicy.LAST_ATTEMPT)
        assertFalse(last.sendPlay)
        assertTrue(last.warn)
        assertEquals("not supported: $pkg", last.message)
    }

    @Test
    fun stoppedSessionWithPlayActionSendsPlay() {
        val early = decide(listOf(snap(KeepPolicy.STATE_STOPPED, KeepPolicy.ACTION_PLAY)), 0)
        assertTrue(early.sendPlay)
        assertEquals("play() sent: $pkg", early.message)

        val last = decide(listOf(snap(KeepPolicy.STATE_STOPPED, KeepPolicy.ACTION_PLAY)), KeepPolicy.LAST_ATTEMPT)
        assertFalse(last.sendPlay)
        assertTrue(last.warn)
        assertEquals("not supported: $pkg", last.message)
    }

    @Test
    fun playingSessionWinsOverPaused() {
        val snaps = listOf(
            snap(KeepPolicy.STATE_PAUSED, KeepPolicy.ACTION_PLAY),
            snap(KeepPolicy.STATE_PLAYING, 0)
        )
        val decision = decide(snaps, 1)
        assertFalse(decision.sendPlay)
        assertEquals("playing: $pkg", decision.message)
    }

    private fun snap(state: Int, actions: Long) = KeepPolicy.Snap(state, actions)

    private fun decide(snaps: List<KeepPolicy.Snap>, attempt: Int): KeepPolicy.Decision {
        return KeepPolicy.decide(KeepPolicy.presence(snaps), KeepPolicy.stateLabel(snaps), pkg, attempt)
    }
}
