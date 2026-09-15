package com.example.vinyl

import android.os.Bundle
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
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
import com.example.vinyl.data.MoodOptions
import com.example.vinyl.data.MoodTag
import com.example.vinyl.data.Supabase
import com.example.vinyl.ui.daily.ArrivedRecordOption
import com.example.vinyl.ui.daily.ArrivedTodayScreen
import com.example.vinyl.ui.daily.ArrivedTodayUiState
import com.example.vinyl.ui.daily.MoodQuestionnaireScreen
import com.example.vinyl.ui.daily.UnopenedRecordScreen
import com.example.vinyl.ui.daily.UnopenedRecordUiState
import com.example.vinyl.ui.received.ReceivedCardScreen
import com.example.vinyl.ui.received.ReceivedCardUiState
import com.example.vinyl.ui.theme.VinylPalette
import com.example.vinyl.ui.theme.VinylTheme
import com.example.vinyl.ui.write.WriteCardScreen
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VinylTheme {
                // TESTING ONLY: lets you skip the Google sign-in gate and go straight to
                // VinylApp() without a real session. Remove before submitting/shipping.
                var bypassAuthForTesting by remember { mutableStateOf(false) }
                val sessionStatus by Supabase.client.auth.sessionStatus.collectAsState()

                if (sessionStatus is SessionStatus.Authenticated || bypassAuthForTesting) {
                    VinylApp()
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        AuthScreen(
                            modifier = Modifier.padding(innerPadding),
                            onSkipForTesting = { bypassAuthForTesting = true },
                        )
                    }
                }
            }
        }
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

@Composable
private fun VinylApp() {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Home) }

    var receiveFlowStep by remember { mutableStateOf<ReceiveFlowStep?>(null) }
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
                // Placeholders — not the actual designs, just enough to prove the tab works
                AppTab.Collection -> PlaceholderTab("Collection")
                AppTab.Home -> HomeTab(
                    onOpenReceive = {
                        dailyMood = null
                        dailyGenres = emptySet()
                        receiveFlowStep = ReceiveFlowStep.Questionnaire
                    },
                )
                AppTab.Create -> WriteCardScreen()
            }
        }
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
                    onSubmit = { receiveFlowStep = ReceiveFlowStep.ArrivedToday },
                    onLetCrateDecide = { receiveFlowStep = ReceiveFlowStep.ArrivedToday },
                    onBack = { receiveFlowStep = null },
                )
            }
        }

        ReceiveFlowStep.ArrivedToday -> {
            val moodLabel = MoodOptions.all.firstOrNull { it.tag == dailyMood }?.title ?: "Surprise"
            BottomSheetContainer(onDismiss = { receiveFlowStep = null }) {
                ArrivedTodayScreen(
                    state = sampleArrivedToday(moodLabel = moodLabel, genreLabel = dailyGenres.firstOrNull()),
                    onSelect = { option -> receiveFlowStep = ReceiveFlowStep.Unopened(option) },
                    onNotNow = { receiveFlowStep = null },
                )
            }
        }

        is ReceiveFlowStep.Unopened -> {
            UnopenedRecordScreen(
                state = UnopenedRecordUiState(
                    distanceLabel = step.option.distanceLabel,
                    moodLabel = step.option.moodLabel,
                    sentTimeLabel = "Just now",
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
                        mood = null, // ArrivedRecordOption's mood is the daily check-in mood, a
                        // different vocabulary from MoodTag — nothing to map it to yet
                        message = step.option.messagePreview,
                        senderDistanceLabel = step.option.distanceLabel,
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
private fun HomeTab(onOpenReceive: () -> Unit) {
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
        }
    }
}

// Temporary sample data — swap for a real fetched ArrivedTodayUiState once there's a query/RPC
// that pulls three matching records from the crate.
private fun sampleArrivedToday(moodLabel: String, genreLabel: String?): ArrivedTodayUiState {
    return ArrivedTodayUiState(
        moodLabel = moodLabel,
        genreLabel = genreLabel,
        fallbackNote = genreLabel?.let { "No $it in today's crate — closest three instead" },
        options = listOf(
            ArrivedRecordOption("1", "Slow Rain, Rooftop", "Marin Ochre", "I played this the night I moved out…", moodLabel, "2.4 km"),
            ArrivedRecordOption("2", "Kitchen Light, 2am", "Sora Lin", "My mother never turned the hall lamp off…", moodLabel, "5.1 km"),
            ArrivedRecordOption("3", "Long Way From Cebu", "Teo Marasigan", "Third winter here and it still surprises me…", moodLabel, "18 km"),
        ),
    )
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
    onSkipForTesting: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleAuthRepository = remember { GoogleAuthRepository(context) }
    val sessionStatus by Supabase.client.auth.sessionStatus.collectAsState()
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                            errorMessage = googleAuthRepository.signIn()
                                .exceptionOrNull()?.message
                            isSigningIn = false
                        }
                    }) {
                        Text("Sign in with Google")
                    }
                }
                errorMessage?.let { Text(it) }

                // TESTING ONLY — bypasses sign-in entirely. Remove before submitting/shipping.
                TextButton(onClick = onSkipForTesting) {
                    Text("Skip sign-in (testing)", color = VinylPalette.Background)
                }
            }
        }
    }
}