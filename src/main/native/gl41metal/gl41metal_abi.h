#pragma once

#include <cstddef>
#include <cstdint>

namespace gl41metal {

constexpr int MAX_LOD_ITERATIONS = 5;
constexpr size_t NODE_BYTES = 16;
constexpr size_t SECTION_METADATA_BYTES = 32;
constexpr size_t WORKLIST_ITEM_BYTES = 16;
constexpr size_t SCENE_UNIFORM_BYTES = 224;
constexpr uint32_t MAX_QUADS_PER_RANGE = 16380;
// CPU range builder uses the same coarse far-to-near buckets as GL46 translucent sorting.
constexpr int TRANSLUCENT_BUCKET_COUNT = 1024;

struct alignas(16) SceneUniformHost {
  float traversalMvp[16];
  float drawMvp[16];
  int32_t baseSectionFrame[4];
  float cameraSubPos[4];
  float renderParams[4];
  uint32_t queueSizes[4];
  uint32_t viewport[4];
  uint32_t rasterLimits[4];
};

static_assert(sizeof(SceneUniformHost) == SCENE_UNIFORM_BYTES);
static_assert(offsetof(SceneUniformHost, traversalMvp) == 0);
static_assert(offsetof(SceneUniformHost, drawMvp) == 64);
static_assert(offsetof(SceneUniformHost, baseSectionFrame) == 128);
static_assert(offsetof(SceneUniformHost, cameraSubPos) == 144);
static_assert(offsetof(SceneUniformHost, renderParams) == 160);
static_assert(offsetof(SceneUniformHost, queueSizes) == 176);
static_assert(offsetof(SceneUniformHost, viewport) == 192);
static_assert(offsetof(SceneUniformHost, rasterLimits) == 208);

}  // namespace gl41metal
