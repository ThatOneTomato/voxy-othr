#version 410 core

#define FRAGMENT_SHADER
#define PATCHED_SHADER
#define TRANSLUCENT

uniform sampler2D uBlockModelAtlas;

layout(location = 0) in vec2 vUv;
layout(location = 2) flat in uvec4 vData;

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

void voxy_emitFragment(VoxyFragmentParameters parameters);

//__VOXY_IRIS_PATCH__

vec4 unpackRGBA(uint colour) {
  return vec4((uvec4(colour) >> uvec4(24, 16, 8, 0)) & uvec4(0xffu)) / 255.0;
}

vec2 lightmapUv(uint lightRaw) {
  vec2 uv = vec2(float(lightRaw & 0xf0u), float((lightRaw & 0x0fu) << 4u));
  return clamp(uv / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
}

void main() {
  uint flags = vData.x;
  uint face = (flags >> 16u) & 7u;
  uint modelId = vData.z & 0xffffu;
  uint lightRaw = (vData.z >> 16u) & 0xffu;
  vec2 tile = floor(vUv);
  vec2 quadSize = vec2(float((flags >> 8u) & 0xfu), float((flags >> 12u) & 0xfu));
  if (any(lessThan(tile, vec2(0.0))) || any(greaterThan(tile, quadSize))) {
    discard;
  }

  vec2 uvFrac = vUv - tile;
  vec2 modelBase = vec2(float(modelId & 0xffu), float((modelId >> 8u) & 0xffu)) / 256.0;
  vec2 faceBase = vec2(float(face >> 1u), float(face & 1u)) / (vec2(3.0, 2.0) * 256.0);
  vec2 texPos = modelBase + faceBase + uvFrac / (vec2(3.0, 2.0) * 256.0);
  vec2 uvSmol = vUv / (vec2(3.0, 2.0) * 256.0);
  vec4 colour = textureGrad(uBlockModelAtlas, texPos, dFdx(uvSmol), dFdy(uvSmol));
  vec4 topMip = textureLod(uBlockModelAtlas, texPos, 0.0);
  if (topMip.a == 0.0) {
    discard;
  }

  uint tintState = (flags >> 2u) & 3u;
  vec4 tint = vec4(1.0);
  if (tintState == 2u) {
    if (vData.y != 0xffffffffu) {
      tint = unpackRGBA(vData.y).yzwx;
    }
  } else if (tintState == 1u) {
    vec4 tintTest = texture(uBlockModelAtlas, texPos, -2.0);
    if (abs(tintTest.r - tintTest.g) < 0.02 && abs(tintTest.g - tintTest.b) < 0.02) {
      if (vData.y != 0xffffffffu) {
        tint = unpackRGBA(vData.y).yzwx;
      }
    }
  }

  uint winding = uint(gl_FrontFacing != ((face >> 1u) != 0u));
  face ^= uint((face & 1u) != winding);
  voxyQuadFlags = flags;
  voxy_emitFragment(
      VoxyFragmentParameters(
          colour, tile, texPos, int(face), modelId, lightmapUv(lightRaw), tint, vData.w));
}
