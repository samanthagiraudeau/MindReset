package com.example.mindreset.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color(0xFF003829),
    primaryContainer = Color(0xFF00513C),
    onPrimaryContainer = Color(0xFF91F7D1),
    onTertiary = Color(0xFFf1ecfc),
    
    secondary = AccentViolet,
    onSecondary = Color(0xFF320070),
    secondaryContainer = Color(0xFF4B23A0),
    onSecondaryContainer = Color(0xFFEADDFF),
    
    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF91F7D1),
    onPrimaryContainer = Color(0xFF002117),
    
    secondary = AccentViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEADDFF),
    onSecondaryContainer = Color(0xFF21005D),
    
    background = LightBackground,
    onBackground = Color(0xFF1A1C1B),
    surface = LightSurface,
    onSurface = Color(0xFF1A1C1B),
    
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    onTertiary = DarkPurple
)

@Composable
fun MindResetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
