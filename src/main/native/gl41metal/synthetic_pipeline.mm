#include "gl41metal_internal.h"

using namespace gl41metal;

extern "C" {

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_Gl41MetalNative_submitSynthetic(
    JNIEnv* env,
    jclass,
    jlong handle,
    jint slotIndex,
    jlong frameId) {
  @autoreleasepool {
    NativeContext* context = requireContext(env, handle);
    if (context == nullptr) {
      return;
    }
    if (slotIndex < 0 || slotIndex >= static_cast<jint>(context->slots.size())) {
      throwJava(env, "GL41Metal submit received an invalid slot index");
      return;
    }

    {
      std::lock_guard<std::mutex> lock(context->mutex);
      Slot& slot = context->slots[slotIndex];
      if (slot.state != SlotState::Free) {
        throwJava(env, "GL41Metal submit attempted to write a non-free slot");
        return;
      }
      slot.state = SlotState::MetalSubmitted;
      slot.frameId = frameId;
      context->pendingCommandBuffers++;
    }

    Slot& slot = context->slots[slotIndex];
    id<MTLCommandBuffer> commandBuffer = [context->queue commandBuffer];
    if (commandBuffer == nil) {
      resetSubmittedSlot(context, slotIndex);
      throwJava(env, "GL41Metal commandBuffer returned nil");
      return;
    }
    commandBuffer.label = @"Voxy Synthetic Clear";

    // Synthetic interop smoke test: clear-only (no draw) into the 3 packed gbuffer textures so
    // the GL bridge has something decodable. The clear values fake a single opaque fragment per
    // the QuadFragmentOut layout in quad_raster.metal: gbuffer1.x = 0.5 mid depth (passes the
    // bridge depth gate), gbuffer2.w low bit = coverage 1 (passes the coverage gate), and
    // gbuffer2.x carries an animated packed albedo so the screen visibly flickers.
    MTLRenderPassDescriptor* pass = [MTLRenderPassDescriptor renderPassDescriptor];
    const double framePhase = static_cast<double>(frameId % 256) / 255.0;
    const uint32_t albedoR = static_cast<uint32_t>(framePhase * 255.0 + 0.5);
    // albedoPacked = (r<<16)|(g<<8)|b with g=0.35, b=0.85; lightPacked/tintPacked = full/white.
    const double albedoPacked = static_cast<double>((albedoR << 16) | (89u << 8) | 217u);
    const double lightPacked = static_cast<double>((4095u << 12) | 4095u);
    const double tintPacked = static_cast<double>((255u << 16) | (255u << 8) | 255u);
    const double faceFlagsCoverage = 1.0;  // face 0, flags 0, coverage 1
    pass.colorAttachments[0].texture = slot.gbuffer0->metalTexture;
    pass.colorAttachments[0].loadAction = MTLLoadActionClear;
    pass.colorAttachments[0].storeAction = MTLStoreActionStore;
    pass.colorAttachments[0].clearColor = MTLClearColorMake(0.5, 0.5, 0.0, 0.0);
    pass.colorAttachments[1].texture = slot.gbuffer1->metalTexture;
    pass.colorAttachments[1].loadAction = MTLLoadActionClear;
    pass.colorAttachments[1].storeAction = MTLStoreActionStore;
    pass.colorAttachments[1].clearColor = MTLClearColorMake(0.5, 0.0, 0.0, 0.0);
    pass.colorAttachments[2].texture = slot.gbuffer2->metalTexture;
    pass.colorAttachments[2].loadAction = MTLLoadActionClear;
    pass.colorAttachments[2].storeAction = MTLStoreActionStore;
    pass.colorAttachments[2].clearColor = MTLClearColorMake(albedoPacked, lightPacked, tintPacked, faceFlagsCoverage);

    id<MTLRenderCommandEncoder> encoder = [commandBuffer renderCommandEncoderWithDescriptor:pass];
    if (encoder == nil) {
      resetSubmittedSlot(context, slotIndex);
      throwJava(env, "GL41Metal renderCommandEncoderWithDescriptor returned nil");
      return;
    }
    [encoder endEncoding];

    NativeContext* capturedContext = context;
    int capturedSlot = slotIndex;
    [commandBuffer addCompletedHandler:^(id<MTLCommandBuffer> buffer) {
      if (capturedContext->completionDelayMs > 0) {
        std::this_thread::sleep_for(std::chrono::milliseconds(capturedContext->completionDelayMs));
      }
      {
        std::lock_guard<std::mutex> lock(capturedContext->mutex);
        if (buffer.status == MTLCommandBufferStatusError && buffer.error != nil) {
          capturedContext->asyncFailure = [[buffer.error localizedDescription] UTF8String];
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
    [commandBuffer commit];
  }
}

}  // extern "C"
