package com.example.vinyl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.example.vinyl.ui.theme.VinylPalette
import com.example.vinyl.ui.theme.VinylTheme
import com.example.vinyl.ui.write.WriteCardScreen

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

private enum class AppTab(val label: String) {
    Collection("Collection"),
    Home("Home"),
    Create("Create"),
}

@Composable
private fun VinylApp() {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Home) }

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
                AppTab.Home -> PlaceholderTab("Home")
                AppTab.Create -> WriteCardScreen()
            }
        }
    }
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