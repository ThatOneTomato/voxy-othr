package me.cortex.voxy.client.core.rendering.backend.gl41metal.jni;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import me.cortex.voxy.common.Logger;

public final class NativeBindings {
  private static final String RESOURCE_PATH = "/natives/macos/libvoxy_gl41metal.dylib";
  private static final String SHADER_RESOURCE_PATH =
      "/natives/macos/gl41metal/voxy_gl41metal.metallib";
  private static final String DEV_PATH = "build/gl41metal-native/libvoxy_gl41metal.dylib";
  private static final String DEV_SHADER_PATH = "build/gl41metal-shaders/voxy_gl41metal.metallib";
  private static final boolean LOADED;
  private static final String LOAD_FAILURE;
  private static final Path SHADER_LIBRARY_PATH;
  public static final int OUTPUT_MODE_SHARED_GBUFFER = 0;
  public static final int OUTPUT_MODE_DRAWLIST = 1;
  public static final int OPAQUE_DRAW_INSTANCE_BYTES = 48;
  public static final int OPAQUE_DRAW_COUNTERS_BYTES = 4 * Integer.BYTES;

  static {
    String failure = null;
    boolean loaded = false;
    Path shaderLibraryPath = null;
    try {
      loadLibrary();
      shaderLibraryPath = resolveShaderLibrary();
      loaded = true;
    } catch (Throwable e) {
      failure = e.getMessage();
    }
    LOADED = loaded;
    LOAD_FAILURE = failure;
    SHADER_LIBRARY_PATH = shaderLibraryPath;
  }

  private NativeBindings() {}

  public static boolean isLoaded() {
    return LOADED;
  }

  public static String loadFailure() {
    return LOAD_FAILURE == null ? "none" : LOAD_FAILURE;
  }

  private static void loadLibrary() throws IOException {
    try (InputStream stream = NativeBindings.class.getResourceAsStream(RESOURCE_PATH)) {
      if (stream != null) {
        Path extracted = Files.createTempFile("voxy_gl41metal", ".dylib");
        Files.copy(stream, extracted, StandardCopyOption.REPLACE_EXISTING);
        extracted.toFile().deleteOnExit();
        System.load(extracted.toAbsolutePath().toString());
        Logger.info("Loaded Voxy GL41Metal native library from bundled resource");
        return;
      }
    }

    Path devLibrary = Path.of(DEV_PATH);
    if (Files.exists(devLibrary)) {
      System.load(devLibrary.toAbsolutePath().toString());
      Logger.info("Loaded Voxy GL41Metal native library from " + devLibrary);
      return;
    }

    throw new IOException("missing " + RESOURCE_PATH + " and " + DEV_PATH);
  }

  private static Path resolveShaderLibrary() throws IOException {
    try (InputStream stream = NativeBindings.class.getResourceAsStream(SHADER_RESOURCE_PATH)) {
      if (stream != null) {
        Path extracted = Files.createTempFile("voxy_gl41metal", ".metallib");
        Files.copy(stream, extracted, StandardCopyOption.REPLACE_EXISTING);
        extracted.toFile().deleteOnExit();
        Logger.info("Loaded Voxy GL41Metal shader library from bundled resource");
        return extracted.toAbsolutePath();
      }
    }

    Path devLibrary = Path.of(DEV_SHADER_PATH);
    if (Files.exists(devLibrary)) {
      Logger.info("Loaded Voxy GL41Metal shader library from " + devLibrary);
      return devLibrary.toAbsolutePath();
    }

    throw new IOException("missing " + SHADER_RESOURCE_PATH + " and " + DEV_SHADER_PATH);
  }

  public static native String getUnsupportedReason();

  public static native long createContext(
      int slotCount, int width, int height, String shaderLibraryPath);

  public static long createContext(int slotCount, int width, int height) {
    if (SHADER_LIBRARY_PATH == null) {
      throw new IllegalStateException("GL41Metal shader library was not loaded");
    }
    return createContext(slotCount, width, height, SHADER_LIBRARY_PATH.toString());
  }

  public static native void destroyContext(long handle);

  // Resizes the per-slot screen-sized gbuffer/depth textures in place while preserving the same
  // context handle and all terrain/world/atlas resources. Used on viewport resize so distant LOD
  // residency is not wiped and re-streamed.
  public static native void resizeContext(long handle, int width, int height);

  public static native String getDeviceName(long handle);

  public static native int getTextureTarget(long handle);

  public static native double getLastMetalGpuTimeMs(long handle);

  // Per-pass GPU timing (ms) from the most recent completed Metal frame.
  // Returns [traversal, opaqueRaster, ssao, translucent].
  public static native double[] getPerPassGpuTimesMs(long handle);

  // Distant gbuffer is 3 shared RGBA32F textures (see quad_raster.metal QuadFragmentOut):
  // gbuffer0 = uv/tile, gbuffer1 = depth/modelId/customId, gbuffer2 = packed albedo/light/tint
  // and face/flags/coverage. Three is the sampler-budget limit for the Iris bridge program.
  public static native int getGbuffer0Texture(long handle, int slot);

  public static native int getGbuffer1Texture(long handle, int slot);

  public static native int getGbuffer2Texture(long handle, int slot);

  // Translucent distant gbuffer is 3 further shared RGBA32F textures (see quad_raster.metal
  // TranslucentFragmentOut): tgbuffer0/1 carry the front-most translucent surface for strict pack
  // water shading, tgbufferAccum carries the back->front over-blended flat colour + alpha.
  public static native int getTgbuffer0Texture(long handle, int slot);

  public static native int getTgbuffer1Texture(long handle, int slot);

  public static native int getTgbufferAccumTexture(long handle, int slot);

  public static native int acquireFreeSlot(long handle);

  public static native void submitSynthetic(long handle, int slot, long frameId);

  // ssaoMatricesAddress points at 48 contiguous floats (proj, invProj, modelView in JOML
  // column-major order, see SsaoUniformHost); ssaoSteps == 0 disables the distant SSAO pass.
  public static native void submitTraversal(
      long handle,
      int slot,
      long frameId,
      double cameraX,
      double cameraY,
      double cameraZ,
      long traversalMvpAddress,
      long drawMvpAddress,
      float subDivisionSize,
      float earthRadius,
      float nearExclusionRadius,
      float renderDistanceSquared,
      int viewportWidth,
      int viewportHeight,
      long ssaoMatricesAddress,
      int ssaoSteps,
      int outputMode);

  // countersAddress points at four uint32 values:
  // [0]=written instance count, [1]=overflow count, [2]=worklist item count, [3]=accepted quad
  // hint.
  public static native long buildOpaqueInstances(
      long handle, int slot, int capacity, boolean faceGroupCull, long countersAddress);

  // countersAddress points at nine uint64 values:
  // [0]=written merged range count, [1]=overflow range count, [2]=range-covered quad count,
  // [3]=max merged range quads, [4]=worklist item count, [5]=raw face-group range count,
  // [6]=total merged range count before capacity clamp, [7]=visible work item count,
  // [8]=work items whose worklist LOD differs from section metadata detail.
  public static native int buildOpaqueRanges(
      long handle,
      int slot,
      long countsAddress,
      long indicesAddress,
      long baseVerticesAddress,
      int capacity,
      boolean faceGroupCull,
      long countersAddress);

  // Returns:
  // [0]=worklist items, [1]=raw visible face-group ranges, [2]=same-section merged ranges,
  // [3]=quads covered by those ranges, [4]=worklist accepted-quad hint,
  // [5]=max raw range quads, [6]=max merged range quads, [7]=visible work items,
  // [8]=work items whose worklist LOD differs from section metadata detail.
  public static native long[] measureOpaqueRanges(long handle, int slot, boolean faceGroupCull);

  public static native int waitCurrent(long handle, int currentSlot, int timeoutMs);

  // Whether the slot's submit ran the translucent Metal pass. False when no translucent geometry
  // was resident (the tgbuffer textures are stale - neither rastered nor cleared), in which case
  // the GL translucent composite must be skipped for this slot.
  public static native boolean isSlotTranslucentValid(long handle, int slot);

  public static native void discardCurrentSlot(long handle, int slot);

  public static native void releaseSampledSlot(long handle, int slot);

  public static native void createTerrainResources(
      long handle,
      int maxSections,
      long geometryCapacityBytes,
      int maxNodes,
      int maxTraversalQueue,
      int maxTraversalRequests,
      int maxWorklistItems,
      int maxRasterQuads,
      int atlasWidth,
      int atlasHeight,
      int atlasMipLevels,
      int meshBatchSize);

  public static native void clearTerrainResources(long handle);

  public static native void uploadModel(
      long handle,
      int modelId,
      long modelAddress,
      long modelBytes,
      long textureAddress,
      long textureBytes,
      int renderLayer,
      int fallbackReason);

  public static native void uploadBiomeData(
      long handle,
      long colourAddress,
      long colourBytes,
      long modelBiomePairsAddress,
      long modelBiomePairsBytes);

  public static native void uploadSection(
      long handle,
      int sectionId,
      long sectionPos,
      int aabb,
      int childExistence,
      long offsetsAddress,
      long offsetsBytes,
      long geometryAddress,
      long geometryBytes);

  public static native void removeSection(long handle, int sectionId);

  public static native void uploadNode(long handle, int nodeId, long nodeAddress);

  public static native void uploadSectionMetadata(long handle, int sectionId, long metadataAddress);

  public static native void uploadGeometry(
      long handle, int geometryElementOffset, long geometryAddress, long geometryBytes);

  public static native void addTopNode(long handle, int nodeId);

  public static native void removeTopNode(long handle, int nodeId);

  public static native long[] pollTraversalRequests(long handle);

  public static native void clearTraversalWorklist(long handle);

  public static native void validateTerrainResources(long handle);

  public static native long[] getTerrainStats(long handle);
}
