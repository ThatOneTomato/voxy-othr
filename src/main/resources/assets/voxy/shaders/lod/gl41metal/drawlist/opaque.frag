#version 410 core

uniform sampler2D uBlockModelAtlas;
uniform sampler2D uNearDepthTex;
uniform vec2 uNearDepthSize;
uniform int uUseNearDepthMask;
uniform int uReverseDepth;
uniform int uSsaoMetadataMode;
uniform vec4 uFogParams;
uniform vec4 uFogColor;
uniform int uFogShape;

layout(location = 0) in vec2 vUv;
layout(location = 1) in vec3 vFogPos;
layout(location = 2) flat in uvec4 vData;
layout(location = 3) flat in uint vLightPacked;
layout(location = 4) noperspective in float vVanillaNdcDepth;

layout(location = 0) out vec4 outColor;

vec4 unpackRGBA(uint colour) {
  return vec4((uvec4(colour) >> uvec4(24, 16, 8, 0)) & uvec4(0xffu)) / 255.0;
}

float fogDistance(int shape, vec3 pos) {
  if (shape == 1) {
    return max(length(pos.xz), abs(pos.y));
  }
  return length(pos);
}

vec3 applyFog(vec3 color, vec3 pos) {
  if (uFogParams.z <= 0.0) {
    return color;
  }
  float fogLerp = smoothstep(uFogParams.x, uFogParams.y, fogDistance(uFogShape, pos));
  if (uFogParams.w > 0.0) {
    fogLerp = (exp(uFogParams.w * fogLerp) - 1.0) / (exp(uFogParams.w) - 1.0);
  }
  return mix(color, uFogColor.rgb, clamp(fogLerp * uFogParams.z, 0.0, 1.0));
}

vec2 clampToFace(vec2 texPos, vec2 faceOrigin, vec2 dx, vec2 dy) {
  vec2 atlasSize = vec2(textureSize(uBlockModelAtlas, 0));
  float rho = max(length(dx * atlasSize), length(dy * atlasSize));
  float mip = clamp(ceil(log2(max(rho, 1.0))), 0.0, 3.0);
  vec2 margin = exp2(mip) * 0.5 / atlasSize;
  vec2 faceExtent = 1.0 / (vec2(3.0, 2.0) * 256.0);
  vec2 low = faceOrigin + margin;
  vec2 high = faceOrigin + faceExtent - margin;
  return clamp(texPos, min(low, high), max(low, high));
}

bool hiddenByNearDepth(float vanillaDepth) {
  if (uUseNearDepthMask == 0) {
    return false;
  }
  ivec2 texel =
      ivec2(clamp(floor(gl_FragCoord.xy), vec2(0.0), uNearDepthSize - vec2(1.0)));
  float nearDepth = texelFetch(uNearDepthTex, texel, 0).r;
  const float DEPTH_EPSILON = 0.00001;
  if (uReverseDepth != 0) {
    if (nearDepth <= 0.000001) {
      return false;
    }
    return vanillaDepth <= nearDepth + DEPTH_EPSILON;
  }
  if (nearDepth >= 0.999999) {
    return false;
  }
  return vanillaDepth >= nearDepth - DEPTH_EPSILON;
}

void main() {
  float vanillaWindowDepth =
      gl_DepthRange.diff * (vVanillaNdcDepth * 0.5 + 0.5) + gl_DepthRange.near;
  if (hiddenByNearDepth(vanillaWindowDepth)) {
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
  vec2 faceOrigin = modelBase + faceBase;
  vec2 texPos = faceOrigin + uvFrac / (vec2(3.0, 2.0) * 256.0);
  vec2 uvSmol = vUv / (vec2(3.0, 2.0) * 256.0);
  vec2 dx = dFdx(uvSmol);
  vec2 dy = dFdy(uvSmol);
  texPos = clampToFace(texPos, faceOrigin, dx, dy);
  vec4 colour = textureGrad(uBlockModelAtlas, texPos, dx, dy);

  bool useDiscard = (flags & 1u) != 0u;
  uint tintState = (flags >> 2u) & 3u;
  vec4 topMip = textureLod(uBlockModelAtlas, texPos, 0.0);
  if (useDiscard && topMip.a <= 0.1) {
    discard;
  }

  vec4 tint = vec4(1.0);
  if (tintState == 2u) {
    if (vData.y != 0xffffffffu) {
      tint = unpackRGBA(vData.y).yzwx;
    }
  } else if (tintState == 1u) {
    if (abs(topMip.r - topMip.g) < 0.02 && abs(topMip.g - topMip.b) < 0.02) {
      if (vData.y != 0xffffffffu) {
        tint = unpackRGBA(vData.y).yzwx;
      }
    }
  }

  vec4 light = unpackRGBA(vLightPacked);
  colour.a = 1.0;
  colour *= tint * light;
  if (uSsaoMetadataMode != 0) {
    uint hasAo = (flags >> 6u) & 1u;
    uint encoded = face | (hasAo << 6u);
    outColor = vec4(colour.rgb, float(max(encoded, 1u)) / 255.0);
  } else {
    colour.rgb = applyFog(colour.rgb, vFogPos);
    outColor = vec4(colour.rgb, 1.0);
  }
}
