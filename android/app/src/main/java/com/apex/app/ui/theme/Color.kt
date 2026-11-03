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
    APEX_PINK("apexPink", R.string.theme_color_apex_pink, Color(0xFFDC143C), Color(0xFFFF3B30)),
    ORANGE("orange", R.string.theme_color_orange, Color(0xFFFF6B00), Color(0xFFFF9F1C)),
    GREEN("green", R.string.theme_color_green, Color(0xFF1DB954), Color(0xFF4ADE80)),
    MINT("mint", R.string.theme_color_mint, Color(0xFF00C7BE), Color(0xFF66E3DC)),
    TEAL("teal", R.string.theme_color_teal, Color(0xFF0EA5E9), Color(0xFF38BDF8)),
    BLUE("blue", R.string.theme_color_blue, Color(0xFF007AFF), Color(0xFF4DA3FF)),
    PURPLE("purple", R.string.theme_color_purple, Color(0xFF8B5CF6), Color(0xFFB388FF)),
    YELLOW("yellow", R.string.theme_color_yellow, Color(0xFFF59E0B), Color(0xFFFBBF24)),
    CORAL("coral", R.string.theme_color_coral, Color(0xFFFF3B30), Color(0xFFFF6B6B)),
    ROSE_GOLD("roseGold", R.string.theme_color_rose_gold, Color(0xFFB91C1C), Color(0xFFEF4444)),
    MOCHA_BROWN("mochaBrown", R.string.theme_color_mocha_brown, Color(0xFF78350F), Color(0xFFB45309)),
    INDIGO("indigo", R.string.theme_color_indigo, Color(0xFF4F46E5), Color(0xFF818CF8)),
    LAVENDER("lavender", R.string.theme_color_lavender, Color(0xFF9333EA), Color(0xFFC084FC)),
    SKY_CYAN("skyCyan", R.string.theme_color_sky_cyan, Color(0xFF06B6D4), Color(0xFF67E8F9)),
    GRAPHITE("graphite", R.string.theme_color_graphite, Color(0xFF18181B), Color(0xFF52525B)),
    BABY_PINK("babyPink", R.string.theme_color_baby_pink, Color(0xFFE11D48), Color(0xFFFB7185)),
    LIME("lime", R.string.theme_color_lime, Color(0xFF65A30D), Color(0xFFA3E635));

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
        activeThemeColor = themeColor
    }

    fun applyThemeColor(themeColor: AppThemeColor) {
        activeThemeColor = themeColor
    }

    val ThemeColor: AppThemeColor
        get() = activeThemeColor

    val CalorieStart: Color
        get() = activeThemeColor.start

    val CalorieEnd: Color
        get() = activeThemeColor.end

    val Calorie: Color
        get() = CalorieStart

    val Protein: Color = Color(0xFFFF453A)
    val Carbs: Color = Color(0xFFF8FAFC)
    val Fat: Color = Color(0xFFEF4444)

    val CalorieGradient: Brush
        get() = Brush.linearGradient(listOf(CalorieStart, CalorieEnd))

    val AppBackgroundLight = Color(0xFFF8F8FA)
    val AppBackgroundDark = Color(0xFF050507)

    val AppCardLight = Color(0xFFFFFFFF)
    val AppCardDark = Color(0xFF0E0E12)

    val OnLight = Color(0xFF09090B)
    val OnDark = Color(0xFFFFFFFF)

    val MutedLight = Color(0xFF52525B)
    val MutedDark = Color(0xFFA1A1AA)

    val DividerLight = Color(0xFFE4E4E7)
    val DividerDark = Color(0xFF23232D)
}
