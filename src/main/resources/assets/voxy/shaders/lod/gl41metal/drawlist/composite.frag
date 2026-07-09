#version 410 core

uniform sampler2D uColourTex;
uniform sampler2D uDepthTex;
uniform mat4 uInvSourceMvp;
uniform mat4 uTargetMvp;
uniform vec4 uFogParams;
uniform vec4 uFogColor;
uniform int uFogShape;

in vec2 UV;
layout(location = 0) out vec4 outColor;

float fogDistance(int shape, vec3 pos) {
  if (shape == 1) {
    return max(length(pos.xz), abs(pos.y));
  }
  return length(pos);
}

vec3 viewPos(vec2 uv, float depth) {
  vec4 view = uInvSourceMvp * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
  return view.xyz / view.w;
}

float targetDepth(vec3 pos) {
  vec4 clip = uTargetMvp * vec4(pos, 1.0);
  float depth = clip.z / clip.w;
  depth = min(1.0 - (2.0 / 16777215.0), depth);
  depth = depth * 0.5 + 0.5;
  return gl_DepthRange.diff * depth + gl_DepthRange.near;
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

void main() {
  vec4 colour = texture(uColourTex, UV);
  if (colour.a <= 0.0) {
    discard;
  }
  float depth = texture(uDepthTex, UV).r;
  if (depth <= 0.0 || depth >= 1.0) {
    discard;
  }
  vec3 pos = viewPos(UV, depth);
  gl_FragDepth = targetDepth(pos);
  colour.rgb = applyFog(colour.rgb, pos);
  outColor = vec4(colour.rgb, 1.0);
}
