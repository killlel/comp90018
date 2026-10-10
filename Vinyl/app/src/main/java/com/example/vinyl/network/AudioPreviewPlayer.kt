package com.example.vinyl.network

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class AudioPreviewController {
    private var player: MediaPlayer? = null
    private var prepared = false

    var isPlaying by mutableStateOf(false)
    var currentUrl by mutableStateOf<String?>(null)

    fun toggle(url: String?) {
        if (url == null) return

        // Same track: pause or resume, but only once the player is ready.
        if (currentUrl == url && player != null) {
            if (!prepared) return   // still loading; the prepared listener will start it
            if (isPlaying) {
                runCatching { player?.pause() }
                isPlaying = false
            } else {
                runCatching { player?.start() }
                    .onSuccess { isPlaying = true }
                    .onFailure { release() }
            }
            return
        }

        // Different track (or nothing loaded yet): start fresh.
        release()
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

    fun release() {
        runCatching { player?.release() }
        player = null
        prepared = false
        isPlaying = false
        currentUrl = null
    }
}

@Composable
fun rememberAudioPreviewController(): AudioPreviewController {
    val controller = remember { AudioPreviewController() }
    DisposableEffect(Unit) { onDispose { controller.release() } }
    return controller
}