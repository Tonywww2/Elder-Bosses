#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 WingMatrix;
uniform float EffectTime;
uniform float Spread;
uniform float Activity;
out vec2 wingUv;
out vec4 wingData;
out float vertexDistance;

void main() {
    float u = Position.x, v = Position.y, layer = Position.z;
    float side = Color.r * 2.0 - 1.0;
    float phase = EffectTime * (1.5 + Activity * 0.5) + side * 1.8 + Color.b * 9.0;
    float flare = pow(u, 0.72);
    float pulse = sin(phase + u * 3.5 + v * 4.0) * (0.035 + Activity * 0.025);
    vec3 p;
    p.x = side * (0.16 + u * (3.62 - 0.75 * pow(v - 0.5, 2.0))) * (0.38 + Spread * 0.62);
    p.y = 0.10 + (-1.55 + v * 4.8) * flare + u * u * (0.30 + v * 0.90) + pulse * u;
    p.z = 0.20 + 0.60 * u + 0.20 * sin(v * 3.14159) * u + layer * 0.23;
    p.z += (1.0 - Spread) * u * 1.65 + sin(phase + u * 5.0 - v * 2.0) * u * (0.12 + Activity * 0.12);
    p.y += layer * 0.08 + side * 0.09 * u;
    p.z += layer * sin(phase * 0.7 + v * 5.0 + layer) * u * 0.11;
    vec4 view = ModelViewMat * WingMatrix * vec4(p, 1.0);
    gl_Position = ProjMat * view;
    vertexDistance = length(view.xyz);
    wingUv = UV0;
    wingData = Color;
}
