package me.cortex.voxy.client.core.model;

import me.cortex.voxy.common.util.MemoryBuffer;
import org.jetbrains.annotations.Nullable;

public record BakedModelPayload(
    int modelId,
    MemoryBuffer model,
    MemoryBuffer texture,
    int biomeUploadIndex,
    @Nullable MemoryBuffer biomeUpload,
    int renderLayer,
    int fallbackReason,
    String sourceDescription) {
  public static final int RENDER_LAYER_OPAQUE = 0;
  public static final int RENDER_LAYER_CUTOUT = 1;
  public static final int RENDER_LAYER_TRANSLUCENT = 2;
  public static final int RENDER_LAYER_FLUID = 3;

  public static final int FALLBACK_NONE = 0;
  public static final int FALLBACK_INVISIBLE = 1;
  public static final int FALLBACK_FLUID_ONLY = 2;
  public static final int FALLBACK_NO_QUADS = 3;
  public static final int FALLBACK_GENERAL_QUADS = 4;
  public static final int FALLBACK_MISSING_SPRITE = 5;

  public boolean hasFallback() {
    return this.fallbackReason != FALLBACK_NONE;
  }

  public static String renderLayerName(int renderLayer) {
    return switch (renderLayer) {
      case RENDER_LAYER_OPAQUE -> "opaque";
      case RENDER_LAYER_CUTOUT -> "cutout";
      case RENDER_LAYER_TRANSLUCENT -> "translucent";
      case RENDER_LAYER_FLUID -> "fluid";
      default -> "unknown";
    };
  }

  public static String fallbackReasonName(int reason) {
    return switch (reason) {
      case FALLBACK_NONE -> "none";
      case FALLBACK_INVISIBLE -> "invisible";
      case FALLBACK_FLUID_ONLY -> "fluid_only";
      case FALLBACK_NO_QUADS -> "no_quads";
      case FALLBACK_GENERAL_QUADS -> "general_quads";
      case FALLBACK_MISSING_SPRITE -> "missing_sprite";
      default -> "unknown";
    };
  }
}
