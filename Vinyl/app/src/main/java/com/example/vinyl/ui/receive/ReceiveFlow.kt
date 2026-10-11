package com.example.vinyl.ui.receive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.vinyl.data.MoodOptions
import com.example.vinyl.data.MoodTag
import com.example.vinyl.notification.ReminderPrefs
import com.example.vinyl.ui.components.AnimatedBottomSheet
import com.example.vinyl.ui.components.rememberLastNonNull
import com.example.vinyl.ui.daily.ArrivedRecordOption
import com.example.vinyl.ui.daily.ArrivedTodayLoading
import com.example.vinyl.ui.daily.ArrivedTodayScreen
import com.example.vinyl.ui.daily.ArrivedTodayUiState
import com.example.vinyl.ui.daily.DailyGenresViewModel
import com.example.vinyl.ui.daily.MoodQuestionnaireScreen
import com.example.vinyl.ui.daily.RoomViewModel
import com.example.vinyl.ui.daily.UnopenedRecordScreen
import com.example.vinyl.ui.daily.UnopenedRecordUiState
import com.example.vinyl.ui.daily.openKey
import com.example.vinyl.ui.daily.toArrivedOption
import com.example.vinyl.ui.home.HomeViewModel
import com.example.vinyl.ui.location.LocationViewModel
import com.example.vinyl.ui.write.EnvelopeStyle

/**
 * Where the user currently is within the receive flow (Questionnaire -> Arrived Today ->
 * Unopened -> Opened/received). Null means the flow isn't active and the normal tabs show.
 */
internal sealed class ReceiveFlowStep {
    object Questionnaire : ReceiveFlowStep()
    object ArrivedToday : ReceiveFlowStep()
    data class Unopened(val option: ArrivedRecordOption) : ReceiveFlowStep()
    data class Opened(val option: ArrivedRecordOption) : ReceiveFlowStep()
    /** The compass screen, reached by tapping "Tap for direction" on the opened letter. */
    data class Direction(val option: ArrivedRecordOption) : ReceiveFlowStep()
}

/** The step shown in the bottom sheet; the rest (ArrivedToday, Unopened, Opened, Direction) are full-screen. */
private val ReceiveFlowStep.isSheet: Boolean
    get() = this is ReceiveFlowStep.Questionnaire

/**
 * The receive flow, drawn on top of everything (including the bottom bar) while it is active, one
 * step at a time. Only the mood question is a sheet; the other steps are full-screen. The step and
 * the chosen mood belong to the caller (VinylApp in MainActivity) because Home and the reminder also start the flow, so this
 * only reads them and reports changes.
 *
 * @param onBackToHome switches to the Home tab when the flow is left.
 * @param onShowOpenedCard shows an already opened letter in the receive-mode sheet.
 */
@Composable
internal fun ReceiveFlow(
    step: ReceiveFlowStep?,
    onStepChange: (ReceiveFlowStep?) -> Unit,
    dailyMood: MoodTag?,
    onDailyMoodChange: (MoodTag?) -> Unit,
    roomViewModel: RoomViewModel,
    homeViewModel: HomeViewModel,
    genresViewModel: DailyGenresViewModel,
    locationViewModel: LocationViewModel,
    onBackToHome: () -> Unit,
    onShowOpenedCard: (ArrivedRecordOption) -> Unit,
    onPlayOnTurntable: (ArrivedRecordOption) -> Unit,
) {
    // The hand of cards Arrived Today last showed, so its rise-in plays once per hand. Lives here,
    // not in ArrivedTodayStep, so it survives stepping into an envelope and back.
    val risenHand = remember { RisenHand() }

    // Home's "Arrived today" shelf shows the latest hand, so it reloads after the flow closes in
    // case a new one was dealt.
    var wasReceiving by remember { mutableStateOf(false) }
    LaunchedEffect(step == null) {
        if (step == null && wasReceiving) homeViewModel.refresh()
        wasReceiving = step != null
    }

    MoodSheet(
        step = step,
        dailyMood = dailyMood,
        onDailyMoodChange = onDailyMoodChange,
        roomViewModel = roomViewModel,
        genresViewModel = genresViewModel,
        onStepChange = onStepChange,
    )

    when (step) {
        ReceiveFlowStep.ArrivedToday -> ArrivedTodayStep(
            dailyMood = dailyMood,
            risenHand = risenHand,
            roomViewModel = roomViewModel,
            homeViewModel = homeViewModel,
            genresViewModel = genresViewModel,
            locationViewModel = locationViewModel,
            onOpenSealed = { onStepChange(ReceiveFlowStep.Unopened(it)) },
            onShowOpened = {
                onStepChange(null)
                onShowOpenedCard(it)
            },
            // "Back to Home": closes the flow without pulling again or reopening the mood sheet.
            onNotNow = {
                onStepChange(null)
                onBackToHome()
            },
        )

        is ReceiveFlowStep.Unopened -> UnopenedStep(
            option = step.option,
            homeViewModel = homeViewModel,
            onOpen = { onStepChange(ReceiveFlowStep.Opened(step.option)) },
            onBack = { onStepChange(ReceiveFlowStep.ArrivedToday) },
        )

        is ReceiveFlowStep.Opened -> OpenedStep(
            option = step.option,
            roomViewModel = roomViewModel,
            homeViewModel = homeViewModel,
            onOpenCompass = { onStepChange(ReceiveFlowStep.Direction(step.option)) },
            onPlayOnTurntable = { onPlayOnTurntable(step.option) },
        )

        is ReceiveFlowStep.Direction -> DirectionStep(
            option = step.option,
            locationViewModel = locationViewModel,
            onBack = { onStepChange(ReceiveFlowStep.Opened(step.option)) },
        )

        else -> Unit
    }
}

/**
 * Holds the hand of cards Arrived Today last showed. A plain holder rather than state: updating it
 * must not trigger another recomposition.
 */
private class RisenHand {
    var cards: Any? = null
}

/**
 * The mood question: slides up when the flow opens and back down when it closes or moves on. While
 * sliding out it keeps drawing what it held.
 */
@Composable
private fun MoodSheet(
    step: ReceiveFlowStep?,
    dailyMood: MoodTag?,
    onDailyMoodChange: (MoodTag?) -> Unit,
    roomViewModel: RoomViewModel,
    genresViewModel: DailyGenresViewModel,
    onStepChange: (ReceiveFlowStep?) -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val reminderPrefs = remember(appContext) { ReminderPrefs(appContext) }
    // The chips' options and selection, as slugs. Reset to empty ("my favourites") on every open.
    val genresState by genresViewModel.uiState.collectAsState()

    val sheetStep = step?.takeIf { it.isSheet }
    val shownSheetStep = rememberLastNonNull(sheetStep)
    AnimatedBottomSheet(
        visible = sheetStep != null,
        onDismiss = { onStepChange(null) },
        dismissOnSwipe = shownSheetStep == ReceiveFlowStep.Questionnaire,
    ) {
        when (shownSheetStep) {
            ReceiveFlowStep.Questionnaire -> {
                MoodQuestionnaireScreen(
                    selectedMood = dailyMood,
                    genreOptions = genresState.options,
                    selectedGenres = genresState.selected,
                    anyGenre = genresState.anyGenre,
                    // Tapping the selected mood again clears it.
                    onMoodSelected = { onDailyMoodChange(if (dailyMood == it) null else it) },
                    onGenreToggled = genresViewModel::toggle,
                    onPickAnyGenre = genresViewModel::pickAnyGenre,
                    onPickFavourites = genresViewModel::pickFavourites,
                    // Load here rather than on entering Arrived Today: Unopened's back button
                    // returns there, and request_recommendations records new matches on every
                    // call — loading on entry would deal a fresh hand each time.
                    onSubmit = {
                        reminderPrefs.markPulledToday()
                        roomViewModel.load(dailyMood, genresViewModel.genresToSend())
                        onStepChange(ReceiveFlowStep.ArrivedToday)
                    },
                    onLetCrateDecide = {
                        reminderPrefs.markPulledToday()
                        // Deals a fresh hand on a random mood; kept in dailyMood so "Try again" reuses it.
                        val randomMood = MoodTag.entries.random()
                        onDailyMoodChange(randomMood)
                        roomViewModel.load(randomMood, genresViewModel.genresToSend())
                        onStepChange(ReceiveFlowStep.ArrivedToday)
                    },
                    onBack = { onStepChange(null) },
                )
            }

            else -> Unit
        }
    }
}

/** The picker of sealed envelopes, or its loading and error states. */
@Composable
private fun ArrivedTodayStep(
    dailyMood: MoodTag?,
    risenHand: RisenHand,
    roomViewModel: RoomViewModel,
    homeViewModel: HomeViewModel,
    genresViewModel: DailyGenresViewModel,
    locationViewModel: LocationViewModel,
    onOpenSealed: (ArrivedRecordOption) -> Unit,
    onShowOpened: (ArrivedRecordOption) -> Unit,
    onNotNow: () -> Unit,
) {
    val roomState by roomViewModel.uiState.collectAsState()
    val homeState by homeViewModel.uiState.collectAsState()
    val locationState by locationViewModel.uiState.collectAsState()

    if (roomState.isLoading) {
        ArrivedTodayLoading()
        return
    }

    // The cards rise in once per hand; coming back from the envelope shows them in place.
    val animateIn = roomState.cards !== risenHand.cards
    SideEffect { risenHand.cards = roomState.cards }

    ArrivedTodayScreen(
        state = ArrivedTodayUiState(
            moodLabel = MoodOptions.all.firstOrNull { it.tag == dailyMood }?.title ?: "Surprise",
            // No genre label: genre only nudges the ranking, so naming it here would promise a
            // filter that the records may not match.
            fallbackNote = when {
                roomState.error != null ->
                    "Couldn't load today's music cards. Check your connection and try again."
                roomState.cards.isEmpty() -> "No music cards yet. Check back later."
                else -> null
            },
            // Only the envelopes still sealed; opened ones are already on the shelf.
            options = roomState.cards
                .filterNot { it.openKey in homeState.openedKeys }
                .map { it.toArrivedOption(readerLat = locationState.lat, readerLng = locationState.lng) },
        ),
        onSelect = { option ->
            if (option.sessionKey in homeState.openedKeys) onShowOpened(option) else onOpenSealed(option)
        },
        onNotNow = onNotNow,
        animateIn = animateIn,
        onRetry = if (roomState.error != null) {
            { roomViewModel.load(dailyMood, genresViewModel.genresToSend()) }
        } else {
            null
        },
    )
}

/** One sealed envelope, waiting to be opened by shake or press-and-hold. */
@Composable
private fun UnopenedStep(
    option: ArrivedRecordOption,
    homeViewModel: HomeViewModel,
    onOpen: () -> Unit,
    onBack: () -> Unit,
) {
    UnopenedRecordScreen(
        state = UnopenedRecordUiState(
            distanceLabel = option.distanceLabel,
            moodLabel = option.moodLabel,
            sentTimeLabel = option.sentTimeLabel,
            distanceNote = option.distanceNote,
            mood = option.mood,
            sentDateLabel = option.sentDateLabel,
            envelopeStyle = EnvelopeStyle.fromSlug(option.envelopeStyle),
        ),
        onOpen = {
            homeViewModel.markOpened(option.sessionKey)
            onOpen()
        },
        onOpening = { homeViewModel.markOpened(option.sessionKey) },
        onBack = onBack,
    )
}

/** The opened letter as a music card. */
@Composable
private fun OpenedStep(
    option: ArrivedRecordOption,
    roomViewModel: RoomViewModel,
    homeViewModel: HomeViewModel,
    onOpenCompass: () -> Unit,
    onPlayOnTurntable: () -> Unit,
) {
    val roomState by roomViewModel.uiState.collectAsState()
    LaunchedEffect(option.sessionKey) { homeViewModel.markOpened(option.sessionKey) }
    val submissionId = option.submissionId

    MusicCardScreen(
        state = MusicCardUiState(
            trackName = option.trackName,
            artistName = option.artistName,
            artworkUrl = option.artworkUrl,
            previewUrl = option.previewUrl,
            mood = option.mood,
            message = option.messagePreview,
            sentDateLabel = option.sentDateLabel,
            hasDirection = option.distanceLabel != null,
            isKept = submissionId != null && submissionId in roomState.keptIds,
            keepError = roomState.keepError,
        ),
        onToggleKeep = { submissionId?.let(roomViewModel::toggleKeep) },
        onOpenCompass = onOpenCompass,
        onPlayOnTurntable = onPlayOnTurntable,
    )
}

/**
 * The compass pointing at the sender's city. Full-screen with its own back arrow, the same style as
 * UnopenedRecordScreen, not a bottom sheet.
 */
@Composable
private fun DirectionStep(
    option: ArrivedRecordOption,
    locationViewModel: LocationViewModel,
    onBack: () -> Unit,
) {
    val locationState by locationViewModel.uiState.collectAsState()
    val compassState by rememberCompassHeading(
        enabled = true,
        readerLat = locationState.lat,
        readerLng = locationState.lng,
        senderLat = option.senderLat,
        senderLng = option.senderLng,
    )
    val senderCityLabel by rememberSenderCityLabel(
        senderLat = option.senderLat,
        senderLng = option.senderLng,
    )
    CompassScreen(
        state = CompassUiState(
            distanceLabel = option.distanceLabel,
            cityLabel = senderCityLabel,
        ),
        compassState = compassState,
        onBack = onBack,
    )
}