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
        float corona = exp(-radius * radius * 4.5);
        float rays = pow(abs(cos(angle * 4.0 + EffectTime * 0.7)), 18.0)
            * (1.0 - smoothstep(0.18, 1.0, radius));
        core = 1.0 - smoothstep(0.06, 0.32, radius);
        opacity = corona * 0.40 + rays * 0.48 + core * 0.75;
    } else if (EffectMode == 8) {
        float streak = centered.x + sin(effectUv.y * 17.0 - EffectTime * 14.0) * 0.025;
        core = band(streak, 0.05);
        opacity = (band(streak, 0.95) * 0.32 + band(streak, 0.32) * 0.4 + core * 0.7)
            * pow(clamp(1.0 - effectUv.y, 0.0, 1.0), 0.65);
    } else {
        float wave = mix(max(0.12, InnerRatio), 0.98, clamp(Progress, 0.0, 1.0));
        core = band(radius - wave, 0.022);
        opacity = band(radius - wave, 0.16) * 0.34 + core * 0.45;
    }
    vec3 tint = mix(vertexColor.rgb, vec3(1.0, 0.98, 0.90), core * (EffectMode >= 6 ? 0.88 : 0.42));
    float alpha = opacity * vertexColor.a * ColorModulator.a;
    if (alpha < 0.004) discard;
    fragColor = vec4(tint * ColorModulator.rgb, alpha);
}