package com.example.vinyl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.tooling.preview.Preview
import com.example.vinyl.ui.daily.ReceiveFlowStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.data.GoogleAuthRepository
import com.example.vinyl.data.GoogleSignInOutcome
import com.example.vinyl.data.MoodOptions
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.Supabase
import com.example.vinyl.data.onboarding.OnboardingRepository
import com.example.vinyl.ui.collection.CollectionScreen
import com.example.vinyl.ui.home.HomeAvatar
import com.example.vinyl.ui.home.HomeScreen
import com.example.vinyl.ui.home.HomeViewModel
import com.example.vinyl.ui.home.NowPlaying
import com.example.vinyl.ui.home.PlaybackViewModel
import com.example.vinyl.ui.daily.ArrivedRecordOption
import com.example.vinyl.ui.daily.ArrivedTodayLoading
import com.example.vinyl.ui.daily.ArrivedTodayScreen
import com.example.vinyl.ui.daily.ArrivedTodayUiState
import com.example.vinyl.ui.daily.MoodQuestionnaireScreen
import com.example.vinyl.ui.daily.RoomViewModel
import com.example.vinyl.ui.daily.ReceiveCardStore
import com.example.vinyl.ui.daily.DailyGenresViewModel
import com.example.vinyl.ui.daily.UnopenedRecordScreen
import com.example.vinyl.ui.daily.UnopenedRecordUiState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.example.vinyl.ui.theme.PoppinsFontFamily
import com.example.vinyl.ui.onboarding.OnboardingButtonHeight
import com.example.vinyl.ui.onboarding.OnboardingHorizontalPadding
import com.example.vinyl.ui.onboarding.OnboardingActionMinimumHeight
import com.example.vinyl.ui.onboarding.OnboardingBottomPadding
import com.example.vinyl.ui.onboarding.OnboardingPagerScreen
import com.example.vinyl.ui.daily.toArrivedOption
import com.example.vinyl.ui.location.LocationGateScreen
import com.example.vinyl.ui.location.LocationSettingsScreen
import com.example.vinyl.ui.location.LocationUiState
import com.example.vinyl.ui.settings.SettingsScreen
import com.example.vinyl.ui.settings.rememberAvatarAppearance
import com.example.vinyl.ui.location.LocationViewModel
import com.example.vinyl.ui.receive.MusicCardScreen
import com.example.vinyl.ui.receive.MusicCardUiState
import com.example.vinyl.ui.receive.ReceivedCardScreen
import com.example.vinyl.ui.receive.ReceivedCardUiState
import com.example.vinyl.ui.receive.CompassScreen
import com.example.vinyl.ui.receive.CompassUiState
import com.example.vinyl.ui.receive.CompassState
import com.example.vinyl.ui.receive.rememberCompassHeading
import com.example.vinyl.ui.receive.rememberSenderCityLabel
import com.example.vinyl.ui.collection.CollectionViewModel
import com.example.vinyl.data.model.VinylRecord
import com.example.vinyl.data.model.RecordSource
import com.example.vinyl.ui.daily.distanceLabelAndNote
import com.example.vinyl.ui.daily.sentTimeLabel
import com.example.vinyl.ui.daily.sentDateLabel
import com.example.vinyl.repository.RoomRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.example.vinyl.ui.theme.VinylPalette
import com.example.vinyl.ui.theme.VinylTheme
import com.example.vinyl.ui.write.WriteCardScreen
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.example.vinyl.notification.DailyReminder
import com.example.vinyl.notification.ReminderPrefs
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    // Set when the auth callback comes back carrying an error instead of a code. Held here rather
    // than in AuthScreen because the callback arrives at the activity, not at the composition.
    private var authCallbackError by mutableStateOf<String?>(null)

    // Set when the daily reminder is tapped; VinylApp opens the receive flow and clears it.
    // Activity-level for the same reason as authCallbackError: the intent arrives here.
    private var openReceiveRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cold-start leg of the browser sign-in fallback: the app was killed while the user was in
        // the browser, so the callback arrives as the launch intent. handleDeeplinks() ignores
        // anything that isn't com.example.vinyl://auth-callback, so this is safe on a normal launch.
        handleAuthDeeplink(intent)
        handleReminderTap(intent)
        DailyReminder.createChannel(this)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            VinylTheme {
                val sessionStatus by Supabase.client.auth.sessionStatus.collectAsState()
                val inApp = sessionStatus is SessionStatus.Authenticated

                // Sign-out runs here rather than in VinylApp: the session clears part-way through,
                // which removes VinylApp from composition and would cancel its scope before the
                // Credential Manager cleanup ran.
                val authScope = rememberCoroutineScope()
                val googleAuthRepository = remember { GoogleAuthRepository(applicationContext) }
                val signOut: () -> Unit = {
                    authScope.launch {
                        googleAuthRepository.signOut()
                        DailyReminder.stop(applicationContext)
                    }
                }

                // The app's view models are activity-scoped, so they outlive a sign-out and would
                // show the previous user's location, cards and collection to the next one.
                var wasInApp by remember { mutableStateOf(false) }
                LaunchedEffect(inApp) {
                    if (wasInApp && !inApp) viewModelStore.clear()
                    wasInApp = inApp
                }

                // Re-aims the daily reminder on every launch, so a changed clock or time zone is
                // picked up. Not while signed out: there's nothing to come back to.
                LaunchedEffect(inApp) { if (inApp) DailyReminder.sync(applicationContext) }

                if (inApp) {
                    OnboardingGate {
                        VinylApp(
                            onSignOut = signOut,
                            openReceiveRequested = openReceiveRequested,
                            onReceiveOpened = { openReceiveRequested = false },
                        )
                    }
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        AuthScreen(
                            modifier = Modifier.padding(innerPadding),
                            callbackError = authCallbackError,
                            onClearCallbackError = { authCallbackError = null },
                        )
                    }
                }
            }
        }
    }

    // Warm leg, and the common one: MainActivity is launchMode=singleTask, so the callback is
    // delivered to the running instance instead of starting a second one.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeeplink(intent)
        handleReminderTap(intent)
    }

    private fun handleReminderTap(intent: Intent) {
        if (!intent.getBooleanExtra(DailyReminder.EXTRA_OPEN_RECEIVE, false)) return
        // Consumed once, so rotating or returning to the app doesn't reopen the flow.
        intent.removeExtra(DailyReminder.EXTRA_OPEN_RECEIVE)
        openReceiveRequested = true
    }

    // Exchanges the PKCE code in the callback for a session. On success the session lands in
    // Supabase's sessionStatus flow, which the gate in setContent is already collecting, so the
    // app switches itself out of AuthScreen with no further wiring.
    private fun handleAuthDeeplink(intent: Intent) {
        val data = intent.data ?: return
        val config = Supabase.client.auth.config
        if (data.scheme != config.scheme || data.host != config.host) return

        // handleDeeplinks() drops an error-carrying callback silently, which reads as "nothing
        // happened" on screen. Read the error params ourselves before handing the intent over.
        val error = data.getQueryParameter("error_description")
            ?: data.getQueryParameter("error")
        if (error != null) {
            Log.e(TAG, "Auth callback returned an error: $error")
            authCallbackError = "Google sign-in couldn't be completed. Please try again."
            return
        }

        authCallbackError = null
        Supabase.client.handleDeeplinks(
            intent = intent,
            onError = {
                Log.e(TAG, "Auth deeplink exchange failed", it)
                authCallbackError = "Google sign-in couldn't be completed. Please try again."
            },
        )
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}

private enum class AppTab(val label: String) {
    Collection("Collection"),
    Home("Home"),
    Create("Create"),
}

/**
 * Where the user currently is within the receive flow (Questionnaire -> Arrived Today ->
 * Unopened -> Opened/received). Null means the flow isn't active and the normal tabs show.
 */
private sealed class ReceiveFlowStep {
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
 * Runs the onboarding flow once per account (`profiles.onboarding_completed`), then
 * hands over to the app. Replaces the old `LocationGate` wrapper — the location ask is now page 3
 * of [OnboardingPagerScreen] instead of living here on its own, per that screen's original
 * docstring.
 *
 * Reads the flag once on entry rather than through [com.example.vinyl.ui.onboarding.OnboardingViewModel],
 * since that view model's state doesn't carry `onboarding_completed` — it only tracks the fields
 * onboarding itself edits.
 */
@Composable
private fun OnboardingGate(content: @Composable () -> Unit) {
    // null = still resolving, so the gate can't yet say which way to go.
    var onboardingCompleted by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        onboardingCompleted = OnboardingRepository().getMyProfile()
            .getOrNull()
            ?.onboardingCompleted
            ?: false
    }

    when (onboardingCompleted) {
        // Blank rather than a spinner: the read is usually a few hundred ms, and a spinner that
        // fast reads as a flicker.
        null -> Box(Modifier.fillMaxSize().background(VinylPalette.Background))
        false -> OnboardingPagerScreen(onOnboardingComplete = { onboardingCompleted = true })
        true -> content()
    }
}

@Composable
private fun VinylApp(
    onSignOut: () -> Unit,
    openReceiveRequested: Boolean = false,
    onReceiveOpened: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Home) }

    var receiveFlowStep by remember { mutableStateOf<ReceiveFlowStep?>(null) }
    val appContext = LocalContext.current.applicationContext
    val reminderPrefs = remember(appContext) { ReminderPrefs(appContext) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showLocationSettings by rememberSaveable { mutableStateOf(false) }

    val roomViewModel: RoomViewModel = viewModel()

    // Activity-scoped, the same instance CollectionScreen draws its shelves from. A record opened
    // there is shown below as a music card, over the bottom bar like the receive flow.
    val collectionViewModel: CollectionViewModel = viewModel()
    val collectionState by collectionViewModel.uiState.collectAsState()

    // Opening history survives app restarts and is local to the signed-in account on this device.
    val receiveCardStore = remember(appContext) { ReceiveCardStore(appContext) }
    var shelfCard by remember { mutableStateOf<ArrivedRecordOption?>(null) }
    var shelfCompassOpen by remember { mutableStateOf(false) }

    // "Removed. Undo" after taking a card off the Collection shelf.
    val snackbarHostState = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    val roomRepository = remember { RoomRepository() }

    // Same activity-scoped instance HomeScreen uses. Its "Arrived today" shelf shows the latest
    // hand, so it reloads after the receive flow closes in case a new one was dealt.
    val homeViewModel: HomeViewModel = viewModel()
    val homeState by homeViewModel.uiState.collectAsState()
    val roomState by roomViewModel.uiState.collectAsState()
    LaunchedEffect(roomState.cards) {
        if (roomState.cards.isNotEmpty()) homeViewModel.showToday(roomState.cards)
    }

    // What's on the Home turntable. "Play this song" on any music card starts it here and goes
    // to Home, so the needle drop is seen; HomeScreen reads the same instance.
    val playbackViewModel: PlaybackViewModel = viewModel()
    val playOnHome: (NowPlaying) -> Unit = { track ->
        receiveFlowStep = null
        shelfCard = null
        shelfCompassOpen = false
        collectionViewModel.closeRecord()
        selectedTab = AppTab.Home
        val current = playbackViewModel.nowPlaying.value
        val alreadyPlaying = current != null && current.submissionId == track.submissionId &&
            current.title == track.title && current.artist == track.artist
        if (alreadyPlaying) {
            if (playbackViewModel.clock.value?.isPaused == true) playbackViewModel.togglePause()
        } else {
            playbackViewModel.play(track)
        }
    }

    // "Play on turntable" on a received card, from page 4 or the shelf's details sheet.
    val playCardOnTurntable: (ArrivedRecordOption) -> Unit = { option ->
        homeViewModel.publishToday()
        val track = NowPlaying(
            submissionId = option.submissionId,
            title = option.trackName,
            artist = option.artistName,
            artworkUrl = option.artworkUrl,
            previewUrl = option.previewUrl,
        )
        playOnHome(track)
    }

    // Collection records use the same player without publishing a pending daily hand. A song in
    // the library can be unrelated to today's three cards.
    val playCollectionRecord: (VinylRecord) -> Unit = { record ->
        playOnHome(
            NowPlaying(
                submissionId = record.id,
                title = record.songName,
                artist = record.artist,
                artworkUrl = record.coverUrl,
                previewUrl = record.previewUrl,
            ),
        )
    }

    // Takes a card off the Collection shelf, closes its sheet and offers an undo. The undo keeps
    // the card again and restores its star, then reloads the Collection.
    val removeFromCollection: (VinylRecord) -> Unit = { record ->
        val countBefore = collectionState.totalCount
        collectionViewModel.closeRecord()
        collectionViewModel.remove(record)
        appScope.launch {
            // Waits for the remove to land (or fail) before offering to undo it.
            val settled = withTimeoutOrNull(RemoveSettleTimeoutMs) {
                collectionViewModel.uiState.first { it.actionError != null || it.totalCount < countBefore }
            } ?: return@launch
            if (settled.actionError != null) {
                snackbarHostState.showSnackbar(settled.actionError)
                return@launch
            }
            // Held for UndoSnackbarMs rather than the short default, long enough to reach Undo.
            // Timing out cancels the call, which takes the snackbar down; null means no undo.
            val result = withTimeoutOrNull(UndoSnackbarMs) {
                snackbarHostState.showSnackbar(
                    message = "Removed.",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Indefinite,
                )
            }
            if (result == SnackbarResult.ActionPerformed) {
                roomRepository.keep(record.id)
                    .onSuccess {
                        if (record.isFavourite) roomRepository.setFavourite(record.id, true)
                        collectionViewModel.refresh()
                    }
                    .onFailure { snackbarHostState.showSnackbar("Couldn't put it back. Try again.") }
            }
        }
    }

    // The hand of cards Arrived Today last showed, so its rise-in plays once per hand. A plain
    // holder rather than state: updating it must not trigger another recomposition.
    val risenHand = remember { object { var cards: Any? = null } }

    var wasReceiving by remember { mutableStateOf(false) }
    LaunchedEffect(receiveFlowStep == null) {
        if (receiveFlowStep == null && wasReceiving) homeViewModel.refresh()
        wasReceiving = receiveFlowStep != null
    }

    // The avatar is changed in Settings, so it's read again each time Settings opens or closes.
    val avatarAppearance = rememberAvatarAppearance()
    val homeAvatar = remember(showSettings) {
        HomeAvatar(avatarAppearance.iconIndex, avatarAppearance.gradientIndex, avatarAppearance.imageUrl)
    }

    // Activity-scoped, so it's the same instance the onboarding pager and settings screen use
    val locationViewModel: LocationViewModel = viewModel()
    val locationState by locationViewModel.uiState.collectAsState()

    // Resolves the city name for the stored centroid once it's loaded. No-op when it's already
    // known or nothing is stored, so the city list is searched at most once per launch.
    LaunchedEffect(locationState.hasLocation) { locationViewModel.loadCityLabel() }
    var dailyMood by remember { mutableStateOf<MoodTag?>(null) }
    // The chips' options and selection, as slugs. Reset to the onboarding favourites on every open.
    val dailyGenresViewModel: DailyGenresViewModel = viewModel()
    val dailyGenresState by dailyGenresViewModel.uiState.collectAsState()

    // Tapping the daily reminder lands on the mood question, same as Home's "Open today's music cards".
    LaunchedEffect(openReceiveRequested, homeState.isLoading) {
        if (!openReceiveRequested || homeState.isLoading) return@LaunchedEffect
        selectedTab = AppTab.Home
        if (homeState.arrivedToday.isEmpty() && homeState.error == null) {
            if (homeState.pendingToday.isNotEmpty()) {
                roomViewModel.showExisting(homeState.todayHand)
                receiveFlowStep = ReceiveFlowStep.ArrivedToday
            } else {
                dailyMood = null
                dailyGenresViewModel.reset()
                receiveFlowStep = ReceiveFlowStep.Questionnaire
            }
        }
        onReceiveOpened()
    }

    Scaffold(
        containerColor = VinylPalette.Background,
        // Sits above the bottom bar, drawn in the app's own panel style.
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                VinylSnackbar(
                    message = data.visuals.message,
                    actionLabel = data.visuals.actionLabel,
                    onAction = data::performAction,
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = VinylPalette.PanelDark,
                contentColor = VinylPalette.TextPrimary,
            ) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = VinylPalette.TealAccent,
                    selectedTextColor = VinylPalette.TealAccent,
                    unselectedIconColor = VinylPalette.TextMuted,
                    unselectedTextColor = VinylPalette.TextMuted,
                    indicatorColor = VinylPalette.Background,
                )

                NavigationBarItem(
                    selected = selectedTab == AppTab.Collection,
                    onClick = { selectedTab = AppTab.Collection },
                    icon = { Icon(Icons.Filled.List, contentDescription = "Collection") },
                    label = { Text("Collection") },
                    colors = itemColors,
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.Home,
                    onClick = { selectedTab = AppTab.Home },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    colors = itemColors,
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.Create,
                    onClick = { selectedTab = AppTab.Create },
                    icon = { Icon(Icons.Filled.Add, contentDescription = "Create") },
                    label = { Text("Create") },
                    colors = itemColors,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                AppTab.Collection -> CollectionScreen(viewModel = collectionViewModel)
                AppTab.Home -> HomeScreen(
                    onOpenReceive = {
                        if (!homeState.isLoading && homeState.error == null && homeState.arrivedToday.isEmpty()) {
                            if (homeState.pendingToday.isNotEmpty()) {
                                roomViewModel.showExisting(homeState.todayHand)
                                receiveFlowStep = ReceiveFlowStep.ArrivedToday
                            } else if (homeState.canPull) {
                                dailyMood = null
                                dailyGenresViewModel.reset()
                                receiveFlowStep = ReceiveFlowStep.Questionnaire
                            }
                        }
                    },
                    onOpenSettings = { showSettings = true },
                    avatar = homeAvatar,
                    // The picker uses exactly the hand on Home, with no load or draw on a card tap.
                    onOpenCard = { card ->
                        roomViewModel.showExisting(homeState.todayHand)
                        val option = card.toArrivedOption(readerLat = locationState.lat, readerLng = locationState.lng)
                        shelfCard = option
                    },
                )
                AppTab.Create -> WriteCardScreen()
            }
        }
    }

    // Settings sits above the bottom bar like the receive flow, with the location detail
    // layered over it so backing out lands on the settings list rather than the app.
    // These are overlays, not navigation destinations, so the system back gesture doesn't know
    // about them — without these it closes the whole app. Mutually exclusive, detail first.
    BackHandler(enabled = showLocationSettings) { showLocationSettings = false }
    BackHandler(enabled = showSettings && !showLocationSettings) { showSettings = false }

    // The receive flow is the same kind of overlay. Each step goes where its own on-screen
    // back or close button goes: an unopened record steps back to the picker, the compass back
    // to its card; every other step closes the flow. Declared last so it wins while the flow is on top.
    BackHandler(enabled = collectionState.openRecord != null) { collectionViewModel.closeRecord() }
    BackHandler(enabled = shelfCard != null) {
        if (shelfCompassOpen) shelfCompassOpen = false else shelfCard = null
    }
    BackHandler(enabled = receiveFlowStep != null) {
        // Arrived Today and the music card have no back arrow: back from them goes to Home.
        if (receiveFlowStep is ReceiveFlowStep.ArrivedToday || receiveFlowStep is ReceiveFlowStep.Opened) {
            selectedTab = AppTab.Home
        }
        receiveFlowStep = when (val step = receiveFlowStep) {
            is ReceiveFlowStep.Unopened -> ReceiveFlowStep.ArrivedToday
            // The compass's own back arrow returns to the card, so the system back does too.
            is ReceiveFlowStep.Direction -> ReceiveFlowStep.Opened(step.option)
            else -> null
        }
    }

    if (showSettings) {
        // Read each time Settings opens, so a re-rolled alias is never stale.
        val displayName by produceState("") {
            value = OnboardingRepository().getMyProfile().getOrNull()?.username.orEmpty()
        }
        SettingsScreen(
            displayName = displayName,
            locationValue = settingsLocationValue(locationState),
            onOpenLocation = { showLocationSettings = true },
            onSignOut = onSignOut,
            onBack = { showSettings = false },
        )
    }

    if (showLocationSettings) {
        LocationSettingsScreen(onBack = { showLocationSettings = false })
    }

    // The receive flow sits on top of everything (including the bottom bar) while active, one
    // step at a time. Only the mood question is a sheet: it slides up when the flow opens and
    // back down when it closes or moves on. While sliding out it keeps drawing what it held.
    val sheetStep = receiveFlowStep?.takeIf { it.isSheet }
    val shownSheetStep = rememberLastNonNull(sheetStep)
    AnimatedBottomSheet(
        visible = sheetStep != null,
        onDismiss = { receiveFlowStep = null },
        dismissOnSwipe = shownSheetStep == ReceiveFlowStep.Questionnaire,
    ) {
        when (shownSheetStep) {
            ReceiveFlowStep.Questionnaire -> {
                MoodQuestionnaireScreen(
                    selectedMood = dailyMood,
                    genreOptions = dailyGenresState.options,
                    selectedGenres = dailyGenresState.selected,
                    // Tapping the selected mood again clears it.
                    onMoodSelected = { dailyMood = if (dailyMood == it) null else it },
                    onGenreToggled = dailyGenresViewModel::toggle,
                    // Load here rather than on entering Arrived Today: Unopened's back button
                    // returns there, and request_recommendations records new matches on every
                    // call — loading on entry would deal a fresh hand each time.
                    onSubmit = {
                        reminderPrefs.markPulledToday()
                        roomViewModel.load(dailyMood, dailyGenresState.selected)
                        receiveFlowStep = ReceiveFlowStep.ArrivedToday
                    },
                    onLetCrateDecide = {
                        reminderPrefs.markPulledToday()
                        // Deals a fresh hand on a random mood; kept in dailyMood so "Try again" reuses it.
                        val randomMood = MoodTag.entries.random()
                        dailyMood = randomMood
                        roomViewModel.load(randomMood, dailyGenresState.selected)
                        receiveFlowStep = ReceiveFlowStep.ArrivedToday
                    },
                    onBack = { receiveFlowStep = null },
                )
            }

            else -> Unit
        }
    }

    // The full-screen steps, drawn over the sheet layer.
    when (val step = receiveFlowStep) {
        ReceiveFlowStep.ArrivedToday -> {
            val moodLabel = MoodOptions.all.firstOrNull { it.tag == dailyMood }?.title ?: "Surprise"

            if (roomState.isLoading) {
                ArrivedTodayLoading()
            } else {
                // The cards rise in once per hand; coming back from the envelope shows them in place.
                val animateIn = roomState.cards !== risenHand.cards
                SideEffect { risenHand.cards = roomState.cards }
                ArrivedTodayScreen(
                    state = ArrivedTodayUiState(
                        moodLabel = moodLabel,
                        // No genre label: genre only nudges the ranking, so naming it here
                        // would promise a filter that the records may not match.
                        fallbackNote = when {
                            roomState.error != null ->
                                "Couldn't load today's music cards. Check your connection and try again."
                            roomState.cards.isEmpty() -> "No music cards yet. Check back later."
                            else -> null
                        },
                        options = roomState.cards.map {
                            it.toArrivedOption(readerLat = locationState.lat, readerLng = locationState.lng)
                        },
                    ),
                    onSelect = { option ->
                        if (homeState.arrivedToday.isNotEmpty() || receiveCardStore.wasOpened(option.sessionKey)) {
                            receiveFlowStep = null
                            shelfCard = option
                        } else {
                            receiveFlowStep = ReceiveFlowStep.Unopened(option)
                        }
                    },
                    // "Back to Home": closes the flow without pulling again or reopening the mood sheet.
                    onNotNow = {
                        receiveFlowStep = null
                        selectedTab = AppTab.Home
                    },
                    animateIn = animateIn,
                    onRetry = if (roomState.error != null) {
                        { roomViewModel.load(dailyMood, dailyGenresState.selected) }
                    } else {
                        null
                    },
                )
            }
        }

        is ReceiveFlowStep.Unopened -> {
            UnopenedRecordScreen(
                state = UnopenedRecordUiState(
                    distanceLabel = step.option.distanceLabel,
                    moodLabel = step.option.moodLabel,
                    sentTimeLabel = step.option.sentTimeLabel,
                    distanceNote = step.option.distanceNote,
                    mood = step.option.mood,
                    sentDateLabel = step.option.sentDateLabel,
                ),
                onOpen = {
                    receiveCardStore.markOpened(step.option.sessionKey)
                    receiveFlowStep = ReceiveFlowStep.Opened(step.option)
                },
                onOpening = { receiveCardStore.markOpened(step.option.sessionKey) },
                onBack = { receiveFlowStep = ReceiveFlowStep.ArrivedToday },
            )
        }

        is ReceiveFlowStep.Opened -> {
            LaunchedEffect(step.option.sessionKey) { receiveCardStore.markOpened(step.option.sessionKey) }
            val submissionId = step.option.submissionId

            MusicCardScreen(
                state = MusicCardUiState(
                    trackName = step.option.trackName,
                    artistName = step.option.artistName,
                    artworkUrl = step.option.artworkUrl,
                    previewUrl = step.option.previewUrl,
                    mood = step.option.mood,
                    message = step.option.messagePreview,
                    sentDateLabel = step.option.sentDateLabel,
                    hasDirection = step.option.distanceLabel != null,
                    isKept = submissionId != null && submissionId in roomState.keptIds,
                    keepError = roomState.keepError,
                ),
                onToggleKeep = { submissionId?.let(roomViewModel::toggleKeep) },
                onOpenCompass = { receiveFlowStep = ReceiveFlowStep.Direction(step.option) },
                onPlayOnTurntable = { playCardOnTurntable(step.option) },
            )
        }

        // Not in the sheet - CompassScreen is full-screen with its own back
        // arrow, the same style as UnopenedRecordScreen, not a bottom sheet.
        is ReceiveFlowStep.Direction -> {
            val compassState by rememberCompassHeading(
                enabled = true,
                readerLat = locationState.lat,
                readerLng = locationState.lng,
                senderLat = step.option.senderLat,
                senderLng = step.option.senderLng,
            )
            val senderCityLabel by rememberSenderCityLabel(
                senderLat = step.option.senderLat,
                senderLng = step.option.senderLng,
            )
            CompassScreen(
                state = CompassUiState(
                    distanceLabel = step.option.distanceLabel,
                    cityLabel = senderCityLabel,
                ),
                compassState = compassState,
                onBack = { receiveFlowStep = ReceiveFlowStep.Opened(step.option) },
            )
        }

        else -> Unit
    }

    // An already opened card: receive mode in a sheet,
    // with the bookmark, the compass and "Play on turntable".
    val roomStateForShelf by roomViewModel.uiState.collectAsState()
    ShelfCardSheet(
        option = shelfCard,
        compassOpen = shelfCompassOpen,
        keptIds = roomStateForShelf.keptIds,
        keepError = roomStateForShelf.keepError,
        readerLat = locationState.lat,
        readerLng = locationState.lng,
        onToggleKeep = { id -> roomViewModel.toggleKeep(id) },
        onOpenCompass = { shelfCompassOpen = true },
        onCloseCompass = { shelfCompassOpen = false },
        onPlayOnTurntable = playCardOnTurntable,
        onClose = {
            shelfCompassOpen = false
            shelfCard = null
        },
    )

    CollectionCardSheet(
        record = collectionState.openRecord,
        actionError = collectionState.actionError,
        onToggleFavourite = { collectionState.openRecord?.let(collectionViewModel::toggleFavourite) },
        onRemove = { collectionState.openRecord?.let(removeFromCollection) },
        onPlayOnTurntable = playCollectionRecord,
        onClose = collectionViewModel::closeRecord,
    )
}

/** Identifies a card in the session's set of opened cards: its record id, else its delivery's. */
private val ArrivedRecordOption.sessionKey: String get() = submissionId ?: id

/** How long to wait for a remove to reach the server before giving up on offering an undo. */
private const val RemoveSettleTimeoutMs = 10_000L

/** How long "Removed. Undo" stays up. */
private const val UndoSnackbarMs = 8_000L

/**
 * A snackbar in the app's panel style: the dark gradient with its border, cream text and a teal
 * action, in Poppins with 20dp corners.
 */
@Composable
private fun VinylSnackbar(message: String, actionLabel: String?, onAction: () -> Unit) {
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

/**
 * A card from the Home shelf in receive mode, in a bottom sheet: the same music card page 4
 * shows, without the envelope. The compass opens over it and its back arrow returns here.
 *
 * [option] is null when nothing is open. It's still drawn then, so the sheet can slide out
 * showing the card it's closing.
 */
@Composable
private fun ShelfCardSheet(
    option: ArrivedRecordOption?,
    compassOpen: Boolean,
    keptIds: Set<String>,
    keepError: String?,
    readerLat: Double?,
    readerLng: Double?,
    onToggleKeep: (String) -> Unit,
    onOpenCompass: () -> Unit,
    onCloseCompass: () -> Unit,
    onPlayOnTurntable: (ArrivedRecordOption) -> Unit,
    onClose: () -> Unit,
) {
    val shown = rememberLastNonNull(option)
    val showCompass = option != null && compassOpen && option.distanceLabel != null

    AnimatedBottomSheet(visible = option != null && !showCompass, onDismiss = onClose, dismissOnSwipe = true) {
        if (shown == null) return@AnimatedBottomSheet
        val submissionId = shown.submissionId
        // A fresh player for each card, so one card's progress never carries over to the next.
        key(shown.sessionKey) {
            MusicCardScreen(
                state = MusicCardUiState(
                    trackName = shown.trackName,
                    artistName = shown.artistName,
                    artworkUrl = shown.artworkUrl,
                    previewUrl = shown.previewUrl,
                    mood = shown.mood,
                    message = shown.messagePreview,
                    sentDateLabel = shown.sentDateLabel,
                    hasDirection = shown.distanceLabel != null,
                    isKept = submissionId != null && submissionId in keptIds,
                    keepError = keepError,
                ),
                onToggleKeep = { submissionId?.let(onToggleKeep) },
                onOpenCompass = onOpenCompass,
                onPlayOnTurntable = { onPlayOnTurntable(shown) },
                inSheet = true,
                playerActive = option != null && !showCompass,
            )
        }
    }

    if (showCompass && shown != null) {
        val compassState by rememberCompassHeading(
            enabled = true,
            readerLat = readerLat,
            readerLng = readerLng,
            senderLat = shown.senderLat,
            senderLng = shown.senderLng,
        )
        val senderCityLabel by rememberSenderCityLabel(shown.senderLat, shown.senderLng)
        CompassScreen(
            state = CompassUiState(distanceLabel = shown.distanceLabel, cityLabel = senderCityLabel),
            compassState = compassState,
            onBack = onCloseCompass,
        )
    }
}

/**
 * A record opened from the Collection, in history mode: the music card with favourite and remove
 * actions, plus Play on turntable. The compass and distance belong to the day a card arrives, so
 * this sheet reads no location and starts no sensor. A card the reader sent is signed by them.
 *
 * [record] is null when nothing is open. It's still drawn then, so the sheet can slide out
 * showing the card it's closing.
 */
@Composable
private fun CollectionCardSheet(
    record: VinylRecord?,
    actionError: String?,
    onToggleFavourite: () -> Unit,
    onRemove: () -> Unit,
    onPlayOnTurntable: (VinylRecord) -> Unit,
    onClose: () -> Unit,
) {
    val shown = rememberLastNonNull(record)

    AnimatedBottomSheet(visible = record != null, onDismiss = onClose, dismissOnSwipe = true) {
        if (shown == null) return@AnimatedBottomSheet
        val received = shown.source == RecordSource.RECEIVED
        key(shown.id) {
            MusicCardScreen(
                state = MusicCardUiState(
                    trackName = shown.songName,
                    artistName = shown.artist,
                    artworkUrl = shown.coverUrl,
                    previewUrl = shown.previewUrl,
                    mood = shown.moodTag,
                    message = shown.message.orEmpty(),
                    sentDateLabel = sentDateLabel(shown.sentAt),
                    hasDirection = false,
                    keepError = actionError,
                    isFavourite = if (received) shown.isFavourite else null,
                    isOwn = !received,
                ),
                onToggleKeep = null,
                onOpenCompass = {},
                onPlayOnTurntable = { onPlayOnTurntable(shown) },
                onToggleFavourite = onToggleFavourite,
                onRemove = if (received) onRemove else null,
                inSheet = true,
                playerActive = record != null,
            )
        }
    }
}

/**
 * A manually-built bottom sheet — not Material3's ModalBottomSheet, to avoid depending on an
 * experimental API whose surface has shifted across Compose versions. Just a dimmed scrim behind
 * a rounded-top panel pinned to the bottom, sized to a fraction of the screen. Tapping the scrim
 * dismisses; tapping the panel itself does not. With [dismissOnSwipe], dragging the top strip
 * (where the content draws its handle) down past a quarter of the sheet, or flinging it, dismisses.
 *
 * Showing it fades the scrim in and slides the panel up from the bottom edge; hiding it plays
 * the reverse. [content] keeps being drawn while it slides out, so callers give it the last
 * thing it showed (see [rememberLastNonNull]) rather than nothing.
 */
@Composable
private fun AnimatedBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    heightFraction: Float = 0.88f,
    dismissOnSwipe: Boolean = false,
    content: @Composable () -> Unit,
) {
    // The parent itself doesn't animate; the scrim and the panel each bring their own motion.
    AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None) {
        // Inside the visibility scope so a drag offset never survives into the next opening.
        var dragOffset by remember { mutableFloatStateOf(0f) }
        var sheetHeight by remember { mutableIntStateOf(0) }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .animateEnterExit(
                        enter = fadeIn(tween(SheetEnterMs)),
                        exit = fadeOut(tween(SheetExitMs)),
                    )
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(heightFraction)
                    .onSizeChanged { sheetHeight = it.height }
                    .offset { IntOffset(0, dragOffset.roundToInt()) }
                    .animateEnterExit(
                        enter = slideInVertically(tween(SheetEnterMs, easing = LinearOutSlowInEasing)) { it },
                        exit = slideOutVertically(tween(SheetExitMs, easing = FastOutLinearInEasing)) { it },
                    )
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {}, // absorbs taps so they don't fall through to the scrim behind
                    ),
            ) {
                content()
                if (dismissOnSwipe) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(32.dp)
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                                },
                                onDragStopped = { velocity ->
                                    if (dragOffset > sheetHeight * 0.25f || velocity > 1500f) {
                                        onDismiss()
                                    } else {
                                        animate(dragOffset, 0f) { value, _ -> dragOffset = value }
                                    }
                                },
                            ),
                    )
                }
            }
        }
    }
}

/** Material's standard durations: entering a little slower than leaving. */
private const val SheetEnterMs = 300
private const val SheetExitMs = 250

/**
 * [value], or the last non-null value it had. Lets a closing sheet keep showing what it held
 * while it slides out, after its state has already gone back to null.
 */
@Composable
private fun <T : Any> rememberLastNonNull(value: T?): T? {
    val last = remember { object { var value: T? = null } }
    if (value != null) last.value = value
    return last.value
}

/** Secondary line under "Location" on the settings list. */
private fun settingsLocationValue(state: LocationUiState): String? = when {
    !state.hasLocation -> null
    // Brief: the name resolves from the bundled city list just after the profile loads.
    state.city == null -> "Saved"
    else -> state.city
}


@Composable
private fun PlaceholderTab(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylPalette.Background),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = VinylPalette.TextMuted, fontSize = 16.sp)
    }
}

@Composable
private fun AuthScreen(
    modifier: Modifier = Modifier,
    callbackError: String? = null,
    onClearCallbackError: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleAuthRepository = remember { GoogleAuthRepository(context) }
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Offered after a Credential Manager failure the user might still get past in a browser,
    // e.g. a misconfigured client ID or a flaky Play Services.
    var showBrowserFallback by remember { mutableStateOf(false) }
    // Returns as soon as the Custom Tab opens; the session arrives later through the
    // auth-callback deeplink, which flips sessionStatus and dismisses this screen.
    val startBrowserSignIn: suspend () -> Unit = {
        googleAuthRepository.signInWithBrowser()
            .onFailure {
                Log.e("MainActivity", "Google browser sign-in could not be started", it)
                errorMessage = "Google sign-in is unavailable right now. Please try again later."
            }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(VinylPalette.Background)) {
        val viewportHeight = maxHeight
        val artworkHeight = maxOf(maxWidth * 0.80f, viewportHeight * 0.43f)
        val artworkSize = maxWidth * 1.10f
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(viewportHeight * 0.075f))
            Image(
                painter = painterResource(R.drawable.vinyl_logo_white),
                contentDescription = "Vinyl",
                modifier = Modifier.width(200.dp).height(88.dp),
            )
            Text(
                "MUSIC TRAVELS FURTHER",
                color = VinylPalette.TextPrimary,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp, lineHeight = 16.sp, letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 28.dp, end = 28.dp),
            )
            Spacer(Modifier.height(viewportHeight * 0.12f))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = OnboardingHorizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    enabled = !isSigningIn,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF4F0EA),
                        contentColor = Color(0xFF1F1F1F),
                    ),
                    onClick = {
                        scope.launch {
                            isSigningIn = true
                            errorMessage = null
                            showBrowserFallback = false
                            onClearCallbackError()
                            try {
                                when (val outcome = googleAuthRepository.signIn()) {
                                    GoogleSignInOutcome.Success, GoogleSignInOutcome.Cancelled -> Unit
                                    // Devices without a Google account use the existing browser fallback.
                                    GoogleSignInOutcome.NoDeviceAccount -> startBrowserSignIn()
                                    is GoogleSignInOutcome.Failed -> {
                                        errorMessage = outcome.message
                                        showBrowserFallback = outcome.offerBrowserFallback
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Google sign-in could not be started", e)
                                errorMessage = "Google sign-in is unavailable right now. Please try again later."
                                showBrowserFallback = false
                            } finally {
                                isSigningIn = false
                            }
                        }
                    }
                ) {
                    Image(painterResource(R.drawable.google_g), contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(if (isSigningIn) "Signing in…" else "Continue with Google", fontSize = 18.sp, lineHeight = 24.sp, fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(12.dp))
                if (!isSigningIn && (showBrowserFallback || callbackError != null)) {
                    TextButton(onClick = {
                        onClearCallbackError()
                        scope.launch { startBrowserSignIn() }
                    }) { Text("Sign in with a browser instead") }
                }
                (errorMessage ?: callbackError)?.let {
                    Text(it, color = VinylPalette.TealAccent, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp), fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(viewportHeight * 0.045f))
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.fillMaxWidth().height(artworkHeight).clip(RoundedCornerShape(0.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // The artwork's transparent margins are cropped; the illustration remains intact.
                Image(
                    painterResource(R.drawable.opening_visual), contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.requiredSize(artworkSize),
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
