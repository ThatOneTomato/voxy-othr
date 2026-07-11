package me.cortex.voxy.client.core.rendering.backend.gl41metal.terrain;

import java.util.ArrayDeque;
import java.util.Queue;
import me.cortex.voxy.client.core.model.BakedModelPayload;
import me.cortex.voxy.client.core.model.BiomeModelPayload;
import me.cortex.voxy.client.core.model.ModelOutputSink;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;

public final class MaterialStore implements ModelOutputSink {
  private static final int MAX_MODEL_UPLOADS_PER_FRAME = 256;
  private static final int MAX_BIOME_UPLOADS_PER_FRAME = 1024;

  private final Queue<ModelUpload> pendingModels = new ArrayDeque<>();
  private final Queue<BiomeUpload> pendingBiomes = new ArrayDeque<>();
  private int uploadedModelCount;
  private int fallbackModelCount;
  private int uploadedCustomIdModelCount;
  private int uploadedZeroCustomIdModelCount;
  private long uploadedBiomeBatchCount;
  private boolean loggedFirstModelUpload;
  private AtlasMirror atlasMirror;

  public interface AtlasMirror {
    default void uploadModelData(int modelId, long modelAddress, long modelBytes) {}

    default void uploadBiomeData(
        long colourAddress,
        long colourBytes,
        long modelBiomePairsAddress,
        long modelBiomePairsBytes) {}

    void uploadModelTexture(int modelId, long textureAddress, long textureBytes);
  }

  synchronized void setAtlasMirror(AtlasMirror atlasMirror) {
    this.atlasMirror = atlasMirror;
  }

  @Override
  public synchronized void uploadModel(BakedModelPayload payload) {
    if (payload.biomeUploadIndex() != -1 && payload.biomeUpload() != null) {
      MemoryBuffer modelBiomePair = new MemoryBuffer(Long.BYTES);
      MemoryUtil.memPutLong(
          modelBiomePair.address,
          Integer.toUnsignedLong(payload.modelId())
              | (Integer.toUnsignedLong(payload.biomeUploadIndex()) << 32));
      this.pendingBiomes.add(new BiomeUpload(payload.biomeUpload().copy(), modelBiomePair));
    }
    this.pendingModels.add(
        new ModelUpload(
            payload.modelId(),
            payload.model().copy(),
            this.atlasMirror != null ? payload.texture().copy() : null,
            payload.renderLayer(),
            payload.fallbackReason(),
            payload.sourceDescription()));
  }

  @Override
  public synchronized void uploadBiomeData(BiomeModelPayload payload) {
    this.pendingBiomes.add(
        new BiomeUpload(payload.biomeColourBuffer().copy(), payload.modelBiomeIndexPairs().copy()));
  }

  synchronized void drainUploads() {
    this.drainBiomeUploads();

    int modelUploads = 0;
    while (modelUploads++ < MAX_MODEL_UPLOADS_PER_FRAME) {
      ModelUpload upload = this.pendingModels.poll();
      if (upload == null) {
        break;
      }
      try {
        int customId = upload.customId();
        if (this.atlasMirror != null) {
          this.atlasMirror.uploadModelData(upload.modelId, upload.model.address, upload.model.size);
          if (upload.texture != null) {
            this.atlasMirror.uploadModelTexture(
                upload.modelId, upload.texture.address, upload.texture.size);
          }
        }
        this.uploadedModelCount++;
        if (customId == 0) {
          this.uploadedZeroCustomIdModelCount++;
        } else {
          this.uploadedCustomIdModelCount++;
        }
        if (upload.fallbackReason != BakedModelPayload.FALLBACK_NONE) {
          this.fallbackModelCount++;
          if (this.fallbackModelCount <= 16) {
            Logger.info(
                "GL41Metal model fallback "
                    + BakedModelPayload.fallbackReasonName(upload.fallbackReason)
                    + " for "
                    + upload.sourceDescription
                    + " as "
                    + BakedModelPayload.renderLayerName(upload.renderLayer)
                    + " model "
                    + upload.modelId);
          }
        }
        if (!this.loggedFirstModelUpload) {
          this.loggedFirstModelUpload = true;
          Logger.info("GL41Metal material store uploaded first model " + upload.modelId);
        }
      } finally {
        upload.free();
      }
    }

    this.drainBiomeUploads();
  }

  private void drainBiomeUploads() {
    int biomeUploads = 0;
    while (biomeUploads++ < MAX_BIOME_UPLOADS_PER_FRAME) {
      BiomeUpload upload = this.pendingBiomes.poll();
      if (upload == null) {
        break;
      }
      try {
        if (this.atlasMirror != null) {
          this.atlasMirror.uploadBiomeData(
              upload.biomeColours.address,
              upload.biomeColours.size,
              upload.modelBiomePairs.address,
              upload.modelBiomePairs.size);
        }
        this.uploadedBiomeBatchCount++;
      } finally {
        upload.free();
      }
    }
  }

  synchronized int pendingModelUploads() {
    return this.pendingModels.size();
  }

  synchronized int pendingBiomeUploads() {
    return this.pendingBiomes.size();
  }

  synchronized int uploadedModelCount() {
    return this.uploadedModelCount;
  }

  synchronized long uploadedBiomeBatchCount() {
    return this.uploadedBiomeBatchCount;
  }

  synchronized int uploadedCustomIdModelCount() {
    return this.uploadedCustomIdModelCount;
  }

  synchronized int uploadedZeroCustomIdModelCount() {
    return this.uploadedZeroCustomIdModelCount;
  }

  @Override
  public synchronized void close() {
    ModelUpload model;
    while ((model = this.pendingModels.poll()) != null) {
      model.free();
    }
    BiomeUpload biome;
    while ((biome = this.pendingBiomes.poll()) != null) {
      biome.free();
    }
  }

  private record ModelUpload(
      int modelId,
      MemoryBuffer model,
      MemoryBuffer texture,
      int renderLayer,
      int fallbackReason,
      String sourceDescription) {
    int customId() {
      return MemoryUtil.memGetInt(this.model.address + 32);
    }

    void free() {
      this.model.free();
      if (this.texture != null) {
        this.texture.free();
      }
    }
  }

  private record BiomeUpload(MemoryBuffer biomeColours, MemoryBuffer modelBiomePairs) {
    void free() {
      this.biomeColours.free();
      this.modelBiomePairs.free();
    }
  }
}
