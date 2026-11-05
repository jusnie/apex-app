package com.apex.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.apex.app.ui.theme.AppColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Signature APEX Animated Frosted-Blur Aurora Background:
 * - Rich Deep Charcoal-Slate base (#0F121B -> #161B28 -> #14111D) — never flat pitch-black
 * - 5 continuous 60/120fps Lissajous-orbiting aurora light blooms (Crimson, Scarlet, Ruby,
 *   Midnight Indigo-Slate, and Warm Coral) rendered through hardware GPU Gaussian blur
 *   (Modifier.blur) + multi-stop soft radial falloff
 * - Translucent frosted diffusion veil preserving crisp WCAG AAA contrast for White text
 */
@Composable
fun APEXAnimatedBackground(
    modifier: Modifier = Modifier
) {
    val crimson = AppColors.Calorie        // #E10600
    val scarlet = AppColors.CalorieEnd     // #FF2E2E
    val ruby = Color(0xFFB80A2C)
    val midnightSlate = Color(0xFF2B3658)
    val warmEmber = Color(0xFFFF5247)

    val infiniteTransition = rememberInfiniteTransition(label = "apexBlurAurora")

    val phaseA by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraPhaseA"
    )
    val phaseB by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15_500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraPhaseB"
    )
    val phaseC by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 19_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraPhaseC"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F121B),
                        Color(0xFF161B28),
                        Color(0xFF14111C)
                    )
                )
            )
    ) {
        // Hardware-accelerated GPU Gaussian blur layer over the flowing liquid aurora mesh
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 68.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        ) {
            val w = size.width
            val h = size.height
            val maxDim = max(w, h).coerceAtLeast(1f)

            // 1. Primary Crimson Aurora Bloom (upper orbit + gentle breathing radius)
            val c1 = Offset(
                x = w * (0.26f + 0.30f * cos(phaseA)),
                y = h * (0.18f + 0.14f * sin(phaseA * 1.0f + 0.35f))
            )
            val r1 = maxDim * (0.48f + 0.05f * sin(phaseB))
            drawSoftAuroraOrb(center = c1, radius = r1, color = crimson, peakAlpha = 0.42f)

            // 2. Midnight Slate-Indigo Counter-Bloom (adds atmospheric non-black depth)
            val c2 = Offset(
                x = w * (0.76f - 0.28f * cos(phaseC)),
                y = h * (0.34f + 0.18f * sin(phaseC))
            )
            val r2 = maxDim * (0.54f + 0.04f * cos(phaseA))
            drawSoftAuroraOrb(center = c2, radius = r2, color = midnightSlate, peakAlpha = 0.52f)

            // 3. Scarlet Ember Bloom (lower-right <-> mid-left sweep)
            val c3 = Offset(
                x = w * (0.72f - 0.32f * sin(phaseB)),
                y = h * (0.78f - 0.18f * cos(phaseB))
            )
            val r3 = maxDim * (0.46f + 0.05f * cos(phaseC))
            drawSoftAuroraOrb(center = c3, radius = r3, color = scarlet, peakAlpha = 0.34f)

            // 4. Deep Ruby Wine Core Drift (center-left figure-eight)
            val c4 = Offset(
                x = w * (0.34f + 0.24f * sin(phaseC + 0.8f)),
                y = h * (0.56f + 0.16f * sin(phaseA))
            )
            val r4 = maxDim * 0.44f
            drawSoftAuroraOrb(center = c4, radius = r4, color = ruby, peakAlpha = 0.30f)

            // 5. Warm Coral Specular Highlight (floating top/bottom accent)
            val c5 = Offset(
                x = w * (0.52f + 0.26f * cos(phaseB - phaseA * 0.5f)),
                y = h * (0.42f + 0.24f * sin(phaseB))
            )
            val r5 = maxDim * 0.32f
            drawSoftAuroraOrb(center = c5, radius = r5, color = warmEmber, peakAlpha = 0.20f)
        }

        // Frosted-glass diffusion veil so crisp White typography pops cleanly everywhere
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF10131D).copy(alpha = 0.22f),
                            Color(0xFF131724).copy(alpha = 0.18f),
                            Color(0xFF0E1018).copy(alpha = 0.30f)
                        )
                    )
                )
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSoftAuroraOrb(
    center: Offset,
    radius: Float,
    color: Color,
    peakAlpha: Float
) {
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to color.copy(alpha = peakAlpha),
                0.35f to color.copy(alpha = peakAlpha * 0.62f),
                0.68f to color.copy(alpha = peakAlpha * 0.22f),
                1.00f to Color.Transparent
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}
