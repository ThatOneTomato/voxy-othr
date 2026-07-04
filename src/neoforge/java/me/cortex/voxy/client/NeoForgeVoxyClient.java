package me.cortex.voxy.client;

import me.cortex.voxy.client.config.VoxyConfigScreenFactory;
import me.cortex.voxy.impl.VoxyCommon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "voxy", dist = Dist.CLIENT)
public class NeoForgeVoxyClient extends VoxyClient {
  public NeoForgeVoxyClient(IEventBus modBus, ModContainer container) {
    NeoForge.EVENT_BUS.addListener(
        (RegisterClientCommandsEvent evt) -> {
          if (VoxyCommon.isAvailable()) {
            evt.getDispatcher().register(VoxyCommands.register());
          }
        });

    container.registerExtensionPoint(
        IConfigScreenFactory.class,
        (mc, parent) -> {
          var screen = VoxyConfigScreenFactory.create(parent);
          return screen != null ? screen : parent;
        });
  }
}
