package me.cortex.voxy.client.compat;

import java.nio.file.Path;
import me.cortex.voxy.common.platform.PlatformAccess;

public class FlashbackCompat {
  public static final boolean FLASHBACK_INSTALLED = PlatformAccess.get().isModLoaded("flashback");

  public static Path getReplayStoragePath() {
    // FlashbackCompatImpl is a same-FQN dual implementation per loader source set;
    // flashback is fabric-only so the neoforge implementation always returns null.
    if (!FLASHBACK_INSTALLED) {
      return null;
    }
    return FlashbackCompatImpl.getReplayStoragePath();
  }
}
