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
    throwJava(env, std::string("GL41Metal shader function is missing for ") + label);
    return nil;
  }
  NSError* error = nil;
  id<MTLComputePipelineState> pipeline =
      [context->device newComputePipelineStateWithFunction:function error:&error];
  if (pipeline == nil) {
    std::string message = std::string("GL41Metal compute pipeline creation failed for ") + label;
    if (error != nil) {
      message += ": ";
      message += [[error localizedDescription] UTF8String];
    }
    throwJava(env, message);
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
    return nil;
  }
  NSString* path = [NSString stringWithUTF8String:pathChars];
  env->ReleaseStringUTFChars(shaderLibraryPath, pathChars);
  NSError* error = nil;
  id<MTLLibrary> library =
      [device newLibraryWithURL:[NSURL fileURLWithPath:path] error:&error];
  if (library == nil) {
    std::string message = "GL41Metal shader library load failed";
    if (error != nil) {
      message += ": ";
      message += [[error localizedDescription] UTF8String];
    }
    throwJava(env, message);
  }
  return library;
}

}  // namespace gl41metal

extern "C" {

JNIEXPORT jstring JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_getUnsupportedReason(
    JNIEnv* env, jclass) {
  @autoreleasepool {
    return MTLCreateSystemDefaultDevice() == nil
               ? env->NewStringUTF("no default Metal device is available")
               : nullptr;
  }
}

JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_createContext(
    JNIEnv* env, jclass, jint slotCount, jstring shaderLibraryPath) {
  @autoreleasepool {
    if (slotCount < 2) {
      throwJava(env, "GL41Metal requires at least two frame slots");
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
    context->slots.resize(static_cast<size_t>(slotCount));
    return reinterpret_cast<jlong>(context.release());
  }
}

JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_destroyContext(
    JNIEnv* env, jclass, jlong handle) {
  NativeContext* context = requireContext(env, handle);
  if (context == nullptr) return;
  {
    std::unique_lock<std::mutex> lock(context->mutex);
    context->condition.wait(lock, [&] { return context->pendingCommandBuffers == 0; });
  }
  delete context;
}

JNIEXPORT jstring JNICALL
Java_me_cortex_voxy_client_core_rendering_backend_gl41metal_jni_NativeBindings_getDeviceName(
    JNIEnv* env, jclass, jlong handle) {
  NativeContext* context = requireContext(env, handle);
  return context == nullptr ? nullptr : env->NewStringUTF(context->deviceName.c_str());
}

}  // extern "C"
