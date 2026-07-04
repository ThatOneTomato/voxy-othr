#version 410 core

uniform sampler2DRect uTgbuffer0Tex;
uniform sampler2DRect uTgbuffer1Tex;
uniform sampler2DRect uTgbufferAccumTex;
uniform sampler2D uLightmapTex;
uniform vec2 uSharedSize;
uniform vec2 uTargetSize;

layout(location = 0) out vec4 behindColour;

uint decodePackedUint(float f) {
  return uint(f + 0.5);
}

vec3 unpackRgb8(uint packed) {
  return vec3(
      float((packed >> 16u) & 0xFFu) / 255.0,
      float((packed >> 8u) & 0xFFu) / 255.0,
      float(packed & 0xFFu) / 255.0);
}

vec2 sharedPixelForTarget(vec2 targetPixel) {
  vec2 shared = targetPixel * (uSharedSize / max(uTargetSize, vec2(1.0)));
  return vec2(shared.x, uSharedSize.y - shared.y);
}

float faceTint(uint face) {
  if ((face >> 1u) == 1u) return 0.8;
  if ((face >> 1u) == 2u) return 0.6;
  if (face == 1u) return 1.0;
  return 0.5;
}

void main() {
  vec2 sp = sharedPixelForTarget(gl_FragCoord.xy);
  vec4 accum = texture(uTgbufferAccumTex, sp);
  if (accum.a <= 0.0) discard;

  vec4 t0 = texture(uTgbuffer0Tex, sp);
  vec4 t1 = texture(uTgbuffer1Tex, sp);

  float frontAlpha = t1.y;
  float bA = max(accum.a - frontAlpha, 0.0);
  if (bA <= 0.001) discard;

  float depth = t1.x;
  if (depth <= 0.0 || depth >= 1.0) discard;
  gl_FragDepth = depth;

  // Decode lightmap UV from tgbuffer0.y (Metal already applied the 15/16 + 0.5/16 transform).
  uint lightPacked = decodePackedUint(t0.y);
  vec2 lightUv = vec2(float((lightPacked >> 12u) & 4095u), float(lightPacked & 4095u)) / 4095.0;
  vec3 light = texture(uLightmapTex, lightUv).rgb;

  // Metal bakes per-fragment face shade into tgbufferAccum, so the front subtraction must
  // also include the front surface's face shade. The remainder (bRgb) then already has
  // correct per-layer face shade; we only multiply by lightmap (approximated from front).
  uint faceFlagsCoverage = decodePackedUint(t0.w);
  uint face = faceFlagsCoverage >> 9u;
  uint flags = (faceFlagsCoverage >> 1u) & 255u;
  bool isShaded = ((flags >> 6u) & 1u) != 0u;
  float frontFaceShade = isShaded ? faceTint(face) : 1.0;

  vec3 frontAlbedo = unpackRgb8(decodePackedUint(t0.x));
  vec3 frontTint = unpackRgb8(decodePackedUint(t0.z));
  vec3 frontPremul = frontAlbedo * frontTint * frontFaceShade * frontAlpha;
  // accum.rgb is an order-independent SUM of premultiplied flat colours (Metal blends the
  // accum target additively because within-section draw order is arbitrary). Subtracting the
  // front layer leaves sum(behind flats); attenuating once by (1 - frontAlpha) is exact for
  // two layers in any order and mildly overestimates the deepest layers beyond that.
  vec3 bRgb = max(accum.rgb - frontPremul, vec3(0.0));
  behindColour = vec4(bRgb * light * (1.0 - frontAlpha), bA);
}
