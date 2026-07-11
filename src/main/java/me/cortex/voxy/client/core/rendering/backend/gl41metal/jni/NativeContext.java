package me.cortex.voxy.client.core.rendering.backend.gl41metal.jni;

public final class NativeContext implements AutoCloseable {
  private final long handle;
  private final int slotCount;
  private final String deviceName;
  private boolean closed;

  public NativeContext(int slotCount) {
    this.handle = NativeBindings.createContext(slotCount);
    this.slotCount = slotCount;
    this.deviceName = NativeBindings.getDeviceName(this.handle);
  }

  public long handle() {
    return this.handle;
  }

  public int slotCount() {
    return this.slotCount;
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
