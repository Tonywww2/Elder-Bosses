#version 150

uniform sampler2D SceneColor;
uniform vec2 ScreenSize;
uniform float EffectTime;
uniform float Intensity;
uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 effectUv;
out vec4 fragColor;

void main() {
    vec2 centered = effectUv * 2.0 - 1.0;
    float radius = length(centered);
    if (radius >= 1.0) discard;
    vec2 direction = centered / max(radius, 0.015);
    vec2 tangent = vec2(-direction.y, direction.x);
    float envelope = 1.0 - smoothstep(0.14, 1.0, radius);
    float angle = atan(centered.y, centered.x);
    float turbulence = sin(angle * 7.0 + radius * 17.0 - EffectTime * 7.1)
        + 0.45 * sin(angle * 11.0 - radius * 23.0 + EffectTime * 10.3);
    float pulse = 0.92 + 0.16 * sin(EffectTime * 4.7 + radius * 12.0) + 0.09 * turbulence;
    vec2 screenUv = gl_FragCoord.xy / ScreenSize;
    vec2 shift = (direction * (12.0 + 19.0 * envelope + turbulence * 4.0)
        + tangent * (sin(radius * 13.0 - EffectTime * 4.5) * 10.0 + turbulence * 6.0))
        * envelope * pulse * Intensity / ScreenSize;
    vec2 halfPixel = 0.5 / ScreenSize;
    vec3 scene = texture(SceneColor, clamp(screenUv + shift, halfPixel, 1.0 - halfPixel)).rgb;
    float center = (1.0 - smoothstep(0.06, 0.27, radius)) * Intensity;
    float ring = exp(-pow((radius - 0.30 - turbulence * 0.025 * envelope) * 28.0, 2.0)) * Intensity;
    scene *= 1.0 - center * 0.86;
    scene += vec3(0.30, 0.08, 0.49) * ring * 0.7;
    float alpha = (1.0 - smoothstep(0.73, 1.0, radius)) * vertexColor.a * ColorModulator.a;
    fragColor = vec4(scene * ColorModulator.rgb, alpha);
}