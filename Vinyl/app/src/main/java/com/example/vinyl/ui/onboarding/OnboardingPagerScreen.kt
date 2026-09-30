package com.example.vinyl.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vinyl.data.onboarding.FakeOnboardingRepository
import com.example.vinyl.ui.location.LocationGateScreen
import com.example.vinyl.ui.theme.VinylPalette
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 4

/**
 * The four-step first-run flow: server-assigned username + icon pick, genre picks, location
 * permission (reuses [LocationGateScreen] as-is — it already handles skip and save), then
 * notifications. Nothing here writes `onboarding_completed`; the last page's RPC does that.
 *
 * This replaces the standalone `LocationGateScreen` call in MainActivity — see that screen's own
 * docstring, which already anticipated this.
 */
@Composable
fun OnboardingPagerScreen(
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    fun advance() {
        if (pagerState.currentPage < PAGE_COUNT - 1) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        } else {
            onOnboardingComplete()
        }
    }

    // No page before 0, so this is only ever called while a back button is actually showing.
    fun retreat() {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VinylPalette.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 10.dp),
    ) {
        // Back button at the left (replacing the logo's old spot on every page except the
        // first, which has nothing to go back to), logo at the right. SpaceBetween pushes them
        // to opposite ends regardless of which one is showing.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (pagerState.currentPage > 0) {
                IconButton(onClick = ::retreat) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = VinylPalette.TealAccent,
                    )
                }
            } else {
                // Reserves the same width AND height as the button above (not just width), so
                // the row itself is the same height on every page and the logo doesn't shift
                // vertically just because page 1 has no button to match.
                Spacer(Modifier.size(48.dp))
            }
            OnboardingHeader()
        }

        StepIndicator(
            currentPage = pagerState.currentPage,
            pageCount = PAGE_COUNT,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .padding(top = 70.dp),
        )

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> UsernameAvatarPage(viewModel = viewModel, onNext = ::advance)
                1 -> GenrePage(viewModel = viewModel, onNext = ::advance)
                2 -> LocationGateScreen(onDone = ::advance)
                3 -> NotificationPage(viewModel = viewModel, onFinish = ::advance)
            }
        }
    }
}

@Composable
private fun StepIndicator(currentPage: Int, pageCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
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

/**
 * Runs the real flow end to end using [FakeOnboardingRepository] — the same stub used for the
 * `bypassAuthForTesting` path in `MainActivity` — so this previews without a signed-in session or
 * live Supabase calls. Page navigation still only moves forward via each page's own button
 * (`userScrollEnabled = false` above), so use the preview's interactive mode and tap through
 * rather than expecting swipe here.
 */
@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun OnboardingPagerScreenPreview() {
    OnboardingPagerScreen(
        onOnboardingComplete = {},
        viewModel = OnboardingViewModel(repository = FakeOnboardingRepository()),
    )
}