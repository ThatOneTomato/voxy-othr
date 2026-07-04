#include "gl41metal_internal.h"

using namespace gl41metal;

namespace gl41metal {

void throwJava(JNIEnv* env, const std::string& message) {
  jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
  if (exceptionClass != nullptr) {
    env->ThrowNew(exceptionClass, message.c_str());
  }
}

NativeContext* requireContext(JNIEnv* env, jlong handle) {
  if (handle == 0) {
    throwJava(env, "GL41Metal native context handle is null");
    return nullptr;
  }
  return reinterpret_cast<NativeContext*>(handle);
}

id<MTLComputePipelineState> createComputePipeline(JNIEnv* env, NativeContext* context,
                                                  NSString* functionName, const char* label) {
  if (context->shaderLibrary == nil) {
    throwJava(env, std::string("GL41Metal shader library is not loaded for ") + label);
    return nil;
  }
  id<MTLFunction> function = [context->shaderLibrary newFunctionWithName:functionName];
  if (function == nil) {
    std::string message = "GL41Metal shader function is missing for ";
    message += label;
    message += ": ";
    message += [functionName UTF8String];
    throwJava(env, message);
    return nil;
  }
  NSError* error = nil;
  id<MTLComputePipelineState> pipeline =
      [context->device newComputePipelineStateWithFunction:function error:&error];
  if (pipeline == nil) {
    std::string message = "GL41Metal compute pipeline creation failed for ";
    message += label;
    if (error != nil) {
      message += ": ";
      message += [[error localizedDescription] UTF8String];
    }
    throwJava(env, message);
    return nil;
  }
  return pipeline;
}

static id<MTLLibrary> loadShaderLibrary(JNIEnv* env, id<MTLDevice> device,
                                        jstring shaderLibraryPath) {
  if (shaderLibraryPath == nullptr) {
    throwJava(env, "GL41Metal shader library path is null");
    return nil;
  }
  const char* pathChars = env->GetStringUTFChars(shaderLibraryPath, nullptr);
  if (pathChars == nullptr) {
    throwJava(env, "GL41Metal could not read shader library path");
    return nil;
  }
  NSString* path = [NSString stringWithUTF8String:pathChars];
  env->ReleaseStringUTFChars(shaderLibraryPath, pathChars);
  if (path == nil || [path length] == 0) {
    throwJava(env, "GL41Metal shader library path is empty");
    return nil;
  }
  NSURL* url = [NSURL fileURLWithPath:path];
  NSError* error = nil;
  id<MTLLibrary> library = [device newLibraryWithURL:url error:&error];
  if (library == nil) {
    std::string message = "GL41Metal shader library load failed from ";
    message += [path UTF8String];
    if (error != nil) {
      message += ": ";
      message += [[error localizedDescription] UTF8String];
    }
    throwJava(env, message);
    return nil;
  }
  return library;
}

}  // namespace gl41metal

extern "C" {

JNIEXPORT jstring JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_getUnsupportedReason(
    JNIEnv* env, jclass) {
  @autoreleasepool {
    id<MTLDevice> device = MTLCreateSystemDefaultDevice();
    if (device == nil) {
      return env->NewStringUTF("no default Metal device is available");
    }
    if (CGLGetCurrentContext() == nullptr) {
      return env->NewStringUTF("no current CGL OpenGL context is available");
    }
    return nullptr;
  }
}

JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_createContext(
    JNIEnv* env, jclass, jint slotCount, jint width, jint height, jstring shaderLibraryPath) {
  @autoreleasepool {
    if (slotCount < 2) {
      throwJava(env, "GL41Metal requires at least two shared slots");
      return 0;
    }
    if (width <= 0 || height <= 0) {
      throwJava(env, "GL41Metal native context received an invalid size");
      return 0;
    }
    if (CGLGetCurrentContext() == nullptr) {
      throwJava(env, "GL41Metal native context creation requires a current CGL context");
      return 0;
    }

    std::unique_ptr<NativeContext> context = std::make_unique<NativeContext>();
    context->device = MTLCreateSystemDefaultDevice();
    if (context->device == nil) {
      throwJava(env, "GL41Metal could not create a default Metal device");
      return 0;
    }
    context->shaderLibrary = loadShaderLibrary(env, context->device, shaderLibraryPath);
    if (context->shaderLibrary == nil) {
      return 0;
    }
    context->queue = [context->device newCommandQueue];
    if (context->queue == nil) {
      throwJava(env, "GL41Metal could not create a Metal command queue");
      return 0;
    }
    context->deviceName = [[context->device name] UTF8String];
    context->width = width;
    context->height = height;
    context->slots.resize(static_cast<size_t>(slotCount));

    for (Slot& slot : context->slots) {
      std::string error;
      if (!createSlotTextures(&slot, context.get(), &error)) {
        throwJava(env, error);
        return 0;
      }
    }

    return reinterpret_cast<jlong>(context.release());
  }
}

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_destroyContext(
    JNIEnv* env, jclass, jlong handle) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr) {
    return;
  }
  {
    std::unique_lock<std::mutex> lock(context->mutex);
    context->condition.wait_for(lock, std::chrono::seconds(5),
                                [&] { return context->pendingCommandBuffers == 0; });
  }
  delete context;
}

// Resizes only the screen-sized per-slot gbuffer/depth textures in place. The Metal device,
// command queue, shader library, per-slot FrameResources, and (critically) all terrain/world/atlas
// resources are sized independently of the viewport and are preserved. Keeping the same context
// handle means GL41Metal terrain residency survives window resizes instead of being wiped and
// re-streamed every time the user drags the window edge.
JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_resizeContext(
    JNIEnv* env, jclass, jlong handle, jint width, jint height) {
  @autoreleasepool {
    NativeContext* context = requireContext(env, handle);
    if (context == nullptr) {
      return;
    }
    if (width <= 0 || height <= 0) {
      throwJava(env, "GL41Metal native context resize received an invalid size");
      return;
    }
    if (CGLGetCurrentContext() == nullptr) {
      throwJava(env, "GL41Metal native context resize requires a current CGL context");
      return;
    }
    {
      std::unique_lock<std::mutex> lock(context->mutex);
      context->condition.wait_for(lock, std::chrono::seconds(5),
                                  [&] { return context->pendingCommandBuffers == 0; });
      if (context->width == width && context->height == height) {
        return;
      }
      context->width = width;
      context->height = height;
      // No Metal command buffers are in flight and slot acquisition runs on this (render) thread,
      // so it is safe to reset slot bookkeeping now and recreate the GL/IOSurface textures after
      // releasing the lock (matching destroyContext, which also performs GL teardown unlocked).
      for (Slot& slot : context->slots) {
        slot.state = SlotState::Free;
        slot.frameId = -1;
      }
    }
    for (Slot& slot : context->slots) {
      slot.gbuffer0.reset();
      slot.gbuffer1.reset();
      slot.gbuffer2.reset();
      slot.tgbuffer0.reset();
      slot.tgbuffer1.reset();
      slot.tgbufferAccum.reset();
      slot.renderDepth = nil;
      std::string error;
      if (!createSlotTextures(&slot, context, &error)) {
        throwJava(env, error);
        return;
      }
    }
  }
}

JNIEXPORT jstring JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_getDeviceName(
    JNIEnv* env, jclass, jlong handle) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr) {
    return nullptr;
  }
  return env->NewStringUTF(context->deviceName.c_str());
}

JNIEXPORT jint JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_getTextureTarget(
    JNIEnv* env, jclass, jlong handle) {
  NativeContext* context = requireContext(env, handle);
  return context == nullptr ? 0 : static_cast<jint>(context->textureTarget);
}

JNIEXPORT jdouble JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_getLastMetalGpuTimeMs(
    JNIEnv* env, jclass, jlong handle) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr) {
    return 0.0;
  }
  std::lock_guard<std::mutex> lock(context->mutex);
  return context->lastMetalGpuTimeMs;
}

// The distant gbuffer is 3 shared textures (see quad_raster.metal QuadFragmentOut). These three
// getters return the imported GL texture name for each; the Java DistantGbufferSlot mirrors
// the same order.
JNIEXPORT jint JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_getGbuffer0Texture(
    JNIEnv* env, jclass, jlong handle, jint slotIndex) {
  NativeContext* context = requireContext(env, handle);
  return context == nullptr ? 0
                            : static_cast<jint>(context->slots.at(slotIndex).gbuffer0->glTexture);
}

JNIEXPORT jint JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_getGbuffer1Texture(
    JNIEnv* env, jclass, jlong handle, jint slotIndex) {
  NativeContext* context = requireContext(env, handle);
  return context == nullptr ? 0
                            : static_cast<jint>(context->slots.at(slotIndex).gbuffer1->glTexture);
}

JNIEXPORT jint JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_getGbuffer2Texture(
    JNIEnv* env, jclass, jlong handle, jint slotIndex) {
  NativeContext* context = requireContext(env, handle);
  return context == nullptr ? 0
                            : static_cast<jint>(context->slots.at(slotIndex).gbuffer2->glTexture);
}

// The translucent distant gbuffer is 3 further shared textures (see quad_raster.metal
// TranslucentFragmentOut): tgbuffer0/1 carry the front-most translucent surface for strict pack
// water shading, tgbufferAccum carries the back->front over-blended flat colour + alpha.
JNIEXPORT jint JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_getTgbuffer0Texture(
    JNIEnv* env, jclass, jlong handle, jint slotIndex) {
  NativeContext* context = requireContext(env, handle);
  return context == nullptr ? 0
                            : static_cast<jint>(context->slots.at(slotIndex).tgbuffer0->glTexture);
}

JNIEXPORT jint JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_getTgbuffer1Texture(
    JNIEnv* env, jclass, jlong handle, jint slotIndex) {
  NativeContext* context = requireContext(env, handle);
  return context == nullptr ? 0
                            : static_cast<jint>(context->slots.at(slotIndex).tgbuffer1->glTexture);
}

JNIEXPORT jint JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_getTgbufferAccumTexture(
    JNIEnv* env, jclass, jlong handle, jint slotIndex) {
  NativeContext* context = requireContext(env, handle);
  return context == nullptr
             ? 0
             : static_cast<jint>(context->slots.at(slotIndex).tgbufferAccum->glTexture);
}

}  // extern "C"
