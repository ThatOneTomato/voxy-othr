package me.cortex.voxy.client.compat.sable;

import static org.lwjgl.opengl.GL11C.GL_ALWAYS;
import static org.lwjgl.opengl.GL11C.GL_COLOR_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_FUNC;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_FALSE;
import static org.lwjgl.opengl.GL11C.GL_LEQUAL;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_NONE;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11C.glColorMask;
import static org.lwjgl.opengl.GL11C.glDepthFunc;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glGetIntegerv;
import static org.lwjgl.opengl.GL11C.glIsEnabled;
import static org.lwjgl.opengl.GL11C.glViewport;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL14C.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL14C.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.GL_DRAW_BUFFER0;
import static org.lwjgl.opengl.GL20C.glUniform2f;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL20C.nglUniformMatrix4fv;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30C.GL_MAX_COLOR_ATTACHMENTS;
import static org.lwjgl.opengl.GL30C.GL_MAX_DRAW_BUFFERS;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_RENDERBUFFER;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL30C.glGetIntegeri;
import static org.lwjgl.opengl.GL33C.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.opengl.GL33C.glGenSamplers;
import static org.lwjgl.opengl.GL33C.glSamplerParameteri;
import static org.lwjgl.opengl.GL45C.glBindTextureUnit;
import static org.lwjgl.opengl.GL45C.glCheckNamedFramebufferStatus;
import static org.lwjgl.opengl.GL45C.glGetNamedFramebufferAttachmentParameteri;
import static org.lwjgl.opengl.GL45C.glNamedFramebufferDrawBuffers;
import static org.lwjgl.opengl.GL45C.glNamedFramebufferRenderbuffer;
import static org.lwjgl.opengl.GL45C.glNamedFramebufferTexture;

import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.VoxyRenderSystemAccess;
import me.cortex.voxy.client.core.gl.GlFramebuffer;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.backend.gl46.util.FullscreenBlit;
import me.cortex.voxy.client.core.rendering.util.DepthFramebuffer;
import me.cortex.voxy.common.Logger;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/**
 * Presents Sable's vanilla-path sub level rendering with a depth buffer that contains both the
 * vanilla terrain depth and Voxy's LOD depth, so contraptions are correctly occluded by distant
 * terrain when an Iris shader pack keeps Voxy's depth out of the vanilla depth buffer. After Sable
 * renders, only the depth it actually changed is copied back.
 *
 * <p>All GL resources are created lazily after the backend reports a usable Voxy depth texture:
 * this class must not touch GL4.2+ entry points on backends (gl41metal) that never report one.
 */
public final class VoxySableDepthShim {
  private static Resources resources;
  private static State activeState;
  private static boolean loggedEnabled;
  private static boolean loggedUnsupportedFramebuffer;

  private VoxySableDepthShim() {}

  /** GL objects for the shim; only ever constructed on the GL46 backend. */
  private static final class Resources {
    final DepthFramebuffer combinedDepth = new DepthFramebuffer(GL_DEPTH_COMPONENT24);
    final DepthFramebuffer beforeSableDepth = new DepthFramebuffer(GL_DEPTH_COMPONENT24);
    final GlFramebuffer drawFramebuffer = new GlFramebuffer().name("Sable Voxy depth shim");

    final FullscreenBlit copyDepth = new FullscreenBlit("voxy:post/depth_copy.frag");
    final FullscreenBlit transformDepth =
        new FullscreenBlit("voxy:post/blit_texture_depth_cutout.frag");
    final FullscreenBlit copyChangedDepth = new FullscreenBlit("voxy:post/depth_copy_changed.frag");

    final int depthSampler = glGenSamplers();
    final long matrixScratch = MemoryUtil.nmemAlloc(16L * Float.BYTES);

    Resources() {
      glSamplerParameteri(this.depthSampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
      glSamplerParameteri(this.depthSampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
      glSamplerParameteri(this.depthSampler, GL_TEXTURE_COMPARE_MODE, GL_NONE);
    }
  }

  public static void begin(Matrix4f modelView, Matrix4f projection) {
    if (activeState != null) {
      activeState.nesting++;
      return;
    }

    VoxyRenderSystem renderer = VoxyRenderSystemAccess.getNullable();
    if (renderer == null) {
      return;
    }

    int voxyDepthTexture = renderer.getSableOcclusionDepthTexture();
    if (voxyDepthTexture == 0) {
      return;
    }

    Viewport<?> viewport = renderer.getSableOcclusionViewport();
    if (viewport == null || viewport.width <= 0 || viewport.height <= 0) {
      return;
    }

    State state = State.capture();
    if (state.drawFramebuffer == 0
        || state.width <= 0
        || state.height <= 0
        || state.x != 0
        || state.y != 0) {
      return;
    }

    int vanillaDepthType =
        glGetNamedFramebufferAttachmentParameteri(
            state.drawFramebuffer, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
    int vanillaDepthTexture =
        glGetNamedFramebufferAttachmentParameteri(
            state.drawFramebuffer, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
    if (vanillaDepthType != GL_TEXTURE || vanillaDepthTexture == 0) {
      if (!loggedUnsupportedFramebuffer) {
        Logger.warn(
            "Skipping Sable/Voxy depth shim because the active depth attachment is not a texture");
        loggedUnsupportedFramebuffer = true;
      }
      return;
    }

    Resources res = resources;
    if (res == null) {
      res = resources = new Resources();
    }

    res.combinedDepth.resize(state.width, state.height);
    res.beforeSableDepth.resize(state.width, state.height);

    if (!prepareDrawFramebuffer(res, state.drawFramebuffer)) {
      state.restoreAll();
      return;
    }

    copyDepth(
        res, vanillaDepthTexture, res.combinedDepth.framebuffer.id, state.width, state.height);
    mergeVoxyDepth(
        res,
        voxyDepthTexture,
        res.combinedDepth.framebuffer.id,
        state.width,
        state.height,
        viewport,
        modelView,
        projection);
    copyDepth(
        res,
        res.combinedDepth.getDepthTex().id,
        res.beforeSableDepth.framebuffer.id,
        state.width,
        state.height);

    state.restoreForSableRender(res.drawFramebuffer.id);
    activeState = state;

    if (!loggedEnabled) {
      Logger.info("Enabled Sable/Voxy combined depth shim");
      loggedEnabled = true;
    }
  }

  public static void end() {
    State state = activeState;
    if (state == null) {
      return;
    }
    if (--state.nesting > 0) {
      return;
    }

    Resources res = resources;
    copyChangedDepth(
        res, res.beforeSableDepth.getDepthTex().id, res.combinedDepth.getDepthTex().id, state);
    state.restoreAll();
    activeState = null;
  }

  private static boolean prepareDrawFramebuffer(Resources res, int sourceFramebuffer) {
    int maxDrawBuffers = glGetInteger(GL_MAX_DRAW_BUFFERS);
    int maxColorAttachments = glGetInteger(GL_MAX_COLOR_ATTACHMENTS);

    for (int i = 0; i < maxColorAttachments; i++) {
      glNamedFramebufferTexture(res.drawFramebuffer.id, GL_COLOR_ATTACHMENT0 + i, 0, 0);
    }

    int[] drawBuffers = new int[maxDrawBuffers];
    boolean hasColorTarget = false;
    for (int i = 0; i < maxDrawBuffers; i++) {
      int drawBuffer = glGetInteger(GL_DRAW_BUFFER0 + i);
      drawBuffers[i] = drawBuffer;

      if (drawBuffer == GL_NONE) {
        continue;
      }

      int attachmentIndex = drawBuffer - GL_COLOR_ATTACHMENT0;
      if (attachmentIndex < 0 || attachmentIndex >= maxColorAttachments) {
        if (!loggedUnsupportedFramebuffer) {
          Logger.warn(
              "Skipping Sable/Voxy depth shim because the active draw buffer is not a color attachment");
          loggedUnsupportedFramebuffer = true;
        }
        return false;
      }

      int attachment = GL_COLOR_ATTACHMENT0 + attachmentIndex;
      int attachmentType =
          glGetNamedFramebufferAttachmentParameteri(
              sourceFramebuffer, attachment, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
      int attachmentName =
          glGetNamedFramebufferAttachmentParameteri(
              sourceFramebuffer, attachment, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
      if (attachmentName == 0) {
        continue;
      }

      if (attachmentType == GL_TEXTURE) {
        glNamedFramebufferTexture(res.drawFramebuffer.id, attachment, attachmentName, 0);
      } else if (attachmentType == GL_RENDERBUFFER) {
        glNamedFramebufferRenderbuffer(
            res.drawFramebuffer.id, attachment, GL_RENDERBUFFER, attachmentName);
      } else {
        if (!loggedUnsupportedFramebuffer) {
          Logger.warn(
              "Skipping Sable/Voxy depth shim because an active color attachment is unsupported");
          loggedUnsupportedFramebuffer = true;
        }
        return false;
      }
      hasColorTarget = true;
    }

    if (!hasColorTarget) {
      return false;
    }

    glNamedFramebufferTexture(
        res.drawFramebuffer.id, GL_DEPTH_ATTACHMENT, res.combinedDepth.getDepthTex().id, 0);
    glNamedFramebufferDrawBuffers(res.drawFramebuffer.id, drawBuffers);

    int status = glCheckNamedFramebufferStatus(res.drawFramebuffer.id, GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
      if (!loggedUnsupportedFramebuffer) {
        Logger.warn(
            "Skipping Sable/Voxy depth shim because the temporary framebuffer is incomplete: "
                + status);
        loggedUnsupportedFramebuffer = true;
      }
      return false;
    }

    return true;
  }

  private static void copyDepth(
      Resources res, int sourceDepthTexture, int destinationFramebuffer, int width, int height) {
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, destinationFramebuffer);
    glViewport(0, 0, width, height);
    glDisable(GL_STENCIL_TEST);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_ALWAYS);
    glDepthMask(true);
    glColorMask(false, false, false, false);

    res.copyDepth.bind();
    glBindTextureUnit(0, sourceDepthTexture);
    glBindSampler(0, res.depthSampler);
    glUniform2f(1, 1.0f, 1.0f);
    res.copyDepth.blit();
  }

  private static void mergeVoxyDepth(
      Resources res,
      int voxyDepthTexture,
      int destinationFramebuffer,
      int width,
      int height,
      Viewport<?> viewport,
      Matrix4f modelView,
      Matrix4f projection) {
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, destinationFramebuffer);
    glViewport(0, 0, width, height);
    glDisable(GL_STENCIL_TEST);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);
    glDepthMask(true);
    glColorMask(false, false, false, false);

    res.transformDepth.bind();
    glBindTextureUnit(0, voxyDepthTexture);
    glBindSampler(0, res.depthSampler);

    new Matrix4f(viewport.MVP).invert().getToAddress(res.matrixScratch);
    nglUniformMatrix4fv(1, 1, false, res.matrixScratch);
    new Matrix4f(projection).mul(modelView).getToAddress(res.matrixScratch);
    nglUniformMatrix4fv(2, 1, false, res.matrixScratch);

    res.transformDepth.blit();
  }

  private static void copyChangedDepth(
      Resources res, int beforeDepthTexture, int afterDepthTexture, State state) {
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, state.drawFramebuffer);
    glViewport(state.x, state.y, state.width, state.height);
    glDisable(GL_STENCIL_TEST);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_ALWAYS);
    glDepthMask(true);
    glColorMask(false, false, false, false);

    res.copyChangedDepth.bind();
    glBindTextureUnit(0, beforeDepthTexture);
    glBindSampler(0, res.depthSampler);
    glBindTextureUnit(1, afterDepthTexture);
    glBindSampler(1, res.depthSampler);
    res.copyChangedDepth.blit();
  }

  private static final class State {
    private final int drawFramebuffer;
    private final int readFramebuffer;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int currentProgram;
    private final int activeTexture;
    private final int texture0;
    private final int texture1;
    private final int sampler0;
    private final int sampler1;
    private final int depthFunc;
    private final int depthMask;
    private final int[] colorMask;
    private final boolean depthTest;
    private final boolean stencilTest;

    private int nesting = 1;

    private State(
        int drawFramebuffer,
        int readFramebuffer,
        int x,
        int y,
        int width,
        int height,
        int currentProgram,
        int activeTexture,
        int texture0,
        int texture1,
        int sampler0,
        int sampler1,
        int depthFunc,
        int depthMask,
        int[] colorMask,
        boolean depthTest,
        boolean stencilTest) {
      this.drawFramebuffer = drawFramebuffer;
      this.readFramebuffer = readFramebuffer;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.currentProgram = currentProgram;
      this.activeTexture = activeTexture;
      this.texture0 = texture0;
      this.texture1 = texture1;
      this.sampler0 = sampler0;
      this.sampler1 = sampler1;
      this.depthFunc = depthFunc;
      this.depthMask = depthMask;
      this.colorMask = colorMask;
      this.depthTest = depthTest;
      this.stencilTest = stencilTest;
    }

    private static State capture() {
      int[] viewport = new int[4];
      int[] colorMask = new int[4];
      glGetIntegerv(GL_VIEWPORT, viewport);
      glGetIntegerv(GL_COLOR_WRITEMASK, colorMask);

      int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
      glActiveTexture(GL_TEXTURE0);
      int texture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
      glActiveTexture(GL_TEXTURE1);
      int texture1 = glGetInteger(GL_TEXTURE_BINDING_2D);
      glActiveTexture(activeTexture);

      return new State(
          glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),
          glGetInteger(GL_READ_FRAMEBUFFER_BINDING),
          viewport[0],
          viewport[1],
          viewport[2],
          viewport[3],
          glGetInteger(GL_CURRENT_PROGRAM),
          activeTexture,
          texture0,
          texture1,
          glGetIntegeri(GL_SAMPLER_BINDING, 0),
          glGetIntegeri(GL_SAMPLER_BINDING, 1),
          glGetInteger(GL_DEPTH_FUNC),
          glGetInteger(GL_DEPTH_WRITEMASK),
          colorMask,
          glIsEnabled(GL_DEPTH_TEST),
          glIsEnabled(GL_STENCIL_TEST));
    }

    private void restoreForSableRender(int shimFramebuffer) {
      this.restoreMutableState();
      glBindFramebuffer(GL_DRAW_FRAMEBUFFER, shimFramebuffer);
      glBindFramebuffer(GL_READ_FRAMEBUFFER, this.readFramebuffer);
      glViewport(0, 0, this.width, this.height);
    }

    private void restoreAll() {
      this.restoreMutableState();
      glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.drawFramebuffer);
      glBindFramebuffer(GL_READ_FRAMEBUFFER, this.readFramebuffer);
      glViewport(this.x, this.y, this.width, this.height);
    }

    private void restoreMutableState() {
      glUseProgram(this.currentProgram);
      glBindTextureUnit(0, this.texture0);
      glBindSampler(0, this.sampler0);
      glBindTextureUnit(1, this.texture1);
      glBindSampler(1, this.sampler1);
      glActiveTexture(this.activeTexture);

      if (this.depthTest) {
        glEnable(GL_DEPTH_TEST);
      } else {
        glDisable(GL_DEPTH_TEST);
      }
      if (this.stencilTest) {
        glEnable(GL_STENCIL_TEST);
      } else {
        glDisable(GL_STENCIL_TEST);
      }

      glDepthFunc(this.depthFunc);
      glDepthMask(this.depthMask != GL_FALSE);
      glColorMask(
          this.colorMask[0] != GL_FALSE,
          this.colorMask[1] != GL_FALSE,
          this.colorMask[2] != GL_FALSE,
          this.colorMask[3] != GL_FALSE);
    }
  }
}
