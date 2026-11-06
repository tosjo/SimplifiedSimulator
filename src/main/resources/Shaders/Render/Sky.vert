#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform mat4 g_ViewMatrix;
uniform mat4 g_ProjectionMatrix;

attribute vec3 inPosition;
varying vec3 vDirection;

void main() {
    vDirection = inPosition;
    // w = 0 removes the camera translation: the sky is infinitely far away
    vec4 pos = g_ViewMatrix * vec4(inPosition, 0.0);
    pos.w = 1.0;
    gl_Position = g_ProjectionMatrix * pos;
    gl_Position.z = gl_Position.w;
}
