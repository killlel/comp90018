package com.example.vinyl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.vinyl.data.GoogleAuthRepository
import com.example.vinyl.data.Supabase
import com.example.vinyl.ui.navigation.VinylNavHost
import com.example.vinyl.ui.theme.VinylTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VinylTheme {
                VinylApp()
            }
        }
    }
}

@Composable
fun VinylApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleAuthRepository = remember { GoogleAuthRepository(context) }
    val sessionStatus by Supabase.client.auth.sessionStatus.collectAsState()

    when (sessionStatus) {
        is SessionStatus.Authenticated -> {
            VinylNavHost(
                modifier = modifier,
                onSignOut = { scope.launch { googleAuthRepository.signOut() } },
            )
        }
        else -> {
            Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
                AuthScreen(
                    modifier = Modifier.padding(innerPadding),
                    googleAuthRepository = googleAuthRepository,
                )
            }
        }
    }
}

@Composable
private fun AuthScreen(
    googleAuthRepository: GoogleAuthRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
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
    }
}
