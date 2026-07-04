#version 410 core

uniform sampler2DRect uDistantDepthTex;
uniform sampler2D uBoundDepthTex;
uniform vec2 uBoundSize;
uniform vec2 uSharedSize;
uniform vec2 uTargetSize;

vec2 sharedPixelForTarget(vec2 targetPixel) {
  vec2 shared = targetPixel * (uSharedSize / max(uTargetSize, vec2(1.0)));
  return vec2(shared.x, uSharedSize.y - shared.y);
}

void main() {
  vec2 targetPixel = gl_FragCoord.xy;
  // The distant carrier's .x is the fragment's Voxy-NDC depth (g.depth), the SAME space the
  // bound was rasterized in, so the compare is direct - no reprojection (matches the vanilla
  // in-shader arm and voxy-fabric quads.frag).
  float gd = clamp(texture(uDistantDepthTex, sharedPixelForTarget(targetPixel)).x, 0.0, 1.0);
  if (gd <= 0.0 || gd >= 1.0) {
    // No distant coverage here: leave the (near-mask) stencil untouched.
    discard;
  }
  vec2 boundPixel = targetPixel * (uBoundSize / max(uTargetSize, vec2(1.0)));
  ivec2 texel = ivec2(clamp(floor(boundPixel), vec2(0.0), uBoundSize - vec2(1.0)));
  float bound = texelFetch(uBoundDepthTex, texel, 0).r;
  const float BOUND_EPSILON = 0.00001;
  // Voxy NDC is non-reverse: inside the loaded volume == nearer than the boundary == smaller.
  if (gd > bound - BOUND_EPSILON) {
    // Distant fragment is beyond the loaded volume: keep the stencil as the near mask left it.
    discard;
  }
  // Inside the loaded volume: fall through so GL_REPLACE writes stencil := 1 (blocks distant).
}
