package com.example.vinyl.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A manually-built bottom sheet — not Material3's ModalBottomSheet, to avoid depending on an
 * experimental API whose surface has shifted across Compose versions. Just a dimmed scrim behind
 * a rounded-top panel pinned to the bottom, sized to a fraction of the screen. Tapping the scrim
 * dismisses; tapping the panel itself does not. With [dismissOnSwipe], dragging the top strip
 * (where the content draws its handle) down past a quarter of the sheet, or flinging it, dismisses.
 *
 * Showing it fades the scrim in and slides the panel up from the bottom edge; hiding it plays
 * the reverse. [content] keeps being drawn while it slides out, so callers give it the last
 * thing it showed (see [rememberLastNonNull]) rather than nothing.
 */
@Composable
internal fun AnimatedBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    heightFraction: Float = 0.88f,
    dismissOnSwipe: Boolean = false,
    content: @Composable () -> Unit,
) {
    // The parent itself doesn't animate; the scrim and the panel each bring their own motion.
    AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None) {
        // Inside the visibility scope so a drag offset never survives into the next opening.
        var dragOffset by remember { mutableFloatStateOf(0f) }
        var sheetHeight by remember { mutableIntStateOf(0) }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .animateEnterExit(
                        enter = fadeIn(tween(SheetEnterMs)),
                        exit = fadeOut(tween(SheetExitMs)),
                    )
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(heightFraction)
                    .onSizeChanged { sheetHeight = it.height }
                    .offset { IntOffset(0, dragOffset.roundToInt()) }
                    .animateEnterExit(
                        enter = slideInVertically(tween(SheetEnterMs, easing = LinearOutSlowInEasing)) { it },
                        exit = slideOutVertically(tween(SheetExitMs, easing = FastOutLinearInEasing)) { it },
                    )
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {}, // absorbs taps so they don't fall through to the scrim behind
                    ),
            ) {
                content()
                if (dismissOnSwipe) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(32.dp)
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                                },
                                onDragStopped = { velocity ->
                                    if (dragOffset > sheetHeight * 0.25f || velocity > 1500f) {
                                        onDismiss()
                                    } else {
                                        animate(dragOffset, 0f) { value, _ -> dragOffset = value }
                                    }
                                },
                            ),
                    )
                }
            }
        }
    }
}

/** Material's standard durations: entering a little slower than leaving. */
private const val SheetEnterMs = 300
private const val SheetExitMs = 250

/**
 * [value], or the last non-null value it had. Lets a closing sheet keep showing what it held
 * while it slides out, after its state has already gone back to null.
 */
@Composable
internal fun <T : Any> rememberLastNonNull(value: T?): T? {
    val last = remember { object { var value: T? = null } }
    if (value != null) last.value = value
    return last.value
}