#version 150
in vec4 vertexColor;
in vec2 effectUv;
uniform vec4 ColorModulator;
uniform int EffectMode;
out vec4 fragColor;
void main() {
    vec2 uv = effectUv;
    float mask;
    if (EffectMode == 1) {
        // Stepped, veined petal: broad body, torn edge and a narrow tip.
        vec2 pixel = floor(uv * vec2(16.0, 24.0)) / vec2(16.0, 24.0);
        float width = max(0.03, sin(pixel.y * 3.141593) * 0.5);
        mask = 1.0 - smoothstep(width - 0.06, width, abs(pixel.x - 0.5));
        float vein = step(0.88, fract(pixel.x * 9.0 + pixel.y * 4.0));
        vec3 color = vertexColor.rgb * mix(0.88, 1.24, pixel.y) * (1.0 - vein * 0.13);
        fragColor = vec4(color, vertexColor.a * mask) * ColorModulator;
    } else {
        mask = smoothstep(0.0, 0.12, uv.x) * smoothstep(0.0, 0.12, 1.0 - uv.x);
        fragColor = vertexColor * ColorModulator;
        fragColor.a *= mask;
    }
    if (fragColor.a < 0.004) discard;
}
