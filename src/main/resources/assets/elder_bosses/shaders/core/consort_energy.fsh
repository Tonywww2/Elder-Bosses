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
        core = exp(-centered.x * centered.x * 180.0);
        float glow = exp(-centered.x * centered.x * 9.0) * 0.32 + exp(-centered.x * centered.x * 2.8) * 0.16;
        opacity = (core * 0.95 + glow) * smoothstep(0.0, 0.025, effectUv.y)
            * (1.0 - smoothstep(0.65, 1.0, effectUv.y)) * (1.0 - Progress * 0.38);
    } else if (EffectMode == 3) {
        float fracture = centered.x + sin(effectUv.y * 26.0) * 0.12 + sin(effectUv.y * 53.0) * 0.04;
        core = band(fracture, 0.035);
        opacity = (band(fracture, 0.28) * 0.40 + core * 0.5)
            * smoothstep(0.0, 0.08, effectUv.y) * (1.0 - smoothstep(0.88, 1.0, effectUv.y));
    } else if (EffectMode == 4) {
        float turbulence = sin(angle * 7.0 - EffectTime * 8.3 + radius * 11.0) * 0.65
            + sin(angle * 13.0 + EffectTime * 5.7 - radius * 19.0) * 0.28;
        float spiral = sin(angle * 4.0 + radius * 23.0 + EffectTime * 5.0 + turbulence);
        core = band(spiral, 0.09);
        float rupture = band(sin(angle * 11.0 + turbulence * 1.5 + radius * 6.0), 0.16);
        opacity = (band(spiral, 0.48) * 0.38 + core * 0.5 + rupture * 0.22)
            * (0.80 + 0.20 * sin(EffectTime * 11.0 + angle * 3.0)) * smoothstep(0.10, 0.25, radius)
            * (1.0 - smoothstep(0.85, 1.0, radius));
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
        core = exp(-radius * radius * 28.0);
        opacity = (core * 0.65 + exp(-radius * radius * 3.8) * 0.30)
            * (1.0 - smoothstep(0.82, 1.0, radius)) * (1.0 - Progress * 0.55);
    } else if (EffectMode == 11) {
        vec2 cell = floor((effectUv + vec2(0.0, -EffectTime * 0.18)) * vec2(9.0, 13.0));
        vec2 local = fract((effectUv + vec2(0.0, -EffectTime * 0.18)) * vec2(9.0, 13.0)) - 0.5;
        float seed = fract(sin(dot(cell, vec2(127.1, 311.7))) * 43758.5453);
        core = exp(-dot(local, local) * 80.0) * step(0.43, seed);
        opacity = core * (1.0 - smoothstep(0.1, 1.0, effectUv.y))
            * (1.0 - smoothstep(0.7, 1.0, abs(centered.x))) * (1.0 - Progress * 0.5);
    } else if (EffectMode == 12) {
        float cracks = sin(centered.x * 14.0 + sin(effectUv.y * 28.0) * 1.4);
        float front = clamp(Progress * 2.5, 0.0, 1.0);
        core = band(cracks, 0.09) * (1.0 - smoothstep(front, front + 0.1, effectUv.y));
        opacity = core * 0.7 + band(effectUv.y - front, 0.08) * 0.75;
        opacity *= (1.0 - smoothstep(0.90, 1.0, abs(centered.x))) * (1.0 - Progress);
    } else if (EffectMode == 13) {
        core = exp(-effectUv.y * effectUv.y * 38.0);
        opacity = (core * 0.65 + 0.12) * (1.0 - smoothstep(0.06, 1.0, effectUv.y)) * (1.0 - Progress * 0.5);
    } else if (EffectMode == 14) {
        float sweep = fract(effectUv.x * 0.65 + effectUv.y * 0.35 - Progress);
        float strands = sin(effectUv.x * 27.0 + effectUv.y * 19.0 - EffectTime * 12.0);
        core = band(sweep - 0.5, 0.16);
        opacity = 0.32 + core * 0.55 + band(strands, 0.13) * 0.13;
    } else if (EffectMode == 15) {
        float branches = sin(angle * 13.0 + sin(radius * 31.0) * 0.5 + sin(angle * 5.0) * 1.1);
        float front = mix(0.16, 0.99, smoothstep(0.0, 0.28, Progress));
        float revealed = 1.0 - smoothstep(front - 0.035, front + 0.02, radius);
        core = band(branches, 0.07) * revealed;
        opacity = (core * 0.95 + band(branches, 0.22) * 0.32) * revealed
            * smoothstep(0.04, 0.16, radius) * (1.0 - smoothstep(0.9, 1.0, radius))
            * (1.0 - smoothstep(0.55, 1.0, Progress));
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