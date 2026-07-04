package me.cortex.voxy.client.core.rendering.backend;

import java.util.List;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;

public interface VoxyRenderBackend extends AutoCloseable {
    RenderBackendId id();

    boolean rendersLodTerrain();

    default RenderFrame runFrameStage(
            RenderStage stage, RenderStageContext context, RenderFrame frame) {
        if (stage != RenderStage.LEGACY_OPAQUE) {
            return frame;
        }
        RenderFrame renderFrame = frame;
        if (renderFrame == null) {
            renderFrame = this.setupFrame(context.frameContext());
        }
        this.renderOpaque(renderFrame);
        return renderFrame;
    }

    RenderFrame setupFrame(RenderFrameContext context);

    void renderOpaque(RenderFrame frame);

    void setRenderDistance(float renderDistance);

    void addDebugInfo(List<String> debug);

    RenderFrameMatrices getLastFrameMatrices();

    /**
     * GL texture id of this backend's Voxy distant-terrain depth target for the most recent frame,
     * or 0 if it does not own one. The texture holds combined near+far depth (a depth-component
     * texture whose {@code texelFetch(...).r} yields normalised [0,1] depth). Iris shader packs
     * sample it as {@code vxDepthTexOpaque} / {@code vxDepthTexTrans} (see {@code
     * MixinIrisSamplers}). Backends that do not maintain a private distant-depth target (e.g. GL46,
     * which renders into the Iris-managed pipeline framebuffer) keep the default 0 and let the
     * caller fall back to the Iris depth target.
     */
    default int voxyDistantDepthTextureId() {
        return 0;
    }

    void onChunkTrackerReset();

    void onSectionRenderStateChanged(long sectionPos, boolean present);

    default void beginVanillaRenderSectionSync() {}

    default void syncVanillaRenderSections(ChunkRenderListIterable renderLists, boolean reverse) {}

    @Override
    void close();
}
