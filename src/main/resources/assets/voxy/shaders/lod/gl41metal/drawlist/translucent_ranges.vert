#version 410 core

uniform mat4 uVoxyMvp;
uniform mat4 uVanillaMvp;
uniform float uEarthRadius;
uniform ivec3 uBaseSectionFrame;
uniform usamplerBuffer uGeometryQuads;
uniform usamplerBuffer uSectionMeta;
uniform usamplerBuffer uModelBuffer;
uniform usamplerBuffer uModelColours;
uniform usamplerBuffer uQuadSectionIds;
uniform sampler2D uLightmapTex;
uniform vec4 uFaceShade;
uniform float uFaceShadeX;

layout(location = 0) out vec2 vUv;
layout(location = 1) out vec3 vFogPos;
layout(location = 2) flat out uvec4 vData;
layout(location = 3) noperspective out float vVoxyDepth;
layout(location = 4) flat out uint vLightPacked;

const uint MODEL_COUNT = 65536u;

uint extractBits(uvec2 data, uint amount, uint shift) {
  uint mask = amount >= 32u ? 0xffffffffu : ((1u << amount) - 1u);
  if (shift >= 32u) {
    return (data.y >> (shift - 32u)) & mask;
  }
  uint value = data.x >> shift;
  if (shift + amount > 32u) {
    value |= data.y << (32u - shift);
  }
  return value & mask;
}

uint extractFace(uvec2 quad) {
  return extractBits(quad, 3u, 0u);
}

uvec2 extractSize(uvec2 quad) {
  return uvec2(extractBits(quad, 4u, 3u), extractBits(quad, 4u, 7u)) + uvec2(1u);
}

uvec3 extractPos(uvec2 quad) {
  return uvec3(
      extractBits(quad, 5u, 21u),
      extractBits(quad, 5u, 16u),
      extractBits(quad, 5u, 11u));
}

uint extractModelId(uvec2 quad) {
  return extractBits(quad, 16u, 26u);
}

uint extractBiomeId(uvec2 quad) {
  return extractBits(quad, 9u, 46u);
}

uint extractLightId(uvec2 quad) {
  return extractBits(quad, 8u, 55u);
}

vec2 lightmapUv(uint lightRaw) {
  vec2 uv = vec2(float(lightRaw & 0xf0u), float((lightRaw & 0x0fu) << 4u));
  return clamp(uv / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
}

ivec3 extractSectionPos(uvec4 metaA) {
  int y = (int(metaA.x) << 4) >> 24;
  int x = (int(metaA.y) << 4) >> 8;
  int z = int((metaA.x & ((1u << 20u) - 1u)) << 4u);
  z |= int(metaA.y >> 28u);
  z <<= 8;
  z >>= 8;
  return ivec3(x, y, z);
}

vec4 faceSize(uint faceData) {
  vec4 size =
      vec4(
          float(faceData & 0xfu),
          float((faceData >> 4u) & 0xfu),
          float((faceData >> 8u) & 0xfu),
          float((faceData >> 12u) & 0xfu)) /
          16.0 +
      vec4(0.0, 1.0 / 16.0, 0.0, 1.0 / 16.0);
  size.xz -= vec2(0.00005);
  size.yw -= size.xz;
  return size;
}

float faceIndentation(uint faceData) {
  uint enc = (faceData >> 16u) & 63u;
  enc += uint(enc == 63u);
  return float(enc) / 64.0;
}

uint modelFaceData(uint face, uvec4 model0, uvec4 model1) {
  if (face == 0u) {
    return model0.x;
  }
  if (face == 1u) {
    return model0.y;
  }
  if (face == 2u) {
    return model0.z;
  }
  if (face == 3u) {
    return model0.w;
  }
  if (face == 4u) {
    return model1.x;
  }
  return model1.y;
}

vec3 swizzleAxis(uint axis, vec3 data) {
  if (axis == 0u) {
    return data.xzy;
  }
  if (axis == 1u) {
    return data;
  }
  return data.zxy;
}

vec3 applyWorldCurvature(vec3 point) {
  if (uEarthRadius <= 0.0) {
    return point;
  }
  float localRadius = uEarthRadius + point.y;
  float horizontalDist = length(point.xz);
  float phi = horizontalDist / localRadius;
  float sinPhi = sin(phi);
  float cosPhi = cos(phi);
  point.y += (cosPhi - 1.0) * localRadius;
  if (phi > 0.0001) {
    point.xz = point.xz * sinPhi / phi;
  }
  return point;
}

float faceTint(bool shaded, uint face) {
  if (!shaded) {
    return uFaceShade.x;
  }
  if ((face >> 1u) == 1u) {
    return uFaceShade.w;
  }
  if ((face >> 1u) == 2u) {
    return uFaceShadeX;
  }
  if (face == 1u) {
    return uFaceShade.y;
  }
  return uFaceShade.z;
}

uint packRGBA(vec4 colour) {
  uvec4 packed = uvec4(colour * 255.0) << uvec4(24, 16, 8, 0);
  return packed.x | packed.y | packed.z | packed.w;
}

void emitSkipped() {
  gl_Position = vec4(2.0, 2.0, 1.0, 1.0);
  vUv = vec2(0.0);
  vFogPos = vec3(0.0);
  vData = uvec4(0u);
  vVoxyDepth = 1.0;
  vLightPacked = 0u;
}

void main() {
  uint globalVertex = uint(gl_VertexID);
  uint quadIndex = globalVertex >> 2u;
  uint corner = globalVertex & 3u;
  uvec2 quad = texelFetch(uGeometryQuads, int(quadIndex)).xy;
  if (all(equal(quad, uvec2(0u)))) {
    emitSkipped();
    return;
  }

  uint face = extractFace(quad);
  uint modelId = extractModelId(quad);
  if (modelId >= MODEL_COUNT) {
    emitSkipped();
    return;
  }

  uint sectionId = texelFetch(uQuadSectionIds, int(quadIndex)).x;
  uvec4 metaA = texelFetch(uSectionMeta, int(sectionId * 2u));

  uint modelTexel = modelId * 4u;
  uvec4 model0 = texelFetch(uModelBuffer, int(modelTexel));
  uvec4 model1 = texelFetch(uModelBuffer, int(modelTexel + 1u));
  uvec4 model2 = texelFetch(uModelBuffer, int(modelTexel + 2u));
  uint faceData = modelFaceData(face, model0, model1);
  if (faceData == 0xffffffffu) {
    emitSkipped();
    return;
  }

  uint flagsA = model1.z;
  uint colourTint = model1.w;
  uint customId = model2.x;
  uint detail = metaA.x >> 28u;
  int lodScaleInt = int(1u << detail);
  float lodScale = float(lodScaleInt);
  ivec3 sectionBaseSections = extractSectionPos(metaA) * lodScaleInt - uBaseSectionFrame;
  vec3 sectionWorldBase = vec3(sectionBaseSections) * 32.0;

  vec4 fSize = faceSize(faceData);
  uvec2 qSize = extractSize(quad);
  uvec3 qStartRaw = extractPos(quad);
  float depthOffset = faceIndentation(faceData);
  float depth = (face & 1u) != 0u ? (1.0 - depthOffset) : depthOffset;
  vec3 qStart =
      vec3(qStartRaw) + swizzleAxis(face >> 1u, vec3(fSize.x, fSize.z, depth));
  vec2 quadSizeAdd = fSize.yw + vec2(qSize) - 1.0;
  vec2 corner01 = vec2(float((corner >> 1u) & 1u), float(corner & 1u));
  vec3 point =
      qStart * lodScale +
      sectionWorldBase +
      swizzleAxis(face >> 1u, vec3(quadSizeAdd * corner01 * lodScale, 0.0));

  point = applyWorldCurvature(point);
  vec4 voxyClip = uVoxyMvp * vec4(point, 1.0);
  vec4 vanillaClip = uVanillaMvp * vec4(point, 1.0);
  float vanillaNdcDepth = vanillaClip.z / vanillaClip.w;
  vanillaNdcDepth = clamp(vanillaNdcDepth, -1.0, 1.0 - 2.0 / 16777215.0);
  gl_Position = vec4(voxyClip.xy, vanillaNdcDepth * voxyClip.w, voxyClip.w);
  vVoxyDepth = clamp(voxyClip.z / voxyClip.w * 0.5 + 0.5, 0.0, 1.0);

  uint tintState = (faceData >> 24u) & 3u;
  uint tintPacked = 0xffffffffu;
  if (tintState != 0u) {
    tintPacked = colourTint;
    if ((flagsA & 2u) != 0u) {
      uint colourIndex = colourTint + extractBiomeId(quad);
      if (colourIndex < MODEL_COUNT) {
        tintPacked = texelFetch(uModelColours, int(colourIndex)).x;
      }
    }
  }

  vUv = fSize.xz + quadSizeAdd * corner01;
  vFogPos = point;
  uint useDiscard =
      ((faceData >> 22u) & 1u) |
      ((any(greaterThan(qSize, uvec2(1u))) ? 1u : 0u) & ((faceData >> 23u) & 1u));
  vData =
      uvec4(
          (useDiscard | (tintState << 2u) | (((flagsA >> 3u) & 1u) << 6u) |
              ((qSize.x - 1u) << 8u) | ((qSize.y - 1u) << 12u)) |
              (face << 16u),
          tintPacked,
          modelId | (extractLightId(quad) << 16u),
          customId);
  vLightPacked = 0u;
  if (corner == 1u) {
    uint lightRaw = extractLightId(quad);
    vec4 light = texture(uLightmapTex, lightmapUv(lightRaw));
    bool shaded = ((flagsA >> 3u) & 1u) != 0u;
    light.rgb *= faceTint(shaded, face);
    vLightPacked = packRGBA(light);
  }
}
