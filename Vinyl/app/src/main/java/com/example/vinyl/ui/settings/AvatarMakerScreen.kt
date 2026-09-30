package com.example.vinyl.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.R
import com.example.vinyl.ui.theme.VinylPalette

data class AvatarGradient(
    val start: Color,
    val end: Color,
)

val AvatarIconIds: List<Int> = listOf(
    R.drawable.user_icon_1,
    R.drawable.user_icon_2,
    R.drawable.user_icon_3,
    R.drawable.user_icon_4,
    R.drawable.user_icon_5,
    R.drawable.user_icon_6,
    R.drawable.user_icon_7,
    R.drawable.user_icon_8,
)

val AvatarGradients: List<AvatarGradient> = listOf(
    AvatarGradient(Color(0xFF9B2EFF), Color(0xFFFF3D7A)),
    AvatarGradient(Color(0xFFC79212), Color(0xFFFFE36A)),
    AvatarGradient(Color(0xFF0B4A4A), Color(0xFF4EE0D0)),
    AvatarGradient(Color(0xFF0B2A6B), Color(0xFF5AD2FF)),
    AvatarGradient(Color(0xFF3B1A8A), Color(0xFFB26BFF)),
    AvatarGradient(Color(0xFF1A1A1A), Color(0xFF8A8A8A)),
)

@Composable
fun AvatarPreview(
    iconIndex: Int,
    gradientIndex: Int,
    size: androidx.compose.ui.unit.Dp,
    selected: Boolean = false,
    showGradient: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val g = AvatarGradients[gradientIndex.coerceIn(AvatarGradients.indices)]
    val icon = AvatarIconIds.getOrNull(iconIndex.coerceIn(AvatarIconIds.indices))
    val ring = if (selected) VinylPalette.TealAccent else Color(0xFF2A2A2A)
    val bg = if (showGradient) {
        Modifier.background(Brush.verticalGradient(listOf(g.start, g.end)))
    } else {
        Modifier.background(Color(0xFF1C1C1C))
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(bg)
            .border(if (selected) 2.dp else 1.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(size * 1.17f),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
fun AvatarMakerBody(
    iconIndex: Int,
    gradientIndex: Int,
    onIconChange: (Int) -> Unit,
    onGradientChange: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        AvatarPreview(iconIndex, gradientIndex, size = 148.dp)
        Spacer(Modifier.height(36.dp))
        Text(
            "Icon",
            color = VinylPalette.TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        IconGrid(iconIndex, gradientIndex, onIconChange)
        Spacer(Modifier.height(32.dp))
        Text(
            "Background",
            color = VinylPalette.TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AvatarGradients.forEachIndexed { i, g ->
                val on = i == gradientIndex
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(g.start, g.end)))
                        .border(
                            width = if (on) 2.dp else 1.dp,
                            color = if (on) VinylPalette.TealAccent else Color(0xFF2E2E2E),
                            shape = CircleShape,
                        )
                        .clickable { onGradientChange(i) },
                )
            }
        }
    }
}

@Composable
private fun IconGrid(
    iconIndex: Int,
    gradientIndex: Int,
    onIconChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(0..3, 4..7).forEach { range ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                range.forEach { i ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        AvatarPreview(
                            iconIndex = i,
                            gradientIndex = gradientIndex,
                            size = 64.dp,
                            selected = i == iconIndex,
                            onClick = { onIconChange(i) },
                        )
                    }
                }
            }
        }
    }
}
