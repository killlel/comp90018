package com.example.vinyl.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.vinyl.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import java.security.MessageDigest
import java.util.UUID

class GoogleAuthRepository(private val context: Context) {

    suspend fun signIn(): Result<Unit> = runCatching {
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
        } catch (e: GetCredentialException) {
            throw IllegalStateException("Google sign-in failed: ${e.message}", e)
        }

        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("Unexpected credential type returned")
        }

        val googleIdToken = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw IllegalStateException("Couldn't parse Google ID token", e)
        }

        Supabase.client.auth.signInWith(IDToken) {
            idToken = googleIdToken
            provider = Google
            nonce = rawNonce
        }
    }

    suspend fun signOut() {
        Supabase.client.auth.signOut()
    }
}
