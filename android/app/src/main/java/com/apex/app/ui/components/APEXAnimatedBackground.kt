package com.apex.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
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
 * Signature APEX smooth ambient animated background:
 * Pure OLED Black (#000000) base with silky continuous 0..2π Lissajous Crimson Red radial drift.
 */
@Composable
fun APEXAnimatedBackground(
    modifier: Modifier = Modifier
) {
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

        // 1. Pure Black (#000000) canvas
        drawRect(color = Color.Black)

        // 2. Primary APEX Crimson Red drifting ambient orb
        val orb1Center = Offset(
            x = w * (0.24f + 0.22f * cos(phaseA)),
            y = h * (0.14f + 0.09f * sin(phaseA + 0.4f))
        )
        val orb1Radius = maxDim * 0.58f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryAccent.copy(alpha = 0.14f),
                    primaryAccent.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = orb1Center,
                radius = orb1Radius
            ),
            radius = orb1Radius,
            center = orb1Center
        )

        // 3. Secondary Scarlet Red counter-drifting ambient orb
        val orb2Center = Offset(
            x = w * (0.78f - 0.24f * sin(phaseB)),
            y = h * (0.80f - 0.13f * cos(phaseB))
        )
        val orb2Radius = maxDim * 0.54f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondaryAccent.copy(alpha = 0.10f),
                    primaryAccent.copy(alpha = 0.035f),
                    Color.Transparent
                ),
                center = orb2Center,
                radius = orb2Radius
            ),
            radius = orb2Radius,
            center = orb2Center
        )
    }
}
