package com.example.vinyl.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.example.vinyl.R
import com.example.vinyl.ui.theme.VinylPalette

/**
 * The "Vinyl" logo with its letters in the current accent colour.
 *
 * Same artwork as `vinyl_logo_white.xml`, split in two so only the letters take the tint: the
 * record over the "i", the arm and the box after the "l" keep their own black and cream. Both
 * halves share one viewport, so they line up at any size. Size it exactly like the old Image,
 * e.g. `Modifier.height(26.dp)`.
 */
@Composable
fun VinylWordmark(modifier: Modifier = Modifier) {
    Box(modifier.semantics { contentDescription = "Vinyl" }) {
        // The letters size the box; the marks are stretched over the same box.
        Image(
            painter = painterResource(R.drawable.vinyl_wordmark_letters),
            contentDescription = null,
            colorFilter = ColorFilter.tint(VinylPalette.TealAccent),
        )
        Image(
            painter = painterResource(R.drawable.vinyl_wordmark_marks),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun VinylWordmarkPreview() {
    VinylWordmark()
}
