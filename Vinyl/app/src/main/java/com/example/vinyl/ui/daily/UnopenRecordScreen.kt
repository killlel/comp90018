package com.example.vinyl.ui.daily

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.data.MoodTag
import com.example.vinyl.haptics.vibrateOnce
import com.example.vinyl.ui.theme.ThemeState
import com.example.vinyl.ui.theme.VinylPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

data class UnopenedRecordUiState(
    /** Null when either end has no location — see [distanceNote] for why. */
    val distanceLabel: String?,
    val moodLabel: String,
    val sentTimeLabel: String,
    val distanceNote: String? = null,
    /** The sender's mood, drawn as a face above the envelope. Null draws no face. */
    val mood: MoodTag? = null,
    /** The day it was sent, for example "8 Oct". Null hides the "Sent" line. */
    val sentDateLabel: String? = null,
)

/**
 * Page 3 of the receive flow: the sealed envelope, full screen.
 *
 * Shaking the phone opens it: [DetectShakeGesture] listens to the accelerometer for as long as
 * this screen is visible (see ShakeDetector.kt and ShakeAlgorithm.kt for the detection itself).
 * A tap on the envelope doesn't open it: the envelope jiggles and the helper line pulses, as a
 * nudge to shake. As a backup, holding the envelope for [LongPressOpenMs] opens it, with a ring
 * filling round the seal while it's held. With touch exploration on (TalkBack), a double tap on
 * the envelope opens it, and the "Open card" action stays available for Switch Access and the
 * actions menu. Either way the flap flips up, the letter slides out, and then [onOpen] moves on
 * to the music card.
 */
@Composable
fun UnopenedRecordScreen(
    state: UnopenedRecordUiState,
    onOpen: () -> Unit,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnOpen by rememberUpdatedState(onOpen)

    var opening by remember { mutableStateOf(false) }
    val flip = remember { Animatable(0f) }
    val letterLift = remember { Animatable(0f) }
    val wobbleAmount = remember { Animatable(1f) }
    // A tap's nudge: an extra, stronger jiggle of the envelope and one pulse of the helper line.
    val nudge = remember { Animatable(0f) }
    val helperPulse = remember { Animatable(0f) }
    // How far through the long press the finger is, for the ring round the seal.
    val holdProgress = remember { Animatable(0f) }

    val touchExploration = rememberTouchExplorationEnabled()
    val currentTouchExploration by rememberUpdatedState(touchExploration)
    val reduceMotion = remember(context) { animationsRemoved(context) }

    val open: () -> Unit = {
        if (!opening) {
            opening = true
            context.vibrateOnce()
            scope.launch { wobbleAmount.animateTo(0f, tween(150)) }
            scope.launch { flip.animateTo(1f, tween(FlapFlipMs, easing = FastOutSlowInEasing)) }
            scope.launch {
                // The letter starts rising as the flap clears the top of the envelope.
                delay(LetterStartMs)
                letterLift.animateTo(1f, tween(LetterSlideMs, easing = FastOutSlowInEasing))
                delay(HoldBeforeCardMs)
                currentOnOpen()
            }
        }
    }

    val remind: () -> Unit = {
        if (!opening) {
            scope.launch {
                nudge.snapTo(0f)
                nudge.animateTo(0f, keyframes {
                    durationMillis = NudgeMs
                    NudgeDegrees at 70
                    -NudgeDegrees * 0.8f at 170
                    NudgeDegrees * 0.5f at 280
                    -NudgeDegrees * 0.25f at 380
                })
            }
            scope.launch {
                helperPulse.snapTo(0f)
                helperPulse.animateTo(1f, tween(PulseMs / 2, easing = FastOutSlowInEasing))
                helperPulse.animateTo(0f, tween(PulseMs / 2, easing = FastOutSlowInEasing))
            }
        }
    }

    DetectShakeGesture(enabled = !opening, onShake = open)

    val idle = rememberInfiniteTransition(label = "envelopeWobble")
    val wobble by idle.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(WobbleMs / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wobble",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylPalette.SheetSurface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 48dp touch target; the arrow itself stays 24dp.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = VinylPalette.Cream,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Open your music card",
            color = VinylPalette.Cream,
            style = ReceiveFlowStyle.Title,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Shake your phone, or press and hold the envelope",
            // Brightens from the usual 55% towards full cream at the top of a pulse.
            color = VinylPalette.Cream.copy(alpha = 0.55f + 0.45f * helperPulse.value),
            style = ReceiveFlowStyle.Helper,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                val scale = 1f + 0.06f * helperPulse.value
                scaleX = scale
                scaleY = scale
            },
        )

        Spacer(modifier = Modifier.weight(1f))

        // The open flap rises above the envelope into the face's space, so the face makes way.
        state.mood?.let { mood ->
            Icon(
                painter = painterResource(moodIcon(mood)),
                contentDescription = "Sender's mood: ${state.moodLabel}",
                tint = VinylPalette.Cream,
                modifier = Modifier
                    .size(60.dp)
                    .graphicsLayer { alpha = 1f - flip.value },
            )
            Spacer(modifier = Modifier.height(GroupGap))
        }

        Envelope(
            flip = flip.value,
            letterLift = LetterLiftDistance * letterLift.value,
            holdProgress = holdProgress.value,
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = wobble * WobbleDegrees * wobbleAmount.value + nudge.value
                    transformOrigin = TransformOrigin(0.5f, 0.92f)
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        // The ring fills over the hold; with animations removed it shows full
                        // straight away instead of moving.
                        if (!opening) {
                            scope.launch {
                                if (reduceMotion) {
                                    holdProgress.snapTo(1f)
                                } else {
                                    holdProgress.animateTo(1f, tween(LongPressOpenMs.toInt(), easing = LinearEasing))
                                }
                            }
                        }
                        var released = false
                        // Null when the finger is still down once the time is up: a long press.
                        val endedInTime = withTimeoutOrNull(LongPressOpenMs) {
                            released = waitForUpOrCancellation() != null
                        }
                        scope.launch { holdProgress.snapTo(0f) }
                        when {
                            endedInTime == null -> open()
                            // A TalkBack double tap arrives as a tap: it opens the card.
                            released && currentTouchExploration -> open()
                            released -> remind()
                            // Dragged off the envelope: neither a tap nor a hold.
                            else -> Unit
                        }
                    }
                }
                .semantics {
                    contentDescription = if (touchExploration) {
                        "Sealed envelope. Double tap to open."
                    } else {
                        "Sealed envelope. Shake your phone, or press and hold to open."
                    }
                    if (touchExploration) {
                        onClick(label = "Open card") {
                            open()
                            true
                        }
                    }
                    customActions = listOf(
                        CustomAccessibilityAction("Open card") {
                            open()
                            true
                        },
                    )
                },
        )

        Spacer(modifier = Modifier.height(GroupGap))

        Text(
            text = state.distanceLabel?.let { "Someone $it away" } ?: "Someone, somewhere",
            color = VinylPalette.Cream,
            style = ReceiveFlowStyle.text(16.sp, FontWeight.Medium, 24.sp),
            textAlign = TextAlign.Center,
        )
        state.sentDateLabel?.let {
            Text(
                text = "Sent $it",
                color = VinylPalette.Cream.copy(alpha = 0.55f),
                style = ReceiveFlowStyle.text(13.sp, FontWeight.Light, 20.sp),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(GroupGap))

        Icon(
            Icons.Outlined.Vibration,
            contentDescription = null,
            tint = VinylPalette.Cream.copy(alpha = 0.6f),
            modifier = Modifier.size(30.dp),
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * Whether touch exploration (TalkBack) is on, kept up to date while the screen is shown so
 * turning it on or off takes effect without leaving the page.
 */
@Composable
private fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        if (manager == null) return@DisposableEffect onDispose {}
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager.addTouchExplorationStateChangeListener(listener)
        enabled = manager.isTouchExplorationEnabled
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

/** True when the system's "Remove animations" setting is on (animator duration scale 0). */
private fun animationsRemoved(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * The envelope, drawn rather than imported so its parts can move independently.
 *
 * [flip] runs the flap from closed (0) to fully open (1), a 180 degree turn about its top edge.
 * The turn is drawn as a flat vertical squash with no perspective, so the flap keeps the body's
 * exact width the whole way and lands with its base on the body's top edge. As it opens the
 * body's top corners square off, so the open flap meets them without a notch.
 */
@Composable
private fun Envelope(flip: Float, letterLift: Dp, holdProgress: Float = 0f, modifier: Modifier = Modifier) {
    val turn = cos(Math.PI * flip).toFloat() // 1 closed, 0 edge-on, -1 open
    val flapOpen = turn < 0f
    // Square by the time the flap is edge-on, before its open side shows.
    val bodyTopCorner = BodyCorner * (1f - 2f * flip).coerceIn(0f, 1f)

    Box(modifier = modifier.size(EnvelopeWidth, EnvelopeHeight)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawBack(bodyTopCorner.toPx(), BodyCorner.toPx())
            if (flapOpen) drawFlap(turn, open = true)
        }

        // The letter sits between the back and the front pocket.
        Box(
            modifier = Modifier
                .offset { IntOffset(LetterInset.roundToPx(), (LetterTop - letterLift).roundToPx()) }
                .size(EnvelopeWidth - LetterInset * 2, LetterHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(LetterColor),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPocket(bodyTopCorner.toPx(), BodyCorner.toPx())
            if (!flapOpen) drawFlap(turn, open = false)
        }

        // The long-press ring: a thin accent arc round the seal that fills while the envelope is held.
        // It rides on the flap's tip with the seal and fades with it.
        if (holdProgress > 0f) {
            Canvas(
                modifier = Modifier
                    .offset {
                        val tipY = (EnvelopeHeight * FlapDepth).toPx() * turn
                        IntOffset(
                            ((EnvelopeWidth - HoldRingSize) / 2).roundToPx(),
                            (tipY - (HoldRingSize / 2).toPx()).roundToInt(),
                        )
                    }
                    .graphicsLayer { alpha = (1f - flip * 2.5f).coerceIn(0f, 1f) }
                    .size(HoldRingSize),
            ) {
                val stroke = 3.dp.toPx()
                val outline = 1.dp.toPx()
                // Inset by the outlined width, so the cream edge stays inside the canvas.
                val inset = stroke / 2 + outline
                val arcTopLeft = Offset(inset, inset)
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                // A 1dp cream edge on both sides: against the darker accents' flaps the arc alone
                // is under 3:1.
                drawArc(
                    color = VinylPalette.Cream,
                    startAngle = -90f,
                    sweepAngle = 360f * holdProgress,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke + outline * 2, cap = StrokeCap.Round),
                )
                drawArc(
                    // Half of the ring is over the cream pocket, where the light accent vanishes.
                    color = ThemeState.accent.onCream,
                    startAngle = -90f,
                    sweepAngle = 360f * holdProgress,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        // The seal rides on the flap's tip and fades as the flap lifts.
        Box(
            modifier = Modifier
                .offset {
                    val tipY = (EnvelopeHeight * FlapDepth).toPx() * turn
                    IntOffset(
                        ((EnvelopeWidth - SealSize) / 2).roundToPx(),
                        (tipY - (SealSize / 2).toPx()).roundToInt(),
                    )
                }
                .graphicsLayer { alpha = (1f - flip * 2.5f).coerceIn(0f, 1f) }
                .size(SealSize)
                .shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .background(sealColor(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = ThemeState.accent.onCream,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

/** The back panel: rounded all round when closed, square on top once open. */
private fun DrawScope.drawBack(topCorner: Float, bottomCorner: Float) {
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f, top = 0f, right = size.width, bottom = size.height,
                topLeftCornerRadius = CornerRadius(topCorner),
                topRightCornerRadius = CornerRadius(topCorner),
                bottomRightCornerRadius = CornerRadius(bottomCorner),
                bottomLeftCornerRadius = CornerRadius(bottomCorner),
            ),
        )
    }
    drawPath(path, BackColor)
}

/** The front pocket, its top edge cut into a V that dips to [PocketDip] of the height. */
private fun DrawScope.drawPocket(topCorner: Float, bottomCorner: Float) {
    val w = size.width
    val h = size.height
    val tip = Offset(w / 2f, h * PocketDip)
    val path = Path().apply {
        moveTo(0f, topCorner)
        val leftStart = towards(Offset.Zero, tip, topCorner)
        quadraticTo(0f, 0f, leftStart.x, leftStart.y)
        lineTo(tip.x, tip.y)
        val rightEnd = towards(Offset(w, 0f), tip, topCorner)
        lineTo(rightEnd.x, rightEnd.y)
        quadraticTo(w, 0f, w, topCorner)
        lineTo(w, h - bottomCorner)
        quadraticTo(w, h, w - bottomCorner, h)
        lineTo(bottomCorner, h)
        quadraticTo(0f, h, 0f, h - bottomCorner)
        close()
    }
    drawPath(path, VinylPalette.Cream)
}

/**
 * The flap, hinged on the top edge. [turn] squashes it vertically about that edge (negative
 * flips it upward). Closed, its top corners are rounded off to sit inside the body's rounded
 * corners; open, it is a plain triangle exactly as wide as the body.
 */
private fun DrawScope.drawFlap(turn: Float, open: Boolean) {
    val w = size.width
    val tip = Offset(w / 2f, size.height * FlapDepth)
    val corner = if (open) 0f else FlapCorner.toPx()
    val path = Path().apply {
        val leftStart = towards(Offset.Zero, tip, corner)
        moveTo(leftStart.x, leftStart.y)
        quadraticTo(0f, 0f, corner, 0f)
        lineTo(w - corner, 0f)
        val rightEnd = towards(Offset(w, 0f), tip, corner)
        quadraticTo(w, 0f, rightEnd.x, rightEnd.y)
        lineTo(tip.x, tip.y)
        close()
    }
    scale(scaleX = 1f, scaleY = turn, pivot = Offset(w / 2f, 0f)) {
        drawPath(path, VinylPalette.TealAccent)
    }
}

/** The point [distance] along the line from [from] to [to]. */
private fun towards(from: Offset, to: Offset, distance: Float): Offset {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = hypot(dx, dy)
    if (length == 0f) return from
    return Offset(from.x + dx / length * distance, from.y + dy / length * distance)
}

private val EnvelopeWidth = 240.dp
private val EnvelopeHeight = 160.dp
private val BodyCorner = 16.dp
private val FlapCorner = 14.dp

/** How far down the body the flap's tip reaches, as a fraction of the height. */
private const val FlapDepth = 0.56f

/** Where the pocket's V bottoms out, as a fraction of the height. */
private const val PocketDip = 0.54f

// The letter is tall enough that, lifted, its bottom edge still sits below the V's tip
// (12 + 140 - 60 = 92dp, against a tip at 86dp), so no back panel shows under it.
private val LetterInset = 16.dp
private val LetterTop = 12.dp
private val LetterHeight = 140.dp
private val LetterLiftDistance = 60.dp

private val SealSize = 60.dp

/** The long-press ring sits just outside the seal. */
private val HoldRingSize = SealSize + 12.dp
private val GroupGap = 28.dp

private val BackColor = Color(0xFFCFC8BA)
private val LetterColor = Color(0xFFFBF9F4)

/** A near-white disc with a hint of the accent, like the original mint seal on teal. */
private fun sealColor(): Color = ThemeState.accent.color.copy(alpha = 0.12f).compositeOver(Color.White)

private const val WobbleMs = 1800
private const val WobbleDegrees = 2.5f
private const val FlapFlipMs = 550
private const val LetterStartMs = 400L
private const val LetterSlideMs = 450
private const val HoldBeforeCardMs = 750L

/** How long the envelope has to be held to open it without shaking. */
private const val LongPressOpenMs = 1200L

private const val NudgeMs = 460
private const val NudgeDegrees = 7f
private const val PulseMs = 700

@Preview(showBackground = true, backgroundColor = 0xFF1C1C1C, widthDp = 390, heightDp = 844)
@Composable
private fun UnopenedRecordScreenPreview() {
    UnopenedRecordScreen(
        state = UnopenedRecordUiState(
            distanceLabel = "2.4 km",
            moodLabel = "Romantic",
            sentTimeLabel = "3 hr. ago",
            mood = MoodTag.Romantic,
            sentDateLabel = "8 Oct",
        ),
        onOpen = {},
    )
}

/** The envelope with its flap flipped and the letter lifted, to check the open geometry. */
@Preview(showBackground = true, backgroundColor = 0xFF1C1C1C, widthDp = 320, heightDp = 360)
@Composable
private fun EnvelopeOpenPreview() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Envelope(flip = 1f, letterLift = LetterLiftDistance)
    }
}

/** The closed envelope part way through a long press, showing the ring round the seal. */
@Preview(showBackground = true, backgroundColor = 0xFF1C1C1C, widthDp = 320, heightDp = 260)
@Composable
private fun EnvelopeHoldPreview() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Envelope(flip = 0f, letterLift = 0.dp, holdProgress = 0.6f)
    }
}
