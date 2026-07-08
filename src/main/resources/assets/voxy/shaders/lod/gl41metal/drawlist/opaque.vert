#version 410 core

layout(location = 0) in vec4 aBaseAndLod;
layout(location = 1) in vec4 aSizeAndUv;
layout(location = 2) in uvec4 aData;

uniform mat4 uVoxyMvp;
uniform mat4 uVanillaMvp;
uniform float uEarthRadius;

layout(location = 0) out vec2 vUv;
layout(location = 1) out vec3 vFogPos;
layout(location = 2) flat out uvec4 vData;

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
  gl_Position = vec4(voxyClip.xy, vanillaNdcDepth * voxyClip.w, voxyClip.w);
  vUv = aSizeAndUv.zw + aSizeAndUv.xy * corner01;
  vFogPos = point;
  vData = aData;
}
