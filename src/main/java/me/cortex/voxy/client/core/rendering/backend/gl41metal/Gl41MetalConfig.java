package me.cortex.voxy.client.core.rendering.backend.gl41metal;

record Gl41MetalConfig(
    int slotCount,
    int waitTimeoutMs,
    boolean visibleComposite,
    int debugCompletionDelayMs,
    int meshBatchSize) {
  static Gl41MetalConfig fromSystemProperties() {
    return new Gl41MetalConfig(
        readInt("voxy.gl41metal.slotCount", 3, 2, 8),
        readInt("voxy.gl41metal.waitTimeoutMs", 0, 0, 100),
        readBoolean("voxy.gl41metal.visibleComposite", true),
        readInt("voxy.gl41metal.debugCompletionDelayMs", 0, 0, 1000),
        readInt("voxy.gl41metal.meshBatchSize", 32, 16, 64));
  }

  private static boolean readBoolean(String property, boolean fallback) {
    String value = System.getProperty(property);
    return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value.trim());
  }

  private static int readInt(String property, int fallback, int min, int max) {
    String value = System.getProperty(property);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }
}
