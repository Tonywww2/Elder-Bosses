#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec4 vertexColor;
in vec2 effectUv;
out vec4 fragColor;
void main() {
    vec4 mask = texture(Sampler0, effectUv);
    float coverage = max(max(mask.r, mask.g), mask.b) * mask.a;
    fragColor = vec4(vertexColor.rgb * ColorModulator.rgb, coverage * vertexColor.a * ColorModulator.a);
    if (fragColor.a < 0.004) discard;
}
