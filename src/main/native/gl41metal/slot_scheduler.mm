#include "gl41metal_internal.h"

using namespace gl41metal;

namespace gl41metal {

void resetSubmittedSlot(NativeContext* context, int slotIndex) {
  std::lock_guard<std::mutex> lock(context->mutex);
  Slot& slot = context->slots[slotIndex];
  if (slot.state == SlotState::MetalSubmitted) {
    slot.state = SlotState::Free;
    slot.frameId = -1;
  }
  context->pendingCommandBuffers = std::max(0, context->pendingCommandBuffers - 1);
  context->condition.notify_all();
}

}  // namespace gl41metal

extern "C" {

JNIEXPORT jint JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_acquireFreeSlot(
    JNIEnv* env, jclass, jlong handle) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr) {
    return -1;
  }
  std::lock_guard<std::mutex> lock(context->mutex);
  for (size_t i = 0; i < context->slots.size(); i++) {
    Slot& slot = context->slots[i];
    if (slot.state == SlotState::Free) {
      return static_cast<jint>(i);
    }
  }
  return -1;
}

JNIEXPORT jint JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_waitCurrent(
    JNIEnv* env, jclass, jlong handle, jint currentSlot, jint timeoutMs) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr) {
    return -1;
  }
  std::unique_lock<std::mutex> lock(context->mutex);
  if (currentSlot < 0 || currentSlot >= static_cast<jint>(context->slots.size())) {
    return -1;
  }
  auto readyOrFailed = [&] {
    return context->slots[currentSlot].state == SlotState::MetalReady ||
           !context->asyncFailure.empty();
  };
  if (timeoutMs <= 0) {
    context->condition.wait(lock, readyOrFailed);
  } else {
    context->condition.wait_for(lock, std::chrono::milliseconds(timeoutMs), readyOrFailed);
  }
  if (!context->asyncFailure.empty()) {
    throwJava(env, context->asyncFailure);
    return -1;
  }
  if (context->slots[currentSlot].state != SlotState::MetalReady) {
    return -1;
  }
  context->slots[currentSlot].state = SlotState::GlSampling;
  return currentSlot;
}

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_discardCurrentSlot(
    JNIEnv* env, jclass, jlong handle, jint slotIndex) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr) {
    return;
  }
  if (slotIndex < 0 || slotIndex >= static_cast<jint>(context->slots.size())) {
    throwJava(env, "GL41Metal discard received an invalid slot index");
    return;
  }
  {
    std::lock_guard<std::mutex> lock(context->mutex);
    Slot& slot = context->slots[slotIndex];
    if (slot.state == SlotState::MetalReady) {
      slot.state = SlotState::Free;
      slot.frameId = -1;
    } else if (slot.state == SlotState::MetalSubmitted) {
      slot.state = SlotState::Retiring;
    }
  }
  context->condition.notify_all();
}

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_releaseSampledSlot(
    JNIEnv* env, jclass, jlong handle, jint slotIndex) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr) {
    return;
  }
  if (slotIndex < 0 || slotIndex >= static_cast<jint>(context->slots.size())) {
    throwJava(env, "GL41Metal release received an invalid slot index");
    return;
  }
  {
    std::lock_guard<std::mutex> lock(context->mutex);
    Slot& slot = context->slots[slotIndex];
    if (slot.state == SlotState::GlSampling) {
      slot.state = SlotState::Retiring;
      slot.state = SlotState::Free;
      slot.frameId = -1;
    }
  }
  context->condition.notify_all();
}

}  // extern "C"
