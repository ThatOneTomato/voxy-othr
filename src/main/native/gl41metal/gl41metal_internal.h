#pragma once

#include <jni.h>

#import <Foundation/Foundation.h>
#import <IOSurface/IOSurface.h>
#import <Metal/Metal.h>
#import <OpenGL/CGLCurrent.h>
#import <OpenGL/CGLIOSurface.h>
#import <OpenGL/gl3.h>
#import <OpenGL/glext.h>
#import <CoreVideo/CoreVideo.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#include "gl41metal_abi.h"

namespace gl41metal {

struct FormatSpec {
  const char* name;
  OSType ioSurfaceFormat;
  size_t bytesPerElement;
  MTLPixelFormat metalFormat;
  GLenum cglInternalFormat;
  GLenum glFormat;
  GLenum glType;
};

// GL41Metal packs distant terrain into 3 shared textures (see quad_raster.metal's
// QuadFragmentOut for the authoritative per-channel layout). Apple GL4.1 only exposes 16
// fragment texture units and SIGSEGVs at exactly 16; the Iris bridge program also binds the
// shader pack's samplers (up to 12), so the distant reconstruction must stay at <= 3 samplers.
extern const FormatSpec GBUFFER0_FORMAT;  // RGBA32F: atlas uv.xy + quad tile.zw
extern const FormatSpec GBUFFER1_FORMAT;  // RGBA32F: depth + modelId + customId(lo/hi)
extern const FormatSpec GBUFFER2_FORMAT;  // RGBA32F: packed albedo/light/tint + face/flags/coverage
extern const FormatSpec TGBUFFER0_FORMAT;  // RGBA32F: front translucent albedo/light/tint + face/flags/cov
extern const FormatSpec TGBUFFER1_FORMAT;  // RGBA32F: front translucent depth/alpha + customId(lo/hi)
extern const FormatSpec TGBUFFER_ACCUM_FORMAT;  // RGBA32F: back->front over-blended flat rgb + alpha

enum class SlotState {
  Free,
  MetalSubmitted,
  MetalReady,
  GlSampling,
  Retiring,
};

struct SharedTexture {
  IOSurfaceRef surface = nullptr;
  id<MTLTexture> metalTexture = nil;
  GLuint glTexture = 0;
  GLenum glTarget = GL_TEXTURE_RECTANGLE;

  ~SharedTexture();
};

struct FrameResources {
  id<MTLBuffer> queueMeta = nil;
  id<MTLBuffer> scratchQueueA = nil;
  id<MTLBuffer> scratchQueueB = nil;
  id<MTLBuffer> requestQueue = nil;
  id<MTLBuffer> worklistCounter = nil;
  id<MTLBuffer> worklist = nil;
  id<MTLBuffer> traversalStats = nil;
  id<MTLBuffer> sceneUniform = nil;
  id<MTLBuffer> drawArgs = nil;
  // Translucent (group 0) pipeline mirrors of the opaque buffers above. The traversal kernel emits
  // translucent work items here (independent of the opaque worklist); the sort kernel bins them by
  // section distance into distanceBuckets and scatters them far->near into translucentQuadRefs so
  // the Metal over-blend accumulates correctly. Kept separate so the opaque path is unaffected.
  id<MTLBuffer> translucentWorklistCounter = nil;  // [0]=item count, [1]=quad cursor
  id<MTLBuffer> translucentWorklist = nil;
  id<MTLBuffer> translucentQuadRefs = nil;
  id<MTLBuffer> translucentDrawArgs = nil;
  id<MTLBuffer> translucentDistanceBuckets = nil;  // TRANSLUCENT_BUCKET_COUNT uints
  id<MTLBuffer> meshIndirectArgs = nil;
  id<MTLBuffer> translucentMeshIndirectArgs = nil;
};

struct Slot {
  SlotState state = SlotState::Free;
  int64_t frameId = -1;
  std::unique_ptr<SharedTexture> gbuffer0;
  std::unique_ptr<SharedTexture> gbuffer1;
  std::unique_ptr<SharedTexture> gbuffer2;
  // Translucent distant gbuffer (deferred-hybrid). tgbuffer0/1 carry the front-most translucent
  // surface attributes for strict pack water shading; tgbufferAccum carries the back->front
  // over-blended flat colour + accumulated alpha of all translucent layers. See quad_raster.metal's
  // TranslucentFragmentOut for the authoritative per-channel layout.
  std::unique_ptr<SharedTexture> tgbuffer0;
  std::unique_ptr<SharedTexture> tgbuffer1;
  std::unique_ptr<SharedTexture> tgbufferAccum;
  id<MTLTexture> renderDepth = nil;
  std::unique_ptr<FrameResources> frame;
};

struct TerrainSectionSlot {
  bool resident = false;
  uint64_t geometryOffsetBytes = 0;
  uint64_t geometryBytes = 0;
};

struct TerrainResources {
  int maxSections = 0;
  uint64_t geometryCapacityBytes = 0;
  int maxNodes = 0;
  int maxTraversalQueue = 0;
  int maxTraversalRequests = 0;
  int maxWorklistItems = 0;
  int maxRasterQuads = 0;
  int atlasWidth = 0;
  int atlasHeight = 0;
  int atlasMipLevels = 0;
  uint64_t geometryCursorBytes = 0;
  uint64_t topNodeCount = 0;
  uint64_t residentSections = 0;
  uint64_t geometryBytes = 0;
  uint64_t uploadedSections = 0;
  uint64_t removedSections = 0;
  uint64_t uploadedNodes = 0;
  uint64_t uploadedTopNodes = 0;
  uint64_t removedTopNodes = 0;
  uint64_t uploadedModels = 0;
  uint64_t uploadedBiomes = 0;
  uint64_t uploadedGeometryBytes = 0;
  uint32_t lastValidation[6] = {};
  uint32_t lastTraversal[8] = {};
  uint32_t lastRaster[8] = {};
  std::vector<TerrainSectionSlot> sections;
  std::vector<uint32_t> topNodes;
  std::vector<uint64_t> pendingRequests;
  id<MTLBuffer> sectionMetadata = nil;
  id<MTLBuffer> geometry = nil;
  id<MTLBuffer> nodeBuffer = nil;
  id<MTLBuffer> topNodeBuffer = nil;
  id<MTLBuffer> modelBuffer = nil;
  id<MTLBuffer> modelColourBuffer = nil;
  id<MTLBuffer> modelPresentBuffer = nil;
  id<MTLBuffer> validationStats = nil;
  id<MTLBuffer> validationMaxSections = nil;
  id<MTLBuffer> validationGeometryQuadCapacity = nil;
  id<MTLTexture> atlas = nil;
  id<MTLComputePipelineState> validationPipeline = nil;
  id<MTLComputePipelineState> traversalPipeline = nil;
  id<MTLDepthStencilState> quadDepthStencil = nil;
  // Translucent (group 0) distance sort + raster pipeline states.
  id<MTLComputePipelineState> translucentCountPipeline = nil;
  id<MTLComputePipelineState> translucentPrefixSumPipeline = nil;
  id<MTLComputePipelineState> translucentScatterPipeline = nil;
  id<MTLDepthStencilState> translucentDepthStencil = nil;
  id<MTLComputePipelineState> meshArgsPipeline = nil;
  id<MTLRenderPipelineState> opaqueMeshPipeline = nil;
  id<MTLRenderPipelineState> translucentMeshPipeline = nil;
  uint32_t meshBatchSize = 32;
};

struct NativeContext {
  id<MTLDevice> device = nil;
  id<MTLCommandQueue> queue = nil;
  id<MTLLibrary> shaderLibrary = nil;
  std::string deviceName;
  int width = 0;
  int height = 0;
  int completionDelayMs = 0;
  GLenum textureTarget = GL_TEXTURE_RECTANGLE;
  std::vector<Slot> slots;
  std::mutex mutex;
  std::condition_variable condition;
  int pendingCommandBuffers = 0;
  std::string asyncFailure;
  double lastMetalGpuTimeMs = 0.0;
  std::unique_ptr<TerrainResources> terrain;
};

void throwJava(JNIEnv* env, const std::string& message);
NativeContext* requireContext(JNIEnv* env, jlong handle);
id<MTLComputePipelineState> createComputePipeline(
    JNIEnv* env,
    NativeContext* context,
    NSString* functionName,
    const char* label);

bool createSlotTextures(Slot* slot, NativeContext* context, std::string* error);
bool createSlotFrameResources(Slot* slot, NativeContext* context, TerrainResources* terrain, std::string* error);
void clearSlotFrameResources(FrameResources* frame, TerrainResources* terrain);
void resetSubmittedSlot(NativeContext* context, int slotIndex);
void clearTerrainCounters(TerrainResources* terrain);
void clearTraversalScratch(FrameResources* frame, TerrainResources* terrain);
id<MTLComputePipelineState> createValidationPipeline(JNIEnv* env, NativeContext* context);
id<MTLComputePipelineState> createTraversalPipeline(JNIEnv* env, NativeContext* context);
bool createTranslucentSortPipelines(JNIEnv* env, NativeContext* context, TerrainResources* terrain);
id<MTLComputePipelineState> createMeshArgsPipeline(JNIEnv* env, NativeContext* context);
bool createOpaqueMeshPipeline(JNIEnv* env, NativeContext* context, TerrainResources* terrain);
bool createTranslucentMeshPipeline(JNIEnv* env, NativeContext* context, TerrainResources* terrain);

}  // namespace gl41metal
