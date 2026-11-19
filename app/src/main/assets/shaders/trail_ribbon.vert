#version 300 es
layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec3 a_Normal;
layout(location = 2) in vec2 a_Uv;

uniform mat4 u_MvpMatrix;
uniform mat4 u_ModelMatrix;

out vec3 v_WorldPos;
out vec3 v_Normal;
out vec2 v_Uv;

void main() {
    vec4 worldPos = u_ModelMatrix * vec4(a_Position, 1.0);
    v_WorldPos = worldPos.xyz;
    v_Normal = normalize(mat3(u_ModelMatrix) * a_Normal);
    v_Uv = a_Uv;
    gl_Position = u_MvpMatrix * vec4(a_Position, 1.0);
}
