#version 150

uniform float u_Time;
uniform float u_Strength;
uniform float u_Alpha;
uniform vec4 u_BaseColor;
uniform vec3 u_CamPos;
uniform float u_Inside;

in vec4 vColor;
in vec3 vPos;

out vec4 fragColor;

float hash11(float p) {
    return fract(sin(p * 127.1) * 43758.5453123);
}

float hash31(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 191.3))) * 43758.5453123);
}

float valueNoise3(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    vec3 u = f * f * (3.0 - 2.0 * f);

    float n000 = hash31(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash31(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash31(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash31(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash31(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash31(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash31(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash31(i + vec3(1.0, 1.0, 1.0));

    float nx00 = mix(n000, n100, u.x);
    float nx10 = mix(n010, n110, u.x);
    float nx01 = mix(n001, n101, u.x);
    float nx11 = mix(n011, n111, u.x);
    float nxy0 = mix(nx00, nx10, u.y);
    float nxy1 = mix(nx01, nx11, u.y);
    return mix(nxy0, nxy1, u.z);
}

float fbm(vec3 p) {
    float value = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        value += valueNoise3(p) * amp;
        p = p * 2.03 + vec3(13.7, 9.2, 11.4);
        amp *= 0.52;
    }
    return value;
}

void main() {
    float t = u_Time;

    vec3 local = vPos;
    vec3 dir = normalize(local + vec3(0.0001, 0.0001, 0.0001));
    float radial = clamp(length(local.xz), 0.0, 1.2);
    float height = clamp(abs(local.y), 0.0, 1.2);
    float shell = clamp(length(local), 0.0, 1.2);

    vec3 flowA = dir * 6.2 + vec3(0.0, t * 0.42, 0.0);
    vec3 flowB = local * vec3(1.9, 1.1, 1.9) + vec3(t * 0.34, -t * 0.24, t * 0.19);
    vec3 flowC = vec3(atan(dir.z, dir.x) * 3.4, height * 4.8, radial * 4.4 - t * 0.55);

    float n1 = fbm(flowA);
    float n2 = fbm(flowB);
    float n3 = fbm(flowC + vec3(n1, n2, n1 - n2) * 1.7);
    float n4 = valueNoise3(local * 12.0 + vec3(-t * 0.72, t * 0.48, t * 0.38));

    float field = n1 * 0.30 + n2 * 0.24 + n3 * 0.30 + n4 * 0.16;
    float distortion = fbm(local * 3.3 + dir.yzx * 2.8 + vec3(0.0, -t * 0.28, t * 0.19));

    float filaments = smoothstep(0.46, 0.80, field + distortion * 0.32);
    float fractures = 1.0 - smoothstep(0.05, 0.18, abs(field - 0.54 - distortion * 0.16));
    float wisps = smoothstep(0.22, 0.92, n3 + n4 * 0.34);

    float rim = smoothstep(0.86, 1.01, shell);
    float polarFade = smoothstep(0.02, 0.78, height);
    float coreSoft = 1.0 - smoothstep(0.00, 0.24, shell);
    float shellMask = smoothstep(0.18, 1.00, shell) * (0.84 + polarFade * 0.16);

    float gradient = clamp(0.12 + shell * 0.20 + height * 0.10 + filaments * 0.28 - coreSoft * 0.06, 0.0, 1.0);
    float intensity = clamp(
        field * 0.24 +
        filaments * 0.38 +
        fractures * 0.30 +
        wisps * 0.22 +
        rim * 0.24 +
        gradient,
        0.0,
        1.0
    );
    intensity = smoothstep(0.04, 0.98, intensity);

    float shimmer = 0.90 + 0.10 * sin(t * 1.1 + hash11(floor(shell * 18.0) + floor(height * 23.0) * 7.0) * 6.28318);
    vec3 normal = normalize(local + vec3(0.0001, 0.0001, 0.0001));
    vec3 viewDir = normalize(u_CamPos - local);
    float facing = u_Inside > 0.5 ? max(dot(-normal, viewDir), 0.0) : max(dot(normal, viewDir), 0.0);
    float faceMask = 0.55 + 0.45 * smoothstep(0.00, 0.18, facing);
    float alpha = u_Alpha * (0.32 + intensity * 0.42 + rim * 0.16) * shellMask * shimmer * u_Strength;
    alpha *= faceMask;
    alpha = clamp(alpha, 0.0, 0.82);

    vec3 base = u_BaseColor.rgb * vColor.rgb;
    vec3 deep = base * 0.12;
    vec3 mid = mix(base * 0.30, base * 0.62, gradient);
    vec3 hot = mix(vec3(0.84, 0.14, 0.10), vec3(1.0, 0.40, 0.28), wisps * 0.55 + rim * 0.45);

    vec3 color = mix(deep, mid, intensity);
    color = mix(color, hot, filaments * 0.36 + fractures * 0.18);
    color *= 0.66 + 0.18 * gradient;

    fragColor = vec4(color, alpha);
}
