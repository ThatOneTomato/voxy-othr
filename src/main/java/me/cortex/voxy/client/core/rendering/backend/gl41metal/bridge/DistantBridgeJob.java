package me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge;

import java.util.function.LongConsumer;

/** Inputs needed to draw direct distant geometry into an Iris framebuffer. */
public record DistantBridgeJob(
    int[] targetTextureIds,
    boolean colorWriteEnabled,
    int sourceDepthTextureId,
    int sourceDepthWidth,
    int sourceDepthHeight,
    int outputWidth,
    int outputHeight,
    int uniformBufferBytes,
    LongConsumer uniformUpdater,
    Runnable resourceBinder,
    Runnable blendSetup) {
  private static final Runnable NOOP = () -> {};
  private static final LongConsumer NOOP_UNIFORM_UPDATER = ptr -> {};

  public DistantBridgeJob {
    targetTextureIds = targetTextureIds == null ? new int[0] : targetTextureIds.clone();
    uniformUpdater = uniformUpdater == null ? NOOP_UNIFORM_UPDATER : uniformUpdater;
    resourceBinder = resourceBinder == null ? NOOP : resourceBinder;
    blendSetup = blendSetup == null ? NOOP : blendSetup;
  }

  boolean valid() {
    return this.outputWidth > 0
        && this.outputHeight > 0
        && this.sourceDepthTextureId != 0
        && this.targetTextureIds.length != 0;
  }
}
