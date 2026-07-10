package me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge;

import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_COLOR_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_FUNC;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_GEQUAL;
import static org.lwjgl.opengl.GL11C.GL_GREATER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_1D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11C.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11C.glColorMask;
import static org.lwjgl.opengl.GL11C.glDepthFunc;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGetBooleanv;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glGetIntegerv;
import static org.lwjgl.opengl.GL11C.glViewport;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_BINDING_3D;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.GL_MAX_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL31C.GL_TEXTURE_BINDING_RECTANGLE;
import static org.lwjgl.opengl.GL33C.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33C.glBindSampler;

import me.cortex.voxy.client.core.rendering.backend.RenderFrameContext;
import me.cortex.voxy.client.core.rendering.backend.ShaderPatchBridgePayload;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge.BridgePrograms.BridgeProgram;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge.BridgePrograms.TranslucentBridgeProgram;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.terrain.LoadedVolumeBound;
import me.cortex.voxy.common.Logger;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

/**
 * Single OpenGL bridge that samples the Metal-produced shared distant gbuffer, reconstructs {@link
 * me.cortex.voxy.client.core.rendering.backend.ShaderPatchBridgePayload VoxyFragmentParameters} and
 * emits the distant terrain fragment through {@code voxy_emitFragment}.
 *
 * <p>This is the OpenGL counterpart of GL46 {@code quads.frag}: the gbuffer decode, depth
 * reprojection and near-depth occlusion mask are shared, and only the {@code voxy_emitFragment}
 * implementation differs between the two {@link DistantBridgeJob} flavours:
 *
 * <ul>
 *   <li>vanilla / no shader pack uses the built-in vanilla patch (GL46 non-patched lighting:
 *       lightmap sample + directional face tint), drawing straight into the source framebuffer; and
 *   <li>Iris strict uses the shader pack's patch, header, uniforms and SSBOs, drawing into the Iris
 *       render targets.
 * </ul>
 *
 * Keeping both flavours on one shader template and one Java bridge is the design boundary required
 * by the backend contract: the vanilla path must remain a specialization of the same distant output
 * the Iris path generalizes, not a parallel duplicate.
 */
public final class DistantTerrainBridge implements AutoCloseable {
  private static final boolean USE_MANUAL_DEPTH_MASK =
      Boolean.parseBoolean(System.getProperty("voxy.gl41metal.manualDepthMask", "true"));

  // Program compilation/caching and GLSL assembly.
  private final BridgePrograms programs;
  // Framebuffer/texture state + the composite passes.
  private final GbufferCompositor compositor;
  private boolean loggedFirstBridge;
  private boolean loggedFirstTranslucentBridge;

  public DistantTerrainBridge() {
    this.programs = new BridgePrograms();
    this.compositor = new GbufferCompositor(this.programs);
  }

  /** Iris strict shader-pack job: own FBO drawing into the Iris render targets. */
  public static DistantBridgeJob irisJob(ShaderPatchBridgePayload payload) {
    return new DistantBridgeJob(
        true,
        0,
        payload.targetTextureIds(),
        payload.depthTextureId(),
        true,
        payload.sourceDepthTextureId(),
        payload.sourceDepthWidth(),
        payload.sourceDepthHeight(),
        payload.outputWidth(),
        payload.outputHeight(),
        payload.shaderHeader(),
        payload.uniformBufferBytes(),
        payload.uniformUpdater(),
        payload.opaqueFragmentPatch(),
        payload.resourceBinder(),
        payload.programSetup(),
        null,
        false);
  }

  /**
   * Iris strict distant translucent (water) job. Same shape as {@link #irisJob} but the {@code
   * payload} carries the pack's TRANSLUCENT patch (in {@code opaqueFragmentPatch()}), translucent
   * draw targets and the {@code depthtex1}/noTranslucents near depth, and the {@code blendSetup}
   * applies the pack's translucent blend. Routed through {@link #renderTranslucent}, which samples
   * the tgbuffer0/1 front-surface ABI and shades it with the pack's gbuffers_water patch.
   */
  public static DistantBridgeJob translucentJob(ShaderPatchBridgePayload payload) {
    return new DistantBridgeJob(
        true,
        0,
        payload.targetTextureIds(),
        payload.depthTextureId(),
        true,
        payload.sourceDepthTextureId(),
        payload.sourceDepthWidth(),
        payload.sourceDepthHeight(),
        payload.outputWidth(),
        payload.outputHeight(),
        payload.shaderHeader(),
        payload.uniformBufferBytes(),
        payload.uniformUpdater(),
        payload.opaqueFragmentPatch(),
        payload.resourceBinder(),
        payload.programSetup(),
        payload.blendSetup(),
        true);
  }

  /**
   * Vanilla job: draw straight into the source framebuffer with the built-in patch. The source
   * depth texture used for the near mask is resolved from that framebuffer at draw time.
   */
  public static DistantBridgeJob vanillaJob(RenderFrameContext context, boolean colorWriteEnabled) {
    return new DistantBridgeJob(
        false,
        context.sourceFramebuffer(),
        new int[0],
        0,
        colorWriteEnabled,
        0,
        context.viewportWidth(),
        context.viewportHeight(),
        context.viewportWidth(),
        context.viewportHeight(),
        "",
        0,
        null,
        BridgePrograms.VANILLA_PATCH,
        null,
        null,
        null,
        false);
  }

  /**
   * Vanilla (no shader pack) distant water job: composite into the source framebuffer with the
   * built-in water shade. Routed through {@link #renderTranslucent}, which samples the tgbuffer0/1
   * front-surface ABI and depth-tests against the real scene depth.
   */
  public static DistantBridgeJob vanillaTranslucentJob(
      RenderFrameContext context, boolean colorWriteEnabled) {
    return new DistantBridgeJob(
        false,
        context.sourceFramebuffer(),
        new int[0],
        0,
        colorWriteEnabled,
        0,
        context.viewportWidth(),
        context.viewportHeight(),
        context.viewportWidth(),
        context.viewportHeight(),
        "",
        0,
        null,
        "",
        null,
        null,
        null,
        true);
  }

  public boolean render(
      RenderFrameContext context,
      DistantGbufferSlot slot,
      DistantBridgeJob job,
      Matrix4fc voxyMvp,
      Matrix4fc vanillaMvp) {
    if (!job.valid()) {
      return false;
    }
    if (!job.ownFramebuffer() && FogCapture.vanillaFogHidesDistant()) {
      return false;
    }
    BridgeProgram colorProgram = this.programs.programFor(job);
    if (colorProgram == null) {
      return false;
    }
    // The strict Iris path (own FBO, shader-pack patch) renders in a single pass into a Voxy-owned
    // private depth attachment, exactly like voxy-fabric's IrisVoxyRenderPipeline. Vanilla stays
    // single pass too; only the program shape differs (see programFor).
    boolean irisStrict = job.ownFramebuffer();

    StateSnapshot state = StateSnapshot.capture();
    try (MemoryStack stack = MemoryStack.stackPush()) {
      int sourceDepthTexture =
          job.ownFramebuffer()
              ? job.sourceDepthTextureId()
              : this.compositor.findFramebufferDepthTexture(job.sourceFramebuffer());
      int sourceDepthWidth = job.ownFramebuffer() ? job.sourceDepthWidth() : job.outputWidth();
      int sourceDepthHeight = job.ownFramebuffer() ? job.sourceDepthHeight() : job.outputHeight();
      boolean reverseDepth = state.depthFunc == GL_GEQUAL || state.depthFunc == GL_GREATER;

      boolean drawn;
      if (irisStrict) {
        // Strict Iris contract: we must have the near (Sodium opaque) depth to composite near vs
        // far. Without it we skip this frame's distant output rather than paint distant terrain
        // over the near scene Sodium already wrote into the Iris colour targets.
        if (sourceDepthTexture == 0) {
          return false;
        }
        drawn =
            this.compositor.runOpaquePass(
                stack,
                colorProgram,
                slot,
                job,
                voxyMvp,
                vanillaMvp,
                sourceDepthTexture,
                sourceDepthWidth,
                sourceDepthHeight,
                false,
                reverseDepth,
                true);
      } else {
        boolean useManualDepthMask = USE_MANUAL_DEPTH_MASK && sourceDepthTexture != 0;
        int nearDepthSnapshot =
            useManualDepthMask
                ? this.compositor.snapshotNearDepth(
                    sourceDepthTexture, sourceDepthWidth, sourceDepthHeight)
                : 0;
        if (useManualDepthMask && nearDepthSnapshot == 0) {
          useManualDepthMask = false;
        }
        drawn =
            this.compositor.runOpaquePass(
                stack,
                colorProgram,
                slot,
                job,
                voxyMvp,
                vanillaMvp,
                nearDepthSnapshot,
                sourceDepthWidth,
                sourceDepthHeight,
                useManualDepthMask,
                reverseDepth,
                false);
      }
      if (drawn && !this.loggedFirstBridge) {
        this.loggedFirstBridge = true;
        Logger.info(
            "Voxy GL41Metal distant terrain bridge sampled shared textures ("
                + (job.ownFramebuffer()
                    ? (irisStrict
                        ? "Iris strict shader-pack, private-depth single pass"
                        : "Iris debug")
                    : "vanilla built-in")
                + ")");
      }
      return drawn;
    } finally {
      state.restore();
    }
  }

  /** Prepares the strict-Iris MRT and private depth/stencil for direct distant geometry. */
  public boolean prepareDirectIrisOpaque(
      MemoryStack stack, DistantBridgeJob job, boolean reverseDepth) {
    if (job == null || !job.valid() || !job.ownFramebuffer() || job.sourceDepthTextureId() == 0) {
      return false;
    }
    return this.compositor.prepareDirectIrisOpaque(
        stack,
        job,
        job.sourceDepthTextureId(),
        job.sourceDepthWidth(),
        job.sourceDepthHeight(),
        reverseDepth);
  }

  /** Uploads the current Iris UBO and binds the pack textures for a direct colour draw. */
  public void bindDirectIrisResources(DistantBridgeJob job) {
    this.compositor.bindDirectIrisResources(job);
  }

  public void finishDirectIrisOpaque() {
    this.compositor.finishDirectIrisOpaque();
  }

  /** Prepares the Iris translucent MRT with a copy of opaque Voxy depth and a fresh near mask. */
  public boolean prepareDirectIrisTranslucent(
      MemoryStack stack, DistantBridgeJob job, boolean reverseDepth) {
    if (job == null || !job.valid() || !job.ownFramebuffer() || job.sourceDepthTextureId() == 0) {
      return false;
    }
    return this.compositor.prepareDirectIrisTranslucent(
        stack,
        job,
        job.sourceDepthTextureId(),
        job.sourceDepthWidth(),
        job.sourceDepthHeight(),
        reverseDepth);
  }

  public void beginDirectIrisTranslucentColor(DistantBridgeJob job) {
    this.compositor.beginDirectIrisTranslucentColor(job);
  }

  public void finishDirectIrisTranslucent() {
    this.compositor.finishDirectIrisTranslucent();
  }

  /**
   * Distant translucent (water) composite, run at Iris {@code beginTranslucents()} RETURN. By that
   * point Iris has copied the opaque scene depth into {@code depthtex1}/noTranslucents, the pack's
   * deferred passes have lit the opaque scene into the colour targets, and the near translucent
   * geometry has NOT drawn yet - so compositing the distant water here blends it over the lit
   * opaque scene and lets the near Sodium water/glass blend over it afterwards.
   *
   * <p>Deferred-hybrid design (see GL41METAL_BACKEND_PLAN.md): Metal already rasterized and
   * back-to-front resolved the distant translucent layers, leaving the FRONT-most surface in
   * tgbuffer0/1. This pass shades that front surface with the shader pack's gbuffers_water patch
   * (so it does NOT degrade to vanilla water under shaders) and composites it into the pack's
   * translucent draw targets with the pack's blend. Near/far occlusion against the opaque scene is
   * resolved by the same stencil coverage mask the opaque path uses, fed with noTranslucents depth.
   *
   * <p>FIRST CUT: only the front surface is pack-shaded; the deeper-layer {@code tgbufferAccum}
   * over-blend is a documented follow-up (the dominant ocean-surface case is single-layer, where
   * front == the only layer).
   */
  public boolean renderTranslucent(
      RenderFrameContext context,
      DistantGbufferSlot slot,
      DistantBridgeJob job,
      Matrix4fc voxyMvp,
      Matrix4fc vanillaMvp,
      LoadedVolumeBound bound) {
    if (!job.valid() || !job.translucent()) {
      return false;
    }
    boolean vanilla = !job.ownFramebuffer();
    if (vanilla && FogCapture.vanillaFogHidesDistant()) {
      return false;
    }
    if (!vanilla && job.sourceDepthTextureId() == 0) {
      // Strict Iris contract: without the near (opaque) depth we cannot occlude distant water
      // against the near scene, so skip this frame's distant translucent output.
      return false;
    }
    TranslucentBridgeProgram colorProgram = this.programs.programForTranslucent(job);
    if (colorProgram == null) {
      return false;
    }
    StateSnapshot state = StateSnapshot.capture();
    try (MemoryStack stack = MemoryStack.stackPush()) {
      boolean reverseDepth = state.depthFunc == GL_GEQUAL || state.depthFunc == GL_GREATER;
      boolean drawn =
          this.compositor.runTranslucentPass(
              stack,
              colorProgram,
              slot,
              job,
              voxyMvp,
              vanillaMvp,
              bound,
              job.sourceDepthTextureId(),
              job.sourceDepthWidth(),
              job.sourceDepthHeight(),
              reverseDepth,
              vanilla);
      if (drawn && !this.loggedFirstTranslucentBridge) {
        this.loggedFirstTranslucentBridge = true;
        Logger.info(
            "Voxy GL41Metal distant translucent bridge composited shared water textures ("
                + (vanilla ? "vanilla built-in water" : "Iris strict, front-surface pack-shaded")
                + ")");
      }
      return drawn;
    } finally {
      state.restore();
    }
  }

  /**
   * GL texture id of the Voxy-private Iris distant-terrain depth-stencil attachment
   * (GL_DEPTH24_STENCIL8) for the most recent strict Iris frame, or 0 before one has run. Iris
   * shader packs sample its depth component as {@code vxDepthTexOpaque} / {@code vxDepthTexTrans}:
   * a voxy-only, Voxy-NDC depth (distant geometry depth where Voxy drew, far=1.0 elsewhere),
   * matching voxy-fabric's IrisVoxyRenderPipeline.fb.getDepthTex() after its shaderDepthHackFix.
   */
  public int voxyDistantOpaqueDepthTextureId() {
    return this.compositor.irisOpaqueDepthTextureId();
  }

  public int voxyDistantTranslucentDepthTextureId() {
    return this.compositor.irisTranslucentDepthTextureId();
  }

  @Override
  public void close() {
    this.programs.close();
    this.compositor.close();
  }

  private record StateSnapshot(
      int drawFramebuffer,
      int readFramebuffer,
      int program,
      int vao,
      int arrayBuffer,
      int elementArrayBuffer,
      int activeTexture,
      int[] textures1d,
      int[] rectangleTextures,
      int[] textures2d,
      int[] textures3d,
      int[] samplers,
      int depthFunc,
      boolean depthMask,
      boolean colorMaskR,
      boolean colorMaskG,
      boolean colorMaskB,
      boolean colorMaskA,
      boolean blendEnabled,
      boolean depthEnabled,
      int viewportX,
      int viewportY,
      int viewportWidth,
      int viewportHeight) {
    static StateSnapshot capture() {
      int oldActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
      int[] viewport = new int[4];
      glGetIntegerv(GL_VIEWPORT, viewport);
      boolean colorMaskR;
      boolean colorMaskG;
      boolean colorMaskB;
      boolean colorMaskA;
      boolean depthMask;
      try (MemoryStack stack = MemoryStack.stackPush()) {
        var colorMask = stack.malloc(4);
        glGetBooleanv(GL_COLOR_WRITEMASK, colorMask);
        colorMaskR = colorMask.get(0) != 0;
        colorMaskG = colorMask.get(1) != 0;
        colorMaskB = colorMask.get(2) != 0;
        colorMaskA = colorMask.get(3) != 0;
        var depthMaskBuffer = stack.malloc(1);
        glGetBooleanv(GL_DEPTH_WRITEMASK, depthMaskBuffer);
        depthMask = depthMaskBuffer.get(0) != 0;
      }
      // The bridge only ever binds FRAGMENT-stage texture units: the reconstruction samplers on
      // units 0-5 (see GbufferCompositor) and the Iris pack samplers on SAMPLER_BINDING_BASE+i,
      // which IrisBridgeShaderBindings caps at GL_MAX_TEXTURE_IMAGE_UNITS - 1 (15 on Apple GL4.1).
      // Snapshotting GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS (80 on Apple) instead cost ~480 glGet*
      // calls per capture plus the matching rebinds on restore, several times per frame, on a
      // driver where every glGet is CPU-expensive. Units above the fragment budget are never
      // touched by this bridge and need no save/restore.
      int maxUnit =
          Math.max(
              glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS), GbufferCompositor.BOUND_TEXTURE_UNIT + 1);
      int[] rectangleTextures = new int[maxUnit];
      int[] textures1d = new int[maxUnit];
      int[] textures2d = new int[maxUnit];
      int[] textures3d = new int[maxUnit];
      int[] samplers = new int[maxUnit];
      for (int unit = 0; unit < maxUnit; unit++) {
        glActiveTexture(GL_TEXTURE0 + unit);
        textures1d[unit] = glGetInteger(GL_TEXTURE_BINDING_1D);
        rectangleTextures[unit] = glGetInteger(GL_TEXTURE_BINDING_RECTANGLE);
        textures2d[unit] = glGetInteger(GL_TEXTURE_BINDING_2D);
        textures3d[unit] = glGetInteger(GL_TEXTURE_BINDING_3D);
        samplers[unit] = glGetInteger(GL_SAMPLER_BINDING);
      }
      glActiveTexture(oldActiveTexture);
      return new StateSnapshot(
          glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),
          glGetInteger(GL_READ_FRAMEBUFFER_BINDING),
          glGetInteger(GL_CURRENT_PROGRAM),
          glGetInteger(GL_VERTEX_ARRAY_BINDING),
          glGetInteger(GL_ARRAY_BUFFER_BINDING),
          glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING),
          oldActiveTexture,
          textures1d,
          rectangleTextures,
          textures2d,
          textures3d,
          samplers,
          glGetInteger(GL_DEPTH_FUNC),
          depthMask,
          colorMaskR,
          colorMaskG,
          colorMaskB,
          colorMaskA,
          org.lwjgl.opengl.GL11C.glIsEnabled(GL_BLEND),
          org.lwjgl.opengl.GL11C.glIsEnabled(GL_DEPTH_TEST),
          viewport[0],
          viewport[1],
          viewport[2],
          viewport[3]);
    }

    void restore() {
      glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.drawFramebuffer);
      glBindFramebuffer(GL_READ_FRAMEBUFFER, this.readFramebuffer);
      glUseProgram(this.program);
      glBindVertexArray(this.vao);
      glBindBuffer(GL_ARRAY_BUFFER, this.arrayBuffer);
      glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.elementArrayBuffer);
      for (int unit = 0; unit < this.rectangleTextures.length; unit++) {
        restoreUnit(
            unit,
            this.textures1d[unit],
            this.rectangleTextures[unit],
            this.textures2d[unit],
            this.textures3d[unit],
            this.samplers[unit]);
      }
      glActiveTexture(this.activeTexture);
      glDepthFunc(this.depthFunc);
      glDepthMask(this.depthMask);
      glColorMask(this.colorMaskR, this.colorMaskG, this.colorMaskB, this.colorMaskA);
      if (this.blendEnabled) {
        glEnable(GL_BLEND);
      } else {
        glDisable(GL_BLEND);
      }
      if (this.depthEnabled) {
        glEnable(GL_DEPTH_TEST);
      } else {
        glDisable(GL_DEPTH_TEST);
      }
      glViewport(this.viewportX, this.viewportY, this.viewportWidth, this.viewportHeight);
    }

    private static void restoreUnit(
        int unit, int texture1d, int rectangleTexture, int texture2d, int texture3d, int sampler) {
      glActiveTexture(GL_TEXTURE0 + unit);
      org.lwjgl.opengl.GL11C.glBindTexture(org.lwjgl.opengl.GL11C.GL_TEXTURE_1D, texture1d);
      org.lwjgl.opengl.GL11C.glBindTexture(
          org.lwjgl.opengl.GL31C.GL_TEXTURE_RECTANGLE, rectangleTexture);
      org.lwjgl.opengl.GL11C.glBindTexture(GL_TEXTURE_2D, texture2d);
      org.lwjgl.opengl.GL11C.glBindTexture(org.lwjgl.opengl.GL12C.GL_TEXTURE_3D, texture3d);
      glBindSampler(unit, sampler);
    }
  }
}
