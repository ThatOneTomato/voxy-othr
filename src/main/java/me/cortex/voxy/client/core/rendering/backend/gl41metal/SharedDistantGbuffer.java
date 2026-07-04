package me.cortex.voxy.client.core.rendering.backend.gl41metal;

final class SharedDistantGbuffer implements AutoCloseable {
  private final Gl41MetalNativeContext context;
  private DistantGbufferSlot[] slots;

  private SharedDistantGbuffer(Gl41MetalNativeContext context, DistantGbufferSlot[] slots) {
    this.context = context;
    this.slots = slots;
  }

  static SharedDistantGbuffer create(int slotCount, int width, int height) {
    Gl41MetalNativeContext context = new Gl41MetalNativeContext(slotCount, width, height);
    return new SharedDistantGbuffer(context, buildSlots(context));
  }

  // Resizes the underlying screen-sized textures in place. The native handle and all terrain
  // resources are preserved, so distant LOD residency survives the resize; only the GL texture
  // names backing the gbuffer slots change, so the slot views are rebuilt.
  void resize(int width, int height) {
    this.context.resize(width, height);
    this.slots = buildSlots(this.context);
  }

  private static DistantGbufferSlot[] buildSlots(Gl41MetalNativeContext context) {
    DistantGbufferSlot[] slots = new DistantGbufferSlot[context.slotCount()];
    for (int i = 0; i < slots.length; i++) {
      slots[i] =
          new DistantGbufferSlot(
              i,
              context.textureTarget(),
              Gl41MetalNative.getGbuffer0Texture(context.handle(), i),
              Gl41MetalNative.getGbuffer1Texture(context.handle(), i),
              Gl41MetalNative.getGbuffer2Texture(context.handle(), i),
              Gl41MetalNative.getTgbuffer0Texture(context.handle(), i),
              Gl41MetalNative.getTgbuffer1Texture(context.handle(), i),
              Gl41MetalNative.getTgbufferAccumTexture(context.handle(), i),
              context.width(),
              context.height());
    }
    return slots;
  }

  long nativeHandle() {
    return this.context.handle();
  }

  int width() {
    return this.context.width();
  }

  int height() {
    return this.context.height();
  }

  int slotCount() {
    return this.context.slotCount();
  }

  DistantGbufferSlot slot(int index) {
    return this.slots[index];
  }

  String description() {
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
