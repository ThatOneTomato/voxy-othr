package me.cortex.voxy.client.core.util;

import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.rendering.backend.RenderFrame;
import me.cortex.voxy.client.core.rendering.backend.RenderStage;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.shadows.ShadowRenderer;

import java.io.IOException;

public class IrisUtil {
    public record CapturedViewportParameters(ChunkRenderMatrices matrices, double x, double y, double z) {
        public RenderFrame apply(VoxyRenderSystem vrs) {
            return vrs.setupFrame(this.matrices, this.x, this.y, this.z);
        }

        public RenderFrame runStage(VoxyRenderSystem vrs, RenderStage stage, RenderFrame frame) {
            return this.runStage(vrs, stage, frame, null);
        }

        public RenderFrame runStage(VoxyRenderSystem vrs, RenderStage stage, RenderFrame frame, Object payload) {
            return vrs.runFrameStage(
                    stage,
                    frame,
                    this.matrices,
                    this.x,
                    this.y,
                    this.z,
                    IRIS_INSTALLED,
                    irisShaderPackEnabled(),
                    payload);
        }
    }

    public static CapturedViewportParameters CAPTURED_VIEWPORT_PARAMETERS;

    public static final boolean IRIS_INSTALLED = FabricLoader.getInstance().isModLoaded("iris");
    public static final boolean SHADER_SUPPORT = true;//System.getProperty("voxy.enableExperimentalIrisPipeline", "false").equalsIgnoreCase("true");


    private static boolean irisShadowActive0() {
        return ShadowRenderer.ACTIVE;
    }

    public static boolean irisShadowActive() {
        return IRIS_INSTALLED && irisShadowActive0();
    }

    public static void clearIrisSamplers() {
        if (IRIS_INSTALLED) clearIrisSamplers0();
    }
    public static void reload() {
        if (IRIS_INSTALLED) reload0();
    }

    private static void reload0() {
        try {
            if (IrisApi.getInstance().isShaderPackInUse()||IrisApi.getInstance().getConfig().areShadersEnabled()) {//Only reload if there is a shaderpack
                Iris.reload();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void clearIrisSamplers0() {
        for (int i = 0; i < 16; i++) {
            IrisRenderSystem.bindSamplerToUnit(i, 0);
        }
    }

    private static boolean irisShaderPackEnabled0() {
        return Iris.isPackInUseQuick();
    }

    public static boolean irisShaderPackEnabled() {
        return IRIS_INSTALLED && irisShaderPackEnabled0();
    }
    private static boolean irisShadersEnabledInConfig0() {
        return !Iris.getCurrentPack().isEmpty();
    }
    public static boolean irisShadersEnabledInConfig() {
        return IRIS_INSTALLED && irisShadersEnabledInConfig0();
    }
    public static void disableIrisShaders() {
        if(IRIS_INSTALLED) disableIrisShaders0();
    }
    private static void disableIrisShaders0() {
        IrisApi.getInstance().getConfig().setShadersEnabledAndApply(false);//Disable shaders
    }
}
