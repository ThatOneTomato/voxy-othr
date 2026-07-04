package me.cortex.voxy.client.core.rendering.backend;

import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;

public record RenderFrameContext(
    ChunkRenderMatrices matrices,
    double cameraX,
    double cameraY,
    double cameraZ,
    int sourceFramebuffer,
    int viewportX,
    int viewportY,
    int viewportWidth,
    int viewportHeight) {}
