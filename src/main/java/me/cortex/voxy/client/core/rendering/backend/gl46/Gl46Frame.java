package me.cortex.voxy.client.core.rendering.backend.gl46;

import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.backend.RenderBackendId;
import me.cortex.voxy.client.core.rendering.backend.RenderFrame;

// Only carries the viewport: the gl46 render pass reads the bound framebuffer and GL viewport
// directly at render time (matching the pre-abstraction VoxyRenderSystem behaviour) instead of
// using values captured at frame-setup time, which under Iris happens in beginLevelRendering with
// a different framebuffer bound.
public record Gl46Frame(Viewport<?> viewport) implements RenderFrame {
    @Override
    public RenderBackendId backendId() {
        return RenderBackendId.GL46;
    }
}
