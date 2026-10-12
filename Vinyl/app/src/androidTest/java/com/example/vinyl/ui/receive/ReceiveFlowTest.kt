package com.example.vinyl.ui.receive

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.vinyl.ui.daily.ArrivedRecordOption
import com.example.vinyl.ui.daily.ArrivedTodayScreen
import com.example.vinyl.ui.daily.ArrivedTodayUiState
import com.example.vinyl.ui.daily.UnopenedRecordScreen
import com.example.vinyl.ui.daily.UnopenedRecordUiState
import com.example.vinyl.ui.theme.VinylTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The receiving side, one screen at a time: Arrived Today (the sealed cards), the sealed envelope,
 * and the opened music card. Each screen is driven with fixed state, so no network or sign-in is
 * needed and the checks are about what the reader is shown and what each tap reports.
 *
 * The anonymity checks matter most: until a letter is opened, nothing on screen may name the song,
 * the artist or the sender.
 */
class ReceiveFlowTest {
    @get:Rule val compose = createComposeRule()

    private fun option(
        id: String,
        mood: String,
        song: String = "Slow Rain, Rooftop",
        artist: String = "Marin Ochre",
        distance: String? = "2.4 km",
        wildcard: Boolean = false,
    ) = ArrivedRecordOption(
        id = id,
        trackName = song,
        artistName = artist,
        messagePreview = "I played this the night I moved out",
        moodLabel = mood,
        distanceLabel = distance,
        isWildcard = wildcard,
    )

    // ---- Arrived Today: the sealed cards ------------------------------------------------------

    private fun showArrivedToday(
        options: List<ArrivedRecordOption>,
        fallbackNote: String? = null,
        onSelect: (ArrivedRecordOption) -> Unit = {},
        onNotNow: () -> Unit = {},
        onRetry: (() -> Unit)? = null,
    ) = compose.setContent {
        VinylTheme {
            ArrivedTodayScreen(
                state = ArrivedTodayUiState(moodLabel = "Nostalgic", fallbackNote = fallbackNote, options = options),
                onSelect = onSelect,
                onNotNow = onNotNow,
                animateIn = false,
                onRetry = onRetry,
            )
        }
    }

    @Test
    fun sealedCardsNameTheMoodButNotTheSongOrArtist() {
        showArrivedToday(listOf(option("1", "Nostalgic"), option("2", "Energetic", song = "Kitchen Light, 2am", artist = "Sora Lin")))

        compose.onNodeWithText("Arrived today").assertIsDisplayed()
        compose.onNodeWithText("A nostalgic record").assertIsDisplayed()
        compose.onNodeWithText("An energetic record").assertIsDisplayed()
        compose.onNodeWithText("Both are yours to open.").assertIsDisplayed()

        // Nothing identifying the track is on the outside of the envelope.
        compose.onAllNodesWithText("Slow Rain, Rooftop").assertCountEquals(0)
        compose.onAllNodesWithText("Marin Ochre").assertCountEquals(0)
        compose.onAllNodesWithText("Kitchen Light, 2am").assertCountEquals(0)
        compose.onAllNodesWithText("Sora Lin").assertCountEquals(0)
    }

    @Test
    fun tappingASealedCardReportsThatOption() {
        var selected: ArrivedRecordOption? = null
        showArrivedToday(
            options = listOf(option("1", "Nostalgic"), option("2", "Energetic")),
            onSelect = { selected = it },
        )

        compose.onNodeWithText("An energetic record").performClick()

        assertEquals("2", selected?.id)
    }

    @Test
    fun theHandsRandomPickIsLabelled() {
        showArrivedToday(listOf(option("1", "Nostalgic"), option("2", "Calm", wildcard = true)))

        compose.onAllNodesWithText("Wildcard · picked at random").assertCountEquals(1)
    }

    @Test
    fun backToHomeReportsOnce() {
        val notNow = AtomicInteger()
        showArrivedToday(listOf(option("1", "Nostalgic")), onNotNow = { notNow.incrementAndGet() })

        compose.onNodeWithText("Back to Home").performClick()

        assertEquals(1, notNow.get())
    }

    @Test
    fun anEmptyHandShowsTheNoteAndOnlyOffersRetryWhenAskedTo() {
        showArrivedToday(emptyList(), fallbackNote = "No music cards yet. Check back later.")

        compose.onNodeWithText("No music cards yet. Check back later.").assertIsDisplayed()
        compose.onAllNodesWithText("Try again").assertCountEquals(0)
    }

    @Test
    fun aFailedLoadOffersTryAgain() {
        val retries = AtomicInteger()
        showArrivedToday(
            options = emptyList(),
            fallbackNote = "Couldn't load today's music cards. Check your connection and try again.",
            onRetry = { retries.incrementAndGet() },
        )

        compose.onNodeWithText("Try again").performClick()

        assertEquals(1, retries.get())
    }

    // ---- The sealed envelope ------------------------------------------------------------------

    private fun showEnvelope(
        distanceLabel: String? = "2.4 km",
        onOpen: () -> Unit = {},
        onOpening: () -> Unit = {},
        onBack: () -> Unit = {},
    ) = compose.setContent {
        VinylTheme {
            UnopenedRecordScreen(
                state = UnopenedRecordUiState(
                    distanceLabel = distanceLabel,
                    moodLabel = "Nostalgic",
                    sentTimeLabel = "Just now",
                    sentDateLabel = "8 Oct",
                ),
                onOpen = onOpen,
                onBack = onBack,
                onOpening = onOpening,
            )
        }
    }

    private val envelope get() = compose.onNode(hasContentDescription("Sealed envelope", substring = true))

    @Test
    fun theEnvelopeSaysHowFarAwayItCameFromAndNothingElse() {
        showEnvelope()

        compose.onNodeWithText("Open your music card").assertIsDisplayed()
        compose.onNodeWithText("Someone 2.4 km away").assertIsDisplayed()
        compose.onNodeWithText("Sent 8 Oct").assertIsDisplayed()
    }

    @Test
    fun withoutALocationTheSenderIsJustSomeoneSomewhere() {
        showEnvelope(distanceLabel = null)

        compose.onNodeWithText("Someone, somewhere").assertIsDisplayed()
    }

    @Test
    fun aQuickTapDoesNotOpenTheEnvelope() {
        val opening = AtomicInteger()
        val opened = AtomicInteger()
        showEnvelope(onOpen = { opened.incrementAndGet() }, onOpening = { opening.incrementAndGet() })

        envelope.performClick()
        compose.waitForIdle()

        assertEquals(0, opening.get())
        assertEquals(0, opened.get())
    }

    @Test
    fun pressAndHoldOpensTheEnvelopeOnce() {
        val opening = AtomicInteger()
        val opened = AtomicInteger()
        showEnvelope(onOpen = { opened.incrementAndGet() }, onOpening = { opening.incrementAndGet() })

        // The hold needs 1.2 s; the flap and letter animations add about 1.6 s before it moves on.
        envelope.performTouchInput { longClick(durationMillis = 1_500) }
        compose.waitUntil(timeoutMillis = 10_000) { opened.get() > 0 }

        assertEquals(1, opening.get())
        assertEquals(1, opened.get())
    }

    @Test
    fun theBackArrowGoesBack() {
        val back = AtomicInteger()
        showEnvelope(onBack = { back.incrementAndGet() })

        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, back.get())
    }

    // ---- The opened music card ----------------------------------------------------------------

    private fun showMusicCard(
        state: MusicCardUiState = musicCard(),
        onToggleKeep: (() -> Unit)? = {},
        onOpenCompass: () -> Unit = {},
        onPlayOnTurntable: (() -> Unit)? = {},
    ) = compose.setContent {
        VinylTheme {
            MusicCardScreen(
                state = state,
                onToggleKeep = onToggleKeep,
                onOpenCompass = onOpenCompass,
                onPlayOnTurntable = onPlayOnTurntable,
            )
        }
    }

    private fun musicCard(
        isKept: Boolean = false,
        hasDirection: Boolean = true,
        previewUrl: String? = null,
        keepError: String? = null,
    ) = MusicCardUiState(
        trackName = "Slow Rain, Rooftop",
        artistName = "Marin Ochre",
        previewUrl = previewUrl,
        message = "I played this the night I moved out",
        sentDateLabel = "8 Oct",
        hasDirection = hasDirection,
        isKept = isKept,
        keepError = keepError,
    )

    @Test
    fun anOpenedCardShowsTheSongTheNoteAndAnAnonymousSignature() {
        showMusicCard()

        compose.onNodeWithText("Card details").assertIsDisplayed()
        compose.onAllNodesWithText("Slow Rain, Rooftop").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("Marin Ochre").onFirst().assertIsDisplayed()
        compose.onNodeWithText("I played this the night I moved out").assertIsDisplayed()
        compose.onNodeWithText("- someone, somewhere").assertIsDisplayed()
        compose.onNodeWithText("Sent 8 Oct").assertIsDisplayed()
    }

    @Test
    fun addToCollectionReportsTheTap() {
        val keeps = AtomicInteger()
        showMusicCard(onToggleKeep = { keeps.incrementAndGet() })

        compose.onNodeWithText("Add to collection").performClick()

        assertEquals(1, keeps.get())
    }

    @Test
    fun aKeptCardSaysSo() {
        showMusicCard(state = musicCard(isKept = true))

        compose.onNodeWithText("In collection").assertIsDisplayed()
        compose.onAllNodesWithText("Add to collection").assertCountEquals(0)
    }

    @Test
    fun theCompassChipOnlyAppearsWhenThereIsADirection() {
        showMusicCard(state = musicCard(hasDirection = false))

        compose.onAllNodesWithText("See where it's from").assertCountEquals(0)
    }

    @Test
    fun theCompassChipOpensTheCompass() {
        val compass = AtomicInteger()
        showMusicCard(onOpenCompass = { compass.incrementAndGet() })

        compose.onNodeWithText("See where it's from").performClick()

        assertEquals(1, compass.get())
    }

    @Test
    fun playOnTurntableReportsTheTap() {
        val plays = AtomicInteger()
        showMusicCard(onPlayOnTurntable = { plays.incrementAndGet() })

        compose.onNodeWithText("Play on turntable").performClick()

        assertEquals(1, plays.get())
    }

    @Test
    fun aSongWithNoPreviewSaysSo() {
        showMusicCard(state = musicCard(previewUrl = null))

        compose.onNodeWithContentDescription("No preview for this song").assertIsDisplayed()
    }

    @Test
    fun aFailedKeepShowsWhy() {
        showMusicCard(state = musicCard(keepError = "Couldn't update your shelf. Try again."))

        compose.onNodeWithText("Couldn't update your shelf. Try again.").assertIsDisplayed()
    }
}