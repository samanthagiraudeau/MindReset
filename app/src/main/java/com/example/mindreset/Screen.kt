package com.example.mindreset

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Timer : Screen("timer", "Minuteur", Icons.Default.Build)
    object AppBlock : Screen("app_block", "Bloquer", Icons.Default.Block)
    object ThoughtsList : Screen("thoughts_list", "Mes Listes", Icons.Default.List)
    object Reminders : Screen("reminders", "Rappels", Icons.Default.Notifications)
}

val bottomNavItems = listOf(
    Screen.Timer,
    Screen.AppBlock,
    Screen.ThoughtsList,
    Screen.Reminders
)
