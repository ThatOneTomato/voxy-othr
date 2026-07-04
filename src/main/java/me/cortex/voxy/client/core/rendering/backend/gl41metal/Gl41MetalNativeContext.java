package me.cortex.voxy.client.core.rendering.backend.gl41metal;

final class Gl41MetalNativeContext implements AutoCloseable {
  private final long handle;
  private final int slotCount;
  private int width;
  private int height;
  private final int textureTarget;
  private final String deviceName;
  private boolean closed;

  Gl41MetalNativeContext(int slotCount, int width, int height, int completionDelayMs) {
    this.handle = Gl41MetalNative.createContext(slotCount, width, height, completionDelayMs);
    this.slotCount = slotCount;
    this.width = width;
    this.height = height;
    this.textureTarget = Gl41MetalNative.getTextureTarget(this.handle);
    this.deviceName = Gl41MetalNative.getDeviceName(this.handle);
  }

  long handle() {
    return this.handle;
  }

  int slotCount() {
    return this.slotCount;
  }

  int width() {
    return this.width;
  }

  int height() {
    return this.height;
  }

  // Resizes the screen-sized gbuffer/depth textures in place; the handle and all terrain resources
  // are preserved.
  void resize(int width, int height) {
    if (this.closed) {
      throw new IllegalStateException("Cannot resize a closed GL41Metal native context");
    }
    Gl41MetalNative.resizeContext(this.handle, width, height);
    this.width = width;
    this.height = height;
  }

  int textureTarget() {
    return this.textureTarget;
  }

  String deviceName() {
    return this.deviceName;
  }

  @Override
  public void close() {
    if (!this.closed) {
      this.closed = true;
      Gl41MetalNative.destroyContext(this.handle);
    }
  }
}
