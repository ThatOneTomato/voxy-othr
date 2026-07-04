#version 410 core

uniform sampler2D uTexture;

in vec2 texCoord;
flat in uint metadata;

layout(location = 0) out vec4 colour;
layout(location = 1) out uvec4 metaOut;

void main() {
  colour = textureLod(uTexture, texCoord, 0.0);
  if (colour.a < 0.001 && (metadata & 1u) != 0u) {
    discard;
  }
  metaOut = uvec4((metadata >> 2) & 1u);
}
