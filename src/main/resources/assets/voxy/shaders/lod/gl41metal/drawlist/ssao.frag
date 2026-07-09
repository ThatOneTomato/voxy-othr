#version 410 core

uniform sampler2D uColourTex;
uniform sampler2D uDepthTex;
uniform mat4 uProjection;
uniform mat4 uInvProjection;
uniform mat4 uModelView;
uniform int uSteps;

in vec2 UV;
layout(location = 0) out vec4 outColor;

const float SSAO_TAU = 6.2831853;
const float SSAO_PHI1 = 1.6180339887;
const float SSAO_MAX_RADIUS_SCREEN = 0.05;
const float SSAO_RADIUS = 1.0;

vec3 viewPos(vec2 uv, float depth) {
  vec4 view = uInvProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
  return view.xyz / view.w;
}

vec2 projectUv(vec3 viewPos_) {
  vec4 clip = uProjection * vec4(viewPos_, 1.0);
  return clip.xy / clip.w * 0.5 + 0.5;
}

vec3 faceToNormal(uint face) {
  float sign_ = float(face & 1u) * 2.0 - 1.0;
  uint axis = face >> 1u;
  return vec3(axis == 2u ? sign_ : 0.0, axis == 0u ? sign_ : 0.0, axis == 1u ? sign_ : 0.0);
}

mat3 tbnMatrix(vec3 normal) {
  vec3 tangent =
      abs(normal.y) > 0.999
          ? vec3(1.0, 0.0, 0.0)
          : normalize(cross(vec3(0.0, 1.0, 0.0), normal));
  vec3 bitangent = normalize(cross(tangent, normal));
  return mat3(tangent, bitangent, normal);
}

vec2 clampLength(vec2 value, float minLen, float maxLen) {
  float len = length(value);
  if (len <= 0.000001) {
    return vec2(0.0);
  }
  return value * (clamp(len, minLen, maxLen) / len);
}

void main() {
  vec4 colour = texture(uColourTex, UV);
  if (colour.a <= 0.0) {
    outColor = vec4(0.0);
    return;
  }

  uint metadata = uint(colour.a * 255.0 + 0.5);
  bool hasAo = (metadata & (1u << 6u)) != 0u;
  float depth = texture(uDepthTex, UV).r;
  if (!hasAo || depth <= 0.0 || depth >= 1.0 || uSteps <= 0) {
    outColor = vec4(colour.rgb, 1.0);
    return;
  }

  uint face = metadata & 7u;
  vec3 positionView = viewPos(UV, depth);
  vec3 viewNormal = normalize(mat3(uModelView) * faceToNormal(face));
  mat3 tbn = tbnMatrix(viewNormal);
  mat2 sampleMatrix =
      SSAO_RADIUS *
      mat2(
          clampLength(projectUv(positionView + tbn[0]) - UV, 0.0, SSAO_MAX_RADIUS_SCREEN),
          clampLength(projectUv(positionView + tbn[1]) - UV, 0.0, SSAO_MAX_RADIUS_SCREEN));

  float invSteps = 1.0 / float(uSteps);
  float ao = 0.0;
  ivec2 texSize = textureSize(uDepthTex, 0);
  vec2 size = vec2(texSize);
  for (int i = 0; i < uSteps; i++) {
    float a = (float(i) + 0.5) * invSteps;
    float r = sqrt(fract(float(i) * (1.0 / SSAO_PHI1) + 0.5));
    float theta = a * SSAO_TAU;
    vec2 sampleUv =
        clamp(UV + sampleMatrix * (r * vec2(cos(theta), sin(theta))), vec2(0.0), vec2(1.0));
    sampleUv = (floor(sampleUv * size) + 0.5) / size;

    float sampleDepth = texture(uDepthTex, sampleUv).r;
    if (sampleDepth <= 0.0 || sampleDepth >= 1.0 || sampleDepth == depth) {
      continue;
    }
    vec4 sampleColour = texture(uColourTex, sampleUv);
    if (sampleColour.a > 0.0) {
      uint sampleMetadata = uint(sampleColour.a * 255.0 + 0.5);
      if ((sampleMetadata & (1u << 6u)) == 0u) {
        continue;
      }
    }
    vec3 offsetView = viewPos(sampleUv, sampleDepth) - positionView;
    float lenSquared = dot(offsetView, offsetView);
    if (lenSquared <= 0.0) {
      continue;
    }
    float invLen = inversesqrt(lenSquared);
    float cosAngle = clamp(dot(offsetView, viewNormal) * invLen, 0.0, 1.0);
    float distanceFalloff = 1.0 / (1.0 + (1.0 / invLen) * (1.0 / SSAO_RADIUS));
    ao += cosAngle * distanceFalloff;
  }

  float aoFactor = pow(clamp(1.0 - ao * invSteps, 0.0, 1.0), 2.5);
  outColor = vec4(colour.rgb * aoFactor, 1.0);
}
