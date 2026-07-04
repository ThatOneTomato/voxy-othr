#include <metal_stdlib>
using namespace metal;
struct SectionMeta { uint4 a; uint4 b; };
struct BlockModel { uint faceData[6]; uint flagsA; uint colourTint; uint customId; uint pad[7]; };
kernel void validateTerrain(device const SectionMeta* sections [[buffer(0)]],
                            device const ulong* quads [[buffer(1)]],
                            device const BlockModel* models [[buffer(2)]],
                            device const uint* modelPresent [[buffer(3)]],
                            device atomic_uint* stats [[buffer(4)]],
                            constant uint& maxSections [[buffer(5)]],
                            constant uint& geometryQuadCapacity [[buffer(6)]],
                            uint id [[thread_position_in_grid]]) {
  if (id >= maxSections) return;
  SectionMeta meta = sections[id];
  uint ptr = meta.a.w;
  uint total = (meta.b.x & 0xffffu) + (meta.b.x >> 16) +
               (meta.b.y & 0xffffu) + (meta.b.y >> 16) +
               (meta.b.z & 0xffffu) + (meta.b.z >> 16) +
               (meta.b.w & 0xffffu) + (meta.b.w >> 16);
  if (total == 0) return;
  atomic_fetch_add_explicit(&stats[0], 1u, memory_order_relaxed);
  atomic_fetch_add_explicit(&stats[1], total, memory_order_relaxed);
  if (ptr >= geometryQuadCapacity || total > geometryQuadCapacity - ptr) {
    atomic_fetch_add_explicit(&stats[4], 1u, memory_order_relaxed);
    return;
  }
  uint sampleCount = min(total, 4u);
  for (uint i = 0; i < sampleCount; i++) {
    ulong quad = quads[ptr + i];
    uint modelId = uint((quad >> 26) & 0xfffful);
    if (modelId >= 65536u) {
      atomic_fetch_add_explicit(&stats[5], 1u, memory_order_relaxed);
    } else if (modelPresent[modelId] == 0u) {
      atomic_fetch_add_explicit(&stats[3], 1u, memory_order_relaxed);
    } else {
      BlockModel model = models[modelId];
      (void)model.faceData[uint((quad >> 0) & 7ul) % 6u];
      atomic_fetch_add_explicit(&stats[2], 1u, memory_order_relaxed);
    }
  }
}
