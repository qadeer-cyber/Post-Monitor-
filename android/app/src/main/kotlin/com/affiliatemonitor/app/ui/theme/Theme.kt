package com.affiliatemonitor.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = NeonBlue,
    onPrimary = Color.Black,
    secondary = NeonGreen,
    onSecondary = Color.Black,
    tertiary = NeonBlue,
    background = DeepBg,
    onBackground = Color(0xFFE6EDF3),
    surface = ElevBg,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF121826),
    onSurfaceVariant = TextMuted,
    error = Danger,
    onError = Color.Black,
)

@Composable
fun AppTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // We always use the dark palette — the product is dark-mode by spec.
    val colors = DarkColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DeepBg.toArgb()
            window.navigationBarColor = DeepBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
    @Suppress("UNUSED_VARIABLE") val ctx = LocalContext.current
}
