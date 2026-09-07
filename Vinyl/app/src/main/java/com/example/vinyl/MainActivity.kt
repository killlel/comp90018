package com.example.vinyl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.vinyl.ui.theme.VinylTheme
import com.example.vinyl.ui.write.WriteCardScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VinylTheme {
                WriteCardScreen()
            }
        }
    }
}