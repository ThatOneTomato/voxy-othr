package me.cortex.voxy.client.core.rendering.backend.gl41metal;

import me.cortex.voxy.client.core.rendering.backend.gl41metal.jni.NativeBindings;
import org.lwjgl.system.Platform;

public final class Gl41MetalSupport {
  private Gl41MetalSupport() {}

  public static String getUnsupportedReason() {
    if (Platform.get() != Platform.MACOSX) {
      return "macOS is required";
    }
    if (!NativeBindings.isLoaded()) {
      return "native library unavailable: " + NativeBindings.loadFailure();
    }
    String nativeReason = NativeBindings.getUnsupportedReason();
    if (nativeReason != null && !nativeReason.isBlank()) {
      return nativeReason;
    }
    return null;
  }
}
