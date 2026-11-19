#version 300 es
precision highp float;

in vec3 v_WorldPos;
in vec3 v_LocalPos;
in vec3 v_Normal;
in vec2 v_Uv;

uniform vec3 u_CameraPos;
uniform vec3 u_BaseColor;
uniform vec3 u_RimColor;
uniform vec3 u_LightDir;
uniform float u_DissolveAmount;
uniform float u_SkinStyle;
uniform float u_AlertPulse;
uniform float u_Time;

out vec4 fragColor;

float hash3(vec3 p) {
    p = fract(p * vec3(443.8975, 397.2973, 491.1871));
    p += dot(p, p.yxz + 19.19);
    return fract((p.x + p.y) * p.z);
}

float valueNoise3D(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float n000 = hash3(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash3(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash3(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash3(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash3(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash3(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash3(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash3(i + vec3(1.0, 1.0, 1.0));

    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);

    return mix(mix(nx00, nx10, f.y), mix(nx01, nx11, f.y), f.z);
}

void main() {
    if (u_DissolveAmount > 0.001) {
        float n = valueNoise3D(v_LocalPos * 4.5 + vec3(0.0, u_Time * 0.6, 0.0));
        float threshold = u_DissolveAmount * 1.15;
        if (n < threshold) {
            discard;
        }
        float edgeGlow = 1.0 - smoothstep(threshold, threshold + 0.14, n);
        if (edgeGlow > 0.01) {
            vec3 ember = mix(u_RimColor, vec3(1.0, 0.92, 0.55), edgeGlow);
            fragColor = vec4(ember * 2.2, 1.0);
            return;
        }
    }

    vec3 N = normalize(v_Normal);
    vec3 V = normalize(u_CameraPos - v_WorldPos);
    vec3 L = normalize(u_LightDir);
    vec3 H = normalize(L + V);

    vec3 albedo = u_BaseColor;
    vec3 rimTint = u_RimColor;

    // Skin-specific UV coloring (including Watermelon Slice 3D model seeds, pith & rind!)
    if (u_SkinStyle > 0.5 && u_SkinStyle < 1.5) {
        if (v_Uv.y < 0.03) {
            albedo = vec3(0.06, 0.05, 0.06); // Raised 3D black seeds
            rimTint = vec3(0.35, 0.25, 0.25);
        } else if (v_Uv.y < 0.72) {
            albedo = vec3(0.96, 0.18, 0.30); // Juicy red flesh
            rimTint = vec3(1.0, 0.45, 0.55);
        } else if (v_Uv.y < 0.85) {
            albedo = vec3(0.92, 0.98, 0.88); // Crisp white pith ring
            rimTint = vec3(0.85, 1.0, 0.85);
        } else {
            float stripe = step(0.5, fract(v_Uv.x * 6.0));
            albedo = mix(vec3(0.08, 0.52, 0.18), vec3(0.18, 0.76, 0.30), stripe); // Striped green rind
            rimTint = vec3(0.42, 1.0, 0.58);
        }
    } else if (u_SkinStyle >= 1.5 && u_SkinStyle < 2.5) {
        albedo = mix(vec3(0.92, 0.68, 0.14), vec3(1.0, 0.88, 0.45), fract(v_Uv.x * 2.0));
        rimTint = vec3(1.0, 0.95, 0.65);
    }

    float hemi = 0.5 + 0.5 * N.y;
    vec3 ambient = mix(vec3(0.10, 0.13, 0.22), vec3(0.28, 0.34, 0.48), hemi) * albedo;
    float diff = max(dot(N, L), 0.0);
    vec3 diffuse = albedo * diff * 0.85;
    float spec = pow(max(dot(N, H), 0.0), 42.0);
    vec3 specular = vec3(1.0, 0.98, 0.95) * spec * 0.55;

    float fresnel = pow(1.0 - max(dot(N, V), 0.0), 2.8);
    float pulse = 0.88 + 0.12 * sin(u_Time * 4.0);
    vec3 rim = rimTint * fresnel * 1.15 * pulse;

    float panel = smoothstep(0.02, 0.05, abs(fract(v_Uv.x * 3.0) - 0.5));
    vec3 litColor = (ambient + diffuse) * mix(0.88, 1.04, panel) + specular + rim;

    // Alert pulse for armed ticking bombs
    if (u_AlertPulse > 0.01) {
        litColor = mix(litColor, vec3(1.0, 0.95, 0.85) * 1.4, u_AlertPulse * 0.65);
    }

    fragColor = vec4(litColor, 1.0);
}
