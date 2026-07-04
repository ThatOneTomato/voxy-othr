package me.cortex.voxy.client.core.rendering.backend.gl41metal;

import java.util.List;
import java.util.Locale;
import me.cortex.voxy.common.Logger;

/**
 * Per-phase CPU timing profiler for the gl41metal backend. Accumulates nanosecond-resolution
 * wall-clock times for each frame phase over a rolling window and exposes averages, maxes, and
 * periodic log summaries.
 *
 * <p>Enable periodic log output with {@code -Dvoxy.gl41metal.profileLogIntervalMs=5000} (default
 * 5000 ms, 0 to disable).
 */
public final class FrameProfiler {
  private static final int WINDOW_SIZE =
      Math.max(
          10,
          Integer.parseInt(
              System.getProperty("voxy.gl41metal.profileWindowSize", "120").trim()));
  private static final long LOG_INTERVAL_MS =
      Long.parseLong(
          System.getProperty("voxy.gl41metal.profileLogIntervalMs", "5000").trim());

  private long tickAccum;
  private long metalSubmitAccum;
  private long slotWaitAccum;
  private long boundRenderAccum;
  private long bridgeOpaqueAccum;
  private long bridgeTranslucentAccum;

  private long maxTickNanos;
  private long maxMetalSubmitNanos;
  private long maxSlotWaitNanos;
  private long maxBoundRenderNanos;
  private long maxBridgeOpaqueNanos;
  private long maxBridgeTranslucentNanos;

  private double metalGpuMsAccum;
  private double maxMetalGpuMs;

  private int frames;
  private long windowStartNanos;
  private long lastLogTimeMs;

  private volatile ProfileSnapshot snapshot = ProfileSnapshot.EMPTY;

  FrameProfiler() {
    this.windowStartNanos = System.nanoTime();
    this.lastLogTimeMs = System.currentTimeMillis();
  }

  long begin() {
    return System.nanoTime();
  }

  void recordTick(long startNanos) {
    long elapsed = System.nanoTime() - startNanos;
    this.tickAccum += elapsed;
    this.maxTickNanos = Math.max(this.maxTickNanos, elapsed);
  }

  void recordMetalSubmit(long startNanos) {
    long elapsed = System.nanoTime() - startNanos;
    this.metalSubmitAccum += elapsed;
    this.maxMetalSubmitNanos = Math.max(this.maxMetalSubmitNanos, elapsed);
  }

  void recordSlotWait(long startNanos) {
    long elapsed = System.nanoTime() - startNanos;
    this.slotWaitAccum += elapsed;
    this.maxSlotWaitNanos = Math.max(this.maxSlotWaitNanos, elapsed);
  }

  void recordBoundRender(long startNanos) {
    long elapsed = System.nanoTime() - startNanos;
    this.boundRenderAccum += elapsed;
    this.maxBoundRenderNanos = Math.max(this.maxBoundRenderNanos, elapsed);
  }

  void recordBridgeOpaque(long startNanos) {
    long elapsed = System.nanoTime() - startNanos;
    this.bridgeOpaqueAccum += elapsed;
    this.maxBridgeOpaqueNanos = Math.max(this.maxBridgeOpaqueNanos, elapsed);
  }

  void recordBridgeTranslucent(long startNanos) {
    long elapsed = System.nanoTime() - startNanos;
    this.bridgeTranslucentAccum += elapsed;
    this.maxBridgeTranslucentNanos = Math.max(this.maxBridgeTranslucentNanos, elapsed);
  }

  void recordMetalGpuMs(double ms) {
    this.metalGpuMsAccum += ms;
    this.maxMetalGpuMs = Math.max(this.maxMetalGpuMs, ms);
  }

  void endFrame() {
    this.frames++;
    if (this.frames >= WINDOW_SIZE) {
      this.rotateWindow();
    }
  }

  ProfileSnapshot snapshot() {
    return this.snapshot;
  }

  void addDebugInfo(List<String> debug) {
    ProfileSnapshot s = this.snapshot;
    if (s == ProfileSnapshot.EMPTY) {
      debug.add("Voxy GL41Metal perf: collecting...");
      return;
    }
    debug.add(
        String.format(
            Locale.ROOT,
            "Voxy perf avg(ms): tick=%.2f submit=%.2f wait=%.2f bound=%.2f"
                + " opaqueGL=%.2f transGL=%.2f metalGPU=%.2f",
            s.avgTickMs,
            s.avgMetalSubmitMs,
            s.avgSlotWaitMs,
            s.avgBoundRenderMs,
            s.avgBridgeOpaqueMs,
            s.avgBridgeTranslucentMs,
            s.avgMetalGpuMs));
    debug.add(
        String.format(
            Locale.ROOT,
            "Voxy perf max(ms): tick=%.2f submit=%.2f wait=%.2f bound=%.2f"
                + " opaqueGL=%.2f transGL=%.2f metalGPU=%.2f | voxyTotal=%.2f @%.0ffps",
            s.maxTickMs,
            s.maxMetalSubmitMs,
            s.maxSlotWaitMs,
            s.maxBoundRenderMs,
            s.maxBridgeOpaqueMs,
            s.maxBridgeTranslucentMs,
            s.maxMetalGpuMs,
            s.avgVoxyTotalMs(),
            s.avgFps()));
  }

  private void rotateWindow() {
    long windowNanos = System.nanoTime() - this.windowStartNanos;
    double windowMs = windowNanos / 1_000_000.0;
    int n = this.frames;
    ProfileSnapshot s =
        new ProfileSnapshot(
            toAvgMs(this.tickAccum, n),
            toAvgMs(this.metalSubmitAccum, n),
            toAvgMs(this.slotWaitAccum, n),
            toAvgMs(this.boundRenderAccum, n),
            toAvgMs(this.bridgeOpaqueAccum, n),
            toAvgMs(this.bridgeTranslucentAccum, n),
            this.metalGpuMsAccum / n,
            toMs(this.maxTickNanos),
            toMs(this.maxMetalSubmitNanos),
            toMs(this.maxSlotWaitNanos),
            toMs(this.maxBoundRenderNanos),
            toMs(this.maxBridgeOpaqueNanos),
            toMs(this.maxBridgeTranslucentNanos),
            this.maxMetalGpuMs,
            n,
            windowMs);
    this.snapshot = s;

    if (LOG_INTERVAL_MS > 0) {
      long now = System.currentTimeMillis();
      if (now - this.lastLogTimeMs >= LOG_INTERVAL_MS) {
        this.lastLogTimeMs = now;
        Logger.info(
            String.format(
                Locale.ROOT,
                "GL41Metal perf [%d frames / %.0fms]: "
                    + "avg tick=%.2f submit=%.2f wait=%.2f bound=%.2f opaqueGL=%.2f"
                    + " transGL=%.2f metalGPU=%.2f | voxyTotal=%.2f ms"
                    + " | max wait=%.2f opaqueGL=%.2f metalGPU=%.2f",
                s.frames,
                s.windowMs,
                s.avgTickMs,
                s.avgMetalSubmitMs,
                s.avgSlotWaitMs,
                s.avgBoundRenderMs,
                s.avgBridgeOpaqueMs,
                s.avgBridgeTranslucentMs,
                s.avgMetalGpuMs,
                s.avgVoxyTotalMs(),
                s.maxSlotWaitMs,
                s.maxBridgeOpaqueMs,
                s.maxMetalGpuMs));
      }
    }

    this.tickAccum = 0;
    this.metalSubmitAccum = 0;
    this.slotWaitAccum = 0;
    this.boundRenderAccum = 0;
    this.bridgeOpaqueAccum = 0;
    this.bridgeTranslucentAccum = 0;
    this.metalGpuMsAccum = 0;
    this.maxTickNanos = 0;
    this.maxMetalSubmitNanos = 0;
    this.maxSlotWaitNanos = 0;
    this.maxBoundRenderNanos = 0;
    this.maxBridgeOpaqueNanos = 0;
    this.maxBridgeTranslucentNanos = 0;
    this.maxMetalGpuMs = 0;
    this.frames = 0;
    this.windowStartNanos = System.nanoTime();
  }

  private static double toMs(long nanos) {
    return nanos / 1_000_000.0;
  }

  private static double toAvgMs(long accumNanos, int count) {
    return count > 0 ? (accumNanos / 1_000_000.0) / count : 0.0;
  }

  record ProfileSnapshot(
      double avgTickMs,
      double avgMetalSubmitMs,
      double avgSlotWaitMs,
      double avgBoundRenderMs,
      double avgBridgeOpaqueMs,
      double avgBridgeTranslucentMs,
      double avgMetalGpuMs,
      double maxTickMs,
      double maxMetalSubmitMs,
      double maxSlotWaitMs,
      double maxBoundRenderMs,
      double maxBridgeOpaqueMs,
      double maxBridgeTranslucentMs,
      double maxMetalGpuMs,
      int frames,
      double windowMs) {
    static final ProfileSnapshot EMPTY =
        new ProfileSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    double avgVoxyTotalMs() {
      return avgTickMs
          + avgMetalSubmitMs
          + avgSlotWaitMs
          + avgBoundRenderMs
          + avgBridgeOpaqueMs
          + avgBridgeTranslucentMs;
    }

    double avgFps() {
      return windowMs > 0 ? frames / (windowMs / 1000.0) : 0;
    }
  }
}
