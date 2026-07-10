package me.cortex.voxy.client.core.rendering.backend.gl41metal.jni;

public final class NativeContext implements AutoCloseable {
  private final long handle;
  private final int slotCount;
  private int width;
  private int height;
  private boolean sharedTexturesEnabled;
  private final int textureTarget;
  private final String deviceName;
  private boolean closed;

  public NativeContext(int slotCount, int width, int height, boolean sharedTexturesEnabled) {
    this.handle = NativeBindings.createContext(slotCount, width, height, sharedTexturesEnabled);
    this.slotCount = slotCount;
    this.width = width;
    this.height = height;
    this.sharedTexturesEnabled = sharedTexturesEnabled;
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

  public boolean sharedTexturesEnabled() {
    return this.sharedTexturesEnabled;
  }

  public void configureSharedTextures(int width, int height, boolean enabled) {
    if (this.closed) {
      throw new IllegalStateException("Cannot configure a closed GL41Metal native context");
    }
    NativeBindings.configureSharedTextures(this.handle, width, height, enabled);
    this.width = width;
    this.height = height;
    this.sharedTexturesEnabled = enabled;
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
