package com.example.vinyl.ui.home

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The song on the Home turntable: enough to draw the card and the label, and to play it. */
data class NowPlaying(
    /** The record's id, for its sleeve colour. Null only if the card didn't carry one. */
    val submissionId: String?,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    /** The 30-second iTunes preview. Null for tracks without one, which play silently. */
    val previewUrl: String?,
    /**
     * When playback began, on the [SystemClock.elapsedRealtime] clock. Set by
     * [PlaybackViewModel.play]. Lets a turntable drawn mid-song — say after switching tabs — start
     * with the needle already down instead of replaying the drop.
     */
    val startedAtMillis: Long = 0L,
)

/**
 * Where the song is, for the progress bar: when the sound started, on the
 * `SystemClock.elapsedRealtime()` clock, and how long it runs. The bar works out the rest each
 * frame, so the player never has to publish a position.
 */
data class PlaybackClock(val startedAtMillis: Long, val durationMillis: Long)

/**
 * What's playing on the Home turntable. Activity-scoped, so the card that starts a song (in the
 * receive flow or the Collection) and the Home screen that shows it share one instance.
 *
 * A song with a preview plays it and stops when it ends. One without a preview — every seeded
 * demo record — still "plays" for [SILENT_PLAY_MILLIS], so the turntable and the card behave the
 * same either way. [stop] ends either early.
 */
class PlaybackViewModel : ViewModel() {

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    /** Null until sound (or the silent stand-in) actually starts, and again once stopped. */
    private val _clock = MutableStateFlow<PlaybackClock?>(null)
    val clock: StateFlow<PlaybackClock?> = _clock.asStateFlow()

    private var player: MediaPlayer? = null
    private var session: Job? = null

    fun play(track: NowPlaying) {
        stop()
        _nowPlaying.value = track.copy(startedAtMillis = SystemClock.elapsedRealtime())
        session = viewModelScope.launch {
            // Sound starts as the needle lands, not while the arm is still swinging over.
            delay(NEEDLE_LANDS_AFTER_MILLIS)
            val url = track.previewUrl
            if (url != null) startAudio(url) else playSilently()
        }
    }

    fun stop() {
        session?.cancel()
        session = null
        releasePlayer()
        _clock.value = null
        _nowPlaying.value = null
    }

    private fun startAudio(url: String) {
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        mediaPlayer.setOnPreparedListener {
            it.start()
            // The clock starts with the sound, not with the tap, so buffering doesn't eat into
            // the bar. A stream that won't say how long it is is treated as a standard preview.
            val duration = it.duration.toLong().takeIf { d -> d > 0 } ?: SILENT_PLAY_MILLIS
            _clock.value = PlaybackClock(SystemClock.elapsedRealtime(), duration)
        }
        // Listeners run on the main thread, where this was created, so they can touch state.
        mediaPlayer.setOnCompletionListener { stop() }
        mediaPlayer.setOnErrorListener { _, what, extra ->
            Log.w(TAG, "preview failed ($what, $extra), playing on silently")
            playOnSilently()
            true
        }
        runCatching {
            mediaPlayer.setDataSource(url)
            mediaPlayer.prepareAsync()
        }.onFailure {
            Log.w(TAG, "couldn't load preview, playing on silently", it)
            playOnSilently()
        }
    }

    /**
     * A preview that won't load shouldn't cut the record off mid-spin: it plays on silently for
     * whatever is left of a preview's length.
     */
    private fun playOnSilently() {
        releasePlayer()
        val played = _clock.value?.let { SystemClock.elapsedRealtime() - it.startedAtMillis } ?: 0L
        val remaining = (SILENT_PLAY_MILLIS - played).coerceAtLeast(0L)
        _clock.value = PlaybackClock(SystemClock.elapsedRealtime() - played, SILENT_PLAY_MILLIS)
        session = viewModelScope.launch {
            delay(remaining)
            stop()
        }
    }

    private suspend fun playSilently() {
        _clock.value = PlaybackClock(SystemClock.elapsedRealtime(), SILENT_PLAY_MILLIS)
        delay(SILENT_PLAY_MILLIS)
        stop()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun onCleared() {
        releasePlayer()
    }

    private companion object {
        const val TAG = "Playback"

        /** As long as an iTunes preview, so silent and audible records last the same. */
        const val SILENT_PLAY_MILLIS = 30_000L
    }
}
