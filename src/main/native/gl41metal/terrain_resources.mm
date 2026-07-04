#include "gl41metal_internal.h"

using namespace gl41metal;

namespace gl41metal {

uint64_t alignUp(uint64_t value, uint64_t alignment) {
  return (value + alignment - 1) & ~(alignment - 1);
}

struct AtlasMipUpload {
  uint64_t sourceOffset;
  uint64_t stagingOffset;
  NSUInteger bytesPerRow;
  NSUInteger width;
  NSUInteger height;
  NSUInteger destinationX;
  NSUInteger destinationY;
  NSUInteger mip;
};

void clearTerrainCounters(TerrainResources* terrain) {
  terrain->residentSections = 0;
  terrain->geometryBytes = 0;
  terrain->uploadedSections = 0;
  terrain->removedSections = 0;
  terrain->uploadedNodes = 0;
  terrain->uploadedTopNodes = 0;
  terrain->removedTopNodes = 0;
  terrain->uploadedModels = 0;
  terrain->uploadedBiomes = 0;
  terrain->uploadedGeometryBytes = 0;
  terrain->topNodeCount = 0;
  terrain->pendingRequests.clear();
  std::memset(terrain->lastValidation, 0, sizeof(terrain->lastValidation));
  std::memset(terrain->lastTraversal, 0, sizeof(terrain->lastTraversal));
  std::memset(terrain->lastRaster, 0, sizeof(terrain->lastRaster));
}

bool createSlotFrameResources(
    Slot* slot,
    NativeContext* context,
    TerrainResources* terrain,
    std::string* error) {
  auto frame = std::make_unique<FrameResources>();
  frame->queueMeta =
      [context->device newBufferWithLength:MAX_LOD_ITERATIONS * 4 * sizeof(uint32_t)
                                   options:MTLResourceStorageModeShared];
  frame->scratchQueueA =
      [context->device newBufferWithLength:static_cast<NSUInteger>(terrain->maxTraversalQueue) *
                                           sizeof(uint32_t)
                                   options:MTLResourceStorageModeShared];
  frame->scratchQueueB =
      [context->device newBufferWithLength:static_cast<NSUInteger>(terrain->maxTraversalQueue) *
                                           sizeof(uint32_t)
                                   options:MTLResourceStorageModeShared];
  frame->requestQueue =
      [context->device newBufferWithLength:(1 + static_cast<NSUInteger>(terrain->maxTraversalRequests) * 2) *
                                           sizeof(uint32_t)
                                   options:MTLResourceStorageModeShared];
  frame->worklistCounter =
      [context->device newBufferWithLength:2 * sizeof(uint32_t)
                                   options:MTLResourceStorageModeShared];
  frame->worklist =
      [context->device newBufferWithLength:static_cast<NSUInteger>(terrain->maxWorklistItems) *
                                           WORKLIST_ITEM_BYTES
                                   options:MTLResourceStorageModeShared];
  frame->traversalStats =
      [context->device newBufferWithLength:8 * sizeof(uint32_t)
                                   options:MTLResourceStorageModeShared];
  frame->sceneUniform =
      [context->device newBufferWithLength:SCENE_UNIFORM_BYTES
                                   options:MTLResourceStorageModeShared];
  frame->drawArgs =
      [context->device newBufferWithLength:5 * sizeof(uint32_t)
                                   options:MTLResourceStorageModeShared];
  frame->translucentWorklistCounter =
      [context->device newBufferWithLength:2 * sizeof(uint32_t)
                                   options:MTLResourceStorageModeShared];
  frame->translucentWorklist =
      [context->device newBufferWithLength:static_cast<NSUInteger>(terrain->maxWorklistItems) *
                                           WORKLIST_ITEM_BYTES
                                   options:MTLResourceStorageModeShared];
  frame->translucentQuadRefs =
      [context->device newBufferWithLength:static_cast<NSUInteger>(terrain->maxRasterQuads) *
                                           QUAD_DRAW_REF_BYTES
                                   options:MTLResourceStorageModeShared];
  frame->translucentDrawArgs =
      [context->device newBufferWithLength:5 * sizeof(uint32_t)
                                   options:MTLResourceStorageModeShared];
  frame->translucentDistanceBuckets =
      [context->device newBufferWithLength:TRANSLUCENT_BUCKET_COUNT * sizeof(uint32_t)
                                   options:MTLResourceStorageModeShared];
  frame->meshIndirectArgs =
      [context->device newBufferWithLength:3 * sizeof(uint32_t)
                                   options:MTLResourceStorageModeShared];
  frame->translucentMeshIndirectArgs =
      [context->device newBufferWithLength:3 * sizeof(uint32_t)
                                   options:MTLResourceStorageModeShared];

  if (frame->queueMeta == nil || frame->scratchQueueA == nil || frame->scratchQueueB == nil ||
      frame->requestQueue == nil || frame->worklistCounter == nil || frame->worklist == nil ||
      frame->traversalStats == nil ||
      frame->sceneUniform == nil || frame->drawArgs == nil ||
      frame->translucentWorklistCounter == nil || frame->translucentWorklist == nil ||
      frame->translucentQuadRefs == nil || frame->translucentDrawArgs == nil ||
      frame->translucentDistanceBuckets == nil ||
      frame->meshIndirectArgs == nil || frame->translucentMeshIndirectArgs == nil) {
    *error = "GL41Metal could not allocate per-slot frame Metal buffers";
    return false;
  }

  clearSlotFrameResources(frame.get(), terrain);
  slot->frame = std::move(frame);
  return true;
}

void clearSlotFrameResources(FrameResources* frame, TerrainResources* terrain) {
  if (frame == nullptr) {
    return;
  }
  std::memset([frame->queueMeta contents], 0, MAX_LOD_ITERATIONS * 4 * sizeof(uint32_t));
  std::memset(
      [frame->scratchQueueA contents],
      0xff,
      static_cast<size_t>(terrain->maxTraversalQueue) * sizeof(uint32_t));
  std::memset(
      [frame->scratchQueueB contents],
      0xff,
      static_cast<size_t>(terrain->maxTraversalQueue) * sizeof(uint32_t));
  std::memset(
      [frame->requestQueue contents],
      0,
      (1 + static_cast<size_t>(terrain->maxTraversalRequests) * 2) * sizeof(uint32_t));
  std::memset([frame->worklistCounter contents], 0, 2 * sizeof(uint32_t));
  std::memset(
      [frame->worklist contents],
      0,
      static_cast<size_t>(terrain->maxWorklistItems) * WORKLIST_ITEM_BYTES);
  std::memset([frame->traversalStats contents], 0, 8 * sizeof(uint32_t));
  std::memset([frame->sceneUniform contents], 0, SCENE_UNIFORM_BYTES);
  std::memset([frame->drawArgs contents], 0, 5 * sizeof(uint32_t));
  std::memset([frame->translucentWorklistCounter contents], 0, 2 * sizeof(uint32_t));
  std::memset(
      [frame->translucentWorklist contents],
      0,
      static_cast<size_t>(terrain->maxWorklistItems) * WORKLIST_ITEM_BYTES);
  std::memset(
      [frame->translucentQuadRefs contents],
      0,
      static_cast<size_t>(terrain->maxRasterQuads) * QUAD_DRAW_REF_BYTES);
  std::memset([frame->translucentDrawArgs contents], 0, 5 * sizeof(uint32_t));
  std::memset(
      [frame->translucentDistanceBuckets contents], 0, TRANSLUCENT_BUCKET_COUNT * sizeof(uint32_t));
  std::memset([frame->meshIndirectArgs contents], 0, 3 * sizeof(uint32_t));
  std::memset([frame->translucentMeshIndirectArgs contents], 0, 3 * sizeof(uint32_t));
  [frame->queueMeta didModifyRange:NSMakeRange(0, MAX_LOD_ITERATIONS * 4 * sizeof(uint32_t))];
  [frame->scratchQueueA didModifyRange:NSMakeRange(
                                           0,
                                           static_cast<NSUInteger>(terrain->maxTraversalQueue) *
                                               sizeof(uint32_t))];
  [frame->scratchQueueB didModifyRange:NSMakeRange(
                                           0,
                                           static_cast<NSUInteger>(terrain->maxTraversalQueue) *
                                               sizeof(uint32_t))];
  [frame->requestQueue didModifyRange:NSMakeRange(
                                        0,
                                        (1 + static_cast<NSUInteger>(terrain->maxTraversalRequests) * 2) *
                                            sizeof(uint32_t))];
  [frame->worklistCounter didModifyRange:NSMakeRange(0, 2 * sizeof(uint32_t))];
  [frame->worklist didModifyRange:NSMakeRange(
                                      0,
                                      static_cast<NSUInteger>(terrain->maxWorklistItems) *
                                          WORKLIST_ITEM_BYTES)];
  [frame->traversalStats didModifyRange:NSMakeRange(0, 8 * sizeof(uint32_t))];
  [frame->sceneUniform didModifyRange:NSMakeRange(0, SCENE_UNIFORM_BYTES)];
  [frame->drawArgs didModifyRange:NSMakeRange(0, 5 * sizeof(uint32_t))];
  [frame->translucentWorklistCounter didModifyRange:NSMakeRange(0, 2 * sizeof(uint32_t))];
  [frame->translucentWorklist didModifyRange:NSMakeRange(
                                                 0,
                                                 static_cast<NSUInteger>(terrain->maxWorklistItems) *
                                                     WORKLIST_ITEM_BYTES)];
  [frame->translucentQuadRefs didModifyRange:NSMakeRange(
                                                 0,
                                                 static_cast<NSUInteger>(terrain->maxRasterQuads) *
                                                     QUAD_DRAW_REF_BYTES)];
  [frame->translucentDrawArgs didModifyRange:NSMakeRange(0, 5 * sizeof(uint32_t))];
  [frame->translucentDistanceBuckets
      didModifyRange:NSMakeRange(0, TRANSLUCENT_BUCKET_COUNT * sizeof(uint32_t))];
  [frame->meshIndirectArgs didModifyRange:NSMakeRange(0, 3 * sizeof(uint32_t))];
  [frame->translucentMeshIndirectArgs didModifyRange:NSMakeRange(0, 3 * sizeof(uint32_t))];
}

void refreshTopNodeBuffer(TerrainResources* terrain) {
  uint32_t* buffer = reinterpret_cast<uint32_t*>([terrain->topNodeBuffer contents]);
  std::memset(
      buffer,
      0xff,
      static_cast<size_t>(terrain->maxTraversalQueue) * sizeof(uint32_t));
  size_t count = std::min(
      terrain->topNodes.size(), static_cast<size_t>(terrain->maxTraversalQueue));
  if (count != 0) {
    std::memcpy(buffer, terrain->topNodes.data(), count * sizeof(uint32_t));
  }
  terrain->topNodeCount = count;
  [terrain->topNodeBuffer didModifyRange:NSMakeRange(
                                             0,
                                             static_cast<NSUInteger>(terrain->maxTraversalQueue) *
                                                 sizeof(uint32_t))];
}

uint32_t sectionQuadCount(const uint32_t* meta) {
  return (meta[4] & 0xffffu) + (meta[4] >> 16) +
      (meta[5] & 0xffffu) + (meta[5] >> 16) +
      (meta[6] & 0xffffu) + (meta[6] >> 16) +
      (meta[7] & 0xffffu) + (meta[7] >> 16);
}

void updateSectionResidencyFromMetadata(TerrainResources* terrain, int sectionId, const uint32_t* meta) {
  uint32_t quadCount = sectionQuadCount(meta);
  TerrainSectionSlot& slot = terrain->sections[sectionId];
  uint64_t bytes = static_cast<uint64_t>(quadCount) * 8;
  if (quadCount == 0) {
    if (slot.resident) {
      terrain->residentSections--;
      terrain->geometryBytes -= slot.geometryBytes;
      slot = TerrainSectionSlot{};
      terrain->removedSections++;
    }
    return;
  }
  if (!slot.resident) {
    terrain->residentSections++;
  } else {
    terrain->geometryBytes -= slot.geometryBytes;
  }
  slot.resident = true;
  slot.geometryOffsetBytes = static_cast<uint64_t>(meta[3]) * 8;
  slot.geometryBytes = bytes;
  terrain->geometryBytes += bytes;
  terrain->uploadedSections++;
}

void clearTraversalScratch(FrameResources* frame, TerrainResources* terrain) {
  clearSlotFrameResources(frame, terrain);
}

}  // namespace gl41metal

extern "C" {

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_Gl41MetalNative_createTerrainResources(
    JNIEnv* env,
    jclass,
    jlong handle,
    jint maxSections,
    jlong geometryCapacityBytes,
    jint maxNodes,
    jint maxTraversalQueue,
    jint maxTraversalRequests,
    jint maxWorklistItems,
    jint maxRasterQuads,
    jint atlasWidth,
    jint atlasHeight,
    jint atlasMipLevels,
    jint meshBatchSize) {
  @autoreleasepool {
    NativeContext* context = requireContext(env, handle);
    if (context == nullptr) {
      return;
    }
    const bool atlasEnabled = atlasWidth > 0 || atlasHeight > 0 || atlasMipLevels > 0;
    if (maxSections <= 0 || geometryCapacityBytes <= 0 || maxNodes <= 0 ||
        maxTraversalQueue <= 0 || maxTraversalRequests <= 0 || maxWorklistItems <= 0 ||
        maxRasterQuads <= 0 ||
        (atlasEnabled && (atlasWidth <= 0 || atlasHeight <= 0 || atlasMipLevels <= 0))) {
      throwJava(env, "GL41Metal terrain resources received invalid dimensions");
      return;
    }
    if (context->terrain != nullptr) {
      return;
    }

    std::unique_ptr<TerrainResources> terrain = std::make_unique<TerrainResources>();
    terrain->maxSections = maxSections;
    terrain->geometryCapacityBytes = static_cast<uint64_t>(geometryCapacityBytes);
    terrain->maxNodes = maxNodes;
    terrain->maxTraversalQueue = maxTraversalQueue;
    terrain->maxTraversalRequests = maxTraversalRequests;
    terrain->maxWorklistItems = maxWorklistItems;
    terrain->maxRasterQuads = maxRasterQuads;
    terrain->atlasWidth = atlasWidth;
    terrain->atlasHeight = atlasHeight;
    terrain->atlasMipLevels = atlasMipLevels;
    terrain->sections.resize(static_cast<size_t>(maxSections));
    terrain->topNodes.reserve(static_cast<size_t>(std::min(maxNodes, maxTraversalQueue)));

    terrain->sectionMetadata =
        [context->device newBufferWithLength:static_cast<NSUInteger>(maxSections) * 32
                                     options:MTLResourceStorageModeShared];
    terrain->geometry =
        [context->device newBufferWithLength:static_cast<NSUInteger>(geometryCapacityBytes)
                                     options:MTLResourceStorageModeShared];
    terrain->nodeBuffer =
        [context->device newBufferWithLength:static_cast<NSUInteger>(maxNodes) * NODE_BYTES
                                     options:MTLResourceStorageModeShared];
    terrain->topNodeBuffer =
        [context->device newBufferWithLength:static_cast<NSUInteger>(maxTraversalQueue) *
                                             sizeof(uint32_t)
                                     options:MTLResourceStorageModeShared];
    terrain->modelBuffer =
        [context->device newBufferWithLength:MODEL_COUNT * MODEL_SIZE
                                     options:MTLResourceStorageModeShared];
    terrain->modelColourBuffer =
        [context->device newBufferWithLength:MODEL_COUNT * sizeof(uint32_t)
                                     options:MTLResourceStorageModeShared];
    terrain->modelPresentBuffer =
        [context->device newBufferWithLength:MODEL_COUNT * sizeof(uint32_t)
                                     options:MTLResourceStorageModeShared];
    terrain->validationStats =
        [context->device newBufferWithLength:6 * sizeof(uint32_t)
                                     options:MTLResourceStorageModeShared];
    terrain->validationMaxSections =
        [context->device newBufferWithLength:sizeof(uint32_t)
                                     options:MTLResourceStorageModeShared];
    terrain->validationGeometryQuadCapacity =
        [context->device newBufferWithLength:sizeof(uint32_t)
                                     options:MTLResourceStorageModeShared];

    if (terrain->sectionMetadata == nil || terrain->geometry == nil || terrain->nodeBuffer == nil ||
        terrain->topNodeBuffer == nil || terrain->modelBuffer == nil ||
        terrain->modelColourBuffer == nil || terrain->modelPresentBuffer == nil ||
        terrain->validationStats == nil || terrain->validationMaxSections == nil ||
        terrain->validationGeometryQuadCapacity == nil) {
      throwJava(env, "GL41Metal could not allocate terrain Metal buffers");
      return;
    }

    std::memset([terrain->sectionMetadata contents], 0, static_cast<size_t>(maxSections) * 32);
    std::memset([terrain->geometry contents], 0, static_cast<size_t>(geometryCapacityBytes));
    std::memset([terrain->nodeBuffer contents], 0xff, static_cast<size_t>(maxNodes) * NODE_BYTES);
    std::memset([terrain->topNodeBuffer contents], 0xff, static_cast<size_t>(maxTraversalQueue) * sizeof(uint32_t));
    std::memset([terrain->modelBuffer contents], 0, MODEL_COUNT * MODEL_SIZE);
    std::memset([terrain->modelColourBuffer contents], 0xff, MODEL_COUNT * sizeof(uint32_t));
    std::memset([terrain->modelPresentBuffer contents], 0, MODEL_COUNT * sizeof(uint32_t));
    *reinterpret_cast<uint32_t*>([terrain->validationMaxSections contents]) =
        static_cast<uint32_t>(maxSections);
    *reinterpret_cast<uint32_t*>([terrain->validationGeometryQuadCapacity contents]) =
        static_cast<uint32_t>(static_cast<uint64_t>(geometryCapacityBytes) / 8);

    if (atlasEnabled) {
      MTLTextureDescriptor* descriptor =
          [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatRGBA8Unorm
                                                             width:static_cast<NSUInteger>(atlasWidth)
                                                            height:static_cast<NSUInteger>(atlasHeight)
                                                         mipmapped:YES];
      descriptor.mipmapLevelCount = static_cast<NSUInteger>(atlasMipLevels);
      descriptor.usage = MTLTextureUsageShaderRead;
      descriptor.storageMode = MTLStorageModeShared;
      terrain->atlas = [context->device newTextureWithDescriptor:descriptor];
      if (terrain->atlas == nil) {
        throwJava(env, "GL41Metal could not allocate terrain model atlas");
        return;
      }
    }

    terrain->meshBatchSize = std::clamp(static_cast<uint32_t>(meshBatchSize), 16u, 64u);

    terrain->validationPipeline = createValidationPipeline(env, context);
    if (terrain->validationPipeline == nil) {
      return;
    }
    terrain->traversalPipeline = createTraversalPipeline(env, context);
    if (terrain->traversalPipeline == nil) {
      return;
    }
    if (!createTranslucentSortPipelines(env, context, terrain.get())) {
      return;
    }
    terrain->meshArgsPipeline = createMeshArgsPipeline(env, context);
    if (!createOpaqueMeshPipeline(env, context, terrain.get())) {
      return;
    }
    if (!createTranslucentMeshPipeline(env, context, terrain.get())) {
      return;
    }

    for (Slot& slot : context->slots) {
      std::string frameError;
      if (!createSlotFrameResources(&slot, context, terrain.get(), &frameError)) {
        throwJava(env, frameError);
        return;
      }
    }

    context->terrain = std::move(terrain);
  }
}

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_Gl41MetalNative_clearTerrainResources(
    JNIEnv* env,
    jclass,
    jlong handle) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr || context->terrain == nullptr) {
    return;
  }
  TerrainResources* terrain = context->terrain.get();
  std::memset([terrain->sectionMetadata contents], 0, static_cast<size_t>(terrain->maxSections) * 32);
  std::memset([terrain->geometry contents], 0, static_cast<size_t>(terrain->geometryCapacityBytes));
  std::memset([terrain->nodeBuffer contents], 0xff, static_cast<size_t>(terrain->maxNodes) * NODE_BYTES);
  std::memset(
      [terrain->topNodeBuffer contents],
      0xff,
      static_cast<size_t>(terrain->maxTraversalQueue) * sizeof(uint32_t));
  std::memset([terrain->modelBuffer contents], 0, MODEL_COUNT * MODEL_SIZE);
  std::memset([terrain->modelColourBuffer contents], 0xff, MODEL_COUNT * sizeof(uint32_t));
  std::memset([terrain->modelPresentBuffer contents], 0, MODEL_COUNT * sizeof(uint32_t));
  for (Slot& slot : context->slots) {
    clearSlotFrameResources(slot.frame.get(), terrain);
  }
  std::fill(terrain->sections.begin(), terrain->sections.end(), TerrainSectionSlot{});
  terrain->topNodes.clear();
  terrain->geometryCursorBytes = 0;
  clearTerrainCounters(terrain);
}

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_Gl41MetalNative_uploadModel(
    JNIEnv* env,
    jclass,
    jlong handle,
    jint modelId,
    jlong modelAddress,
    jlong modelBytes,
    jlong textureAddress,
    jlong textureBytes,
    jint,
    jint) {
  @autoreleasepool {
    NativeContext* context = requireContext(env, handle);
    if (context == nullptr || context->terrain == nullptr) {
      return;
    }
    if (modelId < 0 || modelId >= static_cast<jint>(MODEL_COUNT) || modelBytes < static_cast<jlong>(MODEL_SIZE)) {
      throwJava(env, "GL41Metal uploadModel received invalid model data");
      return;
    }
    TerrainResources* terrain = context->terrain.get();
    std::memcpy(
        static_cast<uint8_t*>([terrain->modelBuffer contents]) + static_cast<uint64_t>(modelId) * MODEL_SIZE,
        reinterpret_cast<const void*>(modelAddress),
        MODEL_SIZE);
    reinterpret_cast<uint32_t*>([terrain->modelPresentBuffer contents])[modelId] = 1;

    if (terrain->atlas != nil) {
      if (textureAddress == 0 || textureBytes <= 0) {
        throwJava(env, "GL41Metal uploadModel requires texture data when atlas uploads are enabled");
        return;
      }
      const uint8_t* texture = reinterpret_cast<const uint8_t*>(textureAddress);
      int atlasX = (modelId & 0xff) * MODEL_TEXTURE_SIZE * 3;
      int atlasY = ((modelId >> 8) & 0xff) * MODEL_TEXTURE_SIZE * 2;
      uint64_t textureOffset = 0;
      uint64_t stagingBytes = 0;
      std::vector<AtlasMipUpload> mipUploads;
      mipUploads.reserve(static_cast<size_t>(terrain->atlasMipLevels));
      for (int mip = 0; mip < terrain->atlasMipLevels; mip++) {
        int width = (MODEL_TEXTURE_SIZE * 3) >> mip;
        int height = (MODEL_TEXTURE_SIZE * 2) >> mip;
        uint64_t mipBytes = static_cast<uint64_t>(width) * static_cast<uint64_t>(height) * 4;
        if (textureOffset + mipBytes > static_cast<uint64_t>(textureBytes)) {
          throwJava(env, "GL41Metal uploadModel received a truncated texture payload");
          return;
        }
        NSUInteger bytesPerRow = static_cast<NSUInteger>(alignUp(static_cast<uint64_t>(width) * 4, 256));
        int mipAtlasHeight = terrain->atlasHeight >> mip;
        int mipAtlasX = atlasX >> mip;
        int mipAtlasY = atlasY >> mip;
        mipUploads.push_back(AtlasMipUpload{
            textureOffset,
            stagingBytes,
            bytesPerRow,
            static_cast<NSUInteger>(width),
            static_cast<NSUInteger>(height),
            static_cast<NSUInteger>(mipAtlasX),
            static_cast<NSUInteger>(mipAtlasHeight - mipAtlasY - height),
            static_cast<NSUInteger>(mip)});
        stagingBytes += static_cast<uint64_t>(bytesPerRow) * static_cast<uint64_t>(height);
        stagingBytes = alignUp(stagingBytes, 256);
        textureOffset += mipBytes;
      }
      id<MTLBuffer> staging =
          [context->device newBufferWithLength:static_cast<NSUInteger>(stagingBytes)
                                       options:MTLResourceStorageModeShared];
      if (staging == nil) {
        throwJava(env, "GL41Metal uploadModel could not allocate atlas staging buffer");
        return;
      }
      uint8_t* stagingBase = static_cast<uint8_t*>([staging contents]);
      for (const AtlasMipUpload& upload : mipUploads) {
        const uint8_t* mipSource = texture + upload.sourceOffset;
        uint8_t* mipDest = stagingBase + upload.stagingOffset;
        uint64_t sourceBytesPerRow = static_cast<uint64_t>(upload.width) * 4;
        for (NSUInteger row = 0; row < upload.height; row++) {
          std::memcpy(
              mipDest + (upload.height - row - 1) * upload.bytesPerRow,
              mipSource + static_cast<uint64_t>(row) * sourceBytesPerRow,
              sourceBytesPerRow);
        }
      }
      [staging didModifyRange:NSMakeRange(0, static_cast<NSUInteger>(stagingBytes))];

      id<MTLCommandBuffer> commandBuffer = [context->queue commandBuffer];
      if (commandBuffer == nil) {
        throwJava(env, "GL41Metal uploadModel atlas blit commandBuffer returned nil");
        return;
      }
      commandBuffer.label = @"Voxy Atlas Upload";
      id<MTLBlitCommandEncoder> blit = [commandBuffer blitCommandEncoder];
      if (blit == nil) {
        throwJava(env, "GL41Metal uploadModel atlas blit encoder returned nil");
        return;
      }
      blit.label = @"Voxy Atlas Mip Blit";
      for (const AtlasMipUpload& upload : mipUploads) {
        [blit copyFromBuffer:staging
                sourceOffset:static_cast<NSUInteger>(upload.stagingOffset)
           sourceBytesPerRow:upload.bytesPerRow
         sourceBytesPerImage:upload.bytesPerRow * upload.height
                  sourceSize:MTLSizeMake(upload.width, upload.height, 1)
                   toTexture:terrain->atlas
            destinationSlice:0
            destinationLevel:upload.mip
           destinationOrigin:MTLOriginMake(upload.destinationX, upload.destinationY, 0)];
      }
      [blit endEncoding];
      NativeContext* capturedContext = context;
      [commandBuffer addCompletedHandler:^(id<MTLCommandBuffer> buffer) {
        (void)staging;
        if (buffer.status == MTLCommandBufferStatusError && buffer.error != nil) {
          std::lock_guard<std::mutex> lock(capturedContext->mutex);
          capturedContext->asyncFailure = [[buffer.error localizedDescription] UTF8String];
        }
      }];
      [commandBuffer commit];
    }
    terrain->uploadedModels++;
  }
}

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_Gl41MetalNative_uploadBiomeData(
    JNIEnv* env,
    jclass,
    jlong handle,
    jlong colourAddress,
    jlong colourBytes,
    jlong modelBiomePairsAddress,
    jlong modelBiomePairsBytes) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr || context->terrain == nullptr) {
    return;
  }
  if (colourBytes < 0 || modelBiomePairsBytes < 0 ||
      static_cast<uint64_t>(colourBytes) > MODEL_COUNT * sizeof(uint32_t)) {
    throwJava(env, "GL41Metal uploadBiomeData received invalid sizes");
    return;
  }
  TerrainResources* terrain = context->terrain.get();
  const uint64_t* pairs = reinterpret_cast<const uint64_t*>(modelBiomePairsAddress);
  uint64_t pairCount = static_cast<uint64_t>(modelBiomePairsBytes) / sizeof(uint64_t);
  uint32_t minBiomeBase = 0;
  if (pairCount != 0) {
    minBiomeBase = UINT32_MAX;
    for (uint64_t i = 0; i < pairCount; i++) {
      minBiomeBase = std::min<uint32_t>(minBiomeBase, static_cast<uint32_t>(pairs[i] >> 32));
    }
  }
  if (static_cast<uint64_t>(minBiomeBase) * sizeof(uint32_t) + static_cast<uint64_t>(colourBytes) >
      MODEL_COUNT * sizeof(uint32_t)) {
    throwJava(env, "GL41Metal uploadBiomeData range exceeds model colour buffer");
    return;
  }
  std::memcpy(
      static_cast<uint8_t*>([terrain->modelColourBuffer contents]) +
          static_cast<uint64_t>(minBiomeBase) * sizeof(uint32_t),
      reinterpret_cast<const void*>(colourAddress),
      static_cast<size_t>(colourBytes));
  uint8_t* modelBase = static_cast<uint8_t*>([terrain->modelBuffer contents]);
  for (uint64_t i = 0; i < pairCount; i++) {
    uint32_t modelId = static_cast<uint32_t>(pairs[i] & 0xffffffffu);
    uint32_t biomeBase = static_cast<uint32_t>(pairs[i] >> 32);
    if (modelId < MODEL_COUNT) {
      std::memcpy(modelBase + static_cast<uint64_t>(modelId) * MODEL_SIZE + 28, &biomeBase, sizeof(uint32_t));
    }
  }
  terrain->uploadedBiomes++;
}

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_Gl41MetalNative_uploadSection(
    JNIEnv* env,
    jclass,
    jlong handle,
    jint sectionId,
    jlong sectionPos,
    jint aabb,
    jint childExistence,
    jlong offsetsAddress,
    jlong offsetsBytes,
    jlong geometryAddress,
    jlong geometryBytes) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr || context->terrain == nullptr) {
    return;
  }
  TerrainResources* terrain = context->terrain.get();
  if (sectionId < 0 || sectionId >= terrain->maxSections || offsetsBytes < 8 * static_cast<jlong>(sizeof(uint32_t)) ||
      geometryBytes <= 0 || (geometryBytes & 7) != 0) {
    throwJava(env, "GL41Metal uploadSection received invalid section data");
    return;
  }
  uint64_t geometrySize = static_cast<uint64_t>(geometryBytes);
  uint64_t geometryOffset = (terrain->geometryCursorBytes + 7ull) & ~7ull;
  if (geometryOffset + geometrySize > terrain->geometryCapacityBytes) {
    throwJava(env, "GL41Metal terrain geometry arena is full");
    return;
  }

  TerrainSectionSlot& slot = terrain->sections[sectionId];
  if (!slot.resident) {
    terrain->residentSections++;
  } else {
    terrain->geometryBytes -= slot.geometryBytes;
  }

  // TODO(gl41metal): replace this bump allocator with a reclaiming arena and async upload staging.
  std::memcpy(
      static_cast<uint8_t*>([terrain->geometry contents]) + geometryOffset,
      reinterpret_cast<const void*>(geometryAddress),
      static_cast<size_t>(geometrySize));
  terrain->geometryCursorBytes = geometryOffset + geometrySize;
  slot.resident = true;
  slot.geometryOffsetBytes = geometryOffset;
  slot.geometryBytes = geometrySize;
  terrain->geometryBytes += geometrySize;
  terrain->uploadedSections++;
  terrain->uploadedGeometryBytes += geometrySize;

  const uint32_t* offsets = reinterpret_cast<const uint32_t*>(offsetsAddress);
  uint32_t itemCount = static_cast<uint32_t>(geometrySize / 8);
  uint32_t geometryPtr = static_cast<uint32_t>(geometryOffset / 8);
  uint32_t* meta =
      reinterpret_cast<uint32_t*>(static_cast<uint8_t*>([terrain->sectionMetadata contents]) +
                                  static_cast<uint64_t>(sectionId) * 32);
  meta[0] = static_cast<uint32_t>(static_cast<uint64_t>(sectionPos) >> 32);
  meta[1] = static_cast<uint32_t>(sectionPos);
  meta[2] = static_cast<uint32_t>(aabb);
  meta[3] = geometryPtr + offsets[0];
  meta[4] = (offsets[1] - offsets[0]) | ((offsets[2] - offsets[1]) << 16);
  meta[5] = (offsets[3] - offsets[2]) | ((offsets[4] - offsets[3]) << 16);
  meta[6] = (offsets[5] - offsets[4]) | ((offsets[6] - offsets[5]) << 16);
  meta[7] = (offsets[7] - offsets[6]) | ((itemCount - offsets[7]) << 16);
  (void)childExistence;
}

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_Gl41MetalNative_removeSection(
    JNIEnv* env,
    jclass,
    jlong handle,
    jint sectionId) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr || context->terrain == nullptr) {
    return;
  }
  TerrainResources* terrain = context->terrain.get();
  if (sectionId < 0 || sectionId >= terrain->maxSections) {
    throwJava(env, "GL41Metal removeSection received an invalid section id");
    return;
  }
  TerrainSectionSlot& slot = terrain->sections[sectionId];
  if (slot.resident) {
    slot.resident = false;
    terrain->residentSections--;
    terrain->geometryBytes -= slot.geometryBytes;
    terrain->removedSections++;
  }
  std::memset(
      static_cast<uint8_t*>([terrain->sectionMetadata contents]) + static_cast<uint64_t>(sectionId) * 32,
      0,
      32);
}

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_Gl41MetalNative_uploadNode(
    JNIEnv* env,
    jclass,
    jlong handle,
    jint nodeId,
    jlong nodeAddress) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr || context->terrain == nullptr) {
    return;
  }
  TerrainResources* terrain = context->terrain.get();
  if (nodeId < 0 || nodeId >= terrain->maxNodes || nodeAddress == 0) {
    throwJava(env, "GL41Metal uploadNode received invalid data");
    return;
  }
  std::memcpy(
      static_cast<uint8_t*>([terrain->nodeBuffer contents]) + static_cast<uint64_t>(nodeId) * NODE_BYTES,
      reinterpret_cast<const void*>(nodeAddress),
      NODE_BYTES);
  [terrain->nodeBuffer didModifyRange:NSMakeRange(
                                          static_cast<NSUInteger>(nodeId) * NODE_BYTES,
                                          NODE_BYTES)];
  terrain->uploadedNodes++;
}

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_Gl41MetalNative_uploadSectionMetadata(
    JNIEnv* env,
    jclass,
    jlong handle,
    jint sectionId,
    jlong metadataAddress) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr || context->terrain == nullptr) {
    return;
  }
  TerrainResources* terrain = context->terrain.get();
  if (sectionId < 0 || sectionId >= terrain->maxSections || metadataAddress == 0) {
    throwJava(env, "GL41Metal uploadSectionMetadata received invalid data");
    return;
  }
  uint8_t* destination = static_cast<uint8_t*>([terrain->sectionMetadata contents]) +
      static_cast<uint64_t>(sectionId) * SECTION_METADATA_BYTES;
  std::memcpy(destination, reinterpret_cast<const void*>(metadataAddress), SECTION_METADATA_BYTES);
  [terrain->sectionMetadata didModifyRange:NSMakeRange(
                                             static_cast<NSUInteger>(sectionId) * SECTION_METADATA_BYTES,
                                             SECTION_METADATA_BYTES)];
  updateSectionResidencyFromMetadata(
      terrain, sectionId, reinterpret_cast<const uint32_t*>(metadataAddress));
}

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_Gl41MetalNative_uploadGeometry(
    JNIEnv* env,
    jclass,
    jlong handle,
    jint geometryElementOffset,
    jlong geometryAddress,
    jlong geometryBytes) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr || context->terrain == nullptr) {
    return;
  }
  TerrainResources* terrain = context->terrain.get();
  if (geometryElementOffset < 0 || geometryAddress == 0 || geometryBytes < 0 || (geometryBytes & 7) != 0) {
    throwJava(env, "GL41Metal uploadGeometry received invalid data");
    return;
  }
  uint64_t byteOffset = static_cast<uint64_t>(geometryElementOffset) * 8;
  uint64_t bytes = static_cast<uint64_t>(geometryBytes);
  if (byteOffset > terrain->geometryCapacityBytes || bytes > terrain->geometryCapacityBytes - byteOffset) {
    throwJava(env, "GL41Metal uploadGeometry exceeded the geometry arena");
    return;
  }
  std::memcpy(
      static_cast<uint8_t*>([terrain->geometry contents]) + byteOffset,
      reinterpret_cast<const void*>(geometryAddress),
      static_cast<size_t>(bytes));
  [terrain->geometry didModifyRange:NSMakeRange(static_cast<NSUInteger>(byteOffset), static_cast<NSUInteger>(bytes))];
  terrain->uploadedGeometryBytes += bytes;
}

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_Gl41MetalNative_addTopNode(
    JNIEnv* env,
    jclass,
    jlong handle,
    jint nodeId) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr || context->terrain == nullptr) {
    return;
  }
  TerrainResources* terrain = context->terrain.get();
  if (nodeId < 0 || nodeId >= terrain->maxNodes) {
    throwJava(env, "GL41Metal addTopNode received invalid node id");
    return;
  }
  if (terrain->topNodes.size() >= static_cast<size_t>(terrain->maxTraversalQueue)) {
    throwJava(env, "GL41Metal top-node buffer is full");
    return;
  }
  uint32_t id = static_cast<uint32_t>(nodeId);
  if (std::find(terrain->topNodes.begin(), terrain->topNodes.end(), id) == terrain->topNodes.end()) {
    terrain->topNodes.push_back(id);
    terrain->uploadedTopNodes++;
    refreshTopNodeBuffer(terrain);
  }
}

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_Gl41MetalNative_removeTopNode(
    JNIEnv* env,
    jclass,
    jlong handle,
    jint nodeId) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr || context->terrain == nullptr) {
    return;
  }
  TerrainResources* terrain = context->terrain.get();
  uint32_t id = static_cast<uint32_t>(nodeId);
  auto iter = std::find(terrain->topNodes.begin(), terrain->topNodes.end(), id);
  if (iter != terrain->topNodes.end()) {
    terrain->topNodes.erase(iter);
    terrain->removedTopNodes++;
    refreshTopNodeBuffer(terrain);
  }
}

JNIEXPORT jlongArray JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_Gl41MetalNative_pollTraversalRequests(
    JNIEnv* env,
    jclass,
    jlong handle) {
  NativeContext* context = requireContext(env, handle);
  std::vector<uint64_t> requests;
  if (context != nullptr && context->terrain != nullptr) {
    std::lock_guard<std::mutex> lock(context->mutex);
    requests.swap(context->terrain->pendingRequests);
  }
  jlongArray result = env->NewLongArray(static_cast<jsize>(requests.size()));
  if (result != nullptr && !requests.empty()) {
    env->SetLongArrayRegion(
        result,
        0,
        static_cast<jsize>(requests.size()),
        reinterpret_cast<const jlong*>(requests.data()));
  }
  return result;
}

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_Gl41MetalNative_clearTraversalWorklist(
    JNIEnv* env,
    jclass,
    jlong handle) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr || context->terrain == nullptr) {
    return;
  }
  std::lock_guard<std::mutex> lock(context->mutex);
  TerrainResources* terrain = context->terrain.get();
  terrain->pendingRequests.clear();
  std::memset(terrain->lastTraversal, 0, sizeof(terrain->lastTraversal));
  std::memset(terrain->lastRaster, 0, sizeof(terrain->lastRaster));
  for (Slot& slot : context->slots) {
    clearTraversalScratch(slot.frame.get(), terrain);
  }
}

JNIEXPORT jlongArray JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_Gl41MetalNative_getTerrainStats(
    JNIEnv* env,
    jclass,
    jlong handle) {
  NativeContext* context = requireContext(env, handle);
  jlong values[32] = {};
  if (context != nullptr && context->terrain != nullptr) {
    TerrainResources* terrain = context->terrain.get();
    values[0] = static_cast<jlong>(terrain->residentSections);
    values[1] = static_cast<jlong>(terrain->geometryBytes);
    values[2] = static_cast<jlong>(terrain->uploadedSections);
    values[3] = static_cast<jlong>(terrain->removedSections);
    values[4] = static_cast<jlong>(terrain->uploadedModels);
    values[5] = static_cast<jlong>(terrain->uploadedBiomes);
    values[6] = static_cast<jlong>(terrain->uploadedGeometryBytes);
    values[7] = static_cast<jlong>(terrain->lastValidation[0]);
    values[8] = static_cast<jlong>(terrain->lastValidation[1]);
    values[9] = static_cast<jlong>(terrain->lastValidation[2]);
    values[10] = static_cast<jlong>(terrain->lastValidation[3]);
    values[11] = static_cast<jlong>(terrain->lastValidation[4]);
    values[12] = static_cast<jlong>(terrain->lastValidation[5]);
    values[13] = static_cast<jlong>(terrain->uploadedNodes);
    values[14] = static_cast<jlong>(terrain->uploadedTopNodes);
    values[15] = static_cast<jlong>(terrain->removedTopNodes);
    values[16] = static_cast<jlong>(terrain->topNodeCount);
    values[17] = static_cast<jlong>(terrain->lastTraversal[0]);
    values[18] = static_cast<jlong>(terrain->lastTraversal[1]);
    values[19] = static_cast<jlong>(terrain->lastTraversal[2]);
    values[20] = static_cast<jlong>(terrain->lastTraversal[3]);
    values[21] = static_cast<jlong>(terrain->lastTraversal[5]);
    values[22] = static_cast<jlong>(terrain->lastTraversal[6]);
    values[23] = static_cast<jlong>(terrain->pendingRequests.size());
    values[24] = static_cast<jlong>(terrain->lastRaster[0]);
    values[25] = static_cast<jlong>(terrain->lastRaster[1]);
    values[26] = static_cast<jlong>(terrain->lastRaster[2]);
    values[27] = static_cast<jlong>(terrain->lastRaster[3]);
    values[28] = static_cast<jlong>(terrain->lastRaster[4]);
    values[29] = static_cast<jlong>(terrain->lastRaster[5]);
    values[30] = static_cast<jlong>(terrain->lastRaster[6]);
    values[31] = static_cast<jlong>(terrain->lastRaster[7]);
  }
  jlongArray result = env->NewLongArray(32);
  if (result != nullptr) {
    env->SetLongArrayRegion(result, 0, 32, values);
  }
  return result;
}

}  // extern "C"
