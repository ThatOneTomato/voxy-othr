#pragma once

#include <cstddef>
#include <cstdint>

namespace gl41metal {

constexpr uint64_t MODEL_SIZE = 64;
constexpr uint64_t MODEL_COUNT = 1ull << 16;
constexpr int MODEL_TEXTURE_SIZE = 16;
constexpr int MAX_LOD_ITERATIONS = 5;
constexpr size_t NODE_BYTES = 16;
constexpr size_t SECTION_METADATA_BYTES = 32;
constexpr size_t WORKLIST_ITEM_BYTES = 16;
constexpr size_t QUAD_DRAW_REF_BYTES = 16;
constexpr size_t SCENE_UNIFORM_BYTES = 224;
constexpr int OUTPUT_MODE_SHARED_GBUFFER = 0;
constexpr int OUTPUT_MODE_DRAWLIST = 1;

// Distance-bucket count for the translucent section sort, mirroring GL46
// buildtranslucents.comp's TRANSLUCENT_WRITE_BASE (1024). Translucent sections
// are binned by Manhattan distance into these buckets and rasterised far->near
// so the Metal over-blend accumulates correctly.
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

// Uniforms for the distant SSAO pass; must match SsaoUniform in ssao.metal.
// proj/invProj are the Voxy projection alone (view-space reconstruction),
// modelView rotates the world-space face normal into view space. The Java side
// writes the 3 matrices contiguously (48 floats, JOML column-major) at
// ssaoMatricesAddress; submitTraversal fills params.
constexpr size_t SSAO_UNIFORM_BYTES = 208;

struct alignas(16) SsaoUniformHost {
  float proj[16];
  float invProj[16];
  float modelView[16];
  uint32_t params[4];  // .x = sample steps, .yzw unused
};

static_assert(sizeof(SsaoUniformHost) == SSAO_UNIFORM_BYTES);
static_assert(offsetof(SsaoUniformHost, proj) == 0);
static_assert(offsetof(SsaoUniformHost, invProj) == 64);
static_assert(offsetof(SsaoUniformHost, modelView) == 128);
static_assert(offsetof(SsaoUniformHost, params) == 192);

}  // namespace gl41metal
