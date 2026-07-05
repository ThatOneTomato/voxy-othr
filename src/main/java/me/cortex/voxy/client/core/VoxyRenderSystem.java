package me.cortex.voxy.client.core;

import static org.lwjgl.opengl.GL11.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11.glGetIntegerv;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;

import java.util.List;
import me.cortex.voxy.client.VoxyClient;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.backend.BackendContext;
import me.cortex.voxy.client.core.rendering.backend.RenderBackendId;
import me.cortex.voxy.client.core.rendering.backend.RenderBackendSelection;
import me.cortex.voxy.client.core.rendering.backend.RenderFrame;
import me.cortex.voxy.client.core.rendering.backend.RenderFrameContext;
import me.cortex.voxy.client.core.rendering.backend.RenderFrameMatrices;
import me.cortex.voxy.client.core.rendering.backend.RenderFrameStageState;
import me.cortex.voxy.client.core.rendering.backend.RenderStage;
import me.cortex.voxy.client.core.rendering.backend.RenderStageContext;
import me.cortex.voxy.client.core.rendering.backend.VoxyRenderBackend;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.Gl41MetalRenderBackend;
import me.cortex.voxy.client.core.rendering.backend.gl46.Gl46RenderBackend;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.world.WorldEngine;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import org.lwjgl.opengl.GL11;

public class VoxyRenderSystem {
  private final WorldEngine worldIn;
  private final RenderBackendSelection backendSelection;
  private final VoxyRenderBackend backend;

  // Fog parameters captured before modification by MixinFogRenderer, for Voxy's own fog pass
  private float capturedFogStart;
  private float capturedFogEnd;
  private final float[] capturedFogColor = new float[4];

  public VoxyRenderSystem(WorldEngine world, ServiceManager sm) {
    // Keep the world loaded, NOTE: this is done FIRST, to keep and ensure that even if the rest of
    // loading takes more
    // than timeout, we keep the world acquired
    world.acquireRef();
    Logger.info("Creating Voxy render system");

    System.gc();

    this.worldIn = world;
    this.backendSelection = VoxyClient.getRenderBackendSelection();

    try {
      this.backend = this.createBackend(new BackendContext(world, sm, this.backendSelection));
    } catch (RuntimeException e) {
      world.releaseRef(); // If something goes wrong, we must release the world first
      throw e;
    }

    Logger.info("Voxy render system created with backend '" + this.backend.id() + "'");
  }

  private VoxyRenderBackend createBackend(BackendContext context) {
    return switch (context.selection().id()) {
      case GL46 -> new Gl46RenderBackend(context);
      case GL41METAL -> new Gl41MetalRenderBackend(context);
      case DISABLED ->
          throw new IllegalStateException(
              "Cannot create Voxy render system for disabled backend: "
                  + context.selection().reason());
    };
  }

  public void setCapturedFog(float fogStart, float fogEnd, float[] fogColor) {
    this.capturedFogStart = fogStart;
    this.capturedFogEnd = fogEnd;
    System.arraycopy(fogColor, 0, this.capturedFogColor, 0, 4);
  }

  public float getCapturedFogStart() {
    return this.capturedFogStart;
  }

  public float getCapturedFogEnd() {
    return this.capturedFogEnd;
  }

  public float[] getCapturedFogColor() {
    return this.capturedFogColor;
  }

  /** See {@link VoxyRenderBackend#getSableOcclusionDepthTexture()}. */
  public int getSableOcclusionDepthTexture() {
    return this.backend.getSableOcclusionDepthTexture();
  }

  /** See {@link VoxyRenderBackend#getSableOcclusionViewport()}. */
  public Viewport<?> getSableOcclusionViewport() {
    return this.backend.getSableOcclusionViewport();
  }

  public RenderFrame setupFrame(
      ChunkRenderMatrices matrices, double cameraX, double cameraY, double cameraZ) {
    return this.backend.setupFrame(this.captureFrameContext(matrices, cameraX, cameraY, cameraZ));
  }

  public RenderFrame runFrameStage(
      RenderStage stage,
      RenderFrame frame,
      ChunkRenderMatrices matrices,
      double cameraX,
      double cameraY,
      double cameraZ,
      boolean irisActive,
      boolean shaderPackActive) {
    return this.runFrameStage(
        stage, frame, matrices, cameraX, cameraY, cameraZ, irisActive, shaderPackActive, null);
  }

  public RenderFrame runFrameStage(
      RenderStage stage,
      RenderFrame frame,
      ChunkRenderMatrices matrices,
      double cameraX,
      double cameraY,
      double cameraZ,
      boolean irisActive,
      boolean shaderPackActive,
      Object payload) {
    return this.backend.runFrameStage(
        stage,
        new RenderStageContext(
            this.captureFrameContext(matrices, cameraX, cameraY, cameraZ),
            irisActive,
            shaderPackActive,
            payload),
        frame);
  }

  public RenderFrame runFrameStage(
      RenderStage stage,
      RenderFrame frame,
      ChunkRenderMatrices matrices,
      double cameraX,
      double cameraY,
      double cameraZ) {
    return this.runFrameStage(stage, frame, matrices, cameraX, cameraY, cameraZ, false, false);
  }

  private RenderFrameContext captureFrameContext(
      ChunkRenderMatrices matrices, double cameraX, double cameraY, double cameraZ) {
    int[] dims = new int[4];
    glGetIntegerv(GL_VIEWPORT, dims);
    return new RenderFrameContext(
        matrices,
        cameraX,
        cameraY,
        cameraZ,
        GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),
        dims[0],
        dims[1],
        dims[2],
        dims[3]);
  }

  public void renderOpaque(RenderFrame frame) {
    if (frame == null) {
      return;
    }
    this.backend.renderOpaque(frame);
  }

  public void setRenderDistance(float renderDistance) {
    this.backend.setRenderDistance(renderDistance);
  }

  public RenderFrameMatrices getLastFrameMatrices() {
    return this.backend.getLastFrameMatrices();
  }

  /**
   * GL texture id of the active backend's Voxy distant-terrain depth target (combined near+far,
   * sampled as {@code .r} for [0,1] depth), or 0 if the backend doesn't expose one. Used by {@code
   * MixinIrisSamplers} to bind {@code vxDepthTexOpaque} / {@code vxDepthTexTrans}; callers fall
   * back to the Iris depth target when this is 0.
   */
  public int getVoxyDistantDepthTextureId() {
    return this.backend.voxyDistantDepthTextureId();
  }

  public RenderBackendId getRenderBackendId() {
    return this.backend.id();
  }

  public RenderBackendSelection getRenderBackendSelection() {
    return this.backendSelection;
  }

  public boolean rendersLodTerrain() {
    return this.backend.rendersLodTerrain();
  }

  public void onChunkTrackerReset() {
    this.backend.onChunkTrackerReset();
  }

  public void onSectionRenderStateChanged(long sectionPos, boolean present) {
    this.backend.onSectionRenderStateChanged(sectionPos, present);
  }

  public void beginVanillaRenderSectionSync() {
    this.backend.beginVanillaRenderSectionSync();
  }

  public void syncVanillaRenderSections(ChunkRenderListIterable renderLists, boolean reverse) {
    this.backend.syncVanillaRenderSections(renderLists, reverse);
  }

  public void addDebugInfo(List<String> debug) {
    this.backend.addDebugInfo(debug);
  }

  public void shutdown() {
    try {
      RenderFrameStageState.clear();
      this.backend.close();
    } catch (Exception e) {
      Logger.error("Error shutting down Voxy render backend", e);
    } finally {
      // Release hold on the world
      this.worldIn.releaseRef();
      Logger.info("Render shutdown completed");
    }
  }

  public WorldEngine getEngine() {
    return this.worldIn;
  }
}
