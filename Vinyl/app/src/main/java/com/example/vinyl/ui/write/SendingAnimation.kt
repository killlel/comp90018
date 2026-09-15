package com.example.vinyl.ui.write

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.vinyl.data.Track
import com.example.vinyl.ui.theme.VinylPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Plays once, from a frozen snapshot of the card's data, when the user taps "Send this record":
 * the letter card (folded in half) slides down first since it's closest to the envelope, then —
 * only once the card has fully finished, not overlapping it — the record follows behind it. Both
 * are modest, fixed-size elements clustered close to the open envelope so travel distances are
 * small and predictable. Then the flap closes and seals with a stamp, and finally the whole
 * envelope travels up to the top of the screen and fades away.
 *
 * Layering is controlled explicitly with zIndex rather than declaration order: the record sits
 * lowest, the card in front of it, and the envelope in front of both — so the card is always in
 * front of the record, and both visibly disappear behind the envelope once they reach it.
 */
@Composable
internal fun SendingAnimation(
    state: WriteCardUiState,
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val track = state.selectedTrack ?: return

    // Card: folds in half first, then slides/shrinks modestly down into the envelope
    val cardFoldProgress = remember { Animatable(0f) }
    val cardOffsetY = remember { Animatable(0f) }
    val cardScale = remember { Animatable(1f) }
    val cardAlpha = remember { Animatable(1f) }

    // Record: full size throughout — only slides and fades, doesn't shrink
    val discOffsetY = remember { Animatable(0f) }
    val discAlpha = remember { Animatable(1f) }

    // Envelope: flapProgress 0 = open (starting state), 1 = sealed shut
    val envelopeFlapProgress = remember { Animatable(0f) }
    val envelopeScale = remember { Animatable(1f) }
    val envelopeOffsetY = remember { Animatable(0f) }
    val envelopeAlpha = remember { Animatable(1f) }

    var showEnvelope by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Envelope starts open — AnimatedEnvelope just renders flapProgress = 0, nothing to animate yet

        // 1. The letter card folds in half — since it's closest to the envelope, it enters first
        cardFoldProgress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))

        delay(80.milliseconds)

        launch { cardScale.animateTo(0.6f, tween(500, easing = FastOutSlowInEasing)) }
        launch { cardAlpha.animateTo(0f, tween(300, delayMillis = 350)) }
        cardOffsetY.animateTo(280f, tween(500, easing = FastOutSlowInEasing))

        // Let the card clearly finish its entrance before the record starts — no overlap
        delay(250.milliseconds)

        // 2. Then the record slides down and follows it in — no shrink, just slides and fades
        launch { discAlpha.animateTo(0f, tween(300, delayMillis = 350)) }
        discOffsetY.animateTo(260f, tween(500, easing = FastOutSlowInEasing))

        delay(300.milliseconds)

        // 3. The flap closes, hiding whatever's left behind it...
        envelopeFlapProgress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))

        // ...then a quick pulse confirms it's sealed (the music-note badge inside AnimatedEnvelope
        // appears automatically once flapProgress reaches 1, and pulses along with this scale)
        envelopeScale.animateTo(1.08f, tween(160))
        envelopeScale.animateTo(1f, tween(160))

        delay(400.milliseconds)

        // 4. ...and it travels up to the top of the screen, disappearing as it's sent
        launch { envelopeOffsetY.animateTo(-900f, tween(700, easing = FastOutSlowInEasing)) }
        envelopeAlpha.animateTo(0f, tween(600, delayMillis = 150))
        showEnvelope = false

        delay(150.milliseconds)
        onFinished()
    }

    val cardWidth = 170.dp
    val discWidth = 320.dp
    val envelopeWidth = 340.dp

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Record — full size, same as it renders in the normal preview. Only its position and
        // opacity animate, not its scale. zIndex(1f): behind the card, and will end up behind
        // the envelope too once it slides down far enough to reach it.
        Box(
            modifier = Modifier
                .width(discWidth)
                .offset(y = discOffsetY.value.dp)
                .alpha(discAlpha.value)
                .zIndex(1f),
        ) {
            VinylPreviewDisc(trackName = track.trackName, envelopeStyle = state.envelopeStyle)
        }

        // Card — the one element that's deliberately smaller, and rests just above the record
        // (a natural stacked-letter look). zIndex(2f) keeps it in front of the record.
        Box(
            modifier = Modifier
                .width(cardWidth)
                .offset(y = (-60 + cardOffsetY.value).dp)
                .graphicsLayer {
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                    alpha = cardAlpha.value
                }
                .zIndex(2f),
        ) {
            FoldingLetterCard(track = track, foldProgress = cardFoldProgress.value)
        }

        // Envelope — also full size. Only its position/opacity animate for the fly-away; the
        // brief scale bump is just the momentary "seal" pulse, not a size change. zIndex(3f)
        // keeps it in front of both, so they visibly disappear behind it.
        if (showEnvelope) {
            AnimatedEnvelope(
                style = state.envelopeStyle,
                flapProgress = envelopeFlapProgress.value,
                modifier = Modifier
                    .width(envelopeWidth)
                    .offset(y = (260 + envelopeOffsetY.value).dp)
                    .scale(envelopeScale.value)
                    .alpha(envelopeAlpha.value)
                    .zIndex(3f),
            )
        }

        Text(
            text = "Sending your record…",
            color = VinylPalette.TextMuted,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .zIndex(0f),
        )
    }
}

/**
 * A card that shows normally until foldProgress > 0, at which point it splits into a static
 * bottom half and a top half that hinges shut over it (rotationX around the shared edge) —
 * a genuine half-fold rather than a uniform shrink. Content is dropped once folding starts,
 * since real paper folding also obscures what's written on it.
 */
@Composable
private fun FoldingLetterCard(track: Track, foldProgress: Float, modifier: Modifier = Modifier) {
    val cardHeight = 88.dp

    Box(modifier = modifier.fillMaxWidth()) {
        if (foldProgress < 0.02f) {
            MiniLetterCard(track = track)
        } else {
            val halfHeight = cardHeight / 2

            // Bottom half stays put
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(halfHeight)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(VinylPalette.Cream)
            )

            // Top half hinges down over the bottom half
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(halfHeight)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        rotationX = -180f * foldProgress
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        cameraDistance = 12f * density
                    }
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(VinylPalette.Cream)
            )
        }
    }
}

@Composable
private fun MiniLetterCard(track: Track) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VinylPalette.Cream)
            .padding(16.dp),
    ) {
        Text(track.trackName, color = VinylPalette.Background, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(track.artistName, color = VinylPalette.Background.copy(alpha = 0.6f), fontSize = 13.sp)
    }
}

/**
 * A proper open-envelope silhouette: an upward-pointing triangle (the open flap) sitting flush
 * on top of a rectangle (the pocket/base) — like the standard ✉ icon — that crossfades into the
 * usual sealed look (shadow triangle folded down inside the rectangle, motif, stamp) as
 * flapProgress goes from 0 (open) to 1 (sealed). Distinct from the static EnvelopePreview used
 * elsewhere, which is always sealed.
 */
@Composable
private fun AnimatedEnvelope(style: EnvelopeStyle, flapProgress: Float, modifier: Modifier = Modifier) {
    val gradient = Brush.linearGradient(style.colors)

    Column(modifier = modifier.fillMaxWidth()) {
        // Open flap: a triangle flush against the top of the rectangle below, fading out as it seals
        if (flapProgress < 0.999f) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3.2f)
                    .alpha(1f - flapProgress),
            ) {
                val w = size.width
                val h = size.height
                val openFlap = ComposePath().apply {
                    moveTo(0f, h)
                    lineTo(w / 2f, 0f)
                    lineTo(w, h)
                    close()
                }
                drawPath(openFlap, brush = gradient)
                drawPath(openFlap, color = Color.Black.copy(alpha = 0.15f), style = Stroke(width = 1.5.dp.toPx()))
            }
        }

        // Rectangle pocket/base — always present
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.1f)
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp, topStart = 4.dp, topEnd = 4.dp))
                .background(gradient),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                if (flapProgress > 0.05f) {
                    when (style.motif) {
                        EnvelopeMotif.MOON -> drawMoonMotif(w, h)
                        EnvelopeMotif.HEARTS -> drawHeartsMotif(w, h)
                        EnvelopeMotif.NONE -> Unit
                    }
                }

                if (flapProgress > 0f) {
                    // Closed flap shadow, folded down inside the rectangle — fades in as it seals
                    val closedFlap = ComposePath().apply {
                        moveTo(0f, 0f)
                        lineTo(w / 2f, h * 0.7f)
                        lineTo(w, 0f)
                        close()
                    }
                    drawPath(closedFlap, color = Color.Black.copy(alpha = 0.16f * flapProgress))
                    drawPath(
                        closedFlap,
                        color = Color.White.copy(alpha = 0.15f * flapProgress),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }

            if (flapProgress >= 0.999f) {
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
            }

            Text(
                text = if (flapProgress >= 0.999f) "${style.label.uppercase()} · SEALED" else "OPEN",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            )
        }
    }
}