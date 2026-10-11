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
import com.example.vinyl.network.AudioPlaybackArbiter

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
 *
 * A paused song keeps the moment it was paused, so its position holds still; resuming moves the
 * start later by however long it was paused, so the same sum carries on from where it stopped.
 */
data class PlaybackClock(
    val startedAtMillis: Long,
    val durationMillis: Long,
    /** When it was paused, on the same clock. Null while it plays. */
    val pausedAtMillis: Long? = null,
    /** A pending preview accepts pause immediately but keeps progress at zero. */
    val isLoading: Boolean = false,
) {
    val isPaused: Boolean get() = pausedAtMillis != null

    /** How far into the song it is at [now], never past either end. */
    fun elapsedAt(now: Long): Long =
        if (isLoading) 0L else ((pausedAtMillis ?: now) - startedAtMillis).coerceIn(0L, durationMillis)

    fun pausedAt(now: Long): PlaybackClock = if (isPaused) this else copy(pausedAtMillis = now)

    fun resumedAt(now: Long): PlaybackClock {
        val pausedAt = pausedAtMillis ?: return this
        return copy(startedAtMillis = startedAtMillis + (now - pausedAt), pausedAtMillis = null)
    }

    /**
     * The same song moved to [positionMillis] at [now], kept between the start and the end. A
     * paused song stays paused, now holding at the new position.
     */
    fun seekedTo(positionMillis: Long, now: Long): PlaybackClock {
        val position = positionMillis.coerceIn(0L, durationMillis)
        return copy(startedAtMillis = now - position, pausedAtMillis = pausedAtMillis?.let { now })
    }
}

/**
 * What's playing on the Home turntable. Activity-scoped, so the card that starts a song (in the
 * receive flow or the Collection) and the Home screen that shows it share one instance.
 *
 * A song with a preview plays it and stops when it ends. One without a preview still "plays" for
 * [SILENT_PLAY_MILLIS], so the turntable and the card behave the same either way. [togglePause]
 * holds either where it is, [seekBy] skips either back or on, and [stop] ends either early.
 */
class PlaybackViewModel : ViewModel() {

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    /** Available from the play tap, including while the preview is preparing; null once stopped. */
    private val _clock = MutableStateFlow<PlaybackClock?>(null)
    val clock: StateFlow<PlaybackClock?> = _clock.asStateFlow()

    private var player: MediaPlayer? = null
    private var session: Job? = null

    fun play(track: NowPlaying) {
        stop()
        AudioPlaybackArbiter.claim(this, ::pauseForOtherAudio)
        val now = SystemClock.elapsedRealtime()
        _nowPlaying.value = track.copy(startedAtMillis = now)
        _clock.value = PlaybackClock(now, SILENT_PLAY_MILLIS, isLoading = true)
        session = viewModelScope.launch {
            // Sound starts as the needle lands, not while the arm is still swinging over.
            delay(NEEDLE_LANDS_AFTER_MILLIS)
            val url = track.previewUrl
            if (url != null) startAudio(url) else playOnSilently()
        }
    }

    /**
     * Pauses or resumes immediately, including while the needle drops or a preview buffers.
     * A preview prepared while paused waits for an explicit resume.
     */
    fun togglePause() {
        val clock = _clock.value ?: return
        val now = SystemClock.elapsedRealtime()
        if (clock.isPaused) AudioPlaybackArbiter.claim(this, ::pauseForOtherAudio)
        if (clock.isLoading) {
            _clock.value = if (clock.isPaused) clock.resumedAt(now) else clock.pausedAt(now)
            return
        }
        if (clock.isPaused) {
            _clock.value = clock.resumedAt(now)
            val mediaPlayer = player
            if (mediaPlayer != null) mediaPlayer.start() else finishSilentlyAfter(clock.remaining())
        } else {
            _clock.value = clock.pausedAt(now)
            // A preview pauses itself; a silent song's only moving part is its timer.
            val mediaPlayer = player
            if (mediaPlayer != null) mediaPlayer.pause() else session?.cancel()
        }
    }

    /**
     * Jumps [deltaMillis] forwards or back. Skipping past the end finishes the song, as letting it
     * play out would. Does nothing until sound has started.
     */
    fun seekBy(deltaMillis: Long) {
        val clock = _clock.value ?: return
        if (clock.isLoading) return
        val now = SystemClock.elapsedRealtime()
        val target = clock.elapsedAt(now) + deltaMillis
        if (target >= clock.durationMillis) {
            stop()
            return
        }
        val moved = clock.seekedTo(target, now)
        _clock.value = moved
        val mediaPlayer = player
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(moved.elapsedAt(now).toInt())
        } else if (!moved.isPaused) {
            // A silent song's end is a timer, so it moves with the position.
            finishSilentlyAfter(moved.remaining())
        }
    }

    fun stop() {
        session?.cancel()
        session = null
        releasePlayer()
        _clock.value = null
        _nowPlaying.value = null
        AudioPlaybackArbiter.release(this)
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
            if (player !== it) return@setOnPreparedListener
            // The clock starts with the sound, not with the tap, so buffering doesn't eat into
            // the bar. A stream that won't say how long it is is treated as a standard preview.
            val duration = it.duration.toLong().takeIf { d -> d > 0 } ?: SILENT_PLAY_MILLIS
            val now = SystemClock.elapsedRealtime()
            val paused = _clock.value?.isPaused == true
            _clock.value = PlaybackClock(now, duration, pausedAtMillis = now.takeIf { paused })
            if (!paused) it.start()
        }
        // Listeners run on the main thread, where this was created, so they can touch state.
        mediaPlayer.setOnCompletionListener { if (player === it) stop() }
        mediaPlayer.setOnErrorListener { failed, what, extra ->
            if (player !== failed) return@setOnErrorListener true
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
     * Plays on with no sound, for a song without a preview or one whose preview won't load — that
     * shouldn't cut the record off mid-spin. Runs for whatever is left of a preview's length, and
     * stays paused if it was.
     */
    private fun playOnSilently() {
        releasePlayer()
        val now = SystemClock.elapsedRealtime()
        val previous = _clock.value
        val clock = if (previous == null || previous.isLoading) {
            PlaybackClock(now, SILENT_PLAY_MILLIS, pausedAtMillis = now.takeIf { previous?.isPaused == true })
        } else {
            previous.copy(durationMillis = SILENT_PLAY_MILLIS)
        }
        _clock.value = clock
        if (!clock.isPaused) finishSilentlyAfter(clock.remaining())
    }

    private fun finishSilentlyAfter(millis: Long) {
        session?.cancel()
        session = viewModelScope.launch {
            delay(millis)
            stop()
        }
    }

    private fun PlaybackClock.remaining(): Long =
        durationMillis - elapsedAt(SystemClock.elapsedRealtime())

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    /** A card or the Write screen started playing: hold the turntable where it is. */
    private fun pauseForOtherAudio() {
        val clock = _clock.value ?: return
        if (!clock.isPaused) togglePause()
    }

    override fun onCleared() {
        releasePlayer()
        AudioPlaybackArbiter.release(this)
    }

    private companion object {
        const val TAG = "Playback"

        /** As long as an iTunes preview, so silent and audible records last the same. */
        const val SILENT_PLAY_MILLIS = 30_000L
    }
}
