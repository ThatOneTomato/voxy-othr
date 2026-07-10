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
   * GL texture id of this backend's opaque Voxy distant-terrain depth target for the most recent
   * frame, or 0 if it does not own one. The depth-component texture yields normalised [0,1] depth
   * from {@code texelFetch(...).r}; Iris shader packs sample it as {@code vxDepthTexOpaque}.
   * Backends that render into the Iris-managed pipeline framebuffer keep the default 0 and let the
   * caller fall back to the Iris depth target.
   */
  default int voxyDistantOpaqueDepthTextureId() {
    return 0;
  }

  /**
   * Translucent counterpart of {@link #voxyDistantOpaqueDepthTextureId()}, sampled by Iris packs as
   * {@code vxDepthTexTrans}.
   */
  default int voxyDistantTranslucentDepthTextureId() {
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
