package me.cortex.voxy.client.core.rendering.backend.gl41metal;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Arrays;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import me.cortex.voxy.client.core.rendering.RenderDistanceTracker;
import me.cortex.voxy.client.core.rendering.backend.BackendContext;
import me.cortex.voxy.client.core.rendering.backend.RenderFrameContext;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.hierachical.MetalNodeSyncHost;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;

final class Gl41MetalTerrainResources implements AutoCloseable {
  private static final int MAX_RESIDENT_SECTIONS =
      readInt("voxy.gl41metal.residencyMaxSections", 1 << 18, 1024, 1 << 20);
  private static final long GEOMETRY_CAPACITY_BYTES =
      (long) readInt("voxy.gl41metal.geometryCapacityMb", 256, 16, 2048) * 1024L * 1024L;
  private static final int MAX_NODES =
      readInt("voxy.gl41metal.maxNodes", 1 << 21, 1024, (1 << 24) - 1);
  private static final int MAX_TRAVERSAL_QUEUE =
      readInt("voxy.gl41metal.maxTraversalQueue", 200_000, 1024, 2_000_000);
  private static final int MAX_TRAVERSAL_REQUESTS =
      readInt("voxy.gl41metal.maxTraversalRequests", 1024, 1, 50_000);
  private static final int MAX_WORKLIST_ITEMS =
      readInt("voxy.gl41metal.maxWorklistItems", 400_000, 1024, 2_000_000);
  private static final int MAX_RASTER_QUADS =
      readInt("voxy.gl41metal.maxRasterQuads", 8_000_000, 1024, 32_000_000);
  private static final int MESH_BATCH_SIZE =
      readInt("voxy.gl41metal.meshBatchSize", 32, 16, 64);

  private final WorldEngine world;
  private final Gl41MetalMaterialStore materialStore;
  private final ModelBakerySubsystem modelService;
  private final RenderGenerationService renderGen;
  private final MetalNodeSyncHost nodeSyncHost;
  private final RenderDistanceTracker renderDistanceTracker;
  private final MetalNodeSyncHost.Sink nativeSink = new NativeSyncSink();
  private long nativeHandle;
  private boolean nativeResourcesCreated;
  private boolean closed;
  private long validationRuns;
  private Gl41MetalTerrainStats lastStats = Gl41MetalTerrainStats.fromNative(new long[0]);
  private Object2IntMap<BlockState> irisBlockStateMapping;
  private boolean loggedFirstValidation;

  Gl41MetalTerrainResources(BackendContext context) {
    this.world = context.world();
    this.materialStore = new Gl41MetalMaterialStore();
    this.modelService = new ModelBakerySubsystem(this.world.getMapper(), this.materialStore);
    this.renderGen =
        new RenderGenerationService(
            this.world,
            this.modelService,
            context.serviceManager(),
            false,
            RenderGenerationService.TaskPriorityMode.FINE_LOD_FIRST);
    this.nodeSyncHost =
        new MetalNodeSyncHost(
            MAX_NODES, MAX_RESIDENT_SECTIONS, GEOMETRY_CAPACITY_BYTES, this.renderGen);
    Arrays.stream(this.world.getMapper().getBiomeEntries()).forEach(this.modelService::addBiome);
    this.world.getMapper().setBiomeCallback(this.modelService::addBiome);
    this.world.setDirtyCallback(this::onWorldChanged);

    int minSec = -4;
    int maxSec = 19;
    var level = Minecraft.getInstance().level;
    if (level != null) {
      minSec = level.getMinSection() >> 5;
      maxSec = (level.getMaxSection() - 1) >> 5;
    }
    if (VoxyCommon.IS_MINE_IN_ABYSS) {
      minSec = -8;
      maxSec = 7;
    }
    this.renderDistanceTracker =
        new RenderDistanceTracker(
            20, minSec, maxSec, this.nodeSyncHost::addTopLevel, this.nodeSyncHost::removeTopLevel);
    this.setRenderDistance(VoxyConfig.CONFIG.sectionRenderDistance);
    this.syncIrisBlockStateMapping();
    Logger.info("Created GL41Metal Metal LOD/culling worklist resources");
  }

  void tick(RenderFrameContext frameContext, long nativeHandle) {
    this.syncIrisBlockStateMapping();
    this.ensureNativeResources(nativeHandle);
    this.renderDistanceTracker.setCenterAndProcess(frameContext.cameraX(), frameContext.cameraZ());
    this.modelService.tick(100_000_000L);
    this.materialStore.drainUploads(nativeHandle);
    this.nodeSyncHost.drain(Gl41MetalNative.pollTraversalRequests(nativeHandle), this.nativeSink);
    this.runValidation(nativeHandle);
  }

  void onChunkTrackerReset() {
    // Sodium rebuilds its RenderSectionManager (firing this hook) on world load AND on every
    // vanilla render-distance change. GL41Metal distant residency is keyed by Voxy 32-block
    // WorldSection ids and driven entirely by WorldEngine dirty events plus the Metal request
    // queue; it is independent of the vanilla/Sodium near-scene render distance. Clearing native
    // terrain (and the Java residency tracker) here wiped all distant LOD whenever the player
    // raised vanilla render distance, and the Java side then believed everything was still
    // resident so nothing was re-streamed. Per AGENTS.md ("Do not clear Metal traversal or
    // residency state from Sodium section add/remove callbacks"), this is a no-op. World reloads
    // recreate the whole backend, which provides a genuine fresh start.
  }

  void onSectionRenderStateChanged(long sectionPos, boolean present) {
    // Sodium reports vanilla 16-block render-section lifecycle here. GL41Metal
    // terrain residency is keyed by Voxy 32-block WorldSection ids and is driven
    // by WorldEngine dirty events plus the Metal request queue. Clearing Metal
    // traversal scratch from this hook can turn a valid current frame into an
    // empty distant gbuffer when chunks cross the vanilla render boundary.
  }

  void addDebugInfo(java.util.List<String> debug) {
    debug.add("Voxy GL41Metal terrain residency: " + this.lastStats.compact());
    debug.add(
        "Voxy GL41Metal terrain queues: models="
            + this.materialStore.pendingModelUploads()
            + ", biomes="
            + this.materialStore.pendingBiomeUploads()
            + ", bakedModels="
            + this.materialStore.uploadedModelCount()
            + ", biomeBatches="
            + this.materialStore.uploadedBiomeBatchCount()
            + ", customIdModels="
            + this.materialStore.uploadedCustomIdModelCount()
            + ", zeroCustomIdModels="
            + this.materialStore.uploadedZeroCustomIdModelCount());
    this.nodeSyncHost.addDebug(debug);
  }

  private void ensureNativeResources(long handle) {
    if (this.nativeResourcesCreated && this.nativeHandle == handle) {
      return;
    }
    if (this.nativeResourcesCreated) {
      // Viewport resizes now resize the shared gbuffer in place (same native handle, terrain
      // preserved), so the handle should stay stable for the backend's lifetime. Reaching here
      // means the underlying native context was replaced unexpectedly; reset Java residency stats
      // as a defensive fallback. (Resident model/section data on the lost handle cannot be
      // re-streamed from here without a full host rebuild, which the in-place resize avoids.)
      Logger.warn(
          "GL41Metal terrain native context changed unexpectedly; resetting residency stats");
      this.resetJavaResidencyState();
    }
    this.nativeHandle = handle;
    Gl41MetalNative.createTerrainResources(
        handle,
        MAX_RESIDENT_SECTIONS,
        GEOMETRY_CAPACITY_BYTES,
        MAX_NODES,
        MAX_TRAVERSAL_QUEUE,
        MAX_TRAVERSAL_REQUESTS,
        MAX_WORKLIST_ITEMS,
        MAX_RASTER_QUADS,
        Gl41MetalMaterialStore.FULL_ATLAS_UPLOADS ? Gl41MetalMaterialStore.ATLAS_WIDTH : 0,
        Gl41MetalMaterialStore.FULL_ATLAS_UPLOADS ? Gl41MetalMaterialStore.ATLAS_HEIGHT : 0,
        Gl41MetalMaterialStore.FULL_ATLAS_UPLOADS ? Gl41MetalMaterialStore.ATLAS_MIP_LEVELS : 0,
        MESH_BATCH_SIZE);
    this.nativeResourcesCreated = true;
    Logger.info(
        "GL41Metal terrain native resources initialized: maxSections="
            + MAX_RESIDENT_SECTIONS
            + ", geometryCapacity="
            + GEOMETRY_CAPACITY_BYTES
            + ", maxNodes="
            + MAX_NODES
            + ", traversalQueue="
            + MAX_TRAVERSAL_QUEUE
            + ", traversalRequests="
            + MAX_TRAVERSAL_REQUESTS
            + ", worklistItems="
            + MAX_WORKLIST_ITEMS
            + ", rasterQuads="
            + MAX_RASTER_QUADS
            + ", atlas="
            + Gl41MetalMaterialStore.ATLAS_WIDTH
            + "x"
            + Gl41MetalMaterialStore.ATLAS_HEIGHT
            + " mips="
            + Gl41MetalMaterialStore.ATLAS_MIP_LEVELS
            + ", atlasUploads="
            + (Gl41MetalMaterialStore.FULL_ATLAS_UPLOADS
                ? "enabled"
                : "disabled; textured quad raster will use material/debug colors only"));
  }

  private void resetJavaResidencyState() {
    this.validationRuns = 0;
    this.loggedFirstValidation = false;
    this.lastStats = Gl41MetalTerrainStats.fromNative(new long[0]);
  }

  private void runValidation(long handle) {
    Gl41MetalNative.validateTerrainResources(handle);
    this.validationRuns++;
    this.lastStats = Gl41MetalTerrainStats.fromNative(Gl41MetalNative.getTerrainStats(handle));
    if (!this.loggedFirstValidation && this.lastStats.validationResidentSections() > 0) {
      this.loggedFirstValidation = true;
      Logger.info("GL41Metal terrain Metal validation passed: " + this.lastStats.compact());
    }
  }

  private void onWorldChanged(WorldSection section, int updateFlags, int neighborMask) {
    this.nodeSyncHost.worldEvent(section, updateFlags, neighborMask);
  }

  private void syncIrisBlockStateMapping() {
    Object2IntMap<BlockState> mapping = IrisUtil.irisBlockStateIds();
    if (mapping == this.irisBlockStateMapping) {
      return;
    }
    this.modelService.factory.setCustomBlockStateMapping(mapping);
    if (mapping == null) {
      if (this.irisBlockStateMapping != null) {
        Logger.info("GL41Metal cleared Iris material block-state mapping");
      }
      this.irisBlockStateMapping = null;
      return;
    }
    if (this.irisBlockStateMapping == null) {
      Logger.info("GL41Metal installed Iris material block-state mapping for model baking");
    } else {
      Logger.warn(
          "GL41Metal Iris material block-state mapping changed; newly baked models will use the "
              + "new custom ids. Reload the world if already-baked distant materials look stale.");
    }
    this.irisBlockStateMapping = mapping;
  }

  void setRenderDistance(float renderDistance) {
    // Reference passes the raw section render distance (no +1 ring, unlike Gl46RenderBackend);
    // ceil only converts this branch's fractional RD to the tracker's int domain.
    this.renderDistanceTracker.setRenderDistance((int) Math.ceil(renderDistance));
  }

  @Override
  public void close() {
    if (this.closed) {
      return;
    }
    this.closed = true;
    this.world.setDirtyCallback(null);
    this.world.getMapper().setBiomeCallback(null);
    try {
      this.renderGen.shutdown();
    } catch (Exception e) {
      Logger.error("Error shutting down GL41Metal render generation service", e);
    }
    try {
      this.modelService.shutdown();
    } catch (Exception e) {
      Logger.error("Error shutting down GL41Metal model service", e);
    }
    this.nodeSyncHost.close();
    this.materialStore.close();
  }

  private static int readInt(String key, int fallback, int min, int max) {
    String value = System.getProperty(key);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      int parsed = Integer.parseInt(value);
      return Math.max(min, Math.min(max, parsed));
    } catch (NumberFormatException e) {
      Logger.warn("Invalid GL41Metal integer config " + key + "=" + value + ", using " + fallback);
      return fallback;
    }
  }

  private final class NativeSyncSink implements MetalNodeSyncHost.Sink {
    @Override
    public void uploadNode(int nodeId, long nodeAddress) {
      Gl41MetalNative.uploadNode(Gl41MetalTerrainResources.this.nativeHandle, nodeId, nodeAddress);
    }

    @Override
    public void uploadSectionMetadata(int sectionId, long metadataAddress) {
      Gl41MetalNative.uploadSectionMetadata(
          Gl41MetalTerrainResources.this.nativeHandle, sectionId, metadataAddress);
    }

    @Override
    public void uploadGeometry(
        int geometryElementOffset, long geometryAddress, long geometryBytes) {
      Gl41MetalNative.uploadGeometry(
          Gl41MetalTerrainResources.this.nativeHandle,
          geometryElementOffset,
          geometryAddress,
          geometryBytes);
    }

    @Override
    public void removeSection(int sectionId) {
      Gl41MetalNative.removeSection(Gl41MetalTerrainResources.this.nativeHandle, sectionId);
    }

    @Override
    public void addTopNode(int nodeId) {
      Gl41MetalNative.addTopNode(Gl41MetalTerrainResources.this.nativeHandle, nodeId);
    }

    @Override
    public void removeTopNode(int nodeId) {
      Gl41MetalNative.removeTopNode(Gl41MetalTerrainResources.this.nativeHandle, nodeId);
    }
  }
}
