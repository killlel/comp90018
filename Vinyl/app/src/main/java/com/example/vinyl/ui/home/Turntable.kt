package com.example.vinyl.ui.home

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.vinyl.R
import com.example.vinyl.data.THUMB_ARTWORK_PX
import com.example.vinyl.data.itunesArtworkAt
import com.example.vinyl.ui.theme.VinylPalette
import kotlinx.coroutines.delay

/**
 * The record player on the home screen.
 *
 * Four layers, because each one moves differently:
 *
 *     tonearm   parked beside the platter; swings in and lands when music plays
 *     record    only there while music plays: lowered on, turns once the needle
 *               lands, coasts to rest while paused, lifted off when it stops
 *     platter   static
 *     plinth    static
 *
 * The art is a photographic 3/4 view, but the only usable record export is a flat top-down disc —
 * the perspective ones have the record baked into the platter and so cannot turn. The record is
 * therefore **rotated first and squashed second**: the inner layer spins as a true circle, the
 * outer layer flattens the result into the platter's ellipse. Squashing first would leave a
 * rotating ellipse, which wobbles like a dropped coin instead of spinning flat.
 *
 * The arm's motion is a horizontal sweep rather than the vertical cue-lever lift a real deck uses.
 * A lift is almost invisible at this camera angle on a phone; a sweep reads instantly, and it is
 * the movement the haptic lands against.
 *
 * The fractions at the foot of this file place each layer. They are measured off the exports, but
 * by eye — expect to nudge them against the preview, and re-check after any re-export.
 */
@Composable
fun Turntable(
    modifier: Modifier = Modifier,
    /** Music is playing. False keeps the record still and the arm parked beside it. */
    playing: Boolean = false,
    /** The song is held. The record stays on with the needle in it, but stops turning. */
    paused: Boolean = false,
    /** Painted onto the record label. Falls back to the accent colour when absent. */
    labelArtworkUrl: String? = null,
    /** Tapping the deck while it plays pauses or resumes the song. Null leaves the deck inert. */
    onTogglePause: (() -> Unit)? = null,
    /**
     * When the song began, on the `SystemClock.elapsedRealtime()` clock. The drop is only played
     * if it would still be under way; a turntable drawn later — after switching tabs and coming
     * back — starts with the needle down and the record already at speed.
     */
    startedAtMillis: Long = 0L,
) {
    val haptics = rememberTurntableHaptics()

    // Decided once, when this turntable first appears. Mid-song it appears in the playing pose;
    // otherwise at rest, so "Play this song" shows the full drop.
    val arrivedMidSong = remember {
        playing && SystemClock.elapsedRealtime() - startedAtMillis >= NEEDLE_LANDS_AFTER_MILLIS
    }
    val arm = remember { Animatable(if (arrivedMidSong) ARM_PLAYING_DEGREES else ARM_PARKED_DEGREES) }
    val spin = remember { Animatable(0f) }
    // 0 = no record on the deck, 1 = sitting on the platter. In between it is being lowered on or
    // lifted off: it rises and fades as it goes.
    val recordOn = remember { Animatable(if (arrivedMidSong) 1f else 0f) }
    var needleDown by remember { mutableStateOf(arrivedMidSong) }

    // The song is cleared the moment it stops, but its record is still on view as it's lifted
    // off; keep its label until then rather than flashing the default.
    var lastPlayingLabel by remember { mutableStateOf(labelArtworkUrl) }
    SideEffect { if (playing) lastPlayingLabel = labelArtworkUrl }
    val shownLabel = if (playing) labelArtworkUrl else lastPlayingLabel

    // Only the very first spin can skip the spin-up; a later replay starts from rest again.
    var skipSpinUp by remember { mutableStateOf(arrivedMidSong) }

    // The order is the point. Playing: the record goes on, the arm swings over and lands, *then*
    // the record turns. Stopping: the record slows as the arm returns, *then* it comes off. A real
    // deck can't spin a record it isn't touching, or lift one the needle is still in.
    LaunchedEffect(playing) {
        if (playing) {
            if (needleDown) return@LaunchedEffect // already down: arrived mid-song
            // A beat for the card sheet to slide away, so the drop isn't hidden behind it —
            // less whatever of it has already passed.
            val sinceStart = SystemClock.elapsedRealtime() - startedAtMillis
            delay((NEEDLE_DROP_DELAY_MILLIS - sinceStart).coerceAtLeast(0L))
            recordOn.animateTo(1f, tween(RECORD_PLACE_MILLIS, easing = FastOutSlowInEasing))
            arm.animateTo(ARM_PLAYING_DEGREES, tween(ARM_SWING_MILLIS, easing = FastOutSlowInEasing))
            haptics.needleDrop()
            needleDown = true
        } else if (needleDown || arm.value != ARM_PARKED_DEGREES || recordOn.value != 0f) {
            val wasDown = needleDown || arm.value != ARM_PARKED_DEGREES
            needleDown = false
            arm.animateTo(ARM_PARKED_DEGREES, tween(ARM_SWING_MILLIS, easing = FastOutSlowInEasing))
            if (wasDown) haptics.needleLift()
            recordOn.animateTo(0f, tween(RECORD_PLACE_MILLIS, easing = FastOutSlowInEasing))
        }
    }

    // A paused record sits still under the needle, like a deck with its motor switched off.
    val turning = needleDown && !paused
    LaunchedEffect(turning) {
        if (turning) {
            // Spin up over the first turn rather than jumping to speed, then hold it. Skipped
            // when the record was already turning before this screen appeared.
            if (!skipSpinUp) {
                spin.animateTo(spin.value + 360f, tween(SPIN_UP_MILLIS, easing = FastOutLinearInEasing))
            }
            skipSpinUp = false
            while (true) {
                spin.snapTo(spin.value % 360f)
                spin.animateTo(spin.value + 360f, tween(REVOLUTION_MILLIS, easing = LinearEasing))
            }
        } else {
            // Arriving mid-song but paused, the record is still; resuming spins it up from rest.
            skipSpinUp = false
            // Coast to a stop instead of freezing mid-turn.
            if (spin.value % 360f != 0f) {
                spin.animateTo(spin.value + COAST_DEGREES, tween(COAST_MILLIS, easing = LinearOutSlowInEasing))
            }
        }
    }

    val tapToPause = if (playing && onTogglePause != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null, // a ripple over a photo of a turntable looks like a glitch
            onClickLabel = if (paused) "Resume" else "Pause",
            onClick = onTogglePause,
        )
    } else {
        Modifier
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            // The plinth export carries clear rows above and below the wood. Reporting only the
            // drawn part keeps the gap to its neighbours the gap the layout asked for. Not
            // clipped, so the arm's counterweight can still rise a little past the top edge.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val top = (placeable.height * PLINTH_CLEAR_TOP).roundToInt()
                val bottom = (placeable.height * PLINTH_CLEAR_BOTTOM).roundToInt()
                layout(placeable.width, placeable.height - top - bottom) {
                    placeable.place(0, -top)
                }
            }
            .then(tapToPause),
    ) {
        val plinthWidth = maxWidth
        val plinthHeight = plinthWidth * PLINTH_HEIGHT_RATIO
        val platterWidth = plinthWidth * PLATTER_WIDTH_FRACTION
        val recordSize = platterWidth * RECORD_WIDTH_FRACTION
        val tonearmWidth = plinthWidth * TONEARM_WIDTH_FRACTION
        val tonearmHeight = tonearmWidth * TONEARM_HEIGHT_RATIO

        Image(
            painter = painterResource(R.drawable.turntable_plinth),
            contentDescription = null,
            // An explicit size, like every other layer. With only a width, Compose caps the
            // height at the export's natural size (1100px, ~367dp wide at 3x) and shrinks the
            // plinth to match, so on a wider phone it sat inset from the platter and arm.
            modifier = Modifier.size(width = plinthWidth, height = plinthHeight),
        )

        // Sized to the export's own aspect. A square box would letterbox the image and push it
        // down by an eighth of its width, off the front of the mat.
        val platterHeight = platterWidth * PLATTER_HEIGHT_RATIO
        Image(
            painter = painterResource(R.drawable.turntable_platter),
            contentDescription = null,
            modifier = Modifier
                .size(width = platterWidth, height = platterHeight)
                .offset(
                    x = plinthWidth * SPINDLE_X - platterWidth / 2,
                    y = plinthHeight * SPINDLE_Y - platterHeight / 2,
                )
                // The platter is shot from a little higher than the plinth; flattening it puts
                // both under one camera. Pivots on the box centre, which is the spindle point.
                .graphicsLayer { scaleY = PLATTER_SQUASH },
        )

        // The record sits on the platter's top face, whose centre is a little above the middle
        // of the export, and shares its flattening.
        val surfaceCentreY = plinthHeight * SPINDLE_Y +
            platterHeight * PLATTER_SQUASH * (PLATTER_SURFACE_CENTRE_Y - 0.5f)
        // How high above the platter the record starts as it's lowered on.
        val recordLift = recordSize * RECORD_LIFT_FRACTION
        Box(
            modifier = Modifier
                .size(recordSize)
                // Centre the square on the platter's face, then flatten. scaleY pivots on the box
                // centre, so the disc squashes around its own spindle.
                .offset(
                    x = plinthWidth * SPINDLE_X - recordSize / 2,
                    y = surfaceCentreY - recordSize / 2,
                )
                // Lowered on and lifted off: drawn higher and fainter the less it's on. Read at
                // draw time, so the move doesn't recompose. Off entirely, it isn't drawn at all.
                .graphicsLayer {
                    val on = recordOn.value
                    alpha = on
                    translationY = -(1f - on) * recordLift.toPx()
                }
                .graphicsLayer { scaleY = PLATTER_SURFACE_ASPECT * PLATTER_SQUASH },
        ) {
            // Held still on purpose: the photo's sheen is lit by the room, so it must not turn with
            // the disc. Grooves are circles and look the same at any angle - the label below is
            // what shows the record spinning.
            Image(
                painter = painterResource(R.drawable.turntable_record),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )

            // The export has a plain white label. Today's artwork goes there when there is any,
            // the accent colour when there isn't. It's the only part that turns.
            val labelSize = recordSize * LABEL_DIAMETER_FRACTION
            Box(
                modifier = Modifier
                    .size(labelSize)
                    .offset(
                        x = recordSize * LABEL_CENTRE_X - labelSize / 2,
                        y = (recordSize - labelSize) / 2,
                    )
                    // Read at draw time, so each frame of the spin redraws without recomposing.
                    .graphicsLayer { rotationZ = spin.value }
                    .clip(CircleShape),
            ) {
                if (shownLabel != null) {
                    AsyncImage(
                        model = itunesArtworkAt(shownLabel, THUMB_ARTWORK_PX),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // A plain disc looks the same at every angle, so a spinning one would look
                    // stopped. The printed wedge is what the eye follows round.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawBehind {
                                drawRect(VinylPalette.TealAccent)
                                drawArc(
                                    color = VinylPalette.Background.copy(alpha = 0.28f),
                                    startAngle = -30f,
                                    sweepAngle = 60f,
                                    useCenter = true,
                                )
                            },
                    )
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawCircle(
                                color = VinylPalette.Background,
                                radius = size.width * SPINDLE_HOLE_OF_LABEL,
                                center = Offset(size.width / 2f, size.height / 2f),
                            )
                        },
                )
            }
        }

        Image(
            painter = painterResource(R.drawable.turntable_tonearm),
            contentDescription = null,
            modifier = Modifier
                .size(width = tonearmWidth, height = tonearmHeight)
                // Positioned by its bearing, not its corner, so changing the arm's size or angle
                // doesn't drag the pivot off the plinth.
                .offset(
                    x = plinthWidth * ARM_PIVOT_ON_PLINTH_X - tonearmWidth * ARM_PIVOT_IN_IMAGE_X,
                    y = plinthHeight * ARM_PIVOT_ON_PLINTH_Y - tonearmHeight * ARM_PIVOT_IN_IMAGE_Y,
                )
                .graphicsLayer {
                    rotationZ = arm.value
                    transformOrigin = TransformOrigin(ARM_PIVOT_IN_IMAGE_X, ARM_PIVOT_IN_IMAGE_Y)
                },
        )
    }
}

/** 1100x619 export. */
private const val PLINTH_HEIGHT_RATIO = 619f / 1100f

/**
 * Clear rows in the plinth export: the wood starts at row 79 and the feet end at row 559. A few
 * rows are left above, where the arm's counterweight stands slightly proud of the plinth.
 */
private const val PLINTH_CLEAR_TOP = 70f / 619f
private const val PLINTH_CLEAR_BOTTOM = 56f / 619f

/** 1100x825 export. */
private const val PLATTER_HEIGHT_RATIO = 825f / 1100f

/**
 * Measured off the platter export: its top face spans y 141–646 of 825 and x 112–984 of 1100, so
 * the face is 0.58 as tall as it is wide and centred at 0.477 of the image height.
 */
private const val PLATTER_SURFACE_ASPECT = 0.58f
private const val PLATTER_SURFACE_CENTRE_Y = 0.477f

/** Extra vertical flattening so the platter matches the plinth's lower camera angle. */
private const val PLATTER_SQUASH = 0.88f

/**
 * Where the spindle sits on the plinth, as a fraction of the plinth's width and height. Chosen so
 * the whole platter, rim included, sits inside the black mat (y 92–439 of the 619 export).
 */
private const val SPINDLE_X = 0.39f
private const val SPINDLE_Y = 0.435f

/**
 * As big as the mat allows. The mat's depth is the limit, not its width: any wider and the
 * platter's rim runs off the front or back of it. What's left on the right is the arm's.
 */
private const val PLATTER_WIDTH_FRACTION = 0.68f

/**
 * The record box as a fraction of the platter width. The disc fills only 83% of its export, so
 * this makes the disc 96% of the platter's face — a thin ring of mat stays visible, as on a real
 * deck.
 */
private const val RECORD_WIDTH_FRACTION = 0.917f

/**
 * Measured off the record export: the white paper label is 0.252 of the image across and centred
 * a touch left of the middle, at 0.495. The cover is drawn slightly larger than the paper so none
 * of its white rim shows round the edge.
 */
private const val LABEL_DIAMETER_FRACTION = 0.258f
private const val LABEL_CENTRE_X = 0.495f
private const val SPINDLE_HOLE_OF_LABEL = 0.046f

/**
 * 950x964 export. Sized so the parked arm fits on the mat beside the platter, headshell and all,
 * and still reaches the outer grooves when it swings in.
 */
private const val TONEARM_WIDTH_FRACTION = 0.40f
private const val TONEARM_HEIGHT_RATIO = 964f / 950f

/** The bearing inside the tonearm export — both the anchor and the rotation origin. */
private const val ARM_PIVOT_IN_IMAGE_X = 0.77f
private const val ARM_PIVOT_IN_IMAGE_Y = 0.30f

/** Where that bearing sits on the plinth. */
private const val ARM_PIVOT_ON_PLINTH_X = 0.86f
private const val ARM_PIVOT_ON_PLINTH_Y = 0.28f

/**
 * Positive is clockwise, which swings the stylus in towards the spindle. Parked rests it on the
 * mat to the right of the platter; playing sets it down in the outer grooves.
 */
private const val ARM_PARKED_DEGREES = 2f
private const val ARM_PLAYING_DEGREES = 27f
private const val ARM_SWING_MILLIS = 900

/** Lets the card sheet slide away before the arm moves, so the drop is seen. */
private const val NEEDLE_DROP_DELAY_MILLIS = 400L

/** Lowering the record onto the platter, and lifting it off again. */
private const val RECORD_PLACE_MILLIS = 550

/** How far above the platter the record starts, as a fraction of its width. */
private const val RECORD_LIFT_FRACTION = 0.18f

/** When the needle touches the record after playing starts — the player waits this long. */
internal const val NEEDLE_LANDS_AFTER_MILLIS =
    NEEDLE_DROP_DELAY_MILLIS + RECORD_PLACE_MILLIS + ARM_SWING_MILLIS

/** 33 1/3 rpm. Slower reads as a stopped record; faster looks like a fan. */
private const val REVOLUTION_MILLIS = 1800

/** The first turn, accelerating from rest. */
private const val SPIN_UP_MILLIS = 2400

/** How far and how long the record turns on after the music stops. */
private const val COAST_DEGREES = 120f
private const val COAST_MILLIS = 900

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393, heightDp = 260)
@Composable
private fun TurntablePlayingPreview() {
    Turntable(playing = true, modifier = Modifier.size(width = 393.dp, height = 260.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 393, heightDp = 260)
@Composable
private fun TurntableParkedPreview() {
    Turntable(playing = false, modifier = Modifier.size(width = 393.dp, height = 260.dp))
}
