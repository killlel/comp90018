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

        Image(
            painter = painterResource(R.drawable.turntable_platter),
            contentDescription = null,
            modifier = Modifier
                .size(platterWidth)
                .offset(
                    x = plinthWidth * SPINDLE_X - platterWidth / 2,
                    y = plinthHeight * SPINDLE_Y - platterWidth * PLATTER_HEIGHT_RATIO / 2,
                ),
        )

        Box(
            modifier = Modifier
                .size(recordSize)
                // Centre the square on the spindle, then flatten. scaleY pivots on the box centre,
                // so the spindle stays put while the disc squashes around it.
                .offset(
                    x = plinthWidth * SPINDLE_X - recordSize / 2,
                    y = plinthHeight * SPINDLE_Y - recordSize / 2,
                )
                .graphicsLayer { scaleY = PERSPECTIVE_SQUASH },
        ) {
            Image(
                painter = painterResource(R.drawable.turntable_record),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = spinAngle },
            )

            // The export has a plain white label. Today's artwork goes there when there is any,
            // the accent colour when there isn't — both turn with the disc.
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

/** The platter's top surface is roughly 0.57 as tall as it is wide at this camera angle. */
private const val PERSPECTIVE_SQUASH = 0.57f

/** Where the spindle sits on the plinth, as a fraction of the plinth's width and height. */
private const val SPINDLE_X = 0.45f
private const val SPINDLE_Y = 0.48f

private const val PLATTER_WIDTH_FRACTION = 0.56f

/** A record is a little smaller than the platter it sits on, so the metal rim stays visible. */
private const val RECORD_WIDTH_FRACTION = 0.86f

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

/** Parked clear of the disc; swung in over it. Positive is clockwise. */
private const val ARM_PARKED_DEGREES = 14f
private const val ARM_PLAYING_DEGREES = -2f
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
