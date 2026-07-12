package me.cortex.voxy.client.core.util;

/**
 * Release builds deliberately omit GPU timestamp instrumentation. The renderer still calls this
 * facade at its stage boundaries, so retaining a no-op implementation keeps the render path free
 * of query creation, driver calls, polling, and result allocation.
 */
public class GPUTiming {
  public static GPUTiming INSTANCE = new GPUTiming();

  public void marker() {}

  public void marker(String label) {}

  public void setEnabled(boolean enable) {}

  public String getDebug() {
    return "";
  }

  public void tick() {}

  public void free() {}
}
