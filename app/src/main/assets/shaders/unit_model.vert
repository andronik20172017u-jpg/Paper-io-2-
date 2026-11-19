#version 300 es
layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec3 a_Normal;
layout(location = 2) in vec2 a_Uv;

uniform mat4 u_MvpMatrix;
uniform mat4 u_ModelMatrix;
uniform mat3 u_NormalMatrix;
uniform float u_DissolveAmount;
uniform float u_Time;

out vec3 v_WorldPos;
out vec3 v_LocalPos;
out vec3 v_Normal;
out vec2 v_Uv;

void main() {
    // Expand vertices outward along normal when unit dissolves on death
    float burst = u_DissolveAmount * u_DissolveAmount * 2.2;
    vec3 displaced = a_Position + a_Normal * burst + vec3(0.0, u_DissolveAmount * 0.9, 0.0);

    vec4 worldPos = u_ModelMatrix * vec4(displaced, 1.0);
    v_WorldPos = worldPos.xyz;
    v_LocalPos = a_Position;
    v_Normal = normalize(u_NormalMatrix * a_Normal);
    v_Uv = a_Uv;

    gl_Position = u_MvpMatrix * vec4(displaced, 1.0);
}
