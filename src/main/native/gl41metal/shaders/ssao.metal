#include <metal_stdlib>
using namespace metal;

// ============================== Distant SSAO (vanilla path) ==============================
//
// Port of GL46's voxy:post/ssao.comp BETTER_SSAO variant (photon's horizon-based SSAO, used
// with permission upstream). GL46 runs it as a GL4.3 compute pass over the composited lit
// colour; Apple GL4.1 has no compute shaders, so gl41metal computes the AO factor natively
// right after the opaque raster and BAKES it into the shared gbuffer2.w spare bits
// (ao8 << 12, see quad_raster.metal's QuadFragmentOut ABI). The GL bridge folds the decoded
// factor into the distant albedo for BOTH the vanilla and Iris strict composites - the same
// place near terrain carries vanilla's per-vertex AO (vertex colour) into shader-pack
// gbuffers - so distant grass-side shading matches near terrain with or without a pack.
//
// Written as a full-screen fragment pass with a framebuffer-fetch read-modify-write of
// gbuffer2 (the same tile-memory pattern the translucent raster already relies on) instead
// of a compute kernel, so no Tier-2 read-write texture support is needed and the pass is
// free of an extra gbuffer2 round-trip on TBDR.
//
// Known deviations from ssao.comp, both boundary-only and accepted:
//  * The vanilla near-scene depth lives on the GL side, so LOD pixels next to the near
//    boundary miss occlusion from vanilla geometry (GL46 samples sourceDepthTex there).
//  * The per-sample "occluder hasAO" meta check is dropped (gbuffer2 is this pass's own
//    attachment and cannot be sampled at other pixels); every opaque LOD sample occludes.

constant float SSAO_TAU = 6.2831853f;
constant float SSAO_PHI1 = 1.6180339887f;  // Golden ratio, matches ssao.comp's r1()
constant float SSAO_MAX_RADIUS_SCREEN = 0.05f;
constant float SSAO_RADIUS = 1.0f;

// Must match SsaoUniformHost in gl41metal_abi.h (column-major matrices, JOML layout, exactly
// like SceneUniform's mvps). proj/invProj are the VOXY projection alone (not the MVP): the
// algorithm reconstructs and occludes in view space; modelView's upper 3x3 rotates the
// world-space face normal into view space.
struct SsaoUniform {
  float4x4 proj;
  float4x4 invProj;
  float4x4 modelView;
  uint4 params;  // .x = sample steps (>0), .yzw unused
};

struct SsaoVertexOut {
  float4 position [[position]];
};

// Standard full-screen triangle: (-1,-1) (3,-1) (-1,3).
vertex SsaoVertexOut voxy_ssao_vertex(uint vid [[vertex_id]]) {
  SsaoVertexOut out;
  float2 xy = float2(float((vid << 1u) & 2u), float(vid & 2u)) * 2.0f - 1.0f;
  out.position = float4(xy, 0.0f, 1.0f);
  return out;
}

// ssao.comp face2norm: face pairs are (-Y,+Y,-Z,+Z,-X,+X); even = negative direction.
static inline float3 ssao_face_to_normal(uint face) {
  float sign = float(face & 1u) * 2.0f - 1.0f;
  uint axis = face >> 1u;
  return float3(axis == 2u ? sign : 0.0f, axis == 0u ? sign : 0.0f, axis == 1u ? sign : 0.0f);
}

// uv is Metal texture space (y down, origin top-left); the projection is GL-convention
// (NDC y up, z in [-1,1]) because the raster's metalClip only remapped z = (glZ+w)/2, i.e.
// stored depth d = glNdcZ*0.5+0.5. Undo both here; ssao_project applies them again, so the
// pass is internally consistent in y-down uv space (only uv DIFFERENCES feed the sample
// matrix, exactly like ssao.comp works in y-up GL uv space).
static inline float3 ssao_view_pos(constant SsaoUniform& u, float2 uv, float depth) {
  float2 ndc = float2(uv.x * 2.0f - 1.0f, 1.0f - uv.y * 2.0f);
  float4 view = u.invProj * float4(ndc, depth * 2.0f - 1.0f, 1.0f);
  return view.xyz / view.w;
}

// ssao.comp projProj: view -> [0,1] uv (y flipped back to Metal orientation).
static inline float2 ssao_project_uv(constant SsaoUniform& u, float3 viewPos) {
  float4 clip = u.proj * float4(viewPos, 1.0f);
  float2 ndc = clip.xy / clip.w;
  return float2((ndc.x + 1.0f) * 0.5f, (1.0f - ndc.y) * 0.5f);
}

static inline float3x3 ssao_tbn(float3 normal) {
  float3 tangent = normal.y == 1.0f
      ? float3(1.0f, 0.0f, 0.0f)
      : normalize(cross(float3(0.0f, 1.0f, 0.0f), normal));
  float3 bitangent = normalize(cross(tangent, normal));
  return float3x3(tangent, bitangent, normal);
}

static inline float2 ssao_clamp_length(float2 a, float minLen, float maxLen) {
  return normalize(a) * clamp(length(a), minLen, maxLen);
}

fragment float4 voxy_ssao_fragment(
    SsaoVertexOut in [[stage_in]],
    float4 prevG2 [[color(0)]],
    depth2d<float> depthTex [[texture(0)]],
    constant SsaoUniform& u [[buffer(0)]]) {
  // gbuffer2.w = (ao8<<12)|(face<<9)|(flags<<1)|coverage, stored as an exact float value.
  uint faceFlagsCov = uint(max(prevG2.w, 0.0f));
  bool covered = (faceFlagsCov & 1u) != 0u;
  // flags bit6 = isShaded/hasAO (packed bit 7): unshaded models skip AO, like ssao.comp.
  bool hasAO = ((faceFlagsCov >> 7u) & 1u) != 0u;
  if (!covered || !hasAO) {
    return prevG2;
  }

  constexpr sampler depthSampler(coord::normalized, address::clamp_to_edge, filter::nearest);
  float2 size = float2(float(depthTex.get_width()), float(depthTex.get_height()));
  float2 uv = in.position.xy / size;
  float depth = depthTex.sample(depthSampler, uv);
  if (depth <= 0.0f || depth >= 1.0f) {
    return prevG2;
  }

  uint face = (faceFlagsCov >> 9u) & 7u;
  float3 positionView = ssao_view_pos(u, uv, depth);
  float3 viewNormal = normalize(float3x3(
      u.modelView[0].xyz, u.modelView[1].xyz, u.modelView[2].xyz) * ssao_face_to_normal(face));
  float3x3 tbn = ssao_tbn(viewNormal);

  float2x2 sampleMatrix = float2x2(
      ssao_clamp_length(
          ssao_project_uv(u, positionView + tbn[0]) - uv, 0.0f, SSAO_MAX_RADIUS_SCREEN),
      ssao_clamp_length(
          ssao_project_uv(u, positionView + tbn[1]) - uv, 0.0f, SSAO_MAX_RADIUS_SCREEN));
  sampleMatrix = SSAO_RADIUS * sampleMatrix;

  uint steps = max(u.params.x, 1u);
  float invSteps = 1.0f / float(steps);
  float ao = 0.0f;
  for (uint i = 0u; i < steps; i++) {
    // ssao.comp get_ssao_sample_offset with the fixed (0.5, 0.5) dither GL46 compiles into a
    // const array: r = sqrt(fract(i/phi1 + 0.5)), theta = (i+0.5)/steps * tau.
    float a = (float(i) + 0.5f) * invSteps;
    float r = sqrt(fract(float(i) * (1.0f / SSAO_PHI1) + 0.5f));
    float sinTheta;
    float cosTheta;
    sinTheta = sincos(a * SSAO_TAU, cosTheta);
    float2 sampleUv = clamp(uv + sampleMatrix * (r * float2(cosTheta, sinTheta)),
        float2(0.0f), float2(1.0f));
    sampleUv = (floor(sampleUv * size) + 0.5f) / size;

    float sampleDepth = depthTex.sample(depthSampler, sampleUv);
    // 1.0 = cleared/far (or the near-exclusion region where the vanilla scene renders; its
    // depth lives GL-side, see the header note). Same-depth skip mirrors ssao.comp.
    if (sampleDepth >= 1.0f || sampleDepth <= 0.0f || sampleDepth == depth) {
      continue;
    }
    float3 offsetView = ssao_view_pos(u, sampleUv, sampleDepth) - positionView;
    float slen = dot(offsetView, offsetView);
    if (slen <= 0.0f) {
      continue;
    }
    float rlen = rsqrt(slen);
    float cosAngle = saturate(dot(offsetView, viewNormal) * rlen);
    float distanceFalloff = 1.0f / (1.0f + (1.0f / rlen) * (1.0f / SSAO_RADIUS));
    ao += cosAngle * distanceFalloff;
  }

  float aoFactor = pow(saturate(1.0f - ao * invSteps), 2.5f);
  // 1..255 so 0 keeps meaning "no AO data" on the GL side (pre-SSAO or SSAO-disabled frames).
  uint ao8 = clamp(uint(aoFactor * 255.0f + 0.5f), 1u, 255u);
  float newW = float((faceFlagsCov & 0xFFFu) | (ao8 << 12u));
  return float4(prevG2.xyz, newW);
}
