#version 150

uniform vec4 ColorModulator;
uniform float EffectTime;
uniform float Activity;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
in vec2 wingUv;
in vec4 wingData;
in float vertexDistance;
out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x),
               mix(hash(i + vec2(0, 1)), hash(i + vec2(1)), f.x), f.y);
}
float fbm(vec2 p) { return noise(p) * 0.57 + noise(p * 2.07 + 8.3) * 0.28 + noise(p * 4.13) * 0.15; }

void main() {
    // Deliberate small pixel steps retain the material language of the block world.
    vec2 uv = floor(wingUv * vec2(176, 144)) / vec2(176, 144);
    float u = uv.x, v = uv.y, layer = wingData.g * 2.0;
    float seed = wingData.b * 19.0 + wingData.r * 7.7 + layer * 5.1;
    float time = EffectTime * (0.46 + Activity * 0.18);
    vec2 flow = vec2(u * 4.0 - time, v * 6.0 + seed);
    float turbulence = fbm(flow);
    float warp = (turbulence - 0.5) * 0.13 * (0.3 + u);
    float angle = v + warp + sin(u * 6.0 - time * 1.2 + seed) * 0.038 * u;
    float tongue = pow(0.5 + 0.5 * sin(angle * 39.0 + seed + sin(angle * 21.0) * 1.7), 0.55);
    float reach = 0.50 + 0.39 * tongue + 0.07 * sin(angle * 31.0 + seed);
    reach *= 0.62 + 0.38 * pow(max(0.0, sin(v * 3.14159)), 0.35);
    float edge = reach - u + (turbulence - 0.5) * 0.19;
    float body = smoothstep(-0.012, 0.065, edge);
    body *= smoothstep(0.0, 0.055, v) * (1.0 - smoothstep(0.94, 1.0, v));
    body *= smoothstep(0.015, 0.10, u);
    float grain = noise(uv * vec2(87.0, 103.0) + seed);
    float holes = fbm(vec2(u * 13.0 - time * 0.5, angle * 38.0 + seed));
    body *= smoothstep(0.19 + u * 0.14, 0.38 + u * 0.13, holes);
    float filament = pow(0.5 + 0.5 * sin(angle * 76.0 + turbulence * 7.0 - u * 8.0), 4.0);
    float heat = clamp(turbulence * 0.90 + filament * 0.20 + grain * 0.10 + 0.10, 0.0, 1.0);
    vec3 soot = vec3(0.065, 0.027, 0.055);
    vec3 violet = vec3(0.25, 0.075, 0.28);
    vec3 scarlet = vec3(0.61, 0.095, 0.065);
    vec3 orange = vec3(0.97, 0.32, 0.065);
    vec3 yellow = vec3(1.0, 0.77, 0.28);
    vec3 color = mix(soot, violet, smoothstep(0.18, 0.42, heat));
    color = mix(color, scarlet, smoothstep(0.38, 0.61, heat));
    color = mix(color, orange, smoothstep(0.58, 0.79, heat));
    color = mix(color, yellow, smoothstep(0.79, 0.98, heat));
    float bruise = smoothstep(0.46, 0.72, fbm(vec2(u * 6.0 - time * 0.35, angle * 9.0 + seed + 30.0)));
    color = mix(color, mix(soot, violet, turbulence), bruise * 0.85);
    float emberRim = (1.0 - smoothstep(0.01, 0.12, abs(edge))) * filament;
    color = mix(color, yellow, emberRim * 0.65);

    // Detached, fluttering ash/butterfly flecks follow the same outward flow as the tongues.
    vec2 motes = vec2(u - time * 0.085, v + sin(time + u * 8.0 + seed) * 0.009) * vec2(63, 79);
    vec2 cell = floor(motes), local = fract(motes) - 0.5;
    float random = hash(cell + seed);
    float flutter = abs(local.x) * (1.6 + sin(time * 12.0 + random * 30.0) * 0.7) + abs(local.y);
    float flecks = (1.0 - smoothstep(0.10, 0.26, flutter)) * step(0.87, random);
    flecks *= smoothstep(0.38, 0.72, u) * (1.0 - smoothstep(0.98, 1.12, u));
    flecks *= smoothstep(0.02, 0.09, v) * (1.0 - smoothstep(0.91, 0.99, v));
    color = mix(color, mix(orange, yellow, random), flecks);
    float alpha = max(body * (0.64 - layer * 0.12), flecks * 0.75) * wingData.a;
    if (alpha < 0.015) discard;
    float fog = smoothstep(FogStart, max(FogStart + 0.01, FogEnd), vertexDistance);
    fragColor = vec4(mix(color * ColorModulator.rgb, FogColor.rgb, fog * FogColor.a), alpha * ColorModulator.a);
}
