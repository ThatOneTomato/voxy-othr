#include <metal_stdlib>
using namespace metal;

constant uint MODEL_COUNT = 1u << 16u;
constant uint TRANSLUCENT_GROUP = 0u;
constant uint DOUBLE_SIDED_GROUP = 1u;
constant uint DRAWLIST_THREADS_PER_ITEM = 128u;

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

struct BlockModel {
  uint faceData[6];
  uint flagsA;
  uint colourTint;
  uint customId;
  uint pad[7];
};

struct OpaqueDrawInstance {
  float4 baseAndLod;
  float4 sizeAndUv;
  uint flagsFace;
  uint tintPacked;
  uint modelLight;
  uint customId;
};

static inline uint extract_quad_bits(ulong v, uint bits, uint shift) {
  return uint((v >> shift) & ((1ul << bits) - 1ul));
}

static inline uint3 extract_quad_pos(ulong quad) {
  return uint3(extract_quad_bits(quad, 5u, 21u),
               extract_quad_bits(quad, 5u, 16u),
               extract_quad_bits(quad, 5u, 11u));
}

static inline uint2 extract_quad_size(ulong quad) {
  return uint2(extract_quad_bits(quad, 4u, 3u),
               extract_quad_bits(quad, 4u, 7u)) +
         1u;
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

static inline uint group_count(SectionMeta meta, uint group) {
  switch (group) {
    case 0u:
      return meta.b.x & 0xffffu;
    case 1u:
      return meta.b.x >> 16u;
    case 2u:
      return meta.b.y & 0xffffu;
    case 3u:
      return meta.b.y >> 16u;
    case 4u:
      return meta.b.z & 0xffffu;
    case 5u:
      return meta.b.z >> 16u;
    case 6u:
      return meta.b.w & 0xffffu;
    default:
      return meta.b.w >> 16u;
  }
}

static inline bool group_visible_from_camera(uint group, SectionMeta meta,
                                             constant SceneUniform& scene,
                                             bool faceGroupCull) {
  if (group == DOUBLE_SIDED_GROUP) return true;
  if (group == TRANSLUCENT_GROUP) return false;
  if (!faceGroupCull) return true;
  uint detail = extract_detail(meta);
  int3 relative =
      extract_section_pos(meta) - int3(scene.baseSectionFrame.x >> detail,
                                       scene.baseSectionFrame.y >> detail,
                                       scene.baseSectionFrame.z >> detail);
  switch (group) {
    case 2u:
      return relative.y > -1;
    case 3u:
      return relative.y < 1;
    case 4u:
      return relative.z > -1;
    case 5u:
      return relative.z < 1;
    case 6u:
      return relative.x > -1;
    case 7u:
      return relative.x < 1;
    default:
      return false;
  }
}

static inline float3 swizzle_axis(uint axis, float3 data) {
  if (axis == 0u) return data.xzy;
  if (axis == 1u) return data;
  return data.zxy;
}

static inline float4 face_size(uint faceData) {
  float4 size =
      float4(float(faceData & 0xfu), float((faceData >> 4u) & 0xfu),
             float((faceData >> 8u) & 0xfu), float((faceData >> 12u) & 0xfu)) /
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

kernel void build_opaque_drawlist_instances(
    device const WorkItem* worklist [[buffer(0)]],
    device const uint* worklistCounter [[buffer(1)]],
    constant SceneUniform& scene [[buffer(2)]],
    device const SectionMeta* sections [[buffer(3)]],
    device const ulong* quads [[buffer(4)]],
    device const BlockModel* models [[buffer(5)]],
    device const uint* modelColours [[buffer(6)]],
    device const uint* modelPresent [[buffer(7)]],
    device OpaqueDrawInstance* out [[buffer(8)]],
    device atomic_uint* counters [[buffer(9)]],
    constant uint4& params [[buffer(10)]],
    uint objectId [[threadgroup_position_in_grid]],
    uint threadIdx [[thread_index_in_threadgroup]]) {
  uint workItemCount = min(worklistCounter[0], scene.queueSizes.x);
  if (objectId >= workItemCount) return;

  WorkItem item = worklist[objectId];
  uint totalAccepted = item.lodAndQuadCount & 0x00ffffffu;
  if (totalAccepted == 0u) return;

  uint lodLevel = item.lodAndQuadCount >> 24u;
  SectionMeta meta = sections[item.meshId];
  int lodScaleInt = int(1u << lodLevel);
  int3 sectionBaseSections =
      extract_section_pos(meta) * lodScaleInt - scene.baseSectionFrame.xyz;
  float3 sectionWorldBase = float3(sectionBaseSections) * 32.0f;
  float lodScale = float(lodScaleInt);

  uint sourceOffset = meta.a.w;
  uint capacity = params.x;
  uint geometryQuadCapacity = params.y;
  bool faceGroupCull = params.z != 0u;
  for (uint group = 0u; group < 8u; group++) {
    uint count = group_count(meta, group);
    bool visible = count > 0u && group_visible_from_camera(group, meta, scene, faceGroupCull);
    if (visible) {
      for (uint quadIndex = threadIdx; quadIndex < count;
           quadIndex += DRAWLIST_THREADS_PER_ITEM) {
        uint geometryIndex = sourceOffset + quadIndex;
        if (geometryIndex >= geometryQuadCapacity) {
          continue;
        }
        ulong quad = quads[geometryIndex];
        if (quad == 0ul) {
          continue;
        }
        uint face = extract_quad_face(quad);
        uint modelId = extract_model_id(quad);
        if (modelId >= MODEL_COUNT || modelPresent[modelId] == 0u) {
          continue;
        }
        BlockModel model = models[modelId];
        uint faceData = model.faceData[min(face, 5u)];
        if (faceData == 0xffffffffu) {
          continue;
        }

        uint outputIndex =
            atomic_fetch_add_explicit(&counters[0], 1u, memory_order_relaxed);
        if (outputIndex >= capacity) {
          atomic_fetch_add_explicit(&counters[1], 1u, memory_order_relaxed);
          continue;
        }

        float4 fSize = face_size(faceData);
        uint2 qSize = extract_quad_size(quad);
        bool useDiscard = ((faceData >> 22u) & 1u) != 0u;
        useDiscard |= any(qSize > uint2(1u)) && (((faceData >> 23u) & 1u) != 0u);
        uint3 qStartRaw = extract_quad_pos(quad);
        float depthOffset = face_indentation(faceData);
        float depth = (face & 1u) != 0u ? (1.0f - depthOffset) : depthOffset;
        float3 qStart =
            float3(qStartRaw) +
            swizzle_axis(face >> 1u, float3(fSize.x, fSize.z, depth));
        float2 quadSizeAdd = fSize.yw + float2(qSize) - 1.0f;

        uint tintState = (faceData >> 24u) & 3u;
        uint tintPacked = 0xffffffffu;
        if (tintState != 0u) {
          tintPacked = model.colourTint;
          if ((model.flagsA & 2u) != 0u) {
            uint colourIndex = model.colourTint + extract_biome_id(quad);
            if (colourIndex < MODEL_COUNT) {
              tintPacked = modelColours[colourIndex];
            }
          }
        }

        OpaqueDrawInstance instance;
        instance.baseAndLod =
            float4(qStart * lodScale + sectionWorldBase, lodScale);
        instance.sizeAndUv = float4(quadSizeAdd, fSize.xz);
        instance.flagsFace =
            (uint(useDiscard) | (tintState << 2u) |
             (((model.flagsA >> 3u) & 1u) << 6u) | ((qSize.x - 1u) << 8u) |
             ((qSize.y - 1u) << 12u)) |
            (face << 16u);
        instance.tintPacked = tintPacked;
        instance.modelLight = modelId | (extract_light_id(quad) << 16u);
        instance.customId = model.customId;
        out[outputIndex] = instance;
      }
    }
    sourceOffset += count;
  }
}
