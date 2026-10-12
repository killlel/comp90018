package com.example.vinyl

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vinyl.data.GoogleAuthRepository
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.Supabase
import com.example.vinyl.data.model.VinylRecord
import com.example.vinyl.data.onboarding.OnboardingRepository
import com.example.vinyl.notification.DailyReminder
import com.example.vinyl.repository.RoomRepository
import com.example.vinyl.ui.auth.AuthScreen
import com.example.vinyl.ui.collection.CollectionScreen
import com.example.vinyl.ui.collection.CollectionViewModel
import com.example.vinyl.ui.components.VinylSnackbar
import com.example.vinyl.ui.daily.ArrivedRecordOption
import com.example.vinyl.ui.daily.DailyGenresViewModel
import com.example.vinyl.ui.daily.RoomViewModel
import com.example.vinyl.ui.daily.toArrivedOption
import com.example.vinyl.ui.home.HomeAvatar
import com.example.vinyl.ui.home.HomeScreen
import com.example.vinyl.ui.home.HomeViewModel
import com.example.vinyl.ui.home.NowPlaying
import com.example.vinyl.ui.home.PlaybackViewModel
import com.example.vinyl.ui.location.LocationSettingsScreen
import com.example.vinyl.ui.location.LocationUiState
import com.example.vinyl.ui.location.LocationViewModel
import com.example.vinyl.ui.onboarding.OnboardingPagerScreen
import com.example.vinyl.ui.receive.CollectionCardSheet
import com.example.vinyl.ui.receive.ReceiveFlow
import com.example.vinyl.ui.receive.ReceiveFlowStep
import com.example.vinyl.ui.receive.ShelfCardSheet
import com.example.vinyl.ui.settings.SettingsScreen
import com.example.vinyl.ui.settings.rememberAvatarAppearance
import com.example.vinyl.ui.theme.VinylPalette
import com.example.vinyl.ui.theme.VinylTheme
import com.example.vinyl.ui.write.WriteCardScreen
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * The app's only activity. It shows the sign-in screen until a session exists, then the onboarding
 * gate and [VinylApp]. It also receives the two things that arrive as intents rather than through
 * the UI: the sign-in callback from the browser, and a tap on the daily reminder.
 */
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

private enum class AppTab(val label: String, val icon: ImageVector) {
    Collection("Collection", Icons.Filled.List),
    Home("Home", Icons.Filled.Home),
    Create("Create", Icons.Filled.Add),
}

/**
 * Runs the onboarding flow once per account (`profiles.onboarding_completed`), then hands over to
 * the app. The location ask is page 3 of [OnboardingPagerScreen].
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

/**
 * The signed-in app: the Collection, Home and Create tabs under one bottom bar, with Settings, the
 * receive flow and the card sheets layered on top. It owns the state that links them, such as the
 * selected tab, the current receive-flow step and the card open in a sheet; the pieces it hands
 * off ([VinylBottomBar], [ReceiveFlow], [offerUndoAfterRemove]) each do one job.
 *
 * @param openReceiveRequested true when the daily reminder was tapped; the app opens the receive
 *   flow once Home has loaded, then calls [onReceiveOpened].
 */
@Composable
private fun VinylApp(
    onSignOut: () -> Unit,
    openReceiveRequested: Boolean = false,
    onReceiveOpened: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Home) }
    var receiveFlowStep by remember { mutableStateOf<ReceiveFlowStep?>(null) }
    var dailyMood by remember { mutableStateOf<MoodTag?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showLocationSettings by rememberSaveable { mutableStateOf(false) }

    // The card open in the receive-mode sheet (an already opened letter), and whether that sheet
    // is showing its compass.
    var shelfCard by remember { mutableStateOf<ArrivedRecordOption?>(null) }
    var shelfCompassOpen by remember { mutableStateOf(false) }

    // All activity-scoped, so every screen below reads the same instance as the screen that
    // writes to it.
    val roomViewModel: RoomViewModel = viewModel()
    val homeViewModel: HomeViewModel = viewModel()
    val collectionViewModel: CollectionViewModel = viewModel()
    val playbackViewModel: PlaybackViewModel = viewModel()
    val locationViewModel: LocationViewModel = viewModel()
    val dailyGenresViewModel: DailyGenresViewModel = viewModel()
    val roomState by roomViewModel.uiState.collectAsState()
    val homeState by homeViewModel.uiState.collectAsState()
    val collectionState by collectionViewModel.uiState.collectAsState()
    val locationState by locationViewModel.uiState.collectAsState()

    // Home's "Arrived today" shelf shows the latest hand, so it follows the receive flow's.
    LaunchedEffect(roomState.cards) {
        if (roomState.cards.isNotEmpty()) homeViewModel.showToday(roomState.cards)
    }

    // Resolves the city name for the stored city centre once it's loaded. No-op when it's already
    // known or nothing is stored, so the city list is searched at most once per launch.
    LaunchedEffect(locationState.hasLocation) { locationViewModel.loadCityLabel() }

    // The two ways into the receive flow: the mood question for a fresh pull, or today's hand
    // again when it has already been dealt.
    val openQuestionnaire: () -> Unit = {
        dailyMood = null
        dailyGenresViewModel.reset()
        receiveFlowStep = ReceiveFlowStep.Questionnaire
    }
    val openTodaysHand: () -> Unit = {
        roomViewModel.showExisting(homeState.todayHand)
        receiveFlowStep = ReceiveFlowStep.ArrivedToday
    }

    // Tapping the daily reminder lands on the mood question, same as Home's "Open today's music cards".
    LaunchedEffect(openReceiveRequested, homeState.isLoading) {
        if (!openReceiveRequested || homeState.isLoading) return@LaunchedEffect
        selectedTab = AppTab.Home
        if (homeState.error == null && !homeState.doneForToday) {
            if (homeState.todayHand.isNotEmpty()) openTodaysHand() else openQuestionnaire()
        }
        onReceiveOpened()
    }

    // "Play this song" on any music card starts it on the Home turntable and goes to Home, so the
    // needle drop is seen. HomeScreen reads the same PlaybackViewModel.
    val playOnHome: (NowPlaying) -> Unit = { track ->
        receiveFlowStep = null
        shelfCard = null
        shelfCompassOpen = false
        collectionViewModel.closeRecord()
        selectedTab = AppTab.Home
        playbackViewModel.playOrResume(track)
    }
    val playCardOnTurntable: (ArrivedRecordOption) -> Unit = { playOnHome(it.toNowPlaying()) }

    // Collection records use the same player without publishing a pending daily hand. A song in
    // the library can be unrelated to today's three cards.
    val playCollectionRecord: (VinylRecord) -> Unit = { playOnHome(it.toNowPlaying()) }

    // "Removed. Undo" after taking a card off the Collection shelf.
    val snackbarHostState = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    val roomRepository = remember { RoomRepository() }
    val removeFromCollection: (VinylRecord) -> Unit = { record ->
        val countBefore = collectionState.totalCount
        collectionViewModel.closeRecord()
        collectionViewModel.remove(record)
        appScope.launch {
            offerUndoAfterRemove(record, countBefore, collectionViewModel, roomRepository, snackbarHostState)
        }
    }

    // The avatar is changed in Settings, so it's read again each time Settings opens or closes.
    val avatarAppearance = rememberAvatarAppearance()
    val homeAvatar = remember(showSettings) {
        HomeAvatar(avatarAppearance.iconIndex, avatarAppearance.gradientIndex, avatarAppearance.imageUrl)
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
        bottomBar = { VinylBottomBar(selectedTab = selectedTab, onSelect = { selectedTab = it }) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                AppTab.Collection -> CollectionScreen(viewModel = collectionViewModel)
                AppTab.Home -> HomeScreen(
                    // Today's pull is made once: the first tap asks the mood question and deals the
                    // hand; after that the button reopens the same hand's sealed envelopes until
                    // every one is open.
                    onOpenReceive = {
                        if (!homeState.isLoading && homeState.error == null && !homeState.doneForToday) {
                            if (homeState.todayHand.isNotEmpty()) {
                                openTodaysHand()
                            } else if (homeState.canPull) {
                                openQuestionnaire()
                            }
                        }
                    },
                    onOpenSettings = { showSettings = true },
                    avatar = homeAvatar,
                    // The picker uses exactly the hand on Home, with no load or draw on a card tap.
                    onOpenCard = { card ->
                        roomViewModel.showExisting(homeState.todayHand)
                        shelfCard = card.toArrivedOption(readerLat = locationState.lat, readerLng = locationState.lng)
                    },
                )
                AppTab.Create -> WriteCardScreen()
            }
        }
    }

    OverlayBackHandlers(
        locationSettingsOpen = showLocationSettings,
        onCloseLocationSettings = { showLocationSettings = false },
        settingsOpen = showSettings,
        onCloseSettings = { showSettings = false },
        collectionRecordOpen = collectionState.openRecord != null,
        onCloseCollectionRecord = collectionViewModel::closeRecord,
        shelfCardOpen = shelfCard != null,
        onBackFromShelfCard = { if (shelfCompassOpen) shelfCompassOpen = false else shelfCard = null },
        receiveFlowStep = receiveFlowStep,
        onReceiveFlowStepChange = { receiveFlowStep = it },
        onBackToHome = { selectedTab = AppTab.Home },
    )

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

    ReceiveFlow(
        step = receiveFlowStep,
        onStepChange = { receiveFlowStep = it },
        dailyMood = dailyMood,
        onDailyMoodChange = { dailyMood = it },
        roomViewModel = roomViewModel,
        homeViewModel = homeViewModel,
        genresViewModel = dailyGenresViewModel,
        locationViewModel = locationViewModel,
        onBackToHome = { selectedTab = AppTab.Home },
        onShowOpenedCard = { shelfCard = it },
        onPlayOnTurntable = playCardOnTurntable,
    )

    // An already opened card: receive mode in a sheet,
    // with the bookmark, the compass and "Play on turntable".
    ShelfCardSheet(
        option = shelfCard,
        compassOpen = shelfCompassOpen,
        keptIds = roomState.keptIds,
        keepError = roomState.keepError,
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

/** The Collection / Home / Create bar under the tabs. */
@Composable
private fun VinylBottomBar(selectedTab: AppTab, onSelect: (AppTab) -> Unit) {
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
        AppTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = itemColors,
            )
        }
    }
}

/**
 * System back for the overlays. Settings, the receive flow and the card sheets are overlays, not
 * navigation destinations, so the system back gesture doesn't know about them and would close the
 * whole app. Each overlay gets a handler that is enabled only while it is open. Handlers declared
 * later win, so the order here is the order the overlays stack: location detail over Settings, and
 * the receive flow over everything.
 */
@Composable
private fun OverlayBackHandlers(
    locationSettingsOpen: Boolean,
    onCloseLocationSettings: () -> Unit,
    settingsOpen: Boolean,
    onCloseSettings: () -> Unit,
    collectionRecordOpen: Boolean,
    onCloseCollectionRecord: () -> Unit,
    shelfCardOpen: Boolean,
    onBackFromShelfCard: () -> Unit,
    receiveFlowStep: ReceiveFlowStep?,
    onReceiveFlowStepChange: (ReceiveFlowStep?) -> Unit,
    onBackToHome: () -> Unit,
) {
    BackHandler(enabled = locationSettingsOpen, onBack = onCloseLocationSettings)
    BackHandler(enabled = settingsOpen && !locationSettingsOpen, onBack = onCloseSettings)
    BackHandler(enabled = collectionRecordOpen, onBack = onCloseCollectionRecord)
    BackHandler(enabled = shelfCardOpen, onBack = onBackFromShelfCard)
    // Each receive-flow step goes where its own on-screen back or close button goes: an unopened
    // record steps back to the picker, the compass back to its card, and every other step closes
    // the flow.
    BackHandler(enabled = receiveFlowStep != null) {
        // Arrived Today and the music card have no back arrow: back from them goes to Home.
        if (receiveFlowStep is ReceiveFlowStep.ArrivedToday || receiveFlowStep is ReceiveFlowStep.Opened) {
            onBackToHome()
        }
        onReceiveFlowStepChange(
            when (receiveFlowStep) {
                is ReceiveFlowStep.Unopened -> ReceiveFlowStep.ArrivedToday
                // The compass's own back arrow returns to the card, so the system back does too.
                is ReceiveFlowStep.Direction -> ReceiveFlowStep.Opened(receiveFlowStep.option)
                else -> null
            },
        )
    }
}

/**
 * Waits for a remove to reach the server, then offers "Removed. Undo". Undo keeps the card again,
 * restores its star and reloads the Collection.
 *
 * @param countBefore the Collection's size before the remove, so a smaller count means it landed.
 */
private suspend fun offerUndoAfterRemove(
    record: VinylRecord,
    countBefore: Int,
    collectionViewModel: CollectionViewModel,
    roomRepository: RoomRepository,
    snackbarHostState: SnackbarHostState,
) {
    // Waits for the remove to land (or fail) before offering to undo it.
    val settled = withTimeoutOrNull(RemoveSettleTimeoutMs.milliseconds) {
        collectionViewModel.uiState.first { it.actionError != null || it.totalCount < countBefore }
    } ?: return
    if (settled.actionError != null) {
        snackbarHostState.showSnackbar(settled.actionError)
        return
    }
    // Held for UndoSnackbarMs rather than the short default, long enough to reach Undo.
    // Timing out cancels the call, which takes the snackbar down; null means no undo.
    val result = withTimeoutOrNull(UndoSnackbarMs.milliseconds) {
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

/** Starts [track] on the turntable, or un-pauses it if it is already the song loaded. */
private fun PlaybackViewModel.playOrResume(track: NowPlaying) {
    val current = nowPlaying.value
    val alreadyPlaying = current != null && current.submissionId == track.submissionId &&
            current.title == track.title && current.artist == track.artist
    if (alreadyPlaying) {
        if (clock.value?.isPaused == true) togglePause()
    } else {
        play(track)
    }
}

private fun ArrivedRecordOption.toNowPlaying() = NowPlaying(
    submissionId = submissionId,
    title = trackName,
    artist = artistName,
    artworkUrl = artworkUrl,
    previewUrl = previewUrl,
)

private fun VinylRecord.toNowPlaying() = NowPlaying(
    submissionId = id,
    title = songName,
    artist = artist,
    artworkUrl = coverUrl,
    previewUrl = previewUrl,
)

/** How long to wait for a remove to reach the server before giving up on offering an undo. */
private const val RemoveSettleTimeoutMs = 10_000L

/** How long "Removed. Undo" stays up. */
private const val UndoSnackbarMs = 8_000L

/** Secondary line under "Location" on the settings list. */
private fun settingsLocationValue(state: LocationUiState): String? = when {
    !state.hasLocation -> null
    // Brief: the name resolves from the bundled city list just after the profile loads.
    state.city == null -> "Saved"
    else -> state.city
}