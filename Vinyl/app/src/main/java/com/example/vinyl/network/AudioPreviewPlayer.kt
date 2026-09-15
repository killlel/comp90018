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
    var isPlaying by mutableStateOf(false)
    var currentUrl by mutableStateOf<String?>(null)

    fun toggle(url: String?) {
        if (url == null) return
        if (currentUrl == url && isPlaying) {
            player?.pause()
            isPlaying = false
            return
        }
        if (currentUrl == url && player != null) {
            player?.start()
            isPlaying = true
            return
        }
        release()
        currentUrl = url
        player = MediaPlayer().apply {
            setDataSource(url)
            setOnPreparedListener {
                it.start()
                this@AudioPreviewController.isPlaying = true   // qualified, avoids MediaPlayer's own isPlaying
            }
            setOnCompletionListener {
                this@AudioPreviewController.isPlaying = false  // same fix here
            }
            prepareAsync()
        }
    }

    fun release() {
        player?.release()
        player = null
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