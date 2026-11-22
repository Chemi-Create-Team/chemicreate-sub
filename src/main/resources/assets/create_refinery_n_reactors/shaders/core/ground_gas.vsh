#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out vec4 vertexColor;
out vec3 samplePosition;
out vec3 cameraRelative;
out float vertexDistance;
flat out int cellData;
flat out int sliceAxis;
flat out float sliceDepth;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    vertexDistance = fog_distance(Position, FogShape);
    samplePosition = vec3(UV0.x, float(UV2.x) / 64.0, UV0.y);
    cameraRelative = Position;
    cellData = UV2.y;
    sliceAxis = UV1.x;
    sliceDepth = float(UV1.y) / 16384.0;
}
