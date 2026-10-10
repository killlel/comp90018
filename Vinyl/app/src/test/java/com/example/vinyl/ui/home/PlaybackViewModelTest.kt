package com.example.vinyl.ui.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var playback: PlaybackViewModel
    private val track = NowPlaying("record", "Song", "Artist", null, null)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        playback = PlaybackViewModel()
    }

    @After
    fun tearDown() {
        playback.stop()
        Dispatchers.resetMain()
    }

    @Test
    fun `pause works before preview preparation starts`() = runTest(dispatcher) {
        playback.play(track.copy(previewUrl = "https://example.com/preview.m4a"))
        assertTrue(playback.clock.value!!.isLoading)
        playback.togglePause()
        assertTrue(playback.clock.value!!.isPaused)
        playback.togglePause()
        assertFalse(playback.clock.value!!.isPaused)
        playback.stop()
    }

    @Test
    fun `pausing during needle drop remains paused once ready`() = runTest(dispatcher) {
        playback.play(track)
        playback.togglePause()
        runCurrent()
        advanceTimeBy(NEEDLE_LANDS_AFTER_MILLIS)
        runCurrent()
        assertFalse(playback.clock.value!!.isLoading)
        assertTrue(playback.clock.value!!.isPaused)
    }

    @Test
    fun `stop during loading prevents a delayed start`() = runTest(dispatcher) {
        playback.play(track)
        runCurrent()
        playback.stop()
        advanceTimeBy(NEEDLE_LANDS_AFTER_MILLIS + 1L)
        runCurrent()
        assertNull(playback.clock.value)
        assertNull(playback.nowPlaying.value)
    }
}
