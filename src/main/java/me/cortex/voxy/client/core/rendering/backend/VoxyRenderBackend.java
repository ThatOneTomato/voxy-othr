package me.cortex.voxy.client.core.rendering.backend;

import java.util.List;
import me.cortex.voxy.client.core.rendering.Viewport;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;

public interface VoxyRenderBackend extends AutoCloseable {
  RenderBackendId id();

  boolean rendersLodTerrain();

  /**
   * Advance this backend through a host frame stage. Backends ignore stages they do not participate
   * in by returning {@code frame} unchanged.
   */
  RenderFrame runFrameStage(RenderStage stage, RenderStageContext context, RenderFrame frame);

  RenderFrame setupFrame(RenderFrameContext context);

  void renderOpaque(RenderFrame frame);

  void setRenderDistance(float renderDistance);

  void addDebugInfo(List<String> debug);

  RenderFrameMatrices getLastFrameMatrices();

  /**
   * GL texture id of this backend's Voxy distant-terrain depth target for the most recent frame, or
   * 0 if it does not own one. The texture holds combined near+far depth (a depth-component texture
   * whose {@code texelFetch(...).r} yields normalised [0,1] depth). Iris shader packs sample it as
   * {@code vxDepthTexOpaque} / {@code vxDepthTexTrans} (see {@code MixinIrisSamplers}). Backends
   * that do not maintain a private distant-depth target (e.g. GL46, which renders into the
   * Iris-managed pipeline framebuffer) keep the default 0 and let the caller fall back to the Iris
   * depth target.
   */
  default int voxyDistantDepthTextureId() {
    return 0;
  }

  /**
   * GL texture id of the depth target that holds Voxy's LOD depth when it is NOT written to the
   * vanilla depth buffer (Iris pipeline with a private depth target), used by the Sable
   * compatibility depth shim to occlude contraptions against distant terrain. 0 when the backend
   * writes depth to the vanilla buffer already (no shim needed) or does not support the shim.
   */
  default int getSableOcclusionDepthTexture() {
    return 0;
  }

  /**
   * The viewport that produced {@link #getSableOcclusionDepthTexture()} (needed for its MVP and
   * dimensions), or null when the shim is unsupported.
   */
  default Viewport<?> getSableOcclusionViewport() {
    return null;
  }

  void onChunkTrackerReset();

  void onSectionRenderStateChanged(long sectionPos, boolean present);

  default void beginVanillaRenderSectionSync() {}

  default void syncVanillaRenderSections(ChunkRenderListIterable renderLists, boolean reverse) {}

  @Override
  void close();
}
