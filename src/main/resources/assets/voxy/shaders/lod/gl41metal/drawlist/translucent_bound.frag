#version 410 core

uniform sampler2D uBlockModelAtlas;
uniform sampler2D uBoundDepthTex;
uniform vec2 uBoundSize;
uniform vec2 uTargetSize;
uniform int uBoundEnabled;

layout(location = 0) in vec2 vUv;
layout(location = 2) flat in uvec4 vData;
layout(location = 3) noperspective in float vVoxyDepth;

void main() {
  if (uBoundEnabled == 0) {
    discard;
  }
  vec2 boundPixel = gl_FragCoord.xy * (uBoundSize / max(uTargetSize, vec2(1.0)));
  ivec2 texel = ivec2(clamp(floor(boundPixel), vec2(0.0), uBoundSize - vec2(1.0)));
  float bound = texelFetch(uBoundDepthTex, texel, 0).r;
  const float BOUND_EPSILON = 0.00001;
  if (vVoxyDepth > bound - BOUND_EPSILON) {
    discard;
  }

  uint flags = vData.x;
  uint face = (flags >> 16u) & 7u;
  uint modelId = vData.z & 0xffffu;
  vec2 tile = floor(vUv);
  vec2 quadSize = vec2(float((flags >> 8u) & 0xfu), float((flags >> 12u) & 0xfu));
  if (any(lessThan(tile, vec2(0.0))) || any(greaterThan(tile, quadSize))) {
    discard;
  }
  vec2 uvFrac = vUv - tile;
  vec2 modelBase = vec2(float(modelId & 0xffu), float((modelId >> 8u) & 0xffu)) / 256.0;
  vec2 faceBase = vec2(float(face >> 1u), float(face & 1u)) / (vec2(3.0, 2.0) * 256.0);
  vec2 texPos = modelBase + faceBase + uvFrac / (vec2(3.0, 2.0) * 256.0);
  if (textureLod(uBlockModelAtlas, texPos, 0.0).a == 0.0) {
    discard;
  }
}
