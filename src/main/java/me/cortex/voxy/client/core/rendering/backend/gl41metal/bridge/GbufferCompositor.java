package me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge;

import static org.lwjgl.opengl.GL11C.GL_ALWAYS;
import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_EQUAL;
import static org.lwjgl.opengl.GL11C.GL_KEEP;
import static org.lwjgl.opengl.GL11C.GL_LEQUAL;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_NONE;
import static org.lwjgl.opengl.GL11C.GL_REPLACE;
import static org.lwjgl.opengl.GL11C.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.glClear;
import static org.lwjgl.opengl.GL11C.glClearDepth;
import static org.lwjgl.opengl.GL11C.glClearStencil;
import static org.lwjgl.opengl.GL11C.glColorMask;
import static org.lwjgl.opengl.GL11C.glDepthFunc;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glReadBuffer;
import static org.lwjgl.opengl.GL11C.glStencilFunc;
import static org.lwjgl.opengl.GL11C.glStencilMask;
import static org.lwjgl.opengl.GL11C.glStencilOp;
import static org.lwjgl.opengl.GL11C.glTexImage2D;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL11C.glViewport;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL14C.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glBufferData;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL15C.nglBufferSubData;
import static org.lwjgl.opengl.GL20C.glDrawBuffers;
import static org.lwjgl.opengl.GL20C.glUniform1i;
import static org.lwjgl.opengl.GL20C.glUniform2f;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_DEPTH24_STENCIL8;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_STENCIL;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_STENCIL_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_UNSIGNED_INT_24_8;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glBlitFramebuffer;
import static org.lwjgl.opengl.GL30C.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30C.glGenFramebuffers;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL33C.glBindSampler;

import me.cortex.voxy.client.iris.IrisBridgeShaderBindings;
import me.cortex.voxy.common.Logger;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Owns the GL4.1-compatible Iris MRT, private depth/stencil and pack UBO state. */
final class GbufferCompositor {
  static final int SOURCE_DEPTH_TEXTURE_UNIT = 0;

  private final BridgePrograms programs;
  private final int framebuffer = glGenFramebuffers();
  private final int depthCopyFramebuffer = glGenFramebuffers();
  private final int translucentDepthFramebuffer = glGenFramebuffers();
  private final int fullscreenVao = glGenVertexArrays();

  private int uniformBuffer;
  private int uniformBufferBytes;
  private long uniformScratchAddr;
  private int irisPrivateDepthTexture;
  private int irisPrivateDepthWidth;
  private int irisPrivateDepthHeight;
  private int translucentDepthTexture;
  private int translucentDepthWidth;
  private int translucentDepthHeight;
  private boolean directTranslucentDepthValid;

  GbufferCompositor(BridgePrograms programs) {
    this.programs = programs;
  }

  boolean prepareDirectIrisOpaque(
      MemoryStack stack,
      DistantBridgeJob job,
      int sourceDepthTexture,
      int sourceDepthWidth,
      int sourceDepthHeight,
      boolean reverseDepth) {
    this.directTranslucentDepthValid = false;
    int privateDepth = this.ensureIrisPrivateDepth(job.outputWidth(), job.outputHeight());
    var maskShader = this.programs.stencilMask();
    if (privateDepth == 0
        || maskShader == null
        || !this.bindTargetFramebuffer(stack, job, privateDepth)) {
      return false;
    }
    glViewport(0, 0, job.outputWidth(), job.outputHeight());
    glDisable(GL_SCISSOR_TEST);
    glDepthMask(true);
    glStencilMask(0xFF);
    glColorMask(false, false, false, false);
    glClearDepth(1.0);
    glClearStencil(0);
    glClear(GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

    this.writeNearCoverageMask(
        maskShader,
        sourceDepthTexture,
        sourceDepthWidth,
        sourceDepthHeight,
        job.outputWidth(),
        job.outputHeight(),
        reverseDepth);
    this.beginDirectOpaqueColor(job);
    return true;
  }

  void bindDirectIrisResources(DistantBridgeJob job) {
    int size = job.uniformBufferBytes();
    if (size > 0) {
      this.ensureUniformBuffer(size);
      job.uniformUpdater().accept(this.uniformScratchAddr);
      glBindBuffer(GL_UNIFORM_BUFFER, this.uniformBuffer);
      nglBufferSubData(GL_UNIFORM_BUFFER, 0L, size, this.uniformScratchAddr);
      glBindBufferBase(
          GL_UNIFORM_BUFFER, IrisBridgeShaderBindings.UNIFORM_BINDING_POINT, this.uniformBuffer);
    }
    job.resourceBinder().run();
  }

  void finishDirectIrisOpaque() {
    glDisable(GL_STENCIL_TEST);
    glStencilMask(0xFF);
  }

  boolean prepareDirectIrisTranslucent(
      MemoryStack stack,
      DistantBridgeJob job,
      int sourceDepthTexture,
      int sourceDepthWidth,
      int sourceDepthHeight,
      boolean reverseDepth) {
    int translucentDepth = this.ensureTranslucentDepth(job.outputWidth(), job.outputHeight());
    var maskShader = this.programs.stencilMask();
    if (translucentDepth == 0
        || this.irisPrivateDepthTexture == 0
        || maskShader == null
        || !this.copyOpaqueDepth(job.outputWidth(), job.outputHeight(), translucentDepth)
        || !this.bindTargetFramebuffer(stack, job, translucentDepth)) {
      return false;
    }
    glViewport(0, 0, job.outputWidth(), job.outputHeight());
    glDisable(GL_SCISSOR_TEST);
    glColorMask(false, false, false, false);
    glDepthMask(false);
    glStencilMask(0xFF);
    glClearStencil(0);
    glClear(GL_STENCIL_BUFFER_BIT);
    this.writeNearCoverageMask(
        maskShader,
        sourceDepthTexture,
        sourceDepthWidth,
        sourceDepthHeight,
        job.outputWidth(),
        job.outputHeight(),
        reverseDepth);
    this.directTranslucentDepthValid = true;
    return true;
  }

  void beginDirectIrisTranslucentColor(DistantBridgeJob job) {
    boolean colorWrite = job.colorWriteEnabled();
    glColorMask(colorWrite, colorWrite, colorWrite, colorWrite);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);
    glDepthMask(true);
    glStencilMask(0x00);
    glStencilFunc(GL_EQUAL, 0, 0xFF);
    glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
    glEnable(GL_BLEND);
    org.lwjgl.opengl.GL14C.glBlendFuncSeparate(
        org.lwjgl.opengl.GL11C.GL_SRC_ALPHA,
        org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA,
        org.lwjgl.opengl.GL11C.GL_ONE,
        org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA);
    job.blendSetup().run();
  }

  void finishDirectIrisTranslucent() {
    glDisable(GL_STENCIL_TEST);
    glStencilMask(0xFF);
  }

  int irisOpaqueDepthTextureId() {
    return this.irisPrivateDepthTexture;
  }

  int irisTranslucentDepthTextureId() {
    return this.directTranslucentDepthValid
        ? this.translucentDepthTexture
        : this.irisPrivateDepthTexture;
  }

  private void writeNearCoverageMask(
      BridgePrograms.StencilMask maskShader,
      int sourceDepthTexture,
      int sourceDepthWidth,
      int sourceDepthHeight,
      int targetWidth,
      int targetHeight,
      boolean reverseDepth) {
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_BLEND);
    glDepthMask(false);
    glEnable(GL_STENCIL_TEST);
    glStencilMask(0xFF);
    glStencilFunc(GL_ALWAYS, 1, 0xFF);
    glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
    maskShader.shader().bind();
    glUniform1i(maskShader.nearDepthUniform(), SOURCE_DEPTH_TEXTURE_UNIT);
    glUniform2f(maskShader.nearSizeUniform(), sourceDepthWidth, sourceDepthHeight);
    glUniform2f(maskShader.targetSizeUniform(), targetWidth, targetHeight);
    glUniform1i(maskShader.reverseDepthUniform(), reverseDepth ? 1 : 0);
    this.bind2DTexture(SOURCE_DEPTH_TEXTURE_UNIT, sourceDepthTexture);
    glBindVertexArray(this.fullscreenVao);
    glDrawArrays(org.lwjgl.opengl.GL11C.GL_TRIANGLE_STRIP, 0, 4);
  }

  private void beginDirectOpaqueColor(DistantBridgeJob job) {
    boolean colorWrite = job.colorWriteEnabled();
    glColorMask(colorWrite, colorWrite, colorWrite, colorWrite);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);
    glDepthMask(true);
    glStencilMask(0x00);
    glStencilFunc(GL_EQUAL, 0, 0xFF);
    glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
    glDisable(GL_BLEND);
  }

  private boolean bindTargetFramebuffer(MemoryStack stack, DistantBridgeJob job, int depthTexture) {
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.framebuffer);
    int[] targetTextures = job.targetTextureIds();
    var drawBuffers = stack.mallocInt(targetTextures.length);
    for (int i = 0; i < targetTextures.length; i++) {
      glFramebufferTexture2D(
          GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, GL_TEXTURE_2D, targetTextures[i], 0);
      drawBuffers.put(i, GL_COLOR_ATTACHMENT0 + i);
    }
    glFramebufferTexture2D(
        GL_DRAW_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0);
    glDrawBuffers(drawBuffers);
    int status = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
      Logger.error(
          "Voxy GL41Metal direct Iris framebuffer incomplete: 0x" + Integer.toHexString(status));
      return false;
    }
    return true;
  }

  private boolean copyOpaqueDepth(int width, int height, int translucentDepth) {
    glBindFramebuffer(GL_READ_FRAMEBUFFER, this.depthCopyFramebuffer);
    glFramebufferTexture2D(
        GL_READ_FRAMEBUFFER,
        GL_DEPTH_STENCIL_ATTACHMENT,
        GL_TEXTURE_2D,
        this.irisPrivateDepthTexture,
        0);
    glReadBuffer(GL_NONE);
    boolean readComplete = glCheckFramebufferStatus(GL_READ_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;

    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.translucentDepthFramebuffer);
    glFramebufferTexture2D(
        GL_DRAW_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, translucentDepth, 0);
    org.lwjgl.opengl.GL11C.glDrawBuffer(GL_NONE);
    boolean drawComplete = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
    if (readComplete && drawComplete) {
      glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_DEPTH_BUFFER_BIT, GL_NEAREST);
    }
    glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, 0, 0);
    glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, 0, 0);
    return readComplete && drawComplete;
  }

  private int ensureIrisPrivateDepth(int width, int height) {
    if (this.irisPrivateDepthTexture != 0
        && this.irisPrivateDepthWidth == width
        && this.irisPrivateDepthHeight == height) {
      return this.irisPrivateDepthTexture;
    }
    this.irisPrivateDepthTexture =
        reallocateDepthTexture(this.irisPrivateDepthTexture, width, height);
    this.irisPrivateDepthWidth = width;
    this.irisPrivateDepthHeight = height;
    return this.irisPrivateDepthTexture;
  }

  private int ensureTranslucentDepth(int width, int height) {
    if (this.translucentDepthTexture != 0
        && this.translucentDepthWidth == width
        && this.translucentDepthHeight == height) {
      return this.translucentDepthTexture;
    }
    this.translucentDepthTexture =
        reallocateDepthTexture(this.translucentDepthTexture, width, height);
    this.translucentDepthWidth = width;
    this.translucentDepthHeight = height;
    return this.translucentDepthTexture;
  }

  private static int reallocateDepthTexture(int oldTexture, int width, int height) {
    if (width <= 0 || height <= 0) {
      return 0;
    }
    if (oldTexture != 0) {
      org.lwjgl.opengl.GL11C.glDeleteTextures(oldTexture);
    }
    int texture = org.lwjgl.opengl.GL11C.glGenTextures();
    glActiveTexture(GL_TEXTURE0 + SOURCE_DEPTH_TEXTURE_UNIT);
    org.lwjgl.opengl.GL11C.glBindTexture(GL_TEXTURE_2D, texture);
    glTexImage2D(
        GL_TEXTURE_2D,
        0,
        GL_DEPTH24_STENCIL8,
        width,
        height,
        0,
        GL_DEPTH_STENCIL,
        GL_UNSIGNED_INT_24_8,
        0L);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_NONE);
    return texture;
  }

  private void bind2DTexture(int unit, int texture) {
    glActiveTexture(GL_TEXTURE0 + unit);
    org.lwjgl.opengl.GL11C.glBindTexture(GL_TEXTURE_2D, texture);
    glBindSampler(unit, 0);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
  }

  private void ensureUniformBuffer(int size) {
    if (this.uniformBuffer != 0 && this.uniformBufferBytes == size) {
      return;
    }
    if (this.uniformBuffer != 0) {
      glDeleteBuffers(this.uniformBuffer);
    }
    if (this.uniformScratchAddr != 0L) {
      MemoryUtil.nmemFree(this.uniformScratchAddr);
    }
    this.uniformBuffer = glGenBuffers();
    glBindBuffer(GL_UNIFORM_BUFFER, this.uniformBuffer);
    glBufferData(GL_UNIFORM_BUFFER, (long) size, GL_DYNAMIC_DRAW);
    this.uniformScratchAddr = MemoryUtil.nmemCalloc(1, size);
    this.uniformBufferBytes = size;
  }

  void close() {
    if (this.translucentDepthTexture != 0) {
      org.lwjgl.opengl.GL11C.glDeleteTextures(this.translucentDepthTexture);
    }
    if (this.irisPrivateDepthTexture != 0) {
      org.lwjgl.opengl.GL11C.glDeleteTextures(this.irisPrivateDepthTexture);
    }
    if (this.uniformBuffer != 0) {
      glDeleteBuffers(this.uniformBuffer);
    }
    if (this.uniformScratchAddr != 0L) {
      MemoryUtil.nmemFree(this.uniformScratchAddr);
    }
    glDeleteVertexArrays(this.fullscreenVao);
    glDeleteFramebuffers(this.translucentDepthFramebuffer);
    glDeleteFramebuffers(this.depthCopyFramebuffer);
    glDeleteFramebuffers(this.framebuffer);
  }
}
