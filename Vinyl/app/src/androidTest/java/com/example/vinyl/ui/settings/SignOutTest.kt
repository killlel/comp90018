package com.example.vinyl.ui.settings

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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

    // The Settings row and the dialog's confirm button are both labelled "Sign out" (and the
    // dialog title too), so the confirm button is picked out by being clickable inside the dialog.
    private val confirmButton = hasText("Sign out") and hasClickAction() and hasAnyAncestor(isDialog())

    /** Opens Settings and taps the Sign out row, which raises the confirmation dialog. */
    private fun showSignOutDialog() {
        compose.setContent {
            VinylTheme { SettingsScreen(onBack = {}, onSignOut = { signOutCalls++ }) }
        }
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
    }

    @Test
    fun confirmingTheDialogSignsOut() {
        showSignOutDialog()
        compose.onNode(confirmButton).performClick()
        compose.waitForIdle()
        assertEquals(1, signOutCalls)
    }

    @Test
    fun cancellingTheDialogKeepsTheUserSignedIn() {
        showSignOutDialog()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertEquals(0, signOutCalls)
        compose.onNode(confirmButton).assertDoesNotExist()
    }
}