package me.cortex.voxy.client.core.rendering.backend.gl41metal;

import java.util.List;
import me.cortex.voxy.client.core.rendering.backend.BackendContext;
import me.cortex.voxy.client.core.rendering.backend.RenderBackendId;
import me.cortex.voxy.client.core.rendering.backend.RenderFrame;
import me.cortex.voxy.client.core.rendering.backend.RenderFrameContext;
import me.cortex.voxy.client.core.rendering.backend.RenderFrameMatrices;
import me.cortex.voxy.client.core.rendering.backend.RenderStage;
import me.cortex.voxy.client.core.rendering.backend.RenderStageContext;
import me.cortex.voxy.client.core.rendering.backend.ShaderPatchBridgePayload;
import me.cortex.voxy.client.core.rendering.backend.VoxyRenderBackend;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge.DistantChunkBoundRenderer;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge.DistantGbufferSlot;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge.DistantTerrainBridge;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge.SharedDistantGbuffer;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge.SlotScheduler;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.jni.NativeBindings;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.terrain.LoadedVolumeBound;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.terrain.TerrainResources;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.common.Logger;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class Gl41MetalRenderBackend implements VoxyRenderBackend {
  private final BackendContext context;
  private final Config config;
  private final DistantRenderer metalRenderer;
  private final SlotScheduler slotScheduler;
  private final DistantTerrainBridge bridge;
  private final DrawlistOpaqueRenderer drawlistOpaqueRenderer;
  private final FrameProfiler profiler = new FrameProfiler();
  // GL-only loaded-volume bound (P1): tracks the Sodium-loaded 16-block render sections and
  // produces
  // the per-frame far-boundary depth the bridge clips distant LOD against. Driven purely by
  // Sodium's
  // 16-block near-scene section signals; it changes NO Metal traversal/residency state (per
  // AGENTS.md).
  private final DistantChunkBoundRenderer boundRenderer = new DistantChunkBoundRenderer();
  // The loaded-volume bound rasterized at the start of the current frame's sampleFrame, reused by
  // the held-slot translucent pass (sampleTranslucent) which runs later in the SAME frame with the
  // same camera/matrices.
  private LoadedVolumeBound currentBound = LoadedVolumeBound.DISABLED;
  private final TerrainResources terrainResources;
  private RenderFrameMatrices lastFrameMatrices = RenderFrameMatrices.identity();
  private SharedDistantGbuffer gbuffer;
  private long nextFrameId;
  private String loggedStrictBridgeUnavailableReason = "";
  private String loggedStrictTranslucentUnavailableReason = "";
  private boolean loggedMissingMatrices;
  // Plan A slot lifecycle for distant translucent water. The opaque pass (PRE_TRANSLUCENT at Iris
  // beginHand RETURN) and the translucent pass (TRANSLUCENT at beginTranslucents RETURN) sample the
  // SAME shared slot within one frame, so PRE_TRANSLUCENT holds the slot (does not retire it) and
  // TRANSLUCENT reuses it and retires it afterwards. Retirement is fence-deferred (decoupled from
  // matrix/uniform publishing), so holding it for the extra deferred passes is safe; we never call
  // waitCurrent twice on the same slot. -1 means nothing held.
  private int heldTranslucentSlot = -1;
  private Frame heldTranslucentFrame;
  private RenderFrameContext heldTranslucentContext;

  public Gl41MetalRenderBackend(BackendContext context) {
    this.context = context;
    this.config = Config.fromSystemProperties();
    this.metalRenderer = new DistantRenderer();
    this.slotScheduler = new SlotScheduler(this.config.waitTimeoutMs());
    this.bridge = new DistantTerrainBridge();
    this.terrainResources = new TerrainResources(context);
    if (this.config.useDrawlistPipeline()) {
      this.drawlistOpaqueRenderer =
          new DrawlistOpaqueRenderer(
              TerrainResources.maxResidentSections(), TerrainResources.geometryCapacityBytes());
      this.terrainResources.setAtlasMirror(this.drawlistOpaqueRenderer);
      this.terrainResources.setDrawlistMirror(this.drawlistOpaqueRenderer);
    } else {
      this.drawlistOpaqueRenderer = null;
    }
    Logger.info(
        "Created Voxy GL41Metal backend with pipeline="
            + this.config.pipelineMode()
            + " (shared_gbuffer remains the default path).");
  }

  @Override
  public RenderBackendId id() {
    return RenderBackendId.GL41METAL;
  }

  @Override
  public boolean rendersLodTerrain() {
    return true;
  }

  @Override
  public RenderFrame setupFrame(RenderFrameContext context) {
    return this.submitMetalFrame(context);
  }

  @Override
  public RenderFrame runFrameStage(
      RenderStage stage, RenderStageContext context, RenderFrame frame) {
    return switch (stage) {
      case FRAME_BEGIN -> {
        if (this.useDirectDrawlistOpaque()) {
          yield frame;
        }
        yield this.submitMetalFrame(context.frameContext());
      }
      // gl46-only setup point inside Iris beginLevelRendering; gl41metal already submitted its
      // frame at FRAME_BEGIN so this is a pass-through.
      case VIEWPORT_SETUP -> frame;
      case SODIUM_SOLID_SYNC, SODIUM_CUTOUT_SYNC -> {
        if (context.shaderPackActive() || frame != null) {
          yield frame;
        }
        if (this.useDirectDrawlistOpaque()) {
          yield frame;
        }
        yield this.submitMetalFrame(context.frameContext());
      }
      case PRE_TRANSLUCENT -> {
        if (!context.shaderPackActive()) {
          yield frame;
        }
        // Hold the sampled slot so the TRANSLUCENT stage can reuse it for the distant water pass.
        this.sampleFrame(frame, context, true);
        yield frame;
      }
      // Distant translucent (water/glass) composite at Iris beginTranslucents RETURN. Reuses the
      // slot
      // held by PRE_TRANSLUCENT and retires it afterwards (Plan A).
      case TRANSLUCENT -> {
        if (context.shaderPackActive()) {
          this.sampleTranslucent(context);
        } else if (this.useDirectDrawlistOpaque()) {
          this.sampleDrawlistTranslucent(context);
        } else {
          this.releaseHeldTranslucentSlot();
        }
        yield frame;
      }
      case OPAQUE -> {
        if (context.shaderPackActive()) {
          yield frame;
        }
        RenderFrame renderFrame = frame;
        if (renderFrame == null) {
          renderFrame = this.submitMetalFrame(context.frameContext());
        }
        if (this.useDirectDrawlistOpaque()) {
          this.sampleDrawlistOpaque(renderFrame, context.frameContext());
        } else {
          this.sampleFrame(renderFrame, context, false);
        }
        yield renderFrame;
      }
      case FRAME_END -> frame;
    };
  }

  private RenderFrame submitMetalFrame(RenderFrameContext context) {
    this.profiler.endFrame();
    this.releaseHeldTranslucentSlot();
    this.ensureGbuffer(context.viewportWidth(), context.viewportHeight());

    long tTick = this.profiler.begin();
    this.terrainResources.tick(context, this.gbuffer.nativeHandle());
    this.profiler.recordTick(tTick);

    if (this.gbuffer != null) {
      long nh = this.gbuffer.nativeHandle();
      double metalGpuMs = NativeBindings.getLastMetalGpuTimeMs(nh);
      if (metalGpuMs > 0) {
        this.profiler.recordMetalGpuMs(metalGpuMs);
      }
      this.profiler.recordPerPassGpuMs(NativeBindings.getPerPassGpuTimesMs(nh));
    }

    if (context.matrices() == null) {
      this.lastFrameMatrices = RenderFrameMatrices.identity();
      if (!this.loggedMissingMatrices) {
        this.loggedMissingMatrices = true;
        Logger.warn("GL41Metal skipped Metal submit because real frame matrices were unavailable");
      }
      return new Frame(context, this.nextFrameId++, -1, new Matrix4f(), new Matrix4f());
    }
    DistantRenderer.FrameMatrices frameMatrices = DistantRenderer.computeFrameMatrices(context);
    this.lastFrameMatrices =
        new RenderFrameMatrices(
            frameMatrices.traversalMvp(),
            context.matrices().modelView(),
            frameMatrices.projection());
    long frameId = this.nextFrameId++;
    int writeSlot = this.slotScheduler.acquireWriteSlot(this.gbuffer);

    long tSubmit = this.profiler.begin();
    if (writeSlot >= 0) {
      this.metalRenderer.submitTraversal(
          this.gbuffer,
          writeSlot,
          frameId,
          context,
          frameMatrices.traversalMvp(),
          frameMatrices.drawMvp(),
          frameMatrices.projection(),
          this.submitOutputMode());
      this.slotScheduler.recordSubmitted();
    } else {
      this.slotScheduler.recordNoFreeSlot();
    }
    this.profiler.recordMetalSubmit(tSubmit);

    return new Frame(
        context, frameId, writeSlot, frameMatrices.drawMvp(), frameMatrices.vanillaDrawMvp());
  }

  @Override
  public void renderOpaque(RenderFrame frame) {
    if (this.useDirectDrawlistOpaque()) {
      this.sampleDrawlistOpaque(frame, null);
    } else {
      this.sampleFrame(frame);
    }
  }

  private boolean useDirectDrawlistOpaque() {
    return this.drawlistOpaqueRenderer != null && !IrisUtil.irisShaderPackEnabled();
  }

  private int submitOutputMode() {
    return this.useDirectDrawlistOpaque()
        ? NativeBindings.OUTPUT_MODE_DRAWLIST
        : NativeBindings.OUTPUT_MODE_SHARED_GBUFFER;
  }

  private void sampleFrame(
      RenderFrame frame, RenderStageContext stageContext, boolean holdForTranslucent) {
    this.sampleFrame(
        frame,
        stageContext.frameContext(),
        stageContext.payload() instanceof ShaderPatchBridgePayload payload ? payload : null,
        holdForTranslucent);
  }

  private void sampleFrame(RenderFrame frame) {
    this.sampleFrame(frame, null, null, false);
  }

  private void sampleDrawlistOpaque(RenderFrame frame, RenderFrameContext stageContext) {
    if (frame == null || this.drawlistOpaqueRenderer == null) {
      return;
    }
    if (!(frame instanceof Frame gl41MetalFrame)) {
      throw new IllegalArgumentException(
          "Cannot render frame for backend " + frame.backendId() + " with GL41Metal backend");
    }
    if (this.gbuffer == null) {
      return;
    }
    RenderFrameContext renderContext =
        stageContext == null ? gl41MetalFrame.context() : stageContext;
    long tWait = this.profiler.begin();
    int sampleSlot =
        this.slotScheduler.selectSlotForSampling(this.gbuffer, gl41MetalFrame.writeSlot());
    this.profiler.recordSlotWait(tWait);
    if (sampleSlot < 0) {
      return;
    }
    boolean slotHasTranslucent =
        NativeBindings.isSlotTranslucentValid(this.gbuffer.nativeHandle(), sampleSlot);
    boolean held = false;
    try {
      DistantRenderer.FrameMatrices drawMatrices =
          DistantRenderer.computeFrameMatrices(renderContext);
      this.currentBound =
          slotHasTranslucent
              ? this.renderCurrentBound(drawMatrices.drawMvp(), renderContext)
              : LoadedVolumeBound.DISABLED;
      this.drawlistOpaqueRenderer.render(
          this.gbuffer.nativeHandle(),
          sampleSlot,
          renderContext,
          drawMatrices.drawMvp(),
          drawMatrices.vanillaDrawMvp(),
          this.config.visibleComposite(),
          this.profiler);
      if (slotHasTranslucent) {
        this.heldTranslucentSlot = sampleSlot;
        this.heldTranslucentFrame = gl41MetalFrame;
        this.heldTranslucentContext = renderContext;
        held = true;
      }
    } finally {
      if (!held) {
        this.slotScheduler.queueSampledSlotRetirement(this.gbuffer, sampleSlot);
      }
    }
  }

  private void sampleFrame(
      RenderFrame frame,
      RenderFrameContext stageContext,
      ShaderPatchBridgePayload bridgePayload,
      boolean holdForTranslucent) {
    if (frame == null) {
      return;
    }
    if (!(frame instanceof Frame gl41MetalFrame)) {
      throw new IllegalArgumentException(
          "Cannot render frame for backend " + frame.backendId() + " with GL41Metal backend");
    }
    if (this.gbuffer == null) {
      return;
    }

    long tWait = this.profiler.begin();
    int sampleSlot =
        this.slotScheduler.selectSlotForSampling(this.gbuffer, gl41MetalFrame.writeSlot());
    this.profiler.recordSlotWait(tWait);
    if (sampleSlot < 0) {
      return;
    }
    // False when the Metal submit skipped the translucent pass (no translucent geometry
    // resident): the tgbuffer textures are stale, so the GL translucent composite (and its
    // full-screen tgbuffer reads) must be skipped for this slot.
    boolean slotHasTranslucent =
        NativeBindings.isSlotTranslucentValid(this.gbuffer.nativeHandle(), sampleSlot);
    boolean held = false;
    try {
      RenderFrameContext renderContext =
          stageContext == null ? gl41MetalFrame.context() : stageContext;
      DistantGbufferSlot slot = this.gbuffer.slot(sampleSlot);

      this.currentBound = this.renderCurrentBound(gl41MetalFrame, renderContext);

      long tBridge = this.profiler.begin();
      if (bridgePayload != null) {
        if (bridgePayload.strictBridgeAvailable()) {
          boolean rendered =
              this.bridge.render(
                  renderContext,
                  slot,
                  DistantTerrainBridge.irisJob(bridgePayload),
                  gl41MetalFrame.drawMvp(),
                  gl41MetalFrame.vanillaDrawMvp());
          this.profiler.recordBridgeOpaque(tBridge);
          if (rendered && holdForTranslucent && slotHasTranslucent) {
            this.heldTranslucentSlot = sampleSlot;
            this.heldTranslucentFrame = gl41MetalFrame;
            this.heldTranslucentContext = renderContext;
            held = true;
          }
          return;
        } else {
          this.profiler.recordBridgeOpaque(tBridge);
          if (!bridgePayload.unavailableReason().isEmpty()
              && !bridgePayload
                  .unavailableReason()
                  .equals(this.loggedStrictBridgeUnavailableReason)) {
            this.loggedStrictBridgeUnavailableReason = bridgePayload.unavailableReason();
            Logger.info(
                "Voxy GL41Metal Iris strict bridge unavailable: "
                    + bridgePayload.unavailableReason());
          }
          return;
        }
      }

      this.bridge.render(
          renderContext,
          slot,
          DistantTerrainBridge.vanillaJob(renderContext, this.config.visibleComposite()),
          gl41MetalFrame.drawMvp(),
          gl41MetalFrame.vanillaDrawMvp());
      if (slotHasTranslucent) {
        this.bridge.renderTranslucent(
            renderContext,
            slot,
            DistantTerrainBridge.vanillaTranslucentJob(
                renderContext, this.config.visibleComposite()),
            gl41MetalFrame.drawMvp(),
            gl41MetalFrame.vanillaDrawMvp(),
            this.currentBound);
      }
      this.profiler.recordBridgeOpaque(tBridge);
    } finally {
      if (!held) {
        this.slotScheduler.queueSampledSlotRetirement(this.gbuffer, sampleSlot);
      }
    }
  }

  /**
   * TRANSLUCENT stage (Iris beginTranslucents RETURN): composite the distant water into the slot
   * the opaque pass held this frame, then retire it. Skips cleanly if no slot was held (e.g. the
   * opaque strict bridge did not run) or the translucent payload is unavailable.
   */
  private void sampleTranslucent(RenderStageContext context) {
    int slot = this.heldTranslucentSlot;
    if (slot < 0 || this.gbuffer == null || this.heldTranslucentFrame == null) {
      this.releaseHeldTranslucentSlot();
      return;
    }
    ShaderPatchBridgePayload payload =
        context.payload() instanceof ShaderPatchBridgePayload p ? p : null;
    long tTrans = this.profiler.begin();
    try {
      if (payload != null && payload.strictBridgeAvailable()) {
        RenderFrameContext renderContext =
            context.frameContext() != null ? context.frameContext() : this.heldTranslucentContext;
        this.bridge.renderTranslucent(
            renderContext,
            this.gbuffer.slot(slot),
            DistantTerrainBridge.translucentJob(payload),
            this.heldTranslucentFrame.drawMvp(),
            this.heldTranslucentFrame.vanillaDrawMvp(),
            this.currentBound);
      } else if (payload != null
          && !payload.unavailableReason().isEmpty()
          && !payload.unavailableReason().equals(this.loggedStrictTranslucentUnavailableReason)) {
        this.loggedStrictTranslucentUnavailableReason = payload.unavailableReason();
        Logger.info(
            "Voxy GL41Metal Iris strict translucent bridge unavailable: "
                + payload.unavailableReason());
      }
    } finally {
      this.profiler.recordBridgeTranslucent(tTrans);
      this.releaseHeldTranslucentSlot();
    }
  }

  private void sampleDrawlistTranslucent(RenderStageContext context) {
    int slot = this.heldTranslucentSlot;
    if (slot < 0
        || this.gbuffer == null
        || this.heldTranslucentFrame == null
        || this.drawlistOpaqueRenderer == null) {
      this.releaseHeldTranslucentSlot();
      return;
    }
    long tTrans = this.profiler.begin();
    try {
      RenderFrameContext renderContext =
          context.frameContext() != null ? context.frameContext() : this.heldTranslucentContext;
      DistantRenderer.FrameMatrices drawMatrices =
          DistantRenderer.computeFrameMatrices(renderContext);
      this.drawlistOpaqueRenderer.renderTranslucent(
          this.gbuffer.nativeHandle(),
          slot,
          renderContext,
          drawMatrices.drawMvp(),
          drawMatrices.vanillaDrawMvp(),
          this.currentBound,
          this.config.visibleComposite(),
          this.profiler);
    } finally {
      this.profiler.recordBridgeTranslucent(tTrans);
      this.releaseHeldTranslucentSlot();
    }
  }

  private LoadedVolumeBound renderCurrentBound(Frame gl41MetalFrame, RenderFrameContext context) {
    return this.renderCurrentBound(gl41MetalFrame.drawMvp(), context);
  }

  private LoadedVolumeBound renderCurrentBound(Matrix4fc drawMvp, RenderFrameContext context) {
    long tBound = this.profiler.begin();
    int worldMinY = -64;
    int worldMaxY = 320;
    var level = Minecraft.getInstance().level;
    if (level != null) {
      worldMinY = level.getMinSection() << 4;
      worldMaxY = level.getMaxSection() << 4;
    }
    int verticalRadiusBlocks = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
    LoadedVolumeBound bound =
        this.boundRenderer.render(
            drawMvp,
            context.cameraX(),
            context.cameraY(),
            context.cameraZ(),
            worldMinY,
            worldMaxY,
            verticalRadiusBlocks,
            context.viewportWidth(),
            context.viewportHeight());
    this.profiler.recordBoundRender(tBound);
    return bound;
  }

  /** Fence-deferred retirement of the held translucent slot, if any. */
  private void releaseHeldTranslucentSlot() {
    if (this.heldTranslucentSlot >= 0 && this.gbuffer != null) {
      this.slotScheduler.queueSampledSlotRetirement(this.gbuffer, this.heldTranslucentSlot);
    }
    this.heldTranslucentSlot = -1;
    this.heldTranslucentFrame = null;
    this.heldTranslucentContext = null;
  }

  /** Drops the held slot reference WITHOUT retiring it (used when the native slots are reset). */
  private void dropHeldTranslucentSlot() {
    this.heldTranslucentSlot = -1;
    this.heldTranslucentFrame = null;
    this.heldTranslucentContext = null;
  }

  @Override
  public void setRenderDistance(float renderDistance) {
    this.terrainResources.setRenderDistance(renderDistance);
  }

  @Override
  public void addDebugInfo(List<String> debug) {
    debug.add("Voxy backend: GL41METAL");
    debug.add(
        "Voxy GL41Metal status: pipeline="
            + this.config.pipelineMode()
            + ", directDrawlistOpaque="
            + this.useDirectDrawlistOpaque());
    debug.add("Voxy GL41Metal selection: " + this.context.selection().reason());
    debug.add("Voxy GL41Metal config: " + this.config);
    if (this.gbuffer != null) {
      debug.add("Voxy GL41Metal native: " + this.gbuffer.description());
    }
    this.slotScheduler.addDebugInfo(debug);
    this.terrainResources.addDebugInfo(debug);
    this.profiler.addDebugInfo(debug);
  }

  @Override
  public RenderFrameMatrices getLastFrameMatrices() {
    return this.lastFrameMatrices;
  }

  @Override
  public int voxyDistantDepthTextureId() {
    return this.bridge.voxyDistantDepthTextureId();
  }

  @Override
  public void onChunkTrackerReset() {
    // GL-only loaded-volume bound: Sodium rebuilt its render section manager (world load or vanilla
    // render-distance change), so drop the tracked near-scene volume. This touches no Metal state.
    this.boundRenderer.reset();
    this.terrainResources.onChunkTrackerReset();
  }

  @Override
  public void onSectionRenderStateChanged(long sectionPos, boolean present) {
    // GL-only loaded-volume bound: track Sodium's 16-block near-scene render sections so the bridge
    // can clip distant LOD that overlaps the near scene (P1). This is exactly the kind of 16-block
    // near-scene signal AGENTS.md allows here; it does NOT change Metal traversal/residency.
    if (present) {
      this.boundRenderer.addSection(sectionPos);
    } else {
      this.boundRenderer.removeSection(sectionPos);
    }
    this.terrainResources.onSectionRenderStateChanged(sectionPos, present);
  }

  @Override
  public void close() {
    Logger.info("Shutting down Voxy GL41Metal shared texture backend");
    this.dropHeldTranslucentSlot();
    this.terrainResources.close();
    if (this.drawlistOpaqueRenderer != null) {
      this.drawlistOpaqueRenderer.close();
    }
    this.bridge.close();
    this.boundRenderer.close();
    if (this.gbuffer != null) {
      this.slotScheduler.closeRetiringSlots(this.gbuffer);
      Logger.info("Voxy GL41Metal slot stats: " + this.slotScheduler.summary());
      this.gbuffer.close();
      this.gbuffer = null;
    }
  }

  private void ensureGbuffer(int width, int height) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Invalid GL41Metal viewport size " + width + "x" + height);
    }
    if (this.gbuffer != null && this.gbuffer.width() == width && this.gbuffer.height() == height) {
      return;
    }
    if (this.gbuffer != null) {
      // Resize the screen-sized gbuffer textures in place, preserving the native context handle and
      // all terrain/world/atlas residency. The bridge's own depth textures self-heal on size change
      // and the slot scheduler is reset because the native slots are forced back to Free.
      // Any held translucent slot is dropped (not retired): the native slots are about to be reset
      // to Free, so retiring a now-stale slot id would be wrong.
      this.dropHeldTranslucentSlot();
      this.slotScheduler.closeRetiringSlots(this.gbuffer);
      this.gbuffer.resize(width, height);
      this.slotScheduler.reset();
      Logger.info("Voxy GL41Metal shared gbuffer resized: " + this.gbuffer.description());
      return;
    }
    this.gbuffer = SharedDistantGbuffer.create(this.config.slotCount(), width, height);
    this.slotScheduler.reset();
    Logger.info("Voxy GL41Metal shared gbuffer initialized: " + this.gbuffer.description());
  }
}
