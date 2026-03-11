package dev.milinko.workoutapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.milinko.workoutapp.ui.ExerciseScreen
import dev.milinko.workoutapp.ui.HomeScreen
import dev.milinko.workoutapp.ui.StatisticsScreen
import dev.milinko.workoutapp.ui.navigation.Screen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Launcher za runtime permission
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            // Bez obzira na dozvolu, sada uvek postavljamo sadržaj
            // UI će sam ponuditi ručni unos ako kamera nije dostupna
            setupContent()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Uvek idemo na setupContent, ali usput proverimo/tražimo dozvolu
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            setupContent()
        }
    }

    private fun setupContent() {
        setContent {
            val navController = rememberNavController()
            val screens = listOf(Screen.Home, Screen.Training, Screen.Statistics)

            Scaffold(
                bottomBar = {
                    NavigationBar {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination
                        screens.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = null) },
                                label = { Text(screen.title) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController,
                    startDestination = Screen.Home.route,
                    Modifier.padding(innerPadding)
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(onStartTraining = {
                            navController.navigate(Screen.Training.route)
                        })
                    }
                    composable(Screen.Training.route) {
                        ExerciseScreen()
                    }
                    composable(Screen.Statistics.route) {
                        StatisticsScreen()
                    }
                }
            }
        }
    }
}