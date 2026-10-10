package com.example.vinyl.ui.receive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.haptics.vibrateOnce
import com.example.vinyl.sensor.CompassAlgorithm
import com.example.vinyl.ui.theme.VinylColors
import com.example.vinyl.ui.theme.VinylPalette
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private object CompassColors {
    val DialBackground = VinylColors.Charcoal
    val TealArrow get() = VinylColors.Teal
    /** The ring itself only - decorative, not informational, so it can stay faint without
     *  hurting anyone's ability to read the compass. */
    val Ring get() = VinylColors.Teal.copy(alpha = 0.45f)
    /** The N/E/S/W letters and the degree numbers - these ARE the information, so they need to
     *  be genuinely legible, not just present. */
    val CardinalLabel get() = TealArrow
    /** The record's centre "label" sticker - a step lighter than Charcoal so the two discs read
     *  as distinct circles rather than one flat shape. Purely decorative. */
    val RecordLabel = VinylColors.Cream
    /** The spindle hole at the very centre of the record - the darkest tone in the palette. */
    val SpindleHole = VinylColors.Ink
}

data class CompassUiState(
    val distanceLabel: String?,
    val cityLabel: String?,
)

/**
 * Which way the sender's record arrived from, relative to however the reader is currently
 * holding their phone. Backed by the magnetometer (see CompassDetector / CompassAlgorithm);
 * Loading is the brief window before the first sensor reading arrives, Unavailable is the
 * permanent state on a device with no compass-capable sensor - this is NOT the same as Loading
 * taking a long time, it is reported synchronously, so the screen never has to guess which one
 * it is from a timeout.
 */
sealed interface CompassState {
    data object Loading : CompassState
    data object Unavailable : CompassState

    /**
     * [deviceHeadingDegrees] is the phone's own smoothed compass heading (0..360, true north);
     * [targetBearingDegrees] is the fixed, never-changing true-north bearing to the sender (from
     * Distance.bearingOrNull). Kept as two separate numbers, not one pre-combined angle, because
     * the dial now needs both independently: the whole rose (ring/ticks/N-E-S-W) spins by
     * [deviceHeadingDegrees] alone, so "N" always points at true north as the phone turns, while
     * [arrowRotationDegrees] below (unchanged, for anything already reading it) is the two
     * combined - how far the sender's bearing sits from "straight ahead" right now.
     */
    data class Available(
        val deviceHeadingDegrees: Float,
        val targetBearingDegrees: Float,
    ) : CompassState {
        val arrowRotationDegrees: Float
            get() = CompassAlgorithm.arrowRotationDegrees(deviceHeadingDegrees, targetBearingDegrees)
    }
}

@Composable
fun CompassScreen(
    state: CompassUiState,
    compassState: CompassState,
    onBack: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylPalette.Background)
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = VinylPalette.TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Music card from", color = VinylPalette.TextMuted, fontSize = 17.sp)
        Text(
            text = (state.distanceLabel ?: "an unknown distance") + " away",
            color = VinylPalette.TextPrimary,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
        )
        state.cityLabel?.let {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = VinylPalette.TextMuted,
                    modifier = Modifier.size(15.dp),
                )
                Text(it, color = VinylPalette.TextMuted, fontSize = 17.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }

        val isFacingSender = compassState is CompassState.Available &&
                CompassAlgorithm.isFacingTarget(compassState.arrowRotationDegrees)

        // From the TARGET bearing, not the arrow's screen rotation - "they're SE of you" is a
        // fact about the real world, unaffected by which way the phone happens to be held right
        // now.
        val targetDirectionLabel = (compassState as? CompassState.Available)
            ?.let { CompassAlgorithm.compassPointLabel(it.targetBearingDegrees) }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            CompassColors.TealArrow.copy(alpha = if (isFacingSender) 0.22f else 0.10f),
                            Color.Transparent,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val dialSize = minOf(maxWidth * 0.98f, 360.dp)
                when (compassState) {
                    CompassState.Loading -> CircularProgressIndicator(color = CompassColors.TealArrow)
                    CompassState.Unavailable -> UnavailableNotice()
                    is CompassState.Available ->
                        CompassDial(
                            deviceHeadingDegrees = compassState.deviceHeadingDegrees,
                            targetBearingDegrees = compassState.targetBearingDegrees,
                            dialSize = dialSize,
                        )
                }
            }
        }

        // One buzz on the moment of crossing INTO facing, not a continuous buzz while held there
        val context = LocalContext.current
        LaunchedEffect(isFacingSender) {
            if (isFacingSender) context.vibrateOnce()
        }

        // Live countdown: needs no compass knowledge at all to read - the number just gets
        // smaller as you turn the right way, and "0 to go" lines up with "You found them!"
        // appearing above. Shown only once there's an actual angle to count down from.
        if (compassState is CompassState.Available) {
            val degreesToGo = CompassAlgorithm.degreesFromFacingTarget(compassState.arrowRotationDegrees).roundToInt()
            Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.Center) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(VinylColors.Surface.copy(alpha = 0.55f))
                        .border(
                            width = 1.dp,
                            color = CompassColors.TealArrow.copy(alpha = if (isFacingSender) 0.5f else 0.2f),
                            shape = RoundedCornerShape(50),
                        )
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = "$degreesToGo°",
                        color = CompassColors.CardinalLabel,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (isFacingSender) " lined up" else " to go",
                        color = VinylPalette.TextMuted,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.6f))

        Text(
            text = when {
                isFacingSender && targetDirectionLabel != null -> "You found them! They're $targetDirectionLabel of you."
                isFacingSender -> "You found them!"
                else -> "Rotate your phone"
            },
            color = VinylPalette.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = when {
                compassState == CompassState.Unavailable ->
                    "This device has no compass sensor, so the arrow can't be shown."
                isFacingSender ->
                    "You're facing the direction this music card travelled from."
                else ->
                    "Turn until the arrow lines up with the mark at the top. It points to their city, not their exact spot."
            },
            color = VinylPalette.TextMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp),
        )
    }
}

/** A single N/E/S/W label: the letter plus its bearing in degrees.
 *  [sideBySide] = true draws them in a row (wide, short) - used for N/S, where the label's
 *  HEIGHT is what has to clear the narrow gap between the ring and the inner dial. false draws
 *  them stacked (narrow, tall) - used for E/W, where it's the label's WIDTH that matters instead.
 *  [scale] keeps the letters in proportion as the whole dial grows or shrinks with screen width -
 *  see [CompassDial]'s own `scale`. */
@Composable
private fun CardinalMark(label: String, degrees: Int, sideBySide: Boolean, scale: Float = 1f) {
    val letter: @Composable () -> Unit = {
        Text(
            label,
            color = CompassColors.CardinalLabel,
            fontSize = 21.sp * scale,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )
    }
    val degreeReadout: @Composable () -> Unit = {
        Text(
            "$degrees°",
            color = CompassColors.CardinalLabel,
            fontSize = 11.sp * scale,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp,
        )
    }
    if (sideBySide) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            letter()
            Spacer(modifier = Modifier.width(3.dp))
            degreeReadout()
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            letter()
            degreeReadout()
        }
    }
}

/**
 * A REAL rotating compass rose now, not static dial art: the ring, ticks and N/E/S/W labels all
 * spin together as [deviceHeadingDegrees] changes, so "N" always sits at whichever screen angle
 * true north is actually at - turn the phone 90 degrees and the whole rose visibly turns 90
 * degrees the other way under it, exactly like a real compass. The dial background (the "record")
 * and centre dot stay fixed; only the rose and the arrow move, each animated with its own short
 * tween so a jump from the compass filter reads as a smooth turn, not a snap - the smoothing in
 * CompassAlgorithm already removes most of the raw jitter, this is just softening what's left of
 * the frame-to-frame motion for the screen itself.
 */
@Composable
private fun CompassDial(
    deviceHeadingDegrees: Float,
    targetBearingDegrees: Float,
    dialSize: Dp = 320.dp,
    modifier: Modifier = Modifier,
) {
    val arrowRotationDegrees = CompassAlgorithm.arrowRotationDegrees(deviceHeadingDegrees, targetBearingDegrees)
    val animatedRotation = remember { Animatable(arrowRotationDegrees) }
    LaunchedEffect(arrowRotationDegrees) {
        // The short way around, e.g. 359 -> 2 animates as +3, not -357.
        val current = animatedRotation.value
        val delta = ((arrowRotationDegrees - current + 540) % 360) - 180
        animatedRotation.animateTo(current + delta, animationSpec = tween(durationMillis = 150))
    }

    val roseRotationDegrees = CompassAlgorithm.normalizeDegrees(-deviceHeadingDegrees)
    val animatedRoseRotation = remember { Animatable(roseRotationDegrees) }
    LaunchedEffect(roseRotationDegrees) {
        val current = animatedRoseRotation.value
        val delta = ((roseRotationDegrees - current + 540) % 360) - 180
        animatedRoseRotation.animateTo(current + delta, animationSpec = tween(durationMillis = 150))
    }

    val scale = dialSize.value / 320f
    Box(modifier = modifier.size(dialSize), contentAlignment = Alignment.Center) {

        val ringRadiusDp = 95.dp * scale

        Box(
            modifier = Modifier
                .size(ringRadiusDp * 2)
                .background(CompassColors.DialBackground, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp * scale)
                    .background(CompassColors.RecordLabel, shape = CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(10.dp * scale)
                    .background(CompassColors.SpindleHole, shape = CircleShape),
            )
        }

        // The rose itself - ring + ticks - rotated as one rigid piece by animatedRoseRotation..
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(animatedRoseRotation.value),
        ) {
            val tickStrokeWidth = (1.5f * scale).dp.toPx()
            val ringStrokeWidth = (2f * scale).dp.toPx()
            val cx = size.width / 2
            val cy = size.height / 2
            val center = Offset(cx, cy)
            val ringRadius = ringRadiusDp.toPx()

            drawCircle(color = CompassColors.TealArrow, radius = ringRadius, center = center, style = Stroke(width = ringStrokeWidth))

            fun pointAt(bearingDegrees: Int, fromRadius: Float): Offset {
                val rad = Math.toRadians(bearingDegrees.toDouble())
                return Offset(
                    x = cx + fromRadius * sin(rad).toFloat(),
                    y = cy - fromRadius * cos(rad).toFloat(),
                )
            }

            for (degrees in 0 until 360 step 15) {
                val isCardinal = degrees % 90 == 0
                val isIntercardinal = degrees % 45 == 0
                val tickLength = when {
                    isCardinal -> (18f * scale).dp.toPx()
                    isIntercardinal -> (10f * scale).dp.toPx()
                    else -> (6f * scale).dp.toPx()
                }
                drawLine(
                    color = if (isCardinal) CompassColors.CardinalLabel else CompassColors.Ring,
                    start = pointAt(degrees, ringRadius),
                    end = pointAt(degrees, ringRadius - tickLength),
                    strokeWidth = if (isCardinal) (2.5f * scale).dp.toPx() else tickStrokeWidth,
                )
            }
        }

        // FIXED - does NOT rotate with the rose, unlike everything above. This is "the direction
        // your phone is currently facing", always straight up on screen by definition.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val ringRadius = ringRadiusDp.toPx()
            val markerLength = (12f * scale).dp.toPx()
            drawLine(
                color = CompassColors.CardinalLabel,
                start = Offset(cx, cy - ringRadius),
                end = Offset(cx, cy - ringRadius - markerLength),
                strokeWidth = (3f * scale).dp.toPx(),
            )
        }

        val labelRadius = 132.dp * scale
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(animatedRoseRotation.value),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.offset(y = -labelRadius).rotate(-animatedRoseRotation.value)) {
                CardinalMark("N", 0, sideBySide = true, scale = scale)
            }
            Box(modifier = Modifier.offset(x = labelRadius).rotate(-animatedRoseRotation.value)) {
                CardinalMark("E", 90, sideBySide = false, scale = scale)
            }
            Box(modifier = Modifier.offset(y = labelRadius).rotate(-animatedRoseRotation.value)) {
                CardinalMark("S", 180, sideBySide = true, scale = scale)
            }
            Box(modifier = Modifier.offset(x = -labelRadius).rotate(-animatedRoseRotation.value)) {
                CardinalMark("W", 270, sideBySide = false, scale = scale)
            }
        }

        // The arrow: a classic compass needle silhouette - pointed at BOTH ends (the tip at the
        // ring, and the base back at the centre)
        Canvas(
            modifier = Modifier
                .size(ringRadiusDp * 2)
                .rotate(animatedRotation.value),
        ) {
            val cx = size.width / 2
            val cy = size.height / 2
            val tip = Offset(cx, 0f)
            val base = Offset(cx, cy)
            val widestY = (30f * scale).dp.toPx()
            val halfWidth = (9f * scale).dp.toPx()

            val needle = androidx.compose.ui.graphics.Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(cx + halfWidth, widestY)
                lineTo(base.x, base.y)
                lineTo(cx - halfWidth, widestY)
                close()
            }
            drawPath(needle, color = CompassColors.TealArrow)

            // A thin pale spine down the centre - the light-catching highlight a real metal
            // needle has along its length, so it reads as a faceted object rather than a flat
            // teal silhouette.
            drawLine(
                color = Color.White.copy(alpha = 0.28f),
                start = Offset(cx, tip.y + (4f * scale).dp.toPx()),
                end = Offset(cx, base.y - (6f * scale).dp.toPx()),
                strokeWidth = (1.2f * scale).dp.toPx(),
            )
        }
    }
}

@Composable
private fun UnavailableNotice() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .background(CompassColors.DialBackground, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "?",
                color = CompassColors.CardinalLabel,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393, heightDp = 852)
@Composable
private fun CompassScreenAvailablePreview() {
    CompassScreen(
        state = CompassUiState(distanceLabel = "3000+ km", cityLabel = "Chicago, US"),
        // Phone held facing true north (0), sender bearing 15 degrees east of that - same
        // effective arrow angle (15) as the old single-number preview, rose un-rotated.
        compassState = CompassState.Available(deviceHeadingDegrees = 0f, targetBearingDegrees = 15f),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393, heightDp = 852)
@Composable
private fun CompassScreenRotatedPreview() {
    // Phone turned 130 degrees off north, sender bearing a fixed 55 degrees - demonstrates the
    // rose actually having turned (N is no longer at the top), independently of the arrow, which
    // still just shows how far "straight ahead" is from the sender (unchanged math).
    CompassScreen(
        state = CompassUiState(distanceLabel = "3000+ km", cityLabel = "Chicago, US"),
        compassState = CompassState.Available(deviceHeadingDegrees = 130f, targetBearingDegrees = 55f),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393, heightDp = 852)
@Composable
private fun CompassScreenUnavailablePreview() {
    CompassScreen(
        state = CompassUiState(distanceLabel = "3000+ km", cityLabel = "Chicago, US"),
        compassState = CompassState.Unavailable,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393, heightDp = 852)
@Composable
private fun CompassScreenLoadingPreview() {
    CompassScreen(
        state = CompassUiState(distanceLabel = "3000+ km", cityLabel = "Chicago, US"),
        compassState = CompassState.Loading,
    )
}