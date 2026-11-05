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

private val ApexSlateCrimsonScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFE10600),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE10600).copy(alpha = 0.26f),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFFF2E2E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE10600).copy(alpha = 0.22f),
    onSecondaryContainer = Color.White,
    tertiary = Color.White,
    onTertiary = Color(0xFF11141E),
    background = Color(0xFF11141E),
    onBackground = Color.White,
    surface = Color(0xFF1A1F2E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF23293B),
    onSurfaceVariant = Color(0xFFD0D4E0),
    surfaceContainerLowest = Color(0xFF0E1119),
    surfaceContainerLow = Color(0xFF151925),
    surfaceContainer = Color(0xFF1A1F2E),
    surfaceContainerHigh = Color(0xFF22283A),
    surfaceContainerHighest = Color(0xFF2B3247),
    outline = Color(0xFF363E54),
    outlineVariant = Color(0xFF272D3F),
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
    MaterialTheme(colorScheme = ApexSlateCrimsonScheme) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            content = content
        )
    }
}
