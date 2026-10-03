package com.example.vinyl.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vinyl.data.onboarding.FakeOnboardingRepository
import com.example.vinyl.ui.location.LocationGateScreen
import com.example.vinyl.ui.theme.VinylPalette
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 7

/**
 * Sign-in stays outside this pager. Order matches the onboarding board:
 * welcome, display name, profile icon, music vibes, city, notifications, ready.
 * Ready writes `onboarding_completed` successfully before entering the app.
 */
@Composable
fun OnboardingPagerScreen(
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    fun advance(expectedPage: Int) {
        if (pagerState.isScrollInProgress || pagerState.currentPage != expectedPage) return
        if (pagerState.currentPage < PAGE_COUNT - 1) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
    }

    fun retreat() {
        if (pagerState.isScrollInProgress || uiState.isSaving || uiState.isRerollingName) return
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
    }

    BackHandler(enabled = pagerState.currentPage > 0) { retreat() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VinylPalette.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (pagerState.currentPage > 0) {
                IconButton(onClick = ::retreat, enabled = !uiState.isSaving && !uiState.isRerollingName, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = VinylPalette.TealAccent,
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            StepIndicator(
                currentPage = pagerState.currentPage,
                pageCount = PAGE_COUNT,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(48.dp))
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            // Adjacent pages are precomposed, but must not expose duplicate actions to TalkBack.
            Box(Modifier.fillMaxSize().then(
                if (page != pagerState.currentPage) Modifier.clearAndSetSemantics {} else Modifier,
            )) {
                when (page) {
                    0 -> WelcomePage(onNext = { advance(page) })
                    1 -> NamePage(viewModel = viewModel, onNext = { advance(page) })
                    2 -> IconPage(viewModel = viewModel, onNext = { advance(page) })
                    3 -> GenrePage(viewModel = viewModel, onNext = { advance(page) })
                    4 -> LocationGateScreen(onDone = { advance(page) })
                    5 -> NotificationPage(viewModel = viewModel, onFinish = { advance(page) })
                    else -> ReadyPage(viewModel = viewModel, onEnter = onOnboardingComplete)
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(currentPage: Int, pageCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (index == currentPage) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) {
                            VinylPalette.TealAccent
                        } else {
                            VinylPalette.TextMuted.copy(alpha = 0.3f)
                        },
                    ),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun OnboardingPagerScreenPreview() {
    OnboardingPagerScreen(
        onOnboardingComplete = {},
        viewModel = viewModel { OnboardingViewModel(repository = FakeOnboardingRepository()) },
    )
}
