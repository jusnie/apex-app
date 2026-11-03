package com.apex.app.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.apex.app.R

enum class AppThemeColor(
    val key: String,
    @param:StringRes val displayNameRes: Int,
    val start: Color,
    val end: Color
) {
    RED("red", R.string.theme_color_red, Color(0xFFE10600), Color(0xFFFF2E2E)),
    APEX_PINK("apexPink", R.string.theme_color_apex_pink, Color(0xFFE10600), Color(0xFFFF2E2E)),
    ORANGE("orange", R.string.theme_color_orange, Color(0xFFE10600), Color(0xFFFF2E2E)),
    GREEN("green", R.string.theme_color_green, Color(0xFFE10600), Color(0xFFFF2E2E)),
    MINT("mint", R.string.theme_color_mint, Color(0xFFE10600), Color(0xFFFF2E2E)),
    TEAL("teal", R.string.theme_color_teal, Color(0xFFE10600), Color(0xFFFF2E2E)),
    BLUE("blue", R.string.theme_color_blue, Color(0xFFE10600), Color(0xFFFF2E2E)),
    PURPLE("purple", R.string.theme_color_purple, Color(0xFFE10600), Color(0xFFFF2E2E)),
    YELLOW("yellow", R.string.theme_color_yellow, Color(0xFFE10600), Color(0xFFFF2E2E)),
    CORAL("coral", R.string.theme_color_coral, Color(0xFFE10600), Color(0xFFFF2E2E)),
    ROSE_GOLD("roseGold", R.string.theme_color_rose_gold, Color(0xFFE10600), Color(0xFFFF2E2E)),
    MOCHA_BROWN("mochaBrown", R.string.theme_color_mocha_brown, Color(0xFFE10600), Color(0xFFFF2E2E)),
    INDIGO("indigo", R.string.theme_color_indigo, Color(0xFFE10600), Color(0xFFFF2E2E)),
    LAVENDER("lavender", R.string.theme_color_lavender, Color(0xFFE10600), Color(0xFFFF2E2E)),
    SKY_CYAN("skyCyan", R.string.theme_color_sky_cyan, Color(0xFFE10600), Color(0xFFFF2E2E)),
    GRAPHITE("graphite", R.string.theme_color_graphite, Color(0xFFE10600), Color(0xFFFF2E2E)),
    BABY_PINK("babyPink", R.string.theme_color_baby_pink, Color(0xFFE10600), Color(0xFFFF2E2E)),
    LIME("lime", R.string.theme_color_lime, Color(0xFFE10600), Color(0xFFFF2E2E));

    val labelRes: Int get() = displayNameRes

    companion object {
        const val DEFAULT_KEY = "red"

        fun fromKey(key: String?): AppThemeColor =
            values().firstOrNull { it.key == key } ?: RED
    }
}

object AppColors {
    private var activeThemeColor: AppThemeColor by mutableStateOf(AppThemeColor.RED)

    fun setThemeColor(themeColor: AppThemeColor) {
        activeThemeColor = AppThemeColor.RED
    }

    fun applyThemeColor(themeColor: AppThemeColor) {
        activeThemeColor = AppThemeColor.RED
    }

    val ThemeColor: AppThemeColor
        get() = AppThemeColor.RED

    val CalorieStart: Color
        get() = Color(0xFFE10600)

    val CalorieEnd: Color
        get() = Color(0xFFFF2E2E)

    val Calorie: Color
        get() = CalorieStart

    val Protein: Color = Color(0xFFFF453A)
    val Carbs: Color = Color(0xFFFFFFFF)
    val Fat: Color = Color(0xFFEF4444)

    val CalorieGradient: Brush
        get() = Brush.linearGradient(listOf(CalorieStart, CalorieEnd))

    // Pure Black background + Crisp White text enforced globally across APEX
    val AppBackgroundLight = Color(0xFF000000)
    val AppBackgroundDark = Color(0xFF000000)

    val AppCardLight = Color(0xFF0B0B10)
    val AppCardDark = Color(0xFF0B0B10)

    val OnLight = Color(0xFFFFFFFF)
    val OnDark = Color(0xFFFFFFFF)

    val MutedLight = Color(0xFFB8B8C2)
    val MutedDark = Color(0xFFB8B8C2)

    val DividerLight = Color(0xFF22222B)
    val DividerDark = Color(0xFF22222B)
}
