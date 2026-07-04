#include <metal_stdlib>
using namespace metal;

struct SceneUniform {
  float4x4 traversalMvp;
  float4x4 drawMvp;
  int4 baseSectionFrame;
  float4 cameraSubPos;
  float4 renderParams;
  uint4 queueSizes;
  uint4 viewport;
  uint4 rasterLimits;
};

struct SectionMeta {
  uint4 a;
  uint4 b;
};

struct WorkItem {
  uint meshId;
  uint quadBase;
  uint reserved;
  uint lodAndQuadCount;
};
struct QuadDrawRef {
  uint meshId;
  uint quadOffset;
  uint faceGroupAndLod;
  uint reserved;
};

struct BlockModel {
  uint faceData[6];
  uint flagsA;
  uint colourTint;
  uint customId;
  uint pad[7];
};

constant uint TRANSLUCENT_GROUP = 0u;
constant uint DOUBLE_SIDED_GROUP = 1u;

static inline uint extract_quad_bits(ulong v, uint bits, uint shift) {
  return uint((v >> shift) & ((1ul << bits) - 1ul));
}

static inline uint3 extract_quad_pos(ulong quad) {
  return uint3(extract_quad_bits(quad, 5u, 21u), extract_quad_bits(quad, 5u, 16u), extract_quad_bits(quad, 5u, 11u));
}

static inline uint2 extract_quad_size(ulong quad) {
  return uint2(extract_quad_bits(quad, 4u, 3u), extract_quad_bits(quad, 4u, 7u)) + 1u;
}

static inline uint extract_quad_face(ulong quad) {
  return extract_quad_bits(quad, 3u, 0u);
}

static inline uint extract_model_id(ulong quad) {
  return extract_quad_bits(quad, 16u, 26u);
}

static inline uint extract_light_id(ulong quad) {
  return extract_quad_bits(quad, 8u, 55u);
}

static inline uint extract_biome_id(ulong quad) {
  return extract_quad_bits(quad, 9u, 46u);
}

static inline int3 extract_section_pos(SectionMeta section) {
  int y = (int(section.a.x) << 4) >> 24;
  int x = (int(section.a.y) << 4) >> 8;
  int z = int((section.a.x & ((1u << 20u) - 1u)) << 4u);
  z |= int(section.a.y >> 28u);
  z <<= 8;
  z >>= 8;
  return int3(x, y, z);
}

static inline uint extract_detail(SectionMeta section) {
  return section.a.x >> 28u;
}

static inline float precompute_light_packed(uint lightRaw) {
  float2 lightBase = float2(float((lightRaw >> 4u) & 0xfu), float(lightRaw & 0xfu)) / 15.0f;
  float2 lightMap = clamp(lightBase * (15.0f / 16.0f) + (0.5f / 16.0f),
      float2(8.0f / 256.0f), float2(248.0f / 256.0f));
  uint lx = uint(round(saturate(lightMap.x) * 4095.0f));
  uint ly = uint(round(saturate(lightMap.y) * 4095.0f));
  return float((lx << 12u) | ly);
}

static inline float2 precompute_tex_origin(uint face, uint modelId) {
  float2 faceBase = float2(float(face >> 1u) / (3.0f * 256.0f), float(face & 1u) / (2.0f * 256.0f));
  float2 modelBase = float2(float(modelId & 0xffu) / 256.0f, float((modelId >> 8u) & 0xffu) / 256.0f);
  return modelBase + faceBase;
}

static inline uint group_count(SectionMeta meta, uint group) {
  switch (group) {
    case 0u: return meta.b.x & 0xffffu;
    case 1u: return meta.b.x >> 16u;
    case 2u: return meta.b.y & 0xffffu;
    case 3u: return meta.b.y >> 16u;
    case 4u: return meta.b.z & 0xffffu;
    case 5u: return meta.b.z >> 16u;
    case 6u: return meta.b.w & 0xffffu;
    default: return meta.b.w >> 16u;
  }
}

static inline float4 unpack_rgba(uint colour) {
  return float4(
      float((colour >> 24u) & 0xffu),
      float((colour >> 16u) & 0xffu),
      float((colour >> 8u) & 0xffu),
      float(colour & 0xffu)) / 255.0f;
}

static inline float3 swizzle_axis(uint axis, float3 data) {
  if (axis == 0u) return data.xzy;
  if (axis == 1u) return data;
  return data.zxy;
}

static inline float4 face_size(uint faceData) {
  float4 size = float4(
                    float(faceData & 0xfu),
                    float((faceData >> 4u) & 0xfu),
                    float((faceData >> 8u) & 0xfu),
                    float((faceData >> 12u) & 0xfu)) /
                16.0f +
      float4(0.0f, 1.0f / 16.0f, 0.0f, 1.0f / 16.0f);
  size.xz -= float2(0.00005f);
  size.yw -= size.xz;
  return size;
}

static inline float face_indentation(uint faceData) {
  uint enc = (faceData >> 16u) & 63u;
  enc += uint(enc == 63u);
  return float(enc) / 64.0f;
}

static inline bool group_visible_from_camera(uint group, SectionMeta meta, constant SceneUniform& scene) {
  if (group == DOUBLE_SIDED_GROUP) return true;
  if (group == TRANSLUCENT_GROUP) return false;
  uint detail = extract_detail(meta);
  int3 relative = extract_section_pos(meta) -
      int3(scene.baseSectionFrame.x >> detail, scene.baseSectionFrame.y >> detail, scene.baseSectionFrame.z >> detail);
  switch (group) {
    case 2u: return relative.y > -1;
    case 3u: return relative.y < 1;
    case 4u: return relative.z > -1;
    case 5u: return relative.z < 1;
    case 6u: return relative.x > -1;
    case 7u: return relative.x < 1;
    default: return false;
  }
}

// Packed to 44 bytes (from 68) for mesh shader output budget. All per-quad-constant fields are
// packed into uints; only `uv` needs perspective-correct interpolation. Fragment shaders unpack.
//
//   flagsFaceCov layout:
//     bits  0-15: flags (bit0=discard, bits2-3=tintState, bit6=isShaded, bits8-11=qSizeX, bits12-15=qSizeY)
//     bits 16-18: face (0-7)
//     bit     19: coverage (0=skip, 1=active fragment)
//
//   colorTintPacked: raw RGBA8 tint colour (0xffffffff = no tint); unpack with unpack_rgba().yzwx
//   lightPacked: raw 8-bit lightId (sky<<4 | block); unpack to lightMap float2 in fragment
struct QuadVertexOut {
  float4 position [[position]];
  float2 uv;
  uint colorTintPacked [[flat]];
  uint flagsFaceCov [[flat]];
  float2 texOrigin [[flat]];
  float gbuf1_y [[flat]];
  float gbuf1_z [[flat]];
  float gbuf1_w [[flat]];
  float gbuf2_y [[flat]];
};

// === GL41Metal distant gbuffer ABI: 3 shared IOSurface textures + 1 private depth ===
//
// WHY ONLY THREE TEXTURES: Apple's OpenGL 4.1 driver caps a fragment program at
// GL_MAX_TEXTURE_IMAGE_UNITS = 16 samplers AND its GLSL compiler SIGSEGVs (inside
// glpLLVMCGFindSamplersAndBuffers / glLinkProgram) when a program actually uses all 16. The
// Iris bridge COLOUR program that samples this gbuffer also binds every shader-pack sampler
// (Complementary needs 12: gaux2/gaux4/colortex18/colortex19/vxDepthTexOpaque/vxDepthTexTrans/
// tex/depthtex1/noisetex/shadowtex0/shadowtex1/shadowcolor0). 12 pack + 4 reconstruction = 16
// crashed; folding the reconstruction to 3 samplers keeps the program at 12 + 3 = 15 (<= 15).
//
// The previous BGRA8 albedo target is removed: albedo is 8-bit anyway, so it is packed into a
// float channel exactly like tint, at identical precision. The 12 RGBA32F channels below hold
// every field losslessly (this is the minimum: the data needs 12 channels).
//
// THIS STRUCT IS THE AUTHORITATIVE LAYOUT. The GL side (GlDistantTerrainBridge GLSL_GBUFFER_DECODE)
// decodes the exact same bit layout, shared_gbuffer.mm allocates the matching IOSurface formats,
// and traversal_pipeline.mm clears the three attachments. Changing a field here means changing
// all three.
//
// LOSSLESSNESS RULES (do not break these):
//  * Integers are stored as exact float *values* (a 32-bit float represents every integer
//    in [0, 2^24] exactly). Every packed integer below stays < 2^24. We never use
//    as_type<float>(uint) bit reinterpretation: a NaN/Inf bit pattern can be canonicalised
//    by the GPU on store and silently corrupt the bits.
//  * The GL side reads with NEAREST filtering on RGBA32F, so each stored float value is
//    returned bit-exact and uint(value) recovers the integer without a +0.5 nudge (a +0.5
//    would overflow the 2^24 exact range for the 24-bit packed fields).
//  * albedo/tint are 8-bit per channel (same precision as the old BGRA8 albedo target).
//    lightMap packs its two coords at 12-bit each: the source light levels are only 16 discrete
//    steps per axis, so 12 bits represent them exactly.
//  * Only RGBA32F is used; it is already validated for IOSurface<->GL interop.
//
//   gbuffer0 RGBA32F  .x = atlas uv.x  .y = atlas uv.y  .z = quad tile.x  .w = quad tile.y
//   gbuffer1 RGBA32F  .x = ndc depth [0,1]  .y = modelId (< 2^16)
//                     .z = customId & 0xFFFFFF  .w = customId >> 24
//   gbuffer2 RGBA32F  .x = albedoPacked = (r<<16)|(g<<8)|b   (each 0..255)
//                     .y = lightPacked  = (lx12<<12)|ly12     (each 0..4095)
//                     .z = tintPacked   = (tr<<16)|(tg<<8)|tb (each 0..255; tint alpha is opaque
//                          here, a future translucent gbuffer carries it separately)
//                     .w = (ao8<<12)|(face<<9)|(flags<<1)|coverage
//                          (face 0..7, flags 0..255, coverage 0/1; coverage doubles as the
//                          "fragment ran" flag, 0 on cleared pixels. ao8 0..255 is the SSAO
//                          factor baked by the ssao.metal pass AFTER this raster; this fragment
//                          always writes ao8 == 0, meaning "no AO data", and the GL side falls
//                          back to 1.0. Max value < 2^20, still float-exact.)
struct QuadFragmentOut {
  float4 gbuffer0 [[color(0)]];
  float4 gbuffer1 [[color(1)]];
  float4 gbuffer2 [[color(2)]];
};

static inline float3 apply_world_curvature(float3 point, constant SceneUniform& scene) {
  float earthRadius = scene.renderParams.y;
  if (earthRadius <= 0.0f) {
    return point;
  }
  float localRadius = earthRadius + point.y;
  float horizontalDist = length(point.xz);
  float phi = horizontalDist / localRadius;
  float sinPhi, cosPhi;
  sinPhi = sincos(phi, cosPhi);
  point.y += (cosPhi - 1.0f) * localRadius;
  if (phi > 0.0001f) {
    point.xz = point.xz * sinPhi / phi;
  }
  return point;
}

fragment QuadFragmentOut voxy_quad_fragment(
    QuadVertexOut in [[stage_in]],
    bool isFrontFacing [[front_facing]],
    texture2d<float> atlas [[texture(0)]]) {
  if ((in.flagsFaceCov & (1u << 19u)) == 0u) {
    discard_fragment();
  }
  uint face = (in.flagsFaceCov >> 16u) & 7u;
  uint flags = in.flagsFaceCov & 0xFFFFu;

  constexpr sampler atlasSampler(coord::normalized, address::clamp_to_edge, filter::nearest, mip_filter::linear);
  float2 tile = floor(in.uv);
  float2 uvFrac = in.uv - tile;
  float2 texPos = in.texOrigin + uvFrac / float2(3.0f * 256.0f, 2.0f * 256.0f);
  float2 atlasTexel = 0.5f / float2(float(atlas.get_width()), float(atlas.get_height()));
  float2 faceExtent = 1.0f / float2(3.0f * 256.0f, 2.0f * 256.0f);
  texPos = clamp(texPos, in.texOrigin + atlasTexel, in.texOrigin + faceExtent - atlasTexel);
  float2 uvSmol = in.uv / float2(3.0f * 256.0f, 2.0f * 256.0f);
  float2 metalTexPos = float2(texPos.x, 1.0f - texPos.y);
  float2 metalUvSmol = float2(uvSmol.x, -uvSmol.y);

  float2 quadSize = float2(float((flags >> 8u) & 0xfu), float((flags >> 12u) & 0xfu));
  if (any(tile < float2(0.0f)) || any(tile > quadSize)) {
    discard_fragment();
  }

  bool useDiscard = (flags & 1u) != 0u;
  uint tintState = (flags >> 2u) & 3u;

  float4 sampled = atlas.sample(atlasSampler, metalTexPos, gradient2d(dfdx(metalUvSmol), dfdy(metalUvSmol)));

  if (useDiscard && sampled.a <= 0.1f) {
    discard_fragment();
  }

  float4 tint = float4(1.0f);
  if (tintState == 2u) {
    if (in.colorTintPacked != 0xffffffffu) {
      tint = unpack_rgba(in.colorTintPacked).yzwx;
    }
  } else if (tintState == 1u) {
    if (abs(sampled.r - sampled.g) < 0.02f && abs(sampled.g - sampled.b) < 0.02f) {
      if (in.colorTintPacked != 0xffffffffu) {
        tint = unpack_rgba(in.colorTintPacked).yzwx;
      }
    }
  }

  bool glFrontFacing = !isFrontFacing;
  uint normalFace = face;
  bool faceAxisNonZero = (normalFace >> 1u) != 0u;
  bool faceFlip = (bool(normalFace & 1u)) != (glFrontFacing != faceAxisNonZero);
  normalFace ^= uint(faceFlip);

  float albedoPacked = float(pack_float_to_unorm4x8(float4(sampled.b, sampled.g, sampled.r, 0.0f)));
  float tintPacked = float(pack_float_to_unorm4x8(float4(tint.b, tint.g, tint.r, 0.0f)));
  float faceFlagsCoverage = float((normalFace << 9u) | ((flags & 0xFFu) << 1u) | 1u);

  QuadFragmentOut out;
  out.gbuffer0 = float4(texPos.x, texPos.y, tile.x, tile.y);
  out.gbuffer1 = float4(in.position.z, in.gbuf1_y, in.gbuf1_z, in.gbuf1_w);
  out.gbuffer2 = float4(albedoPacked, in.gbuf2_y, tintPacked, faceFlagsCoverage);
  return out;
}

// ============================ Mesh Shader Pipeline (Metal 3) ============================
//
// Replaces the indexed vertex pipeline for opaque raster. The object shader reads the
// precomputed buffer in batches of MESH_QUADS_PER_TG quads and forwards each batch via
// the object_data payload to one mesh TG that emits the geometry directly.

// Mesh batch size: configurable via Metal function constants at pipeline creation.
// Benchmark candidates: 16, 32, 64. Budget per mesh TG at each:
//   16 quads:  64v*44 + 32p*idx = ~3.2 KB  (low occupancy risk)
//   32 quads: 128v*44 + 64p*idx = ~6.0 KB  (balanced)
//   64 quads: 256v*44 + 128p*idx = ~11.6 KB (near 16 KB limit)
constant uint MESH_BATCH_SIZE_FC [[function_constant(0)]];
constant uint MESH_QUADS_PER_TG = is_function_constant_defined(MESH_BATCH_SIZE_FC) ? MESH_BATCH_SIZE_FC : 32u;

// metal::mesh template params and max_total_threads_per_threadgroup must be
// compile-time constants, so use the maximum supported batch capacity.
// At runtime MESH_QUADS_PER_TG controls actual output.
constant constexpr uint MESH_MAX_QUADS  = 64u;
constant constexpr uint MESH_MAX_VERTS  = MESH_MAX_QUADS * 4u;   // 256
constant constexpr uint MESH_MAX_PRIMS  = MESH_MAX_QUADS * 2u;   // 128

// Writes the object grid dimensions: 1 object TG per WorkItem (section).
kernel void prepare_mesh_args(
    device const uint* worklistCounter [[buffer(0)]],
    device uint* meshArgs [[buffer(1)]],
    constant SceneUniform& scene [[buffer(2)]],
    uint gid [[thread_position_in_grid]]) {
  if (gid != 0u) return;
  uint workItemCount = min(worklistCounter[0], scene.queueSizes.x);
  meshArgs[0] = workItemCount;
  meshArgs[1] = 1u;
  meshArgs[2] = 1u;
}

// Object shader payload: section-level metadata so mesh TGs can resolve quads inline.
// Each visible face group is recorded as (geometryOffset, count) in the groups array.
constant constexpr uint MAX_OPAQUE_GROUPS = 7u;

struct OpaquePayload {
  uint meshId;
  uint lodLevel;
  float3 sectionWorldBase;
  uint totalQuads;
  uint groupCount;
  uint groupOffsets[MAX_OPAQUE_GROUPS];
  uint groupCounts[MAX_OPAQUE_GROUPS];
  uint groupIds[MAX_OPAQUE_GROUPS];
};

// 1 object TG per WorkItem. Iterates face groups, applies camera-facing culling, builds the
// payload with per-group geometry offsets, and dispatches mesh TGs to cover all visible quads.
// This replaces the old prepare_quad_draw compute pass for opaque.
[[object, max_total_threadgroups_per_mesh_grid(1024)]]
void voxy_opaque_object(
    device const WorkItem* worklist [[buffer(0)]],
    device const uint* worklistCounter [[buffer(1)]],
    constant SceneUniform& scene [[buffer(2)]],
    device const SectionMeta* sections [[buffer(3)]],
    uint objectId [[threadgroup_position_in_grid]],
    uint threadIdx [[thread_index_in_threadgroup]],
    object_data OpaquePayload& payload [[payload]],
    mesh_grid_properties meshGrid) {
  uint workItemCount = min(worklistCounter[0], scene.queueSizes.x);
  if (objectId >= workItemCount) return;

  WorkItem item = worklist[objectId];
  uint totalAccepted = item.lodAndQuadCount & 0x00ffffffu;
  if (totalAccepted == 0u) return;

  uint lodLevel = item.lodAndQuadCount >> 24u;
  SectionMeta meta = sections[item.meshId];

  int3 sectionPos = extract_section_pos(meta);
  int3 baseSection = (sectionPos * int(1u << lodLevel)) - scene.baseSectionFrame.xyz;

  if (threadIdx == 0u) {
    payload.meshId = item.meshId;
    payload.lodLevel = lodLevel;
    payload.sectionWorldBase = float3(baseSection * 32);

    uint sourceOffset = meta.a.w;
    uint visibleQuads = 0u;
    uint gIdx = 0u;
    for (uint group = 0u; group < 8u; group++) {
      uint count = group_count(meta, group);
      if (count > 0u && group_visible_from_camera(group, meta, scene)) {
        payload.groupOffsets[gIdx] = sourceOffset;
        payload.groupCounts[gIdx] = count;
        payload.groupIds[gIdx] = group;
        visibleQuads += count;
        gIdx++;
        if (gIdx >= MAX_OPAQUE_GROUPS) break;
      }
      sourceOffset += count;
    }
    payload.groupCount = gIdx;
    payload.totalQuads = visibleQuads;

    if (visibleQuads == 0u) return;
    uint meshTGs = min((visibleQuads + MESH_QUADS_PER_TG - 1u) / MESH_QUADS_PER_TG, 1024u);
    meshGrid.set_threadgroups_per_grid(uint3(meshTGs, 1u, 1u));
  }
}

using OpaqueMesh =
    metal::mesh<QuadVertexOut, void, MESH_MAX_VERTS, MESH_MAX_PRIMS, metal::topology::triangle>;

struct SharedQuadData {
  packed_float3 basePoint;
  float lodScale;
  packed_float2 quadSizeAdd;
  packed_float2 uvBase;
  uint face;
  uint flagsFaceCov;
  uint colorTintPacked;
  packed_float2 texOrigin;
  float gbuf1_y;
  float gbuf1_z;
  float gbuf1_w;
  float gbuf2_y;
};

// Corner 0 of each quad reads device memory; all 4 corners read resolved data from threadgroup
// shared memory after a barrier, reducing scattered device reads by 75%.
[[mesh, max_total_threads_per_threadgroup(MESH_MAX_VERTS)]]
void voxy_opaque_mesh(
    const object_data OpaquePayload& payload [[payload]],
    device const ulong* quads [[buffer(0)]],
    device const BlockModel* models [[buffer(1)]],
    device const uint* modelColours [[buffer(2)]],
    device const uint* modelPresent [[buffer(3)]],
    constant SceneUniform& scene [[buffer(4)]],
    OpaqueMesh outputMesh,
    uint meshBatchId [[threadgroup_position_in_grid]],
    uint threadIdx [[thread_index_in_threadgroup]]) {
  threadgroup SharedQuadData sharedQuads[MESH_MAX_QUADS];

  uint batchBase = meshBatchId * MESH_QUADS_PER_TG;
  uint batchQuads = min(MESH_QUADS_PER_TG,
      payload.totalQuads - min(batchBase, payload.totalQuads));

  if (threadIdx == 0u) {
    outputMesh.set_primitive_count(batchQuads * 2u);
  }

  uint localQuad = threadIdx / 4u;
  uint corner = threadIdx % 4u;

  if (corner == 0u && localQuad < batchQuads) {
    uint globalLinear = batchBase + localQuad;
    uint geomOffset = 0u;
    {
      uint acc = 0u;
      for (uint g = 0u; g < payload.groupCount; g++) {
        uint next = acc + payload.groupCounts[g];
        if (globalLinear < next) {
          geomOffset = payload.groupOffsets[g] + (globalLinear - acc);
          break;
        }
        acc = next;
      }
    }

    SharedQuadData sq;
    sq.flagsFaceCov = 0u;

    ulong quad = quads[geomOffset];
    if (quad != 0ul) {
      uint face = extract_quad_face(quad);
      uint modelId = extract_model_id(quad);

      if (modelId < (1u << 16u) && modelPresent[modelId] != 0u) {
        BlockModel model = models[modelId];
        uint faceData = model.faceData[min(face, 5u)];

        if (faceData != 0xffffffffu) {
          bool useDiscard = (faceData >> 22u) & 1u;

          float lodScale = float(1u << payload.lodLevel);
          float4 fSize = face_size(faceData);
          uint2 qSize = extract_quad_size(quad);
          uint3 qStartRaw = extract_quad_pos(quad);
          float depthOffset = face_indentation(faceData);
          float3 qStart = float3(qStartRaw) +
              swizzle_axis(face >> 1u, float3(fSize.x, fSize.z,
                  mix(depthOffset, 1.0f - depthOffset, float(face & 1u))));
          float2 quadSizeAdd = fSize.yw + float2(qSize) - 1.0f;

          uint tintState = (faceData >> 24u) & 3u;
          uint tintPacked = 0xffffffffu;
          if (tintState != 0u) {
            tintPacked = model.colourTint;
            if ((model.flagsA & 2u) != 0u) {
              tintPacked = modelColours[model.colourTint + extract_biome_id(quad)];
            }
          }

          sq.basePoint = qStart * lodScale + payload.sectionWorldBase;
          sq.lodScale = lodScale;
          sq.quadSizeAdd = quadSizeAdd;
          sq.uvBase = fSize.xz;
          sq.face = face;
          sq.flagsFaceCov = (uint(useDiscard) |
              (tintState << 2u) |
              (((model.flagsA >> 3u) & 1u) << 6u) |
              ((qSize.x - 1u) << 8u) |
              ((qSize.y - 1u) << 12u)) | (face << 16u) | (1u << 19u);
          sq.colorTintPacked = tintPacked;
          sq.texOrigin = precompute_tex_origin(face, modelId);
          sq.gbuf1_y = float(modelId & 0xFFFFu);
          sq.gbuf1_z = float(model.customId & 0xFFFFFFu);
          sq.gbuf1_w = float(model.customId >> 24u);
          sq.gbuf2_y = precompute_light_packed(extract_light_id(quad));
        }
      }
    }
    sharedQuads[localQuad] = sq;
  }

  threadgroup_barrier(mem_flags::mem_threadgroup);

  if (localQuad >= batchQuads) return;

  SharedQuadData sq = sharedQuads[localQuad];

  QuadVertexOut vert;
  vert.position = float4(2.0f, 2.0f, 1.0f, 1.0f);
  vert.uv = float2(0.0f);
  vert.colorTintPacked = 0xffffffffu;
  vert.flagsFaceCov = 0u;
  vert.texOrigin = float2(0.0f);
  vert.gbuf1_y = 0.0f;
  vert.gbuf1_z = 0.0f;
  vert.gbuf1_w = 0.0f;
  vert.gbuf2_y = 0.0f;

  if ((sq.flagsFaceCov & (1u << 19u)) != 0u) {
    float2 cornerMask = float2(float((corner >> 1u) & 1u), float(corner & 1u)) * sq.lodScale;
    float3 point = float3(sq.basePoint) + swizzle_axis(sq.face >> 1u,
        float3(float2(sq.quadSizeAdd) * cornerMask, 0.0f));
    point = apply_world_curvature(point, scene);
    float4 glClip = scene.drawMvp * float4(point, 1.0f);
    float4 metalClip = glClip;
    metalClip.z = (glClip.z + glClip.w) * 0.5f;

    vert.position = metalClip;
    vert.uv = float2(sq.uvBase) + float2(sq.quadSizeAdd) *
        float2(float((corner >> 1u) & 1u), float(corner & 1u));
    vert.colorTintPacked = sq.colorTintPacked;
    vert.flagsFaceCov = sq.flagsFaceCov;
    vert.texOrigin = float2(sq.texOrigin);
    vert.gbuf1_y = sq.gbuf1_y;
    vert.gbuf1_z = sq.gbuf1_z;
    vert.gbuf1_w = sq.gbuf1_w;
    vert.gbuf2_y = sq.gbuf2_y;
  }

  uint vertexIdx = localQuad * 4u + corner;
  outputMesh.set_vertex(vertexIdx, vert);

  if (corner == 0u) {
    uint vBase = localQuad * 4u;
    uint pBase = localQuad * 2u;
    outputMesh.set_index(pBase * 3u + 0u, vBase + 1u);
    outputMesh.set_index(pBase * 3u + 1u, vBase + 2u);
    outputMesh.set_index(pBase * 3u + 2u, vBase + 0u);
    outputMesh.set_index(pBase * 3u + 3u, vBase + 1u);
    outputMesh.set_index(pBase * 3u + 4u, vBase + 3u);
    outputMesh.set_index(pBase * 3u + 5u, vBase + 2u);
  }
}

// ============================ Translucent (group 0) pipeline ============================
//
// Distance sort: mirrors GL46 buildtranslucents.comp. Translucent sections are binned by Manhattan
// distance into TRANSLUCENT_BUCKET_COUNT buckets (nearer sections -> higher bucket), the per-bucket
// quad counts are exclusive-prefix-summed into start offsets, then each section's group-0 quads are
// scattered into its bucket range. The translucent raster then draws bucket 0..N ascending, i.e.
// far -> near, so the Metal over-blend into tgbufferAccum accumulates in the correct order.

constant uint TRANSLUCENT_BUCKET_COUNT = 1024u;

// Count pass: bin each translucent work item by section distance and accumulate its quad count into
// the bucket. Caches the bucket in WorkItem.reserved so the scatter pass does not recompute it.
kernel void prepare_translucent_sort(
    device WorkItem* worklist [[buffer(0)]],
    device const uint* worklistCounter [[buffer(1)]],
    device const SectionMeta* sections [[buffer(2)]],
    device atomic_uint* distanceBuckets [[buffer(3)]],
    constant SceneUniform& scene [[buffer(4)]],
    uint gid [[thread_position_in_grid]]) {
  uint itemCount = min(worklistCounter[0], scene.queueSizes.x);
  if (gid >= itemCount) return;
  WorkItem item = worklist[gid];
  SectionMeta meta = sections[item.meshId];
  uint detail = extract_detail(meta);
  int3 baseRel = extract_section_pos(meta) -
      int3(scene.baseSectionFrame.x >> detail, scene.baseSectionFrame.y >> detail, scene.baseSectionFrame.z >> detail);
  uint dist = uint(abs(baseRel.x) + abs(baseRel.y) + abs(baseRel.z)) << detail;
  uint bucket = (TRANSLUCENT_BUCKET_COUNT - 1u) - min(dist, TRANSLUCENT_BUCKET_COUNT - 1u);
  uint quadCount = item.lodAndQuadCount & 0x00ffffffu;
  atomic_fetch_add_explicit(&distanceBuckets[bucket], quadCount, memory_order_relaxed);
  worklist[gid].reserved = bucket;
}

// Exclusive prefix sum over the buckets (single thread; 1024 elements run once per frame). After
// this distanceBuckets[b] is the start quad offset for bucket b, clamped to the raster capacity; it
// then doubles as the running cursor consumed by scatter_translucent_draw. Also fills drawArgs.
kernel void prefix_sum_translucent(
    device uint* distanceBuckets [[buffer(0)]],
    device const uint* worklistCounter [[buffer(1)]],
    device uint* drawArgs [[buffer(2)]],
    constant SceneUniform& scene [[buffer(3)]],
    device uint* meshArgs [[buffer(4)]],
    constant uint& meshBatchSize [[buffer(5)]],
    uint gid [[thread_position_in_grid]]) {
  if (gid != 0u) return;
  uint cap = scene.rasterLimits.x;
  uint acc = 0u;
  for (uint b = 0u; b < TRANSLUCENT_BUCKET_COUNT; b++) {
    uint c = distanceBuckets[b];
    distanceBuckets[b] = min(acc, cap);
    acc += c;
  }
  uint total = min(worklistCounter[1], cap);
  drawArgs[0] = total * 6u;
  drawArgs[1] = 1u;
  drawArgs[2] = 0u;
  drawArgs[3] = 0u;
  drawArgs[4] = 0u;
  uint batchSize = max(meshBatchSize, 1u);
  uint meshTGs = (total + batchSize - 1u) / batchSize;
  meshArgs[0] = meshTGs;
  meshArgs[1] = 1u;
  meshArgs[2] = 1u;
}

// Scatter + expand: place each section's group-0 quads into its bucket range (back->front order).
kernel void scatter_translucent_draw(
    device const WorkItem* worklist [[buffer(0)]],
    device const uint* worklistCounter [[buffer(1)]],
    device const SectionMeta* sections [[buffer(2)]],
    device atomic_uint* distanceBuckets [[buffer(3)]],
    device QuadDrawRef* quadRefs [[buffer(4)]],
    constant SceneUniform& scene [[buffer(5)]],
    uint gid [[thread_position_in_grid]]) {
  uint itemCount = min(worklistCounter[0], scene.queueSizes.x);
  if (gid >= itemCount) return;
  WorkItem item = worklist[gid];
  uint quadCount = item.lodAndQuadCount & 0x00ffffffu;
  if (quadCount == 0u) return;
  uint lod = item.lodAndQuadCount >> 24u;
  uint bucket = item.reserved;
  SectionMeta meta = sections[item.meshId];
  // group 0 occupies [meta.a.w, meta.a.w + group_count(meta, 0)) - it is the first group, before
  // every opaque group (prepare_quad_draw advances sourceOffset past it without emitting it).
  uint sourceOffset = meta.a.w;
  uint cap = scene.rasterLimits.x;
  uint base = atomic_fetch_add_explicit(&distanceBuckets[bucket], quadCount, memory_order_relaxed);
  for (uint i = 0u; i < quadCount; i++) {
    uint out = base + i;
    if (out >= cap) break;
    QuadDrawRef ref;
    ref.meshId = item.meshId;
    ref.quadOffset = sourceOffset + i;
    ref.faceGroupAndLod = TRANSLUCENT_GROUP | (lod << 8u);
    ref.reserved = 0u;
    quadRefs[out] = ref;
  }
}

// Translucent mesh shader pair. Reads sorted QuadDrawRefs directly and resolves quad data
// inline, eliminating the translucent precompute_quads pass. Object grid =
// ceil(totalSortedQuads / MESH_QUADS_PER_TG); grid order preserves the far->near draw order.

struct TranslucentBatchPayload {
  uint quadBase;
  uint totalQuads;
};

[[object, max_total_threadgroups_per_mesh_grid(1)]]
void voxy_translucent_object(
    device const uint* drawArgs [[buffer(0)]],
    uint objectId [[threadgroup_position_in_grid]],
    uint threadIdx [[thread_index_in_threadgroup]],
    object_data TranslucentBatchPayload& payload [[payload]],
    mesh_grid_properties meshGrid) {
  uint totalQuads = drawArgs[0] / 6u;
  uint batchBase = objectId * MESH_QUADS_PER_TG;
  if (batchBase >= totalQuads) return;

  if (threadIdx == 0u) {
    payload.quadBase = batchBase;
    payload.totalQuads = totalQuads;
    meshGrid.set_threadgroups_per_grid(uint3(1u, 1u, 1u));
  }
}

using TranslucentMesh =
    metal::mesh<QuadVertexOut, void, MESH_MAX_VERTS, MESH_MAX_PRIMS, metal::topology::triangle>;

[[mesh, max_total_threads_per_threadgroup(MESH_MAX_VERTS)]]
void voxy_translucent_mesh(
    const object_data TranslucentBatchPayload& payload [[payload]],
    device const QuadDrawRef* sortedRefs [[buffer(0)]],
    device const ulong* quads [[buffer(1)]],
    device const SectionMeta* sections [[buffer(2)]],
    device const BlockModel* models [[buffer(3)]],
    device const uint* modelColours [[buffer(4)]],
    device const uint* modelPresent [[buffer(5)]],
    constant SceneUniform& scene [[buffer(6)]],
    TranslucentMesh outputMesh,
    uint meshBatchId [[threadgroup_position_in_grid]],
    uint threadIdx [[thread_index_in_threadgroup]]) {
  threadgroup SharedQuadData sharedQuads[MESH_MAX_QUADS];

  uint batchQuads = min(MESH_QUADS_PER_TG,
      payload.totalQuads - min(payload.quadBase, payload.totalQuads));

  if (threadIdx == 0u) {
    outputMesh.set_primitive_count(batchQuads * 2u);
  }

  uint localQuad = threadIdx / 4u;
  uint corner = threadIdx % 4u;

  if (corner == 0u && localQuad < batchQuads) {
    uint globalQuad = payload.quadBase + localQuad;
    QuadDrawRef drawRef = sortedRefs[globalQuad];
    uint lodLevel = drawRef.faceGroupAndLod >> 8u;

    SharedQuadData sq;
    sq.flagsFaceCov = 0u;

    ulong quad = quads[drawRef.quadOffset];
    if (quad != 0ul) {
      uint face = extract_quad_face(quad);
      uint modelId = extract_model_id(quad);

      if (modelId < (1u << 16u) && modelPresent[modelId] != 0u) {
        BlockModel model = models[modelId];
        uint faceData = model.faceData[min(face, 5u)];

        if (faceData != 0xffffffffu) {
          SectionMeta meta = sections[drawRef.meshId];
          float lodScale = float(1u << lodLevel);
          int3 sectionPos = extract_section_pos(meta);
          int3 baseSection = (sectionPos * int(1u << lodLevel)) - scene.baseSectionFrame.xyz;

          float4 fSize = face_size(faceData);
          uint2 qSize = extract_quad_size(quad);
          uint3 qStartRaw = extract_quad_pos(quad);
          float depthOffset = face_indentation(faceData);
          float3 qStart = float3(qStartRaw) +
              swizzle_axis(face >> 1u, float3(fSize.x, fSize.z,
                  mix(depthOffset, 1.0f - depthOffset, float(face & 1u))));
          float2 quadSizeAdd = fSize.yw + float2(qSize) - 1.0f;

          uint tintState = (faceData >> 24u) & 3u;
          uint tintPacked = 0xffffffffu;
          if (tintState != 0u) {
            tintPacked = model.colourTint;
            if ((model.flagsA & 2u) != 0u) {
              tintPacked = modelColours[model.colourTint + extract_biome_id(quad)];
            }
          }

          sq.basePoint = qStart * lodScale + float3(baseSection * 32);
          sq.lodScale = lodScale;
          sq.quadSizeAdd = quadSizeAdd;
          sq.uvBase = fSize.xz;
          sq.face = face;
          sq.flagsFaceCov = (((faceData >> 22u) & 1u) |
              (tintState << 2u) |
              (((model.flagsA >> 3u) & 1u) << 6u) |
              ((qSize.x - 1u) << 8u) |
              ((qSize.y - 1u) << 12u)) | (face << 16u) | (1u << 19u);
          sq.colorTintPacked = tintPacked;
          sq.texOrigin = precompute_tex_origin(face, modelId);
          sq.gbuf1_y = float(modelId & 0xFFFFu);
          sq.gbuf1_z = float(model.customId & 0xFFFFFFu);
          sq.gbuf1_w = float(model.customId >> 24u);
          sq.gbuf2_y = precompute_light_packed(extract_light_id(quad));
        }
      }
    }
    sharedQuads[localQuad] = sq;
  }

  threadgroup_barrier(mem_flags::mem_threadgroup);

  if (localQuad >= batchQuads) return;

  SharedQuadData sq = sharedQuads[localQuad];

  QuadVertexOut vtx;
  vtx.position = float4(2.0f, 2.0f, 1.0f, 1.0f);
  vtx.uv = float2(0.0f);
  vtx.colorTintPacked = 0xffffffffu;
  vtx.flagsFaceCov = 0u;
  vtx.texOrigin = float2(0.0f);
  vtx.gbuf1_y = 0.0f;
  vtx.gbuf1_z = 0.0f;
  vtx.gbuf1_w = 0.0f;
  vtx.gbuf2_y = 0.0f;

  if ((sq.flagsFaceCov & (1u << 19u)) != 0u) {
    float2 cornerMask = float2(float((corner >> 1u) & 1u), float(corner & 1u)) * sq.lodScale;
    float3 point = float3(sq.basePoint) + swizzle_axis(sq.face >> 1u,
        float3(float2(sq.quadSizeAdd) * cornerMask, 0.0f));
    point = apply_world_curvature(point, scene);
    float4 glClip = scene.drawMvp * float4(point, 1.0f);
    float4 metalClip = glClip;
    metalClip.z = (glClip.z + glClip.w) * 0.5f;

    vtx.position = metalClip;
    vtx.uv = float2(sq.uvBase) + float2(sq.quadSizeAdd) *
        float2(float((corner >> 1u) & 1u), float(corner & 1u));
    vtx.colorTintPacked = sq.colorTintPacked;
    vtx.flagsFaceCov = sq.flagsFaceCov;
    vtx.texOrigin = float2(sq.texOrigin);
    vtx.gbuf1_y = sq.gbuf1_y;
    vtx.gbuf1_z = sq.gbuf1_z;
    vtx.gbuf1_w = sq.gbuf1_w;
    vtx.gbuf2_y = sq.gbuf2_y;
  }

  uint vertexIdx = localQuad * 4u + corner;
  outputMesh.set_vertex(vertexIdx, vtx);

  if (corner == 0u) {
    uint triBase = localQuad * 2u;
    uint vBase = localQuad * 4u;
    outputMesh.set_index(triBase * 3u + 0u, vBase + 1u);
    outputMesh.set_index(triBase * 3u + 1u, vBase + 2u);
    outputMesh.set_index(triBase * 3u + 2u, vBase + 0u);
    outputMesh.set_index(triBase * 3u + 3u, vBase + 1u);
    outputMesh.set_index(triBase * 3u + 4u, vBase + 3u);
    outputMesh.set_index(triBase * 3u + 5u, vBase + 2u);
  }
}

// Translucent distant gbuffer ABI (see SharedDistantGbuffer / GlDistantTerrainBridge). Three RGBA32F
// shared targets. tgbuffer0/1 hold the FRONT-most translucent surface (blend off; nearest wins via
// the framebuffer-fetch depth compare below, since within-section draw order is arbitrary) for
// strict Iris water-program shading; tgbufferAccum holds the order-independent accumulation of ALL
// layers (rgb = additive sum of premultiplied flat colours, alpha = 1 - prod(1-a_i)) so layers
// behind the front surface still contribute colour in the deferred-hybrid composite.
//
//   tgbuffer0 RGBA32F  .x albedoPacked (r<<16|g<<8|b)  .y lightPacked (lx12<<12|ly12)
//                      .z tintPacked (tr<<16|tg<<8|tb) .w (face<<9)|(flags<<1)|coverage
//   tgbuffer1 RGBA32F  .x ndc depth [0,1]  .y alpha[0,1]  .z customId&0xFFFFFF  .w customId>>24
//   tgbufferAccum RGBA32F  .xyz premultiplied (albedo*tint*alpha)  .w alpha   (over-blended)
struct TranslucentFragmentOut {
  float4 tgbuffer0 [[color(0)]];
  float4 tgbuffer1 [[color(1)]];
  float4 tgbufferAccum [[color(2)]];
};

// prevT0/prevT1 are framebuffer-fetch reads (Apple-silicon tile memory) of the current
// tgbuffer0/1 contents. Draw order is only sorted per-SECTION (back->front across sections,
// arbitrary within one section), so "last write wins" does NOT select the nearest surface for
// layers inside the same section (e.g. a glass box standing in water). Instead each fragment
// compares its depth against the stored front surface and keeps whichever is nearer, making the
// front-surface extraction order-independent.
fragment TranslucentFragmentOut voxy_translucent_fragment(
    QuadVertexOut in [[stage_in]],
    bool isFrontFacing [[front_facing]],
    float4 prevT0 [[color(0)]],
    float4 prevT1 [[color(1)]],
    texture2d<float> atlas [[texture(0)]]) {
  if ((in.flagsFaceCov & (1u << 19u)) == 0u) {
    discard_fragment();
  }
  uint face = (in.flagsFaceCov >> 16u) & 7u;
  uint flags = in.flagsFaceCov & 0xFFFFu;

  constexpr sampler atlasSampler(coord::normalized, address::clamp_to_edge, filter::nearest, mip_filter::linear);
  float2 tile = floor(in.uv);
  float2 uvFrac = in.uv - tile;
  float2 texPos = in.texOrigin + uvFrac / float2(3.0f * 256.0f, 2.0f * 256.0f);
  float2 atlasTexel = 0.5f / float2(float(atlas.get_width()), float(atlas.get_height()));
  float2 faceExtent = 1.0f / float2(3.0f * 256.0f, 2.0f * 256.0f);
  texPos = clamp(texPos, in.texOrigin + atlasTexel, in.texOrigin + faceExtent - atlasTexel);
  float2 uvSmol = in.uv / float2(3.0f * 256.0f, 2.0f * 256.0f);
  float2 metalTexPos = float2(texPos.x, 1.0f - texPos.y);
  float2 metalUvSmol = float2(uvSmol.x, -uvSmol.y);

  float2 quadSize = float2(float((flags >> 8u) & 0xfu), float((flags >> 12u) & 0xfu));
  if (any(tile < float2(0.0f)) || any(tile > quadSize)) {
    discard_fragment();
  }

  // Single-sided translucency, aligned to the quad's SEMANTIC face rather than raw winding.
  // The mesher emits the same corner order for both directions of a face pair (that is why the
  // opaque fragment derives the observed face from [[front_facing]]), so fixed-function
  // MTLCullModeFront (added to stop glass rendering double-layered) also culled the exposed side
  // of +Z/+X/-Y faces - distant water and stained glass lost their side walls. Instead keep only
  // fragments seen from the direction the stored face points to, which is exactly vanilla's
  // GL_CULL_FACE/GL_BACK behaviour for translucent blocks.
  bool glFrontFacing = !isFrontFacing;
  bool faceAxisNonZero = (face >> 1u) != 0u;
  bool viewedFromBehind = (bool(face & 1u)) != (glFrontFacing != faceAxisNonZero);
  if (viewedFromBehind) {
    discard_fragment();
  }
  uint normalFace = face;

  float4 sampled = atlas.sample(atlasSampler, metalTexPos, gradient2d(dfdx(metalUvSmol), dfdy(metalUvSmol)));
  float alpha = sampled.a;

  if (alpha == 0.0f) {
    discard_fragment();
  }

  float4 tint = float4(1.0f);
  uint tintState = (flags >> 2u) & 3u;
  if (tintState == 2u) {
    if (in.colorTintPacked != 0xffffffffu) {
      tint = unpack_rgba(in.colorTintPacked).yzwx;
    }
  } else if (tintState == 1u) {
    if (abs(sampled.r - sampled.g) < 0.02f && abs(sampled.g - sampled.b) < 0.02f) {
      if (in.colorTintPacked != 0xffffffffu) {
        tint = unpack_rgba(in.colorTintPacked).yzwx;
      }
    }
  }

  // Fragments viewed from behind the stored face were discarded above, so the observed face is
  // always the stored face here (no winding-derived flip needed).
  float albedoPacked = float(pack_float_to_unorm4x8(float4(sampled.b, sampled.g, sampled.r, 0.0f)));
  float tintPacked = float(pack_float_to_unorm4x8(float4(tint.b, tint.g, tint.r, 0.0f)));
  float faceFlagsCoverage = float((normalFace << 9u) | ((flags & 0xFFu) << 1u) | 1u);
  float screenDepth = in.position.z;

  float faceShade = 1.0f;
  bool isShaded = ((flags >> 6u) & 1u) != 0u;
  if (isShaded) {
    uint fAxis = normalFace >> 1u;
    if (fAxis == 1u) faceShade = 0.8f;
    else if (fAxis == 2u) faceShade = 0.6f;
    else if (normalFace != 1u) faceShade = 0.5f;
  }

  float3 flatColour = saturate(sampled.rgb) * saturate(tint.rgb) * faceShade * alpha;

  TranslucentFragmentOut out;
  // Nearest-wins front surface: keep the stored fragment when it has coverage (tgbuffer0.w low
  // bit) and is closer than this one; tgbuffer0/1 blending is off, so the returned value is
  // stored verbatim either way. tgbufferAccum always accumulates (order-independent blend).
  bool prevCovered = (uint(prevT0.w + 0.5f) & 1u) != 0u;
  if (prevCovered && prevT1.x <= screenDepth) {
    out.tgbuffer0 = prevT0;
    out.tgbuffer1 = prevT1;
  } else {
    out.tgbuffer0 = float4(albedoPacked, in.gbuf2_y, tintPacked, faceFlagsCoverage);
    out.tgbuffer1 = float4(screenDepth, saturate(alpha), in.gbuf1_z, in.gbuf1_w);
  }
  out.tgbufferAccum = float4(flatColour, saturate(alpha));
  return out;
}
