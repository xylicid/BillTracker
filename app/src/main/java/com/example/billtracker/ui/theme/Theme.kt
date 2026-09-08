package com.example.billtracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// App is always black background / white text, by request — no light theme, no dynamic color.
private val AppColorScheme = darkColorScheme(
    primary = AccentGreen,
    onPrimary = PureBlack,
    secondary = AccentGreen,
    background = PureBlack,
    onBackground = PureWhite,
    surface = PureBlack,
    onSurface = PureWhite,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = MutedGray,
    error = DangerRed,
    onError = PureBlack,
    errorContainer = DangerRed,
    onErrorContainer = PureBlack
)

@Composable
fun BillTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}
