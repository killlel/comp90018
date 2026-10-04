package com.example.vinyl.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.annotation.DrawableRes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.ui.theme.PoppinsFontFamily
import com.example.vinyl.ui.theme.VinylPalette

/** Scrollable content and a stable action area, including on small phones and large text. */
@Composable
internal fun OnboardingPageLayout(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    titleFontWeight: FontWeight = FontWeight.SemiBold,
    contentTopPadding: Dp = 32.dp,
    contentSpacing: Dp = 32.dp,
    headerContent: (@Composable ColumnScope.() -> Unit)? = null,
    @DrawableRes illustration: Int? = null,
    centreContentInVisualArea: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier.fillMaxSize().padding(horizontal = OnboardingHorizontalPadding), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(bottom = 16.dp)) {
            val viewportHeight = maxHeight
            val illustrationSize = minOf(maxWidth, 320.dp)
            val descriptionWidth = minOf(maxWidth, (maxWidth + OnboardingHorizontalPadding * 2) * 0.7f)
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight).padding(top = contentTopPadding, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                headerContent?.invoke(this)
                if (headerContent != null) Spacer(Modifier.height(24.dp))
                OnboardingHeading(title, description, titleFontWeight, descriptionWidth)
                Spacer(Modifier.height(contentSpacing))
                if (illustration != null) {
                    // Spare space places the artwork below the centre without compressing the copy.
                    Spacer(Modifier.weight(1f))
                    Image(
                        painter = painterResource(illustration), contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(illustrationSize),
                    )
                    Spacer(Modifier.height(24.dp))
                    content()
                    Spacer(Modifier.weight(0.25f))
                } else if (centreContentInVisualArea) {
                    // Use the same visual area as the illustrated steps for the identity control.
                    Spacer(Modifier.weight(1f))
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = illustrationSize),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        content = content,
                    )
                    Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.weight(0.25f))
                } else {
                    content()
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(bottom = OnboardingBottomPadding).heightIn(min = OnboardingActionMinimumHeight), horizontalAlignment = Alignment.CenterHorizontally, content = actions)
    }
}

@Composable
internal fun OnboardingHeading(
    title: String,
    description: String,
    titleFontWeight: FontWeight = FontWeight.SemiBold,
    descriptionWidth: Dp,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
    Text(title, color = VinylPalette.TextPrimary, fontFamily = PoppinsFontFamily,
        fontSize = 28.sp, lineHeight = 36.sp, fontWeight = titleFontWeight, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
    Text(description, color = VinylPalette.TextPrimary.copy(alpha = 0.72f), fontFamily = PoppinsFontFamily,
        fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Start, modifier = Modifier.width(descriptionWidth).padding(top = 16.dp))
    }
}
