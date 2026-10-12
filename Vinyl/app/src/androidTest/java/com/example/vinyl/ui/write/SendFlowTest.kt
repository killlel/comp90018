package com.example.vinyl.ui.write

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.vinyl.ui.theme.VinylTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The sending side: the "Write a music card" form, driven through the real [WriteCardScreen] and
 * its view models. These checks stay on the form itself, so nothing is sent and no song search is
 * needed: what the writer sees, that the message counter follows what they type, and that
 * Preview and Send stay switched off until the card is complete.
 *
 * Choosing a song searches iTunes and sending writes to Supabase, so those two steps are covered
 * by the smoke test and by hand rather than here.
 */
class SendFlowTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun showTheForm() {
        compose.setContent { VinylTheme { WriteCardScreen() } }
    }

    /** The form is a lazy list, so a row below the fold has to be scrolled to before it exists. */
    private fun scrollTo(text: String) {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
    }

    @Test
    fun theFormIntroducesItself() {
        compose.onNodeWithText("Write a music card").assertIsDisplayed()
        compose.onNodeWithText("A song and a few words, for someone.").assertIsDisplayed()
    }

    @Test
    fun songMessageAndMoodAreMarkedAsRequired() {
        compose.onNodeWithText("SONG").assertIsDisplayed()
        compose.onNodeWithText("MESSAGE").assertIsDisplayed()
        scrollTo("MOOD")
        compose.onNodeWithText("MOOD").assertIsDisplayed()
        // The mood is the only one still empty, so it is the only one that says so.
        compose.onNodeWithText("Required").assertIsDisplayed()
    }

    @Test
    fun theSongSearchFieldIsThere() {
        compose.onNodeWithContentDescription("Search song or artist").assertIsDisplayed()
    }

    @Test
    fun theMessageCounterFollowsWhatIsTyped() {
        compose.onNodeWithText("0 / 280").assertIsDisplayed()

        compose.onNodeWithText("Write a message…").performTextInput("hello")

        compose.onNodeWithText("5 / 280").assertIsDisplayed()
    }

    @Test
    fun previewAndSendStayOffUntilTheCardIsComplete() {
        // A message alone is not enough: there is still no song and no mood.
        compose.onNodeWithText("Write a message…").performTextInput("hello")

        scrollTo("Preview")
        compose.onNode(hasText("Preview") and hasClickAction()).assertIsNotEnabled()
        compose.onNode(hasText("Send") and hasClickAction()).assertIsNotEnabled()
    }
}