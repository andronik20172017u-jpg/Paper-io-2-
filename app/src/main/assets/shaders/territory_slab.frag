#version 300 es
precision highp float;

in vec3 v_WorldPos;
in vec3 v_Normal;
in vec2 v_Uv;

uniform float u_Time;
uniform vec3 u_TeamColor;
uniform vec3 u_CameraPos;
uniform vec3 u_LightDir;
uniform float u_SkinStyle;   // 0=CyberKnit, 1=Watermelon, 2=GoldCarbon, 3=VenomPlasma
uniform float u_FlashAmount; // 0..1 capture flash pulse

out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(234.34, 435.345));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}

float knitWeave(vec2 xz) {
    vec2 p = xz * 2.8;
    float a = sin(p.x * 3.14159 + p.y * 1.2);
    float b = cos(p.y * 3.14159 - p.x * 1.2);
    float grain = hash21(floor(xz * 8.0)) * 0.12;
    return 0.5 + 0.25 * (a * b) + grain;
}

float watermelonSeedMask(vec2 xz) {
    vec2 cell = floor(xz * 0.55);
    vec2 local = fract(xz * 0.55) - 0.5;
    float rnd = hash21(cell);
    if (rnd < 0.72) return 0.0;
    vec2 offset = vec2(hash21(cell + 1.3) - 0.5, hash21(cell + 7.1) - 0.5) * 0.42;
    vec2 d = local - offset;
    d.x *= 1.6;
    return 1.0 - smoothstep(0.06, 0.10, length(d));
}

void main() {
    vec3 N = normalize(v_Normal);
    vec3 L = normalize(u_LightDir);
    vec3 V = normalize(u_CameraPos - v_WorldPos);

    bool isTopFace = N.y > 0.5;
    float weave = knitWeave(v_WorldPos.xz);

    if (isTopFace) {
        float wave1 = sin(v_WorldPos.x * 0.45 + v_WorldPos.z * 0.45 - u_Time * 2.2);
        float wave2 = cos(v_WorldPos.x * 0.90 - v_WorldPos.z * 0.65 + u_Time * 1.4);
        float wavePattern = 0.5 + 0.5 * (wave1 * 0.6 + wave2 * 0.4);

        vec3 surfaceColor = u_TeamColor;

        if (u_SkinStyle > 0.5 && u_SkinStyle < 1.5) {
            // Watermelon Flesh + Dark Seeds
            vec3 flesh = mix(vec3(0.92, 0.16, 0.28), vec3(1.0, 0.26, 0.36), weave);
            float seed = watermelonSeedMask(v_WorldPos.xz);
            surfaceColor = mix(flesh, vec3(0.08, 0.05, 0.06), seed * 0.92);
        } else if (u_SkinStyle >= 1.5 && u_SkinStyle < 2.5) {
            // Gold Carbon weave
            vec2 hex = abs(fract(v_WorldPos.xz * 0.5) - 0.5);
            float carbon = smoothstep(0.32, 0.46, max(hex.x, hex.y));
            surfaceColor = mix(vec3(0.95, 0.70, 0.14), vec3(1.0, 0.88, 0.42), weave) * mix(0.88, 1.12, carbon);
        } else if (u_SkinStyle >= 2.5) {
            // Venom Bio-Hex
            vec2 cell = abs(fract(v_WorldPos.xz * 0.42) - 0.5);
            float hexEdge = smoothstep(0.38, 0.48, max(cell.x, cell.y));
            surfaceColor = mix(vec3(0.12, 0.82, 0.44), vec3(0.28, 0.96, 0.62), weave) + vec3(0.2, 1.0, 0.5) * hexEdge * 0.25;
        } else {
            // Tactile Cyber Knit surface
            vec2 cell = abs(fract(v_WorldPos.xz * 0.40) - 0.5);
            float cellEdge = smoothstep(0.42, 0.49, max(cell.x, cell.y));
            surfaceColor = u_TeamColor * mix(0.82, 1.04, weave) * (0.86 + 0.14 * wavePattern)
                         + mix(u_TeamColor, vec3(1.0), 0.35) * cellEdge * 0.16;
        }

        vec3 H = normalize(L + V);
        float spec = pow(max(dot(N, H), 0.0), 26.0) * 0.20;
        vec3 flash = vec3(1.0, 0.96, 0.82) * u_FlashAmount * 0.68;

        fragColor = vec4(surfaceColor + vec3(spec) + flash, 1.0);
    } else {
        // Extruded 3D side skirt wall with crisp bright border contour
        float diff = 0.48 + 0.52 * max(dot(N, L), 0.0);
        float heightFrac = clamp(v_Uv.y, 0.0, 1.0);
        vec3 wallTint = (u_SkinStyle > 0.5 && u_SkinStyle < 1.5)
            ? vec3(0.14, 0.68, 0.28) // Watermelon green rind border!
            : u_TeamColor;
        vec3 sideColor = wallTint * 0.58 * diff;
        vec3 topLipGlow = mix(wallTint, vec3(1.0), 0.52) * smoothstep(0.60, 1.0, heightFrac) * 0.92;
        vec3 flash = vec3(1.0, 0.96, 0.82) * u_FlashAmount * 0.55;
        fragColor = vec4(sideColor + topLipGlow + flash, 1.0);
    }
}
