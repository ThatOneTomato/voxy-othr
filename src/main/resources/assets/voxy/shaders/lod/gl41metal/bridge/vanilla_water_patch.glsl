
layout(location = 0) out vec4 voxyWaterColor;
uniform sampler2DRect uTgbufferAccumTex;
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

float voxyWaterFaceTint(bool shaded, uint face) {
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
  vec4 frontColor = vec4(parameters.sampledColour.rgb, 1.0) * parameters.tinting * light;
  bool shaded = ((voxyQuadFlags >> 6u) & 1u) != 0u;
  float frontFaceShade = voxyWaterFaceTint(shaded, uint(parameters.face));
  frontColor.rgb *= frontFaceShade;
  float frontAlpha = parameters.sampledColour.a;

  vec2 sp = sharedPixelForTarget(gl_FragCoord.xy);
  vec4 accum = texture(uTgbufferAccumTex, sp);
  float bA = max(accum.a - frontAlpha, 0.0);

  if (bA <= 0.001) {
    vec3 foggedFront =
        voxyApplyFog(frontColor.rgb, voxy_OverrideFragCoord.xy, voxy_OverrideFragCoord.z);
    voxyWaterColor = vec4(foggedFront, frontAlpha);
    return;
  }

  vec3 frontPremul = parameters.sampledColour.rgb * parameters.tinting.rgb * frontFaceShade * frontAlpha;
  // accum.rgb is an order-independent SUM of premultiplied flat colours (Metal blends the
  // accum target additively because within-section draw order is arbitrary). Subtracting the
  // front layer leaves sum(behind flats); attenuating once by (1 - frontAlpha) is exact for
  // two layers in any order and mildly overestimates the deepest layers beyond that.
  vec3 bRgb = max(accum.rgb - frontPremul, vec3(0.0));
  vec3 behindLit = bRgb * light.rgb * (1.0 - frontAlpha);

  float combinedAlpha = frontAlpha + bA;
  vec3 combinedPremul = frontColor.rgb * frontAlpha + behindLit;
  vec3 combined =
      voxyApplyFog(
          combinedPremul / max(combinedAlpha, 0.001),
          voxy_OverrideFragCoord.xy,
          voxy_OverrideFragCoord.z);
  voxyWaterColor = vec4(combined, combinedAlpha);
}
