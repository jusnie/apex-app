#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// APEX Tactical Jarvis HUD + Arc-Reactor Core:
// Precision concentric degree compass ticks, real-time 48-spoke polar voice
// spectrum ring, 3D counter-rotating gyroscopic gimbal rings, dual turbine
// aperture irises, thinking radar sweep, and an incandescent crimson-white core.

namespace liveorb {

float3 rotateX(float3 p, float a) {
    float c = cos(a), s = sin(a);
    return float3(p.x, c * p.y - s * p.z, s * p.y + c * p.z);
}

float3 rotateY(float3 p, float a) {
    float c = cos(a), s = sin(a);
    return float3(c * p.x + s * p.z, p.y, -s * p.x + c * p.z);
}

/// 3D gyroscopic gimbal ring with optional tactical dash segmentation.
float gimbalRing(float2 p, float radius, float3 n, float width, float dashes, float dashShift) {
    float rr = radius * radius - dot(p, p);
    if (rr <= 0.0) return 0.0;
    float h = sqrt(rr);
    float3 u = normalize(abs(n.y) < 0.95 ? cross(n, float3(0, 1, 0)) : cross(n, float3(1, 0, 0)));
    float3 v = cross(n, u);
    float total = 0.0;
    for (int side = 0; side < 2; side++) {
        float z = side == 0 ? h : -h;
        float3 s = float3(p, z);
        float2 slope = n.xy - n.z * p / z;
        float d = abs(dot(s, n)) / max(length(slope), 0.05);
        float line = exp(-(d * d) / (width * width)) + 0.18 * exp(-d / (width * 4.5));
        if (dashes > 0.0) {
            float angle = atan2(dot(s, v), dot(s, u)) / (2.0 * M_PI_F) + 0.5;
            float dash = fract(angle * dashes + dashShift);
            line *= smoothstep(0.0, 0.05, dash) * (1.0 - smoothstep(0.64, 0.72, dash));
        }
        total += line * (side == 0 ? 1.0 : 0.28);
    }
    return total * smoothstep(0.0, 0.003, rr);
}

/// 2D concentric segmented HUD ring / turbine aperture iris.
float hudArcRing(float2 p, float radius, float width, float dashes, float shift, float duty) {
    float d = abs(length(p) - radius);
    float line = exp(-(d * d) / (width * width)) + 0.16 * exp(-d / (width * 4.0));
    if (dashes > 0.0) {
        float angle = atan2(p.y, p.x) / (2.0 * M_PI_F) + 0.5;
        float dash = fract(angle * dashes + shift);
        float gate = smoothstep(0.0, 0.035, dash) * (1.0 - smoothstep(duty - 0.04, duty, dash));
        line *= gate;
    }
    return line;
}

/// Precision radial tick ring (compass / degree reticle).
float tickRing(float2 p, float innerR, float outerR, float count, float shift, float thickness) {
    float r = length(p);
    if (r < innerR - 0.03 || r > outerR + 0.03) return 0.0;
    float radialMask = smoothstep(innerR - 0.008, innerR + 0.004, r)
                     * (1.0 - smoothstep(outerR - 0.004, outerR + 0.008, r));
    float angle = atan2(p.y, p.x) / (2.0 * M_PI_F) + 0.5;
    float cell = fract(angle * count + shift) - 0.5;
    float arcDist = abs(cell) * (2.0 * M_PI_F * r / count);
    float spoke = exp(-(arcDist * arcDist) / (thickness * thickness));
    return spoke * radialMask;
}

/// 48-spoke polar audio spectrum / equalizer ring driven by voice level & flow.
float spectrumRing(float2 p, float baseR, float level, float flow, float time, float active) {
    float r = length(p);
    float angle = atan2(p.y, p.x);
    float normAngle = angle / (2.0 * M_PI_F) + 0.5;
    float spokes = 48.0;
    float idx = floor(normAngle * spokes);
    float wave = 0.45 * sin(idx * 0.65 + flow * 2.4)
               + 0.35 * sin(idx * 1.35 - time * 3.8)
               + 0.20 * cos(idx * 2.70 + flow * 4.1);
    float amp = (0.025 + 0.020 * active) + (0.16 * level + 0.04 * active) * (0.5 + 0.5 * wave);
    float outerR = baseR + amp;
    if (r < baseR - 0.02 || r > outerR + 0.03) return 0.0;
    float radialMask = smoothstep(baseR - 0.006, baseR + 0.004, r)
                     * (1.0 - smoothstep(outerR - 0.006, outerR + 0.008, r));
    float cell = fract(normAngle * spokes) - 0.5;
    float arcDist = abs(cell) * (2.0 * M_PI_F * r / spokes);
    float bar = exp(-(arcDist * arcDist) / (0.011 * 0.011));
    return bar * radialMask;
}

} // namespace liveorb

using namespace liveorb;

[[ stitchable ]] half4 liveOrb(float2 position, half4 color, float2 size, float time,
                               float spin, float flow, float level, float active,
                               float thinking, float speaking, half4 tint, float clearBackground) {
    float scale = min(size.x, size.y) * 0.5;
    float2 raw = (position - size * 0.5) / scale;

    float energy = clamp(0.24 * active + 0.95 * level + 0.34 * speaking + 0.28 * thinking, 0.0, 1.35);
    float breathe = 1.0 + 0.012 * sin(time * 2.0) * (1.0 - active * 0.4);
    // Keep outer HUD within 0.56 * 1.48 ≈ 0.83 of canvas radius so VoiceAssistantHomeButton's
    // scaleEffect(1 / 0.56) and expanded radial masks frame the reactor cleanly.
    float radius = 0.56 * breathe * (1.0 + 0.05 * level);
    float2 p = raw / radius;
    float r = length(p);

    float crimsonGlow = 0.0;
    float whiteHud = 0.0;

    if (r < 1.65) {
        // 1. Outer tactical HUD framing arcs + 36-tick degree compass + 4 cardinal reticles
        float outerBrackets = hudArcRing(p, 1.38, 0.008, 3.0, spin * 0.08, 0.52);
        float fineTicks = tickRing(p, 1.25, 1.32, 36.0, -spin * 0.05, 0.0055);
        float cardinalTicks = tickRing(p, 1.21, 1.35, 4.0, 0.125, 0.009);
        crimsonGlow += outerBrackets * (0.45 + 0.40 * energy);
        whiteHud += fineTicks * (0.22 + 0.25 * active) + cardinalTicks * (0.42 + 0.35 * energy);

        // 2. Real-time 48-spoke polar voice spectrum equalizer ring
        float spec = spectrumRing(p, 1.00, clamp(level + 0.35 * speaking, 0.0, 1.0), flow, time, active);
        crimsonGlow += spec * (0.65 + 0.75 * energy);
        whiteHud += spec * clamp(level * 0.65, 0.0, 0.6);

        // 3. Containment perimeter ring
        float perimeter = hudArcRing(p, 0.96, 0.009, 0.0, 0.0, 1.0);
        crimsonGlow += perimeter * (0.42 + 0.35 * energy);

        // 4. 3D counter-rotating gyroscopic gimbal rings inside reactor chamber
        float3 g1 = rotateY(rotateX(float3(0, 1, 0), 1.12), spin * 0.95);
        float3 g2 = rotateY(rotateX(float3(0, 0, 1), 0.82 + 0.20 * sin(spin * 0.5)), -spin * 0.68);
        float3 g3 = rotateY(rotateX(float3(0, 1, 0), 0.38), -spin * 0.80 + 1.2);
        float gimbals = gimbalRing(p, 0.82, g1, 0.010, 6.0, spin * 0.22)
                      + gimbalRing(p, 0.82, g2, 0.009, 12.0, -spin * 0.18)
                      + gimbalRing(p, 0.66, g3, 0.008, 0.0, 0.0) * 0.85;
        crimsonGlow += gimbals * (0.42 + 0.45 * energy);
        whiteHud += gimbalRing(p, 0.82, g1, 0.005, 6.0, spin * 0.22) * 0.18 * energy;
    }

    // 5. Dual counter-rotating reactor turbine irises + inner tick dial
    float turbineOuter = hudArcRing(p, 0.46 * (1.0 + 0.12 * level), 0.013, 8.0, spin * 0.42, 0.66);
    float turbineInner = hudArcRing(p, 0.29 * (1.0 + 0.18 * level), 0.011, 12.0, -spin * 0.60, 0.58);
    float innerTicks = tickRing(p, 0.34, 0.40, 24.0, spin * 0.20, 0.0045);
    crimsonGlow += (turbineOuter + turbineInner * 0.9) * (0.58 + 0.62 * energy);
    whiteHud += turbineInner * (0.25 + 0.35 * energy) + innerTicks * (0.28 + 0.25 * active);

    // 6. Tactical radar sweep during thinking / scanning
    if (r < 0.96 && (thinking > 0.02 || active > 0.05)) {
        float sweepAngle = atan2(p.y, p.x) - spin * 2.4;
        float trail = fract(sweepAngle / (2.0 * M_PI_F));
        float wedge = pow(trail, 5.0) * (1.0 - smoothstep(0.90, 0.96, r)) * smoothstep(0.14, 0.28, r);
        crimsonGlow += wedge * (0.14 * active + 0.55 * thinking);
    }

    // 7. Incandescent Arc-Reactor Core + Containment Halo
    float pulse = 1.0 + 0.30 * thinking * sin(time * 8.0) + 0.15 * speaking * sin(time * 12.0);
    float corePlasma = exp(-r * r / (0.018 + 0.028 * energy)) * (0.75 + 0.80 * energy) * pulse;
    float coreHot = exp(-r * r / (0.0045 + 0.008 * level)) * (0.85 + 0.65 * energy) * pulse;
    float halo = exp(-abs(r - 0.96) * 10.0) * (0.20 + 0.32 * energy)
               + exp(-max(r - 0.96, 0.0) * 2.6) * 0.09 * (0.5 + energy);

    crimsonGlow += corePlasma + halo;
    whiteHud += coreHot;

    float dim = 0.68 + 0.32 * active;
    crimsonGlow *= dim;
    whiteHud *= dim;

    float3 base = float3(tint.rgb);
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
