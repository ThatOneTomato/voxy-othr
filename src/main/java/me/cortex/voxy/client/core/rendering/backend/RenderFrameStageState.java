package me.cortex.voxy.client.core.rendering.backend;

public final class RenderFrameStageState {
    private static RenderFrame currentFrame;

    private RenderFrameStageState() {}

    public static RenderFrame currentFrame() {
        return currentFrame;
    }

    public static RenderFrame store(RenderFrame frame) {
        currentFrame = frame;
        return frame;
    }

    public static RenderFrame consume() {
        RenderFrame frame = currentFrame;
        currentFrame = null;
        return frame;
    }

    public static void clear() {
        currentFrame = null;
    }
}
