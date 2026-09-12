package com.example.vinyl.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.graphics.vector.ImageVector

enum class VinylDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Collection(route = "collection", label = "Collection", icon = Icons.Filled.Image),
    Home(route = "home", label = "Home", icon = Icons.Filled.Headphones),
    Create(route = "create", label = "Create", icon = Icons.Filled.AddCircle),
}
