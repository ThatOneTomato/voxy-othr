package me.cortex.voxy.common.platform;

import java.nio.file.Path;

/**
 * Loader abstraction: each loader source set (src/fabric, src/neoforge) provides a
 * {@code PlatformUtilImpl} in this package implementing this interface.
 */
public interface PlatformUtil {
    boolean isModLoaded(String modId);

    /** Returns the root path of the given mod, or null if unavailable. */
    Path getModRootPath(String modId);

    /** Returns the mod version string, or null if unavailable. */
    String getModVersion(String modId);

    /** Returns the config directory path for the current runtime, used for config file placement. */
    Path getConfigDir();

    /** True when running as a dedicated server. */
    boolean isDedicatedServer();
}
