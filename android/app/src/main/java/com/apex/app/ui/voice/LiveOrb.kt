package com.apex.app.ui.voice

import android.animation.ValueAnimator
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.apex.app.services.voice.OrbMode
import com.apex.app.services.voice.VoiceLog
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * APEX Tactical Jarvis HUD + Arc-Reactor Core:
 * Precision concentric degree compass ticks, real-time 48-spoke polar voice spectrum ring,
 * 3D counter-rotating gyroscopic gimbal rings, dual turbine aperture irises, thinking radar
 * sweep, and an incandescent crimson-white core.
 *
 * Android 13+ runs the GPU AGSL shader (`OrbShader`); Android 8–12 draws the matching
 * tactical HUD Canvas renderer (`FallbackLiveOrb`).
 */
@Composable
fun LiveOrb(
    mode: OrbMode,
    tint: Color,
    modifier: Modifier = Modifier,
    level: () -> Float = { 0f },
    clearBackground: Boolean = false
) {
    val currentMode by rememberUpdatedState(mode)
    val currentLevel by rememberUpdatedState(level)
    val motion = remember { LiveOrbMotion() }
    var frame by remember { mutableStateOf(motion.still(mode)) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val started = lifecycle.isAtLeast(Lifecycle.State.STARTED)

    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        while (true) {
            if (!ValueAnimator.areAnimatorsEnabled()) {
                frame = motion.still(currentMode)
                delay(250)
            } else {
                withFrameNanos { nanos ->
                    frame = motion.advance(nanos, currentMode, currentLevel())
                }
            }
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { OrbShader.create() }
        if (shader != null) {
            val brush = remember(shader) { ShaderBrush(shader) }
            Canvas(modifier) {
                OrbShader.bind(shader, size.width, size.height, frame, tint, clearBackground)
                drawRect(brush)
            }
            return
        }
    }
    FallbackLiveOrb(frame, tint, modifier, clearBackground)
}

@Composable
internal fun FallbackLiveOrb(
    frame: LiveOrbFrame,
    tint: Color,
    modifier: Modifier = Modifier,
    clearBackground: Boolean = false
) {
    val layer = if (clearBackground) {
        modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    } else {
        modifier
    }
    Canvas(layer) { drawFallbackOrb(frame, tint, clearBackground) }
}

data class LiveOrbFrame(
    val time: Float = 0f,
    val spin: Float = 0f,
    val flow: Float = 0f,
    val level: Float = 0f,
    val active: Float = 0f,
    val thinking: Float = 0f,
    val speaking: Float = 0f
)

class LiveOrbMotion {
    private var start = -1L
    private var last = -1L
    private var frame = LiveOrbFrame()

    fun advance(nanos: Long, mode: OrbMode, level: Float): LiveOrbFrame {
        if (start < 0) start = nanos
        val dt = if (last < 0) 0f else ((nanos - last) / 1e9f).coerceIn(0f, 0.1f)
        last = nanos

        fun approach(value: Float, target: Float, rate: Float): Float =
            value + (target - value) * (1 - exp(-rate * dt))

        val loudness = level.coerceIn(0f, 1f)
        val newLevel = approach(frame.level, loudness, if (loudness > frame.level) 24f else 7f)
        val active = approach(frame.active, if (mode == OrbMode.IDLE) 0f else 1f, 3f)
        val thinking = approach(frame.thinking, if (mode == OrbMode.THINKING) 1f else 0f, 4f)
        val speaking = approach(frame.speaking, if (mode == OrbMode.SPEAKING) 1f else 0f, 4f)
        frame = LiveOrbFrame(
            time = (nanos - start) / 1e9f,
            spin = frame.spin + dt * (0.24f + 0.20f * active + 1.55f * thinking + 0.65f * newLevel),
            flow = frame.flow + dt * (0.28f + 0.32f * active + 0.55f * thinking + 2.5f * newLevel),
            level = newLevel,
            active = active,
            thinking = thinking,
            speaking = speaking
        )
        return frame
    }

    fun still(mode: OrbMode): LiveOrbFrame = frame.copy(
        level = 0f,
        active = if (mode == OrbMode.IDLE) 0f else 1f,
        thinking = if (mode == OrbMode.THINKING) 1f else 0f,
        speaking = if (mode == OrbMode.SPEAKING) 1f else 0f
    ).also { frame = it }
}

// MARK: - AGSL (Android 13+)

internal object OrbShader {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun compile(): RuntimeShader =
        RuntimeShader(SOURCE).also { bind(it, 1f, 1f, LiveOrbFrame(), Color.White, clear = false) }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun create(): RuntimeShader? = runCatching { compile() }
        .onFailure { VoiceLog.warn("LiveOrb", "AGSL orb unavailable: ${it.message}") }
        .getOrNull()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun bind(shader: RuntimeShader, width: Float, height: Float, frame: LiveOrbFrame, tint: Color, clear: Boolean) {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("time", frame.time)
        shader.setFloatUniform("spin", frame.spin)
        shader.setFloatUniform("flow", frame.flow)
        shader.setFloatUniform("level", frame.level)
        shader.setFloatUniform("activity", frame.active)
        shader.setFloatUniform("thinking", frame.thinking)
        shader.setFloatUniform("speaking", frame.speaking)
        shader.setFloatUniform("tint", tint.red, tint.green, tint.blue)
        shader.setFloatUniform("clearBackground", if (clear) 1f else 0f)
    }

    // 1:1 AGSL port of ios/APEX/Views/LiveOrb/LiveOrb.metal.
    // All smoothstep edges satisfy edge0 < edge1 as required by Skia AGSL.
    private const val SOURCE = """
uniform float2 size;
uniform float time;
uniform float spin;
uniform float flow;
uniform float level;
uniform float activity;
uniform float thinking;
uniform float speaking;
uniform float3 tint;
uniform float clearBackground;

const float PI = 3.14159265;

float3 rotateX(float3 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return float3(p.x, c * p.y - s * p.z, s * p.y + c * p.z);
}

float3 rotateY(float3 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return float3(c * p.x + s * p.z, p.y, -s * p.x + c * p.z);
}

float gimbalRing(float2 p, float radius, float3 n, float width, float dashes, float dashShift) {
    float rr = radius * radius - dot(p, p);
    if (rr <= 0.0) return 0.0;
    float h = sqrt(rr);
    float3 u = normalize(abs(n.y) < 0.95 ? cross(n, float3(0.0, 1.0, 0.0)) : cross(n, float3(1.0, 0.0, 0.0)));
    float3 v = cross(n, u);
    float total = 0.0;
    for (int side = 0; side < 2; side++) {
        float z = side == 0 ? h : -h;
        float3 s = float3(p, z);
        float2 slope = n.xy - n.z * p / z;
        float d = abs(dot(s, n)) / max(length(slope), 0.05);
        float line = exp(-(d * d) / (width * width)) + 0.18 * exp(-d / (width * 4.5));
        if (dashes > 0.0) {
            float angle = atan(dot(s, v), dot(s, u)) / (2.0 * PI) + 0.5;
            float dash = fract(angle * dashes + dashShift);
            line *= smoothstep(0.0, 0.05, dash) * (1.0 - smoothstep(0.64, 0.72, dash));
        }
        total += line * (side == 0 ? 1.0 : 0.28);
    }
    return total * smoothstep(0.0, 0.003, rr);
}

float hudArcRing(float2 p, float radius, float width, float dashes, float shift, float duty) {
    float d = abs(length(p) - radius);
    float line = exp(-(d * d) / (width * width)) + 0.16 * exp(-d / (width * 4.0));
    if (dashes > 0.0) {
        float angle = atan(p.y, p.x) / (2.0 * PI) + 0.5;
        float dash = fract(angle * dashes + shift);
        float gate = smoothstep(0.0, 0.035, dash) * (1.0 - smoothstep(duty - 0.04, duty, dash));
        line *= gate;
    }
    return line;
}

float tickRing(float2 p, float innerR, float outerR, float count, float shift, float thickness) {
    float r = length(p);
    if (r < innerR - 0.03 || r > outerR + 0.03) return 0.0;
    float radialMask = smoothstep(innerR - 0.008, innerR + 0.004, r)
                     * (1.0 - smoothstep(outerR - 0.004, outerR + 0.008, r));
    float angle = atan(p.y, p.x) / (2.0 * PI) + 0.5;
    float cell = fract(angle * count + shift) - 0.5;
    float arcDist = abs(cell) * (2.0 * PI * r / count);
    float spoke = exp(-(arcDist * arcDist) / (thickness * thickness));
    return spoke * radialMask;
}

float spectrumRing(float2 p, float baseR, float lvl, float flw, float tm, float act) {
    float r = length(p);
    float normAngle = atan(p.y, p.x) / (2.0 * PI) + 0.5;
    float spokes = 48.0;
    float idx = floor(normAngle * spokes);
    float wave = 0.45 * sin(idx * 0.65 + flw * 2.4)
               + 0.35 * sin(idx * 1.35 - tm * 3.8)
               + 0.20 * cos(idx * 2.70 + flw * 4.1);
    float amp = (0.025 + 0.020 * act) + (0.16 * lvl + 0.04 * act) * (0.5 + 0.5 * wave);
    float outerR = baseR + amp;
    if (r < baseR - 0.02 || r > outerR + 0.03) return 0.0;
    float radialMask = smoothstep(baseR - 0.006, baseR + 0.004, r)
                     * (1.0 - smoothstep(outerR - 0.006, outerR + 0.008, r));
    float cell = fract(normAngle * spokes) - 0.5;
    float arcDist = abs(cell) * (2.0 * PI * r / spokes);
    float bar = exp(-(arcDist * arcDist) / (0.011 * 0.011));
    return bar * radialMask;
}

half4 main(float2 fragCoord) {
    float scale = min(size.x, size.y) * 0.5;
    float2 raw = (fragCoord - size * 0.5) / scale;

    float energy = clamp(0.24 * activity + 0.95 * level + 0.34 * speaking + 0.28 * thinking, 0.0, 1.35);
    float breathe = 1.0 + 0.012 * sin(time * 2.0) * (1.0 - activity * 0.4);
    float radius = 0.56 * breathe * (1.0 + 0.05 * level);
    float2 p = raw / radius;
    float r = length(p);

    float crimsonGlow = 0.0;
    float whiteHud = 0.0;

    if (r < 1.65) {
        float outerBrackets = hudArcRing(p, 1.38, 0.008, 3.0, spin * 0.08, 0.52);
        float fineTicks = tickRing(p, 1.25, 1.32, 36.0, -spin * 0.05, 0.0055);
        float cardinalTicks = tickRing(p, 1.21, 1.35, 4.0, 0.125, 0.009);
        crimsonGlow += outerBrackets * (0.45 + 0.40 * energy);
        whiteHud += fineTicks * (0.22 + 0.25 * activity) + cardinalTicks * (0.42 + 0.35 * energy);

        float spec = spectrumRing(p, 1.00, clamp(level + 0.35 * speaking, 0.0, 1.0), flow, time, activity);
        crimsonGlow += spec * (0.65 + 0.75 * energy);
        whiteHud += spec * clamp(level * 0.65, 0.0, 0.6);

        float perimeter = hudArcRing(p, 0.96, 0.009, 0.0, 0.0, 1.0);
        crimsonGlow += perimeter * (0.42 + 0.35 * energy);

        float3 g1 = rotateY(rotateX(float3(0.0, 1.0, 0.0), 1.12), spin * 0.95);
        float3 g2 = rotateY(rotateX(float3(0.0, 0.0, 1.0), 0.82 + 0.20 * sin(spin * 0.5)), -spin * 0.68);
        float3 g3 = rotateY(rotateX(float3(0.0, 1.0, 0.0), 0.38), -spin * 0.80 + 1.2);
        float gimbals = gimbalRing(p, 0.82, g1, 0.010, 6.0, spin * 0.22)
                      + gimbalRing(p, 0.82, g2, 0.009, 12.0, -spin * 0.18)
                      + gimbalRing(p, 0.66, g3, 0.008, 0.0, 0.0) * 0.85;
        crimsonGlow += gimbals * (0.42 + 0.45 * energy);
        whiteHud += gimbalRing(p, 0.82, g1, 0.005, 6.0, spin * 0.22) * 0.18 * energy;
    }

    float turbineOuter = hudArcRing(p, 0.46 * (1.0 + 0.12 * level), 0.013, 8.0, spin * 0.42, 0.66);
    float turbineInner = hudArcRing(p, 0.29 * (1.0 + 0.18 * level), 0.011, 12.0, -spin * 0.60, 0.58);
    float innerTicks = tickRing(p, 0.34, 0.40, 24.0, spin * 0.20, 0.0045);
    crimsonGlow += (turbineOuter + turbineInner * 0.9) * (0.58 + 0.62 * energy);
    whiteHud += turbineInner * (0.25 + 0.35 * energy) + innerTicks * (0.28 + 0.25 * activity);

    if (r < 0.96 && (thinking > 0.02 || activity > 0.05)) {
        float sweepAngle = atan(p.y, p.x) - spin * 2.4;
        float trail = fract(sweepAngle / (2.0 * PI));
        float wedge = pow(trail, 5.0) * (1.0 - smoothstep(0.90, 0.96, r)) * smoothstep(0.14, 0.28, r);
        crimsonGlow += wedge * (0.14 * activity + 0.55 * thinking);
    }

    float pulse = 1.0 + 0.30 * thinking * sin(time * 8.0) + 0.15 * speaking * sin(time * 12.0);
    float corePlasma = exp(-r * r / (0.018 + 0.028 * energy)) * (0.75 + 0.80 * energy) * pulse;
    float coreHot = exp(-r * r / (0.0045 + 0.008 * level)) * (0.85 + 0.65 * energy) * pulse;
    float halo = exp(-abs(r - 0.96) * 10.0) * (0.20 + 0.32 * energy)
               + exp(-max(r - 0.96, 0.0) * 2.6) * 0.09 * (0.5 + energy);

    crimsonGlow += corePlasma + halo;
    whiteHud += coreHot;

    float dim = 0.68 + 0.32 * activity;
    crimsonGlow *= dim;
    whiteHud *= dim;

    float3 base = tint;
    float3 crispWhite = float3(0.98, 0.99, 1.0);
    float3 light = base * crimsonGlow + crispWhite * (whiteHud * 0.88 + crimsonGlow * crimsonGlow * 0.26);
    light = 1.0 - exp(-light * 1.28);

    if (clearBackground > 0.5) {
        float alpha = clamp(max(light.r, max(light.g, light.b)), 0.0, 1.0);
        return half4(half3(light), half(alpha));
    }
    float3 background = float3(0.012, 0.012, 0.018) + base * 0.045 * exp(-dot(raw, raw) * 1.5);
    return half4(half3(background + light), 1.0);
}
"""
}

// MARK: - Canvas fallback (Android 8 to 12)

private val ORB_BACKGROUND = Color(0xFF030306)
private val CRISP_WHITE = Color(0.98f, 0.99f, 1.0f)

private fun rotateX(p: FloatArray, angle: Float): FloatArray {
    val c = cos(angle)
    val s = sin(angle)
    return floatArrayOf(p[0], c * p[1] - s * p[2], s * p[1] + c * p[2])
}

private fun rotateY(p: FloatArray, angle: Float): FloatArray {
    val c = cos(angle)
    val s = sin(angle)
    return floatArrayOf(c * p[0] + s * p[2], p[1], -s * p[0] + c * p[2])
}

private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(
    a[1] * b[2] - a[2] * b[1],
    a[2] * b[0] - a[0] * b[2],
    a[0] * b[1] - a[1] * b[0]
)

private fun normalize(a: FloatArray): FloatArray {
    val length = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]).takeIf { it > 1e-6f } ?: 1f
    return floatArrayOf(a[0] / length, a[1] / length, a[2] / length)
}

private fun DrawScope.drawFallbackOrb(frame: LiveOrbFrame, tint: Color, clearBackground: Boolean) {
    val scale = size.minDimension / 2f
    val energy = (0.24f * frame.active + 0.95f * frame.level + 0.34f * frame.speaking + 0.28f * frame.thinking).coerceIn(0f, 1.35f)
    val breathe = 1f + 0.012f * sin(frame.time * 2f) * (1f - frame.active * 0.4f)
    val radius = 0.56f * breathe * (1f + 0.05f * frame.level) * scale
    val dim = 0.68f + 0.32f * frame.active
    val hot = lerp(tint, CRISP_WHITE, 0.72f)
    val blend = BlendMode.Plus
    val stroke = max(1f, scale * 0.013f)

    if (!clearBackground) {
        drawRect(ORB_BACKGROUND)
        drawCircle(
            Brush.radialGradient(listOf(tint.copy(alpha = 0.12f), Color.Transparent), center, scale),
            radius = scale
        )
    }

    // Containment halo
    val haloAlpha = ((0.22f + 0.34f * energy) * dim).coerceIn(0f, 1f)
    drawCircle(
        Brush.radialGradient(
            0f to Color.Transparent,
            0.72f to tint.copy(alpha = haloAlpha * 0.28f),
            0.84f to tint.copy(alpha = haloAlpha),
            1f to Color.Transparent,
            center = center,
            radius = radius * 1.22f
        ),
        radius = radius * 1.22f,
        blendMode = blend
    )

    // Outer HUD framing brackets + degree compass ticks
    drawIris(radius * 1.38f, tint, ((0.50f + 0.35f * energy) * dim).coerceIn(0f, 1f), stroke, 3, frame.spin * 0.08f)
    drawTickRing(radius * 1.24f, radius * 1.32f, 36, -frame.spin * 0.05f, CRISP_WHITE.copy(alpha = (0.28f * dim).coerceIn(0f, 1f)), stroke * 0.65f)
    drawTickRing(radius * 1.20f, radius * 1.36f, 4, 0f, CRISP_WHITE.copy(alpha = (0.55f * dim).coerceIn(0f, 1f)), stroke * 1.1f)

    // 48-spoke polar audio spectrum ring
    val specLevel = (frame.level + 0.35f * frame.speaking).coerceIn(0f, 1f)
    for (i in 0 until 48) {
        val angle = (i / 48f) * (2f * Math.PI.toFloat())
        val wave = 0.45f * sin(i * 0.65f + frame.flow * 2.4f) +
            0.35f * sin(i * 1.35f - frame.time * 3.8f) +
            0.20f * cos(i * 2.70f + frame.flow * 4.1f)
        val amp = (0.03f + 0.02f * frame.active) + (0.16f * specLevel + 0.04f * frame.active) * (0.5f + 0.5f * wave)
        val r0 = radius * 1.00f
        val r1 = radius * (1.00f + amp)
        val dx = cos(angle)
        val dy = sin(angle)
        drawLine(
            tint.copy(alpha = ((0.55f + 0.45f * energy) * dim).coerceIn(0f, 1f)),
            Offset(center.x + dx * r0, center.y + dy * r0),
            Offset(center.x + dx * r1, center.y + dy * r1),
            strokeWidth = stroke * 0.85f,
            cap = StrokeCap.Round,
            blendMode = blend
        )
    }

    // 3D counter-rotating gyroscopic gimbal rings
    val spin = frame.spin
    val ringAlpha = ((0.44f + 0.42f * energy) * dim).coerceIn(0f, 1f)
    drawRing(rotateY(rotateX(floatArrayOf(0f, 1f, 0f), 1.12f), spin * 0.95f), radius * 0.82f, tint, ringAlpha, stroke, 6f, spin * 0.22f)
    drawRing(rotateY(rotateX(floatArrayOf(0f, 0f, 1f), 0.82f + 0.20f * sin(spin * 0.5f)), -spin * 0.68f), radius * 0.82f, tint, ringAlpha, stroke * 0.9f, 12f, -spin * 0.18f)
    drawRing(rotateY(rotateX(floatArrayOf(0f, 1f, 0f), 0.38f), -spin * 0.80f + 1.2f), radius * 0.66f, tint, ringAlpha * 0.85f, stroke * 0.8f, 0f, 0f)

    // Dual turbine aperture irises
    val lensAlpha = ((0.58f + 0.42f * energy) * dim).coerceIn(0f, 1f)
    drawIris(radius * 0.46f * (1f + 0.12f * frame.level), tint, lensAlpha, stroke * 1.1f, 8, spin * 0.42f)
    drawIris(radius * 0.29f * (1f + 0.18f * frame.level), hot, lensAlpha * 0.85f, stroke * 0.9f, 12, -spin * 0.60f)

    // Incandescent crimson-white reactor core
    val pulse = 1f + 0.30f * frame.thinking * sin(frame.time * 8f)
    val coreAlpha = ((0.75f + 0.70f * energy) * pulse * dim).coerceIn(0f, 1f)
    val coreRadius = radius * sqrt(0.018f + 0.028f * energy) * 2.5f
    drawCircle(
        Brush.radialGradient(
            0f to CRISP_WHITE.copy(alpha = coreAlpha),
            0.35f to hot.copy(alpha = coreAlpha * 0.85f),
            0.72f to tint.copy(alpha = coreAlpha * 0.42f),
            1f to Color.Transparent,
            center = center,
            radius = coreRadius
        ),
        radius = coreRadius,
        blendMode = blend
    )
}

private fun DrawScope.drawTickRing(
    innerRadius: Float,
    outerRadius: Float,
    count: Int,
    shift: Float,
    color: Color,
    strokeWidth: Float
) {
    for (i in 0 until count) {
        val angle = ((i / count.toFloat()) + shift) * (2f * Math.PI.toFloat())
        val dx = cos(angle)
        val dy = sin(angle)
        drawLine(
            color = color,
            start = Offset(center.x + dx * innerRadius, center.y + dy * innerRadius),
            end = Offset(center.x + dx * outerRadius, center.y + dy * outerRadius),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
            blendMode = BlendMode.Plus
        )
    }
}

private fun DrawScope.drawRing(
    normal: FloatArray,
    radius: Float,
    color: Color,
    alpha: Float,
    width: Float,
    dashes: Float,
    dashShift: Float
) {
    val n = normalize(normal)
    val u = normalize(if (abs(n[1]) < 0.95f) cross(n, floatArrayOf(0f, 1f, 0f)) else cross(n, floatArrayOf(1f, 0f, 0f)))
    val v = cross(n, u)
    val steps = 72
    var previous: FloatArray? = null
    for (step in 0..steps) {
        val t = step / steps.toFloat() * (2f * Math.PI.toFloat())
        val point = floatArrayOf(
            cos(t) * u[0] + sin(t) * v[0],
            cos(t) * u[1] + sin(t) * v[1],
            cos(t) * u[2] + sin(t) * v[2]
        )
        val last = previous
        previous = point
        if (last == null) continue
        if (dashes > 0f) {
            val dash = ((step / steps.toFloat()) * dashes + dashShift).let { it - floor(it) }
            if (dash > 0.68f) continue
        }
        val front = (last[2] + point[2]) >= 0f
        drawLine(
            color.copy(alpha = (alpha * if (front) 1f else 0.28f).coerceIn(0f, 1f)),
            Offset(center.x + last[0] * radius, center.y + last[1] * radius),
            Offset(center.x + point[0] * radius, center.y + point[1] * radius),
            strokeWidth = width,
            cap = StrokeCap.Round,
            blendMode = BlendMode.Plus
        )
    }
}

private fun DrawScope.drawIris(radius: Float, color: Color, alpha: Float, width: Float, dashes: Int, phase: Float) {
    val circumference = (2f * Math.PI.toFloat() * radius).takeIf { it > 1f } ?: return
    val segment = circumference / dashes
    drawCircle(
        color.copy(alpha = alpha.coerceIn(0f, 1f)),
        radius = radius,
        style = Stroke(
            width = width,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(segment * 0.62f, segment * 0.38f), phase * segment * dashes)
        ),
        blendMode = BlendMode.Plus
    )
}
