package com.example.vinyl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.ui.daily.ReceiveFlowStyle
import com.example.vinyl.ui.theme.VinylPalette

/**
 * A snackbar in the app's panel style: the dark gradient with its border, cream text and a teal
 * action, in Poppins with 20dp corners.
 */
@Composable
internal fun VinylSnackbar(message: String, actionLabel: String?, onAction: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(ReceiveFlowStyle.PanelBrush)
            .border(1.dp, ReceiveFlowStyle.PanelBorder, shape)
            .padding(start = 20.dp, end = 8.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = VinylPalette.Cream,
            style = ReceiveFlowStyle.text(15.sp, FontWeight.Normal, 22.sp),
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        actionLabel?.let {
            TextButton(onClick = onAction) {
                Text(
                    text = it,
                    color = VinylPalette.TealAccent,
                    style = ReceiveFlowStyle.text(15.sp, FontWeight.Medium, 22.sp),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 390)
@Composable
private fun VinylSnackbarPreview() {
    VinylSnackbar(message = "Removed.", actionLabel = "Undo", onAction = {})
}