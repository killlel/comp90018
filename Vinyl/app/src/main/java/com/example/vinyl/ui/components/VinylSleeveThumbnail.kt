package com.example.vinyl.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.vinyl.R
import com.example.vinyl.ui.theme.PoppinsFontFamily
import com.example.vinyl.ui.theme.VinylColors
import com.example.vinyl.ui.theme.VinylTheme

// Proportions measured off the exported "Collections" screen: the record sits behind the sleeve,
// vertically centred, with a quarter of a sleeve-width showing past its right edge.
private const val DISC_TO_SLEEVE = 0.83f
private const val DISC_OVERHANG = 0.25f

/**
 * A record sleeve with its vinyl peeking out to the right — the thumbnail used for every shelf
 * row in the design. Total width is [sleeveSize] * 1.25 to leave room for the disc overhang.
 *
 * Records shipping with real artwork ([localCoverRes]) already carry their title in the art, so
 * the printed title/artist is only drawn for flat-colour placeholder sleeves.
 */
@Composable
fun VinylSleeveThumbnail(
    songName: String,
    artist: String,
    coverUrl: String?,
    accentColor: Color,
    sleeveSize: Dp,
    modifier: Modifier = Modifier,
    localCoverRes: Int? = null,
) {
    val sleeveShape = RoundedCornerShape(2.dp)
    Box(
        modifier = modifier
            .width(sleeveSize * (1f + DISC_OVERHANG))
            .height(sleeveSize),
    ) {
        Image(
            painter = painterResource(R.drawable.black_vinyl),
            contentDescription = null,
            modifier = Modifier
                .size(sleeveSize * DISC_TO_SLEEVE)
                .align(Alignment.CenterEnd),
            contentScale = ContentScale.Fit,
        )
        val hasArtwork = localCoverRes != null || coverUrl != null
        Box(
            modifier = Modifier
                .size(sleeveSize)
                .align(Alignment.CenterStart)
                .clip(sleeveShape)
                // Real artwork has its own soft corners; tinting behind it would rim the sleeve
                // in the accent colour, so only placeholders get the flat fill.
                .background(if (hasArtwork) VinylColors.Ink else accentColor)
                .border(1.dp, VinylColors.SleeveBorder.copy(alpha = 0.7f), sleeveShape),
        ) {
            when {
                localCoverRes != null -> Image(
                    painter = painterResource(localCoverRes),
                    contentDescription = songName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                coverUrl != null -> AsyncImage(
                    model = coverUrl,
                    contentDescription = songName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                else -> PlaceholderSleeveArt(
                    songName = songName,
                    artist = artist,
                    accentColor = accentColor,
                )
            }
        }
    }
}

/** Mimics the printed-sleeve look of the real artwork: a disc motif over a rule and a tiny caption. */
@Composable
private fun PlaceholderSleeveArt(songName: String, artist: String, accentColor: Color) {
    // Light sleeves (the cream one) need dark ink printed on them, dark sleeves need light.
    val ink = if (accentColor.luminance() > 0.45f) Color.Black else Color.White

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.46f)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(ink.copy(alpha = 0.22f)),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(1.dp)
                    .background(ink.copy(alpha = 0.45f)),
            )
            Text(
                text = songName.uppercase(),
                fontFamily = PoppinsFontFamily,
                fontSize = 6.sp,
                lineHeight = 8.sp,
                letterSpacing = 0.8.sp,
                color = ink.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = artist.uppercase(),
                fontFamily = PoppinsFontFamily,
                fontSize = 5.sp,
                lineHeight = 7.sp,
                letterSpacing = 0.6.sp,
                color = ink.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun VinylSleeveThumbnailPreview() {
    VinylTheme {
        Row(
            modifier = Modifier
                .background(VinylColors.Ink)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            VinylSleeveThumbnail(
                songName = "Midnight Drive",
                artist = "Various Artists",
                coverUrl = null,
                accentColor = VinylColors.Rust,
                sleeveSize = 97.dp,
                localCoverRes = R.drawable.cover_midnight_drive,
            )
            VinylSleeveThumbnail(
                songName = "Solstice",
                artist = "Kai & Sun",
                coverUrl = null,
                accentColor = VinylColors.Teal,
                sleeveSize = 97.dp,
            )
        }
    }
}
