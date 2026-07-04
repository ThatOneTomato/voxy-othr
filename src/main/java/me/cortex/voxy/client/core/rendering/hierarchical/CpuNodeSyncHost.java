package me.cortex.voxy.client.core.rendering.hierarchical;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.concurrent.ConcurrentLinkedDeque;
import me.cortex.voxy.client.core.rendering.SectionUpdateRouter;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.geometry.BasicAsyncGeometryManager;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import org.lwjgl.system.MemoryUtil;

/**
 * CPU-side GL46 hierarchy/readiness producer for non-GL consumers.
 *
 * <p>This class deliberately reuses {@link NodeManager} and {@link BasicAsyncGeometryManager} so
 * Metal receives the same packed node, section metadata, and geometry ABI as GL46 traversal, while
 * keeping GL compute/SSBO upload machinery out of GL41Metal.
 */
public final class CpuNodeSyncHost implements AutoCloseable {
  public interface Sink {
    void uploadNode(int nodeId, long nodeAddress);

    void uploadSectionMetadata(int sectionId, long metadataAddress);

    void uploadGeometry(int geometryElementOffset, long geometryAddress, long geometryBytes);

    void removeSection(int sectionId);

    void addTopNode(int nodeId);

    void removeTopNode(int nodeId);
  }

  private static final int MAX_TOP_LEVEL_UPDATES_PER_FRAME = 512;
  private static final int MAX_CHILD_UPDATES_PER_FRAME = 512;
  private static final int MAX_GEOMETRY_UPDATES_PER_FRAME = 384;
  private final SectionUpdateRouter router = new SectionUpdateRouter();
  private final BasicAsyncGeometryManager geometryManager;
  private final NodeManager manager;
  private final ConcurrentLinkedDeque<Long> topLevelAdds = new ConcurrentLinkedDeque<>();
  private final ConcurrentLinkedDeque<Long> topLevelRemoves = new ConcurrentLinkedDeque<>();
  private final ConcurrentLinkedDeque<WorldSection> childUpdateQueue =
      new ConcurrentLinkedDeque<>();
  private final ConcurrentLinkedDeque<BuiltSection> geometryUpdateQueue =
      new ConcurrentLinkedDeque<>();
  private final IntOpenHashSet topNodeAdds = new IntOpenHashSet();
  private final IntOpenHashSet topNodeRemoves = new IntOpenHashSet();
  private final LongOpenHashSet nativeRequestsInFlight = new LongOpenHashSet();
  private boolean closed;
  private long nativeRequestsProcessed;
  private long duplicateNativeRequestsSkipped;
  private long nodeUploads;
  private long metadataUploads;
  private long geometryUploads;
  private long topNodeAddCount;
  private long topNodeRemoveCount;

  public CpuNodeSyncHost(
      int maxNodeCount,
      int maxSectionCount,
      long geometryCapacityBytes,
      RenderGenerationService renderService) {
    this.geometryManager = new BasicAsyncGeometryManager(maxSectionCount, geometryCapacityBytes);
    this.router.setCallbacks(
        renderService::enqueueTask, renderService::enqueueTask, this::submitChildChange);
    renderService.setResultConsumer(this::submitGeometryResult);
    this.manager = new NodeManager(maxNodeCount, this.geometryManager, this.router);
    this.manager.setTLNCallbacks(
        nodeId -> this.topNodeAdds.add(nodeId),
        nodeId -> {
          this.topNodeAdds.remove(nodeId);
          this.topNodeRemoves.add(nodeId);
        });
  }

  public void addTopLevel(long sectionPos) {
    this.topLevelAdds.add(sectionPos);
  }

  public void removeTopLevel(long sectionPos) {
    this.topLevelRemoves.add(sectionPos);
  }

  public void worldEvent(WorldSection section, int flags, int neighborMask) {
    this.router.forwardEvent(section, flags);
    if ((neighborMask & 0b000001) != 0) {
      this.router.triggerRemesh(
          WorldEngine.getWorldSectionId(section.lvl, section.x, section.y - 1, section.z));
    }
    if ((neighborMask & 0b000010) != 0) {
      this.router.triggerRemesh(
          WorldEngine.getWorldSectionId(section.lvl, section.x, section.y + 1, section.z));
    }
    if ((neighborMask & 0b000100) != 0) {
      this.router.triggerRemesh(
          WorldEngine.getWorldSectionId(section.lvl, section.x - 1, section.y, section.z));
    }
    if ((neighborMask & 0b001000) != 0) {
      this.router.triggerRemesh(
          WorldEngine.getWorldSectionId(section.lvl, section.x + 1, section.y, section.z));
    }
    if ((neighborMask & 0b010000) != 0) {
      this.router.triggerRemesh(
          WorldEngine.getWorldSectionId(section.lvl, section.x, section.y, section.z - 1));
    }
    if ((neighborMask & 0b100000) != 0) {
      this.router.triggerRemesh(
          WorldEngine.getWorldSectionId(section.lvl, section.x, section.y, section.z + 1));
    }
  }

  public void drain(long[] metalRequests, Sink sink) {
    this.processMetalRequests(metalRequests);
    this.processTopLevelUpdates();
    this.processChildUpdates();
    this.processGeometryUpdates();
    this.flushSync(sink);
  }

  public void addDebug(java.util.List<String> debug) {
    debug.add(
        "Voxy GL41Metal hierarchy sync: nodes="
            + this.manager.getCurrentMaxNodeId()
            + ", sections="
            + this.geometryManager.getSectionCount()
            + ", geometryMb="
            + (this.geometryManager.getGeometryUsedBytes() >> 20)
            + ", requests="
            + this.nativeRequestsProcessed
            + ", duplicateRequests="
            + this.duplicateNativeRequestsSkipped
            + ", inFlight="
            + this.nativeRequestsInFlight.size());
    debug.add(
        "Voxy GL41Metal hierarchy uploads: node="
            + this.nodeUploads
            + ", metadata="
            + this.metadataUploads
            + ", geometry="
            + this.geometryUploads
            + ", topAdd="
            + this.topNodeAddCount
            + ", topRemove="
            + this.topNodeRemoveCount);
  }

  private void processMetalRequests(long[] requests) {
    if (requests == null) {
      return;
    }
    for (long request : requests) {
      if (!this.nativeRequestsInFlight.add(request)) {
        this.duplicateNativeRequestsSkipped++;
        continue;
      }
      this.manager.processRequest(request);
      this.nativeRequestsProcessed++;
    }
  }

  private void processTopLevelUpdates() {
    for (int i = 0; i < MAX_TOP_LEVEL_UPDATES_PER_FRAME; i++) {
      Long section = this.topLevelRemoves.poll();
      if (section == null) {
        break;
      }
      this.manager.removeTopLevelNode(section);
    }
    for (int i = 0; i < MAX_TOP_LEVEL_UPDATES_PER_FRAME; i++) {
      Long section = this.topLevelAdds.poll();
      if (section == null) {
        break;
      }
      this.manager.insertTopLevelNode(section);
    }
  }

  private void processChildUpdates() {
    for (int i = 0; i < MAX_CHILD_UPDATES_PER_FRAME; i++) {
      WorldSection section = this.childUpdateQueue.poll();
      if (section == null) {
        break;
      }
      try {
        this.manager.processChildChange(section.key, section.getNonEmptyChildren());
        this.nativeRequestsInFlight.remove(section.key);
      } finally {
        section.release();
      }
    }
  }

  private void processGeometryUpdates() {
    for (int i = 0; i < MAX_GEOMETRY_UPDATES_PER_FRAME; i++) {
      BuiltSection section = this.geometryUpdateQueue.poll();
      if (section == null) {
        break;
      }
      this.manager.processGeometryResult(section);
      this.nativeRequestsInFlight.remove(section.position);
    }
  }

  private void flushSync(Sink sink) {
    if (!this.topNodeRemoves.isEmpty()) {
      var iter = this.topNodeRemoves.intIterator();
      while (iter.hasNext()) {
        sink.removeTopNode(iter.nextInt());
        this.topNodeRemoveCount++;
      }
      this.topNodeRemoves.clear();
    }
    if (!this.topNodeAdds.isEmpty()) {
      var iter = this.topNodeAdds.intIterator();
      while (iter.hasNext()) {
        sink.addTopNode(iter.nextInt());
        this.topNodeAddCount++;
      }
      this.topNodeAdds.clear();
    }

    var removals = this.geometryManager.getHeapRemovals();
    if (!removals.isEmpty()) {
      // These are geometry arena element offsets, not section ids. Section residency is invalidated
      // through the metadata update stream below. TODO(gl41metal): reclaim native geometry ranges
      // when the
      // GL41Metal arena grows a real free-list/compaction path.
      removals.clear();
    }

    var uploads = this.geometryManager.getUploads();
    if (!uploads.isEmpty()) {
      var iter = uploads.int2ObjectEntrySet().fastIterator();
      while (iter.hasNext()) {
        var entry = iter.next();
        MemoryBuffer geometry = entry.getValue();
        sink.uploadGeometry(entry.getIntKey(), geometry.address, geometry.size);
        geometry.free();
        this.geometryUploads++;
      }
      uploads.clear();
    }

    var metadataIds = this.geometryManager.getUpdateIds();
    if (!metadataIds.isEmpty()) {
      MemoryBuffer metadata = new MemoryBuffer(BasicAsyncGeometryManager.SECTION_METADATA_SIZE);
      try {
        var iter = metadataIds.intIterator();
        while (iter.hasNext()) {
          int sectionId = iter.nextInt();
          MemoryUtil.memSet(metadata.address, 0, metadata.size);
          this.geometryManager.writeMetadata(sectionId, metadata.address);
          sink.uploadSectionMetadata(sectionId, metadata.address);
          this.metadataUploads++;
        }
      } finally {
        metadata.free();
      }
      metadataIds.clear();
    }

    var nodeIds = this.manager.getNodeUpdates();
    if (!nodeIds.isEmpty()) {
      MemoryBuffer node = new MemoryBuffer(16);
      try {
        var iter = nodeIds.intIterator();
        while (iter.hasNext()) {
          int nodeId = iter.nextInt();
          this.manager.writeNode(nodeId, node.address);
          sink.uploadNode(nodeId, node.address);
          this.nodeUploads++;
        }
      } finally {
        node.free();
      }
      nodeIds.clear();
    }
  }

  private void submitChildChange(WorldSection section) {
    if (this.closed) {
      return;
    }
    section.acquire();
    this.childUpdateQueue.add(section);
  }

  private void submitGeometryResult(BuiltSection section) {
    if (this.closed) {
      section.free();
      return;
    }
    this.geometryUpdateQueue.add(section);
  }

  @Override
  public void close() {
    this.closed = true;
    WorldSection section;
    while ((section = this.childUpdateQueue.poll()) != null) {
      section.release();
    }
    BuiltSection geometry;
    while ((geometry = this.geometryUpdateQueue.poll()) != null) {
      geometry.free();
    }
    if (!this.geometryManager.getUploads().isEmpty()) {
      var iter = this.geometryManager.getUploads().values().iterator();
      while (iter.hasNext()) {
        iter.next().free();
      }
      this.geometryManager.getUploads().clear();
    }
  }
}
