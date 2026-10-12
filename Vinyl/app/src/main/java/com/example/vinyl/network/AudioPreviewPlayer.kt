package com.example.vinyl.network

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Plays a 30 second song preview from a URL with a [MediaPlayer].
 *
 * [isPlaying] and [currentUrl] are Compose state, so a screen that reads them redraws when the
 * preview starts, pauses or stops. Before it starts, the controller claims [AudioPlaybackArbiter]
 * so only one audio source in the app plays at a time. If another source starts, this one pauses
 * (or drops its load if it was not ready yet).
 *
 * Create one with [rememberAudioPreviewController], which also releases it when the screen leaves.
 */
class AudioPreviewController {
    private var player: MediaPlayer? = null
    private var prepared = false

    var isPlaying by mutableStateOf(false)
    var currentUrl by mutableStateOf<String?>(null)

    /**
     * Plays [url], or pauses and resumes it if it is already the current preview. A new URL
     * replaces whatever was loaded. Does nothing for a null URL (a track with no preview).
     */
    fun toggle(url: String?) {
        if (url == null) return

        // Same track: pause or resume, but only once the player is ready.
        if (currentUrl == url && player != null) {
            if (!prepared) return   // still loading; the prepared listener will start it
            if (isPlaying) {
                pause()
            } else {
                AudioPlaybackArbiter.claim(this, ::yieldToOther)
                runCatching { player?.start() }
                    .onSuccess { isPlaying = true }
                    .onFailure { release() }
            }
            return
        }

        // Different track (or nothing loaded yet): start fresh.
        release()
        AudioPlaybackArbiter.claim(this, ::yieldToOther)
        currentUrl = url
        prepared = false
        player = MediaPlayer().apply {
            setOnPreparedListener {
                prepared = true
                runCatching { it.start() }
                    .onSuccess { this@AudioPreviewController.isPlaying = true }
                    .onFailure { this@AudioPreviewController.release() }
            }
            setOnCompletionListener {
                // Rewind so a second tap replays from the start instead of doing nothing.
                runCatching { it.seekTo(0) }
                this@AudioPreviewController.isPlaying = false
            }
            setOnErrorListener { _, _, _ ->
                this@AudioPreviewController.release()
                true
            }
            runCatching {
                setDataSource(url)
                prepareAsync()
            }.onFailure { this@AudioPreviewController.release() }
        }
    }

    /** Pauses the preview where it is. Does nothing if it is not playing. */
    fun pause() {
        if (!isPlaying) return
        runCatching { player?.pause() }
        isPlaying = false
    }

    /** Another player has started: hold where we are, or drop the load if not ready yet. */
    private fun yieldToOther() {
        if (prepared) pause() else release()
    }

    /**
     * Frees the [MediaPlayer], clears the state and gives up the claim on [AudioPlaybackArbiter].
     * Safe to call at any time, including when nothing is loaded.
     */
    fun release() {
        runCatching { player?.release() }
        player = null
        prepared = false
        isPlaying = false
        currentUrl = null
        AudioPlaybackArbiter.release(this)
    }
}

/** Remembers one [AudioPreviewController] for the composition and releases it on dispose. */
@Composable
fun rememberAudioPreviewController(): AudioPreviewController {
    val controller = remember { AudioPreviewController() }
    DisposableEffect(Unit) { onDispose { controller.release() } }
    return controller
}