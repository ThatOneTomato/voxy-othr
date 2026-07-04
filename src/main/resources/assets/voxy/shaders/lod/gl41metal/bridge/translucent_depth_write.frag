#version 410 core

uniform sampler2DRect uTgbuffer1Tex;
uniform vec2 uSharedSize;
uniform vec2 uTargetSize;

vec2 sharedPixelForTarget(vec2 targetPixel) {
  vec2 shared = targetPixel * (uSharedSize / max(uTargetSize, vec2(1.0)));
  return vec2(shared.x, uSharedSize.y - shared.y);
}

void main() {
  float depth = texture(uTgbuffer1Tex, sharedPixelForTarget(gl_FragCoord.xy)).x;
  if (depth <= 0.0 || depth >= 1.0) discard;
  gl_FragDepth = depth;
}
