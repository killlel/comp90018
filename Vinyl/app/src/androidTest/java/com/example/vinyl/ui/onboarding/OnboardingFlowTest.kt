package com.example.vinyl.ui.onboarding

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.vinyl.data.onboarding.FakeOnboardingRepository
import com.example.vinyl.data.onboarding.OnboardingRepository
import com.example.vinyl.ui.theme.VinylTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OnboardingFlowTest {
    @get:Rule val compose = createComposeRule()
    private var primaryButtonBounds: androidx.compose.ui.geometry.Rect? = null

    @Test
    fun completeFlowKeepsChoicesAndCompletesOnlyOnEntry() {
        val repository = FakeOnboardingRepository()
        var entered = false
        compose.setContent {
            VinylTheme {
                val model = remember { OnboardingViewModel(repository) }
                OnboardingPagerScreen(onOnboardingComplete = { entered = true }, viewModel = model)
            }
        }
        saveScreenshot("welcome")
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Happy Giraffe").assertIsDisplayed()
        compose.onNodeWithContentDescription("Try another name").performClick()
        compose.onNodeWithText("Sleepy Tiger").assertIsDisplayed()
        saveScreenshot("name")
        compose.onNodeWithText("Continue").performClick()
        saveScreenshot("avatar")
        compose.onNodeWithContentDescription("Profile icon 3").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Ocean background").performScrollTo().performClick()
        compose.onNodeWithText("Continue").performClick()
        saveScreenshot("genres")
        compose.onNodeWithText("JAZZ").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Profile icon 3").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("Ocean background").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("JAZZ").performScrollTo().assertIsOn()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Share your city").assertIsDisplayed()
        saveScreenshot("city")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithText("Turn on\nnotifications").assertIsDisplayed()
        saveScreenshot("notifications")
        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithText("You are ready").assertIsDisplayed()
        saveScreenshot("ready")
        assertFalse(runBlocking { repository.getMyProfile().getOrThrow().onboardingCompleted })
        compose.onNodeWithText("Enter Vinyl").performClick()
        compose.waitUntil { entered }
        val profile = runBlocking { repository.getMyProfile().getOrThrow() }
        assertTrue(profile.onboardingCompleted)
        assertEquals("Sleepy Tiger", profile.username)
        assertEquals("avatar_03", profile.avatarSlug)
        assertEquals(listOf("jazz"), profile.favoriteGenres)
    }
    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val action = when (name) {
            "welcome" -> "Get started"
            "city" -> {
                // Permission state can retain a previous denial on the emulator.
                compose.waitUntil(10_000) {
                    listOf("Share my city", "Open app settings").any {
                        compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty()
                    }
                }
                if (compose.onAllNodesWithText("Share my city").fetchSemanticsNodes().isNotEmpty())
                    "Share my city" else "Open app settings"
            }
            "notifications" -> "Turn on"
            "ready" -> "Enter Vinyl"
            else -> "Continue"
        }
        val button = compose.onNodeWithText(action).assertHeightIsEqualTo(56.dp)
        val bounds = button.fetchSemanticsNode().boundsInRoot
        primaryButtonBounds?.let { assertEquals("Primary actions must stay aligned on every step", it, bounds) }
        primaryButtonBounds = bounds
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        File(directory, "onboarding-$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun avatarChoicesRemainReachableOnSmallPhoneWithLargeText() {
        compose.setContent {
            VinylTheme {
                val density = LocalDensity.current
                val model = remember { OnboardingViewModel(FakeOnboardingRepository()) }
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        IconPage(model, onNext = {})
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Profile icon 8").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Slate background").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Continue").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun failedCompletionStaysOnReadyAndCanBeRetried() {
        val fake = FakeOnboardingRepository()
        var attempts = 0
        var entered = false
        val repository = object : OnboardingRepository() {
            override suspend fun getMyProfile() = fake.getMyProfile()
            override suspend fun getAvatarOptions() = fake.getAvatarOptions()
            override suspend fun getGenreOptions() = fake.getGenreOptions()
            override suspend fun completeOnboarding(): Result<Unit> {
                attempts++
                return if (attempts == 1) Result.failure(IllegalStateException("Offline"))
                else fake.completeOnboarding()
            }
        }
        compose.setContent {
            VinylTheme { ReadyPage(remember { OnboardingViewModel(repository) }, onEnter = { entered = true }) }
        }
        compose.onNodeWithText("Enter Vinyl").performClick()
        compose.onNodeWithText("Something went wrong. Check your connection and try again.").assertIsDisplayed()
        assertFalse(entered)
        assertFalse(runBlocking { fake.getMyProfile().getOrThrow().onboardingCompleted })
        compose.onNodeWithText("Enter Vinyl").performClick()
        compose.waitUntil { entered }
        assertTrue(runBlocking { fake.getMyProfile().getOrThrow().onboardingCompleted })
    }
}
