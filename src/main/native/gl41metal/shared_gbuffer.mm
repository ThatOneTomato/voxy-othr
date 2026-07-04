#include "gl41metal_internal.h"

namespace gl41metal {

// All three distant gbuffers are RGBA32F (not RGBA16F) for two reasons:
//  * gbuffer0.xy carries the atlas sample position handed to packs as
//    VoxyFragmentParameters.uv; half precision near 1.0 (~2^-11) approaches a 768x512 atlas
//    texel, so packs that re-sample the atlas would drift by up to a texel.
//  * gbuffer1/2 store integers (modelId, customId, packed albedo/light/tint, face/flags) as
//    exact float values; f32 represents every integer < 2^24 exactly, half (10-bit) does not.
// See quad_raster.metal's QuadFragmentOut for the per-channel layout shared with the GL side.
const FormatSpec GBUFFER0_FORMAT = {
    "RGBA32F", kCVPixelFormatType_128RGBAFloat, 16, MTLPixelFormatRGBA32Float, GL_RGBA32F, GL_RGBA,
    GL_FLOAT,
};

const FormatSpec GBUFFER1_FORMAT = GBUFFER0_FORMAT;
const FormatSpec GBUFFER2_FORMAT = GBUFFER0_FORMAT;

// Translucent distant gbuffer targets. Same RGBA32F backing/precision rationale as the opaque
// targets: the front-surface targets store packed integers (light/tint/face/flags, customId) as
// exact float values, and the accumulation target stores an over-blended HDR-ish colour.
const FormatSpec TGBUFFER0_FORMAT = GBUFFER0_FORMAT;
const FormatSpec TGBUFFER1_FORMAT = GBUFFER0_FORMAT;
const FormatSpec TGBUFFER_ACCUM_FORMAT = GBUFFER0_FORMAT;

SharedTexture::~SharedTexture() {
  if (glTexture != 0) {
    glDeleteTextures(1, &glTexture);
  }
  if (surface != nullptr) {
    CFRelease(surface);
  }
}

NSDictionary* makeSurfaceProperties(const FormatSpec& format, int width, int height) {
  const size_t unalignedBytesPerRow = static_cast<size_t>(width) * format.bytesPerElement;
  const size_t bytesPerRow = (unalignedBytesPerRow + 15) & ~static_cast<size_t>(15);
  return @{
    (__bridge NSString*)kIOSurfaceWidth : @(width),
    (__bridge NSString*)kIOSurfaceHeight : @(height),
    (__bridge NSString*)kIOSurfaceBytesPerElement : @(format.bytesPerElement),
    (__bridge NSString*)kIOSurfaceBytesPerRow : @(bytesPerRow),
    (__bridge NSString*)kIOSurfacePixelFormat : @(format.ioSurfaceFormat),
    (__bridge NSString*)kIOSurfaceIsGlobal : @NO
  };
}

bool importSurfaceToGlTexture(SharedTexture* shared, const FormatSpec& format, int width,
                              int height, GLenum target, std::string* error) {
  GLuint texture = 0;
  glGenTextures(1, &texture);
  glBindTexture(target, texture);
  glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
  glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
  glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
  glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

  CGLError cglError =
      CGLTexImageIOSurface2D(CGLGetCurrentContext(), target, format.cglInternalFormat, width,
                             height, format.glFormat, format.glType, shared->surface, 0);
  if (cglError != kCGLNoError) {
    std::ostringstream out;
    out << "CGLTexImageIOSurface2D failed for " << format.name << ": " << CGLErrorString(cglError);
    *error = out.str();
    glDeleteTextures(1, &texture);
    return false;
  }
  GLenum glError = glGetError();
  if (glError != GL_NO_ERROR) {
    std::ostringstream out;
    out << "OpenGL IOSurface import failed for " << format.name << ": 0x" << std::hex << glError;
    *error = out.str();
    glDeleteTextures(1, &texture);
    return false;
  }

  shared->glTexture = texture;
  shared->glTarget = target;
  return true;
}

std::unique_ptr<SharedTexture> createSharedTexture(const FormatSpec& format, int width, int height,
                                                   id<MTLDevice> device, std::string* error) {
  auto shared = std::make_unique<SharedTexture>();
  NSDictionary* properties = makeSurfaceProperties(format, width, height);
  shared->surface = IOSurfaceCreate((__bridge CFDictionaryRef)properties);
  if (shared->surface == nullptr) {
    *error = std::string("IOSurfaceCreate returned null for ") + format.name;
    return nullptr;
  }

  MTLTextureDescriptor* descriptor =
      [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:format.metalFormat
                                                         width:width
                                                        height:height
                                                     mipmapped:NO];
  descriptor.usage =
      MTLTextureUsageShaderRead | MTLTextureUsageShaderWrite | MTLTextureUsageRenderTarget;
  descriptor.storageMode = MTLStorageModeShared;
  shared->metalTexture = [device newTextureWithDescriptor:descriptor
                                                iosurface:shared->surface
                                                    plane:0];
  if (shared->metalTexture == nil) {
    *error = std::string("Metal newTextureWithDescriptor returned nil for ") + format.name;
    return nullptr;
  }

  if (!importSurfaceToGlTexture(shared.get(), format, width, height, GL_TEXTURE_RECTANGLE, error)) {
    return nullptr;
  }
  return shared;
}

bool createSlotTextures(Slot* slot, NativeContext* context, std::string* error) {
  slot->gbuffer0 =
      createSharedTexture(GBUFFER0_FORMAT, context->width, context->height, context->device, error);
  if (!slot->gbuffer0) {
    return false;
  }
  slot->gbuffer1 =
      createSharedTexture(GBUFFER1_FORMAT, context->width, context->height, context->device, error);
  if (!slot->gbuffer1) {
    return false;
  }
  slot->gbuffer2 =
      createSharedTexture(GBUFFER2_FORMAT, context->width, context->height, context->device, error);
  if (!slot->gbuffer2) {
    return false;
  }
  slot->tgbuffer0 = createSharedTexture(TGBUFFER0_FORMAT, context->width, context->height,
                                        context->device, error);
  if (!slot->tgbuffer0) {
    return false;
  }
  slot->tgbuffer1 = createSharedTexture(TGBUFFER1_FORMAT, context->width, context->height,
                                        context->device, error);
  if (!slot->tgbuffer1) {
    return false;
  }
  slot->tgbufferAccum = createSharedTexture(TGBUFFER_ACCUM_FORMAT, context->width, context->height,
                                            context->device, error);
  if (!slot->tgbufferAccum) {
    return false;
  }
  MTLTextureDescriptor* depthDescriptor =
      [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatDepth32Float
                                                         width:context->width
                                                        height:context->height
                                                     mipmapped:NO];
  // ShaderRead: the SSAO pass (ssao.metal) samples this depth as a texture after the opaque
  // raster stored it, in the same command buffer.
  depthDescriptor.usage = MTLTextureUsageRenderTarget | MTLTextureUsageShaderRead;
  depthDescriptor.storageMode = MTLStorageModePrivate;
  slot->renderDepth = [context->device newTextureWithDescriptor:depthDescriptor];
  if (slot->renderDepth == nil) {
    *error = "Metal could not allocate GL41Metal per-slot render depth texture";
    return false;
  }
  return true;
}

}  // namespace gl41metal
