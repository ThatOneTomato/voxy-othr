package me.cortex.voxy.client.mixin.minecraft;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.VoxyRenderSystemAccess;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FogRenderer.class, remap = true)
public class MixinFogRenderer {
  @Inject(
      method =
          "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V",
      at = @At("TAIL"))
  private static void voxy$overrideFog(
      Camera camera,
      FogMode fogMode,
      float viewDistance,
      boolean thickFog,
      float tickDelta,
      CallbackInfo ci) {
    var vrs = VoxyRenderSystemAccess.getNullable();
    if (vrs == null) return;

    if (RenderSystem.getShaderFogEnd() < 10.0f) return;

    // Adjust sky fog so it always looks smooth and doesn't change with render distance
    if (fogMode == FogMode.FOG_SKY) {
      RenderSystem.setShaderFogStart(0);
      RenderSystem.setShaderFogEnd(VoxyConfig.CONFIG.skyFogDistance);
    }

    if (fogMode == FogMode.FOG_TERRAIN) {
      // Do NOT override unique fog, it's always displayed close and meant for restricting vision
      boolean noFogType = camera.getFluidInCamera() == FogType.NONE;

      // Capture original fog values BEFORE we modify them,
      // so Voxy's own fog pass can use the correct values
      float capturedFogStart = RenderSystem.getShaderFogStart();
      float capturedFogEnd = RenderSystem.getShaderFogEnd();
      if (noFogType) {
        // Voxy hides vanilla terrain fog and applies its own pass over distant terrain. Use the
        // user-facing Voxy fog distance as the start and the user-facing Voxy render distance as
        // the end; using the traversal radius here makes normal fog effectively invisible.
        capturedFogStart = Math.max(0.0f, VoxyConfig.CONFIG.skyFogDistance);
        capturedFogEnd =
            Math.max(capturedFogStart + 1.0f, VoxyConfig.CONFIG.sectionRenderDistance * 32.0f);
      }

      vrs.setCapturedFog(capturedFogStart, capturedFogEnd, RenderSystem.getShaderFogColor());

      // Always hide vanilla terrain fog - either replaced by voxy or disabled completely
      // unless it's special fog, in that case it must be rendered to restrict vision in regular
      // chunks
      if (noFogType) {
        RenderSystem.setShaderFogStart(999999999);
        RenderSystem.setShaderFogEnd(999999999);
      }
    }
  }
}
