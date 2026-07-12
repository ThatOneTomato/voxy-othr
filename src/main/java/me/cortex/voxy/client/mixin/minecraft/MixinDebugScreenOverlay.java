package me.cortex.voxy.client.mixin.minecraft;

import java.util.ArrayList;
import java.util.List;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.VoxyRenderSystemAccess;
import me.cortex.voxy.common.platform.PlatformAccess;
import me.cortex.voxy.impl.VoxyCommon;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DebugScreenOverlay.class)
public abstract class MixinDebugScreenOverlay {

  @Inject(at = @At("RETURN"), method = "getGameInformation")
  protected void getGameInformation(CallbackInfoReturnable<List<String>> info) {
    List<String> voxyLines = new ArrayList<>();

    if (!VoxyCommon.isAvailable()) {
      voxyLines.add(
          ChatFormatting.RED
              + "voxy-"
              + PlatformAccess.MOD_VERSION); // Voxy installed, not avalible
      return;
    }
    var instance = VoxyCommon.getInstance();
    if (instance == null) {
      voxyLines.add(
          ChatFormatting.YELLOW
              + "voxy-"
              + PlatformAccess.MOD_VERSION); // Voxy avalible, no instance active
      return;
    }
    VoxyRenderSystem vrs = null;
    var wr = Minecraft.getInstance().levelRenderer;
    if (wr != null) vrs = ((VoxyRenderSystemAccess) wr).voxy$getRenderSystem();

    // Voxy instance active
    voxyLines.add(
        (vrs == null ? ChatFormatting.DARK_GREEN : ChatFormatting.GREEN)
            + "voxy-"
            + PlatformAccess.MOD_VERSION);

    // lines.addLineToSection();
    List<String> instanceLines = new ArrayList<>();
    instance.addDebug(instanceLines);
    voxyLines.addAll(instanceLines);

    if (vrs != null) {
      List<String> renderLines = new ArrayList<>();
      vrs.addDebugInfo(renderLines);
      voxyLines.addAll(renderLines);
    }

    info.getReturnValue().addAll(voxyLines);
  }
}
