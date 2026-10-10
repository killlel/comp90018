package com.example.vinyl.ui.write

import android.graphics.Paint
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.draw.drawBehind

@Composable
fun WriteCardScreen(
    viewModel: WriteCardViewModel = viewModel(),
    locationViewModel: LocationViewModel = viewModel(),
    onSent: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val locationState by locationViewModel.uiState.collectAsState()

    // Genres come from the database; if they didn't load last time (offline), try again.
    LaunchedEffect(Unit) { viewModel.loadGenresIfNeeded() }

    // A letter can only carry what the profile already holds. Forcing the toggle off (rather than
    // just disabling the switch) keeps the card preview honest about what's being sent.
    LaunchedEffect(locationState.hasLocation) {
        if (!locationState.hasLocation) viewModel.onAttachLocationToggled(false)
    }

    // Frozen copy of the card's data for the send animation to render from —
    // the ViewModel resets its state as soon as the network call succeeds,
    // which would otherwise yank the card/disc/envelope out from under the animation mid-flight.
    var sendSnapshot by remember { mutableStateOf<WriteCardUiState?>(null) }
    var sendAnimationDone by remember { mutableStateOf(false) }
    val isSending = sendSnapshot != null

    // Only leave once BOTH the network call has actually succeeded AND the animation has
    // finished playing — otherwise a fast network response cuts the animation off mid-flight.
    LaunchedEffect(state.submittedId, sendAnimationDone) {
        val id = state.submittedId
        if (id != null && sendAnimationDone) {
            sendSnapshot = null
            sendAnimationDone = false
            onSent(id)
        }
    }

    // If sending failed, drop the animation and let the user see the error and retry
    LaunchedEffect(state.submissionError) {
        if (state.submissionError != null) {
            sendSnapshot = null
            sendAnimationDone = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylPalette.Background)
    ) {
        if (state.isPreviewMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Preview",
                    color = VinylPalette.TextPrimary,
                    fontSize = 28.sp,
                )
                Button(
                    onClick = viewModel::onTogglePreview,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VinylPalette.TealAccent,
                        contentColor = VinylPalette.Background,
                    ),
                ) {
                    Text(
                        text = "Edit",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
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
            CardPreview(state = state, modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item { CreateHeader() }
                item {
                    SongPickerSection(
                        state = state,
                        onQueryChange = viewModel::onQueryChange,
                        onTrackSelected = viewModel::onTrackSelected,
                        onTrackCleared = viewModel::onTrackCleared,
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
}

private val FormPanelShape = RoundedCornerShape(20.dp)
private val FormPanelBrush = Brush.verticalGradient(listOf(Color(0xFF111413), Color(0xFF2E3938)))
private val FormPanelBorder = Color(0xFF3E4A49)
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
            text = "Send one song and a few words to someone in the world.",
            color = VinylPalette.Cream.copy(alpha = 0.58f),
            style = formText(14.sp, FontWeight.Light, 20.sp),
        )
    }
}

@Composable
private fun SectionHeader(label: String, required: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            color = VinylPalette.Cream.copy(alpha = 0.6f),
            style = formText(11.sp, FontWeight.Medium),
            letterSpacing = 0.8.sp,
        )
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
) {
    Column {
        SectionHeader("Song")
        Spacer(Modifier.height(8.dp))

        if (state.selectedTrack != null) {
            SelectedTrackCard(track = state.selectedTrack, onClear = onTrackCleared)
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
private fun SelectedTrackCard(track: Track, onClear: () -> Unit) {
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
                    model = itunesArtworkAt(artwork, THUMB_ARTWORK_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
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
        SectionHeader("Message")
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
        SectionHeader("Mood", required = selectedMood == null)
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
private fun CardPreview(state: WriteCardUiState, modifier: Modifier = Modifier) {
    val track = state.selectedTrack ?: return
    val moodOption = MoodOptions.all.firstOrNull { it.tag == state.mood }

    // Dark, readable-on-cream colors
    val cardTextPrimary = VinylPalette.Background
    val cardTextMuted = VinylPalette.Background.copy(alpha = 0.6f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "MUSIC CARD · ANONYMOUS",
            color = VinylPalette.TextMuted,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )

        VinylPreviewDisc(trackName = track.trackName, envelopeStyle = state.envelopeStyle)

        // --- The letter card — cream, dark text ---
        // Narrower than the disc/envelope (40dp margin vs their 30dp) so it never outgrows them
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(VinylPalette.Cream)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (track.artworkUrl != null) {
                    AsyncImage(
                        model = itunesArtworkAt(track.artworkUrl, THUMB_ARTWORK_PX),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
                Column {
                    Text(track.trackName, color = cardTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Text(track.artistName, color = cardTextMuted, fontSize = 14.sp)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VinylPalette.Background.copy(alpha = 0.05f))
                    .padding(16.dp)
            ) {
                Text(
                    text = state.message,
                    color = cardTextPrimary,
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                )
            }

            if (moodOption != null || state.selectedGenres.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                ) {
                    moodOption?.let {
                        CardInfoBlock(
                            label = "MOOD",
                            value = it.title,
                            textPrimary = cardTextPrimary,
                            textMuted = cardTextMuted,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    if (state.selectedGenres.isNotEmpty()) {
                        CardInfoBlock(
                            label = "GENRE",
                            value = state.selectedGenreLabels.take(3).joinToString(" · "),
                            textPrimary = cardTextPrimary,
                            textMuted = cardTextMuted,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }

            Text(
                text = if (state.attachLocation)
                    "— sent with your approximate location"
                else
                    "— sent with no location",
                color = cardTextMuted,
                fontSize = 12.sp,
            )
        }

        EnvelopePreview(style = state.envelopeStyle, modifier = Modifier.padding(horizontal = 30.dp))

        Text(
            text = "This is what the person who receives it will see.",
            color = VinylPalette.TextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun CardInfoBlock(
    label: String,
    value: String,
    textPrimary: Color,
    textMuted: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(textPrimary.copy(alpha = 0.06f))
            .border(1.dp, textPrimary.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            value,
            color = textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = textMuted, fontSize = 10.sp, letterSpacing = 1.sp)
    }
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
                .size(30.dp)
                .clip(CircleShape)
                .background(VinylPalette.Background.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("♪", color = Color.White, fontSize = 14.sp)
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

@Composable
internal fun VinylPreviewDisc(trackName: String, envelopeStyle: EnvelopeStyle) {
    // Pick a representative color from the envelope's palette for the label —
    // the middle stop tends to read better than the first/last (often the darkest/lightest)
    val labelColor = envelopeStyle.colors[envelopeStyle.colors.size / 2]

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(horizontal = 30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(color = Color(0xFF1A1A1A), radius = radius, center = center)
            for (i in 1..6) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f),
                    radius = radius * (0.3f + i * 0.1f),
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            drawCircle(color = labelColor, radius = radius * 0.32f, center = center)
            drawCircle(color = Color.Black, radius = 5.dp.toPx(), center = center)

            val textRadius = radius * 0.88f
            val path = android.graphics.Path().apply {
                addCircle(center.x, center.y, textRadius, android.graphics.Path.Direction.CW)
            }
            val paint = Paint().apply {
                color = labelColor.toArgb()
                textSize = 11.sp.toPx()
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                letterSpacing = 0.08f
            }
            rotate(degrees = 90f, pivot = center) {
                drawContext.canvas.nativeCanvas.drawTextOnPath(trackName.uppercase(), path, 0f, 0f, paint)
            }
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
            item { SongPickerSection(state, {}, {}, {}) }
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
