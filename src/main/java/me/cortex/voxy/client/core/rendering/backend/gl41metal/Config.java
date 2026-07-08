package me.cortex.voxy.client.core.rendering.backend.gl41metal;

public record Config(
    int slotCount,
    int waitTimeoutMs,
    boolean visibleComposite,
    int meshBatchSize,
    PipelineMode pipelineMode) {
  static Config fromSystemProperties() {
    return new Config(
        readInt("voxy.gl41metal.slotCount", 3, 2, 8),
        readInt("voxy.gl41metal.waitTimeoutMs", 0, 0, 100),
        readBoolean("voxy.gl41metal.visibleComposite", true),
        readInt("voxy.gl41metal.meshBatchSize", 64, 16, 64),
        readPipelineMode("voxy.gl41metal.pipeline", PipelineMode.SHARED_GBUFFER));
  }

  public boolean useDrawlistPipeline() {
    return this.pipelineMode == PipelineMode.DRAWLIST;
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

  private static PipelineMode readPipelineMode(String property, PipelineMode fallback) {
    String value = System.getProperty(property);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "shared_gbuffer", "shared-gbuffer", "gbuffer" -> PipelineMode.SHARED_GBUFFER;
      case "drawlist", "draw_list", "draw-list" -> PipelineMode.DRAWLIST;
      default -> fallback;
    };
  }

  public enum PipelineMode {
    SHARED_GBUFFER,
    DRAWLIST
  }
}
