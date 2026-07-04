package me.cortex.voxy.client.core.rendering.backend.gl41metal;

import org.lwjgl.system.Platform;

public final class Gl41MetalSupport {
  private Gl41MetalSupport() {}

  public static String getUnsupportedReason() {
    if (Platform.get() != Platform.MACOSX) {
      return "macOS is required";
    }
    if (!Gl41MetalNative.isLoaded()) {
      return "native library unavailable: " + Gl41MetalNative.loadFailure();
    }
    String nativeReason = Gl41MetalNative.getUnsupportedReason();
    if (nativeReason != null && !nativeReason.isBlank()) {
      return nativeReason;
    }
    return null;
  }
}
