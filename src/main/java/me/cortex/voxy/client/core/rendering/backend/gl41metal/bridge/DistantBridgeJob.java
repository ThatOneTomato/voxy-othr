package me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge;

import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * Backend-internal description of one distant-terrain shader-patch bridge invocation.
 *
 * <p>This is the single input shape shared by the vanilla (no shader pack) and the Iris strict
 * paths, mirroring GL46 {@code quads.frag}'s single shader with a {@code PATCHED_SHADER} switch.
 * The GLSL reconstruction of {@code VoxyFragmentParameters}, the depth reprojection and the
 * near-depth occlusion mask are identical for both; only the {@link #fragmentPatch} (the built-in
 * vanilla {@code voxy_emitFragment} versus the shader pack's), the optional {@link #shaderHeader},
 * the draw targets and the resource bindings differ.
 *
 * <p>When {@link #ownFramebuffer} is {@code true} the bridge owns an FBO and attaches {@link
 * #targetTextureIds} + {@link #depthTextureId} (the Iris case). When {@code false} the bridge draws
 * directly into {@link #sourceFramebuffer} (the vanilla case) using whatever colour attachment 0
 * and depth attachment it already has, and {@link #sourceDepthTextureId} is resolved from that
 * framebuffer at draw time.
 */
public record DistantBridgeJob(
    boolean ownFramebuffer,
    int sourceFramebuffer,
    int[] targetTextureIds,
    int depthTextureId,
    boolean colorWriteEnabled,
    int sourceDepthTextureId,
    int sourceDepthWidth,
    int sourceDepthHeight,
    int outputWidth,
    int outputHeight,
    String shaderHeader,
    int uniformBufferBytes,
    LongConsumer uniformUpdater,
    String fragmentPatch,
    Runnable resourceBinder,
    IntConsumer programSetup,
    // Translucent-only: the shader pack's per-draw-buffer blend setup
    // (IrisShaderPatch.createBlendSetup),
    // applied before the translucent colour pass so the distant water composites with the pack's
    // gbuffers_water blend. NOOP for the opaque/vanilla paths (they draw with blending disabled).
    Runnable blendSetup,
    // Marks the distant translucent (water/glass) bridge invocation. The opaque/vanilla paths leave
    // this false; the translucent path samples the tgbuffer0/1 front-surface ABI and runs the
    // pack's
    // translucent patch into the translucent draw targets. See
    // DistantTerrainBridge.renderTranslucent.
    boolean translucent) {
  private static final Runnable NOOP = () -> {};
  private static final LongConsumer NOOP_UNIFORM_UPDATER = ptr -> {};
  private static final IntConsumer NOOP_PROGRAM_SETUP = program -> {};

  public DistantBridgeJob {
    targetTextureIds = targetTextureIds == null ? new int[0] : targetTextureIds.clone();
    shaderHeader = shaderHeader == null ? "" : shaderHeader;
    uniformUpdater = uniformUpdater == null ? NOOP_UNIFORM_UPDATER : uniformUpdater;
    fragmentPatch = fragmentPatch == null ? "" : fragmentPatch;
    resourceBinder = resourceBinder == null ? NOOP : resourceBinder;
    programSetup = programSetup == null ? NOOP_PROGRAM_SETUP : programSetup;
    blendSetup = blendSetup == null ? NOOP : blendSetup;
  }

  int targetCount() {
    return this.ownFramebuffer ? this.targetTextureIds.length : 1;
  }

  /** Stable identity for shader caching: header + patch + draw-target count + translucent flag. */
  int shaderKey() {
    return Objects.hash(
        this.shaderHeader, this.fragmentPatch, this.targetCount(), this.translucent);
  }

  boolean valid() {
    if (this.outputWidth <= 0 || this.outputHeight <= 0) {
      return false;
    }
    if (this.ownFramebuffer) {
      return this.targetTextureIds.length != 0;
    }
    return this.sourceFramebuffer != 0;
  }
}
