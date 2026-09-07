package com.example.vinyl.ui.write

import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vinyl.data.GenreOptions
import com.example.vinyl.data.MoodOptions
import com.example.vinyl.data.Track
import com.example.vinyl.network.AudioPreviewController
import com.example.vinyl.network.rememberAudioPreviewController
import com.example.vinyl.ui.theme.VinylPalette
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.drawBehind

@Composable
fun WriteCardScreen(
    viewModel: WriteCardViewModel = viewModel(),
    onSent: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val audioController = rememberAudioPreviewController()

    LaunchedEffect(state.submittedId) {
        state.submittedId?.let { onSent(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylPalette.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.isPreviewMode) "Preview" else "Write Letter",
                color = VinylPalette.TextPrimary,
                fontSize = 28.sp,
            )
            TextButton(
                onClick = viewModel::onTogglePreview,
                enabled = state.isPreviewMode || state.canSubmit,
            ) {
                Text(
                    text = if (state.isPreviewMode) "Edit" else "Preview",
                    color = if (state.isPreviewMode || state.canSubmit)
                        VinylPalette.TealAccent else VinylPalette.TextMuted,
                    fontSize = 14.sp,
                )
            }
        }

        if (state.isPreviewMode) {
            CardPreview(state = state, modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { SongPickerSection(state, viewModel, audioController) }
                item { LetterSection(state, viewModel) }
                item { MoodGrid(state, viewModel) }
                item { GenreChips(state, viewModel) }
                item { EnvelopeStylePicker(state, viewModel) }
                item { LocationToggle(state, viewModel) }
                state.submissionError?.let { err ->
                    item { Text(err, color = Color(0xFFE08787), fontSize = 13.sp) }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        SendBar(
            state = state,
            onSend = { viewModel.submit(/* lat, lng from your GPS layer once wired */) },
        )
    }
}

@Composable
private fun SongPickerSection(
    state: WriteCardUiState,
    viewModel: WriteCardViewModel,
    audioController: AudioPreviewController,
) {
    val track = state.selectedTrack

    Column {
        if (track != null) {
            SelectedTrackCard(track, onClear = { viewModel.onTrackCleared() }, audioController = audioController)
        } else {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search for a song…", color = VinylPalette.TextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = vinylTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.isSearching) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = VinylPalette.TealAccent,
                    trackColor = VinylPalette.PanelDark,
                )
            }

            if (state.searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VinylPalette.PanelDark)
                ) {
                    state.searchResults.take(6).forEach { result ->
                        TrackResultRow(result, onClick = { viewModel.onTrackSelected(result) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackResultRow(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(track.trackName, color = VinylPalette.TextPrimary, fontSize = 14.sp)
            Text(track.artistName, color = VinylPalette.TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SelectedTrackCard(
    track: Track,
    onClear: () -> Unit,
    audioController: AudioPreviewController,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VinylPalette.PanelDark)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { audioController.toggle(track.previewUrl) },
            enabled = track.previewUrl != null,
        ) {
            Icon(
                imageVector = if (audioController.isPlaying && audioController.currentUrl == track.previewUrl)
                    Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "Preview 30 seconds",
                tint = if (track.previewUrl != null) VinylPalette.TealAccent else VinylPalette.TextMuted,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.trackName, color = VinylPalette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(track.artistName, color = VinylPalette.TextMuted, fontSize = 13.sp)
        }
        TextButton(onClick = onClear) {
            Text("Change", color = VinylPalette.TealAccent, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LetterSection(state: WriteCardUiState, viewModel: WriteCardViewModel) {
    val lineHeight = 28.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VinylPalette.Cream)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val startY = 46.dp.toPx()
                    val lineSpacing = lineHeight.toPx()
                    var y = startY
                    while (y < size.height) {
                        drawLine(
                            color = VinylPalette.Background.copy(alpha = 0.12f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                        y += lineSpacing
                    }
                }
        ) {
            OutlinedTextField(
                value = state.message,
                onValueChange = viewModel::onMessageChange,
                placeholder = { Text("Write anything you'd like to share…", color = VinylPalette.Background.copy(alpha = 0.4f)) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = VinylPalette.Background,
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = VinylPalette.Background,
                    unfocusedTextColor = VinylPalette.Background,
                    cursorColor = VinylPalette.Background,
                ),
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(
            text = "${state.messageCharsRemaining} characters left",
            color = if (state.messageCharsRemaining < 20) Color(0xFFE08787) else VinylPalette.Background.copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.End).padding(top = 6.dp),
        )
    }
}

@Composable
private fun MoodGrid(state: WriteCardUiState, viewModel: WriteCardViewModel) {
    Column {
        Text("MOOD", color = VinylPalette.TextMuted, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))

        val rows = MoodOptions.all.chunked(2)
        rows.forEach { pair ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                pair.forEach { option ->
                    MoodCard(
                        title = option.title,
                        subtitle = option.subtitle,
                        selected = state.mood == option.tag,
                        onClick = { viewModel.onMoodSelected(option.tag) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MoodCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) VinylPalette.TealAccent.copy(alpha = 0.15f) else VinylPalette.PanelDark)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) VinylPalette.TealAccent else VinylPalette.TextMuted.copy(alpha = 0.2f),
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(title, color = VinylPalette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(subtitle, color = VinylPalette.TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun GenreChips(state: WriteCardUiState, viewModel: WriteCardViewModel) {
    Column {
        Text("GENRE · optional", color = VinylPalette.TextMuted, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRowChips(
            items = GenreOptions.all,
            selected = state.selectedGenres,
            onToggle = viewModel::onGenreToggled,
        )
    }
}

@Composable
private fun FlowRowChips(items: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    val rows = remember(items) {
        val chunks = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        var lineLen = 0
        items.forEach { genre ->
            val approxLen = genre.length + 3
            if (lineLen + approxLen > 30 && current.isNotEmpty()) {
                chunks.add(current); current = mutableListOf(); lineLen = 0
            }
            current.add(genre); lineLen += approxLen
        }
        if (current.isNotEmpty()) chunks.add(current)
        chunks
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { genre ->
                    val isSelected = genre in selected
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) VinylPalette.TealAccent else VinylPalette.PanelDark)
                            .border(1.dp, VinylPalette.TextMuted.copy(alpha = 0.2f), RoundedCornerShape(50))
                            .clickable(onClick = { onToggle(genre) })
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            genre,
                            color = if (isSelected) VinylPalette.Background else VinylPalette.TextPrimary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvelopeStylePicker(state: WriteCardUiState, viewModel: WriteCardViewModel) {
    Column {
        Text("ENVELOPE STYLE", color = VinylPalette.TextMuted, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EnvelopeStyle.entries.forEach { style ->
                val selected = state.envelopeStyle == style
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
                        .clickable { viewModel.onEnvelopeStyleSelected(style) }
                )
            }
        }
    }
}

@Composable
private fun LocationToggle(state: WriteCardUiState, viewModel: WriteCardViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Attach my location", color = VinylPalette.TextPrimary, fontSize = 14.sp)
        Switch(
            checked = state.attachLocation,
            onCheckedChange = viewModel::onAttachLocationToggled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VinylPalette.TealAccent,
                checkedTrackColor = VinylPalette.TealAccent.copy(alpha = 0.4f),
                uncheckedThumbColor = Color.Black,
                uncheckedTrackColor = Color.Black.copy(alpha = 0.3f),
            ),
        )
    }
}

@Composable
private fun SendBar(state: WriteCardUiState, onSend: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(VinylPalette.Background)
            .padding(20.dp)
    ) {
        val active = state.canSubmit && !state.isSubmitting
        OutlinedButton(
            onClick = onSend,
            enabled = active,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.5.dp, if (active) VinylPalette.Cream else VinylPalette.TextMuted.copy(alpha = 0.3f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = VinylPalette.Cream,
                disabledContentColor = VinylPalette.TextMuted,
            ),
        ) {
            Text(if (state.isSubmitting) "Sending…" else "Send this record", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun vinylTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = VinylPalette.Background,
    unfocusedContainerColor = VinylPalette.Background,
    focusedBorderColor = VinylPalette.Cream,
    unfocusedBorderColor = VinylPalette.Cream.copy(alpha = 0.4f),
    focusedTextColor = VinylPalette.TextPrimary,
    unfocusedTextColor = VinylPalette.TextPrimary,
    cursorColor = VinylPalette.Cream,
)

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
            text = "LETTER CARD · ANONYMOUS",
            color = VinylPalette.TextMuted,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )

        VinylPreviewDisc(trackName = track.trackName)

        // --- The letter card — cream, dark text ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(VinylPalette.Cream)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(track.trackName, color = cardTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text(track.artistName, color = cardTextMuted, fontSize = 14.sp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VinylPalette.Background.copy(alpha = 0.05f))
                    .drawBehind {
                        val startY = 30.dp.toPx()
                        val lineSpacing = 26.dp.toPx()
                        var y = startY
                        while (y < size.height) {
                            drawLine(
                                color = cardTextPrimary.copy(alpha = 0.12f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx(),
                            )
                            y += lineSpacing
                        }
                    }
                    .padding(16.dp)
            ) {
                Text(
                    text = state.message,
                    color = cardTextPrimary,
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                )
            }

            moodOption?.let {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(VinylPalette.TealAccent)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(it.title.uppercase(), color = VinylPalette.Background, fontSize = 10.sp, letterSpacing = 1.sp)
                }
            }

            if (state.selectedGenres.isNotEmpty()) {
                Column {
                    Text("GENRE", color = cardTextMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.selectedGenres.take(4).forEach { genre ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(VinylPalette.Background.copy(alpha = 0.06f))
                                    .border(1.dp, cardTextMuted.copy(alpha = 0.3f), RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(genre, color = cardTextPrimary, fontSize = 11.sp)
                            }
                        }
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

        EnvelopePreview(style = state.envelopeStyle)

        Text(
            text = "This is what the person who receives it will see. Reactions stay anonymous — you'll only see that someone listened.",
            color = VinylPalette.TextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun EnvelopePreview(style: EnvelopeStyle, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.7f)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(style.colors))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val flap = ComposePath().apply {
                moveTo(0f, 0f)
                lineTo(w / 2f, h * 0.55f)
                lineTo(w, 0f)
                close()
            }
            drawPath(flap, color = Color.Black.copy(alpha = 0.16f))
            drawPath(flap, color = Color.White.copy(alpha = 0.15f), style = Stroke(width = 1.5.dp.toPx()))
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

@Composable
private fun VinylPreviewDisc(trackName: String) {
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
            drawCircle(color = Color.White, radius = radius * 0.32f, center = center)
            drawCircle(color = Color.Black, radius = 5.dp.toPx(), center = center)

            val textRadius = radius * 0.88f
            val path = android.graphics.Path().apply {
                addCircle(center.x, center.y, textRadius, android.graphics.Path.Direction.CW)
            }
            val paint = Paint().apply {
                color = android.graphics.Color.WHITE
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

@androidx.compose.ui.tooling.preview.Preview(
    showBackground = true,
    backgroundColor = 0xFF0D0D0D,
    widthDp = 393,
    heightDp = 852,
)
@Composable
private fun WriteCardScreenPreview() {
    WriteCardScreen()
}

@androidx.compose.ui.tooling.preview.Preview(
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