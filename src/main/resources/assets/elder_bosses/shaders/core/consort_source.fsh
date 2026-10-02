#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec4 vertexColor;
in vec2 effectUv;
out vec4 fragColor;
void main() {
    fragColor=texture(Sampler0,effectUv)*vertexColor*ColorModulator;
    if(fragColor.a<0.004) discard;
}
