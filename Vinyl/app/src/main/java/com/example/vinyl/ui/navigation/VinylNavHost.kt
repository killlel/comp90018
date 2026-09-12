package com.example.vinyl.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.vinyl.ui.theme.VinylColors
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import com.example.vinyl.ui.collection.CollectionScreen
import com.example.vinyl.ui.create.CreateScreen
import com.example.vinyl.ui.home.HomeScreen
import com.example.vinyl.ui.theme.VinylTheme

@Composable
fun VinylNavHost(modifier: Modifier = Modifier, onSignOut: () -> Unit = {}) {
    val navController = rememberNavController()

    Scaffold(
        modifier = modifier,
        bottomBar = { VinylBottomBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = VinylDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(VinylDestination.Collection.route) {
                CollectionScreen(onProfileClick = onSignOut)
            }
            composable(VinylDestination.Home.route) { HomeScreen() }
            composable(VinylDestination.Create.route) { CreateScreen() }
        }
    }
}

@Composable
private fun VinylBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Column {
        HorizontalDivider(color = VinylColors.Divider, thickness = 1.dp)
        NavigationBar(containerColor = VinylColors.NavBar) {
            VinylDestination.entries.forEach { destination ->
                NavigationBarItem(
                    selected = currentRoute == destination.route,
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label, style = MaterialTheme.typography.bodySmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VinylColors.Teal,
                        selectedTextColor = VinylColors.Teal,
                        unselectedIconColor = Color.White,
                        unselectedTextColor = Color.White,
                        indicatorColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VinylNavHostPreview() {
    VinylTheme { VinylNavHost() }
}
