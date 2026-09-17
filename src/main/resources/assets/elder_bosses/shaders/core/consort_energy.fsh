#version 150

uniform vec4 ColorModulator;
uniform float EffectTime;
uniform int EffectMode;
uniform float Progress;
uniform float InnerRatio;

in vec4 vertexColor;
in vec2 effectUv;
out vec4 fragColor;

float band(float distance, float width) {
    return 1.0 - smoothstep(width * 0.3, width, abs(distance));
}

void main() {
    vec2 centered = effectUv * 2.0 - 1.0;
    float radius = length(centered);
    float angle = atan(centered.y, centered.x);
    float opacity = 0.0;
    float core = 0.0;
    if (EffectMode == 0) {
        float lengthFade = smoothstep(0.0, 0.12, effectUv.y) * (1.0 - smoothstep(0.75, 1.0, effectUv.y));
        core = band(centered.x, 0.10);
        opacity = (band(centered.x, 0.95) * 0.55 + core * 0.4) * lengthFade;
    } else if (EffectMode == 1) {
        float ring = max(InnerRatio + 0.035, 0.90);
        core = band(radius - ring, 0.018);
        opacity = (band(radius - ring, 0.085) * 0.65 + core * 0.3)
            * (0.72 + 0.28 * sin(angle * 8.0 - EffectTime * 2.8));
        opacity += band(radius - InnerRatio, 0.025) * 0.22 * step(0.08, InnerRatio);
    } else if (EffectMode == 2) {
        float filament = sin(effectUv.y * 22.0 - EffectTime * 5.0 + centered.x * 5.0) * 0.035;
        core = band(centered.x + filament, 0.035) * (1.0 - smoothstep(0.0, 0.2, Progress));
        opacity = (band(centered.x, 0.92) * 0.18 + band(centered.x + filament, 0.22) * 0.35 + core * 0.35)
            * smoothstep(0.0, 0.055, effectUv.y) * (1.0 - smoothstep(0.55, 1.0, effectUv.y));
    } else if (EffectMode == 3) {
        float fracture = centered.x + sin(effectUv.y * 26.0) * 0.12 + sin(effectUv.y * 53.0) * 0.04;
        core = band(fracture, 0.035);
        opacity = (band(fracture, 0.28) * 0.40 + core * 0.5)
            * smoothstep(0.0, 0.08, effectUv.y) * (1.0 - smoothstep(0.88, 1.0, effectUv.y));
    } else if (EffectMode == 4) {
        float spiral = sin(angle * 4.0 + radius * 23.0 + EffectTime * 5.0);
        core = band(spiral, 0.09);
        opacity = (band(spiral, 0.4) * 0.24 + core * 0.18) * smoothstep(0.14, 0.32, radius)
            * (1.0 - smoothstep(0.80, 1.0, radius));
    } else if (EffectMode == 6) {
        float bladeLength = smoothstep(0.02, 0.20, effectUv.x);
        float wake = pow(clamp(effectUv.y, 0.0, 1.0), 0.55);
        core = band(effectUv.y - 0.90, 0.13) * bladeLength;
        opacity = bladeLength * (0.38 + wake * 0.42) + core * 0.2;
    } else if (EffectMode == 7) {
        float corona = exp(-radius * radius * 5.5) * (1.0 - smoothstep(0.72, 1.0, radius));
        float rays = pow(abs(cos(angle * 2.0)), 30.0) * (1.0 - smoothstep(0.18, 1.0, radius));
        float filaments = pow(abs(cos(angle * 6.0 + EffectTime * 0.22)), 28.0)
            * (1.0 - smoothstep(0.22, 0.82, radius)) * Progress;
        core = 1.0 - smoothstep(0.06, mix(0.22, 0.40, Progress), radius);
        opacity = corona * 0.70 + rays * 0.85 + filaments * 0.22 + core;
    } else if (EffectMode == 8) {
        float streak = centered.x + sin(effectUv.y * 17.0 - EffectTime * 14.0) * 0.025;
        core = band(streak, 0.05);
        opacity = (band(streak, 0.95) * 0.32 + band(streak, 0.32) * 0.4 + core * 0.7)
            * pow(clamp(1.0 - effectUv.y, 0.0, 1.0), 0.65);
    } else if (EffectMode == 9) {
        float tear = centered.y + sin(effectUv.x * 23.0 + EffectTime * 3.0) * 0.10;
        float ends = smoothstep(0.0, 0.06, effectUv.x) * (1.0 - smoothstep(0.94, 1.0, effectUv.x));
        core = band(tear, 0.09);
        opacity = (core * 0.75 + band(tear, 0.8) * 0.32) * ends;
    } else if (EffectMode == 10) {
        float spokes = pow(abs(cos(angle * 8.0 + EffectTime * 0.3)), 22.0);
        float rings = band(radius - 0.82, 0.045) + band(radius - 0.63, 0.022);
        float lattice = band(sin(angle * 6.0 + radius * 16.0), 0.14);
        core = rings * 0.65 + spokes * band(radius - 0.73, 0.20);
        opacity = (core + lattice * 0.22) * (1.0 - smoothstep(0.94, 1.0, radius))
            * smoothstep(max(0.0, InnerRatio - 0.05), max(0.04, InnerRatio), radius);
    } else if (EffectMode == 11) {
        float vertical = effectUv.y;
        float sway = sin(vertical * 9.0 - EffectTime * 8.0) * (0.08 + vertical * 0.17);
        float flicker = sin(vertical * 23.0 - EffectTime * 13.0 + centered.x * 6.0) * 0.07;
        float taper = mix(0.82, 0.025, pow(vertical, 0.75));
        core = band(centered.x + sway, taper * 0.28) * (1.0 - vertical);
        opacity = band(centered.x + sway + flicker, taper) * (0.72 + core * 0.5)
            * smoothstep(0.0, 0.05, vertical) * (1.0 - smoothstep(0.78, 1.0, vertical));
    } else if (EffectMode == 12) {
        float cracks = sin(centered.x * 14.0 + sin(effectUv.y * 28.0) * 1.4);
        float front = clamp(Progress * 2.5, 0.0, 1.0);
        core = band(cracks, 0.09) * (1.0 - smoothstep(front, front + 0.1, effectUv.y));
        opacity = core * 0.7 + band(effectUv.y - front, 0.08) * 0.75;
        opacity *= (1.0 - smoothstep(0.90, 1.0, abs(centered.x))) * (1.0 - Progress);
    } else if (EffectMode == 13) {
        float strands = pow(abs(sin(effectUv.x * 18.84956 + EffectTime * 2.0)), 12.0);
        core = band(effectUv.y - 0.16, 0.08) + strands * 0.35;
        opacity = (0.2 + core) * (1.0 - smoothstep(0.12, 1.0, effectUv.y));
    } else if (EffectMode == 14) {
        float sweep = fract(effectUv.x * 0.65 + effectUv.y * 0.35 - Progress);
        float strands = sin(effectUv.x * 27.0 + effectUv.y * 19.0 - EffectTime * 12.0);
        core = band(sweep - 0.5, 0.16);
        opacity = 0.32 + core * 0.55 + band(strands, 0.13) * 0.13;
    } else {
        float wave = mix(max(0.12, InnerRatio), 0.98, clamp(Progress, 0.0, 1.0));
        core = band(radius - wave, 0.022);
        opacity = band(radius - wave, 0.16) * 0.34 + core * 0.45;
    }
    vec3 tint = mix(vertexColor.rgb, EffectMode == 11 ? vec3(1.0, 0.48, 0.12) : vec3(1.0, 0.98, 0.90),
        clamp(core * (EffectMode >= 6 ? 0.88 : 0.42), 0.0, 1.0));
    float alpha = min(1.0, opacity * vertexColor.a * ColorModulator.a);
    if (alpha < 0.004) discard;
    fragColor = vec4(tint * ColorModulator.rgb, alpha);
}