#version 120

uniform sampler2D texture;
uniform sampler2D lightmap;

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 color;

void main() {
    vec4 shaded = texture2D(texture, texcoord) * color * texture2D(lightmap, lmcoord);
    if (shaded.a < 0.1) discard;
    gl_FragData[0] = shaded;
}
