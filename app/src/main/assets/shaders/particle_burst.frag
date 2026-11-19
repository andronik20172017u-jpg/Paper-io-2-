#version 300 es
precision mediump float;

in vec3 v_Normal;
in vec2 v_Uv;

uniform vec3 u_Color;
uniform float u_Alpha;

out vec4 fragColor;

void main() {
    float light = 0.5 + 0.5 * abs(v_Normal.y);
    vec3 glow = mix(u_Color, vec3(1.0), 0.35) * light * 1.4;
    fragColor = vec4(glow, u_Alpha);
}
