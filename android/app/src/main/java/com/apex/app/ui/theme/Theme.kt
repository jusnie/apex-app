package com.apex.app.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ApexBlackWhiteRedScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFE10600),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE10600).copy(alpha = 0.24f),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFFF2E2E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE10600).copy(alpha = 0.20f),
    onSecondaryContainer = Color.White,
    tertiary = Color.White,
    onTertiary = Color.Black,
    background = Color(0xFF000000),
    onBackground = Color.White,
    surface = Color(0xFF0B0B10),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF15151D),
    onSurfaceVariant = Color(0xFFC4C4CE),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF09090D),
    surfaceContainer = Color(0xFF0E0E14),
    surfaceContainerHigh = Color(0xFF15151E),
    surfaceContainerHighest = Color(0xFF1D1D28),
    outline = Color(0xFF2A2A35),
    outlineVariant = Color(0xFF1D1D26),
    error = Color(0xFFFF3B30),
    onError = Color.White
)

@Composable
fun APEXTheme(
    darkTheme: Boolean = true,
    themeColor: AppThemeColor = AppThemeColor.RED,
    content: @Composable () -> Unit
) {
    AppColors.applyThemeColor(AppThemeColor.RED)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(colorScheme = ApexBlackWhiteRedScheme) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            content = content
        )
    }
}
