package me.cortex.voxy.client.core.rendering.backend.gl41metal.jni;

public final class NativeContext implements AutoCloseable {
  private final long handle;
  private final int slotCount;
  private int width;
  private int height;
  private final int textureTarget;
  private final String deviceName;
  private boolean closed;

  public NativeContext(int slotCount, int width, int height) {
    this.handle = NativeBindings.createContext(slotCount, width, height);
    this.slotCount = slotCount;
    this.width = width;
    this.height = height;
    this.textureTarget = NativeBindings.getTextureTarget(this.handle);
    this.deviceName = NativeBindings.getDeviceName(this.handle);
  }

  public long handle() {
    return this.handle;
  }

  public int slotCount() {
    return this.slotCount;
  }

  public int width() {
    return this.width;
  }

  public int height() {
    return this.height;
  }

  // Resizes the screen-sized gbuffer/depth textures in place; the handle and all terrain resources
  // are preserved.
  public void resize(int width, int height) {
    if (this.closed) {
      throw new IllegalStateException("Cannot resize a closed GL41Metal native context");
    }
    NativeBindings.resizeContext(this.handle, width, height);
    this.width = width;
    this.height = height;
  }

  public int textureTarget() {
    return this.textureTarget;
  }

  public String deviceName() {
    return this.deviceName;
  }

  @Override
  public void close() {
    if (!this.closed) {
      this.closed = true;
      NativeBindings.destroyContext(this.handle);
    }
  }
}
