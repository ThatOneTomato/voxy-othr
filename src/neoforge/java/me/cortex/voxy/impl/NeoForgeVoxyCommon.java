package me.cortex.voxy.impl;

import me.cortex.voxy.common.config.Serialization;
import net.neoforged.fml.common.Mod;

@Mod("voxy")
public class NeoForgeVoxyCommon extends VoxyCommon {
  public NeoForgeVoxyCommon() {
    Serialization.init();
  }
}
