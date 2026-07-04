#version 410 core

uniform sampler2D uNearDepth;
uniform vec2 uNearSize;
uniform vec2 uTargetSize;
uniform int uReverseDepth;

void main() {
  vec2 sourcePixel = gl_FragCoord.xy * (uNearSize / max(uTargetSize, vec2(1.0)));
  ivec2 texel = ivec2(clamp(floor(sourcePixel), vec2(0.0), uNearSize - vec2(1.0)));
  float nearDepth = texelFetch(uNearDepth, texel, 0).r;
  bool sky = (uReverseDepth != 0) ? (nearDepth <= 0.000001) : (nearDepth >= 0.999999);
  if (sky) {
    // No near geometry here: keep stencil 0 so the distant colour pass may draw.
    discard;
  }
  // Near geometry present: fall through so the GL_REPLACE stencil op writes 1 (blocks Voxy).
}
