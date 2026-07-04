#version 410 core

layout(location = 0) in vec4 inPositionAndMetadata;
layout(location = 1) in vec2 inUv;

uniform mat4 uTransform;

out vec2 texCoord;
flat out uint metadata;

void main() {
  metadata = floatBitsToUint(inPositionAndMetadata.w);
  gl_Position = uTransform * vec4(inPositionAndMetadata.xyz, 1.0);
  texCoord = inUv;
}
