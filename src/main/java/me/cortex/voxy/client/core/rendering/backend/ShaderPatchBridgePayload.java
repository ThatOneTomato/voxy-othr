package me.cortex.voxy.client.core.rendering.backend;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

public record ShaderPatchBridgePayload(
    boolean strictBridgeAvailable,
    String unavailableReason,
    int[] drawTargetIds,
    int[] targetTextureIds,
    int depthTextureId,
    int sourceDepthTextureId,
    int sourceDepthWidth,
    int sourceDepthHeight,
    int outputWidth,
    int outputHeight,
    String shaderHeader,
    int uniformBufferBytes,
    LongConsumer uniformUpdater,
    String opaqueFragmentPatch,
    Runnable blendSetup,
    Runnable resourceBinder,
    IntConsumer programSetup) {
  private static final Runnable NOOP = () -> {};
  private static final LongConsumer NOOP_UNIFORM_UPDATER = ptr -> {};
  private static final IntConsumer NOOP_PROGRAM_SETUP = program -> {};

  public ShaderPatchBridgePayload {
    unavailableReason = unavailableReason == null ? "" : unavailableReason;
    drawTargetIds = drawTargetIds == null ? new int[0] : drawTargetIds.clone();
    targetTextureIds = targetTextureIds == null ? new int[0] : targetTextureIds.clone();
    shaderHeader = shaderHeader == null ? "" : shaderHeader;
    uniformUpdater = uniformUpdater == null ? NOOP_UNIFORM_UPDATER : uniformUpdater;
    opaqueFragmentPatch = opaqueFragmentPatch == null ? "" : opaqueFragmentPatch;
    blendSetup = blendSetup == null ? NOOP : blendSetup;
    resourceBinder = resourceBinder == null ? NOOP : resourceBinder;
    programSetup = programSetup == null ? NOOP_PROGRAM_SETUP : programSetup;
  }

  public static ShaderPatchBridgePayload unavailable(String reason) {
    return new ShaderPatchBridgePayload(
        false,
        reason,
        new int[0],
        new int[0],
        0,
        0,
        0,
        0,
        0,
        0,
        "",
        0,
        NOOP_UNIFORM_UPDATER,
        "",
        NOOP,
        NOOP,
        NOOP_PROGRAM_SETUP);
  }

  public static ShaderPatchBridgePayload strict(
      int[] drawTargetIds,
      int[] targetTextureIds,
      int depthTextureId,
      int sourceDepthTextureId,
      int sourceDepthWidth,
      int sourceDepthHeight,
      int outputWidth,
      int outputHeight,
      String shaderHeader,
      int uniformBufferBytes,
      LongConsumer uniformUpdater,
      String opaqueFragmentPatch,
      Runnable blendSetup,
      Runnable resourceBinder,
      IntConsumer programSetup) {
    return new ShaderPatchBridgePayload(
        true,
        "",
        drawTargetIds,
        targetTextureIds,
        depthTextureId,
        sourceDepthTextureId,
        sourceDepthWidth,
        sourceDepthHeight,
        outputWidth,
        outputHeight,
        shaderHeader,
        uniformBufferBytes,
        uniformUpdater,
        opaqueFragmentPatch,
        blendSetup,
        resourceBinder,
        programSetup);
  }

  public int targetCount() {
    return this.targetTextureIds.length;
  }

  public int shaderKey() {
    return Objects.hash(this.shaderHeader, this.opaqueFragmentPatch, this.targetCount());
  }

  @Override
  public int[] drawTargetIds() {
    return this.drawTargetIds.clone();
  }

  @Override
  public int[] targetTextureIds() {
    return this.targetTextureIds.clone();
  }

  public String describeTargets() {
    return "drawTargets="
        + Arrays.toString(this.drawTargetIds)
        + ", textures="
        + Arrays.toString(this.targetTextureIds)
        + ", depthTexture="
        + this.depthTextureId
        + ", sourceDepthTexture="
        + this.sourceDepthTextureId
        + ", sourceDepthSize="
        + this.sourceDepthWidth
        + "x"
        + this.sourceDepthHeight
        + ", size="
        + this.outputWidth
        + "x"
        + this.outputHeight;
  }
}
