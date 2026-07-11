package me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge;

import me.cortex.voxy.client.core.rendering.backend.gl41metal.jni.NativeContext;

/** Fixed native traversal/worklist slots shared between Metal production and OpenGL consumption. */
public final class FrameSlotContext implements AutoCloseable {
  private final NativeContext context;

  private FrameSlotContext(NativeContext context) {
    this.context = context;
  }

  public static FrameSlotContext create(int slotCount) {
    return new FrameSlotContext(new NativeContext(slotCount));
  }

  public long nativeHandle() {
    return this.context.handle();
  }

  int slotCount() {
    return this.context.slotCount();
  }

  public String description() {
    return "device=" + this.context.deviceName() + ", slots=" + this.slotCount();
  }

  @Override
  public void close() {
    this.context.close();
  }
}
