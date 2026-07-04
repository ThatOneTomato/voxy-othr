package me.cortex.voxy.client.core.rendering.backend.gl41metal;

import me.cortex.voxy.client.core.rendering.backend.RenderBackendId;
import me.cortex.voxy.client.core.rendering.backend.RenderFrame;
import me.cortex.voxy.client.core.rendering.backend.RenderFrameContext;
import org.joml.Matrix4f;

public record Frame(
    RenderFrameContext context,
    long frameId,
    int writeSlot,
    Matrix4f drawMvp,
    Matrix4f vanillaDrawMvp)
    implements RenderFrame {
  @Override
  public RenderBackendId backendId() {
    return RenderBackendId.GL41METAL;
  }
}
