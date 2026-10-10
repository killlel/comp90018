package com.example.vinyl.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackClockTest {

    private val clock = PlaybackClock(startedAtMillis = 1_000L, durationMillis = 30_000L)

    @Test
    fun `loading accepts pause and resume without advancing progress`() {
        val loading = clock.copy(isLoading = true)
        val paused = loading.pausedAt(2_000L)
        assertTrue(paused.isPaused)
        assertEquals(0L, paused.elapsedAt(50_000L))
        val resumed = paused.resumedAt(10_000L)
        assertFalse(resumed.isPaused)
        assertEquals(0L, resumed.elapsedAt(50_000L))
    }

    @Test
    fun `runs with the wall clock while playing`() {
        assertEquals(5_000L, clock.elapsedAt(6_000L))
    }

    @Test
    fun `never reads before the start or past the end`() {
        assertEquals(0L, clock.elapsedAt(500L))
        assertEquals(30_000L, clock.elapsedAt(60_000L))
    }

    @Test
    fun `holds still while paused`() {
        val paused = clock.pausedAt(11_000L)
        assertTrue(paused.isPaused)
        assertEquals(10_000L, paused.elapsedAt(11_000L))
        assertEquals(10_000L, paused.elapsedAt(50_000L))
    }

    @Test
    fun `resumes from where it was paused`() {
        // Paused 10 s in, for 20 s.
        val resumed = clock.pausedAt(11_000L).resumedAt(31_000L)
        assertFalse(resumed.isPaused)
        assertEquals(10_000L, resumed.elapsedAt(31_000L))
        assertEquals(15_000L, resumed.elapsedAt(36_000L))
    }

    @Test
    fun `adds up over several pauses`() {
        val twice = clock
            .pausedAt(6_000L).resumedAt(10_000L) // 5 s in, held 4 s
            .pausedAt(15_000L).resumedAt(25_000L) // 10 s in, held 10 s
        assertEquals(12_000L, twice.elapsedAt(27_000L))
    }

    @Test
    fun `pausing twice keeps the first moment, resuming a playing clock changes nothing`() {
        val paused = clock.pausedAt(11_000L)
        assertEquals(paused, paused.pausedAt(20_000L))
        assertSame(clock, clock.resumedAt(20_000L))
    }

    @Test
    fun `seeking moves the position and carries on from there`() {
        val moved = clock.seekedTo(15_000L, now = 6_000L) // was 5 s in
        assertEquals(15_000L, moved.elapsedAt(6_000L))
        assertEquals(17_000L, moved.elapsedAt(8_000L))
    }

    @Test
    fun `seeking never goes before the start or past the end`() {
        assertEquals(0L, clock.seekedTo(-5_000L, now = 3_000L).elapsedAt(3_000L))
        assertEquals(30_000L, clock.seekedTo(40_000L, now = 3_000L).elapsedAt(3_000L))
    }

    @Test
    fun `a paused song stays paused at its new position`() {
        val moved = clock.pausedAt(11_000L).seekedTo(5_000L, now = 20_000L)
        assertTrue(moved.isPaused)
        assertEquals(5_000L, moved.elapsedAt(20_000L))
        assertEquals(5_000L, moved.elapsedAt(90_000L))
        assertEquals(7_000L, moved.resumedAt(30_000L).elapsedAt(32_000L))
    }
}
