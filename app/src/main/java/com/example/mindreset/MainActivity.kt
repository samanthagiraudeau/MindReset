package com.example.mindreset

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.mindreset.ui.theme.MindResetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }

        setContent {
            var darkTheme by remember { mutableStateOf(false) }
            var showAppBlockSettings by remember { mutableStateOf(false) }

            MindResetTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                Scaffold(
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        CenterAlignedTopAppBar(
                            title = {
                                val currentScreen = bottomNavItems.find { it.route == currentDestination?.route }
                                Text(currentScreen?.title ?: "MindReset")
                            },
                            actions = {
                                val isOnAppBlock = currentDestination?.route == Screen.AppBlock.route
                                if (isOnAppBlock) {
                                    IconButton(onClick = { showAppBlockSettings = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Paramètres de blocage"
                                        )
                                    }
                                }
                                IconButton(onClick = { darkTheme = !darkTheme }) {
                                    Icon(
                                        imageVector = if (darkTheme) Icons.Default.Brightness7 else Icons.Default.Brightness4,
                                        contentDescription = "Toggle Dark Mode"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            bottomNavItems.forEach { screen ->
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
                        navController = navController,
                        startDestination = Screen.Timer.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Timer.route) { TimerScreen() }
                        composable(Screen.AppBlock.route) {
                            AppBlockScreen(
                                showSettings = showAppBlockSettings,
                                onSettingsDismiss = { showAppBlockSettings = false }
                            )
                        }
                        composable(Screen.ThoughtsList.route) { ThoughtsListScreen() }
                        composable(Screen.Reminders.route) { RemindersScreen() }
                    }
                }
            }
        }
    }
}
