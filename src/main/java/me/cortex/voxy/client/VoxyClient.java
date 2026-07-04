package me.cortex.voxy.client;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.channels.NonWritableChannelException;
import java.util.HashSet;
import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.client.core.rendering.backend.RenderBackendId;
import me.cortex.voxy.client.core.rendering.backend.RenderBackendSelection;
import me.cortex.voxy.client.core.rendering.backend.RenderBackendSelector;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.impl.VoxyCommon;
import net.minecraft.client.Minecraft;

public abstract class VoxyClient {
  private static final HashSet<String> FREX = new HashSet<>();
  private static FileLock EXCLUSIVE_LOCK;
  private static RenderBackendSelection renderBackendSelection =
      new RenderBackendSelection(
          RenderBackendId.DISABLED,
          "uninitialized",
          "Voxy client has not initialized render backend selection yet",
          false);

  public static void initVoxyClient() {
    Capabilities.init(); // Ensure clinit is called

    renderBackendSelection = RenderBackendSelector.select(Capabilities.INSTANCE);
    boolean systemSupported = renderBackendSelection.id() != RenderBackendId.DISABLED;

    if (systemSupported
        && System.getProperty("voxy.exclusiveLock", "false").equalsIgnoreCase("true")) {
      // Try acquire the lock file
      var vf = Minecraft.getInstance().gameDirectory.toPath().resolve(".voxy");
      if (!vf.toFile().isDirectory()) {
        vf.toFile().mkdir();
      }
      try {
        FileOutputStream fis = new FileOutputStream(vf.resolve("voxy.lock").toFile());
        EXCLUSIVE_LOCK = fis.getChannel().lock(0, Long.MAX_VALUE, false);
      } catch (NonWritableChannelException | IOException e) {
        // If some error write to log and unsupport
        Logger.error("Failed to acquire exclusive voxy lock file, mod will be disabled");
        renderBackendSelection =
            new RenderBackendSelection(
                RenderBackendId.DISABLED,
                renderBackendSelection.requested(),
                "Failed to acquire exclusive voxy lock file",
                renderBackendSelection.forced());
      }
    }

    if (renderBackendSelection.id() == RenderBackendId.GL46) {
      VoxyCommon.setInstanceFactory(VoxyClientInstance::new);

      if (!Capabilities.INSTANCE.subgroup) {
        Logger.warn(
            "GPU does not support subgroup operations, expect some performance degradation");
      }

    } else if (renderBackendSelection.id() == RenderBackendId.GL41METAL) {
      Logger.warn("Voxy GL41Metal backend selected.");
      VoxyCommon.setInstanceFactory(VoxyClientInstance::new);
    } else {
      Logger.error(
          "Voxy is unsupported on your system. Selected backend: "
              + renderBackendSelection.id()
              + ", reason: "
              + renderBackendSelection.reason());
    }
  }

  public static RenderBackendSelection getRenderBackendSelection() {
    return renderBackendSelection;
  }

  public static RenderBackendId getRenderBackendId() {
    return renderBackendSelection.id();
  }

  protected static void setFrexState(String name, boolean active) {
    if (active) {
      FREX.add(name);
    } else {
      FREX.remove(name);
    }
  }

  public static boolean isFrexActive() {
    return !FREX.isEmpty();
  }

  public static int getOcclusionDebugState() {
    return 0;
  }

  public static boolean disableSodiumChunkRender() {
    return false; // getOcclusionDebugState() != 0;
  }
}
