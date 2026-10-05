package com.example.vinyl.ui.settings

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.vinyl.ui.theme.VinylTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SignOutTest {
    @get:Rule val compose = createComposeRule()

    private var signOutCalls = 0

    private fun showSettings() {
        compose.setContent {
            VinylTheme { SettingsScreen(onBack = {}, onSignOut = { signOutCalls++ }) }
        }
        compose.onNodeWithText("Sign Out").performScrollTo().performClick()
    }

    @Test
    fun confirmingTheDialogSignsOut() {
        showSettings()
        // The dialog title is also "Sign out", so pick the clickable one.
        compose.onNode(hasText("Sign out") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(1, signOutCalls)
    }

    @Test
    fun cancellingTheDialogKeepsTheUserSignedIn() {
        showSettings()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertEquals(0, signOutCalls)
        compose.onNode(hasText("Sign out") and hasClickAction()).assertDoesNotExist()
    }
}
