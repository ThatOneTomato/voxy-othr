package me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge;

import static org.lwjgl.opengl.GL20C.glUniform1i;
import static org.lwjgl.opengl.GL20C.glUniform4f;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import net.minecraft.client.Minecraft;

/**
 * Captured-vanilla environmental fog: uniform upload + the "fog hides everything" frame skip.
 * The GLSL side lives in vanilla_patch.glsl / vanilla_water_patch.glsl; the parameters are the
 * ones MixinFogRenderer captured before neutralising vanilla terrain fog. The Iris paths never
 * touch this: shader packs fog their own scene.
 */
final class FogCapture {
  private FogCapture() {}

  /**
   * Uploads the captured-vanilla environmental fog uniforms (see {@code GLSL_VANILLA_FOG}). The
   * gl41metal counterpart of GL46 NormalRenderPipeline.finish's USE_ENV_FOG uniform block: the fog
   * parameters are the ones MixinFogRenderer captured before neutralising vanilla terrain fog.
   * renderVoxyFog=off or degenerate captured fog uploads intensity 0, which the shader treats as
   * "no fog". No-ops on programs that compile without the fog block (strict Iris / debug shapes).
   */
  static void setVanillaFogUniforms(
      int paramsUniform, int colorUniform, int shapeUniform) {
    if (paramsUniform < 0) {
      return;
    }
    var vrs = IGetVoxyRenderSystem.getNullable();
    float fogStart = vrs != null ? vrs.getCapturedFogStart() : RenderSystem.getShaderFogStart();
    float fogEnd = vrs != null ? vrs.getCapturedFogEnd() : RenderSystem.getShaderFogEnd();
    float[] fogColor = vrs != null ? vrs.getCapturedFogColor() : RenderSystem.getShaderFogColor();
    if (VoxyConfig.CONFIG.renderVoxyFog && Math.abs(fogEnd - fogStart) > 1) {
      glUniform4f(
          paramsUniform,
          fogStart,
          fogEnd,
          VoxyConfig.CONFIG.fogIntensity,
          VoxyConfig.CONFIG.fogDensity);
      if (colorUniform >= 0) {
        glUniform4f(colorUniform, fogColor[0], fogColor[1], fogColor[2], 1.0f);
      }
      if (shapeUniform >= 0) {
        glUniform1i(shapeUniform, RenderSystem.getShaderFogShape().getIndex());
      }
    } else {
      glUniform4f(paramsUniform, 0.0f, 0.0f, 0.0f, 0.0f);
      if (colorUniform >= 0) {
        glUniform4f(colorUniform, 0.0f, 0.0f, 0.0f, 0.0f);
      }
      if (shapeUniform >= 0) {
        glUniform1i(shapeUniform, 0);
      }
    }
  }

  /**
   * GL46 NormalRenderPipeline's "fogCoversAllRendering" skip: when the (captured) vanilla fog
   * closes before the vanilla render distance (in lava, blindness, powdered snow...), everything
   * beyond the near scene sits behind fully opaque fog, so the vanilla composite skips the distant
   * output entirely for the frame. The Iris paths never take this branch: shader packs own their
   * fog and Voxy has no business second-guessing it.
   */
  static boolean vanillaFogHidesDistant() {
    var vrs = IGetVoxyRenderSystem.getNullable();
    float fogEnd = vrs != null ? vrs.getCapturedFogEnd() : RenderSystem.getShaderFogEnd();
    return fogEnd < Minecraft.getInstance().gameRenderer.getRenderDistance();
  }
}
