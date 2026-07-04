#version 410 core

uniform sampler2DRect uTgbuffer0Tex;  // .x albedoPacked .y lightPacked .z tintPacked .w face/flags/coverage
uniform sampler2DRect uTgbuffer1Tex;  // .x ndc depth .y alpha .z customId&0xFFFFFF .w customId>>24
uniform vec2 uSharedSize;
uniform vec2 uTargetSize;
uniform mat4 uInvVoxyMvp;
uniform mat4 uVanillaMvp;

uint voxyQuadFlags = 0u;

struct VoxyFragmentParameters {
  vec4 sampledColour;
  vec2 tile;
  vec2 uv;
  int face;
  uint modelId;
  vec2 lightMap;
  vec4 tinting;
  uint customId;
};

uint decodePackedUint(float value) {
  return uint(max(value, 0.0));
}

vec3 unpackRgb8(uint packed) {
  return vec3(float((packed >> 16u) & 255u), float((packed >> 8u) & 255u), float(packed & 255u))
      / 255.0;
}

struct VoxyTranslucentTexel {
  float coverage;
  vec4 colour;
  vec2 uv;
  vec2 tile;
  float depth;
  float alpha;
  vec2 lightMap;
  vec4 tint;
  uint modelId;
  uint customId;
  uint face;
  uint flags;
};

VoxyTranslucentTexel sampleVoxyTranslucent(vec2 sharedPixel) {
  vec4 t0 = texture(uTgbuffer0Tex, sharedPixel);
  vec4 t1 = texture(uTgbuffer1Tex, sharedPixel);
  VoxyTranslucentTexel t;
  t.colour = vec4(unpackRgb8(decodePackedUint(t0.x)), 1.0);
  uint lightPacked = decodePackedUint(t0.y);
  t.lightMap = vec2(float((lightPacked >> 12u) & 4095u), float(lightPacked & 4095u)) / 4095.0;
  t.tint = vec4(unpackRgb8(decodePackedUint(t0.z)), 1.0);
  uint faceFlagsCoverage = decodePackedUint(t0.w);
  t.coverage = float(faceFlagsCoverage & 1u);
  t.flags = (faceFlagsCoverage >> 1u) & 255u;
  t.face = faceFlagsCoverage >> 9u;
  t.depth = clamp(t1.x, 0.0, 1.0);
  t.alpha = clamp(t1.y, 0.0, 1.0);
  t.customId = decodePackedUint(t1.z) | (decodePackedUint(t1.w) << 24u);
  // Not stored for translucents (the opaque ABI carries these; the translucent ABI stores the
  // resolved albedo instead). Report 0 so a pack patch that touches them stays well-defined.
  t.uv = vec2(0.0);
  t.tile = vec2(0.0);
  t.modelId = 0u;
  return t;
}

vec3 rev3d(vec3 clip) {
  vec4 view = uInvVoxyMvp * vec4(clip * 2.0 - 1.0, 1.0);
  return view.xyz / view.w;
}

float projectDepth(vec3 pos) {
  vec4 view = uVanillaMvp * vec4(pos, 1.0);
  float depth = view.z / view.w;
  depth = min(1.0 - 2.0 / 16777215.0, depth);
  depth = depth * 0.5 + 0.5;
  depth = gl_DepthRange.diff * depth + gl_DepthRange.near;
  return clamp(depth, 0.0, 1.0);
}

vec2 sharedPixelForTarget(vec2 targetPixel) {
  vec2 shared = targetPixel * (uSharedSize / max(uTargetSize, vec2(1.0)));
  return vec2(shared.x, uSharedSize.y - shared.y);
}

vec4 voxy_OverrideFragCoord;
