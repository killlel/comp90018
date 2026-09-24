package com.example.vinyl.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.ui.theme.VinylPalette
import com.example.vinyl.ui.theme.VinylTheme

/**
 * The app's settings page. Location is the only entry for now; add rows here as other settings
 * arrive rather than growing another top-level screen.
 */
@Composable
fun SettingsScreen(
    locationValue: String?,
    onOpenLocation: () -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VinylPalette.Background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = VinylPalette.TextPrimary)
            }
            Text(
                text = "Settings",
                color = VinylPalette.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        SettingsRow(
            label = "Location",
            value = locationValue ?: "Not set",
            onClick = onOpenLocation,
        )

        TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text("Sign out", color = VinylPalette.TextMuted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(label, color = VinylPalette.TextPrimary, fontSize = 16.sp)
            Text(
                text = value,
                color = VinylPalette.TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = VinylPalette.TextMuted,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun SettingsScreenPreview() {
    VinylTheme {
        SettingsScreen(locationValue = "Melbourne", onOpenLocation = {}, onSignOut = {}, onBack = {})
    }
}
