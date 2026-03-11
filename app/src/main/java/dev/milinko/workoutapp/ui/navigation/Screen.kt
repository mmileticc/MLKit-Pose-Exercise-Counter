package dev.milinko.workoutapp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Početna", Icons.Default.Home)
    object Training : Screen("training", "Trening", Icons.Default.PlayArrow)
    object Statistics : Screen("statistics", "Statistika", Icons.Default.History)
}
