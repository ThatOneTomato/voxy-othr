package me.cortex.voxy.client.core.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.IOException;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.rendering.backend.RenderFrame;
import me.cortex.voxy.client.core.rendering.backend.RenderStage;
import me.cortex.voxy.client.core.rendering.backend.ShaderPatchBridgePayload;
import me.cortex.voxy.client.iris.IrisBridgeShaderBindings;
import me.cortex.voxy.client.iris.IrisShaderPatch;
import me.cortex.voxy.client.iris.VoxyPatchDataAccess;
import me.cortex.voxy.client.mixin.iris.IrisRenderingPipelineAccessor;
import me.cortex.voxy.common.platform.PlatformAccess;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.minecraft.world.level.block.state.BlockState;

public class IrisUtil {
  public record CapturedViewportParameters(
      ChunkRenderMatrices matrices, double x, double y, double z) {
    public RenderFrame apply(VoxyRenderSystem vrs) {
      return vrs.setupFrame(this.matrices, this.x, this.y, this.z);
    }

    public RenderFrame runStage(VoxyRenderSystem vrs, RenderStage stage, RenderFrame frame) {
      return this.runStage(vrs, stage, frame, null);
    }

    public RenderFrame runStage(
        VoxyRenderSystem vrs, RenderStage stage, RenderFrame frame, Object payload) {
      return vrs.runFrameStage(
          stage,
          frame,
          this.matrices,
          this.x,
          this.y,
          this.z,
          IRIS_INSTALLED,
          irisShaderPackEnabled(),
          payload);
    }
  }

  public static CapturedViewportParameters CAPTURED_VIEWPORT_PARAMETERS;

  // Render-thread-only cache for the strict-bridge shader bindings. Keyed by (pipeline, patch)
  // identity so the value suppliers - in particular VoxyUniforms' PreviousMat (vx*Prev) - persist
  // and accumulate across frames instead of being rebuilt (and reset to identity) every frame. See
  // captureShaderPatchBridgePayload for the failure this prevents. Replaced on pack reload (new
  // pipeline/patch instances); holding the previous pipeline until then is a single bounded ref.
  private static IrisRenderingPipeline cachedBindingsPipeline;
  private static IrisShaderPatch cachedBindingsPatch;
  private static IrisBridgeShaderBindings.Bindings cachedBindings;

  public static final boolean IRIS_INSTALLED = PlatformAccess.get().isModLoaded("iris");
  public static final boolean SHADER_SUPPORT =
      true; // System.getProperty("voxy.enableExperimentalIrisPipeline",

  // "false").equalsIgnoreCase("true");

  public static CapturedViewportParameters getCapturedOrFallbackViewportParameters() {
    return CAPTURED_VIEWPORT_PARAMETERS;
  }

  private static boolean irisShadowActive0() {
    return ShadowRenderer.ACTIVE;
  }

  public static boolean irisShadowActive() {
    return IRIS_INSTALLED && irisShadowActive0();
  }

  public static void clearIrisSamplers() {
    if (IRIS_INSTALLED) clearIrisSamplers0();
  }

  public static void reload() {
    if (IRIS_INSTALLED) reload0();
  }

  private static void reload0() {
    try {
      if (IrisApi.getInstance().isShaderPackInUse()
          || IrisApi.getInstance()
              .getConfig()
              .areShadersEnabled()) { // Only reload if there is a shaderpack
        Iris.reload();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void clearIrisSamplers0() {
    for (int i = 0; i < 16; i++) {
      IrisRenderSystem.bindSamplerToUnit(i, 0);
    }
  }

  private static boolean irisShaderPackEnabled0() {
    return Iris.isPackInUseQuick();
  }

  public static boolean irisShaderPackEnabled() {
    return IRIS_INSTALLED && irisShaderPackEnabled0();
  }

  private static Object2IntMap<BlockState> irisBlockStateIds0() {
    return WorldRenderingSettings.INSTANCE.getBlockStateIds();
  }

  public static Object2IntMap<BlockState> irisBlockStateIds() {
    return irisShaderPackEnabled() ? irisBlockStateIds0() : null;
  }

  public static ShaderPatchBridgePayload captureShaderPatchBridgePayload(
      IrisRenderingPipeline pipeline) {
    if (!irisShaderPackEnabled()) {
      return ShaderPatchBridgePayload.unavailable("Iris shader pack is not active");
    }
    if (!(pipeline instanceof VoxyPatchDataAccess patchSource)) {
      return ShaderPatchBridgePayload.unavailable("Iris pipeline has no Voxy patch accessor");
    }
    IrisShaderPatch patch = patchSource.voxy$getPatchData();
    if (patch == null) {
      return ShaderPatchBridgePayload.unavailable("active shader pack does not provide voxy.json");
    }
    RenderTargets renderTargets = ((IrisRenderingPipelineAccessor) pipeline).getRenderTargets();
    int[] drawTargets = patch.getOpqaueTargets();
    int[] targetTextures = resolveTargetTextures(pipeline, renderTargets, drawTargets, false);
    if (targetTextures == null) {
      return ShaderPatchBridgePayload.unavailable("shader pack Voxy draw targets are empty");
    }
    IrisBridgeShaderBindings.Bindings bindings;
    try {
      bindings = acquireBindings(pipeline, patch);
    } catch (RuntimeException e) {
      return ShaderPatchBridgePayload.unavailable(
          "could not build Iris shader resource bindings: " + e.getMessage());
    }
    return ShaderPatchBridgePayload.strict(
        drawTargets,
        targetTextures,
        renderTargets.getDepthTexture(),
        // The bridge runs after Iris beginHand(), where noHand/depthtex2 has just
        // been copied from the current depth target. depthtex1/noTranslucents is
        // copied later in beginTranslucents() and can be stale at this hook point.
        renderTargets.getDepthTextureNoHand().getTextureId(),
        renderTargets.getCurrentWidth(),
        renderTargets.getCurrentHeight(),
        targetTextureMaxWidth,
        targetTextureMaxHeight,
        bindings.shaderHeader(),
        bindings.uniformSize(),
        bindings.uniformUpdater(),
        patch.getPatchOpaqueSource(),
        patch.getTAAShift(),
        bindings.samplerCount(),
        bindings.samplerTargets(),
        patch.createBlendSetup(),
        bindings.resourceBinder(),
        bindings.programSetup());
  }

  /**
   * Distant translucent (water) variant of {@link #captureShaderPatchBridgePayload}, captured at
   * Iris {@code beginTranslucents()} RETURN. It carries the pack's TRANSLUCENT patch + translucent
   * draw targets and the fresh {@code depthtex1}/noTranslucents (opaque scene) depth for near/far
   * masking. The shader-resource bindings are identical to the opaque payload (same pipeline +
   * patch), so they reuse the same per-pipeline cache. If the pack has no translucent Voxy patch we
   * report unavailable so the backend skips distant water rather than degrading to vanilla water.
   */
  public static ShaderPatchBridgePayload captureTranslucentShaderPatchBridgePayload(
      IrisRenderingPipeline pipeline) {
    if (!irisShaderPackEnabled()) {
      return ShaderPatchBridgePayload.unavailable("Iris shader pack is not active");
    }
    if (!(pipeline instanceof VoxyPatchDataAccess patchSource)) {
      return ShaderPatchBridgePayload.unavailable("Iris pipeline has no Voxy patch accessor");
    }
    IrisShaderPatch patch = patchSource.voxy$getPatchData();
    if (patch == null) {
      return ShaderPatchBridgePayload.unavailable("active shader pack does not provide voxy.json");
    }
    String translucentPatch = patch.getPatchTranslucentSource();
    if (translucentPatch == null || translucentPatch.isBlank()) {
      return ShaderPatchBridgePayload.unavailable(
          "active shader pack provides no translucent Voxy patch");
    }
    RenderTargets renderTargets = ((IrisRenderingPipelineAccessor) pipeline).getRenderTargets();
    int[] drawTargets = patch.getTranslucentTargets();
    int[] targetTextures = resolveTargetTextures(pipeline, renderTargets, drawTargets, true);
    if (targetTextures == null) {
      return ShaderPatchBridgePayload.unavailable("shader pack translucent draw targets are empty");
    }
    IrisBridgeShaderBindings.Bindings bindings;
    try {
      bindings = acquireBindings(pipeline, patch);
    } catch (RuntimeException e) {
      return ShaderPatchBridgePayload.unavailable(
          "could not build Iris shader resource bindings: " + e.getMessage());
    }
    return ShaderPatchBridgePayload.strict(
        drawTargets,
        targetTextures,
        renderTargets.getDepthTexture(),
        // beginTranslucents() copies depthtex1/noTranslucents (the opaque scene depth, no
        // translucents) at its start, so at RETURN it is current-frame fresh - exactly the near
        // opaque depth the distant water must be occluded against.
        renderTargets.getDepthTextureNoTranslucents().getTextureId(),
        renderTargets.getCurrentWidth(),
        renderTargets.getCurrentHeight(),
        targetTextureMaxWidth,
        targetTextureMaxHeight,
        bindings.shaderHeader(),
        bindings.uniformSize(),
        bindings.uniformUpdater(),
        // The payload's "opaqueFragmentPatch" slot is the generic fragment patch the bridge embeds;
        // for the translucent job it carries the pack's translucent (gbuffers_water) patch.
        translucentPatch,
        patch.getTAAShift(),
        bindings.samplerCount(),
        bindings.samplerTargets(),
        patch.createBlendSetup(),
        bindings.resourceBinder(),
        bindings.programSetup());
  }

  // Side-channel outputs of the most recent resolveTargetTextures call (render-thread only): the
  // max width/height across the resolved render targets, used to size the bridge output.
  private static int targetTextureMaxWidth;
  private static int targetTextureMaxHeight;

  private static int[] resolveTargetTextures(
      IrisRenderingPipeline pipeline,
      RenderTargets renderTargets,
      int[] drawTargets,
      boolean afterTranslucent) {
    if (drawTargets == null || drawTargets.length == 0) {
      return null;
    }
    // colortex targets ping-pong (main/alt) between Iris passes. The OPAQUE bridge runs before the
    // deferred passes flip them, so it must read the after-prepare flip snapshot; the TRANSLUCENT
    // (gbuffers_water) bridge runs at beginTranslucents RETURN, AFTER the deferred passes flipped
    // colortex, so it must read the after-translucent snapshot - exactly as Iris selects the
    // gbuffer framebuffer (isBeforeTranslucent ? flippedAfterPrepare : flippedAfterTranslucent) and
    // SodiumPrograms (pass == TRANSLUCENT ? getFlippedAfterTranslucent() :
    // getFlippedAfterPrepare()).
    // Using the after-prepare set for the translucent stage wrote distant water into the wrong
    // physical texture of colortex0, so it never reached the presented image.
    var flipped =
        afterTranslucent
            ? pipeline.getFlippedAfterTranslucent()
            : pipeline.getFlippedAfterPrepare();
    int[] targetTextures = new int[drawTargets.length];
    int width = 0;
    int height = 0;
    for (int i = 0; i < drawTargets.length; i++) {
      RenderTarget target = renderTargets.getOrCreate(drawTargets[i]);
      targetTextures[i] =
          flipped.contains(drawTargets[i]) ? target.getAltTexture() : target.getMainTexture();
      width = Math.max(width, target.getWidth());
      height = Math.max(height, target.getHeight());
    }
    if (width <= 0 || height <= 0) {
      return null;
    }
    targetTextureMaxWidth = width;
    targetTextureMaxHeight = height;
    return targetTextures;
  }

  /**
   * Returns the per-pipeline shader-resource bindings, building them at most once per (pipeline,
   * patch) identity. The Bindings - and crucially the per-uniform value suppliers it captures,
   * including VoxyUniforms' PreviousMat (vx*Prev) which accumulates last-frame matrices across
   * get() calls - must be built ONCE per pipeline. Rebuilding every frame reconstructed those
   * PreviousMat instances with their initial identity, so vxModelViewPrev/vxProjPrev/vxViewProjPrev
   * were ALWAYS the identity matrix in the strict program, silently breaking every previous-frame
   * reprojection inside the gbuffer pass (most visibly the mainLighting.glsl VOXY screenspace
   * LOD-shadow reprojection). Cache by (pipeline, patch) identity and rebuild only on pack reload.
   * Throws on build failure (and clears the cache) so callers can report it as unavailable.
   */
  private static IrisBridgeShaderBindings.Bindings acquireBindings(
      IrisRenderingPipeline pipeline, IrisShaderPatch patch) {
    if (cachedBindings != null
        && cachedBindingsPipeline == pipeline
        && cachedBindingsPatch == patch) {
      return cachedBindings;
    }
    IrisBridgeShaderBindings.Bindings bindings;
    try {
      bindings =
          IrisBridgeShaderBindings.build(
              pipeline,
              patch,
              pipeline.getCustomUniforms(),
              ((IrisRenderingPipelineAccessor) pipeline).getShaderStorageBufferHolder());
    } catch (RuntimeException e) {
      cachedBindings = null;
      cachedBindingsPipeline = null;
      cachedBindingsPatch = null;
      throw e;
    }
    cachedBindings = bindings;
    cachedBindingsPipeline = pipeline;
    cachedBindingsPatch = patch;
    return bindings;
  }

  private static boolean irisShadersEnabledInConfig0() {
    return !Iris.getCurrentPack().isEmpty();
  }

  public static boolean irisShadersEnabledInConfig() {
    return IRIS_INSTALLED && irisShadersEnabledInConfig0();
  }

  public static void disableIrisShaders() {
    if (IRIS_INSTALLED) disableIrisShaders0();
  }

  private static void disableIrisShaders0() {
    IrisApi.getInstance().getConfig().setShadersEnabledAndApply(false); // Disable shaders
  }
}
