package com.example.vinyl

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.data.GoogleAuthRepository
import com.example.vinyl.data.GoogleSignInOutcome
import com.example.vinyl.data.MoodOptions
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.Supabase
import com.example.vinyl.data.onboarding.FakeOnboardingRepository
import com.example.vinyl.data.onboarding.OnboardingRepository
import com.example.vinyl.ui.collection.CollectionScreen
import com.example.vinyl.ui.daily.ArrivedRecordOption
import com.example.vinyl.ui.daily.ArrivedTodayScreen
import com.example.vinyl.ui.daily.ArrivedTodayUiState
import com.example.vinyl.ui.daily.MoodQuestionnaireScreen
import com.example.vinyl.ui.daily.RoomViewModel
import com.example.vinyl.ui.daily.UnopenedRecordScreen
import com.example.vinyl.ui.daily.UnopenedRecordUiState
import com.example.vinyl.ui.onboarding.OnboardingPagerScreen
import com.example.vinyl.ui.onboarding.OnboardingViewModel
import com.example.vinyl.ui.daily.toArrivedOption
import com.example.vinyl.ui.location.LocationGateScreen
import com.example.vinyl.ui.location.LocationSettingsScreen
import com.example.vinyl.ui.location.LocationUiState
import com.example.vinyl.ui.settings.SettingsScreen
import com.example.vinyl.ui.location.LocationViewModel
import com.example.vinyl.ui.received.ReceivedCardScreen
import com.example.vinyl.ui.received.ReceivedCardUiState
import com.example.vinyl.ui.theme.VinylPalette
import com.example.vinyl.ui.theme.VinylTheme
import com.example.vinyl.ui.write.WriteCardScreen
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Set when the auth callback comes back carrying an error instead of a code. Held here rather
    // than in AuthScreen because the callback arrives at the activity, not at the composition.
    private var authCallbackError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cold-start leg of the browser sign-in fallback: the app was killed while the user was in
        // the browser, so the callback arrives as the launch intent. handleDeeplinks() ignores
        // anything that isn't com.example.vinyl://auth-callback, so this is safe on a normal launch.
        handleAuthDeeplink(intent)

        enableEdgeToEdge()
        setContent {
            VinylTheme {
                // TESTING ONLY: lets you skip the Google sign-in gate and go straight to
                // VinylApp() without a real session. Remove before submitting/shipping.
                var bypassAuthForTesting by remember { mutableStateOf(false) }
                val sessionStatus by Supabase.client.auth.sessionStatus.collectAsState()

                if (sessionStatus is SessionStatus.Authenticated || bypassAuthForTesting) {
                    OnboardingGate(useStubData = bypassAuthForTesting) { VinylApp() }
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        AuthScreen(
                            modifier = Modifier.padding(innerPadding),
                            callbackError = authCallbackError,
                            onClearCallbackError = { authCallbackError = null },
                            onSkipForTesting = { bypassAuthForTesting = true },
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
            authCallbackError = error
            return
        }

        authCallbackError = null
        Supabase.client.handleDeeplinks(
            intent = intent,
            onError = {
                Log.e(TAG, "Auth deeplink exchange failed", it)
                authCallbackError = it.message ?: "Couldn't finish signing in."
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
}

/**
 * Runs the four-step onboarding flow once per account (`profiles.onboarding_completed`), then
 * hands over to the app. Replaces the old `LocationGate` wrapper — the location ask is now page 3
 * of [OnboardingPagerScreen] instead of living here on its own, per that screen's original
 * docstring.
 *
 * Reads the flag once on entry rather than through [com.example.vinyl.ui.onboarding.OnboardingViewModel],
 * since that view model's state doesn't carry `onboarding_completed` — it only tracks the fields
 * onboarding itself edits.
 *
 * [useStubData] — TESTING ONLY: when true (wired to the same `bypassAuthForTesting` switch as the
 * sign-in skip), the whole gate runs on [FakeOnboardingRepository] instead of checking Supabase,
 * since there's no real signed-in user to look up in that mode. Remove this parameter, and the
 * branch that uses it, before shipping.
 */
@Composable
private fun OnboardingGate(useStubData: Boolean = false, content: @Composable () -> Unit) {
    if (useStubData) {
        var stubCompleted by remember { mutableStateOf(false) }
        if (stubCompleted) {
            content()
        } else {
            OnboardingPagerScreen(
                onOnboardingComplete = { stubCompleted = true },
                viewModel = remember { OnboardingViewModel(repository = FakeOnboardingRepository()) },
            )
        }
        return
    }

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

private fun LocationGate(content: @Composable () -> Unit) {
    val locationViewModel: LocationViewModel = viewModel()
    val locationState by locationViewModel.uiState.collectAsState()

    // Decided once, from the first completed profile read, and never again this launch. Deciding
    // it live from hasLocation meant that removing a location in Settings threw the user onto
    // this gate mid-session — straight after they'd said they didn't want one. Device testing
    // caught that. Null = the first read hasn't finished yet.
    var showGate by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(locationState.isLoading) {
        if (!locationState.isLoading && showGate == null) showGate = !locationState.hasLocation
    }

    when (showGate) {
        // Blank rather than a spinner: the read is usually a few hundred ms, and a spinner that
        // fast reads as a flicker.
        null -> Box(Modifier.fillMaxSize().background(VinylPalette.Background))

        true -> LocationGateScreen(
            onDone = { showGate = false },
            viewModel = locationViewModel,
        )

        false -> content()
    }
}

@Composable
private fun VinylApp() {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Home) }

    var receiveFlowStep by remember { mutableStateOf<ReceiveFlowStep?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showLocationSettings by rememberSaveable { mutableStateOf(false) }

    val roomViewModel: RoomViewModel = viewModel()

    // Same activity-scoped instance the gate and the settings screen use.
    val locationViewModel: LocationViewModel = viewModel()
    val locationState by locationViewModel.uiState.collectAsState()

    // Resolves the city name for the stored centroid once it's loaded. No-op when it's already
    // known or nothing is stored, so the city list is searched at most once per launch.
    LaunchedEffect(locationState.hasLocation) { locationViewModel.loadCityLabel() }
    var dailyMood by remember { mutableStateOf<MoodTag?>(null) }
    var dailyGenres by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        containerColor = VinylPalette.Background,
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
                AppTab.Collection -> CollectionScreen()
                // Placeholder — not the actual design, just enough to prove the tab works
                AppTab.Home -> HomeTab(
                    onOpenReceive = {
                        dailyMood = null
                        dailyGenres = emptySet()
                        receiveFlowStep = ReceiveFlowStep.Questionnaire
                    },
                    onOpenSettings = { showSettings = true },
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

    if (showSettings) {
        SettingsScreen(
            locationValue = settingsLocationValue(locationState),
            onOpenLocation = { showLocationSettings = true },
            onBack = { showSettings = false },
        )
    }

    if (showLocationSettings) {
        LocationSettingsScreen(onBack = { showLocationSettings = false })
    }

    // The whole receive flow sits on top of everything (including the bottom bar) while active,
    // like a stack of pushed screens. Only one step is ever shown at a time.
    when (val step = receiveFlowStep) {
        null -> Unit

        ReceiveFlowStep.Questionnaire -> {
            BottomSheetContainer(onDismiss = { receiveFlowStep = null }) {
                MoodQuestionnaireScreen(
                    selectedMood = dailyMood,
                    selectedGenres = dailyGenres,
                    onMoodSelected = { dailyMood = it },
                    onGenreToggled = { genre ->
                        dailyGenres = if (genre in dailyGenres) dailyGenres - genre else dailyGenres + genre
                    },
                    // Load here rather than on entering Arrived Today: Unopened's back button
                    // returns there, and request_recommendations records new matches on every
                    // call — loading on entry would deal a fresh hand each time.
                    onSubmit = {
                        roomViewModel.load(dailyMood)
                        receiveFlowStep = ReceiveFlowStep.ArrivedToday
                    },
                    onLetCrateDecide = {
                        roomViewModel.load(mood = null)
                        receiveFlowStep = ReceiveFlowStep.ArrivedToday
                    },
                    onBack = { receiveFlowStep = null },
                )
            }
        }

        ReceiveFlowStep.ArrivedToday -> {
            val moodLabel = MoodOptions.all.firstOrNull { it.tag == dailyMood }?.title ?: "Surprise"
            val roomState by roomViewModel.uiState.collectAsState()

            BottomSheetContainer(onDismiss = { receiveFlowStep = null }) {
                if (roomState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(VinylPalette.SheetSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = VinylPalette.TealAccent)
                    }
                } else {
                    ArrivedTodayScreen(
                        state = ArrivedTodayUiState(
                            moodLabel = moodLabel,
                            // No genre label: the RPCs match on mood only, so showing the chosen
                            // genre would imply a filter that never ran.
                            fallbackNote = when {
                                roomState.error != null ->
                                    "Couldn't reach the crate. Check your connection and try again."
                                roomState.cards.isEmpty() -> "Nothing in the crate yet. Check back later."
                                else -> null
                            },
                            options = roomState.cards.map {
                                it.toArrivedOption(readerLat = locationState.lat, readerLng = locationState.lng)
                            },
                        ),
                        onSelect = { option -> receiveFlowStep = ReceiveFlowStep.Unopened(option) },
                        onNotNow = { receiveFlowStep = null },
                    )
                }
            }
        }

        is ReceiveFlowStep.Unopened -> {
            UnopenedRecordScreen(
                state = UnopenedRecordUiState(
                    distanceLabel = step.option.distanceLabel,
                    moodLabel = step.option.moodLabel,
                    sentTimeLabel = "Just now",
                    distanceNote = step.option.distanceNote,
                ),
                onOpen = { receiveFlowStep = ReceiveFlowStep.Opened(step.option) },
                onBack = { receiveFlowStep = ReceiveFlowStep.ArrivedToday },
            )
        }

        is ReceiveFlowStep.Opened -> {
            BottomSheetContainer(onDismiss = { receiveFlowStep = null }) {
                ReceivedCardScreen(
                    state = ReceivedCardUiState(
                        trackName = step.option.trackName,
                        artistName = step.option.artistName,
                        artworkUrl = step.option.artworkUrl,
                        mood = step.option.mood,
                        message = step.option.messagePreview,
                        senderDistanceLabel = step.option.distanceLabel,
                        senderDistanceNote = step.option.distanceNote,
                        sentTimeLabel = "Just now",
                    ),
                    onClose = { receiveFlowStep = null },
                )
            }
        }
    }
}

/**
 * A manually-built bottom sheet — not Material3's ModalBottomSheet, to avoid depending on an
 * experimental API whose surface has shifted across Compose versions. Just a dimmed scrim behind
 * a rounded-top panel pinned to the bottom, sized to a fraction of the screen. Tapping the scrim
 * dismisses; tapping the panel itself does not.
 */
@Composable
private fun BottomSheetContainer(
    onDismiss: () -> Unit,
    heightFraction: Float = 0.88f,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(heightFraction)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {}, // absorbs taps so they don't fall through to the scrim behind
                ),
        ) {
            content()
        }
    }
}

@Composable
private fun HomeTab(onOpenReceive: () -> Unit, onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylPalette.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Home", color = VinylPalette.TextMuted, fontSize = 16.sp)

            Button(
                onClick = onOpenReceive,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VinylPalette.TealAccent,
                    contentColor = VinylPalette.Background,
                ),
            ) {
                Text("Open receive", fontWeight = FontWeight.Medium)
            }

            TextButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = VinylPalette.TextMuted,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Settings",
                    color = VinylPalette.TextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
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
    onSkipForTesting: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleAuthRepository = remember { GoogleAuthRepository(context) }
    val sessionStatus by Supabase.client.auth.sessionStatus.collectAsState()
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Offered after a Credential Manager failure the user might still get past in a browser,
    // e.g. a misconfigured client ID or a flaky Play Services.
    var showBrowserFallback by remember { mutableStateOf(false) }

    // Returns as soon as the Custom Tab opens; the session arrives later through the
    // auth-callback deeplink, which flips sessionStatus and dismisses this screen.
    val startBrowserSignIn: suspend () -> Unit = {
        googleAuthRepository.signInWithBrowser()
            .onFailure { errorMessage = it.message ?: "Couldn't open the browser." }
        Unit
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (val status = sessionStatus) {
            is SessionStatus.Authenticated -> {
                Text("Signed in as ${status.session.user?.email}")
                Button(onClick = { scope.launch { googleAuthRepository.signOut() } }) {
                    Text("Sign out")
                }
            }
            else -> {
                if (isSigningIn) {
                    CircularProgressIndicator()
                } else {
                    Button(onClick = {
                        scope.launch {
                            isSigningIn = true
                            errorMessage = null
                            showBrowserFallback = false
                            onClearCallbackError()
                            when (val outcome = googleAuthRepository.signIn()) {
                                GoogleSignInOutcome.Success,
                                GoogleSignInOutcome.Cancelled -> Unit
                                // The device has no Google account for the bottom sheet to offer,
                                // and the user can't fix that from in here, so don't make them tap
                                // a second button: go straight to the browser.
                                GoogleSignInOutcome.NoDeviceAccount -> startBrowserSignIn()
                                is GoogleSignInOutcome.Failed -> {
                                    errorMessage = outcome.message
                                    showBrowserFallback = true
                                }
                            }
                            isSigningIn = false
                        }
                    }) {
                        Text("Sign in with Google")
                    }

                    // Also offered after a failed callback: the browser round-trip is the only
                    // thing the user can retry from here.
                    if (showBrowserFallback || callbackError != null) {
                        TextButton(onClick = {
                            onClearCallbackError()
                            scope.launch { startBrowserSignIn() }
                        }) {
                            Text("Sign in with a browser instead")
                        }
                    }
                }
                (errorMessage ?: callbackError)?.let { Text(it) }

                // TESTING ONLY — bypasses sign-in entirely. Remove before submitting/shipping.
                TextButton(onClick = onSkipForTesting) {
                    Text("Skip sign-in (testing)", color = VinylPalette.TealAccent)
                }
            }
        }
    }
}