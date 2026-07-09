package me.cortex.voxy.client.core.rendering.backend.gl41metal;

import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_FUNC;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_GEQUAL;
import static org.lwjgl.opengl.GL11C.GL_GREATER;
import static org.lwjgl.opengl.GL11C.GL_LEQUAL;
import static org.lwjgl.opengl.GL11C.GL_LINEAR_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_NONE;
import static org.lwjgl.opengl.GL11C.GL_ONE;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.GL_TRUE;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11C.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glColorMask;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glDepthFunc;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL11C.glGetBooleanv;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glGetIntegerv;
import static org.lwjgl.opengl.GL11C.glIsEnabled;
import static org.lwjgl.opengl.GL11C.glPixelStorei;
import static org.lwjgl.opengl.GL11C.glReadBuffer;
import static org.lwjgl.opengl.GL11C.glTexImage2D;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL11C.glViewport;
import static org.lwjgl.opengl.GL11C.nglTexImage2D;
import static org.lwjgl.opengl.GL11C.nglTexSubImage2D;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_BASE_LEVEL;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LEVEL;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LOD;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_MIN_LOD;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL14C.GL_BLEND_DST_ALPHA;
import static org.lwjgl.opengl.GL14C.GL_BLEND_DST_RGB;
import static org.lwjgl.opengl.GL14C.GL_BLEND_SRC_ALPHA;
import static org.lwjgl.opengl.GL14C.GL_BLEND_SRC_RGB;
import static org.lwjgl.opengl.GL14C.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL14C.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15C.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL15C.GL_QUERY_RESULT_AVAILABLE;
import static org.lwjgl.opengl.GL15C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15C.GL_STREAM_DRAW;
import static org.lwjgl.opengl.GL15C.glBeginQuery;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glBufferData;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL15C.glDeleteQueries;
import static org.lwjgl.opengl.GL15C.glEndQuery;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL15C.glGenQueries;
import static org.lwjgl.opengl.GL15C.glGetQueryObjecti;
import static org.lwjgl.opengl.GL15C.nglBufferSubData;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20C.glGetUniformLocation;
import static org.lwjgl.opengl.GL20C.glUniform1f;
import static org.lwjgl.opengl.GL20C.glUniform1i;
import static org.lwjgl.opengl.GL20C.glUniform2f;
import static org.lwjgl.opengl.GL20C.glUniform3i;
import static org.lwjgl.opengl.GL20C.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_COMPONENT32F;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30C.GL_R32UI;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_RG32UI;
import static org.lwjgl.opengl.GL30C.GL_RGBA32UI;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glBlitFramebuffer;
import static org.lwjgl.opengl.GL30C.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30C.glGenFramebuffers;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL30C.glGetFramebufferAttachmentParameteri;
import static org.lwjgl.opengl.GL30C.glVertexAttribIPointer;
import static org.lwjgl.opengl.GL31C.GL_TEXTURE_BINDING_BUFFER;
import static org.lwjgl.opengl.GL31C.GL_TEXTURE_BUFFER;
import static org.lwjgl.opengl.GL31C.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL31C.glTexBuffer;
import static org.lwjgl.opengl.GL32C.GL_FIRST_VERTEX_CONVENTION;
import static org.lwjgl.opengl.GL32C.glProvokingVertex;
import static org.lwjgl.opengl.GL32C.nglMultiDrawElementsBaseVertex;
import static org.lwjgl.opengl.GL33C.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33C.GL_TIME_ELAPSED;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.opengl.GL33C.glGetQueryObjecti64;
import static org.lwjgl.opengl.GL33C.glVertexAttribDivisor;

import java.nio.FloatBuffer;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.cortex.voxy.client.core.rendering.backend.RenderFrameContext;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge.FogCapture;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.jni.NativeBindings;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.terrain.LoadedVolumeBound;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.terrain.MaterialStore;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.terrain.TerrainResources;
import me.cortex.voxy.client.core.rendering.util.LightMapHelper;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

final class DrawlistOpaqueRenderer
    implements MaterialStore.AtlasMirror, TerrainResources.DrawlistMirror, AutoCloseable {
  private static final int MODEL_GRID_SIZE = 256;
  private static final int MODEL_COUNT = 1 << 16;
  private static final int MODEL_BYTES = 64;
  private static final int SECTION_METADATA_BYTES = 32;
  private static final int ATLAS_WIDTH = ModelFactory.MODEL_TEXTURE_SIZE * 3 * MODEL_GRID_SIZE;
  private static final int ATLAS_HEIGHT = ModelFactory.MODEL_TEXTURE_SIZE * 2 * MODEL_GRID_SIZE;
  private static final int ATLAS_MIP_LEVELS = ModelFactory.LAYERS;
  private static final boolean USE_DRAW_RANGES =
      Boolean.parseBoolean(System.getProperty("voxy.gl41metal.drawlistRanges", "true"));
  private static final boolean USE_FACE_GROUP_CULL =
      Boolean.parseBoolean(System.getProperty("voxy.gl41metal.drawlistFaceGroupCull", "false"));
  private static final boolean USE_TERRAIN_MIRROR = USE_DRAW_RANGES;
  private static final boolean MEASURE_DRAW_RANGES =
      !USE_DRAW_RANGES
          && Boolean.parseBoolean(System.getProperty("voxy.gl41metal.measureDrawRanges", "true"));
  private static final int STREAM_STRIDE = NativeBindings.OPAQUE_DRAW_INSTANCE_BYTES;
  private static final int BLOCK_ATLAS_UNIT = 0;
  private static final int LIGHTMAP_UNIT = 1;
  private static final int GEOMETRY_BUFFER_UNIT = 2;
  private static final int SECTION_META_UNIT = 3;
  private static final int MODEL_BUFFER_UNIT = 4;
  private static final int MODEL_COLOUR_UNIT = 5;
  private static final int QUAD_SECTION_UNIT = 6;
  private static final int NEAR_DEPTH_UNIT = 7;
  private static final int BOUND_DEPTH_UNIT = 8;
  private static final boolean ENABLE_DRAW_GPU_TIMER =
      Boolean.parseBoolean(System.getProperty("voxy.gl41metal.drawGpuTimer", "true"));
  private static final int STREAM_BUFFER_COUNT =
      readInt("voxy.gl41metal.drawlistStreamBuffers", 3, 1, 8);
  private static final int MAX_INSTANCE_LIMIT =
      readInt("voxy.gl41metal.drawlistMaxInstances", 8_000_000, 1024, 32_000_000);
  private static final int INITIAL_INSTANCE_CAPACITY =
      readInt(
          "voxy.gl41metal.drawlistInitialInstances",
          Math.min(1_000_000, MAX_INSTANCE_LIMIT),
          1024,
          MAX_INSTANCE_LIMIT);
  private static final int MAX_RANGE_LIMIT =
      readInt("voxy.gl41metal.drawlistMaxRanges", 1_000_000, 1024, 4_000_000);
  private static final int INITIAL_RANGE_CAPACITY =
      readInt(
          "voxy.gl41metal.drawlistInitialRanges",
          Math.min(32_768, MAX_RANGE_LIMIT),
          1024,
          MAX_RANGE_LIMIT);
  private static final int INITIAL_RANGE_INDEX_QUADS =
      readInt("voxy.gl41metal.drawlistRangeIndexQuads", 262_144, 1024, 8_000_000);

  private final Shader shader;
  private final Shader translucentShader;
  private final int voxyMvpUniform;
  private final int vanillaMvpUniform;
  private final int earthRadiusUniform;
  private final int blockAtlasUniform;
  private final int lightmapUniform;
  private final int baseSectionFrameUniform;
  private final int geometryQuadsUniform;
  private final int sectionMetaUniform;
  private final int modelBufferUniform;
  private final int modelColourUniform;
  private final int quadSectionIdsUniform;
  private final int fogParamsUniform;
  private final int fogColorUniform;
  private final int fogShapeUniform;
  private final int nearDepthUniform;
  private final int nearDepthSizeUniform;
  private final int useNearDepthMaskUniform;
  private final int reverseDepthUniform;
  private final int translucentVoxyMvpUniform;
  private final int translucentVanillaMvpUniform;
  private final int translucentEarthRadiusUniform;
  private final int translucentBlockAtlasUniform;
  private final int translucentLightmapUniform;
  private final int translucentBaseSectionFrameUniform;
  private final int translucentGeometryQuadsUniform;
  private final int translucentSectionMetaUniform;
  private final int translucentModelBufferUniform;
  private final int translucentModelColourUniform;
  private final int translucentQuadSectionIdsUniform;
  private final int translucentFogParamsUniform;
  private final int translucentFogColorUniform;
  private final int translucentFogShapeUniform;
  private final int translucentBoundDepthUniform;
  private final int translucentBoundSizeUniform;
  private final int translucentBoundEnabledUniform;
  private final int[] vaos = new int[STREAM_BUFFER_COUNT];
  private final int[] instanceBuffers = new int[STREAM_BUFFER_COUNT];
  private final int rangeVao;
  private final int indexBuffer;
  private final int rangeIndexBuffer;
  private final int depthCopyFramebuffer;
  private final int nearDepthFramebuffer;
  private final int atlasTexture;
  private final int geometryBuffer;
  private final int sectionMetaBuffer;
  private final int modelBuffer;
  private final int modelColourBuffer;
  private final int quadSectionBuffer;
  private final int geometryTexture;
  private final int sectionMetaTexture;
  private final int modelTexture;
  private final int modelColourTexture;
  private final int quadSectionTexture;
  private final GpuTimer drawGpuTimer = new GpuTimer();
  private final int maxSections;
  private final long geometryCapacityBytes;
  private int instanceCapacity = INITIAL_INSTANCE_CAPACITY;
  private int rangeCapacity = INITIAL_RANGE_CAPACITY;
  private int rangeIndexQuadCapacity = INITIAL_RANGE_INDEX_QUADS;
  private int nextStreamBuffer;
  private int currentStreamBuffer;
  private MemoryBuffer rangeCommandBuffer;
  private MemoryBuffer quadSectionScratch;
  private int quadSectionScratchCapacity;
  private int nearDepthTexture;
  private int nearDepthWidth;
  private int nearDepthHeight;
  private boolean closed;
  private boolean loggedFirstDraw;
  private boolean loggedOverflow;
  private boolean loggedLodMismatch;
  private boolean loggedNearDepthFallback;

  DrawlistOpaqueRenderer(int maxSections, long geometryCapacityBytes) {
    this.maxSections = maxSections;
    this.geometryCapacityBytes = geometryCapacityBytes;
    var shaderBuilder = Shader.make();
    if (USE_DRAW_RANGES) {
      shaderBuilder.addSource(
          ShaderType.VERTEX,
          ShaderLoader.parse("voxy:lod/gl41metal/drawlist/opaque_ranges.vert", "410 core"));
    } else {
      shaderBuilder.addSource(
          ShaderType.VERTEX,
          ShaderLoader.parse("voxy:lod/gl41metal/drawlist/opaque.vert", "410 core"));
    }
    this.shader =
        shaderBuilder
            .addSource(
                ShaderType.FRAGMENT,
                ShaderLoader.parse("voxy:lod/gl41metal/drawlist/opaque.frag", "410 core"))
            .compile()
            .name("Voxy GL41Metal Drawlist Opaque");
    this.voxyMvpUniform = glGetUniformLocation(this.shader.id(), "uVoxyMvp");
    this.vanillaMvpUniform = glGetUniformLocation(this.shader.id(), "uVanillaMvp");
    this.earthRadiusUniform = glGetUniformLocation(this.shader.id(), "uEarthRadius");
    this.blockAtlasUniform = glGetUniformLocation(this.shader.id(), "uBlockModelAtlas");
    this.lightmapUniform = glGetUniformLocation(this.shader.id(), "uLightmapTex");
    this.baseSectionFrameUniform = glGetUniformLocation(this.shader.id(), "uBaseSectionFrame");
    this.geometryQuadsUniform = glGetUniformLocation(this.shader.id(), "uGeometryQuads");
    this.sectionMetaUniform = glGetUniformLocation(this.shader.id(), "uSectionMeta");
    this.modelBufferUniform = glGetUniformLocation(this.shader.id(), "uModelBuffer");
    this.modelColourUniform = glGetUniformLocation(this.shader.id(), "uModelColours");
    this.quadSectionIdsUniform = glGetUniformLocation(this.shader.id(), "uQuadSectionIds");
    this.fogParamsUniform = glGetUniformLocation(this.shader.id(), "uFogParams");
    this.fogColorUniform = glGetUniformLocation(this.shader.id(), "uFogColor");
    this.fogShapeUniform = glGetUniformLocation(this.shader.id(), "uFogShape");
    this.nearDepthUniform = glGetUniformLocation(this.shader.id(), "uNearDepthTex");
    this.nearDepthSizeUniform = glGetUniformLocation(this.shader.id(), "uNearDepthSize");
    this.useNearDepthMaskUniform = glGetUniformLocation(this.shader.id(), "uUseNearDepthMask");
    this.reverseDepthUniform = glGetUniformLocation(this.shader.id(), "uReverseDepth");
    if (USE_DRAW_RANGES) {
      this.translucentShader =
          Shader.make()
              .addSource(
                  ShaderType.VERTEX,
                  ShaderLoader.parse(
                      "voxy:lod/gl41metal/drawlist/translucent_ranges.vert", "410 core"))
              .addSource(
                  ShaderType.FRAGMENT,
                  ShaderLoader.parse("voxy:lod/gl41metal/drawlist/translucent.frag", "410 core"))
              .compile()
              .name("Voxy GL41Metal Drawlist Translucent");
    } else {
      this.translucentShader = null;
    }
    int translucentProgram = this.translucentShader != null ? this.translucentShader.id() : 0;
    this.translucentVoxyMvpUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uVoxyMvp") : -1;
    this.translucentVanillaMvpUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uVanillaMvp") : -1;
    this.translucentEarthRadiusUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uEarthRadius") : -1;
    this.translucentBlockAtlasUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uBlockModelAtlas") : -1;
    this.translucentLightmapUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uLightmapTex") : -1;
    this.translucentBaseSectionFrameUniform =
        translucentProgram != 0
            ? glGetUniformLocation(translucentProgram, "uBaseSectionFrame")
            : -1;
    this.translucentGeometryQuadsUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uGeometryQuads") : -1;
    this.translucentSectionMetaUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uSectionMeta") : -1;
    this.translucentModelBufferUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uModelBuffer") : -1;
    this.translucentModelColourUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uModelColours") : -1;
    this.translucentQuadSectionIdsUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uQuadSectionIds") : -1;
    this.translucentFogParamsUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uFogParams") : -1;
    this.translucentFogColorUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uFogColor") : -1;
    this.translucentFogShapeUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uFogShape") : -1;
    this.translucentBoundDepthUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uBoundDepthTex") : -1;
    this.translucentBoundSizeUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uBoundSize") : -1;
    this.translucentBoundEnabledUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uBoundEnabled") : -1;

    this.rangeVao = glGenVertexArrays();
    this.indexBuffer = glGenBuffers();
    this.rangeIndexBuffer = glGenBuffers();
    this.depthCopyFramebuffer = glGenFramebuffers();
    this.nearDepthFramebuffer = glGenFramebuffers();
    this.atlasTexture = glGenTextures();
    this.geometryBuffer = glGenBuffers();
    this.sectionMetaBuffer = glGenBuffers();
    this.modelBuffer = glGenBuffers();
    this.modelColourBuffer = glGenBuffers();
    this.quadSectionBuffer = glGenBuffers();
    this.geometryTexture = glGenTextures();
    this.sectionMetaTexture = glGenTextures();
    this.modelTexture = glGenTextures();
    this.modelColourTexture = glGenTextures();
    this.quadSectionTexture = glGenTextures();
    this.initBuffers();
    this.initTerrainMirror();
    this.initRangeCommands();
    this.initAtlas();
    Logger.info(
        "Voxy GL41Metal drawlist opaque renderer initialized: maxInstances="
            + MAX_INSTANCE_LIMIT
            + ", initialInstances="
            + this.instanceCapacity
            + ", instanceBytes="
            + ((long) this.instanceCapacity * STREAM_STRIDE)
            + ", streamBuffers="
            + STREAM_BUFFER_COUNT
            + ", ranges="
            + USE_DRAW_RANGES
            + ", faceGroupCull="
            + USE_FACE_GROUP_CULL
            + ", mirrorGeometryBytes="
            + this.geometryCapacityBytes
            + ", atlas="
            + ATLAS_WIDTH
            + "x"
            + ATLAS_HEIGHT
            + " mips="
            + ATLAS_MIP_LEVELS);
  }

  boolean render(
      long nativeHandle,
      int slot,
      RenderFrameContext context,
      Matrix4fc drawMvp,
      Matrix4fc vanillaDrawMvp,
      boolean colorWriteEnabled,
      FrameProfiler profiler) {
    if (FogCapture.vanillaFogHidesDistant()) {
      return false;
    }
    StateSnapshot state = StateSnapshot.capture();
    try (MemoryStack stack = MemoryStack.stackPush()) {
      if (USE_DRAW_RANGES) {
        return this.renderRanges(
            nativeHandle,
            slot,
            context,
            drawMvp,
            vanillaDrawMvp,
            colorWriteEnabled,
            profiler,
            stack);
      }
      if (MEASURE_DRAW_RANGES) {
        long tRange = profiler.begin();
        profiler.recordDrawlistRangeMeasure(
            tRange, NativeBindings.measureOpaqueRanges(nativeHandle, slot, USE_FACE_GROUP_CULL));
      }
      long tBuild = profiler.begin();
      var counters = stack.mallocInt(4);
      MemoryUtil.memSet(
          MemoryUtil.memAddress(counters), 0, NativeBindings.OPAQUE_DRAW_COUNTERS_BYTES);
      long instanceAddress =
          this.buildDrawStream(nativeHandle, slot, MemoryUtil.memAddress(counters));
      int instanceCount = counters.get(0);
      int overflowCount = counters.get(1);
      profiler.recordDrawlistBuild(tBuild, instanceCount, overflowCount);
      if (overflowCount > 0) {
        int oldCapacity = this.instanceCapacity;
        this.growInstanceCapacity((long) instanceCount + overflowCount);
        if (!this.loggedOverflow) {
          this.loggedOverflow = true;
          Logger.warn(
              "Voxy GL41Metal drawlist overflowed "
                  + oldCapacity
                  + " instances; grew to "
                  + this.instanceCapacity
                  + " and skipped this frame");
        }
        return false;
      }
      if (instanceCount <= 0) {
        return false;
      }
      if (instanceAddress == 0) {
        profiler.recordDrawlistUpload(profiler.begin());
        return false;
      }
      long tUpload = profiler.begin();
      long uploadBytes = (long) instanceCount * STREAM_STRIDE;
      int streamBuffer = this.acquireStreamBuffer();
      glBindBuffer(GL_ARRAY_BUFFER, this.instanceBuffers[streamBuffer]);
      nglBufferSubData(GL_ARRAY_BUFFER, 0, uploadBytes, instanceAddress);
      profiler.recordDrawlistUpload(tUpload);

      long tRaster = profiler.begin();
      this.drawInstances(
          context, drawMvp, vanillaDrawMvp, colorWriteEnabled, instanceCount, stack, profiler);
      profiler.recordDrawlistOpaqueRaster(tRaster);
      if (!this.loggedFirstDraw) {
        this.loggedFirstDraw = true;
        Logger.info("Voxy GL41Metal drawlist rendered first opaque direct-GL frame");
      }
      return true;
    } finally {
      state.restore();
    }
  }

  private boolean renderRanges(
      long nativeHandle,
      int slot,
      RenderFrameContext context,
      Matrix4fc drawMvp,
      Matrix4fc vanillaDrawMvp,
      boolean colorWriteEnabled,
      FrameProfiler profiler,
      MemoryStack stack) {
    long tBuild = profiler.begin();
    this.ensureRangeCommandBuffer(this.rangeCapacity);
    var counters = stack.mallocLong(9);
    MemoryUtil.memSet(MemoryUtil.memAddress(counters), 0, 9L * Long.BYTES);
    int rangeCount =
        NativeBindings.buildOpaqueRanges(
            nativeHandle,
            slot,
            this.rangeCountsAddress(),
            this.rangeIndicesAddress(),
            this.rangeBaseVerticesAddress(),
            this.rangeCapacity,
            USE_FACE_GROUP_CULL,
            MemoryUtil.memAddress(counters));
    long rangeQuads = counters.get(2);
    long overflowRanges = counters.get(1);
    profiler.recordDrawlistBuild(tBuild, rangeQuads, overflowRanges);
    profiler.recordDrawlistRangeStats(rangeStatsFromCounters(counters));
    long lodMismatches = counters.get(8);
    if (lodMismatches > 0 && !this.loggedLodMismatch) {
      this.loggedLodMismatch = true;
      Logger.warn(
          "Voxy GL41Metal drawlist ranges saw "
              + lodMismatches
              + " work items whose worklist LOD differs from section metadata detail");
    }
    if (overflowRanges > 0) {
      int oldCapacity = this.rangeCapacity;
      this.growRangeCapacity(rangeCount + overflowRanges);
      Logger.warn(
          "Voxy GL41Metal drawlist ranges overflowed "
              + oldCapacity
              + " ranges; grew to "
              + this.rangeCapacity
              + " and skipped this frame");
      return false;
    }
    if (rangeCount <= 0 || rangeQuads <= 0) {
      return false;
    }
    long maxRangeQuads = counters.get(3);
    if (maxRangeQuads > this.rangeIndexQuadCapacity) {
      this.growRangeIndexCapacity(maxRangeQuads);
      Logger.warn(
          "Voxy GL41Metal drawlist range index buffer grew to "
              + this.rangeIndexQuadCapacity
              + " quads; skipped this frame");
      return false;
    }

    profiler.recordDrawlistUpload(profiler.begin());
    long tRaster = profiler.begin();
    this.drawRanges(
        context, drawMvp, vanillaDrawMvp, colorWriteEnabled, rangeCount, stack, profiler);
    profiler.recordDrawlistOpaqueRaster(tRaster);
    if (!this.loggedFirstDraw) {
      this.loggedFirstDraw = true;
      Logger.info("Voxy GL41Metal drawlist rendered first opaque direct-GL range frame");
    }
    return true;
  }

  private static long[] rangeStatsFromCounters(java.nio.LongBuffer counters) {
    return new long[] {
      counters.get(4),
      counters.get(5),
      counters.get(6),
      counters.get(2),
      0,
      0,
      counters.get(3),
      counters.get(7)
    };
  }

  boolean renderTranslucent(
      long nativeHandle,
      int slot,
      RenderFrameContext context,
      Matrix4fc drawMvp,
      Matrix4fc vanillaDrawMvp,
      LoadedVolumeBound bound,
      boolean colorWriteEnabled,
      FrameProfiler profiler) {
    if (!USE_DRAW_RANGES || this.translucentShader == null) {
      return false;
    }
    if (FogCapture.vanillaFogHidesDistant()) {
      return false;
    }
    StateSnapshot state = StateSnapshot.capture();
    try (MemoryStack stack = MemoryStack.stackPush()) {
      long tBuild = profiler.begin();
      this.ensureRangeCommandBuffer(this.rangeCapacity);
      var counters = stack.mallocLong(7);
      MemoryUtil.memSet(MemoryUtil.memAddress(counters), 0, 7L * Long.BYTES);
      int rangeCount =
          NativeBindings.buildTranslucentRanges(
              nativeHandle,
              slot,
              this.rangeCountsAddress(),
              this.rangeIndicesAddress(),
              this.rangeBaseVerticesAddress(),
              this.rangeCapacity,
              MemoryUtil.memAddress(counters));
      long rangeQuads = counters.get(2);
      long overflowRanges = counters.get(1);
      profiler.recordDrawlistBuild(tBuild, rangeQuads, overflowRanges);
      profiler.recordDrawlistRangeStats(translucentRangeStatsFromCounters(counters));
      if (overflowRanges > 0) {
        int oldCapacity = this.rangeCapacity;
        this.growRangeCapacity(rangeCount + overflowRanges);
        Logger.warn(
            "Voxy GL41Metal translucent drawlist ranges overflowed "
                + oldCapacity
                + " ranges; grew to "
                + this.rangeCapacity
                + " and skipped this frame");
        return false;
      }
      if (rangeCount <= 0 || rangeQuads <= 0) {
        return false;
      }
      long maxRangeQuads = counters.get(5);
      if (maxRangeQuads > this.rangeIndexQuadCapacity) {
        this.growRangeIndexCapacity(maxRangeQuads);
        Logger.warn(
            "Voxy GL41Metal translucent range index buffer grew to "
                + this.rangeIndexQuadCapacity
                + " quads; skipped this frame");
        return false;
      }

      this.drawTranslucentRanges(
          context, drawMvp, vanillaDrawMvp, bound, colorWriteEnabled, rangeCount, stack, profiler);
      return true;
    } finally {
      state.restore();
    }
  }

  private static long[] translucentRangeStatsFromCounters(java.nio.LongBuffer counters) {
    return new long[] {
      counters.get(3),
      counters.get(4),
      counters.get(4),
      counters.get(2),
      0,
      0,
      counters.get(5),
      counters.get(6)
    };
  }

  private long buildDrawStream(long nativeHandle, int slot, long countersAddress) {
    return NativeBindings.buildOpaqueInstances(
        nativeHandle, slot, this.instanceCapacity, USE_FACE_GROUP_CULL, countersAddress);
  }

  @Override
  public void uploadSectionMetadata(int sectionId, long metadataAddress) {
    if (!USE_TERRAIN_MIRROR
        || sectionId < 0
        || sectionId >= this.maxSections
        || metadataAddress == 0) {
      return;
    }
    glBindBuffer(GL_TEXTURE_BUFFER, this.sectionMetaBuffer);
    nglBufferSubData(
        GL_TEXTURE_BUFFER,
        (long) sectionId * SECTION_METADATA_BYTES,
        SECTION_METADATA_BYTES,
        metadataAddress);
    if (USE_DRAW_RANGES) {
      this.uploadQuadSectionIds(sectionId, metadataAddress);
    }
  }

  @Override
  public void uploadGeometry(int geometryElementOffset, long geometryAddress, long geometryBytes) {
    if (!USE_TERRAIN_MIRROR || geometryAddress == 0 || geometryBytes <= 0) {
      return;
    }
    long byteOffset = (long) geometryElementOffset * Long.BYTES;
    if (byteOffset < 0
        || byteOffset > this.geometryCapacityBytes
        || geometryBytes > this.geometryCapacityBytes - byteOffset) {
      Logger.warn("GL41Metal drawlist mirror skipped out-of-range geometry upload");
      return;
    }
    glBindBuffer(GL_TEXTURE_BUFFER, this.geometryBuffer);
    nglBufferSubData(GL_TEXTURE_BUFFER, byteOffset, geometryBytes, geometryAddress);
  }

  @Override
  public void uploadModelData(int modelId, long modelAddress, long modelBytes) {
    if (!USE_TERRAIN_MIRROR || modelId < 0 || modelId >= MODEL_COUNT || modelAddress == 0) {
      return;
    }
    if (modelBytes < MODEL_BYTES) {
      Logger.warn("GL41Metal drawlist mirror skipped truncated model upload");
      return;
    }
    glBindBuffer(GL_TEXTURE_BUFFER, this.modelBuffer);
    nglBufferSubData(GL_TEXTURE_BUFFER, (long) modelId * MODEL_BYTES, MODEL_BYTES, modelAddress);
  }

  @Override
  public void uploadBiomeData(
      long colourAddress,
      long colourBytes,
      long modelBiomePairsAddress,
      long modelBiomePairsBytes) {
    if (!USE_TERRAIN_MIRROR || modelBiomePairsAddress == 0 || modelBiomePairsBytes <= 0) {
      return;
    }
    if (colourAddress != 0 && colourBytes > 0) {
      int minBiomeBase = Integer.MAX_VALUE;
      long pairCount = modelBiomePairsBytes / Long.BYTES;
      for (long i = 0; i < pairCount; i++) {
        long pair = MemoryUtil.memGetLong(modelBiomePairsAddress + i * Long.BYTES);
        int biomeBase = (int) (pair >>> 32);
        minBiomeBase = Math.min(minBiomeBase, biomeBase);
      }
      if (minBiomeBase != Integer.MAX_VALUE
          && minBiomeBase >= 0
          && colourBytes <= ((long) MODEL_COUNT - minBiomeBase) * Integer.BYTES) {
        glBindBuffer(GL_TEXTURE_BUFFER, this.modelColourBuffer);
        nglBufferSubData(
            GL_TEXTURE_BUFFER, (long) minBiomeBase * Integer.BYTES, colourBytes, colourAddress);
      }
    }
    long pairCount = modelBiomePairsBytes / Long.BYTES;
    glBindBuffer(GL_TEXTURE_BUFFER, this.modelBuffer);
    try (MemoryStack stack = MemoryStack.stackPush()) {
      var biomeBaseValue = stack.mallocInt(1);
      long biomeBaseAddress = MemoryUtil.memAddress(biomeBaseValue);
      for (long i = 0; i < pairCount; i++) {
        long pair = MemoryUtil.memGetLong(modelBiomePairsAddress + i * Long.BYTES);
        int modelId = (int) pair;
        int biomeBase = (int) (pair >>> 32);
        if (modelId < 0 || modelId >= MODEL_COUNT) {
          continue;
        }
        biomeBaseValue.put(0, biomeBase);
        nglBufferSubData(
            GL_TEXTURE_BUFFER, (long) modelId * MODEL_BYTES + 28L, Integer.BYTES, biomeBaseAddress);
      }
    }
  }

  @Override
  public void uploadModelTexture(int modelId, long textureAddress, long textureBytes) {
    if (textureAddress == 0 || textureBytes <= 0) {
      return;
    }
    StateSnapshot state = StateSnapshot.capture();
    try {
      glActiveTexture(GL_TEXTURE0 + BLOCK_ATLAS_UNIT);
      glBindTexture(GL_TEXTURE_2D, this.atlasTexture);
      int oldAlignment = glGetInteger(org.lwjgl.opengl.GL11C.GL_UNPACK_ALIGNMENT);
      int oldRowLength = glGetInteger(org.lwjgl.opengl.GL11C.GL_UNPACK_ROW_LENGTH);
      int oldSkipRows = glGetInteger(org.lwjgl.opengl.GL11C.GL_UNPACK_SKIP_ROWS);
      int oldSkipPixels = glGetInteger(org.lwjgl.opengl.GL11C.GL_UNPACK_SKIP_PIXELS);
      glPixelStorei(org.lwjgl.opengl.GL11C.GL_UNPACK_ALIGNMENT, 4);
      glPixelStorei(org.lwjgl.opengl.GL11C.GL_UNPACK_ROW_LENGTH, 0);
      glPixelStorei(org.lwjgl.opengl.GL11C.GL_UNPACK_SKIP_ROWS, 0);
      glPixelStorei(org.lwjgl.opengl.GL11C.GL_UNPACK_SKIP_PIXELS, 0);
      long address = textureAddress;
      long end = textureAddress + textureBytes;
      int atlasX = (modelId & 0xff) * ModelFactory.MODEL_TEXTURE_SIZE * 3;
      int atlasY = ((modelId >> 8) & 0xff) * ModelFactory.MODEL_TEXTURE_SIZE * 2;
      for (int mip = 0; mip < ATLAS_MIP_LEVELS; mip++) {
        int width = (ModelFactory.MODEL_TEXTURE_SIZE * 3) >> mip;
        int height = (ModelFactory.MODEL_TEXTURE_SIZE * 2) >> mip;
        long mipBytes = (long) width * height * Integer.BYTES;
        if (address + mipBytes > end) {
          Logger.warn("GL41Metal drawlist atlas upload received truncated texture payload");
          break;
        }
        nglTexSubImage2D(
            GL_TEXTURE_2D,
            mip,
            atlasX >> mip,
            atlasY >> mip,
            width,
            height,
            GL_RGBA,
            GL_UNSIGNED_BYTE,
            address);
        address += mipBytes;
      }
      glPixelStorei(org.lwjgl.opengl.GL11C.GL_UNPACK_ALIGNMENT, oldAlignment);
      glPixelStorei(org.lwjgl.opengl.GL11C.GL_UNPACK_ROW_LENGTH, oldRowLength);
      glPixelStorei(org.lwjgl.opengl.GL11C.GL_UNPACK_SKIP_ROWS, oldSkipRows);
      glPixelStorei(org.lwjgl.opengl.GL11C.GL_UNPACK_SKIP_PIXELS, oldSkipPixels);
    } finally {
      state.restore();
    }
  }

  @Override
  public void close() {
    if (this.closed) {
      return;
    }
    this.closed = true;
    this.shader.free();
    if (this.translucentShader != null) {
      this.translucentShader.free();
    }
    for (int vao : this.vaos) {
      glDeleteVertexArrays(vao);
    }
    glDeleteVertexArrays(this.rangeVao);
    for (int buffer : this.instanceBuffers) {
      glDeleteBuffers(buffer);
    }
    glDeleteBuffers(this.indexBuffer);
    glDeleteBuffers(this.rangeIndexBuffer);
    glDeleteBuffers(this.geometryBuffer);
    glDeleteBuffers(this.sectionMetaBuffer);
    glDeleteBuffers(this.modelBuffer);
    glDeleteBuffers(this.modelColourBuffer);
    glDeleteBuffers(this.quadSectionBuffer);
    glDeleteTextures(this.atlasTexture);
    if (this.nearDepthTexture != 0) {
      glDeleteTextures(this.nearDepthTexture);
      this.nearDepthTexture = 0;
    }
    glDeleteTextures(this.geometryTexture);
    glDeleteTextures(this.sectionMetaTexture);
    glDeleteTextures(this.modelTexture);
    glDeleteTextures(this.modelColourTexture);
    glDeleteTextures(this.quadSectionTexture);
    glDeleteFramebuffers(this.depthCopyFramebuffer);
    glDeleteFramebuffers(this.nearDepthFramebuffer);
    if (this.rangeCommandBuffer != null) {
      this.rangeCommandBuffer.free();
      this.rangeCommandBuffer = null;
    }
    if (this.quadSectionScratch != null) {
      this.quadSectionScratch.free();
      this.quadSectionScratch = null;
    }
    this.drawGpuTimer.close();
  }

  private void drawInstances(
      RenderFrameContext context,
      Matrix4fc drawMvp,
      Matrix4fc vanillaDrawMvp,
      boolean colorWriteEnabled,
      int instanceCount,
      MemoryStack stack,
      FrameProfiler profiler) {
    int previousDepthFunc = glGetInteger(GL_DEPTH_FUNC);
    boolean reverseDepth = previousDepthFunc == GL_GEQUAL || previousDepthFunc == GL_GREATER;
    int nearDepthTexture = this.snapshotSourceDepth(context);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, context.sourceFramebuffer());
    glViewport(0, 0, context.viewportWidth(), context.viewportHeight());
    glColorMask(colorWriteEnabled, colorWriteEnabled, colorWriteEnabled, colorWriteEnabled);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(reverseDepth ? GL_GEQUAL : GL_LEQUAL);
    glDepthMask(true);
    glDisable(GL_BLEND);
    glDisable(GL_CULL_FACE);

    this.shader.bind();
    FloatBuffer matrix = stack.mallocFloat(16);
    drawMvp.get(matrix);
    glUniformMatrix4fv(this.voxyMvpUniform, false, matrix);
    matrix.clear();
    vanillaDrawMvp.get(matrix);
    glUniformMatrix4fv(this.vanillaMvpUniform, false, matrix);
    glUniform1f(this.earthRadiusUniform, DistantRenderer.computeEarthRadius());
    glUniform1i(this.blockAtlasUniform, BLOCK_ATLAS_UNIT);
    glUniform1i(this.lightmapUniform, LIGHTMAP_UNIT);
    if (USE_DRAW_RANGES) {
      glUniform3i(
          this.baseSectionFrameUniform,
          floorSection(context.cameraX()),
          floorSection(context.cameraY()),
          floorSection(context.cameraZ()));
      glUniform1i(this.geometryQuadsUniform, GEOMETRY_BUFFER_UNIT);
      glUniform1i(this.sectionMetaUniform, SECTION_META_UNIT);
      glUniform1i(this.modelBufferUniform, MODEL_BUFFER_UNIT);
      glUniform1i(this.modelColourUniform, MODEL_COLOUR_UNIT);
      glUniform1i(this.quadSectionIdsUniform, QUAD_SECTION_UNIT);
    }
    FogCapture.setVanillaFogUniforms(
        this.fogParamsUniform, this.fogColorUniform, this.fogShapeUniform);
    this.setNearDepthUniforms(context, nearDepthTexture, reverseDepth);

    glActiveTexture(GL_TEXTURE0 + BLOCK_ATLAS_UNIT);
    glBindTexture(GL_TEXTURE_2D, this.atlasTexture);
    glBindSampler(BLOCK_ATLAS_UNIT, 0);
    LightMapHelper.bind(LIGHTMAP_UNIT);
    if (USE_DRAW_RANGES) {
      glActiveTexture(GL_TEXTURE0 + GEOMETRY_BUFFER_UNIT);
      glBindTexture(GL_TEXTURE_BUFFER, this.geometryTexture);
      glBindSampler(GEOMETRY_BUFFER_UNIT, 0);
      glActiveTexture(GL_TEXTURE0 + SECTION_META_UNIT);
      glBindTexture(GL_TEXTURE_BUFFER, this.sectionMetaTexture);
      glBindSampler(SECTION_META_UNIT, 0);
      glActiveTexture(GL_TEXTURE0 + MODEL_BUFFER_UNIT);
      glBindTexture(GL_TEXTURE_BUFFER, this.modelTexture);
      glBindSampler(MODEL_BUFFER_UNIT, 0);
      glActiveTexture(GL_TEXTURE0 + MODEL_COLOUR_UNIT);
      glBindTexture(GL_TEXTURE_BUFFER, this.modelColourTexture);
      glBindSampler(MODEL_COLOUR_UNIT, 0);
      glActiveTexture(GL_TEXTURE0 + QUAD_SECTION_UNIT);
      glBindTexture(GL_TEXTURE_BUFFER, this.quadSectionTexture);
      glBindSampler(QUAD_SECTION_UNIT, 0);
    }
    this.bindNearDepthTexture(nearDepthTexture);

    glBindVertexArray(this.vaos[this.currentStreamBuffer]);
    glProvokingVertex(GL_FIRST_VERTEX_CONVENTION);
    this.drawGpuTimer.poll(profiler);
    this.drawGpuTimer.begin();
    glDrawElementsInstanced(GL_TRIANGLES, 6, GL_UNSIGNED_BYTE, 0L, instanceCount);
    this.drawGpuTimer.end();
  }

  private void drawRanges(
      RenderFrameContext context,
      Matrix4fc drawMvp,
      Matrix4fc vanillaDrawMvp,
      boolean colorWriteEnabled,
      int rangeCount,
      MemoryStack stack,
      FrameProfiler profiler) {
    int previousDepthFunc = glGetInteger(GL_DEPTH_FUNC);
    boolean reverseDepth = previousDepthFunc == GL_GEQUAL || previousDepthFunc == GL_GREATER;
    int nearDepthTexture = this.snapshotSourceDepth(context);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, context.sourceFramebuffer());
    glViewport(0, 0, context.viewportWidth(), context.viewportHeight());
    glColorMask(colorWriteEnabled, colorWriteEnabled, colorWriteEnabled, colorWriteEnabled);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(reverseDepth ? GL_GEQUAL : GL_LEQUAL);
    glDepthMask(true);
    glDisable(GL_BLEND);
    glDisable(GL_CULL_FACE);

    this.shader.bind();
    FloatBuffer matrix = stack.mallocFloat(16);
    drawMvp.get(matrix);
    glUniformMatrix4fv(this.voxyMvpUniform, false, matrix);
    matrix.clear();
    vanillaDrawMvp.get(matrix);
    glUniformMatrix4fv(this.vanillaMvpUniform, false, matrix);
    glUniform1f(this.earthRadiusUniform, DistantRenderer.computeEarthRadius());
    glUniform1i(this.blockAtlasUniform, BLOCK_ATLAS_UNIT);
    glUniform1i(this.lightmapUniform, LIGHTMAP_UNIT);
    glUniform3i(
        this.baseSectionFrameUniform,
        floorSection(context.cameraX()),
        floorSection(context.cameraY()),
        floorSection(context.cameraZ()));
    glUniform1i(this.geometryQuadsUniform, GEOMETRY_BUFFER_UNIT);
    glUniform1i(this.sectionMetaUniform, SECTION_META_UNIT);
    glUniform1i(this.modelBufferUniform, MODEL_BUFFER_UNIT);
    glUniform1i(this.modelColourUniform, MODEL_COLOUR_UNIT);
    glUniform1i(this.quadSectionIdsUniform, QUAD_SECTION_UNIT);
    FogCapture.setVanillaFogUniforms(
        this.fogParamsUniform, this.fogColorUniform, this.fogShapeUniform);
    this.setNearDepthUniforms(context, nearDepthTexture, reverseDepth);

    glActiveTexture(GL_TEXTURE0 + BLOCK_ATLAS_UNIT);
    glBindTexture(GL_TEXTURE_2D, this.atlasTexture);
    glBindSampler(BLOCK_ATLAS_UNIT, 0);
    LightMapHelper.bind(LIGHTMAP_UNIT);
    glActiveTexture(GL_TEXTURE0 + GEOMETRY_BUFFER_UNIT);
    glBindTexture(GL_TEXTURE_BUFFER, this.geometryTexture);
    glBindSampler(GEOMETRY_BUFFER_UNIT, 0);
    glActiveTexture(GL_TEXTURE0 + SECTION_META_UNIT);
    glBindTexture(GL_TEXTURE_BUFFER, this.sectionMetaTexture);
    glBindSampler(SECTION_META_UNIT, 0);
    glActiveTexture(GL_TEXTURE0 + MODEL_BUFFER_UNIT);
    glBindTexture(GL_TEXTURE_BUFFER, this.modelTexture);
    glBindSampler(MODEL_BUFFER_UNIT, 0);
    glActiveTexture(GL_TEXTURE0 + MODEL_COLOUR_UNIT);
    glBindTexture(GL_TEXTURE_BUFFER, this.modelColourTexture);
    glBindSampler(MODEL_COLOUR_UNIT, 0);
    glActiveTexture(GL_TEXTURE0 + QUAD_SECTION_UNIT);
    glBindTexture(GL_TEXTURE_BUFFER, this.quadSectionTexture);
    glBindSampler(QUAD_SECTION_UNIT, 0);
    this.bindNearDepthTexture(nearDepthTexture);

    glBindVertexArray(this.rangeVao);
    glProvokingVertex(GL_FIRST_VERTEX_CONVENTION);
    this.drawGpuTimer.poll(profiler);
    this.drawGpuTimer.begin();
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.rangeIndexBuffer);
    nglMultiDrawElementsBaseVertex(
        GL_TRIANGLES,
        this.rangeCountsAddress(),
        GL_UNSIGNED_INT,
        this.rangeIndicesAddress(),
        rangeCount,
        this.rangeBaseVerticesAddress());
    this.drawGpuTimer.end();
  }

  private void drawTranslucentRanges(
      RenderFrameContext context,
      Matrix4fc drawMvp,
      Matrix4fc vanillaDrawMvp,
      LoadedVolumeBound bound,
      boolean colorWriteEnabled,
      int rangeCount,
      MemoryStack stack,
      FrameProfiler profiler) {
    int previousDepthFunc = glGetInteger(GL_DEPTH_FUNC);
    boolean reverseDepth = previousDepthFunc == GL_GEQUAL || previousDepthFunc == GL_GREATER;
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, context.sourceFramebuffer());
    glViewport(0, 0, context.viewportWidth(), context.viewportHeight());
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(reverseDepth ? GL_GEQUAL : GL_LEQUAL);
    glDepthMask(true);
    glColorMask(colorWriteEnabled, colorWriteEnabled, colorWriteEnabled, colorWriteEnabled);
    glEnable(GL_BLEND);
    glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    glDisable(GL_CULL_FACE);

    this.translucentShader.bind();
    FloatBuffer matrix = stack.mallocFloat(16);
    drawMvp.get(matrix);
    glUniformMatrix4fv(this.translucentVoxyMvpUniform, false, matrix);
    matrix.clear();
    vanillaDrawMvp.get(matrix);
    glUniformMatrix4fv(this.translucentVanillaMvpUniform, false, matrix);
    glUniform1f(this.translucentEarthRadiusUniform, DistantRenderer.computeEarthRadius());
    glUniform1i(this.translucentBlockAtlasUniform, BLOCK_ATLAS_UNIT);
    glUniform1i(this.translucentLightmapUniform, LIGHTMAP_UNIT);
    glUniform3i(
        this.translucentBaseSectionFrameUniform,
        floorSection(context.cameraX()),
        floorSection(context.cameraY()),
        floorSection(context.cameraZ()));
    glUniform1i(this.translucentGeometryQuadsUniform, GEOMETRY_BUFFER_UNIT);
    glUniform1i(this.translucentSectionMetaUniform, SECTION_META_UNIT);
    glUniform1i(this.translucentModelBufferUniform, MODEL_BUFFER_UNIT);
    glUniform1i(this.translucentModelColourUniform, MODEL_COLOUR_UNIT);
    glUniform1i(this.translucentQuadSectionIdsUniform, QUAD_SECTION_UNIT);
    FogCapture.setVanillaFogUniforms(
        this.translucentFogParamsUniform,
        this.translucentFogColorUniform,
        this.translucentFogShapeUniform);
    this.setTranslucentBoundUniforms(bound);

    glActiveTexture(GL_TEXTURE0 + BLOCK_ATLAS_UNIT);
    glBindTexture(GL_TEXTURE_2D, this.atlasTexture);
    glBindSampler(BLOCK_ATLAS_UNIT, 0);
    LightMapHelper.bind(LIGHTMAP_UNIT);
    glActiveTexture(GL_TEXTURE0 + GEOMETRY_BUFFER_UNIT);
    glBindTexture(GL_TEXTURE_BUFFER, this.geometryTexture);
    glBindSampler(GEOMETRY_BUFFER_UNIT, 0);
    glActiveTexture(GL_TEXTURE0 + SECTION_META_UNIT);
    glBindTexture(GL_TEXTURE_BUFFER, this.sectionMetaTexture);
    glBindSampler(SECTION_META_UNIT, 0);
    glActiveTexture(GL_TEXTURE0 + MODEL_BUFFER_UNIT);
    glBindTexture(GL_TEXTURE_BUFFER, this.modelTexture);
    glBindSampler(MODEL_BUFFER_UNIT, 0);
    glActiveTexture(GL_TEXTURE0 + MODEL_COLOUR_UNIT);
    glBindTexture(GL_TEXTURE_BUFFER, this.modelColourTexture);
    glBindSampler(MODEL_COLOUR_UNIT, 0);
    glActiveTexture(GL_TEXTURE0 + QUAD_SECTION_UNIT);
    glBindTexture(GL_TEXTURE_BUFFER, this.quadSectionTexture);
    glBindSampler(QUAD_SECTION_UNIT, 0);
    this.bindBoundDepthTexture(bound);

    glBindVertexArray(this.rangeVao);
    glProvokingVertex(GL_FIRST_VERTEX_CONVENTION);
    this.drawGpuTimer.poll(profiler);
    this.drawGpuTimer.begin();
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.rangeIndexBuffer);
    nglMultiDrawElementsBaseVertex(
        GL_TRIANGLES,
        this.rangeCountsAddress(),
        GL_UNSIGNED_INT,
        this.rangeIndicesAddress(),
        rangeCount,
        this.rangeBaseVerticesAddress());
    this.drawGpuTimer.end();
  }

  private void setTranslucentBoundUniforms(LoadedVolumeBound bound) {
    boolean enabled = bound != null && bound.enabled();
    if (this.translucentBoundDepthUniform >= 0) {
      glUniform1i(this.translucentBoundDepthUniform, BOUND_DEPTH_UNIT);
    }
    if (this.translucentBoundSizeUniform >= 0) {
      glUniform2f(
          this.translucentBoundSizeUniform,
          enabled ? bound.width() : 0,
          enabled ? bound.height() : 0);
    }
    if (this.translucentBoundEnabledUniform >= 0) {
      glUniform1i(this.translucentBoundEnabledUniform, enabled ? 1 : 0);
    }
  }

  private void bindBoundDepthTexture(LoadedVolumeBound bound) {
    glActiveTexture(GL_TEXTURE0 + BOUND_DEPTH_UNIT);
    glBindTexture(GL_TEXTURE_2D, bound != null && bound.enabled() ? bound.texture() : 0);
    glBindSampler(BOUND_DEPTH_UNIT, 0);
  }

  private void setNearDepthUniforms(
      RenderFrameContext context, int nearDepthTexture, boolean reverseDepth) {
    if (this.nearDepthUniform >= 0) {
      glUniform1i(this.nearDepthUniform, NEAR_DEPTH_UNIT);
    }
    if (this.nearDepthSizeUniform >= 0) {
      glUniform2f(this.nearDepthSizeUniform, context.viewportWidth(), context.viewportHeight());
    }
    if (this.useNearDepthMaskUniform >= 0) {
      glUniform1i(this.useNearDepthMaskUniform, nearDepthTexture != 0 ? 1 : 0);
    }
    if (this.reverseDepthUniform >= 0) {
      glUniform1i(this.reverseDepthUniform, reverseDepth ? 1 : 0);
    }
  }

  private void bindNearDepthTexture(int nearDepthTexture) {
    glActiveTexture(GL_TEXTURE0 + NEAR_DEPTH_UNIT);
    glBindTexture(GL_TEXTURE_2D, nearDepthTexture);
    glBindSampler(NEAR_DEPTH_UNIT, 0);
  }

  private int snapshotSourceDepth(RenderFrameContext context) {
    int sourceDepthTexture = this.findFramebufferDepthTexture(context.sourceFramebuffer());
    if (sourceDepthTexture == 0) {
      this.logNearDepthFallback();
      return 0;
    }
    int snapshot =
        this.snapshotNearDepth(
            sourceDepthTexture, context.viewportWidth(), context.viewportHeight());
    if (snapshot == 0) {
      this.logNearDepthFallback();
    }
    return snapshot;
  }

  private int snapshotNearDepth(int depthTexture, int width, int height) {
    if (depthTexture == 0 || width <= 0 || height <= 0) {
      return 0;
    }
    this.ensureNearDepthTexture(width, height);
    glBindFramebuffer(GL_READ_FRAMEBUFFER, this.depthCopyFramebuffer);
    glFramebufferTexture2D(
        GL_READ_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0);
    glReadBuffer(GL_NONE);
    int readStatus = glCheckFramebufferStatus(GL_READ_FRAMEBUFFER);
    if (readStatus != GL_FRAMEBUFFER_COMPLETE) {
      Logger.error(
          "Voxy GL41Metal drawlist depth read framebuffer incomplete: 0x"
              + Integer.toHexString(readStatus));
      return 0;
    }
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.nearDepthFramebuffer);
    int drawStatus = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER);
    if (drawStatus != GL_FRAMEBUFFER_COMPLETE) {
      Logger.error(
          "Voxy GL41Metal drawlist depth snapshot framebuffer incomplete: 0x"
              + Integer.toHexString(drawStatus));
      return 0;
    }
    glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_DEPTH_BUFFER_BIT, GL_NEAREST);
    return this.nearDepthTexture;
  }

  private void ensureNearDepthTexture(int width, int height) {
    if (this.nearDepthTexture != 0
        && this.nearDepthWidth == width
        && this.nearDepthHeight == height) {
      return;
    }
    if (this.nearDepthTexture != 0) {
      glDeleteTextures(this.nearDepthTexture);
      this.nearDepthTexture = 0;
    }
    this.nearDepthTexture = glGenTextures();
    this.nearDepthWidth = width;
    this.nearDepthHeight = height;
    glActiveTexture(GL_TEXTURE0 + NEAR_DEPTH_UNIT);
    glBindTexture(GL_TEXTURE_2D, this.nearDepthTexture);
    glTexImage2D(
        GL_TEXTURE_2D,
        0,
        GL_DEPTH_COMPONENT32F,
        width,
        height,
        0,
        GL_DEPTH_COMPONENT,
        GL_FLOAT,
        0L);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_NONE);
    glBindFramebuffer(GL_FRAMEBUFFER, this.nearDepthFramebuffer);
    glFramebufferTexture2D(
        GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, this.nearDepthTexture, 0);
    glReadBuffer(GL_NONE);
  }

  private int findFramebufferDepthTexture(int framebuffer) {
    if (framebuffer == 0) {
      return 0;
    }
    glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    int type =
        glGetFramebufferAttachmentParameteri(
            GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
    if (type != GL_TEXTURE) {
      return 0;
    }
    return glGetFramebufferAttachmentParameteri(
        GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
  }

  private void logNearDepthFallback() {
    if (this.loggedNearDepthFallback) {
      return;
    }
    this.loggedNearDepthFallback = true;
    Logger.warn("Voxy GL41Metal drawlist near-depth mask unavailable; using hardware depth only");
  }

  private void initBuffers() {
    for (int i = 0; i < STREAM_BUFFER_COUNT; i++) {
      this.vaos[i] = glGenVertexArrays();
      this.instanceBuffers[i] = glGenBuffers();

      glBindVertexArray(this.vaos[i]);
      glBindBuffer(GL_ARRAY_BUFFER, this.instanceBuffers[i]);
      glBufferData(GL_ARRAY_BUFFER, (long) this.instanceCapacity * STREAM_STRIDE, GL_STREAM_DRAW);

      glEnableVertexAttribArray(0);
      glVertexAttribPointer(0, 4, GL_FLOAT, false, STREAM_STRIDE, 0L);
      glEnableVertexAttribArray(1);
      glVertexAttribPointer(1, 4, GL_FLOAT, false, STREAM_STRIDE, 16L);
      glVertexAttribDivisor(1, 1);
      glEnableVertexAttribArray(2);
      glVertexAttribIPointer(2, 4, GL_UNSIGNED_INT, STREAM_STRIDE, 32L);
      glVertexAttribDivisor(2, 1);
      glVertexAttribDivisor(0, 1);

      glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.indexBuffer);
      if (i == 0) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
          glBufferData(
              GL_ELEMENT_ARRAY_BUFFER,
              stack.bytes((byte) 1, (byte) 2, (byte) 0, (byte) 1, (byte) 3, (byte) 2),
              GL_STATIC_DRAW);
        }
      }
    }
    glBindVertexArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
  }

  private void initTerrainMirror() {
    if (!USE_TERRAIN_MIRROR) {
      return;
    }
    glBindBuffer(GL_TEXTURE_BUFFER, this.geometryBuffer);
    glBufferData(GL_TEXTURE_BUFFER, this.geometryCapacityBytes, GL_DYNAMIC_DRAW);
    glBindTexture(GL_TEXTURE_BUFFER, this.geometryTexture);
    glTexBuffer(GL_TEXTURE_BUFFER, GL_RG32UI, this.geometryBuffer);

    glBindBuffer(GL_TEXTURE_BUFFER, this.sectionMetaBuffer);
    glBufferData(
        GL_TEXTURE_BUFFER, (long) this.maxSections * SECTION_METADATA_BYTES, GL_DYNAMIC_DRAW);
    glBindTexture(GL_TEXTURE_BUFFER, this.sectionMetaTexture);
    glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32UI, this.sectionMetaBuffer);

    glBindBuffer(GL_TEXTURE_BUFFER, this.modelBuffer);
    glBufferData(GL_TEXTURE_BUFFER, (long) MODEL_COUNT * MODEL_BYTES, GL_DYNAMIC_DRAW);
    MemoryBuffer missingModels = new MemoryBuffer((long) MODEL_COUNT * MODEL_BYTES);
    try {
      MemoryUtil.memSet(missingModels.address, 0xff, missingModels.size);
      nglBufferSubData(GL_TEXTURE_BUFFER, 0, missingModels.size, missingModels.address);
    } finally {
      missingModels.free();
    }
    glBindTexture(GL_TEXTURE_BUFFER, this.modelTexture);
    glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32UI, this.modelBuffer);

    glBindBuffer(GL_TEXTURE_BUFFER, this.modelColourBuffer);
    glBufferData(GL_TEXTURE_BUFFER, (long) MODEL_COUNT * Integer.BYTES, GL_DYNAMIC_DRAW);
    MemoryBuffer defaultColours = new MemoryBuffer((long) MODEL_COUNT * Integer.BYTES);
    try {
      MemoryUtil.memSet(defaultColours.address, 0xff, defaultColours.size);
      nglBufferSubData(GL_TEXTURE_BUFFER, 0, defaultColours.size, defaultColours.address);
    } finally {
      defaultColours.free();
    }
    glBindTexture(GL_TEXTURE_BUFFER, this.modelColourTexture);
    glTexBuffer(GL_TEXTURE_BUFFER, GL_R32UI, this.modelColourBuffer);

    glBindBuffer(GL_TEXTURE_BUFFER, this.quadSectionBuffer);
    glBufferData(
        GL_TEXTURE_BUFFER,
        (this.geometryCapacityBytes / Long.BYTES) * Integer.BYTES,
        GL_DYNAMIC_DRAW);
    glBindTexture(GL_TEXTURE_BUFFER, this.quadSectionTexture);
    glTexBuffer(GL_TEXTURE_BUFFER, GL_R32UI, this.quadSectionBuffer);

    glBindTexture(GL_TEXTURE_BUFFER, 0);
    glBindBuffer(GL_TEXTURE_BUFFER, 0);
  }

  private void initRangeCommands() {
    if (!USE_DRAW_RANGES) {
      return;
    }
    this.ensureRangeCommandBuffer(this.rangeCapacity);
    this.uploadRangeIndexBuffer(this.rangeIndexQuadCapacity);
  }

  private void ensureRangeCommandBuffer(int capacity) {
    long bytes = rangeCommandBytes(capacity);
    if (this.rangeCommandBuffer != null && this.rangeCommandBuffer.size >= bytes) {
      return;
    }
    if (this.rangeCommandBuffer != null) {
      this.rangeCommandBuffer.free();
    }
    this.rangeCommandBuffer = new MemoryBuffer(bytes);
  }

  private long rangeCountsAddress() {
    return this.rangeCommandBuffer.address;
  }

  private long rangeIndicesAddress() {
    return this.rangeCommandBuffer.address + rangeIndicesOffset(this.rangeCapacity);
  }

  private long rangeBaseVerticesAddress() {
    return this.rangeCommandBuffer.address + rangeBaseVerticesOffset(this.rangeCapacity);
  }

  private static long rangeCommandBytes(int capacity) {
    return rangeBaseVerticesOffset(capacity) + (long) capacity * Integer.BYTES;
  }

  private static long rangeIndicesOffset(int capacity) {
    return alignUp((long) capacity * Integer.BYTES, Pointer.POINTER_SIZE);
  }

  private static long rangeBaseVerticesOffset(int capacity) {
    return rangeIndicesOffset(capacity) + (long) capacity * Pointer.POINTER_SIZE;
  }

  private static long alignUp(long value, long alignment) {
    return (value + alignment - 1L) & -alignment;
  }

  private void uploadRangeIndexBuffer(int quadCapacity) {
    long indexCount = (long) quadCapacity * 6L;
    long bytes = indexCount * Integer.BYTES;
    if (bytes > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("GL41Metal range index buffer is too large");
    }
    MemoryBuffer indices = new MemoryBuffer(bytes);
    try {
      long ptr = indices.address;
      for (int quad = 0; quad < quadCapacity; quad++) {
        int base = quad * 4;
        long offset = ptr + (long) quad * 6L * Integer.BYTES;
        MemoryUtil.memPutInt(offset, base + 1);
        MemoryUtil.memPutInt(offset + Integer.BYTES, base + 2);
        MemoryUtil.memPutInt(offset + 2L * Integer.BYTES, base);
        MemoryUtil.memPutInt(offset + 3L * Integer.BYTES, base + 1);
        MemoryUtil.memPutInt(offset + 4L * Integer.BYTES, base + 3);
        MemoryUtil.memPutInt(offset + 5L * Integer.BYTES, base + 2);
      }
      glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.rangeIndexBuffer);
      glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices.asByteBuffer(), GL_STATIC_DRAW);
      glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    } finally {
      indices.free();
    }
  }

  private void uploadQuadSectionIds(int sectionId, long metadataAddress) {
    int geometryOffset = MemoryUtil.memGetInt(metadataAddress + 12L);
    int totalQuads = 0;
    for (int i = 0; i < 4; i++) {
      int packed = MemoryUtil.memGetInt(metadataAddress + 16L + (long) i * Integer.BYTES);
      totalQuads += packed & 0xffff;
      totalQuads += (packed >>> 16) & 0xffff;
    }
    if (geometryOffset < 0 || totalQuads <= 0) {
      return;
    }
    long quadCapacity = this.geometryCapacityBytes / Long.BYTES;
    if ((long) geometryOffset + totalQuads > quadCapacity) {
      Logger.warn("GL41Metal drawlist mirror skipped out-of-range quad section upload");
      return;
    }
    this.ensureQuadSectionScratch(totalQuads);
    long ptr = this.quadSectionScratch.address;
    for (int i = 0; i < totalQuads; i++) {
      MemoryUtil.memPutInt(ptr + (long) i * Integer.BYTES, sectionId);
    }
    glBindBuffer(GL_TEXTURE_BUFFER, this.quadSectionBuffer);
    nglBufferSubData(
        GL_TEXTURE_BUFFER,
        (long) geometryOffset * Integer.BYTES,
        (long) totalQuads * Integer.BYTES,
        this.quadSectionScratch.address);
  }

  private void ensureQuadSectionScratch(int count) {
    if (count <= this.quadSectionScratchCapacity && this.quadSectionScratch != null) {
      return;
    }
    int capacity = Math.max(count, Math.max(1024, this.quadSectionScratchCapacity * 2));
    if (this.quadSectionScratch != null) {
      this.quadSectionScratch.free();
    }
    this.quadSectionScratch = new MemoryBuffer((long) capacity * Integer.BYTES);
    this.quadSectionScratchCapacity = capacity;
  }

  private void initAtlas() {
    glActiveTexture(GL_TEXTURE0 + BLOCK_ATLAS_UNIT);
    glBindTexture(GL_TEXTURE_2D, this.atlasTexture);
    for (int mip = 0; mip < ATLAS_MIP_LEVELS; mip++) {
      nglTexImage2D(
          GL_TEXTURE_2D,
          mip,
          GL_RGBA8,
          ATLAS_WIDTH >> mip,
          ATLAS_HEIGHT >> mip,
          0,
          GL_RGBA,
          GL_UNSIGNED_BYTE,
          0L);
    }
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, ATLAS_MIP_LEVELS - 1);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_LOD, 0);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LOD, ATLAS_MIP_LEVELS - 1);
    glBindTexture(GL_TEXTURE_2D, 0);
  }

  private void growInstanceCapacity(long requiredInstances) {
    if (requiredInstances <= this.instanceCapacity || this.instanceCapacity >= MAX_INSTANCE_LIMIT) {
      return;
    }
    long target = this.instanceCapacity;
    while (target < requiredInstances && target < MAX_INSTANCE_LIMIT) {
      target = Math.max(target * 2L, requiredInstances);
      target = Math.min(target, MAX_INSTANCE_LIMIT);
    }
    if (target <= this.instanceCapacity) {
      return;
    }
    this.instanceCapacity = (int) target;
    this.allocateInstanceBuffers();
    Logger.info(
        "Grew GL41Metal drawlist instance capacity to " + this.instanceCapacity + " instances");
  }

  private void growRangeCapacity(long requiredRanges) {
    if (requiredRanges <= this.rangeCapacity || this.rangeCapacity >= MAX_RANGE_LIMIT) {
      return;
    }
    long target = this.rangeCapacity;
    while (target < requiredRanges && target < MAX_RANGE_LIMIT) {
      target = Math.max(target * 2L, requiredRanges);
      target = Math.min(target, MAX_RANGE_LIMIT);
    }
    if (target <= this.rangeCapacity) {
      return;
    }
    this.rangeCapacity = (int) target;
    this.ensureRangeCommandBuffer(this.rangeCapacity);
    Logger.info("Grew GL41Metal drawlist range capacity to " + this.rangeCapacity + " ranges");
  }

  private void growRangeIndexCapacity(long requiredQuads) {
    if (requiredQuads <= this.rangeIndexQuadCapacity) {
      return;
    }
    long target = this.rangeIndexQuadCapacity;
    while (target < requiredQuads) {
      target = Math.max(target * 2L, requiredQuads);
      target = Math.min(target, 8_000_000L);
      if (target >= 8_000_000L) {
        break;
      }
    }
    if (target <= this.rangeIndexQuadCapacity) {
      return;
    }
    this.rangeIndexQuadCapacity = (int) target;
    this.uploadRangeIndexBuffer(this.rangeIndexQuadCapacity);
    Logger.info(
        "Grew GL41Metal drawlist range index capacity to "
            + this.rangeIndexQuadCapacity
            + " quads");
  }

  private int acquireStreamBuffer() {
    int streamBuffer = this.nextStreamBuffer;
    this.nextStreamBuffer = (streamBuffer + 1) % STREAM_BUFFER_COUNT;
    this.currentStreamBuffer = streamBuffer;
    return streamBuffer;
  }

  private void allocateInstanceBuffers() {
    long bufferBytes = (long) this.instanceCapacity * STREAM_STRIDE;
    for (int buffer : this.instanceBuffers) {
      glBindBuffer(GL_ARRAY_BUFFER, buffer);
      glBufferData(GL_ARRAY_BUFFER, bufferBytes, GL_STREAM_DRAW);
    }
    glBindBuffer(GL_ARRAY_BUFFER, 0);
  }

  private static int floorSection(double value) {
    return ((int) Math.floor(value)) >> 5;
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
      Logger.warn("Invalid GL41Metal drawlist integer config " + key + "=" + value);
      return fallback;
    }
  }

  private static final class GpuTimer implements AutoCloseable {
    private static final int QUERY_COUNT = 6;

    private final int[] queries = new int[QUERY_COUNT];
    private final boolean[] pending = new boolean[QUERY_COUNT];
    private int nextQuery;
    private int activeQuery = -1;

    GpuTimer() {
      if (!ENABLE_DRAW_GPU_TIMER) {
        return;
      }
      for (int i = 0; i < this.queries.length; i++) {
        this.queries[i] = glGenQueries();
      }
    }

    void poll(FrameProfiler profiler) {
      if (!ENABLE_DRAW_GPU_TIMER) {
        return;
      }
      for (int i = 0; i < this.queries.length; i++) {
        if (!this.pending[i]) {
          continue;
        }
        if (glGetQueryObjecti(this.queries[i], GL_QUERY_RESULT_AVAILABLE) != GL_TRUE) {
          continue;
        }
        long nanos = glGetQueryObjecti64(this.queries[i], GL_QUERY_RESULT);
        profiler.recordDrawlistGpuMs(nanos / 1_000_000.0);
        this.pending[i] = false;
      }
    }

    void begin() {
      if (!ENABLE_DRAW_GPU_TIMER || this.activeQuery >= 0) {
        return;
      }
      for (int attempts = 0; attempts < this.queries.length; attempts++) {
        int slot = (this.nextQuery + attempts) % this.queries.length;
        if (!this.pending[slot]) {
          this.nextQuery = (slot + 1) % this.queries.length;
          this.activeQuery = slot;
          glBeginQuery(GL_TIME_ELAPSED, this.queries[slot]);
          return;
        }
      }
    }

    void end() {
      if (!ENABLE_DRAW_GPU_TIMER || this.activeQuery < 0) {
        return;
      }
      glEndQuery(GL_TIME_ELAPSED);
      this.pending[this.activeQuery] = true;
      this.activeQuery = -1;
    }

    @Override
    public void close() {
      if (!ENABLE_DRAW_GPU_TIMER) {
        return;
      }
      glDeleteQueries(this.queries);
    }
  }

  private record StateSnapshot(
      int drawFramebuffer,
      int readFramebuffer,
      int program,
      int vao,
      int arrayBuffer,
      int elementArrayBuffer,
      int activeTexture,
      int texture0,
      int texture1,
      int texture7,
      int texture8,
      int sampler0,
      int sampler1,
      int sampler7,
      int sampler8,
      int[] textureBuffers,
      int[] textureBufferSamplers,
      boolean depthEnabled,
      boolean blendEnabled,
      boolean cullEnabled,
      int depthFunc,
      int blendSrcRgb,
      int blendDstRgb,
      int blendSrcAlpha,
      int blendDstAlpha,
      boolean depthMask,
      boolean colorMaskR,
      boolean colorMaskG,
      boolean colorMaskB,
      boolean colorMaskA,
      int viewportX,
      int viewportY,
      int viewportWidth,
      int viewportHeight) {
    static StateSnapshot capture() {
      int[] viewport = new int[4];
      glGetIntegerv(GL_VIEWPORT, viewport);
      boolean colorMaskR;
      boolean colorMaskG;
      boolean colorMaskB;
      boolean colorMaskA;
      boolean depthMask;
      try (MemoryStack stack = MemoryStack.stackPush()) {
        var colorMask = stack.malloc(4);
        glGetBooleanv(org.lwjgl.opengl.GL11C.GL_COLOR_WRITEMASK, colorMask);
        colorMaskR = colorMask.get(0) != 0;
        colorMaskG = colorMask.get(1) != 0;
        colorMaskB = colorMask.get(2) != 0;
        colorMaskA = colorMask.get(3) != 0;
        var depthMaskBuffer = stack.malloc(1);
        glGetBooleanv(org.lwjgl.opengl.GL11C.GL_DEPTH_WRITEMASK, depthMaskBuffer);
        depthMask = depthMaskBuffer.get(0) != 0;
      }
      return captureCommon(
          viewport,
          glGetInteger(GL_CURRENT_PROGRAM),
          glGetInteger(GL_VERTEX_ARRAY_BINDING),
          glGetInteger(GL_ARRAY_BUFFER_BINDING),
          glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING),
          glIsEnabled(GL_DEPTH_TEST),
          glIsEnabled(GL_BLEND),
          glIsEnabled(GL_CULL_FACE),
          glGetInteger(GL_DEPTH_FUNC),
          depthMask,
          colorMaskR,
          colorMaskG,
          colorMaskB,
          colorMaskA);
    }

    private static StateSnapshot captureCommon(
        int[] viewport,
        int program,
        int vao,
        int arrayBuffer,
        int elementArrayBuffer,
        boolean depthEnabled,
        boolean blendEnabled,
        boolean cullEnabled,
        int depthFunc,
        boolean depthMask,
        boolean colorMaskR,
        boolean colorMaskG,
        boolean colorMaskB,
        boolean colorMaskA) {
      int oldActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
      glActiveTexture(GL_TEXTURE0);
      int texture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
      int sampler0 = glGetInteger(GL_SAMPLER_BINDING);
      glActiveTexture(GL_TEXTURE1);
      int texture1 = glGetInteger(GL_TEXTURE_BINDING_2D);
      int sampler1 = glGetInteger(GL_SAMPLER_BINDING);
      glActiveTexture(GL_TEXTURE0 + NEAR_DEPTH_UNIT);
      int texture7 = glGetInteger(GL_TEXTURE_BINDING_2D);
      int sampler7 = glGetInteger(GL_SAMPLER_BINDING);
      glActiveTexture(GL_TEXTURE0 + BOUND_DEPTH_UNIT);
      int texture8 = glGetInteger(GL_TEXTURE_BINDING_2D);
      int sampler8 = glGetInteger(GL_SAMPLER_BINDING);
      int[] textureBuffers = new int[5];
      int[] textureBufferSamplers = new int[5];
      for (int i = 0; i < textureBuffers.length; i++) {
        int unit = GEOMETRY_BUFFER_UNIT + i;
        glActiveTexture(GL_TEXTURE0 + unit);
        textureBuffers[i] = glGetInteger(GL_TEXTURE_BINDING_BUFFER);
        textureBufferSamplers[i] = glGetInteger(GL_SAMPLER_BINDING);
      }
      glActiveTexture(oldActiveTexture);
      return new StateSnapshot(
          glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),
          glGetInteger(GL_READ_FRAMEBUFFER_BINDING),
          program,
          vao,
          arrayBuffer,
          elementArrayBuffer,
          oldActiveTexture,
          texture0,
          texture1,
          texture7,
          texture8,
          sampler0,
          sampler1,
          sampler7,
          sampler8,
          textureBuffers,
          textureBufferSamplers,
          depthEnabled,
          blendEnabled,
          cullEnabled,
          depthFunc,
          glGetInteger(GL_BLEND_SRC_RGB),
          glGetInteger(GL_BLEND_DST_RGB),
          glGetInteger(GL_BLEND_SRC_ALPHA),
          glGetInteger(GL_BLEND_DST_ALPHA),
          depthMask,
          colorMaskR,
          colorMaskG,
          colorMaskB,
          colorMaskA,
          viewport[0],
          viewport[1],
          viewport[2],
          viewport[3]);
    }

    void restore() {
      glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.drawFramebuffer);
      glBindFramebuffer(GL_READ_FRAMEBUFFER, this.readFramebuffer);
      org.lwjgl.opengl.GL20C.glUseProgram(this.program);
      glBindVertexArray(this.vao);
      glBindBuffer(GL_ARRAY_BUFFER, this.arrayBuffer);
      glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.elementArrayBuffer);
      if (this.depthEnabled) {
        glEnable(GL_DEPTH_TEST);
      } else {
        glDisable(GL_DEPTH_TEST);
      }
      if (this.blendEnabled) {
        glEnable(GL_BLEND);
      } else {
        glDisable(GL_BLEND);
      }
      if (this.cullEnabled) {
        glEnable(GL_CULL_FACE);
      } else {
        glDisable(GL_CULL_FACE);
      }
      glDepthFunc(this.depthFunc);
      glBlendFuncSeparate(
          this.blendSrcRgb, this.blendDstRgb, this.blendSrcAlpha, this.blendDstAlpha);
      glDepthMask(this.depthMask);
      glColorMask(this.colorMaskR, this.colorMaskG, this.colorMaskB, this.colorMaskA);
      if (this.viewportWidth > 0 && this.viewportHeight > 0) {
        glViewport(this.viewportX, this.viewportY, this.viewportWidth, this.viewportHeight);
      }
      glActiveTexture(GL_TEXTURE0);
      glBindTexture(GL_TEXTURE_2D, this.texture0);
      glBindSampler(0, this.sampler0);
      glActiveTexture(GL_TEXTURE1);
      glBindTexture(GL_TEXTURE_2D, this.texture1);
      glBindSampler(1, this.sampler1);
      glActiveTexture(GL_TEXTURE0 + NEAR_DEPTH_UNIT);
      glBindTexture(GL_TEXTURE_2D, this.texture7);
      glBindSampler(NEAR_DEPTH_UNIT, this.sampler7);
      glActiveTexture(GL_TEXTURE0 + BOUND_DEPTH_UNIT);
      glBindTexture(GL_TEXTURE_2D, this.texture8);
      glBindSampler(BOUND_DEPTH_UNIT, this.sampler8);
      for (int i = 0; i < this.textureBuffers.length; i++) {
        int unit = GEOMETRY_BUFFER_UNIT + i;
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_BUFFER, this.textureBuffers[i]);
        glBindSampler(unit, this.textureBufferSamplers[i]);
      }
      glActiveTexture(this.activeTexture);
    }
  }
}
