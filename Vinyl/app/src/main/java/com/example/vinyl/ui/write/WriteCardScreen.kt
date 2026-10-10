package com.example.vinyl.ui.write

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.vinyl.data.THUMB_ARTWORK_PX
import com.example.vinyl.data.itunesArtworkAt
import com.example.vinyl.data.onboarding.GenreOption
import com.example.vinyl.data.MoodOptions
import com.example.vinyl.data.Track
import com.example.vinyl.ui.daily.GenreDropdown
import com.example.vinyl.ui.daily.MoodPicker
import com.example.vinyl.ui.location.LocationViewModel
import com.example.vinyl.ui.theme.PoppinsFontFamily
import com.example.vinyl.ui.theme.VinylPalette
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import com.example.vinyl.ui.daily.moodIcon
import androidx.compose.foundation.Image
import com.example.vinyl.R
import com.example.vinyl.network.AudioPreviewController
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.semantics.Role
import com.example.vinyl.network.rememberAudioPreviewController
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WriteCardScreen(
    viewModel: WriteCardViewModel = viewModel(),
    locationViewModel: LocationViewModel = viewModel(),
    onSent: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val locationState by locationViewModel.uiState.collectAsState()
    val audioController = rememberAudioPreviewController()

    // Genres come from the database; if they didn't load last time (offline), try again.
    LaunchedEffect(Unit) { viewModel.loadGenresIfNeeded() }

    // A letter can only carry what the profile already holds. Forcing the toggle off (rather than
    // just disabling the switch) keeps the card preview honest about what's being sent.
    LaunchedEffect(locationState.hasLocation) {
        if (!locationState.hasLocation) viewModel.onAttachLocationToggled(false)
    }

    // Frozen copy of the card's data for the send animation to render from.
    var sendSnapshot by remember { mutableStateOf<WriteCardUiState?>(null) }
    var sendAnimationDone by remember { mutableStateOf(false) }
    var sentId by remember { mutableStateOf<String?>(null) }
    val isSending = sendSnapshot != null

    // Animation finished AND the network call succeeded: show the "Sent!" screen first.
    LaunchedEffect(state.submittedId, sendAnimationDone) {
        val id = state.submittedId
        if (id != null && sendAnimationDone) {
            sendSnapshot = null
            sendAnimationDone = false
            sentId = id
        }
    }

    // Hold the confirmation briefly, then leave exactly as before.
    LaunchedEffect(sentId) {
        val id = sentId ?: return@LaunchedEffect
        delay(1800)
        onSent(id)
        sentId = null
    }

    // If sending failed, drop the animation and let the user see the error and retry
    LaunchedEffect(state.submissionError) {
        if (state.submissionError != null) {
            sendSnapshot = null
            sendAnimationDone = false
        }
    }

    LaunchedEffect(state.isPreviewMode, isSending) {
        if (state.isPreviewMode || isSending) audioController.release()
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(VinylPalette.Background)
        ) {
            if (state.isPreviewMode && !isSending) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp, end = 40.dp, top = 48.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Preview",
                        color = VinylPalette.Cream,
                        style = formText(32.sp, FontWeight.Normal, 40.sp),
                    )
                    Button(
                        onClick = viewModel::onTogglePreview,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VinylPalette.TealAccent,
                            contentColor = VinylPalette.Background,
                        ),
                        contentPadding = PaddingValues(horizontal = 28.dp),
                    ) {
                        Text("Edit", style = formText(18.sp, FontWeight.Medium))
                    }
                }
            }

            if (isSending) {
                SendingAnimation(
                    state = sendSnapshot!!,
                    onFinished = { sendAnimationDone = true },
                    modifier = Modifier.weight(1f),
                )
            } else if (state.isPreviewMode) {
                CardPreview(
                    state = state,
                    modifier = Modifier.weight(1f),
                    onSend = {
                        sendSnapshot = state
                        sendAnimationDone = false
                        viewModel.submit()
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    item { CreateHeader() }
                    item {
                        SongPickerSection(
                            state = state,
                            onQueryChange = viewModel::onQueryChange,
                            onTrackSelected = viewModel::onTrackSelected,
                            onTrackCleared = viewModel::onTrackCleared,
                            audioController = audioController,
                        )
                    }
                    item { LetterSection(state.message, viewModel::onMessageChange) }
                    item { MoodSection(state.mood, viewModel::onMoodSelected) }
                    item {
                        GenreSection(
                            options = state.genreOptions,
                            selected = state.selectedGenres,
                            onToggle = viewModel::onGenreToggled,
                        )
                    }
                    item { EnvelopeStylePicker(state.envelopeStyle, viewModel::onEnvelopeStyleSelected) }
                    item {
                        LocationToggle(
                            attachLocation = state.attachLocation,
                            onAttachLocationToggled = viewModel::onAttachLocationToggled,
                            hasLocation = locationState.hasLocation,
                        )
                    }
                    state.submissionError?.let { error ->
                        item {
                            Text(error, color = Color(0xFFE08787), fontSize = 13.sp)
                        }
                    }
                    item {
                        FormActions(
                            canSubmit = state.canSubmit,
                            isSubmitting = state.isSubmitting,
                            onPreview = viewModel::onTogglePreview,
                            onSend = {
                                sendSnapshot = state
                                sendAnimationDone = false
                                viewModel.submit()
                            },
                        )
                    }
                }
            }
        }

        if (sentId != null) {
            SentConfirmation()
        }
    }
}

private val FormPanelShape = RoundedCornerShape(20.dp)
private val FormPanelBrush: Brush get() = VinylPalette.PanelColors.panelGradient
private val FormPanelBorder: Color get() = VinylPalette.PanelColors.panelBorder
private val MessageInk = Color(0xFF0D0D0D)
private val CounterInk = Color(0xFF5E6766)

private fun formText(size: androidx.compose.ui.unit.TextUnit, weight: FontWeight, lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified) =
    TextStyle(fontFamily = PoppinsFontFamily, fontSize = size, fontWeight = weight, lineHeight = lineHeight)

@Composable
private fun CreateHeader() {
    Column {
        Text(
            text = "Write a music card",
            color = VinylPalette.Cream,
            style = formText(22.sp, FontWeight.Medium, 30.sp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "A song and a few words, for someone.",
            color = VinylPalette.Cream.copy(alpha = 0.58f),
            style = formText(14.sp, FontWeight.Light, 20.sp),
        )
    }
}

@Composable
private fun SectionHeader(label: String, required: Boolean = false, mandatory: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label.uppercase(),
                color = VinylPalette.Cream.copy(alpha = 0.6f),
                style = formText(11.sp, FontWeight.Medium),
                letterSpacing = 0.8.sp,
            )
            if (mandatory) {
                Text(
                    text = " *",
                    color = VinylPalette.TealAccent,
                    style = formText(12.sp, FontWeight.Medium),
                )
            }
        }
        if (required) {
            Text(
                text = "Required",
                color = VinylPalette.Cream.copy(alpha = 0.55f),
                style = formText(12.sp, FontWeight.Light),
            )
        }
    }
}

@Composable
private fun SongPickerSection(
    state: WriteCardUiState,
    onQueryChange: (String) -> Unit,
    onTrackSelected: (Track) -> Unit,
    onTrackCleared: () -> Unit,
    audioController: AudioPreviewController,
) {
    Column {
        SectionHeader("Song", mandatory = true)
        Spacer(Modifier.height(8.dp))

        if (state.selectedTrack != null) {
            SelectedTrackCard(track = state.selectedTrack, onClear = onTrackCleared, audioController = audioController)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .background(FormPanelBrush, FormPanelShape)
                    .border(
                        1.dp,
                        if (state.query.isNotEmpty()) VinylPalette.TealAccent else FormPanelBorder,
                        FormPanelShape,
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = VinylPalette.Cream.copy(alpha = 0.4f),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    textStyle = formText(15.sp, FontWeight.Normal).copy(color = VinylPalette.Cream),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(VinylPalette.TealAccent),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Search song or artist" },
                    decorationBox = { innerTextField ->
                        Box {
                            if (state.query.isEmpty()) {
                                Text(
                                    "Search song or artist",
                                    color = VinylPalette.Cream.copy(alpha = 0.4f),
                                    style = formText(15.sp, FontWeight.Light),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            if (state.isSearching) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = VinylPalette.TealAccent,
                    trackColor = FormPanelBorder,
                )
            }

            if (state.searchResults.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FormPanelBrush, FormPanelShape)
                        .border(1.dp, FormPanelBorder, FormPanelShape)
                        .clip(FormPanelShape)
                        .padding(vertical = 4.dp),
                ) {
                    state.searchResults.take(6).forEach { result ->
                        TrackResultRow(result, onClick = { onTrackSelected(result) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackArtwork(track: Track, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(VinylPalette.SheetSurface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = VinylPalette.Cream.copy(alpha = 0.5f),
        )
        track.artworkUrl?.let { artwork ->
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TrackResultRow(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArtwork(track, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.trackName,
                color = VinylPalette.Cream,
                style = formText(15.sp, FontWeight.Medium, 20.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artistName,
                color = VinylPalette.Cream.copy(alpha = 0.7f),
                style = formText(12.sp, FontWeight.Light, 17.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SelectedTrackCard(
    track: Track,
    onClear: () -> Unit,
    audioController: AudioPreviewController,
) {
    val isPlayingThis = audioController.isPlaying && audioController.currentUrl == track.previewUrl

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FormPanelBrush, FormPanelShape)
            .border(1.dp, FormPanelBorder, FormPanelShape)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(VinylPalette.SheetSurface)
                .clickable(
                    enabled = track.previewUrl != null,
                    role = Role.Button,
                    onClickLabel = if (isPlayingThis) "Pause preview" else "Play 30 second preview",
                ) { audioController.toggle(track.previewUrl) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = VinylPalette.Cream.copy(alpha = 0.5f),
            )
            track.artworkUrl?.let { artwork ->
                AsyncImage(
                    model = itunesArtworkAt(artwork, THUMB_ARTWORK_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Scrim so the icon stays legible over any artwork
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            }
            Icon(
                imageVector = if (isPlayingThis) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = if (track.previewUrl != null) Color.White else VinylPalette.TextMuted,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.trackName,
                color = VinylPalette.Cream,
                style = formText(16.sp, FontWeight.Medium, 22.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artistName,
                color = VinylPalette.Cream.copy(alpha = 0.7f),
                style = formText(13.sp, FontWeight.Light, 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClear, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Change song",
                tint = VinylPalette.Cream.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LetterSection(message: String, onMessageChange: (String) -> Unit) {
    Column {
        SectionHeader("Message", mandatory = true)
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VinylPalette.Cream, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = message,
                onValueChange = onMessageChange,
                textStyle = formText(15.sp, FontWeight.Normal, 24.sp).copy(color = MessageInk),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MessageInk),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (message.isEmpty()) {
                            Text(
                                "Write a message…",
                                color = MessageInk.copy(alpha = 0.4f),
                                style = formText(15.sp, FontWeight.Normal, 24.sp),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Text(
                text = "${message.length} / 280",
                color = CounterInk,
                style = formText(11.sp, FontWeight.Light),
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun MoodSection(selectedMood: com.example.vinyl.data.MoodTag?, onMoodSelected: (com.example.vinyl.data.MoodTag) -> Unit) {
    Column {
        SectionHeader("Mood", required = selectedMood == null, mandatory = true)
        Spacer(Modifier.height(8.dp))
        MoodPicker(selectedMood = selectedMood, onMoodSelected = onMoodSelected)
    }
}

@Composable
private fun GenreSection(
    options: List<GenreOption>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    if (options.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Column {
        SectionHeader("Genre")
        Spacer(Modifier.height(8.dp))
        GenreDropdown(
            options = options,
            selected = selected,
            open = open,
            onOpenChange = { open = it },
            onToggle = onToggle,
            includeAnyOption = false,
            menuMaxHeight = 216.dp,
        )
    }
}

@Composable
private fun EnvelopeStylePicker(selectedStyle: EnvelopeStyle, onStyleSelected: (EnvelopeStyle) -> Unit) {
    Column {
        Text("ENVELOPE STYLE", color = VinylPalette.TextMuted, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            EnvelopeStyle.entries.forEach { style ->
                val selected = selectedStyle == style
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(style.colors))
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) VinylPalette.TextPrimary else Color.Black.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { onStyleSelected(style) }
                )
            }
        }
    }
}

@Composable
private fun LocationToggle(
    attachLocation: Boolean,
    onAttachLocationToggled: (Boolean) -> Unit,
    hasLocation: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Attach my location",
                color = if (hasLocation) VinylPalette.TextPrimary else VinylPalette.TextMuted,
                fontSize = 14.sp,
            )
            if (!hasLocation) {
                Text(
                    text = "Set your location in Settings to attach it",
                    color = VinylPalette.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Switch(
            checked = attachLocation,
            onCheckedChange = onAttachLocationToggled,
            enabled = hasLocation,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VinylPalette.TealAccent,
                checkedTrackColor = VinylPalette.TealAccent.copy(alpha = 0.4f),
                uncheckedThumbColor = Color.Black,
                uncheckedTrackColor = Color.Black.copy(alpha = 0.3f),
                disabledUncheckedThumbColor = Color.Black.copy(alpha = 0.5f),
                disabledUncheckedTrackColor = Color.Black.copy(alpha = 0.2f),
            ),
        )
    }
}

@Composable
private fun FormActions(
    canSubmit: Boolean,
    isSubmitting: Boolean,
    onPreview: () -> Unit,
    onSend: () -> Unit,
) {
    val active = canSubmit && !isSubmitting
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(
            onClick = onPreview,
            enabled = canSubmit,
            modifier = Modifier.height(44.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = VinylPalette.TealAccent,
                disabledContentColor = VinylPalette.TextMuted.copy(alpha = 0.5f),
            ),
        ) {
            Text("Preview", style = formText(14.sp, FontWeight.Medium))
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onSend,
            enabled = active,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(50),
            border = BorderStroke(
                1.5.dp,
                if (active) VinylPalette.TealAccent else VinylPalette.TextMuted.copy(alpha = 0.35f),
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = VinylPalette.TealAccent,
                disabledContentColor = VinylPalette.TextMuted.copy(alpha = 0.55f),
            ),
            contentPadding = PaddingValues(0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FormPanelBrush),
            ) {
                Text(
                    text = if (isSubmitting) "Sending…" else "Send",
                    style = formText(18.sp, FontWeight.Medium),
                    modifier = Modifier.align(Alignment.Center),
                )
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                    tint = if (active) VinylPalette.TealAccent else VinylPalette.TextMuted.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp)
                        .size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun SentConfirmation(modifier: Modifier = Modifier) {
    val accent = VinylPalette.TealAccent
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(300)) }
        scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 300f))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VinylPalette.Background)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                }
                .shadow(24.dp, CircleShape, ambientColor = accent, spotColor = accent)
                .size(72.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f))
                .border(2.dp, accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(34.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Sent!",
            color = VinylPalette.Cream,
            style = formText(32.sp, FontWeight.Medium, 40.sp),
            modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Your card is on its way.",
            color = VinylPalette.Cream.copy(alpha = 0.55f),
            style = formText(15.sp, FontWeight.Light, 22.sp),
            modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
        )
    }
}

private val PaperCream = Color(0xFFF4F0EA)

@Composable
private fun CardPreview(state: WriteCardUiState, modifier: Modifier = Modifier, onSend: () -> Unit = {}) {
    val track = state.selectedTrack ?: return
    val moodOption = MoodOptions.all.firstOrNull { it.tag == state.mood }

    val ink = VinylPalette.Background
    val inkMuted = VinylPalette.Background.copy(alpha = 0.6f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "MUSIC CARD · ANONYMOUS",
            color = VinylPalette.TextMuted,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )

        VinylPreviewDisc(artworkUrl = track.artworkUrl)

        // Paper card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(PaperCream)
                .padding(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Song row (old design: cover 64, title, artist)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VinylPalette.SheetSurface),
                ) {
                    track.artworkUrl?.let { artwork ->
                        AsyncImage(
                            model = itunesArtworkAt(artwork, THUMB_ARTWORK_PX),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        track.trackName,
                        color = ink,
                        style = formText(20.sp, FontWeight.Medium, 26.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.artistName,
                        color = inkMuted,
                        style = formText(14.sp, FontWeight.Normal, 20.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Message printed on the paper
            Text(
                text = state.message,
                color = ink,
                style = formText(15.sp, FontWeight.Normal, 26.sp),
            )

            // Thin divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ink.copy(alpha = 0.15f)),
            )

            // Mood bottom-left, location note bottom-right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                moodOption?.let { CardMoodMark(it, ink) }
                if (state.attachLocation) {
                    Text(
                        text = "sent with your approximate location",
                        color = inkMuted,
                        style = formText(12.sp, FontWeight.Light, 17.sp),
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                }
            }
        }

        // Same column width as the card
        EnvelopePreview(style = state.envelopeStyle)

        Text(
            text = "This is the card the person who receives it will see.",
            color = VinylPalette.TextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )

        state.submissionError?.let {
            Text(it, color = Color(0xFFE08787), fontSize = 13.sp)
        }

        PreviewSendButton(
            active = state.canSubmit && !state.isSubmitting,
            isSubmitting = state.isSubmitting,
            onSend = onSend,
        )

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CardMoodMark(option: com.example.vinyl.data.MoodOption, ink: Color) {
    Icon(
        painter = painterResource(moodIcon(option.tag)),
        contentDescription = "Mood: ${option.title}",
        tint = ink,
        modifier = Modifier.size(32.dp),
    )
}

@Composable
internal fun EnvelopePreview(style: EnvelopeStyle, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(style.colors))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            when (style.motif) {
                EnvelopeMotif.MOON -> drawMoonMotif(w, h)
                EnvelopeMotif.HEARTS -> drawHeartsMotif(w, h)
                EnvelopeMotif.NONE -> Unit
            }

            if (style.showFold) {
                val flap = ComposePath().apply {
                    moveTo(0f, 0f)
                    lineTo(w / 2f, h * 0.55f)
                    lineTo(w, 0f)
                    close()
                }
                drawPath(flap, color = Color.Black.copy(alpha = 0.16f))
                drawPath(flap, color = Color.White.copy(alpha = 0.15f), style = Stroke(width = 1.5.dp.toPx()))
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .shadow(6.dp, CircleShape)
                .size(48.dp)
                .clip(CircleShape)
                .background(VinylPalette.TealAccent),
            contentAlignment = Alignment.Center,
        ) {
            Text("♪", color = VinylPalette.Background, fontSize = 22.sp)
        }

        Text(
            text = "${style.label.uppercase()} · SEALED",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        )
    }
}

internal fun DrawScope.drawMoonMotif(w: Float, h: Float) {
    val moonRadius = minOf(w, h) * 0.14f
    val moonCenter = Offset(w * 0.72f, h * 0.28f)

    val moonCircle = ComposePath().apply {
        addOval(Rect(center = moonCenter, radius = moonRadius))
    }
    val cutCircle = ComposePath().apply {
        addOval(Rect(center = moonCenter + Offset(moonRadius * 0.55f, -moonRadius * 0.25f), radius = moonRadius))
    }
    val crescent = ComposePath().apply { op(moonCircle, cutCircle, PathOperation.Difference) }
    drawPath(crescent, color = Color(0xFFF4F0EA).copy(alpha = 0.9f))

    val stars = listOf(
        Offset(w * 0.15f, h * 0.18f) to 2.2.dp,
        Offset(w * 0.30f, h * 0.35f) to 1.4.dp,
        Offset(w * 0.55f, h * 0.12f) to 1.8.dp,
        Offset(w * 0.85f, h * 0.50f) to 1.5.dp,
        Offset(w * 0.42f, h * 0.55f) to 1.2.dp,
        Offset(w * 0.65f, h * 0.65f) to 1.6.dp,
    )
    stars.forEach { (pos, radius) ->
        drawCircle(color = Color.White.copy(alpha = 0.75f), radius = radius.toPx(), center = pos)
    }
}

internal fun DrawScope.drawHeartsMotif(w: Float, h: Float) {
    // (xFraction, yFraction, sizeFraction of the shorter side)
    val hearts = listOf(
        Triple(0.18f, 0.22f, 0.11f),
        Triple(0.78f, 0.16f, 0.09f),
        Triple(0.52f, 0.42f, 0.13f),
        Triple(0.28f, 0.65f, 0.10f),
        Triple(0.85f, 0.58f, 0.08f),
    )
    val shortSide = minOf(w, h)
    hearts.forEach { (fx, fy, fSize) ->
        drawPath(
            path = heartPath(cx = w * fx, cy = h * fy, size = shortSide * fSize),
            color = Color.White.copy(alpha = 0.4f),
        )
    }
}

private fun heartPath(cx: Float, cy: Float, size: Float): ComposePath = ComposePath().apply {
    moveTo(cx, cy + size * 0.35f)
    cubicTo(
        cx - size * 0.5f, cy - size * 0.15f,
        cx - size * 0.5f, cy - size * 0.55f,
        cx, cy - size * 0.2f,
    )
    cubicTo(
        cx + size * 0.5f, cy - size * 0.55f,
        cx + size * 0.5f, cy - size * 0.15f,
        cx, cy + size * 0.35f,
    )
    close()
}

// Fraction of the disc's width covered by the album art. Raise it if white still
// shows around the art, lower it if the art spills onto the grooves.
private const val DiscLabelFraction = 0.31f

// Nudge if the label isn't exactly centred in the PNG (fractions of the disc width).
private const val DiscLabelOffsetX = 0f
private const val DiscLabelOffsetY = 0f
// Size of the spindle hole as a fraction of the disc's width.
private const val DiscHoleFraction = 0.035f

@Composable
internal fun VinylPreviewDisc(artworkUrl: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(horizontal = 30.dp)   // padding first, so the box below is square
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.black_vinyl),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val labelSize = maxWidth * DiscLabelFraction
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * DiscLabelOffsetX, y = maxWidth * DiscLabelOffsetY)
                    .size(labelSize)
                    .clip(CircleShape)
                    .background(Color(0xFF0D0D0D)),
            ) {
                if (artworkUrl != null) {
                    AsyncImage(
                        model = itunesArtworkAt(artworkUrl, THUMB_ARTWORK_PX),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Spindle hole, drawn over the artwork
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * DiscLabelOffsetX, y = maxWidth * DiscLabelOffsetY)
                    .size(maxWidth * DiscHoleFraction)
                    .clip(CircleShape)
                    .background(Color(0xFF0D0D0D)),
            )
        }
    }
}

private val PreviewGenreOptions = listOf(
    GenreOption("pop", "Pop"),
    GenreOption("rock", "Rock"),
    GenreOption("indie", "Indie"),
    GenreOption("alternative", "Alternative"),
    GenreOption("electronic", "Electronic"),
    GenreOption("edm", "EDM"),
    GenreOption("synth_pop", "Synth-pop"),
    GenreOption("hip_hop", "Hip-Hop"),
    GenreOption("rap", "Rap"),
    GenreOption("rnb", "R&B"),
    GenreOption("soul", "Soul"),
    GenreOption("funk", "Funk"),
)

@Composable
private fun WriteCardFormPreviewContent(state: WriteCardUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylPalette.Background),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item { CreateHeader() }
            item { SongPickerSection(state, {}, {}, {}, rememberAudioPreviewController()) }
            item { LetterSection(state.message, {}) }
            item { MoodSection(state.mood, {}) }
            item { GenreSection(state.genreOptions, state.selectedGenres, {}) }
            item { EnvelopeStylePicker(state.envelopeStyle, {}) }
            item { LocationToggle(state.attachLocation, {}, hasLocation = true) }
            item { FormActions(state.canSubmit, state.isSubmitting, {}, {}) }
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF0D0D0D,
    widthDp = 393,
    heightDp = 852,
)
@Composable
private fun WriteCardEmptyPreview() {
    WriteCardFormPreviewContent(WriteCardUiState(genreOptions = PreviewGenreOptions))
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF0D0D0D,
    widthDp = 393,
    heightDp = 852,
)
@Composable
private fun WriteCardFilledPreview() {
    WriteCardFormPreviewContent(
        WriteCardUiState(
            selectedTrack = Track(
                trackId = 1L,
                trackName = "Lover",
                artistName = "Taylor Swift",
            ),
            message = "This one got me through a long winter.\nHope it does something small for you today.",
            mood = com.example.vinyl.data.MoodTag.Calm,
            genreOptions = PreviewGenreOptions,
            selectedGenres = setOf("hip_hop", "electronic"),
            attachLocation = true,
        )
    )
}

@Composable
private fun PreviewSendButton(active: Boolean, isSubmitting: Boolean, onSend: () -> Unit) {
    OutlinedButton(
        onClick = onSend,
        enabled = active,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(50),
        border = BorderStroke(
            1.5.dp,
            if (active) VinylPalette.TealAccent else VinylPalette.TextMuted.copy(alpha = 0.35f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = VinylPalette.TealAccent,
            disabledContentColor = VinylPalette.TextMuted.copy(alpha = 0.55f),
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(FormPanelBrush)) {
            Text(
                text = if (isSubmitting) "Sending…" else "Send",
                style = formText(18.sp, FontWeight.Medium),
                modifier = Modifier.align(Alignment.Center),
            )
            Icon(
                Icons.AutoMirrored.Rounded.Send,
                contentDescription = null,
                tint = if (active) VinylPalette.TealAccent else VinylPalette.TextMuted.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp).size(22.dp),
            )
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF0D0D0D,
    widthDp = 393,
    heightDp = 852,
)
@Composable
private fun CardPreviewPreview() {
    val sampleState = WriteCardUiState(
        selectedTrack = Track(
            trackId = 1L,
            trackName = "Landslide",
            artistName = "Fleetwood Mac",
        ),
        message = "Hits differently depending on how old you are.",
        mood = com.example.vinyl.data.MoodTag.Nostalgic,
        attachLocation = true,
        isPreviewMode = true,
        envelopeStyle = EnvelopeStyle.Rainbow,
    )
    CardPreview(state = sampleState)
}
