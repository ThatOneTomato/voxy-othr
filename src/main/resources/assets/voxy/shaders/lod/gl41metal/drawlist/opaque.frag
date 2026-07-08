#version 410 core

uniform sampler2D uBlockModelAtlas;
uniform sampler2D uLightmapTex;
uniform sampler2D uNearDepthTex;
uniform vec2 uNearDepthSize;
uniform int uUseNearDepthMask;
uniform int uReverseDepth;
uniform vec4 uFogParams;
uniform vec4 uFogColor;
uniform int uFogShape;

layout(location = 0) in vec2 vUv;
layout(location = 1) in vec3 vFogPos;
layout(location = 2) flat in uvec4 vData;

layout(location = 0) out vec4 outColor;

vec4 unpackRGBA(uint colour) {
  return vec4((uvec4(colour) >> uvec4(24, 16, 8, 0)) & uvec4(0xffu)) / 255.0;
}

vec2 lightmapUv(uint lightRaw) {
  vec2 uv = vec2(float(lightRaw & 0xf0u), float((lightRaw & 0x0fu) << 4u));
  return clamp(uv / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
}

float faceTint(bool shaded, uint face) {
  if (!shaded) {
    return 1.0;
  }
  if ((face >> 1u) == 1u) {
    return 0.8;
  }
  if ((face >> 1u) == 2u) {
    return 0.6;
  }
  if (face == 1u) {
    return 1.0;
  }
  return 0.5;
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
  if (hiddenByNearDepth(gl_FragCoord.z)) {
    discard;
  }
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
  vec2 atlasTexel = 0.5 / vec2(textureSize(uBlockModelAtlas, 0));
  vec2 faceExtent = 1.0 / (vec2(3.0, 2.0) * 256.0);
  texPos =
      clamp(
          texPos,
          modelBase + faceBase + atlasTexel,
          modelBase + faceBase + faceExtent - atlasTexel);
  vec2 uvSmol = vUv / (vec2(3.0, 2.0) * 256.0);
  vec4 colour = textureGrad(uBlockModelAtlas, texPos, dFdx(uvSmol), dFdy(uvSmol));

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

  uint shadeFace = face;
  shadeFace ^= uint(((shadeFace & 1u) != 0u) != (gl_FrontFacing != ((shadeFace >> 1u) != 0u)));
  vec4 light = texture(uLightmapTex, lightmapUv(lightRaw));
  bool shaded = ((flags >> 6u) & 1u) != 0u;
  colour.a = 1.0;
  colour *= tint * light;
  colour.rgb *= faceTint(shaded, shadeFace);
  colour.rgb = applyFog(colour.rgb, vFogPos);
  outColor = vec4(colour.rgb, 1.0);
}
