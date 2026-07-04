layout(location = 0) out vec4 voxyVanillaColor;
uniform vec4 uFogParams;
uniform vec4 uFogColor;
uniform int uFogShape;

float voxyFogDistance(int shape, vec3 pos) {
  if (shape == 1) {
    return max(length(pos.xz), abs(pos.y));
  }
  return length(pos);
}

vec3 voxyApplyFog(vec3 color, vec2 targetPixel, float voxyDepth) {
  if (uFogParams.z <= 0.0) {
    return color;
  }
  vec3 pos = rev3d(vec3(targetPixel / max(uTargetSize, vec2(1.0)), voxyDepth));
  float fogLerp = smoothstep(uFogParams.x, uFogParams.y, voxyFogDistance(uFogShape, pos));
  if (uFogParams.w > 0.0) {
    fogLerp = (exp(uFogParams.w * fogLerp) - 1.0) / (exp(uFogParams.w) - 1.0);
  }
  return mix(color, uFogColor.rgb, clamp(fogLerp * uFogParams.z, 0.0, 1.0));
}

float voxyVanillaFaceTint(bool shaded, uint face) {
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

void voxy_emitFragment(VoxyFragmentParameters parameters) {
  vec4 light = texture(uLightmapTex, parameters.lightMap);
  vec4 color = parameters.sampledColour * parameters.tinting * light;
  bool shaded = ((voxyQuadFlags >> 6u) & 1u) != 0u;
  color.rgb *= voxyVanillaFaceTint(shaded, uint(parameters.face));
  color.rgb =
      voxyApplyFog(color.rgb, voxy_OverrideFragCoord.xy, voxy_OverrideFragCoord.z);
  voxyVanillaColor = vec4(color.rgb, 1.0);
}
