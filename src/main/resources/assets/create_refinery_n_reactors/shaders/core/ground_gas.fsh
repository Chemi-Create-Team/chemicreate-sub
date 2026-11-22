#version 150

#moj_import <fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;

in vec4 vertexColor;
in vec3 samplePosition;
in vec3 cameraRelative;
in float vertexDistance;
flat in int cellData;
flat in int sliceAxis;
flat in float sliceDepth;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 p, vec2 period) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f *= f * (3.0 - 2.0 * f);
    vec2 low = mod(i.xz, period);
    vec2 high = mod(i.xz + vec2(1.0), period);
    vec2 a = mix(vec2(hash(vec3(low.x, i.y, low.y)), hash(vec3(low.x, i.y + 1.0, low.y))),
        vec2(hash(vec3(high.x, i.y, low.y)), hash(vec3(high.x, i.y + 1.0, low.y))), f.x);
    vec2 b = mix(vec2(hash(vec3(low.x, i.y, high.y)), hash(vec3(low.x, i.y + 1.0, high.y))),
        vec2(hash(vec3(high.x, i.y, high.y)), hash(vec3(high.x, i.y + 1.0, high.y))), f.x);
    vec2 n = mix(a, b, f.z);
    return mix(n.x, n.y, f.y);
}

float driftingNoise(vec3 position, vec2 period) {
    // Travel one lattice period per GameTime cycle so the day boundary is continuous.
    position.xz -= period * GameTime;
    return noise(position, period);
}

bool openTo(int side) {
    return (cellData & side) != 0;
}

float fadeEdge(float d, float width, int side) {
    return openTo(side) ? 1.0 : smoothstep(0.0, width, d);
}

void main() {
    float opticalDepth = sliceDepth;
    if (sliceAxis >= 0) {
        vec3 ray = abs(normalize(cameraRelative));
        float threshold = max(ray.x, max(ray.y, ray.z)) * 0.78;
        vec3 weights = max(vec3(0.0), ray - threshold);
        float weight = weights[sliceAxis];
        if (weight <= 0.0) discard;
        opticalDepth *= weight / ((weights.x + weights.y + weights.z) * ray[sliceAxis]);
    }
    vec3 local = fract(samplePosition + vec3(0.5));
    float floorY = float((cellData >> 6) & 31) / 16.0;
    float ceilingY = float((cellData >> 11) & 31) / 16.0;
    float height = max(0.0, local.y - floorY);
    vec3 p = samplePosition * vec3(0.67, 1.9, 0.67);
    float drift = driftingNoise(p * 0.52, vec2(64.0));
    p += (drift - 0.5) * vec3(0.64, 0.24, 0.50);
    float broad = driftingNoise(p, vec2(128.0, 64.0));
    float fine = driftingNoise(p * 2.35 + vec3(4.0, 2.0, 7.0), vec2(256.0, 128.0));
    float folds = broad * 0.67 + fine * 0.33;
    float edgeWidth = mix(0.28, 0.66, folds);
    float edge = fadeEdge(local.z, edgeWidth, 1) * fadeEdge(1.0 - local.z, edgeWidth, 2)
        * fadeEdge(local.x, edgeWidth, 4) * fadeEdge(1.0 - local.x, edgeWidth, 8)
        * fadeEdge(ceilingY - local.y, min(0.55, (ceilingY - floorY) * 0.85), 32);

    float veil = 0.20 + 0.19 * folds;
    float profile = openTo(16) ? veil * mix(0.36, 0.56, drift)
        : veil + 1.15 * exp2(-height * mix(9.0, 4.5, broad));
    float density = edge * profile * mix(0.12, 1.92, smoothstep(0.20, 0.78, folds));
    float opacity = 1.0 - exp(-density * opticalDepth);
    float neutral = dot(vertexColor.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 color = mix(vertexColor.rgb, vec3(neutral), mix(0.27, 0.04, folds)) * mix(0.81, 1.02, folds);
    fragColor = linear_fog(vec4(color, opacity) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
}
