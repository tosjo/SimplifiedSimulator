#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Shaders/Render/Noise.glsllib"

uniform sampler2D m_FineMap;
uniform sampler2D m_MidMap;
uniform sampler2D m_WideMap;
uniform float m_FineHalf;
uniform float m_MidHalf;
uniform float m_WideHalf;
uniform vec4 m_OuterColor;
uniform vec4 m_AmbientColor;
uniform vec4 m_SunColor;
uniform vec3 m_SunDirection;
uniform vec4 m_FogColor;
uniform float m_FogDensity;
uniform float m_DetailStrength;
uniform vec3 g_CameraPosition;

varying vec3 vWorld;

// texture coordinates of a square area of the given half size centred on the crossing;
// world X is east and world Z is south, image rows run from north to south
vec2 areaUv(float halfSize) {
    return vec2((vWorld.x + halfSize) / (2.0 * halfSize), 1.0 - (vWorld.z + halfSize) / (2.0 * halfSize));
}

// 1 well inside the area, fading to 0 over the outer part so the layers blend without seams
float weight(float halfSize, float fade) {
    float r = max(abs(vWorld.x), abs(vWorld.z));
    return smoothstep(halfSize, halfSize * (1.0 - fade), r);
}

void main() {
    vec3 color = m_OuterColor.rgb * (0.92 + 0.16 * fbm(vWorld.xz * 0.004));
    color = mix(color, texture2D(m_WideMap, areaUv(m_WideHalf)).rgb, weight(m_WideHalf, 0.25));
    color = mix(color, texture2D(m_MidMap, areaUv(m_MidHalf)).rgb, weight(m_MidHalf, 0.08));
    color = mix(color, texture2D(m_FineMap, areaUv(m_FineHalf)).rgb, weight(m_FineHalf, 0.15));

    // a little fine-grained variation so the photo does not look blurry up close
    float dist = distance(g_CameraPosition, vWorld);
    float detail = (valueNoise(vWorld.xz * 6.0) - 0.5) * m_DetailStrength * smoothstep(60.0, 5.0, dist);
    color *= 1.0 + detail;

    float sun = max(m_SunDirection.y, 0.0);
    color *= m_AmbientColor.rgb + m_SunColor.rgb * sun;

    float fog = 1.0 - exp(-pow(dist * m_FogDensity, 2.0));
    gl_FragColor = vec4(mix(color, m_FogColor.rgb, fog), 1.0);
}
