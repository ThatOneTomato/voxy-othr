package me.cortex.voxy.client.core.rendering.backend;

public record RenderBackendSelection(
    RenderBackendId id, String requested, String reason, boolean forced) {}
