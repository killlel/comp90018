package com.example.vinyl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.vinyl.ui.theme.VinylColors

/**
 * The wooden ledge a row of records stands on: a lit top face over a shadowed front edge.
 *
 * Shared by the Collection tab and Home's "Recently collected", so the two shelves always match.
 */
@Composable
fun ShelfLedge(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(VinylColors.ShelfTop, VinylColors.ShelfFade),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(VinylColors.ShelfEdge),
        )
    }
}
