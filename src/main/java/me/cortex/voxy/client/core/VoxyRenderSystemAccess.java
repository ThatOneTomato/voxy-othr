package me.cortex.voxy.client.core;

import net.minecraft.client.Minecraft;

public interface VoxyRenderSystemAccess {
    VoxyRenderSystem voxy$getRenderSystem();
    void voxy$shutdownRenderer();
    void voxy$createRenderer();

    static VoxyRenderSystem getNullable() {
        var lr = (VoxyRenderSystemAccess)Minecraft.getInstance().levelRenderer;
        if (lr == null) return null;
        return lr.voxy$getRenderSystem();
    }
}
