package me.cortex.voxy.client.mixin.sable;

import me.cortex.voxy.client.compat.sable.SableClientRenderDistance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(
    targets = "dev.ryanhcode.sable.sublevel.render.sodium.SubLevelRenderSectionManager",
    remap = false)
public abstract class MixinSableSubLevelRenderSectionManager {
  // The constructor descriptor contains a Minecraft class whose runtime name differs between
  // Fabric (intermediary) and NeoForge (mojmap), so match by owner+name only.
  @ModifyArg(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;<init>"),
      index = 1,
      remap = false)
  private static int voxy$extendSableRenderDistance(int renderDistanceChunks) {
    return SableClientRenderDistance.extendVanillaRenderDistanceChunks(renderDistanceChunks);
  }
}
