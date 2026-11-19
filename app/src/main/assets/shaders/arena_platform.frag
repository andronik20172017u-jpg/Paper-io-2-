#version 300 es
precision highp float;

in vec3 v_WorldPos;
in vec3 v_Normal;
in vec2 v_Uv;

uniform float u_Time;
uniform float u_ArenaRadius;
uniform vec3 u_PlayerPos;
uniform vec3 u_CameraPos;
uniform vec4 u_Bomb0; // (x, z, radius, active01)
uniform vec4 u_Bomb1; // (x, z, radius, active01)

out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float gridLine(vec2 coord, float scale, float width) {
    vec2 p = coord / scale;
    vec2 grid = abs(fract(p - 0.5) - 0.5) / fwidth(p);
    float line = min(grid.x, grid.y);
    return 1.0 - min(line / width, 1.0);
}

float knitRelief(vec2 xz) {
    // Dense woven / knitted micro-fabric texture like tactile Paper.io 3D mats
    vec2 p = xz * 2.6;
    float w1 = sin(p.x * 3.14159 + sin(p.y * 3.14159) * 0.6);
    float w2 = cos(p.y * 3.14159 + cos(p.x * 3.14159) * 0.6);
    float grain = hash21(floor(xz * 10.0)) * 0.14;
    return 0.5 + 0.25 * (w1 * w2) + grain;
}

float bombDangerRing(vec2 xz, vec4 bomb) {
    if (bomb.w < 0.5) return 0.0;
    float d = length(xz - bomb.xy);
    float r = bomb.z;
    float inZone = 1.0 - smoothstep(r - 0.2, r + 0.05, d);
    float ring = smoothstep(r - 0.35, r - 0.08, d) * (1.0 - smoothstep(r - 0.05, r + 0.18, d));
    float pulse = 0.55 + 0.45 * sin(u_Time * 14.0);
    return (inZone * 0.28 + ring * 0.95) * pulse;
}

void main() {
    vec3 N = normalize(v_Normal);
    vec3 V = normalize(u_CameraPos - v_WorldPos);
    float radial = length(v_WorldPos.xz);
    float normR = clamp(radial / u_ArenaRadius, 0.0, 1.2);

    if (N.y < 0.75) {
        vec3 L = normalize(vec3(0.4, 0.85, 0.3));
        float diff = 0.35 + 0.65 * max(dot(N, L), 0.0);
        float rimGlow = smoothstep(-0.2, 0.0, v_WorldPos.y) * 0.65;
        vec3 wallColor = vec3(0.06, 0.10, 0.18) * diff + vec3(0.0, 0.68, 0.96) * rimGlow;
        fragColor = vec4(wallColor, 1.0);
        return;
    }

    // Tactile knit weave + radial depth gradient
    float weave = knitRelief(v_WorldPos.xz);
    vec3 baseColor = mix(vec3(0.082, 0.115, 0.195), vec3(0.036, 0.052, 0.098), normR * normR);
    baseColor *= mix(0.88, 1.12, weave);

    // Anti-aliased dual-scale grid
    float fineGrid = gridLine(v_WorldPos.xz, 2.5, 1.05);
    float majorGrid = gridLine(v_WorldPos.xz, 10.0, 1.45);
    float edgeFade = 1.0 - smoothstep(0.84, 1.0, normR);

    // Player proximity energy ripple
    float distPlayer = length(v_WorldPos.xz - u_PlayerPos.xz);
    float ripple = exp(-distPlayer * 0.18) * (0.65 + 0.35 * sin(distPlayer * 1.4 - u_Time * 4.5));

    vec3 gridColor = vec3(0.14, 0.25, 0.42) * fineGrid * 0.42 * edgeFade
                   + vec3(0.18, 0.50, 0.80) * majorGrid * 0.68 * edgeFade
                   + vec3(0.05, 0.78, 0.96) * (fineGrid + majorGrid) * ripple * 0.55;

    // Glowing arena perimeter ring
    float ringBand = smoothstep(0.955, 0.988, normR) * (1.0 - smoothstep(0.992, 1.015, normR));
    float ringPulse = 0.8 + 0.2 * sin(atan(v_WorldPos.z, v_WorldPos.x) * 8.0 + u_Time * 2.5);
    vec3 boundaryGlow = vec3(0.15, 0.84, 1.0) * ringBand * 1.35 * ringPulse;

    // Bomb danger zone warning rings on floor
    float danger = max(bombDangerRing(v_WorldPos.xz, u_Bomb0), bombDangerRing(v_WorldPos.xz, u_Bomb1));
    vec3 dangerColor = vec3(1.0, 0.22, 0.12) * danger;

    float fresnel = pow(1.0 - max(dot(N, V), 0.0), 3.0);
    vec3 finalColor = baseColor + gridColor + boundaryGlow + dangerColor + vec3(0.05, 0.10, 0.18) * fresnel;

    fragColor = vec4(finalColor, 1.0);
}
