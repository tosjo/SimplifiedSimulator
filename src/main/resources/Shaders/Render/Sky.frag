#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Shaders/Render/Noise.glsllib"

uniform vec3 m_SunDirection;
uniform vec4 m_ZenithColor;
uniform vec4 m_HorizonColor;
uniform vec4 m_GroundColor;
uniform vec4 m_SunColor;
uniform float m_SunGlow;
uniform float m_CloudCover;
uniform vec4 m_CloudColor;
uniform float m_Time;
uniform float m_StarBrightness;

varying vec3 vDirection;

void main() {
    vec3 dir = normalize(vDirection);
    vec3 sun = normalize(m_SunDirection);
    float h = dir.y;

    // sky gradient, with a bright haze band just above the horizon
    float t = pow(clamp(h, 0.0, 1.0), 0.45);
    vec3 color = mix(m_HorizonColor.rgb, m_ZenithColor.rgb, t);

    // sunrise / sunset tint around the sun and along the horizon
    float sunDot = max(dot(dir, sun), 0.0);
    float horizonBand = exp(-abs(h) * 6.0);
    color += m_SunColor.rgb * m_SunGlow * (pow(sunDot, 6.0) * 0.35 * horizonBand + pow(sunDot, 64.0) * 0.5);

    // stars at night, fixed to the sky
    if (m_StarBrightness > 0.0 && h > 0.0) {
        vec2 cell = floor(dir.xz / (h + 0.4) * 300.0);
        float star = step(0.9975, hash12(cell)) * hash12(cell + 3.7);
        color += vec3(star * m_StarBrightness * smoothstep(0.0, 0.3, h));
    }

    // clouds on a virtual plane high above the scene
    if (h > 0.0) {
        vec2 uv = dir.xz / (h + 0.08) * 1.6 + vec2(m_Time * 0.004, m_Time * 0.0015);
        float n = fbm(uv);
        float cover = smoothstep(1.0 - m_CloudCover, 1.0 - m_CloudCover + 0.35, n);
        float thickness = smoothstep(0.4, 1.0, n);
        vec3 lit = m_CloudColor.rgb * (1.0 - 0.35 * thickness) + m_SunColor.rgb * pow(sunDot, 4.0) * 0.25 * m_SunGlow;
        color = mix(color, lit, cover * smoothstep(0.0, 0.12, h) * 0.9);
    }

    // sun disc
    float disc = smoothstep(0.99955, 0.99975, sunDot) * smoothstep(-0.02, 0.01, sun.y);
    color = mix(color, m_SunColor.rgb * 1.6 + vec3(0.2), disc);

    // below the horizon fade into the hazy ground colour
    color = mix(color, m_GroundColor.rgb, smoothstep(0.0, -0.08, h));
    gl_FragColor = vec4(color, 1.0);
}
