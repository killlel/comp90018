package com.example.vinyl.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.vinyl.R
import com.example.vinyl.ui.theme.VinylPalette
import kotlinx.coroutines.delay

/**
 * The record player on the home screen.
 *
 * Four layers, because each one moves differently:
 *
 *     tonearm   swings in over the record, then parks
 *     record    turns continuously around its own centre
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
    playing: Boolean = true,
    /** Painted onto the record label. Falls back to the accent colour when absent. */
    labelArtworkUrl: String? = null,
) {
    val haptics = rememberTurntableHaptics()

    val spin by rememberInfiniteTransition(label = "platter").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(REVOLUTION_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )
    val spinAngle = if (playing) spin else 0f

    val armAngle by animateFloatAsState(
        targetValue = if (playing) ARM_PLAYING_DEGREES else ARM_PARKED_DEGREES,
        animationSpec = tween(ARM_SWING_MILLIS, easing = LinearEasing),
        label = "arm",
    )

    // Delayed to land with the arm rather than fire as it sets off. Keyed on `playing` so it
    // happens once per transition, not on every recomposition the spin animation causes.
    //
    // The first pass is skipped deliberately: opening the tab is not a needle drop, and buzzing
    // on arrival at a screen the user merely navigated to feels like a malfunction.
    var settled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(playing) {
        if (!settled) {
            settled = true
            return@LaunchedEffect
        }
        delay(ARM_SWING_MILLIS.toLong())
        if (playing) haptics.needleDrop() else haptics.needleLift()
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val plinthWidth = maxWidth
        val plinthHeight = plinthWidth * PLINTH_HEIGHT_RATIO
        val platterWidth = plinthWidth * PLATTER_WIDTH_FRACTION
        val recordSize = platterWidth * RECORD_WIDTH_FRACTION
        val tonearmWidth = plinthWidth * TONEARM_WIDTH_FRACTION
        val tonearmHeight = tonearmWidth * TONEARM_HEIGHT_RATIO

        Image(
            painter = painterResource(R.drawable.turntable_plinth),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
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
        Box(
            modifier = Modifier
                .size(recordSize)
                // Centre the square on the platter's face, then flatten. scaleY pivots on the box
                // centre, so the disc squashes around its own spindle.
                .offset(
                    x = plinthWidth * SPINDLE_X - recordSize / 2,
                    y = surfaceCentreY - recordSize / 2,
                )
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
                        x = (recordSize - labelSize) / 2,
                        y = (recordSize - labelSize) / 2,
                    )
                    .graphicsLayer { rotationZ = spinAngle }
                    .clip(CircleShape),
            ) {
                if (labelArtworkUrl != null) {
                    AsyncImage(
                        model = labelArtworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawBehind { drawRect(VinylPalette.TealAccent) },
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
                    rotationZ = armAngle
                    transformOrigin = TransformOrigin(ARM_PIVOT_IN_IMAGE_X, ARM_PIVOT_IN_IMAGE_Y)
                },
        )
    }
}

/** 1100x619 export. */
private const val PLINTH_HEIGHT_RATIO = 619f / 1100f

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
private const val SPINDLE_X = 0.43f
private const val SPINDLE_Y = 0.43f

private const val PLATTER_WIDTH_FRACTION = 0.60f

/**
 * The record box as a fraction of the platter width. The disc fills only 83% of its export, so
 * this makes the disc 96% of the platter's face — a thin ring of mat stays visible, as on a real
 * deck.
 */
private const val RECORD_WIDTH_FRACTION = 0.917f

/** Measured off the record export: the paper label, and the hole at its centre. */
private const val LABEL_DIAMETER_FRACTION = 0.236f
private const val SPINDLE_HOLE_OF_LABEL = 0.05f

/** 950x964 export. */
private const val TONEARM_WIDTH_FRACTION = 0.46f
private const val TONEARM_HEIGHT_RATIO = 964f / 950f

/** The bearing inside the tonearm export — both the anchor and the rotation origin. */
private const val ARM_PIVOT_IN_IMAGE_X = 0.77f
private const val ARM_PIVOT_IN_IMAGE_Y = 0.30f

/** Where that bearing sits on the plinth. */
private const val ARM_PIVOT_ON_PLINTH_X = 0.82f
private const val ARM_PIVOT_ON_PLINTH_Y = 0.30f

/**
 * Positive is clockwise, which swings the stylus in towards the spindle. Parked leaves it just off
 * the disc's front-right edge; playing sets it down about four-fifths of the way out, in the
 * grooves.
 */
private const val ARM_PARKED_DEGREES = 12f
private const val ARM_PLAYING_DEGREES = 28f
private const val ARM_SWING_MILLIS = 900

/** 33 1/3 rpm. Slower reads as a stopped record; faster looks like a fan. */
private const val REVOLUTION_MILLIS = 1800

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
