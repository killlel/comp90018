package com.example.vinyl.ui.auth

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vinyl.R
import com.example.vinyl.data.GoogleAuthRepository
import com.example.vinyl.data.GoogleSignInOutcome
import com.example.vinyl.ui.onboarding.OnboardingHorizontalPadding
import com.example.vinyl.ui.theme.PoppinsFontFamily
import com.example.vinyl.ui.theme.VinylPalette
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

/**
 * The signed-out screen: the logo and a "Continue with Google" button.
 *
 * Sign-in goes through Credential Manager first. If that fails in a way a browser might get past,
 * or the browser callback came back with an error, a "Sign in with a browser instead" button is
 * offered. A successful sign-in changes the session status, which swaps this screen out, so it
 * never navigates anywhere itself.
 *
 * @param callbackError an error carried back by the browser sign-in callback, shown under the button.
 * @param onClearCallbackError clears [callbackError] when the user starts another attempt.
 */
@Composable
internal fun AuthScreen(
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
                Log.e(TAG, "Google browser sign-in could not be started", it)
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
                                Log.e(TAG, "Google sign-in could not be started", e)
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

private const val TAG = "AuthScreen"