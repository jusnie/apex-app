package com.apex.app.ui.workouts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.apex.app.ui.theme.AppColors

data class WorkoutsColors(
    val background: Color,
    val charcoal: Color,
    val card: Color,
    val panel: Color,
    val hairline: Color,
    val accent: Color,
    val secondaryAccent: Color,
    val onAccent: Color,
    val mutedText: Color,
    val isDark: Boolean
)

@Composable
fun workoutsColors(): WorkoutsColors {
    val bg = MaterialTheme.colorScheme.background
    val isDark = (bg.red + bg.green + bg.blue) / 3f < 0.5f

    return WorkoutsColors(
        background = if (isDark) AppColors.AppBackgroundDark else AppColors.AppBackgroundLight,
        charcoal = if (isDark) AppColors.OnDark else AppColors.OnLight,
        card = if (isDark) AppColors.AppCardDark else AppColors.AppCardLight,
        panel = Color(0xFF202638),
        hairline = if (isDark) AppColors.DividerDark else AppColors.DividerLight,
        accent = AppColors.Calorie,
        secondaryAccent = AppColors.CalorieEnd,
        onAccent = Color.White,
        mutedText = if (isDark) AppColors.MutedDark else AppColors.MutedLight,
        isDark = isDark
    )
}
