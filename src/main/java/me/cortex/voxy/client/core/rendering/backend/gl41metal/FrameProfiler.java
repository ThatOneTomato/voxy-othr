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
          Integer.parseInt(System.getProperty("voxy.gl41metal.profileWindowSize", "120").trim()));
  private static final long LOG_INTERVAL_MS =
      Long.parseLong(System.getProperty("voxy.gl41metal.profileLogIntervalMs", "5000").trim());

  private long tickAccum;
  private long metalSubmitAccum;
  private long slotWaitAccum;
  private long boundRenderAccum;
  private long bridgeOpaqueAccum;
  private long bridgeTranslucentAccum;
  private long drawlistBuildAccum;
  private long drawlistUploadAccum;
  private long drawlistOpaqueRasterAccum;
  private long drawlistRangeMeasureAccum;
  private double drawlistGpuMsAccum;
  private final DrawlistPhaseStats opaqueDrawlistStats = new DrawlistPhaseStats();
  private final DrawlistPhaseStats translucentDrawlistStats = new DrawlistPhaseStats();

  private long maxTickNanos;
  private long maxMetalSubmitNanos;
  private long maxSlotWaitNanos;
  private long maxBoundRenderNanos;
  private long maxBridgeOpaqueNanos;
  private long maxBridgeTranslucentNanos;
  private long maxDrawlistBuildNanos;
  private long maxDrawlistUploadNanos;
  private long maxDrawlistOpaqueRasterNanos;
  private long maxDrawlistRangeMeasureNanos;
  private double maxDrawlistGpuMs;

  private double metalGpuMsAccum;
  private double maxMetalGpuMs;

  private double gpuTraversalAccum;
  private double gpuOpaqueRasterAccum;
  private double gpuSsaoAccum;
  private double gpuTranslucentAccum;

  private long drawlistInstanceAccum;
  private long drawlistOverflowAccum;
  private long maxDrawlistInstances;
  private long maxDrawlistOverflows;
  private long drawlistRawRangeAccum;
  private long drawlistMergedRangeAccum;
  private long drawlistRangeQuadAccum;
  private long drawlistVisibleWorkItemAccum;
  private long maxDrawlistRawRanges;
  private long maxDrawlistMergedRanges;
  private long maxDrawlistRangeQuads;

  private long currentDrawlistBuildNanos;
  private long currentDrawlistUploadNanos;
  private long currentDrawlistRasterNanos;
  private long currentDrawlistRangeMeasureNanos;
  private double currentDrawlistGpuMs;
  private long currentDrawlistInstances;
  private long currentDrawlistOverflows;
  private long currentDrawlistRawRanges;
  private long currentDrawlistMergedRanges;
  private long currentDrawlistRangeQuads;

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

  void recordDrawlistOpaqueBuild(long startNanos, long instances, long overflows) {
    this.recordDrawlistBuild(startNanos, instances, overflows, this.opaqueDrawlistStats);
  }

  void recordDrawlistTranslucentBuild(long startNanos, long instances, long overflows) {
    this.recordDrawlistBuild(startNanos, instances, overflows, this.translucentDrawlistStats);
  }

  private void recordDrawlistBuild(
      long startNanos, long instances, long overflows, DrawlistPhaseStats phaseStats) {
    long elapsed = System.nanoTime() - startNanos;
    this.drawlistBuildAccum += elapsed;
    long clampedInstances = Math.max(0, instances);
    long clampedOverflows = Math.max(0, overflows);
    this.currentDrawlistBuildNanos += elapsed;
    this.drawlistInstanceAccum += clampedInstances;
    this.drawlistOverflowAccum += clampedOverflows;
    this.currentDrawlistInstances += clampedInstances;
    this.currentDrawlistOverflows += clampedOverflows;
    phaseStats.recordBuild(elapsed, instances, overflows);
  }

  void recordDrawlistUpload(long startNanos) {
    long elapsed = System.nanoTime() - startNanos;
    this.drawlistUploadAccum += elapsed;
    this.currentDrawlistUploadNanos += elapsed;
  }

  void recordDrawlistOpaqueRaster(long startNanos) {
    this.recordDrawlistRaster(startNanos, this.opaqueDrawlistStats);
  }

  void recordDrawlistTranslucentRaster(long startNanos) {
    this.recordDrawlistRaster(startNanos, this.translucentDrawlistStats);
  }

  private void recordDrawlistRaster(long startNanos, DrawlistPhaseStats phaseStats) {
    long elapsed = System.nanoTime() - startNanos;
    this.drawlistOpaqueRasterAccum += elapsed;
    this.currentDrawlistRasterNanos += elapsed;
    phaseStats.recordRaster(elapsed);
  }

  void recordDrawlistOpaqueGpuMs(double ms) {
    this.recordDrawlistGpuMs(ms, this.opaqueDrawlistStats);
  }

  void recordDrawlistTranslucentGpuMs(double ms) {
    this.recordDrawlistGpuMs(ms, this.translucentDrawlistStats);
  }

  private void recordDrawlistGpuMs(double ms, DrawlistPhaseStats phaseStats) {
    this.drawlistGpuMsAccum += ms;
    this.currentDrawlistGpuMs += ms;
    phaseStats.recordGpu(ms);
  }

  void recordDrawlistRangeMeasure(long startNanos, long[] stats) {
    long elapsed = System.nanoTime() - startNanos;
    this.drawlistRangeMeasureAccum += elapsed;
    this.currentDrawlistRangeMeasureNanos += elapsed;
    if (stats == null || stats.length < 8) {
      return;
    }
    this.recordDrawlistRangeStats(stats, this.opaqueDrawlistStats);
  }

  void recordDrawlistOpaqueRangeStats(long[] stats) {
    this.recordDrawlistRangeStats(stats, this.opaqueDrawlistStats);
  }

  void recordDrawlistTranslucentRangeStats(long[] stats) {
    this.recordDrawlistRangeStats(stats, this.translucentDrawlistStats);
  }

  private void recordDrawlistRangeStats(long[] stats, DrawlistPhaseStats phaseStats) {
    if (stats == null || stats.length < 8) {
      return;
    }
    long rawRanges = Math.max(0, stats[1]);
    long mergedRanges = Math.max(0, stats[2]);
    long rangeQuads = Math.max(0, stats[3]);
    long visibleWorkItems = Math.max(0, stats[7]);
    this.drawlistRawRangeAccum += rawRanges;
    this.drawlistMergedRangeAccum += mergedRanges;
    this.drawlistRangeQuadAccum += rangeQuads;
    this.drawlistVisibleWorkItemAccum += visibleWorkItems;
    this.currentDrawlistRawRanges += rawRanges;
    this.currentDrawlistMergedRanges += mergedRanges;
    this.currentDrawlistRangeQuads += rangeQuads;
    phaseStats.recordRangeStats(rawRanges, mergedRanges, rangeQuads, visibleWorkItems);
  }

  void recordMetalGpuMs(double ms) {
    this.metalGpuMsAccum += ms;
    this.maxMetalGpuMs = Math.max(this.maxMetalGpuMs, ms);
  }

  void recordPerPassGpuMs(double[] times) {
    if (times == null || times.length < 4) return;
    this.gpuTraversalAccum += times[0];
    this.gpuOpaqueRasterAccum += times[1];
    this.gpuSsaoAccum += times[2];
    this.gpuTranslucentAccum += times[3];
  }

  void endFrame() {
    this.recordDrawlistFrameMaxes();
    this.frames++;
    if (this.frames >= WINDOW_SIZE) {
      this.rotateWindow();
    } else {
      this.resetCurrentDrawlistFrame();
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
                + " opaqueGL=%.2f transGL=%.2f drawBuild=%.2f drawUpload=%.2f"
                + " drawRaster=%.2f drawRange=%.2f drawGpu=%.2f metalGPU=%.2f"
                + " (trav=%.2f opaque=%.2f ssao=%.2f trans=%.2f)",
            s.avgTickMs,
            s.avgMetalSubmitMs,
            s.avgSlotWaitMs,
            s.avgBoundRenderMs,
            s.avgBridgeOpaqueMs,
            s.avgBridgeTranslucentMs,
            s.avgDrawlistBuildMs,
            s.avgDrawlistUploadMs,
            s.avgDrawlistOpaqueRasterMs,
            s.avgDrawlistRangeMeasureMs,
            s.avgDrawlistGpuMs,
            s.avgMetalGpuMs,
            s.avgGpuTraversalMs,
            s.avgGpuOpaqueRasterMs,
            s.avgGpuSsaoMs,
            s.avgGpuTranslucentMs));
    debug.add(
        String.format(
            Locale.ROOT,
            "Voxy perf max(ms): tick=%.2f submit=%.2f wait=%.2f bound=%.2f"
                + " opaqueGL=%.2f transGL=%.2f drawBuild=%.2f drawUpload=%.2f"
                + " drawRaster=%.2f drawRange=%.2f drawGpu=%.2f metalGPU=%.2f"
                + " | drawInst avg/max=%.0f/%d overflow avg/max=%.0f/%d"
                + " | drawRanges raw/merged avg=%.0f/%.0f max=%d/%d"
                + " q/r=%.1f cmdKB=%.1f"
                + " | frameWall=%.2f nonVoxyWall=%.2f voxyTotal=%.2f @%.0ffps",
            s.maxTickMs,
            s.maxMetalSubmitMs,
            s.maxSlotWaitMs,
            s.maxBoundRenderMs,
            s.maxBridgeOpaqueMs,
            s.maxBridgeTranslucentMs,
            s.maxDrawlistBuildMs,
            s.maxDrawlistUploadMs,
            s.maxDrawlistOpaqueRasterMs,
            s.maxDrawlistRangeMeasureMs,
            s.maxDrawlistGpuMs,
            s.maxMetalGpuMs,
            s.avgDrawlistInstances,
            s.maxDrawlistInstances,
            s.avgDrawlistOverflows,
            s.maxDrawlistOverflows,
            s.avgDrawlistRawRanges,
            s.avgDrawlistMergedRanges,
            s.maxDrawlistRawRanges,
            s.maxDrawlistMergedRanges,
            s.avgQuadsPerMergedRange(),
            s.avgRangeCommandBytes() / 1024.0,
            s.avgFrameWallMs(),
            s.avgNonVoxyWallMs(),
            s.avgVoxyTotalMs(),
            s.avgFps()));
    debug.add(
        String.format(
            Locale.ROOT,
            "Voxy drawlist split avg(ms) o/t: build=%.2f/%.2f raster=%.2f/%.2f"
                + " gpu=%.2f/%.2f quads=%.0f/%.0f ranges=%.0f/%.0f q/r=%.1f/%.1f",
            s.opaqueDrawlist.avgBuildMs(),
            s.translucentDrawlist.avgBuildMs(),
            s.opaqueDrawlist.avgRasterMs(),
            s.translucentDrawlist.avgRasterMs(),
            s.opaqueDrawlist.avgGpuMs(),
            s.translucentDrawlist.avgGpuMs(),
            s.opaqueDrawlist.avgQuads(),
            s.translucentDrawlist.avgQuads(),
            s.opaqueDrawlist.avgMergedRanges(),
            s.translucentDrawlist.avgMergedRanges(),
            s.opaqueDrawlist.avgQuadsPerMergedRange(),
            s.translucentDrawlist.avgQuadsPerMergedRange()));
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
            toAvgMs(this.drawlistBuildAccum, n),
            toAvgMs(this.drawlistUploadAccum, n),
            toAvgMs(this.drawlistOpaqueRasterAccum, n),
            toAvgMs(this.drawlistRangeMeasureAccum, n),
            this.drawlistGpuMsAccum / n,
            this.metalGpuMsAccum / n,
            toMs(this.maxTickNanos),
            toMs(this.maxMetalSubmitNanos),
            toMs(this.maxSlotWaitNanos),
            toMs(this.maxBoundRenderNanos),
            toMs(this.maxBridgeOpaqueNanos),
            toMs(this.maxBridgeTranslucentNanos),
            toMs(this.maxDrawlistBuildNanos),
            toMs(this.maxDrawlistUploadNanos),
            toMs(this.maxDrawlistOpaqueRasterNanos),
            toMs(this.maxDrawlistRangeMeasureNanos),
            this.maxDrawlistGpuMs,
            this.maxMetalGpuMs,
            n,
            windowMs,
            this.gpuTraversalAccum / n,
            this.gpuOpaqueRasterAccum / n,
            this.gpuSsaoAccum / n,
            this.gpuTranslucentAccum / n,
            this.drawlistInstanceAccum / (double) n,
            this.drawlistOverflowAccum / (double) n,
            this.maxDrawlistInstances,
            this.maxDrawlistOverflows,
            this.drawlistRawRangeAccum / (double) n,
            this.drawlistMergedRangeAccum / (double) n,
            this.drawlistRangeQuadAccum / (double) n,
            this.drawlistVisibleWorkItemAccum / (double) n,
            this.maxDrawlistRawRanges,
            this.maxDrawlistMergedRanges,
            this.maxDrawlistRangeQuads,
            this.opaqueDrawlistStats.snapshot(n),
            this.translucentDrawlistStats.snapshot(n));
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
                    + " transGL=%.2f drawBuild=%.2f drawUpload=%.2f drawRaster=%.2f"
                    + " drawRange=%.2f drawGpu=%.2f metalGPU=%.2f"
                    + " (trav=%.2f opaque=%.2f ssao=%.2f trans=%.2f)"
                    + " | frameWall=%.2f nonVoxyWall=%.2f voxyTotal=%.2f ms"
                    + " | max wait=%.2f opaqueGL=%.2f drawBuild=%.2f drawUpload=%.2f"
                    + " drawRaster=%.2f drawRange=%.2f drawGpu=%.2f metalGPU=%.2f"
                    + " | drawInst avg/max=%.0f/%d overflow avg/max=%.0f/%d"
                    + " | drawRanges raw/merged avg=%.0f/%.0f max=%d/%d"
                    + " q/r=%.1f cmdKB=%.1f"
                    + " | split o/t build=%.2f/%.2f raster=%.2f/%.2f gpu=%.2f/%.2f"
                    + " quads=%.0f/%.0f ranges=%.0f/%.0f q/r=%.1f/%.1f",
                s.frames,
                s.windowMs,
                s.avgTickMs,
                s.avgMetalSubmitMs,
                s.avgSlotWaitMs,
                s.avgBoundRenderMs,
                s.avgBridgeOpaqueMs,
                s.avgBridgeTranslucentMs,
                s.avgDrawlistBuildMs,
                s.avgDrawlistUploadMs,
                s.avgDrawlistOpaqueRasterMs,
                s.avgDrawlistRangeMeasureMs,
                s.avgDrawlistGpuMs,
                s.avgMetalGpuMs,
                s.avgGpuTraversalMs,
                s.avgGpuOpaqueRasterMs,
                s.avgGpuSsaoMs,
                s.avgGpuTranslucentMs,
                s.avgFrameWallMs(),
                s.avgNonVoxyWallMs(),
                s.avgVoxyTotalMs(),
                s.maxSlotWaitMs,
                s.maxBridgeOpaqueMs,
                s.maxDrawlistBuildMs,
                s.maxDrawlistUploadMs,
                s.maxDrawlistOpaqueRasterMs,
                s.maxDrawlistRangeMeasureMs,
                s.maxDrawlistGpuMs,
                s.maxMetalGpuMs,
                s.avgDrawlistInstances,
                s.maxDrawlistInstances,
                s.avgDrawlistOverflows,
                s.maxDrawlistOverflows,
                s.avgDrawlistRawRanges,
                s.avgDrawlistMergedRanges,
                s.maxDrawlistRawRanges,
                s.maxDrawlistMergedRanges,
                s.avgQuadsPerMergedRange(),
                s.avgRangeCommandBytes() / 1024.0,
                s.opaqueDrawlist.avgBuildMs(),
                s.translucentDrawlist.avgBuildMs(),
                s.opaqueDrawlist.avgRasterMs(),
                s.translucentDrawlist.avgRasterMs(),
                s.opaqueDrawlist.avgGpuMs(),
                s.translucentDrawlist.avgGpuMs(),
                s.opaqueDrawlist.avgQuads(),
                s.translucentDrawlist.avgQuads(),
                s.opaqueDrawlist.avgMergedRanges(),
                s.translucentDrawlist.avgMergedRanges(),
                s.opaqueDrawlist.avgQuadsPerMergedRange(),
                s.translucentDrawlist.avgQuadsPerMergedRange()));
      }
    }

    this.tickAccum = 0;
    this.metalSubmitAccum = 0;
    this.slotWaitAccum = 0;
    this.boundRenderAccum = 0;
    this.bridgeOpaqueAccum = 0;
    this.bridgeTranslucentAccum = 0;
    this.drawlistBuildAccum = 0;
    this.drawlistUploadAccum = 0;
    this.drawlistOpaqueRasterAccum = 0;
    this.drawlistRangeMeasureAccum = 0;
    this.drawlistGpuMsAccum = 0;
    this.metalGpuMsAccum = 0;
    this.gpuTraversalAccum = 0;
    this.gpuOpaqueRasterAccum = 0;
    this.gpuSsaoAccum = 0;
    this.gpuTranslucentAccum = 0;
    this.maxTickNanos = 0;
    this.maxMetalSubmitNanos = 0;
    this.maxSlotWaitNanos = 0;
    this.maxBoundRenderNanos = 0;
    this.maxBridgeOpaqueNanos = 0;
    this.maxBridgeTranslucentNanos = 0;
    this.maxDrawlistBuildNanos = 0;
    this.maxDrawlistUploadNanos = 0;
    this.maxDrawlistOpaqueRasterNanos = 0;
    this.maxDrawlistRangeMeasureNanos = 0;
    this.maxDrawlistGpuMs = 0;
    this.maxMetalGpuMs = 0;
    this.drawlistInstanceAccum = 0;
    this.drawlistOverflowAccum = 0;
    this.maxDrawlistInstances = 0;
    this.maxDrawlistOverflows = 0;
    this.drawlistRawRangeAccum = 0;
    this.drawlistMergedRangeAccum = 0;
    this.drawlistRangeQuadAccum = 0;
    this.drawlistVisibleWorkItemAccum = 0;
    this.maxDrawlistRawRanges = 0;
    this.maxDrawlistMergedRanges = 0;
    this.maxDrawlistRangeQuads = 0;
    this.opaqueDrawlistStats.reset();
    this.translucentDrawlistStats.reset();
    this.resetCurrentDrawlistFrame();
    this.frames = 0;
    this.windowStartNanos = System.nanoTime();
  }

  private void recordDrawlistFrameMaxes() {
    this.maxDrawlistBuildNanos =
        Math.max(this.maxDrawlistBuildNanos, this.currentDrawlistBuildNanos);
    this.maxDrawlistUploadNanos =
        Math.max(this.maxDrawlistUploadNanos, this.currentDrawlistUploadNanos);
    this.maxDrawlistOpaqueRasterNanos =
        Math.max(this.maxDrawlistOpaqueRasterNanos, this.currentDrawlistRasterNanos);
    this.maxDrawlistRangeMeasureNanos =
        Math.max(this.maxDrawlistRangeMeasureNanos, this.currentDrawlistRangeMeasureNanos);
    this.maxDrawlistGpuMs = Math.max(this.maxDrawlistGpuMs, this.currentDrawlistGpuMs);
    this.maxDrawlistInstances = Math.max(this.maxDrawlistInstances, this.currentDrawlistInstances);
    this.maxDrawlistOverflows = Math.max(this.maxDrawlistOverflows, this.currentDrawlistOverflows);
    this.maxDrawlistRawRanges = Math.max(this.maxDrawlistRawRanges, this.currentDrawlistRawRanges);
    this.maxDrawlistMergedRanges =
        Math.max(this.maxDrawlistMergedRanges, this.currentDrawlistMergedRanges);
    this.maxDrawlistRangeQuads =
        Math.max(this.maxDrawlistRangeQuads, this.currentDrawlistRangeQuads);
  }

  private void resetCurrentDrawlistFrame() {
    this.currentDrawlistBuildNanos = 0;
    this.currentDrawlistUploadNanos = 0;
    this.currentDrawlistRasterNanos = 0;
    this.currentDrawlistRangeMeasureNanos = 0;
    this.currentDrawlistGpuMs = 0;
    this.currentDrawlistInstances = 0;
    this.currentDrawlistOverflows = 0;
    this.currentDrawlistRawRanges = 0;
    this.currentDrawlistMergedRanges = 0;
    this.currentDrawlistRangeQuads = 0;
  }

  private static double toMs(long nanos) {
    return nanos / 1_000_000.0;
  }

  private static double toAvgMs(long accumNanos, int count) {
    return count > 0 ? (accumNanos / 1_000_000.0) / count : 0.0;
  }

  private static final class DrawlistPhaseStats {
    private long buildAccum;
    private long rasterAccum;
    private double gpuMsAccum;
    private long maxBuildNanos;
    private long maxRasterNanos;
    private double maxGpuMs;
    private long quadAccum;
    private long overflowAccum;
    private long maxQuads;
    private long maxOverflows;
    private long rawRangeAccum;
    private long mergedRangeAccum;
    private long rangeQuadAccum;
    private long visibleWorkItemAccum;
    private long maxRawRanges;
    private long maxMergedRanges;
    private long maxRangeQuads;

    void recordBuild(long elapsedNanos, long quads, long overflows) {
      long clampedQuads = Math.max(0, quads);
      long clampedOverflows = Math.max(0, overflows);
      this.buildAccum += elapsedNanos;
      this.maxBuildNanos = Math.max(this.maxBuildNanos, elapsedNanos);
      this.quadAccum += clampedQuads;
      this.overflowAccum += clampedOverflows;
      this.maxQuads = Math.max(this.maxQuads, clampedQuads);
      this.maxOverflows = Math.max(this.maxOverflows, clampedOverflows);
    }

    void recordRaster(long elapsedNanos) {
      this.rasterAccum += elapsedNanos;
      this.maxRasterNanos = Math.max(this.maxRasterNanos, elapsedNanos);
    }

    void recordGpu(double ms) {
      this.gpuMsAccum += ms;
      this.maxGpuMs = Math.max(this.maxGpuMs, ms);
    }

    void recordRangeStats(
        long rawRanges, long mergedRanges, long rangeQuads, long visibleWorkItems) {
      this.rawRangeAccum += rawRanges;
      this.mergedRangeAccum += mergedRanges;
      this.rangeQuadAccum += rangeQuads;
      this.visibleWorkItemAccum += visibleWorkItems;
      this.maxRawRanges = Math.max(this.maxRawRanges, rawRanges);
      this.maxMergedRanges = Math.max(this.maxMergedRanges, mergedRanges);
      this.maxRangeQuads = Math.max(this.maxRangeQuads, rangeQuads);
    }

    DrawlistPhaseSnapshot snapshot(int frames) {
      return new DrawlistPhaseSnapshot(
          toAvgMs(this.buildAccum, frames),
          toAvgMs(this.rasterAccum, frames),
          frames > 0 ? this.gpuMsAccum / frames : 0.0,
          toMs(this.maxBuildNanos),
          toMs(this.maxRasterNanos),
          this.maxGpuMs,
          frames > 0 ? this.quadAccum / (double) frames : 0.0,
          frames > 0 ? this.overflowAccum / (double) frames : 0.0,
          this.maxQuads,
          this.maxOverflows,
          frames > 0 ? this.rawRangeAccum / (double) frames : 0.0,
          frames > 0 ? this.mergedRangeAccum / (double) frames : 0.0,
          frames > 0 ? this.rangeQuadAccum / (double) frames : 0.0,
          frames > 0 ? this.visibleWorkItemAccum / (double) frames : 0.0,
          this.maxRawRanges,
          this.maxMergedRanges,
          this.maxRangeQuads);
    }

    void reset() {
      this.buildAccum = 0;
      this.rasterAccum = 0;
      this.gpuMsAccum = 0;
      this.maxBuildNanos = 0;
      this.maxRasterNanos = 0;
      this.maxGpuMs = 0;
      this.quadAccum = 0;
      this.overflowAccum = 0;
      this.maxQuads = 0;
      this.maxOverflows = 0;
      this.rawRangeAccum = 0;
      this.mergedRangeAccum = 0;
      this.rangeQuadAccum = 0;
      this.visibleWorkItemAccum = 0;
      this.maxRawRanges = 0;
      this.maxMergedRanges = 0;
      this.maxRangeQuads = 0;
    }
  }

  record DrawlistPhaseSnapshot(
      double avgBuildMs,
      double avgRasterMs,
      double avgGpuMs,
      double maxBuildMs,
      double maxRasterMs,
      double maxGpuMs,
      double avgQuads,
      double avgOverflows,
      long maxQuads,
      long maxOverflows,
      double avgRawRanges,
      double avgMergedRanges,
      double avgRangeQuads,
      double avgVisibleWorkItems,
      long maxRawRanges,
      long maxMergedRanges,
      long maxRangeQuads) {
    static final DrawlistPhaseSnapshot EMPTY =
        new DrawlistPhaseSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    double avgQuadsPerMergedRange() {
      return this.avgMergedRanges > 0 ? this.avgRangeQuads / this.avgMergedRanges : 0.0;
    }

    double avgRangeCommandBytes() {
      return this.avgMergedRanges * 16.0;
    }
  }

  record ProfileSnapshot(
      double avgTickMs,
      double avgMetalSubmitMs,
      double avgSlotWaitMs,
      double avgBoundRenderMs,
      double avgBridgeOpaqueMs,
      double avgBridgeTranslucentMs,
      double avgDrawlistBuildMs,
      double avgDrawlistUploadMs,
      double avgDrawlistOpaqueRasterMs,
      double avgDrawlistRangeMeasureMs,
      double avgDrawlistGpuMs,
      double avgMetalGpuMs,
      double maxTickMs,
      double maxMetalSubmitMs,
      double maxSlotWaitMs,
      double maxBoundRenderMs,
      double maxBridgeOpaqueMs,
      double maxBridgeTranslucentMs,
      double maxDrawlistBuildMs,
      double maxDrawlistUploadMs,
      double maxDrawlistOpaqueRasterMs,
      double maxDrawlistRangeMeasureMs,
      double maxDrawlistGpuMs,
      double maxMetalGpuMs,
      int frames,
      double windowMs,
      double avgGpuTraversalMs,
      double avgGpuOpaqueRasterMs,
      double avgGpuSsaoMs,
      double avgGpuTranslucentMs,
      double avgDrawlistInstances,
      double avgDrawlistOverflows,
      long maxDrawlistInstances,
      long maxDrawlistOverflows,
      double avgDrawlistRawRanges,
      double avgDrawlistMergedRanges,
      double avgDrawlistRangeQuads,
      double avgDrawlistVisibleWorkItems,
      long maxDrawlistRawRanges,
      long maxDrawlistMergedRanges,
      long maxDrawlistRangeQuads,
      DrawlistPhaseSnapshot opaqueDrawlist,
      DrawlistPhaseSnapshot translucentDrawlist) {
    static final ProfileSnapshot EMPTY =
        new ProfileSnapshot(
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            DrawlistPhaseSnapshot.EMPTY,
            DrawlistPhaseSnapshot.EMPTY);

    double avgVoxyTotalMs() {
      return avgTickMs
          + avgMetalSubmitMs
          + avgSlotWaitMs
          + avgBoundRenderMs
          + avgBridgeOpaqueMs
          + avgBridgeTranslucentMs
          + avgDrawlistBuildMs
          + avgDrawlistUploadMs
          + avgDrawlistOpaqueRasterMs
          + avgDrawlistRangeMeasureMs;
    }

    double avgFrameWallMs() {
      return frames > 0 ? windowMs / frames : 0.0;
    }

    double avgNonVoxyWallMs() {
      return Math.max(0.0, avgFrameWallMs() - avgVoxyTotalMs());
    }

    double avgFps() {
      return windowMs > 0 ? frames / (windowMs / 1000.0) : 0;
    }

    double avgQuadsPerMergedRange() {
      return avgDrawlistMergedRanges > 0 ? avgDrawlistRangeQuads / avgDrawlistMergedRanges : 0.0;
    }

    double avgRangeCommandBytes() {
      return avgDrawlistMergedRanges * 16.0;
    }
  }
}
