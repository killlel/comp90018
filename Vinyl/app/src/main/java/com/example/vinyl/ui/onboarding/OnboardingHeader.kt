package com.example.vinyl.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.vinyl.R

/**
 * The wordmark shown at the top of every onboarding page — the real logo asset
 * (`res/drawable/logo_cyan.png`), not a text approximation. Just the mark, no tagline, per
 * earlier design feedback that the full lockup read too large/heavy at this spot.
 */
@Composable
fun OnboardingHeader(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.logo_cyan),
        contentDescription = "Vinyl",
        modifier = modifier
            .padding(top = 5.dp, bottom = 4.dp)
            .height(36.dp)
            .width(82.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun OnboardingHeaderPreview() {
    OnboardingHeader()
}