package com.example.vinyl.ui.receive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import com.example.vinyl.data.model.RecordSource
import com.example.vinyl.data.model.VinylRecord
import com.example.vinyl.ui.components.AnimatedBottomSheet
import com.example.vinyl.ui.components.rememberLastNonNull
import com.example.vinyl.ui.daily.ArrivedRecordOption
import com.example.vinyl.ui.daily.sentDateLabel

/**
 * A card from the Home shelf in receive mode, in a bottom sheet: the same music card page 4
 * shows, without the envelope. The compass opens over it and its back arrow returns here.
 *
 * [option] is null when nothing is open. It's still drawn then, so the sheet can slide out
 * showing the card it's closing.
 */
@Composable
internal fun ShelfCardSheet(
    option: ArrivedRecordOption?,
    compassOpen: Boolean,
    keptIds: Set<String>,
    keepError: String?,
    readerLat: Double?,
    readerLng: Double?,
    onToggleKeep: (String) -> Unit,
    onOpenCompass: () -> Unit,
    onCloseCompass: () -> Unit,
    onPlayOnTurntable: (ArrivedRecordOption) -> Unit,
    onClose: () -> Unit,
) {
    val shown = rememberLastNonNull(option)
    val showCompass = option != null && compassOpen && option.distanceLabel != null

    AnimatedBottomSheet(visible = option != null && !showCompass, onDismiss = onClose, dismissOnSwipe = true) {
        if (shown == null) return@AnimatedBottomSheet
        val submissionId = shown.submissionId
        // A fresh player for each card, so one card's progress never carries over to the next.
        key(shown.sessionKey) {
            MusicCardScreen(
                state = MusicCardUiState(
                    trackName = shown.trackName,
                    artistName = shown.artistName,
                    artworkUrl = shown.artworkUrl,
                    previewUrl = shown.previewUrl,
                    mood = shown.mood,
                    message = shown.messagePreview,
                    sentDateLabel = shown.sentDateLabel,
                    hasDirection = shown.distanceLabel != null,
                    isKept = submissionId != null && submissionId in keptIds,
                    keepError = keepError,
                ),
                onToggleKeep = { submissionId?.let(onToggleKeep) },
                onOpenCompass = onOpenCompass,
                onPlayOnTurntable = { onPlayOnTurntable(shown) },
                inSheet = true,
                playerActive = option != null && !showCompass,
            )
        }
    }

    if (showCompass && shown != null) {
        val compassState by rememberCompassHeading(
            enabled = true,
            readerLat = readerLat,
            readerLng = readerLng,
            senderLat = shown.senderLat,
            senderLng = shown.senderLng,
        )
        val senderCityLabel by rememberSenderCityLabel(shown.senderLat, shown.senderLng)
        CompassScreen(
            state = CompassUiState(distanceLabel = shown.distanceLabel, cityLabel = senderCityLabel),
            compassState = compassState,
            onBack = onCloseCompass,
        )
    }
}

/**
 * A record opened from the Collection, in history mode: the music card with favourite and remove
 * actions, plus Play on turntable. The compass and distance belong to the day a card arrives, so
 * this sheet reads no location and starts no sensor. A card the reader sent is signed by them.
 *
 * [record] is null when nothing is open. It's still drawn then, so the sheet can slide out
 * showing the card it's closing.
 */
@Composable
internal fun CollectionCardSheet(
    record: VinylRecord?,
    actionError: String?,
    onToggleFavourite: () -> Unit,
    onRemove: () -> Unit,
    onPlayOnTurntable: (VinylRecord) -> Unit,
    onClose: () -> Unit,
) {
    val shown = rememberLastNonNull(record)

    AnimatedBottomSheet(visible = record != null, onDismiss = onClose, dismissOnSwipe = true) {
        if (shown == null) return@AnimatedBottomSheet
        val received = shown.source == RecordSource.RECEIVED
        key(shown.id) {
            MusicCardScreen(
                state = MusicCardUiState(
                    trackName = shown.songName,
                    artistName = shown.artist,
                    artworkUrl = shown.coverUrl,
                    previewUrl = shown.previewUrl,
                    mood = shown.moodTag,
                    message = shown.message.orEmpty(),
                    sentDateLabel = sentDateLabel(shown.sentAt),
                    hasDirection = false,
                    keepError = actionError,
                    isFavourite = if (received) shown.isFavourite else null,
                    isOwn = !received,
                ),
                onToggleKeep = null,
                onOpenCompass = {},
                onPlayOnTurntable = { onPlayOnTurntable(shown) },
                onToggleFavourite = onToggleFavourite,
                onRemove = if (received) onRemove else null,
                inSheet = true,
                playerActive = record != null,
            )
        }
    }
}