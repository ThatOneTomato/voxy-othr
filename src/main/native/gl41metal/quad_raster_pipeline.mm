#include "gl41metal_internal.h"

namespace gl41metal {

id<MTLComputePipelineState> createMeshArgsPipeline(JNIEnv* env, NativeContext* context) {
  return createComputePipeline(env, context, @"prepare_mesh_args", "mesh indirect args");
}

bool createOpaqueMeshPipeline(JNIEnv* env, NativeContext* context, TerrainResources* terrain) {
  (void)env;
  NSError* error = nil;
  uint32_t batchSize = terrain->meshBatchSize;

  MTLFunctionConstantValues* constants = [[MTLFunctionConstantValues alloc] init];
  [constants setConstantValue:&batchSize type:MTLDataTypeUInt atIndex:0];

  id<MTLFunction> object =
      [context->shaderLibrary newFunctionWithName:@"voxy_opaque_object" constantValues:constants error:&error];
  if (object == nil) {
    NSLog(@"GL41Metal mesh object function unavailable: %@", error ? [error localizedDescription] : @"unknown");
    return true;
  }
  id<MTLFunction> mesh =
      [context->shaderLibrary newFunctionWithName:@"voxy_opaque_mesh" constantValues:constants error:&error];
  if (mesh == nil) {
    NSLog(@"GL41Metal mesh function unavailable: %@", error ? [error localizedDescription] : @"unknown");
    return true;
  }
  id<MTLFunction> fragment = [context->shaderLibrary newFunctionWithName:@"voxy_quad_fragment"];
  if (fragment == nil) {
    NSLog(@"GL41Metal mesh fragment function unavailable, mesh pipeline disabled");
    return true;
  }

  MTLMeshRenderPipelineDescriptor* descriptor = [[MTLMeshRenderPipelineDescriptor alloc] init];
  descriptor.label = [NSString stringWithFormat:@"Voxy GL41Metal opaque mesh (batch=%u)", batchSize];
  descriptor.objectFunction = object;
  descriptor.meshFunction = mesh;
  descriptor.fragmentFunction = fragment;
  descriptor.colorAttachments[0].pixelFormat = GBUFFER0_FORMAT.metalFormat;
  descriptor.colorAttachments[1].pixelFormat = GBUFFER1_FORMAT.metalFormat;
  descriptor.colorAttachments[2].pixelFormat = GBUFFER2_FORMAT.metalFormat;
  descriptor.depthAttachmentPixelFormat = MTLPixelFormatDepth32Float;
  descriptor.maxTotalThreadsPerObjectThreadgroup = 1;
  descriptor.maxTotalThreadsPerMeshThreadgroup = 64 * 4;

  error = nil;
  MTLRenderPipelineReflection* reflection = nil;
  terrain->opaqueMeshPipeline =
      [context->device newRenderPipelineStateWithMeshDescriptor:descriptor
                                                       options:MTLPipelineOptionNone
                                                    reflection:&reflection
                                                         error:&error];
  if (terrain->opaqueMeshPipeline == nil) {
    std::string message = "GL41Metal opaque mesh pipeline creation failed";
    if (error != nil) {
      message += ": ";
      message += [[error localizedDescription] UTF8String];
    }
    NSLog(@"%s", message.c_str());
    return true;
  }
  NSLog(@"GL41Metal opaque mesh pipeline created (batch=%u)", batchSize);

  MTLDepthStencilDescriptor* depthDescriptor = [[MTLDepthStencilDescriptor alloc] init];
  depthDescriptor.depthCompareFunction = MTLCompareFunctionLessEqual;
  depthDescriptor.depthWriteEnabled = YES;
  terrain->quadDepthStencil = [context->device newDepthStencilStateWithDescriptor:depthDescriptor];
  if (terrain->quadDepthStencil == nil) {
    NSLog(@"GL41Metal opaque depth state creation failed");
    return false;
  }
  return true;
}

bool createTranslucentSortPipelines(JNIEnv* env, NativeContext* context, TerrainResources* terrain) {
  terrain->translucentCountPipeline =
      createComputePipeline(env, context, @"prepare_translucent_sort", "translucent sort count");
  if (terrain->translucentCountPipeline == nil) {
    return false;
  }
  terrain->translucentPrefixSumPipeline =
      createComputePipeline(env, context, @"prefix_sum_translucent", "translucent prefix sum");
  if (terrain->translucentPrefixSumPipeline == nil) {
    return false;
  }
  terrain->translucentScatterPipeline =
      createComputePipeline(env, context, @"scatter_translucent_draw", "translucent scatter");
  if (terrain->translucentScatterPipeline == nil) {
    return false;
  }

  MTLDepthStencilDescriptor* depthDescriptor = [[MTLDepthStencilDescriptor alloc] init];
  depthDescriptor.depthCompareFunction = MTLCompareFunctionLess;
  depthDescriptor.depthWriteEnabled = NO;
  terrain->translucentDepthStencil =
      [context->device newDepthStencilStateWithDescriptor:depthDescriptor];
  if (terrain->translucentDepthStencil == nil) {
    throwJava(env, "GL41Metal translucent depth state creation failed");
    return false;
  }
  return true;
}

bool createTranslucentMeshPipeline(JNIEnv* env, NativeContext* context, TerrainResources* terrain) {
  (void)env;
  uint32_t batchSize = terrain->meshBatchSize;
  MTLFunctionConstantValues* constants = [[MTLFunctionConstantValues alloc] init];
  [constants setConstantValue:&batchSize type:MTLDataTypeUInt atIndex:0];

  NSError* error = nil;
  id<MTLFunction> object =
      [context->shaderLibrary newFunctionWithName:@"voxy_translucent_object"
                                   constantValues:constants
                                            error:&error];
  id<MTLFunction> mesh = [context->shaderLibrary newFunctionWithName:@"voxy_translucent_mesh"
                                                      constantValues:constants
                                                               error:&error];
  id<MTLFunction> fragment =
      [context->shaderLibrary newFunctionWithName:@"voxy_translucent_fragment"];
  if (object == nil || mesh == nil || fragment == nil) {
    NSLog(@"GL41Metal translucent mesh shader functions missing");
    return false;
  }

  MTLMeshRenderPipelineDescriptor* descriptor = [[MTLMeshRenderPipelineDescriptor alloc] init];
  descriptor.label = @"Voxy GL41Metal translucent mesh render";
  descriptor.objectFunction = object;
  descriptor.meshFunction = mesh;
  descriptor.fragmentFunction = fragment;
  descriptor.colorAttachments[0].pixelFormat = TGBUFFER0_FORMAT.metalFormat;
  descriptor.colorAttachments[0].blendingEnabled = NO;
  descriptor.colorAttachments[1].pixelFormat = TGBUFFER1_FORMAT.metalFormat;
  descriptor.colorAttachments[1].blendingEnabled = NO;
  descriptor.colorAttachments[2].pixelFormat = TGBUFFER_ACCUM_FORMAT.metalFormat;
  descriptor.colorAttachments[2].blendingEnabled = YES;
  descriptor.colorAttachments[2].rgbBlendOperation = MTLBlendOperationAdd;
  descriptor.colorAttachments[2].alphaBlendOperation = MTLBlendOperationAdd;
  descriptor.colorAttachments[2].sourceRGBBlendFactor = MTLBlendFactorOne;
  descriptor.colorAttachments[2].destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
  descriptor.colorAttachments[2].sourceAlphaBlendFactor = MTLBlendFactorOne;
  descriptor.colorAttachments[2].destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
  descriptor.depthAttachmentPixelFormat = MTLPixelFormatDepth32Float;
  descriptor.maxTotalThreadsPerObjectThreadgroup = 1;
  descriptor.maxTotalThreadsPerMeshThreadgroup = 64 * 4;

  error = nil;
  MTLRenderPipelineReflection* reflection = nil;
  terrain->translucentMeshPipeline =
      [context->device newRenderPipelineStateWithMeshDescriptor:descriptor
                                                       options:MTLPipelineOptionNone
                                                    reflection:&reflection
                                                         error:&error];
  if (terrain->translucentMeshPipeline == nil) {
    std::string message = "GL41Metal translucent mesh pipeline creation failed";
    if (error != nil) {
      message += ": ";
      message += [[error localizedDescription] UTF8String];
    }
    NSLog(@"%s", message.c_str());
    return false;
  }
  NSLog(@"GL41Metal translucent mesh pipeline created (batch=%u)", batchSize);
  return true;
}

}  // namespace gl41metal
