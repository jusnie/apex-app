package com.apex.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.apex.app.ui.theme.AppColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Signature APEX smooth ambient animated background (Obsidian Black, Crimson Red & Crisp White).
 *
 * Uses continuous 0..2π phase rotation so radial light orbs drift along smooth Lissajous curves
 * with zero turnaround bounce or frame hitching.
 */
@Composable
fun APEXAnimatedBackground(
    modifier: Modifier = Modifier
) {
    val bgColor = MaterialTheme.colorScheme.background
    val isDark = (bgColor.red + bgColor.green + bgColor.blue) / 3f < 0.5f

    val primaryAccent = AppColors.Calorie
    val secondaryAccent = AppColors.CalorieEnd

    val infiniteTransition = rememberInfiniteTransition(label = "apexAmbientBg")
    val phaseA by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "apexPhaseA"
    )
    val phaseB by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "apexPhaseB"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val maxDim = max(w, h).coerceAtLeast(1f)

        // 1. Base Obsidian Black / Crisp Ice White canvas
        drawRect(color = if (isDark) AppColors.AppBackgroundDark else AppColors.AppBackgroundLight)

        // 2. Primary APEX Crimson Red drifting ambient orb (upper-left <-> upper-right)
        val orb1Center = Offset(
            x = w * (0.24f + 0.22f * cos(phaseA)),
            y = h * (0.16f + 0.10f * sin(phaseA * 1.0f + 0.4f))
        )
        val orb1Radius = maxDim * 0.62f
        val orb1Alpha = if (isDark) 0.16f else 0.075f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryAccent.copy(alpha = orb1Alpha),
                    primaryAccent.copy(alpha = orb1Alpha * 0.42f),
                    Color.Transparent
                ),
                center = orb1Center,
                radius = orb1Radius
            ),
            radius = orb1Radius,
            center = orb1Center
        )

        // 3. Secondary Scarlet Red counter-drifting ambient orb (lower-right <-> mid-left)
        val orb2Center = Offset(
            x = w * (0.78f - 0.24f * sin(phaseB)),
            y = h * (0.78f - 0.14f * cos(phaseB))
        )
        val orb2Radius = maxDim * 0.58f
        val orb2Alpha = if (isDark) 0.12f else 0.055f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondaryAccent.copy(alpha = orb2Alpha),
                    primaryAccent.copy(alpha = orb2Alpha * 0.35f),
                    Color.Transparent
                ),
                center = orb2Center,
                radius = orb2Radius
            ),
            radius = orb2Radius,
            center = orb2Center
        )

        // 4. Subtle Crisp White atmospheric crown glow for liquid-glass depth
        val crownCenter = Offset(
            x = w * (0.50f + 0.12f * sin(phaseA - phaseB)),
            y = -h * 0.06f
        )
        val crownRadius = maxDim * 0.48f
        val crownAlpha = if (isDark) 0.045f else 0.45f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = crownAlpha),
                    Color.Transparent
                ),
                center = crownCenter,
                radius = crownRadius
            ),
            radius = crownRadius,
            center = crownCenter
        )
    }
}
