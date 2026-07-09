#version 410 core

layout(location = 0) in vec4 aBaseAndLod;
layout(location = 1) in vec4 aSizeAndUv;
layout(location = 2) in uvec4 aData;

uniform mat4 uVoxyMvp;
uniform mat4 uVanillaMvp;
uniform float uEarthRadius;
uniform sampler2D uLightmapTex;
uniform vec4 uFaceShade;
uniform float uFaceShadeX;
uniform int uUseVoxyDepth;

layout(location = 0) out vec2 vUv;
layout(location = 1) out vec3 vFogPos;
layout(location = 2) flat out uvec4 vData;
layout(location = 3) flat out uint vLightPacked;
layout(location = 4) noperspective out float vVanillaNdcDepth;

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

vec2 lightmapUv(uint lightRaw) {
  vec2 uv = vec2(float(lightRaw & 0xf0u), float((lightRaw & 0x0fu) << 4u));
  return clamp(uv / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
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

void main() {
  uint corner = uint(gl_VertexID) & 3u;
  vec2 corner01 = vec2(float((corner >> 1u) & 1u), float(corner & 1u));
  uint face = (aData.x >> 16u) & 7u;
  vec3 point =
      aBaseAndLod.xyz +
      swizzleAxis(face >> 1u, vec3(aSizeAndUv.xy * corner01 * aBaseAndLod.w, 0.0));
  point = applyWorldCurvature(point);
  vec4 voxyClip = uVoxyMvp * vec4(point, 1.0);
  vec4 vanillaClip = uVanillaMvp * vec4(point, 1.0);
  float vanillaNdcDepth = vanillaClip.z / vanillaClip.w;
  vanillaNdcDepth = clamp(vanillaNdcDepth, -1.0, 1.0 - 2.0 / 16777215.0);
  float voxyNdcDepth = voxyClip.z / voxyClip.w;
  voxyNdcDepth = clamp(voxyNdcDepth, -1.0, 1.0 - 2.0 / 16777215.0);
  float drawNdcDepth = uUseVoxyDepth != 0 ? voxyNdcDepth : vanillaNdcDepth;
  gl_Position = vec4(voxyClip.xy, drawNdcDepth * voxyClip.w, voxyClip.w);
  vUv = aSizeAndUv.zw + aSizeAndUv.xy * corner01;
  vFogPos = point;
  vData = aData;
  vVanillaNdcDepth = vanillaNdcDepth;
  vLightPacked = 0u;
  if (corner == 1u) {
    uint lightRaw = (aData.z >> 16u) & 0xffu;
    vec4 light = texture(uLightmapTex, lightmapUv(lightRaw));
    light.a = 0.0;
    bool shaded = ((aData.x >> 6u) & 1u) != 0u;
    light.rgb *= faceTint(shaded, face);
    vLightPacked = packRGBA(light);
  }
}
