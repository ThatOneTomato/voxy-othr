package me.cortex.voxy.client.core.rendering.backend.gl46;

import me.cortex.voxy.client.core.gl.Capabilities;

public final class Gl46Support {
  private Gl46Support() {}

  /** Returns null when the GL46 backend can run on this system, otherwise the blocking reason. */
  public static String getUnsupportedReason(Capabilities capabilities) {
    if (capabilities.hasBrokenDepthSampler) {
      return "AMD broken depth sampler detected";
    }
    if (!capabilities.compute || !capabilities.indirectParameters) {
      return "compute="
          + capabilities.compute
          + ", indirectParameters="
          + capabilities.indirectParameters;
    }
    return null;
  }
}
