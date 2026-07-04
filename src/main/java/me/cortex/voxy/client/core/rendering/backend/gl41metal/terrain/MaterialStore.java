package me.cortex.voxy.client.core.rendering.backend.gl41metal.terrain;

import me.cortex.voxy.client.core.rendering.backend.gl41metal.jni.NativeBindings;

import java.util.ArrayDeque;
import java.util.Queue;
import me.cortex.voxy.client.core.model.BakedModelPayload;
import me.cortex.voxy.client.core.model.BiomeModelPayload;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.cortex.voxy.client.core.model.ModelOutputSink;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;

public final class MaterialStore implements ModelOutputSink {
  static final int MODEL_GRID_SIZE = 256;
  static final int ATLAS_WIDTH = ModelFactory.MODEL_TEXTURE_SIZE * 3 * MODEL_GRID_SIZE;
  static final int ATLAS_HEIGHT = ModelFactory.MODEL_TEXTURE_SIZE * 2 * MODEL_GRID_SIZE;
  static final int ATLAS_MIP_LEVELS = ModelFactory.LAYERS;
  // TODO(gl41metal): move model atlas uploads through a dedicated Metal staging ring/blit path.
  static final boolean FULL_ATLAS_UPLOADS =
      Boolean.parseBoolean(System.getProperty("voxy.gl41metal.uploadModelAtlas", "true"));

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
            FULL_ATLAS_UPLOADS ? payload.texture().copy() : null,
            payload.renderLayer(),
            payload.fallbackReason(),
            payload.sourceDescription()));
  }

  @Override
  public synchronized void uploadBiomeData(BiomeModelPayload payload) {
    this.pendingBiomes.add(
        new BiomeUpload(payload.biomeColourBuffer().copy(), payload.modelBiomeIndexPairs().copy()));
  }

  synchronized void drainUploads(long nativeHandle) {
    this.drainBiomeUploads(nativeHandle);

    int modelUploads = 0;
    while (modelUploads++ < MAX_MODEL_UPLOADS_PER_FRAME) {
      ModelUpload upload = this.pendingModels.poll();
      if (upload == null) {
        break;
      }
      try {
        int customId = upload.customId();
        NativeBindings.uploadModel(
            nativeHandle,
            upload.modelId,
            upload.model.address,
            upload.model.size,
            upload.texture == null ? 0 : upload.texture.address,
            upload.texture == null ? 0 : upload.texture.size,
            upload.renderLayer,
            upload.fallbackReason);
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

    this.drainBiomeUploads(nativeHandle);
  }

  private void drainBiomeUploads(long nativeHandle) {
    int biomeUploads = 0;
    while (biomeUploads++ < MAX_BIOME_UPLOADS_PER_FRAME) {
      BiomeUpload upload = this.pendingBiomes.poll();
      if (upload == null) {
        break;
      }
      try {
        NativeBindings.uploadBiomeData(
            nativeHandle,
            upload.biomeColours.address,
            upload.biomeColours.size,
            upload.modelBiomePairs.address,
            upload.modelBiomePairs.size);
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
