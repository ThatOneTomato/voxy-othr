
uniform sampler2D uBoundDepthTex;
uniform vec2 uBoundSize;
uniform int uBoundEnabled;

// voxyDepth is the distant fragment's Voxy-NDC depth (g.depth) - the SAME space the bound was
// rasterized in (drawMvp), so no reprojection is needed, exactly like voxy-fabric quads.frag
// comparing gl_FragCoord.z against depthBoundingBuffer. Voxy NDC is non-reverse (0 near .. 1
// far): a fragment INSIDE the loaded volume is NEARER than its far boundary, i.e. smaller.
bool isInsideLoadedBound(vec2 targetPixel, float voxyDepth) {
  if (uBoundEnabled == 0) {
    return false;
  }
  vec2 boundPixel = targetPixel * (uBoundSize / max(uTargetSize, vec2(1.0)));
  ivec2 texel = ivec2(clamp(floor(boundPixel), vec2(0.0), uBoundSize - vec2(1.0)));
  float bound = texelFetch(uBoundDepthTex, texel, 0).r;
  const float BOUND_EPSILON = 0.00001;
  return voxyDepth <= bound - BOUND_EPSILON;
}
