#version 150
in vec4 vertexColor;
in vec2 effectUv;
uniform vec4 ColorModulator;
uniform float EffectTime;
uniform float Progress;
uniform float PulseCount;
uniform vec3 CueColor;
uniform float Success;
out vec4 fragColor;
void main() {
    vec2 p = effectUv * 2.0 - 1.0;
    float r = length(p), angle = atan(p.y, p.x);
    if (Success > 0.5) {
        float t = clamp(Progress, 0.0, 1.0);
        float radius = mix(0.16, 0.91, 1.0 - pow(1.0 - t, 3.0));
        float ring = 1.0 - smoothstep(0.016, 0.065, abs(r - radius));
        float core = (1.0 - smoothstep(0.0, 0.34, r)) * pow(1.0 - t, 5.0);
        float rays = pow(abs(cos(angle * 4.0)), 32.0) *
                (1.0 - smoothstep(0.1, 0.95, r)) * (1.0 - t);
        float alpha = max(ring * (1.0 - t), max(core, rays));
        if (alpha < 0.008) discard;
        fragColor = vec4(mix(vec3(1.0, 0.66, 0.19), vec3(1.0, 0.98, 0.87),
                clamp(core + rays + 0.65, 0.0, 1.0)), alpha) * vertexColor * ColorModulator;
        return;
    }
    float rim = 1.0 - smoothstep(0.025, 0.065, abs(r - 0.78));
    float halo = (1.0 - smoothstep(0.06, 0.20, abs(r - 0.78))) * 0.35;
    float innerRadius = mix(0.72, 0.40, Progress);
    float white = 1.0 - smoothstep(0.012, 0.042, abs(r - innerRadius));
    float rays = pow(abs(cos(angle * 2.0 + EffectTime * 1.5)), 20.0)
            * (1.0 - smoothstep(0.04, 0.20, abs(r - 0.78)));
    float pulse = 0.85 + 0.15 * cos(Progress * PulseCount * 6.2831853);
    float alpha = max(max(rim, halo), max(white, rays)) * pulse;
    vec3 color = mix(CueColor, vec3(1.0, 0.97, 0.90), max(white, rays));
    if (alpha < 0.008) discard;
    fragColor = vec4(color, alpha) * vertexColor * ColorModulator;
}
