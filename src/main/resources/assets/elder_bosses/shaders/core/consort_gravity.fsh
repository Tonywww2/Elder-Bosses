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
    vec2 flow = vec2(sin(centered.y * 5.3 - EffectTime * 2.1) + 0.5 * sin(centered.x * 11.7 + EffectTime),
        cos(centered.x * 6.7 + EffectTime * 1.7) + 0.5 * sin(centered.y * 13.1 - EffectTime * 1.3));
    vec2 warped = centered + flow * 0.28;
    float turbulence = sin(warped.x * 15.0 + warped.y * 8.3 - EffectTime * 4.1)
        + 0.55 * sin(warped.y * 23.0 - warped.x * 11.0 + EffectTime * 3.3);
    float pulse = 0.94 + 0.14 * sin(EffectTime * 4.7 + radius * 12.0);
    vec2 screenUv = gl_FragCoord.xy / ScreenSize;
    vec2 shift = (direction * (38.0 + 45.0 * envelope + turbulence * 12.0)
        + tangent * (sin(radius * 13.0 - EffectTime * 4.5) * 27.0 + turbulence * 24.0) + flow * 19.0)
        * envelope * pulse * Intensity / ScreenSize;
    float maxShift = min(ScreenSize.x, ScreenSize.y) * 0.12;
    shift *= min(1.0, maxShift / max(length(shift * ScreenSize), 0.001));
    vec2 halfPixel = 0.5 / ScreenSize;
    vec3 scene = texture(SceneColor, clamp(screenUv + shift, halfPixel, 1.0 - halfPixel)).rgb;
    float center = (1.0 - smoothstep(0.06, 0.27, radius)) * min(Intensity, 1.0);
    scene *= 1.0 - center * 0.10;
    scene = mix(scene, scene * vec3(1.02, 0.75, 1.12), clamp(envelope * Intensity * 0.04, 0.0, 0.12));
    float alpha = (1.0 - smoothstep(0.73, 1.0, radius)) * vertexColor.a * ColorModulator.a;
    fragColor = vec4(scene * ColorModulator.rgb, alpha);
}
