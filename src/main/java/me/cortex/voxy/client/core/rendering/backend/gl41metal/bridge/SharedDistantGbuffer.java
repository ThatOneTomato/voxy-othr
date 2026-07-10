package me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge;

import me.cortex.voxy.client.core.rendering.backend.gl41metal.jni.NativeBindings;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.jni.NativeContext;

public final class SharedDistantGbuffer implements AutoCloseable {
  private final NativeContext context;
  private DistantGbufferSlot[] slots;

  private SharedDistantGbuffer(NativeContext context, DistantGbufferSlot[] slots) {
    this.context = context;
    this.slots = slots;
  }

  public static SharedDistantGbuffer create(
      int slotCount, int width, int height, boolean sharedTexturesEnabled) {
    NativeContext context = new NativeContext(slotCount, width, height, sharedTexturesEnabled);
    return new SharedDistantGbuffer(context, buildSlots(context));
  }

  // Enables, disables, or resizes the optional bridge textures while preserving the native handle
  // and terrain residency. Slot views are rebuilt because GL texture names may change.
  public void configure(int width, int height, boolean sharedTexturesEnabled) {
    this.context.configureSharedTextures(width, height, sharedTexturesEnabled);
    this.slots = buildSlots(this.context);
  }

  public boolean sharedTexturesEnabled() {
    return this.context.sharedTexturesEnabled();
  }

  private static DistantGbufferSlot[] buildSlots(NativeContext context) {
    DistantGbufferSlot[] slots = new DistantGbufferSlot[context.slotCount()];
    for (int i = 0; i < slots.length; i++) {
      slots[i] =
          new DistantGbufferSlot(
              i,
              context.textureTarget(),
              NativeBindings.getGbuffer0Texture(context.handle(), i),
              NativeBindings.getGbuffer1Texture(context.handle(), i),
              NativeBindings.getGbuffer2Texture(context.handle(), i),
              NativeBindings.getTgbuffer0Texture(context.handle(), i),
              NativeBindings.getTgbuffer1Texture(context.handle(), i),
              NativeBindings.getTgbufferAccumTexture(context.handle(), i),
              context.width(),
              context.height());
    }
    return slots;
  }

  public long nativeHandle() {
    return this.context.handle();
  }

  public int width() {
    return this.context.width();
  }

  public int height() {
    return this.context.height();
  }

  int slotCount() {
    return this.context.slotCount();
  }

  public DistantGbufferSlot slot(int index) {
    return this.slots[index];
  }

  public String description() {
    return "device="
        + this.context.deviceName()
        + ", target=0x"
        + Integer.toHexString(this.context.textureTarget())
        + ", slots="
        + this.slotCount()
        + ", size="
        + this.width()
        + "x"
        + this.height()
        + ", sharedTextures="
        + this.sharedTexturesEnabled()
        + (this.sharedTexturesEnabled()
            ? ", formats=gbuffer0=RGBA32F(uv+tile),gbuffer1=RGBA32F(depth+ids),"
                + "gbuffer2=RGBA32F(packed albedo/light/tint/face/flags/coverage)"
            : "");
  }

  @Override
  public void close() {
    this.context.close();
  }
}
