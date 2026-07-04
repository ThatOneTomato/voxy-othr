package me.cortex.voxy.common.platform;

import me.cortex.voxy.common.Logger;

/**
 * Static holder for the loader-specific {@link PlatformUtil}. {@code PlatformUtilImpl} is a
 * same-FQN dual implementation provided by each loader source set (src/fabric, src/neoforge), so
 * this class can instantiate it directly without depending on any loader API.
 */
public final class PlatformAccess {
  private static final PlatformUtil PLATFORM = new PlatformUtilImpl();

  public static final String MOD_VERSION;
  public static final boolean IS_IN_MINECRAFT;
  public static final boolean IS_DEDICATED_SERVER;

  static {
    var version = PLATFORM.getModVersion("voxy");
    if (version == null) {
      IS_IN_MINECRAFT = false;
      Logger.error("Running voxy without minecraft");
      MOD_VERSION = "<UNKNOWN>";
      IS_DEDICATED_SERVER = false;
    } else {
      IS_IN_MINECRAFT = true;
      MOD_VERSION = version;
      IS_DEDICATED_SERVER = PLATFORM.isDedicatedServer();
    }
  }

  private PlatformAccess() {}

  public static PlatformUtil get() {
    return PLATFORM;
  }
}
