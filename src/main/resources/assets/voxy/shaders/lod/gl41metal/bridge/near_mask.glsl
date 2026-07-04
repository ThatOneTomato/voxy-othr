
uniform sampler2D uSourceDepthTex;
uniform vec2 uSourceDepthSize;
uniform int uReverseDepth;
uniform int uUseManualDepthMask;

bool isHiddenByNearDepth(vec2 targetPixel, float voxyDepth) {
  if (uUseManualDepthMask == 0) {
    return false;
  }
  vec2 sourcePixel = targetPixel * (uSourceDepthSize / max(uTargetSize, vec2(1.0)));
  ivec2 texel = ivec2(clamp(floor(sourcePixel), vec2(0.0), uSourceDepthSize - vec2(1.0)));
  float nearDepth = texelFetch(uSourceDepthTex, texel, 0).r;
  const float DEPTH_EPSILON = 0.00001;
  // Near depth only hides Voxy when Voxy is not meaningfully closer. The epsilon keeps
  // boundary LOD depth noise from fighting vanilla geometry without turning the depth
  // texture into a coarse coverage mask.
  if (uReverseDepth != 0) {
    if (nearDepth <= 0.000001) {
      return false;
    }
    return voxyDepth <= nearDepth + DEPTH_EPSILON;
  }
  if (nearDepth >= 0.999999) {
    return false;
  }
  return voxyDepth >= nearDepth - DEPTH_EPSILON;
}
