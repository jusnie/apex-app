package com.apex.app.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun apexDarkScheme(themeColor: AppThemeColor): ColorScheme = darkColorScheme(
    primary = themeColor.start,
    onPrimary = Color.White,
    primaryContainer = themeColor.start.copy(alpha = 0.22f),
    onPrimaryContainer = Color.White,
    secondary = themeColor.end,
    onSecondary = Color.White,
    tertiary = Color.White,
    background = AppColors.AppBackgroundDark,
    onBackground = AppColors.OnDark,
    surface = AppColors.AppCardDark,
    onSurface = AppColors.OnDark,
    surfaceVariant = Color(0xFF16161D),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF272730),
    outlineVariant = Color(0xFF1C1C24),
    error = Color(0xFFFF3B30),
    onError = Color.White
)

private fun apexLightScheme(themeColor: AppThemeColor): ColorScheme = lightColorScheme(
    primary = themeColor.start,
    onPrimary = Color.White,
    primaryContainer = themeColor.start.copy(alpha = 0.12f),
    onPrimaryContainer = AppColors.OnLight,
    secondary = themeColor.end,
    onSecondary = Color.White,
    tertiary = Color(0xFF09090B),
    background = AppColors.AppBackgroundLight,
    onBackground = AppColors.OnLight,
    surface = AppColors.AppCardLight,
    onSurface = AppColors.OnLight,
    surfaceVariant = Color(0xFFF1F1F5),
    onSurfaceVariant = Color(0xFF52525B),
    outline = Color(0xFFE4E4E7),
    outlineVariant = Color(0xFFD4D4D8),
    error = Color(0xFFE10600),
    onError = Color.White
)

@Composable
fun APEXTheme(
    darkTheme: Boolean = true,
    themeColor: AppThemeColor = AppThemeColor.RED,
    content: @Composable () -> Unit
) {
    AppColors.applyThemeColor(themeColor)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) apexDarkScheme(themeColor) else apexLightScheme(themeColor),
        content = content
    )
}
