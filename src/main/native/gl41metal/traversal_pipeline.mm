#include "gl41metal_internal.h"

using namespace gl41metal;

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_submitSynthetic(
    JNIEnv* env, jclass, jlong handle, jint slotIndex, jlong frameId);

namespace gl41metal {

id<MTLComputePipelineState> createTraversalPipeline(JNIEnv* env, NativeContext* context) {
  return createComputePipeline(env, context, @"traverse", "traversal");
}

// Binds the slot's 3 packed gbuffer textures to a render pass and clears them. Clear values
// matter for empty pixels and must match the QuadFragmentOut layout in quad_raster.metal:
//  * gbuffer1.x == 1 means "far depth" (a discard guard for the GL bridge).
//  * gbuffer2.w == 0 means "no fragment": the low bit of gbuffer2.w is the coverage flag, set
//    to 1 only by a real fragment, so a cleared 0 reads back as coverage == 0 on the GL side.
// Used by both the clear-only pass (when there is nothing to draw) and the real quad raster pass.
static void setupQuadGbufferAttachments(MTLRenderPassDescriptor* pass, const Slot& slot) {
  pass.colorAttachments[0].texture = slot.gbuffer0->metalTexture;
  pass.colorAttachments[0].loadAction = MTLLoadActionClear;
  pass.colorAttachments[0].storeAction = MTLStoreActionStore;
  pass.colorAttachments[0].clearColor = MTLClearColorMake(0.0, 0.0, 0.0, 0.0);
  pass.colorAttachments[1].texture = slot.gbuffer1->metalTexture;
  pass.colorAttachments[1].loadAction = MTLLoadActionClear;
  pass.colorAttachments[1].storeAction = MTLStoreActionStore;
  pass.colorAttachments[1].clearColor = MTLClearColorMake(1.0, 0.0, 0.0, 0.0);
  pass.colorAttachments[2].texture = slot.gbuffer2->metalTexture;
  pass.colorAttachments[2].loadAction = MTLLoadActionClear;
  pass.colorAttachments[2].storeAction = MTLStoreActionStore;
  pass.colorAttachments[2].clearColor = MTLClearColorMake(0.0, 0.0, 0.0, 0.0);
}

// Binds the slot's 3 translucent gbuffer targets starting at colorAttachments[base]. Clear values
// mirror the TranslucentFragmentOut layout: tgbuffer1.x == 1 is "far depth", tgbuffer0.w == 0 is
// "no fragment" (coverage low bit), and tgbufferAccum == 0 is "no accumulated translucency".
static void setupTranslucentGbufferAttachments(MTLRenderPassDescriptor* pass, const Slot& slot,
                                               int base) {
  pass.colorAttachments[base + 0].texture = slot.tgbuffer0->metalTexture;
  pass.colorAttachments[base + 0].loadAction = MTLLoadActionClear;
  pass.colorAttachments[base + 0].storeAction = MTLStoreActionStore;
  pass.colorAttachments[base + 0].clearColor = MTLClearColorMake(0.0, 0.0, 0.0, 0.0);
  pass.colorAttachments[base + 1].texture = slot.tgbuffer1->metalTexture;
  pass.colorAttachments[base + 1].loadAction = MTLLoadActionClear;
  pass.colorAttachments[base + 1].storeAction = MTLStoreActionStore;
  pass.colorAttachments[base + 1].clearColor = MTLClearColorMake(1.0, 0.0, 0.0, 0.0);
  pass.colorAttachments[base + 2].texture = slot.tgbufferAccum->metalTexture;
  pass.colorAttachments[base + 2].loadAction = MTLLoadActionClear;
  pass.colorAttachments[base + 2].storeAction = MTLStoreActionStore;
  pass.colorAttachments[base + 2].clearColor = MTLClearColorMake(0.0, 0.0, 0.0, 0.0);
}

}  // namespace gl41metal

extern "C" {

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_submitTraversal(
    JNIEnv* env, jclass, jlong handle, jint slotIndex, jlong frameId, jdouble cameraX,
    jdouble cameraY, jdouble cameraZ, jlong traversalMvpAddress, jlong drawMvpAddress,
    jfloat subDivisionSize, jfloat earthRadius, jfloat nearExclusionRadius,
    jfloat renderDistanceSquared, jint viewportWidth, jint viewportHeight,
    jlong ssaoMatricesAddress, jint ssaoSteps) {
  @autoreleasepool {
    NativeContext* context = requireContext(env, handle);
    if (context == nullptr) {
      return;
    }
    if (slotIndex < 0 || slotIndex >= static_cast<jint>(context->slots.size())) {
      throwJava(env, "GL41Metal traversal submit received an invalid slot index");
      return;
    }
    if (context->terrain == nullptr) {
      Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_submitSynthetic(
          env, nullptr, handle, slotIndex, frameId);
      return;
    }

    TerrainResources* terrain = context->terrain.get();
    {
      std::lock_guard<std::mutex> lock(context->mutex);
      Slot& slot = context->slots[slotIndex];
      if (slot.state != SlotState::Free) {
        throwJava(env, "GL41Metal traversal submit attempted to write a non-free slot");
        return;
      }
      slot.state = SlotState::MetalSubmitted;
      slot.frameId = frameId;
      context->pendingCommandBuffers++;
    }

    Slot& slot = context->slots[slotIndex];
    FrameResources* frame = slot.frame.get();
    if (frame == nullptr) {
      resetSubmittedSlot(context, slotIndex);
      throwJava(env, "GL41Metal traversal submit has no per-slot frame resources");
      return;
    }
    bool willOpaqueRaster = terrain->opaqueMeshPipeline != nil &&
                            terrain->meshArgsPipeline != nil && terrain->atlas != nil;
    bool willTranslucentRaster = willOpaqueRaster && terrain->translucentMeshPipeline != nil &&
                                 terrain->translucentCountPipeline != nil &&
                                 terrain->translucentPrefixSumPipeline != nil &&
                                 terrain->translucentScatterPipeline != nil &&
                                 terrain->translucentQuadsResident > 0;
    bool willSsao = willOpaqueRaster && ssaoSteps > 0 && terrain->ssaoPipeline != nil &&
                    ssaoMatricesAddress != 0;
    slot.translucentValid = willTranslucentRaster;

    // --- Per-pass GPU timing: each logical pass gets its own MTLCommandBuffer so its
    //     GPUEndTime - GPUStartTime gives isolated per-pass GPU time. All CBs are
    //     committed together at the end; if any encoding fails, none are committed and
    //     the slot is reset safely. ---

    // Phase 1: Clear (optional) + LOD Traversal compute
    id<MTLCommandBuffer> traversalCB = [context->queue commandBuffer];
    if (traversalCB == nil) {
      resetSubmittedSlot(context, slotIndex);
      throwJava(env, "GL41Metal traversal commandBuffer returned nil");
      return;
    }
    traversalCB.label =
        [NSString stringWithFormat:@"Voxy Traversal F%ld S%d", (long)frameId, (int)slotIndex];

    if (!willOpaqueRaster) {
      MTLRenderPassDescriptor* pass = [MTLRenderPassDescriptor renderPassDescriptor];
      setupQuadGbufferAttachments(pass, slot);
      pass.depthAttachment.texture = slot.renderDepth;
      pass.depthAttachment.loadAction = MTLLoadActionClear;
      pass.depthAttachment.storeAction = MTLStoreActionDontCare;
      pass.depthAttachment.clearDepth = 1.0;

      id<MTLRenderCommandEncoder> renderEncoder =
          [traversalCB renderCommandEncoderWithDescriptor:pass];
      if (renderEncoder == nil) {
        resetSubmittedSlot(context, slotIndex);
        throwJava(env, "GL41Metal traversal render encoder returned nil");
        return;
      }
      renderEncoder.label = @"Voxy GBuffer Clear";
      [renderEncoder endEncoding];
    }

    clearTraversalScratch(frame, terrain);
    uint32_t* queueMeta = reinterpret_cast<uint32_t*>([frame->queueMeta contents]);
    queueMeta[0] = static_cast<uint32_t>((terrain->topNodeCount + 31) >> 5);
    queueMeta[1] = 1;
    queueMeta[2] = 1;
    queueMeta[3] = static_cast<uint32_t>(std::min<uint64_t>(
        terrain->topNodeCount, static_cast<uint64_t>(terrain->maxTraversalQueue)));
    for (int i = 1; i < MAX_LOD_ITERATIONS; i++) {
      queueMeta[i * 4 + 1] = 1;
      queueMeta[i * 4 + 2] = 1;
    }

    if (traversalMvpAddress == 0 || drawMvpAddress == 0) {
      resetSubmittedSlot(context, slotIndex);
      throwJava(env, "GL41Metal traversal submit requires explicit GL46-aligned matrices");
      return;
    }
    uint8_t* scene = static_cast<uint8_t*>([frame->sceneUniform contents]);
    std::memcpy(scene, reinterpret_cast<const void*>(traversalMvpAddress), 64);
    std::memcpy(scene + 64, reinterpret_cast<const void*>(drawMvpAddress), 64);
    int camSecX = static_cast<int>(std::floor(cameraX)) >> 5;
    int camSecY = static_cast<int>(std::floor(cameraY)) >> 5;
    int camSecZ = static_cast<int>(std::floor(cameraZ)) >> 5;
    reinterpret_cast<int32_t*>(scene + 128)[0] = camSecX;
    reinterpret_cast<int32_t*>(scene + 128)[1] = camSecY;
    reinterpret_cast<int32_t*>(scene + 128)[2] = camSecZ;
    reinterpret_cast<uint32_t*>(scene + 128)[3] = static_cast<uint32_t>(frameId);
    reinterpret_cast<float*>(scene + 144)[0] =
        static_cast<float>(cameraX - static_cast<double>(camSecX << 5));
    reinterpret_cast<float*>(scene + 144)[1] =
        static_cast<float>(cameraY - static_cast<double>(camSecY << 5));
    reinterpret_cast<float*>(scene + 144)[2] =
        static_cast<float>(cameraZ - static_cast<double>(camSecZ << 5));
    float minScreenSpaceSize = 0.0f;
    if (viewportWidth > 0 && viewportHeight > 0) {
      float threshold = std::max(1.0f, static_cast<float>(subDivisionSize));
      minScreenSpaceSize =
          (threshold * threshold) / static_cast<float>(viewportWidth * viewportHeight);
    }
    reinterpret_cast<float*>(scene + 144)[3] = 0.0f;
    reinterpret_cast<float*>(scene + 160)[0] = minScreenSpaceSize;
    reinterpret_cast<float*>(scene + 160)[1] = earthRadius;
    reinterpret_cast<float*>(scene + 160)[2] =
        std::max(0.0f, static_cast<float>(nearExclusionRadius));
    // renderParams.w: GL46 isWithinRenderDistance / shouldRenderSelf XZ cylinder (blocks^2).
    reinterpret_cast<float*>(scene + 160)[3] =
        std::max(0.0f, static_cast<float>(renderDistanceSquared));
    reinterpret_cast<uint32_t*>(scene + 176)[0] = static_cast<uint32_t>(terrain->maxWorklistItems);
    reinterpret_cast<uint32_t*>(scene + 176)[1] =
        static_cast<uint32_t>(terrain->maxTraversalRequests);
    reinterpret_cast<uint32_t*>(scene + 176)[2] = static_cast<uint32_t>(terrain->maxTraversalQueue);
    reinterpret_cast<uint32_t*>(scene + 176)[3] = static_cast<uint32_t>(terrain->topNodeCount);
    reinterpret_cast<uint32_t*>(scene + 192)[0] = static_cast<uint32_t>(std::max(0, viewportWidth));
    reinterpret_cast<uint32_t*>(scene + 192)[1] =
        static_cast<uint32_t>(std::max(0, viewportHeight));
    reinterpret_cast<uint32_t*>(scene + 192)[2] = 0;
    reinterpret_cast<uint32_t*>(scene + 192)[3] = 0;
    reinterpret_cast<uint32_t*>(scene + 208)[0] = static_cast<uint32_t>(terrain->maxRasterQuads);
    reinterpret_cast<uint32_t*>(scene + 208)[1] = 0;
    reinterpret_cast<uint32_t*>(scene + 208)[2] = 0;
    reinterpret_cast<uint32_t*>(scene + 208)[3] = 0;

    if (terrain->topNodeCount > 0) {
      id<MTLComputeCommandEncoder> encoder = [traversalCB computeCommandEncoder];
      if (encoder == nil) {
        resetSubmittedSlot(context, slotIndex);
        throwJava(env, "GL41Metal traversal compute encoder returned nil");
        return;
      }
      encoder.label = @"Voxy LOD Traversal";
      [encoder setComputePipelineState:terrain->traversalPipeline];
      [encoder setBuffer:terrain->nodeBuffer offset:0 atIndex:0];
      [encoder setBuffer:terrain->sectionMetadata offset:0 atIndex:1];
      [encoder setBuffer:frame->queueMeta offset:0 atIndex:4];
      [encoder setBuffer:frame->requestQueue offset:0 atIndex:5];
      [encoder setBuffer:frame->worklistCounter offset:0 atIndex:6];
      [encoder setBuffer:frame->worklist offset:0 atIndex:7];
      [encoder setBuffer:frame->traversalStats offset:0 atIndex:8];
      [encoder setBuffer:frame->sceneUniform offset:0 atIndex:9];
      [encoder setBuffer:frame->requestQueue offset:sizeof(uint32_t) atIndex:11];
      [encoder setBuffer:frame->translucentWorklistCounter offset:0 atIndex:12];
      [encoder setBuffer:frame->translucentWorklist offset:0 atIndex:13];
      MTLSize threadsPerGroup = MTLSizeMake(32, 1, 1);
      for (uint32_t queueIdx = 0; queueIdx < MAX_LOD_ITERATIONS; queueIdx++) {
        id<MTLBuffer> source = nil;
        id<MTLBuffer> sink = nil;
        if (queueIdx == 0) {
          source = terrain->topNodeBuffer;
          sink = frame->scratchQueueA;
        } else if ((queueIdx & 1u) == 1u) {
          source = frame->scratchQueueA;
          sink = frame->scratchQueueB;
        } else {
          source = frame->scratchQueueB;
          sink = frame->scratchQueueA;
        }
        [encoder pushDebugGroup:[NSString stringWithFormat:@"Traversal Pass %u", queueIdx]];
        [encoder setBuffer:source offset:0 atIndex:2];
        [encoder setBuffer:sink offset:0 atIndex:3];
        [encoder setBytes:&queueIdx length:sizeof(queueIdx) atIndex:10];
        if (queueIdx == 0) {
          MTLSize groups = MTLSizeMake(std::max<uint32_t>(1, queueMeta[0]), 1, 1);
          [encoder dispatchThreadgroups:groups threadsPerThreadgroup:threadsPerGroup];
        } else {
          [encoder dispatchThreadgroupsWithIndirectBuffer:frame->queueMeta
                                     indirectBufferOffset:queueIdx * 4 * sizeof(uint32_t)
                                    threadsPerThreadgroup:threadsPerGroup];
        }
        [encoder memoryBarrierWithScope:MTLBarrierScopeBuffers];
        [encoder popDebugGroup];
      }
      [encoder endEncoding];
    }

    // Phase 2: Opaque raster (mesh args compute + quad mesh shader)
    id<MTLCommandBuffer> opaqueCB = nil;
    if (willOpaqueRaster) {
      opaqueCB = [context->queue commandBuffer];
      if (opaqueCB == nil) {
        resetSubmittedSlot(context, slotIndex);
        throwJava(env, "GL41Metal opaque raster commandBuffer returned nil");
        return;
      }
      opaqueCB.label =
          [NSString stringWithFormat:@"Voxy Opaque F%ld S%d", (long)frameId, (int)slotIndex];
      {
        id<MTLComputeCommandEncoder> meshArgsEncoder = [opaqueCB computeCommandEncoder];
        if (meshArgsEncoder == nil) {
          resetSubmittedSlot(context, slotIndex);
          throwJava(env, "GL41Metal mesh args encoder returned nil");
          return;
        }
        meshArgsEncoder.label = @"Voxy Prepare Mesh Args";
        [meshArgsEncoder setComputePipelineState:terrain->meshArgsPipeline];
        [meshArgsEncoder setBuffer:frame->worklistCounter offset:0 atIndex:0];
        [meshArgsEncoder setBuffer:frame->meshIndirectArgs offset:0 atIndex:1];
        [meshArgsEncoder setBuffer:frame->sceneUniform offset:0 atIndex:2];
        [meshArgsEncoder dispatchThreadgroups:MTLSizeMake(1, 1, 1)
                        threadsPerThreadgroup:MTLSizeMake(1, 1, 1)];
        [meshArgsEncoder endEncoding];
      }

      MTLRenderPassDescriptor* renderPass = [MTLRenderPassDescriptor renderPassDescriptor];
      setupQuadGbufferAttachments(renderPass, slot);
      renderPass.depthAttachment.texture = slot.renderDepth;
      renderPass.depthAttachment.loadAction = MTLLoadActionClear;
      renderPass.depthAttachment.clearDepth = 1.0;
      renderPass.depthAttachment.storeAction =
          (willSsao || willTranslucentRaster) ? MTLStoreActionStore : MTLStoreActionDontCare;

      id<MTLRenderCommandEncoder> quadEncoder =
          [opaqueCB renderCommandEncoderWithDescriptor:renderPass];
      if (quadEncoder == nil) {
        resetSubmittedSlot(context, slotIndex);
        throwJava(env, "GL41Metal quad render encoder returned nil");
        return;
      }
      quadEncoder.label = @"Voxy Opaque Quad Raster";
      [quadEncoder setFragmentTexture:terrain->atlas atIndex:0];
      [quadEncoder setObjectBuffer:frame->worklist offset:0 atIndex:0];
      [quadEncoder setObjectBuffer:frame->worklistCounter offset:0 atIndex:1];
      [quadEncoder setObjectBuffer:frame->sceneUniform offset:0 atIndex:2];
      [quadEncoder setObjectBuffer:terrain->sectionMetadata offset:0 atIndex:3];
      [quadEncoder setMeshBuffer:terrain->geometry offset:0 atIndex:0];
      [quadEncoder setMeshBuffer:terrain->modelBuffer offset:0 atIndex:1];
      [quadEncoder setMeshBuffer:terrain->modelColourBuffer offset:0 atIndex:2];
      [quadEncoder setMeshBuffer:terrain->modelPresentBuffer offset:0 atIndex:3];
      [quadEncoder setMeshBuffer:frame->sceneUniform offset:0 atIndex:4];

      [quadEncoder setRenderPipelineState:terrain->opaqueMeshPipeline];
      [quadEncoder setDepthStencilState:terrain->quadDepthStencil];
      [quadEncoder drawMeshThreadgroupsWithIndirectBuffer:frame->meshIndirectArgs
                                     indirectBufferOffset:0
                              threadsPerObjectThreadgroup:MTLSizeMake(1, 1, 1)
                                threadsPerMeshThreadgroup:MTLSizeMake(terrain->meshBatchSize * 4, 1,
                                                                     1)];

      [quadEncoder endEncoding];
    }

    // Phase 3: Distant SSAO (optional)
    id<MTLCommandBuffer> ssaoCB = nil;
    if (willSsao) {
      ssaoCB = [context->queue commandBuffer];
      if (ssaoCB == nil) {
        resetSubmittedSlot(context, slotIndex);
        throwJava(env, "GL41Metal SSAO commandBuffer returned nil");
        return;
      }
      ssaoCB.label =
          [NSString stringWithFormat:@"Voxy SSAO F%ld S%d", (long)frameId, (int)slotIndex];

      MTLRenderPassDescriptor* ssaoPass = [MTLRenderPassDescriptor renderPassDescriptor];
      ssaoPass.colorAttachments[0].texture = slot.gbuffer2->metalTexture;
      ssaoPass.colorAttachments[0].loadAction = MTLLoadActionLoad;
      ssaoPass.colorAttachments[0].storeAction = MTLStoreActionStore;

      id<MTLRenderCommandEncoder> ssaoEncoder =
          [ssaoCB renderCommandEncoderWithDescriptor:ssaoPass];
      if (ssaoEncoder == nil) {
        resetSubmittedSlot(context, slotIndex);
        throwJava(env, "GL41Metal SSAO render encoder returned nil");
        return;
      }
      ssaoEncoder.label = @"Voxy Distant SSAO";
      SsaoUniformHost ssaoUniform;
      std::memcpy(&ssaoUniform, reinterpret_cast<const void*>(ssaoMatricesAddress),
                  48 * sizeof(float));
      ssaoUniform.params[0] = static_cast<uint32_t>(ssaoSteps);
      ssaoUniform.params[1] = 0;
      ssaoUniform.params[2] = 0;
      ssaoUniform.params[3] = 0;
      [ssaoEncoder setRenderPipelineState:terrain->ssaoPipeline];
      [ssaoEncoder setFragmentTexture:slot.renderDepth atIndex:0];
      [ssaoEncoder setFragmentBytes:&ssaoUniform length:sizeof(ssaoUniform) atIndex:0];
      [ssaoEncoder drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:3];
      [ssaoEncoder endEncoding];
    }

    // Phase 4: Translucent sort + raster (optional)
    id<MTLCommandBuffer> translucentCB = nil;
    if (willTranslucentRaster) {
      translucentCB = [context->queue commandBuffer];
      if (translucentCB == nil) {
        resetSubmittedSlot(context, slotIndex);
        throwJava(env, "GL41Metal translucent commandBuffer returned nil");
        return;
      }
      translucentCB.label =
          [NSString stringWithFormat:@"Voxy Translucent F%ld S%d", (long)frameId, (int)slotIndex];

      id<MTLComputeCommandEncoder> sortEncoder = [translucentCB computeCommandEncoder];
      if (sortEncoder == nil) {
        resetSubmittedSlot(context, slotIndex);
        throwJava(env, "GL41Metal translucent sort compute encoder returned nil");
        return;
      }
      sortEncoder.label = @"Voxy Translucent Sort";
      MTLSize sortGroups = MTLSizeMake(
          std::max<uint32_t>(1, (static_cast<uint32_t>(terrain->maxWorklistItems) + 127) / 128), 1,
          1);
      MTLSize sortThreads = MTLSizeMake(128, 1, 1);

      [sortEncoder pushDebugGroup:@"Count Buckets"];
      [sortEncoder setComputePipelineState:terrain->translucentCountPipeline];
      [sortEncoder setBuffer:frame->translucentWorklist offset:0 atIndex:0];
      [sortEncoder setBuffer:frame->translucentWorklistCounter offset:0 atIndex:1];
      [sortEncoder setBuffer:terrain->sectionMetadata offset:0 atIndex:2];
      [sortEncoder setBuffer:frame->translucentDistanceBuckets offset:0 atIndex:3];
      [sortEncoder setBuffer:frame->sceneUniform offset:0 atIndex:4];
      [sortEncoder dispatchThreadgroups:sortGroups threadsPerThreadgroup:sortThreads];
      [sortEncoder memoryBarrierWithScope:MTLBarrierScopeBuffers];
      [sortEncoder popDebugGroup];

      [sortEncoder pushDebugGroup:@"Prefix Sum"];
      [sortEncoder setComputePipelineState:terrain->translucentPrefixSumPipeline];
      [sortEncoder setBuffer:frame->translucentDistanceBuckets offset:0 atIndex:0];
      [sortEncoder setBuffer:frame->translucentWorklistCounter offset:0 atIndex:1];
      [sortEncoder setBuffer:frame->translucentDrawArgs offset:0 atIndex:2];
      [sortEncoder setBuffer:frame->sceneUniform offset:0 atIndex:3];
      [sortEncoder setBuffer:frame->translucentMeshIndirectArgs offset:0 atIndex:4];
      uint32_t batchSize = terrain->meshBatchSize;
      [sortEncoder setBytes:&batchSize length:sizeof(batchSize) atIndex:5];
      [sortEncoder dispatchThreadgroups:MTLSizeMake(1, 1, 1)
                  threadsPerThreadgroup:MTLSizeMake(1, 1, 1)];
      [sortEncoder memoryBarrierWithScope:MTLBarrierScopeBuffers];
      [sortEncoder popDebugGroup];

      [sortEncoder pushDebugGroup:@"Scatter"];
      [sortEncoder setComputePipelineState:terrain->translucentScatterPipeline];
      [sortEncoder setBuffer:frame->translucentWorklist offset:0 atIndex:0];
      [sortEncoder setBuffer:frame->translucentWorklistCounter offset:0 atIndex:1];
      [sortEncoder setBuffer:terrain->sectionMetadata offset:0 atIndex:2];
      [sortEncoder setBuffer:frame->translucentDistanceBuckets offset:0 atIndex:3];
      [sortEncoder setBuffer:frame->translucentQuadRefs offset:0 atIndex:4];
      [sortEncoder setBuffer:frame->sceneUniform offset:0 atIndex:5];
      [sortEncoder dispatchThreadgroups:sortGroups threadsPerThreadgroup:sortThreads];
      [sortEncoder popDebugGroup];
      [sortEncoder endEncoding];

      MTLRenderPassDescriptor* translucentPass = [MTLRenderPassDescriptor renderPassDescriptor];
      setupTranslucentGbufferAttachments(translucentPass, slot, 0);
      translucentPass.depthAttachment.texture = slot.renderDepth;
      translucentPass.depthAttachment.loadAction = MTLLoadActionLoad;
      translucentPass.depthAttachment.storeAction = MTLStoreActionDontCare;

      id<MTLRenderCommandEncoder> translucentEncoder =
          [translucentCB renderCommandEncoderWithDescriptor:translucentPass];
      if (translucentEncoder == nil) {
        resetSubmittedSlot(context, slotIndex);
        throwJava(env, "GL41Metal translucent render encoder returned nil");
        return;
      }
      translucentEncoder.label = @"Voxy Translucent Quad Raster";
      [translucentEncoder setDepthStencilState:terrain->translucentDepthStencil];
      // No fixed-function culling: the mesher does not normalize quad winding per face direction,
      // so MTLCullModeFront (previously used to emulate vanilla's GL_BACK culling for translucent
      // blocks) also culled the exposed side of +Z/+X/-Y faces, removing water/stained-glass side
      // walls. voxy_translucent_fragment now discards fragments viewed from behind the quad's
      // semantic face instead, which is the correct single-sided behaviour.
      [translucentEncoder setCullMode:MTLCullModeNone];
      [translucentEncoder setFragmentTexture:terrain->atlas atIndex:0];
      [translucentEncoder setRenderPipelineState:terrain->translucentMeshPipeline];
      [translucentEncoder setObjectBuffer:frame->translucentDrawArgs offset:0 atIndex:0];
      [translucentEncoder setMeshBuffer:frame->translucentQuadRefs offset:0 atIndex:0];
      [translucentEncoder setMeshBuffer:terrain->geometry offset:0 atIndex:1];
      [translucentEncoder setMeshBuffer:terrain->sectionMetadata offset:0 atIndex:2];
      [translucentEncoder setMeshBuffer:terrain->modelBuffer offset:0 atIndex:3];
      [translucentEncoder setMeshBuffer:terrain->modelColourBuffer offset:0 atIndex:4];
      [translucentEncoder setMeshBuffer:terrain->modelPresentBuffer offset:0 atIndex:5];
      [translucentEncoder setMeshBuffer:frame->sceneUniform offset:0 atIndex:6];
      [translucentEncoder drawMeshThreadgroupsWithIndirectBuffer:frame->translucentMeshIndirectArgs
                                            indirectBufferOffset:0
                                     threadsPerObjectThreadgroup:MTLSizeMake(1, 1, 1)
                                       threadsPerMeshThreadgroup:MTLSizeMake(
                                                                     terrain->meshBatchSize * 4, 1,
                                                                     1)];
      [translucentEncoder endEncoding];
    }

    // --- Completion handler on the FINAL command buffer. On a serial queue, when the
    //     last CB completes all preceding CBs are guaranteed complete, so we can read
    //     their GPUStartTime/GPUEndTime safely. ---
    id<MTLCommandBuffer> finalCB = translucentCB ? translucentCB
                                   : ssaoCB     ? ssaoCB
                                   : opaqueCB   ? opaqueCB
                                                : traversalCB;
    NativeContext* capturedContext = context;
    int capturedSlot = slotIndex;
    FrameResources* capturedFrame = frame;
    id<MTLCommandBuffer> capturedTraversalCB = traversalCB;
    id<MTLCommandBuffer> capturedOpaqueCB = opaqueCB;
    id<MTLCommandBuffer> capturedSsaoCB = ssaoCB;
    [finalCB addCompletedHandler:^(id<MTLCommandBuffer> buffer) {
      {
        std::lock_guard<std::mutex> lock(capturedContext->mutex);

        // Per-pass GPU timing
        auto cbMs = [](id<MTLCommandBuffer> cb) -> double {
          if (cb == nil || cb.status != MTLCommandBufferStatusCompleted) return 0.0;
          return (cb.GPUEndTime - cb.GPUStartTime) * 1000.0;
        };
        capturedContext->gpuTraversalMs = cbMs(capturedTraversalCB);
        capturedContext->gpuOpaqueRasterMs = cbMs(capturedOpaqueCB);
        capturedContext->gpuSsaoMs = cbMs(capturedSsaoCB);
        capturedContext->gpuTranslucentMs = cbMs(buffer);
        if (buffer != capturedTraversalCB) {
          // finalCB is not the traversal CB; gpuTranslucentMs holds the final pass time.
          // Recalculate in case finalCB is actually opaque or SSAO (the ternary above).
          if (buffer == capturedOpaqueCB) {
            capturedContext->gpuOpaqueRasterMs = cbMs(buffer);
            capturedContext->gpuTranslucentMs = 0.0;
          } else if (buffer == capturedSsaoCB) {
            capturedContext->gpuSsaoMs = cbMs(buffer);
            capturedContext->gpuTranslucentMs = 0.0;
          }
        } else {
          capturedContext->gpuTranslucentMs = 0.0;
        }
        capturedContext->lastMetalGpuTimeMs = capturedContext->gpuTraversalMs +
                                              capturedContext->gpuOpaqueRasterMs +
                                              capturedContext->gpuSsaoMs +
                                              capturedContext->gpuTranslucentMs;

        if (buffer.status == MTLCommandBufferStatusError && buffer.error != nil) {
          capturedContext->asyncFailure = [[buffer.error localizedDescription] UTF8String];
        }
        if (capturedContext->terrain != nullptr) {
          TerrainResources* completedTerrain = capturedContext->terrain.get();
          std::memcpy(completedTerrain->lastTraversal, [capturedFrame->traversalStats contents],
                      sizeof(completedTerrain->lastTraversal));
          std::memset(completedTerrain->lastRaster, 0, sizeof(completedTerrain->lastRaster));
          uint32_t* worklistCounter =
              reinterpret_cast<uint32_t*>([capturedFrame->worklistCounter contents]);
          uint32_t acceptedQuads = worklistCounter[1];
          completedTerrain->lastRaster[0] = acceptedQuads;
          completedTerrain->lastRaster[1] = acceptedQuads;
          completedTerrain->lastRaster[2] = acceptedQuads * 2;
          completedTerrain->lastRaster[7] = worklistCounter[0];
          uint32_t requestCount =
              reinterpret_cast<uint32_t*>([capturedFrame->requestQueue contents])[0];
          uint32_t* requestData =
              reinterpret_cast<uint32_t*>([capturedFrame->requestQueue contents]) + 1;
          requestCount = std::min<uint32_t>(
              requestCount, static_cast<uint32_t>(completedTerrain->maxTraversalRequests));
          completedTerrain->pendingRequests.clear();
          completedTerrain->pendingRequests.reserve(requestCount);
          for (uint32_t i = 0; i < requestCount; i++) {
            uint64_t hi = requestData[i * 2];
            uint64_t lo = requestData[i * 2 + 1];
            completedTerrain->pendingRequests.push_back((hi << 32) | lo);
          }
          completedTerrain->lastTraversal[4] =
              static_cast<uint32_t>(std::min<uint64_t>(completedTerrain->topNodeCount, UINT32_MAX));
          completedTerrain->lastTraversal[5] = requestCount;
          completedTerrain->lastTraversal[6] = worklistCounter[0];
        }
        Slot& completedSlot = capturedContext->slots[capturedSlot];
        if (completedSlot.state == SlotState::MetalSubmitted) {
          completedSlot.state = SlotState::MetalReady;
        } else if (completedSlot.state == SlotState::Retiring) {
          completedSlot.state = SlotState::Free;
          completedSlot.frameId = -1;
        }
        capturedContext->pendingCommandBuffers =
            std::max(0, capturedContext->pendingCommandBuffers - 1);
      }
      capturedContext->condition.notify_all();
    }];

    // Commit all CBs in serial-queue order.
    [traversalCB commit];
    if (opaqueCB != nil) [opaqueCB commit];
    if (ssaoCB != nil) [ssaoCB commit];
    if (translucentCB != nil) [translucentCB commit];
  }
}

}  // extern "C"
