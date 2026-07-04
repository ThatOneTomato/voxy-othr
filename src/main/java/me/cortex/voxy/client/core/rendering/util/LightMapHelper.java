package me.cortex.voxy.client.core.rendering.util;

import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL33C.glBindSampler;

import net.minecraft.client.Minecraft;

public class LightMapHelper {
    public static void bind(int lightingIndex) {
        glBindSampler(lightingIndex, 0);
        // GL4.1-compatible binding (no DSA glBindTextureUnit), shared by gl46 and gl41metal.
        glActiveTexture(GL_TEXTURE0 + lightingIndex);
        glBindTexture(GL_TEXTURE_2D, getLightmapTextureId());
    }

    public static int getLightmapTextureId() {
        return Minecraft.getInstance().gameRenderer.lightTexture().lightTexture.getId();
    }
}
