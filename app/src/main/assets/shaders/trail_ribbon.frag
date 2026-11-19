#version 300 es
precision highp float;

in vec3 v_WorldPos;
in vec3 v_Normal;
in vec2 v_Uv;

uniform float u_Time;
uniform vec3 u_TeamColor;
uniform float u_SkinStyle;

out vec4 fragColor;

void main() {
    float centerDist = abs(v_Uv.y - 0.5) * 2.0; // 0 at top ridge, 1 at floor edge
    float core = 1.0 - smoothstep(0.0, 0.72, centerDist);

    // Segmented chain blocks + high-speed energy wave packets along trail
    float segments = 0.72 + 0.28 * smoothstep(-0.2, 0.4, sin(v_Uv.x * 4.5 - u_Time * 11.0));
    float spark = pow(max(0.0, sin(v_Uv.x * 7.5 - u_Time * 15.0)), 6.0) * core;

    vec3 N = normalize(v_Normal);
    float light = 0.68 + 0.32 * max(N.y, 0.0);

    vec3 baseCol = u_TeamColor;
    if (u_SkinStyle > 0.5 && u_SkinStyle < 1.5) {
        // Watermelon trail: glowing crimson core with crisp emerald border edges
        baseCol = mix(vec3(0.98, 0.24, 0.34), vec3(0.16, 0.82, 0.36), smoothstep(0.55, 0.95, centerDist));
    }

    vec3 color = mix(baseCol, vec3(1.0), core * 0.48 + spark * 0.6) * (0.92 + 0.38 * segments) * light;
    float alpha = mix(0.68, 0.96, core);

    fragColor = vec4(color, alpha);
}
