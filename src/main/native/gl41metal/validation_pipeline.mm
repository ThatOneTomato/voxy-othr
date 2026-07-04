#include "gl41metal_internal.h"

using namespace gl41metal;

namespace gl41metal {

id<MTLComputePipelineState> createValidationPipeline(JNIEnv* env, NativeContext* context) {
  return createComputePipeline(env, context, @"validateTerrain", "terrain validation");
}

}  // namespace gl41metal

extern "C" {

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_Gl41MetalNative_validateTerrainResources(
    JNIEnv* env,
    jclass,
    jlong handle) {
  @autoreleasepool {
    NativeContext* context = requireContext(env, handle);
    if (context == nullptr || context->terrain == nullptr) {
      return;
    }
    TerrainResources* terrain = context->terrain.get();
    id<MTLCommandBuffer> commandBuffer = [context->queue commandBuffer];
    if (commandBuffer == nil) {
      throwJava(env, "GL41Metal terrain validation commandBuffer returned nil");
      return;
    }
    commandBuffer.label = @"Voxy Terrain Validation";
    id<MTLBlitCommandEncoder> blit = [commandBuffer blitCommandEncoder];
    blit.label = @"Voxy Validation Clear Stats";
    [blit fillBuffer:terrain->validationStats range:NSMakeRange(0, 6 * sizeof(uint32_t)) value:0];
    [blit endEncoding];

    id<MTLComputeCommandEncoder> encoder = [commandBuffer computeCommandEncoder];
    if (encoder == nil) {
      throwJava(env, "GL41Metal terrain validation compute encoder returned nil");
      return;
    }
    encoder.label = @"Voxy Validation Compute";
    [encoder setComputePipelineState:terrain->validationPipeline];
    [encoder setBuffer:terrain->sectionMetadata offset:0 atIndex:0];
    [encoder setBuffer:terrain->geometry offset:0 atIndex:1];
    [encoder setBuffer:terrain->modelBuffer offset:0 atIndex:2];
    [encoder setBuffer:terrain->modelPresentBuffer offset:0 atIndex:3];
    [encoder setBuffer:terrain->validationStats offset:0 atIndex:4];
    [encoder setBuffer:terrain->validationMaxSections offset:0 atIndex:5];
    [encoder setBuffer:terrain->validationGeometryQuadCapacity offset:0 atIndex:6];
    NSUInteger width = std::min<NSUInteger>(terrain->validationPipeline.maxTotalThreadsPerThreadgroup, 128);
    MTLSize threadsPerGroup = MTLSizeMake(width, 1, 1);
    MTLSize threads = MTLSizeMake(static_cast<NSUInteger>(terrain->maxSections), 1, 1);
    [encoder dispatchThreads:threads threadsPerThreadgroup:threadsPerGroup];
    [encoder endEncoding];
    [commandBuffer commit];
    [commandBuffer waitUntilCompleted];
    if (commandBuffer.status == MTLCommandBufferStatusError && commandBuffer.error != nil) {
      throwJava(env, [[commandBuffer.error localizedDescription] UTF8String]);
      return;
    }
    std::memcpy(terrain->lastValidation, [terrain->validationStats contents], sizeof(terrain->lastValidation));
  }
}

}  // extern "C"
