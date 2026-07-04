package me.cortex.voxy.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.Consumer;
import java.util.function.Function;
import me.cortex.voxy.impl.VoxyCommon;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;

public class FabricVoxyClient extends VoxyClient implements ClientModInitializer {
  @Override
  @SuppressWarnings("unchecked")
  public void onInitializeClient() {
    ClientCommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess) -> {
          if (VoxyCommon.isAvailable()) {
            // The command tree is built against CommandSourceStack but only ever uses
            // source-agnostic context methods, so the erased cast is safe.
            dispatcher.register(
                (LiteralArgumentBuilder<FabricClientCommandSource>)
                    (Object) VoxyCommands.register());
          }
        });

    FabricLoader.getInstance()
        .getEntrypoints("frex_flawless_frames", Consumer.class)
        .forEach(
            api ->
                ((Consumer<Function<String, Consumer<Boolean>>>) api)
                    .accept(name -> active -> VoxyClient.setFrexState(name, active)));
  }
}
