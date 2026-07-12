package me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge;

import static org.lwjgl.opengl.GL32C.GL_ALREADY_SIGNALED;
import static org.lwjgl.opengl.GL32C.GL_CONDITION_SATISFIED;
import static org.lwjgl.opengl.GL32C.GL_SYNC_FLUSH_COMMANDS_BIT;
import static org.lwjgl.opengl.GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE;
import static org.lwjgl.opengl.GL32C.glClientWaitSync;
import static org.lwjgl.opengl.GL32C.glDeleteSync;
import static org.lwjgl.opengl.GL32C.glFenceSync;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.jni.NativeBindings;
import me.cortex.voxy.common.Logger;

public final class SlotScheduler {
  private final int waitTimeoutMs;
  private final Map<Integer, Long> retiringFences = new HashMap<>();
  private boolean loggedNoFreeSlot;
  private boolean loggedCurrentTimeout;

  public SlotScheduler(int waitTimeoutMs) {
    this.waitTimeoutMs = waitTimeoutMs;
  }

  public int acquireWriteSlot(FrameSlotContext slots) {
    this.retireCompletedSlots(slots);
    return NativeBindings.acquireFreeSlot(slots.nativeHandle());
  }

  public void recordNoFreeSlot() {
    if (!this.loggedNoFreeSlot) {
      this.loggedNoFreeSlot = true;
      Logger.warn("Voxy GL41Metal had no free traversal slot for Metal submission");
    }
  }

  public int selectSlotForSampling(FrameSlotContext slots, int currentSlot) {
    this.retireCompletedSlots(slots);
    int selected =
        NativeBindings.waitCurrent(slots.nativeHandle(), currentSlot, this.waitTimeoutMs);
    if (currentSlot < 0 || selected != currentSlot) {
      if (currentSlot >= 0) {
        NativeBindings.discardCurrentSlot(slots.nativeHandle(), currentSlot);
      }
      if (currentSlot >= 0 && !this.loggedCurrentTimeout) {
        this.loggedCurrentTimeout = true;
        Logger.warn("Voxy GL41Metal skipped current-frame composite after debug bounded wait");
      }
    }
    return selected;
  }

  public void queueSampledSlotRetirement(FrameSlotContext slots, int slot) {
    long fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    if (fence == 0) {
      NativeBindings.releaseSampledSlot(slots.nativeHandle(), slot);
      return;
    }
    Long previous = this.retiringFences.put(slot, fence);
    if (previous != null) {
      glDeleteSync(previous);
      NativeBindings.releaseSampledSlot(slots.nativeHandle(), slot);
    }
  }

  public void closeRetiringSlots(FrameSlotContext slots) {
    for (Map.Entry<Integer, Long> entry : this.retiringFences.entrySet()) {
      glClientWaitSync(entry.getValue(), GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L);
      glDeleteSync(entry.getValue());
      NativeBindings.releaseSampledSlot(slots.nativeHandle(), entry.getKey());
    }
    this.retiringFences.clear();
  }

  public void addDebugInfo(List<String> debug) {
    debug.add("Voxy GL41Metal slots: " + this.summary());
  }

  public String summary() {
    return "retiring="
        + this.retiringFences.size()
        + ", waitTimeoutMs="
        + this.waitTimeoutMs
        + ", waitMode="
        + (this.waitTimeoutMs == 0 ? "blocking-current" : "debug-bounded-current");
  }

  private void retireCompletedSlots(FrameSlotContext slots) {
    Iterator<Map.Entry<Integer, Long>> iterator = this.retiringFences.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<Integer, Long> entry = iterator.next();
      int result = glClientWaitSync(entry.getValue(), 0, 0L);
      if (result == GL_ALREADY_SIGNALED || result == GL_CONDITION_SATISFIED) {
        glDeleteSync(entry.getValue());
        NativeBindings.releaseSampledSlot(slots.nativeHandle(), entry.getKey());
        iterator.remove();
      }
    }
  }
}
