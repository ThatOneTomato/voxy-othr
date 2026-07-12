package me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge;

import me.cortex.voxy.client.core.rendering.backend.ShaderPatchBridgePayload;
import org.lwjgl.system.MemoryStack;

/** OpenGL/Iris state used by the direct GL41 drawlist renderer. */
public final class DistantTerrainBridge implements AutoCloseable {
  private final BridgePrograms programs;
  private final GbufferCompositor compositor;

  public DistantTerrainBridge() {
    this.programs = new BridgePrograms();
    this.compositor = new GbufferCompositor(this.programs);
  }

  public static DistantBridgeJob irisJob(ShaderPatchBridgePayload payload) {
    return createJob(payload, false);
  }

  public static DistantBridgeJob translucentJob(ShaderPatchBridgePayload payload) {
    return createJob(payload, true);
  }

  private static DistantBridgeJob createJob(ShaderPatchBridgePayload payload, boolean translucent) {
    return new DistantBridgeJob(
        payload.targetTextureIds(),
        true,
        payload.sourceDepthTextureId(),
        payload.sourceDepthWidth(),
        payload.sourceDepthHeight(),
        payload.outputWidth(),
        payload.outputHeight(),
        payload.uniformBufferBytes(),
        payload.uniformUpdater(),
        payload.resourceBinder(),
        translucent ? payload.blendSetup() : null);
  }

  public boolean prepareDirectIrisOpaque(MemoryStack stack, DistantBridgeJob job) {
    if (!validDirectJob(job)) {
      return false;
    }
    return this.compositor.prepareDirectIrisOpaque(stack, job);
  }

  public void bindDirectIrisResources(DistantBridgeJob job) {
    this.compositor.bindDirectIrisResources(job);
  }

  public void finishDirectIrisOpaque() {
    this.compositor.finishDirectIrisOpaque();
  }

  public boolean prepareDirectIrisTranslucent(
      MemoryStack stack, DistantBridgeJob job, boolean reverseDepth) {
    if (!validDirectJob(job)) {
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

  private static boolean validDirectJob(DistantBridgeJob job) {
    return job != null && job.valid();
  }
}
