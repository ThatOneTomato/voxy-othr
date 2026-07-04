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

  public static SharedDistantGbuffer create(int slotCount, int width, int height) {
    NativeContext context = new NativeContext(slotCount, width, height);
    return new SharedDistantGbuffer(context, buildSlots(context));
  }

  // Resizes the underlying screen-sized textures in place. The native handle and all terrain
  // resources are preserved, so distant LOD residency survives the resize; only the GL texture
  // names backing the gbuffer slots change, so the slot views are rebuilt.
  public void resize(int width, int height) {
    this.context.resize(width, height);
    this.slots = buildSlots(this.context);
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
        + ", formats=gbuffer0=RGBA32F(uv+tile),gbuffer1=RGBA32F(depth+ids),"
        + "gbuffer2=RGBA32F(packed albedo/light/tint/face/flags/coverage)";
  }

  @Override
  public void close() {
    this.context.close();
  }
}
