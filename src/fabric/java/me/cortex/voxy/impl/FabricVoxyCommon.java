package me.cortex.voxy.impl;

import me.cortex.voxy.common.config.Serialization;
import net.fabricmc.api.ModInitializer;

public class FabricVoxyCommon extends VoxyCommon implements ModInitializer {
    @Override
    public void onInitialize() {
        Serialization.init();
    }
}
