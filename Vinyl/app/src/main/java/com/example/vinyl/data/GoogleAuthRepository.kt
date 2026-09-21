package com.example.vinyl.data

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.vinyl.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import java.security.MessageDigest
import java.util.UUID

/**
 * Result of the Credential Manager sign-in attempt. [NoDeviceAccount] is separated out because it
 * is the one failure the user can't fix from inside the app — it's the signal to fall back to the
 * browser flow rather than to show an error.
 */
sealed interface GoogleSignInOutcome {
    data object Success : GoogleSignInOutcome
    data object NoDeviceAccount : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Failed(val message: String) : GoogleSignInOutcome
}

class GoogleAuthRepository(private val context: Context) {

    /**
     * Primary path: Google's in-app bottom sheet, which can only offer accounts already present on
     * the device. Returns [GoogleSignInOutcome.NoDeviceAccount] when there are none.
     */
    suspend fun signIn(): GoogleSignInOutcome {
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = MessageDigest.getInstance("SHA-256")
            .digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(context)
        val result = try {
            credentialManager.getCredential(context, request)
        } catch (e: NoCredentialException) {
            Log.i(TAG, "No Google account available to Credential Manager", e)
            return GoogleSignInOutcome.NoDeviceAccount
        } catch (e: GetCredentialProviderConfigurationException) {
            // No credential provider at all, e.g. an emulator image without Play Services.
            Log.i(TAG, "No credential provider on this device", e)
            return GoogleSignInOutcome.NoDeviceAccount
        } catch (e: GetCredentialCancellationException) {
            Log.i(TAG, "User dismissed the Credential Manager sheet", e)
            return GoogleSignInOutcome.Cancelled
        } catch (e: GetCredentialException) {
            // The exception type is the diagnosis here and the message often isn't: an empty
            // GOOGLE_WEB_CLIENT_ID or a missing Android OAuth client SHA-1 both land in this branch.
            Log.e(TAG, "Credential Manager failed: ${e::class.java.simpleName}", e)
            return GoogleSignInOutcome.Failed("Google sign-in failed: ${e.message}")
        }

        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            Log.e(TAG, "Unexpected credential type: ${credential.type}")
            return GoogleSignInOutcome.Failed("Unexpected credential type returned")
        }

        val googleIdToken = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "Couldn't parse Google ID token", e)
            return GoogleSignInOutcome.Failed("Couldn't parse Google ID token")
        }

        return try {
            Supabase.client.auth.signInWith(IDToken) {
                idToken = googleIdToken
                provider = Google
                nonce = rawNonce
            }
            GoogleSignInOutcome.Success
        } catch (e: Exception) {
            Log.e(TAG, "Supabase rejected the Google ID token", e)
            GoogleSignInOutcome.Failed("Sign-in failed: ${e.message}")
        }
    }

    /**
     * Fallback for devices with no Google account. Opens the Google consent screen in a Custom Tab;
     * this returns as soon as the tab is launched. The session arrives later, via the
     * com.example.vinyl://auth-callback deeplink that MainActivity hands to handleDeeplinks().
     */
    suspend fun signInWithBrowser(): Result<Unit> = runCatching {
        Supabase.client.auth.signInWith(Google)
    }.onFailure {
        Log.e(TAG, "Couldn't start the browser sign-in flow", it)
    }

    suspend fun signOut() {
        Supabase.client.auth.signOut()
    }

    private companion object {
        const val TAG = "GoogleAuthRepository"
    }
}
