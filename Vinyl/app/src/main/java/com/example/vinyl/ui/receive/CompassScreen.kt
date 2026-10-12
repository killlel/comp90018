package com.example.vinyl.ui.receive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
    /** The minor tick marks - decorative, not informational, so they can stay faint without
     *  hurting anyone's ability to read the compass. */
    val Ring get() = VinylColors.Teal.copy(alpha = 0.45f)
    /** The N/E/S/W letters, the degree numbers and the main ring: this is the information, so it
     *  has to be legible, not just present. */
    val CardinalLabel get() = TealArrow
    /** The record's centre label sticker. Cream against the charcoal record, so the two discs read
     *  as distinct circles. Purely decorative. */
    val RecordLabel = VinylColors.Cream
    /** The spindle hole at the centre of the record: the darkest tone in the palette. */
    val SpindleHole = VinylColors.Ink
}

data class CompassUiState(
    val distanceLabel: String?,
    val cityLabel: String?,
)

/**
 * Which way the sender's record arrived from, relative to however the reader is holding their
 * phone. The heading comes from the rotation vector sensor, or from the accelerometer and
 * magnetometer on a device without one (see CompassDetector and CompassAlgorithm).
 *
 * [Loading] is the brief window before the first sensor reading arrives. [Unavailable] is the
 * permanent state on a device with neither sensor setup. It is reported straight away, so the
 * screen never has to guess from a timeout whether it is still loading.
 */
sealed interface CompassState {
    data object Loading : CompassState
    data object Unavailable : CompassState

    /**
     * [deviceHeadingDegrees] is the phone's smoothed compass heading (0..360, true north, after
     * the declination correction). [targetBearingDegrees] is the fixed true-north bearing to the
     * sender's city (from Distance.bearingOrNull).
     *
     * They are kept as two numbers because the dial needs both: the rose (ring, ticks and
     * N/E/S/W) turns by [deviceHeadingDegrees] alone, so "N" stays on true north as the phone
     * turns, while [arrowRotationDegrees] combines the two into how far the sender's bearing is
     * from straight ahead.
     */
    data class Available(
        val deviceHeadingDegrees: Float,
        val targetBearingDegrees: Float,
    ) : CompassState {
        val arrowRotationDegrees: Float
            get() = CompassAlgorithm.arrowRotationDegrees(deviceHeadingDegrees, targetBearingDegrees)
    }
}

/**
 * "See where it's from": how far away the sender is, their city, and a compass whose needle points
 * towards it. Turning the phone until the needle lines up with the mark at the top faces the
 * sender's city; the phone buzzes once as it locks on.
 *
 * Full screen with its own back arrow, not a bottom sheet.
 */
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

        // From the target bearing, not the arrow's screen rotation: "they're SE of you" is a fact
        // about the real world, whichever way the phone is held.
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

        // One buzz as the arrow crosses into "facing", not a continuous buzz while it stays there.
        val context = LocalContext.current
        LaunchedEffect(isFacingSender) {
            if (isFacingSender) context.vibrateOnce()
        }

        // Live countdown: the number shrinks as you turn the right way, and "lined up" matches
        // "You found them!" below. Shown only once there is an angle to count down from.
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
                isFacingSender && targetDirectionLabel != null -> buildAnnotatedString {
                    append("You found them! They're ")
                    withStyle(SpanStyle(color = CompassColors.TealArrow, fontWeight = FontWeight.Bold)) {
                        append(targetDirectionLabel)
                    }
                    append(" of you.")
                }
                isFacingSender -> buildAnnotatedString { append("You found them!") }
                else -> buildAnnotatedString { append("Rotate your phone") }
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

/**
 * A single N/E/S/W label: the letter plus its bearing in degrees.
 *
 * [sideBySide] draws the letter and degrees in a row (wide, short), used for N and S, where the
 * label's height has to clear the narrow gap between the ring and the inner dial. Otherwise they
 * are stacked (narrow, tall), used for E and W, where the width matters instead.
 * [scale] keeps the letters in proportion as the dial grows or shrinks with screen width (see
 * [CompassDial]'s own `scale`).
 */
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
 * A rotating compass rose: the ring, ticks and N/E/S/W labels turn together as
 * [deviceHeadingDegrees] changes, so "N" always sits at the screen angle true north is at. Turn
 * the phone 90 degrees and the rose turns 90 degrees the other way under it, like a real compass.
 *
 * The dial background (the "record") and the centre dot stay fixed; only the rose and the arrow
 * move. Each has its own short tween, so a jump in the heading reads as a smooth turn. The
 * smoothing in CompassAlgorithm removes most of the jitter; this softens what is left.
 */
@Composable
private fun CompassDial(
    deviceHeadingDegrees: Float,
    targetBearingDegrees: Float,
    modifier: Modifier = Modifier,
    dialSize: Dp = 320.dp,
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

        // The rose itself (ring and ticks), turned as one piece by animatedRoseRotation.
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

        // The mark at the top. It does not turn with the rose: it is the direction the phone is
        // facing, which is straight up on screen by definition.
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

        // The arrow: a compass-needle silhouette, pointed at both ends (the tip at the ring and
        // the base at the centre).
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

            // A thin pale spine down the centre, like the highlight on a metal needle, so it reads
            // as a faceted object rather than a flat silhouette.
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
        // Phone facing true north, sender 15 degrees east of that: the rose is un-rotated and the
        // arrow sits 15 degrees from the mark.
        compassState = CompassState.Available(deviceHeadingDegrees = 0f, targetBearingDegrees = 15f),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393, heightDp = 852)
@Composable
private fun CompassScreenRotatedPreview() {
    // Phone turned 130 degrees off north, sender at a fixed 55 degrees: the rose has turned (N is
    // no longer at the top) independently of the arrow, which shows how far straight ahead is
    // from the sender.
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