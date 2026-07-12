package me.cortex.voxy.client.core.rendering.backend.gl41metal;

import static org.lwjgl.opengl.GL11C.GL_ALWAYS;
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
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_NEAREST_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_NONE;
import static org.lwjgl.opengl.GL11C.GL_ONE;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL11C.GL_SCISSOR_BOX;
import static org.lwjgl.opengl.GL11C.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_SHORT;
import static org.lwjgl.opengl.GL11C.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glColorMask;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glDepthFunc;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL11C.glGetBooleanv;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glGetIntegerv;
import static org.lwjgl.opengl.GL11C.glIsEnabled;
import static org.lwjgl.opengl.GL11C.glPixelStorei;
import static org.lwjgl.opengl.GL11C.glReadBuffer;
import static org.lwjgl.opengl.GL11C.glScissor;
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
import static org.lwjgl.opengl.GL15C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glBufferData;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL15C.nglBufferSubData;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL20C.GL_MAX_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL20C.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL20C.glGetUniformLocation;
import static org.lwjgl.opengl.GL20C.glUniform1f;
import static org.lwjgl.opengl.GL20C.glUniform1i;
import static org.lwjgl.opengl.GL20C.glUniform2f;
import static org.lwjgl.opengl.GL20C.glUniform3i;
import static org.lwjgl.opengl.GL20C.glUniform4f;
import static org.lwjgl.opengl.GL20C.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL30C.GL_COLOR;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_DEPTH;
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
import static org.lwjgl.opengl.GL30C.glClearBufferfv;
import static org.lwjgl.opengl.GL30C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30C.glGenFramebuffers;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL30C.glGetFramebufferAttachmentParameteri;
import static org.lwjgl.opengl.GL31C.GL_MAX_TEXTURE_BUFFER_SIZE;
import static org.lwjgl.opengl.GL31C.GL_TEXTURE_BINDING_BUFFER;
import static org.lwjgl.opengl.GL31C.GL_TEXTURE_BUFFER;
import static org.lwjgl.opengl.GL31C.glTexBuffer;
import static org.lwjgl.opengl.GL32C.GL_FIRST_VERTEX_CONVENTION;
import static org.lwjgl.opengl.GL32C.glProvokingVertex;
import static org.lwjgl.opengl.GL32C.nglMultiDrawElementsBaseVertex;
import static org.lwjgl.opengl.GL33C.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33C.glBindSampler;

import java.nio.FloatBuffer;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.cortex.voxy.client.core.rendering.backend.RenderFrameContext;
import me.cortex.voxy.client.core.rendering.backend.ShaderPatchBridgePayload;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge.BridgePrograms;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge.DistantBridgeJob;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge.DistantTerrainBridge;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge.FogCapture;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.jni.NativeBindings;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.terrain.LoadedVolumeBound;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.terrain.MaterialStore;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.terrain.TerrainResources;
import me.cortex.voxy.client.core.rendering.util.LightMapHelper;
import me.cortex.voxy.client.iris.IrisBridgeShaderBindings;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.MemoryBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
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
  private static final int BLOCK_ATLAS_UNIT = 0;
  private static final int LIGHTMAP_UNIT = 1;
  private static final int GEOMETRY_BUFFER_UNIT = 2;
  private static final int SECTION_META_UNIT = 3;
  private static final int MODEL_BUFFER_UNIT = 4;
  private static final int MODEL_COLOUR_UNIT = 5;
  private static final int QUAD_SECTION_UNIT = 6;
  private static final int NEAR_DEPTH_UNIT = 7;
  private static final int BOUND_DEPTH_UNIT = 8;
  private static final int IRIS_VERTEX_BUFFER_UNIT_BASE = 16;
  private static final int IRIS_VERTEX_SAMPLER_COUNT = 5;
  private static final int MAX_RANGE_LIMIT = TerrainResources.maxOpaqueRangeCommands();
  private static final int INITIAL_RANGE_CAPACITY =
      readInt(
          "voxy.gl41metal.drawlistInitialRanges",
          Math.min(32_768, MAX_RANGE_LIMIT),
          1024,
          MAX_RANGE_LIMIT);
  private static final int RANGE_INDEX_QUADS = 16_380;

  private final Shader shader;
  private final Shader translucentShader;
  private final Shader ssaoShader;
  private final Shader compositeShader;
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
  private final int faceShadeUniform;
  private final int faceShadeXUniform;
  private final int nearDepthUniform;
  private final int nearDepthSizeUniform;
  private final int nearDepthViewportOriginUniform;
  private final int useNearDepthMaskUniform;
  private final int reverseDepthUniform;
  private final int ssaoMetadataModeUniform;
  private final int useVoxyDepthUniform;
  private final int ssaoColourUniform;
  private final int ssaoDepthUniform;
  private final int ssaoProjectionUniform;
  private final int ssaoInvProjectionUniform;
  private final int ssaoModelViewUniform;
  private final int ssaoStepsUniform;
  private final int compositeColourUniform;
  private final int compositeDepthUniform;
  private final int compositeInvSourceMvpUniform;
  private final int compositeTargetMvpUniform;
  private final int compositeFogParamsUniform;
  private final int compositeFogColorUniform;
  private final int compositeFogShapeUniform;
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
  private final int translucentFaceShadeUniform;
  private final int translucentFaceShadeXUniform;
  private final int translucentBoundDepthUniform;
  private final int translucentBoundSizeUniform;
  private final int translucentViewportOriginUniform;
  private final int translucentBoundEnabledUniform;
  private final int rangeVao;
  private final int rangeIndexBuffer;
  private final int depthCopyFramebuffer;
  private final int nearDepthFramebuffer;
  private final int ssaoDrawFramebuffer;
  private final int ssaoFramebuffer;
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
  private final int maxSections;
  private final long geometryCapacityBytes;
  private DirectIrisProgram irisOpaqueProgram;
  private DirectIrisTranslucentProgram irisTranslucentProgram;
  private int failedIrisOpaqueShaderKey = Integer.MIN_VALUE;
  private int failedIrisTranslucentShaderKey = Integer.MIN_VALUE;
  private int rangeCapacity = INITIAL_RANGE_CAPACITY;
  private MemoryBuffer rangeCommandBuffer;
  private MemoryBuffer quadSectionScratch;
  private int quadSectionScratchCapacity;
  private int nearDepthTexture;
  private int nearDepthWidth;
  private int nearDepthHeight;
  private int ssaoColourTexture;
  private int ssaoOutputTexture;
  private int ssaoDepthTexture;
  private int ssaoWidth;
  private int ssaoHeight;
  private boolean closed;
  private boolean loggedFirstDraw;
  private boolean loggedLodMismatch;
  private boolean loggedNearDepthFallback;
  private boolean loggedSsaoFallback;

  DrawlistOpaqueRenderer(int maxSections, long geometryCapacityBytes) {
    this.maxSections = maxSections;
    this.geometryCapacityBytes = geometryCapacityBytes;
    this.shader =
        Shader.make()
            .addSource(
                ShaderType.VERTEX,
                ShaderLoader.parse("voxy:lod/gl41metal/drawlist/opaque_ranges.vert", "410 core"))
            .addSource(
                ShaderType.FRAGMENT,
                ShaderLoader.parse("voxy:lod/gl41metal/drawlist/opaque.frag", "410 core"))
            .compile()
            .name("Voxy GL41Metal Drawlist Opaque");
    this.ssaoShader =
        Shader.make()
            .addSource(
                ShaderType.VERTEX, ShaderLoader.parse("voxy:post/fullscreen.vert", "410 core"))
            .addSource(
                ShaderType.FRAGMENT,
                ShaderLoader.parse("voxy:lod/gl41metal/drawlist/ssao.frag", "410 core"))
            .compile()
            .name("Voxy GL41Metal Drawlist SSAO");
    this.compositeShader =
        Shader.make()
            .addSource(
                ShaderType.VERTEX, ShaderLoader.parse("voxy:post/fullscreen.vert", "410 core"))
            .addSource(
                ShaderType.FRAGMENT,
                ShaderLoader.parse("voxy:lod/gl41metal/drawlist/composite.frag", "410 core"))
            .compile()
            .name("Voxy GL41Metal Drawlist Composite");
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
    this.faceShadeUniform = glGetUniformLocation(this.shader.id(), "uFaceShade");
    this.faceShadeXUniform = glGetUniformLocation(this.shader.id(), "uFaceShadeX");
    this.nearDepthUniform = glGetUniformLocation(this.shader.id(), "uNearDepthTex");
    this.nearDepthSizeUniform = glGetUniformLocation(this.shader.id(), "uNearDepthSize");
    this.nearDepthViewportOriginUniform = glGetUniformLocation(this.shader.id(), "uViewportOrigin");
    this.useNearDepthMaskUniform = glGetUniformLocation(this.shader.id(), "uUseNearDepthMask");
    this.reverseDepthUniform = glGetUniformLocation(this.shader.id(), "uReverseDepth");
    this.ssaoMetadataModeUniform = glGetUniformLocation(this.shader.id(), "uSsaoMetadataMode");
    this.useVoxyDepthUniform = glGetUniformLocation(this.shader.id(), "uUseVoxyDepth");
    this.ssaoColourUniform = glGetUniformLocation(this.ssaoShader.id(), "uColourTex");
    this.ssaoDepthUniform = glGetUniformLocation(this.ssaoShader.id(), "uDepthTex");
    this.ssaoProjectionUniform = glGetUniformLocation(this.ssaoShader.id(), "uProjection");
    this.ssaoInvProjectionUniform = glGetUniformLocation(this.ssaoShader.id(), "uInvProjection");
    this.ssaoModelViewUniform = glGetUniformLocation(this.ssaoShader.id(), "uModelView");
    this.ssaoStepsUniform = glGetUniformLocation(this.ssaoShader.id(), "uSteps");
    this.compositeColourUniform = glGetUniformLocation(this.compositeShader.id(), "uColourTex");
    this.compositeDepthUniform = glGetUniformLocation(this.compositeShader.id(), "uDepthTex");
    this.compositeInvSourceMvpUniform =
        glGetUniformLocation(this.compositeShader.id(), "uInvSourceMvp");
    this.compositeTargetMvpUniform = glGetUniformLocation(this.compositeShader.id(), "uTargetMvp");
    this.compositeFogParamsUniform = glGetUniformLocation(this.compositeShader.id(), "uFogParams");
    this.compositeFogColorUniform = glGetUniformLocation(this.compositeShader.id(), "uFogColor");
    this.compositeFogShapeUniform = glGetUniformLocation(this.compositeShader.id(), "uFogShape");
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
    this.translucentFaceShadeUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uFaceShade") : -1;
    this.translucentFaceShadeXUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uFaceShadeX") : -1;
    this.translucentBoundDepthUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uBoundDepthTex") : -1;
    this.translucentBoundSizeUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uBoundSize") : -1;
    this.translucentViewportOriginUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uViewportOrigin") : -1;
    this.translucentBoundEnabledUniform =
        translucentProgram != 0 ? glGetUniformLocation(translucentProgram, "uBoundEnabled") : -1;

    this.rangeVao = glGenVertexArrays();
    this.rangeIndexBuffer = glGenBuffers();
    this.depthCopyFramebuffer = glGenFramebuffers();
    this.nearDepthFramebuffer = glGenFramebuffers();
    this.ssaoDrawFramebuffer = glGenFramebuffers();
    this.ssaoFramebuffer = glGenFramebuffers();
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
    this.initTerrainMirror();
    this.warnIfTextureBufferCapacityExceeded();
    this.initRangeCommands();
    this.initAtlas();
    Logger.info(
        "Voxy GL41Metal drawlist renderer initialized: mirrorGeometryBytes="
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
      boolean colorWriteEnabled) {
    if (FogCapture.vanillaFogHidesDistant()) {
      return false;
    }
    StateSnapshot state = StateSnapshot.capture();
    try (MemoryStack stack = MemoryStack.stackPush()) {
      return this.renderRanges(
          nativeHandle,
          slot,
          context,
          drawMvp,
          vanillaDrawMvp,
          colorWriteEnabled,
          stack,
          null,
          null,
          null,
          false);
    } finally {
      state.restore();
    }
  }

  boolean renderIrisOpaque(
      long nativeHandle,
      int slot,
      RenderFrameContext context,
      Matrix4fc drawMvp,
      Matrix4fc vanillaDrawMvp,
      ShaderPatchBridgePayload payload,
      DistantTerrainBridge bridge,
      boolean colorWriteEnabled) {
    if (payload == null || !payload.strictBridgeAvailable() || bridge == null) {
      return false;
    }
    DirectIrisProgram program = this.irisProgramFor(payload);
    if (program == null) {
      return false;
    }
    StateSnapshot state = StateSnapshot.captureDirectIris();
    IrisStencilState stencilState = IrisStencilState.capture(state.stencilEnabled);
    IrisTextureState textureState = IrisTextureState.capture(payload.packSamplerTargets());
    try (MemoryStack stack = MemoryStack.stackPush()) {
      return this.renderRanges(
          nativeHandle,
          slot,
          context,
          drawMvp,
          vanillaDrawMvp,
          colorWriteEnabled,
          stack,
          program,
          payload,
          bridge,
          state.depthFunc == GL_GEQUAL || state.depthFunc == GL_GREATER);
    } finally {
      textureState.restore();
      state.restore();
      stencilState.restore();
    }
  }

  private boolean renderRanges(
      long nativeHandle,
      int slot,
      RenderFrameContext context,
      Matrix4fc drawMvp,
      Matrix4fc vanillaDrawMvp,
      boolean colorWriteEnabled,
      MemoryStack stack,
      DirectIrisProgram irisProgram,
      ShaderPatchBridgePayload irisPayload,
      DistantTerrainBridge bridge,
      boolean reverseDepth) {
    this.ensureRangeCommandBuffer(this.rangeCapacity);
    var counters = stack.mallocLong(2);
    MemoryUtil.memSet(MemoryUtil.memAddress(counters), 0, 2L * Long.BYTES);
    int rangeCount =
        NativeBindings.buildOpaqueRanges(
            nativeHandle,
            slot,
            this.rangeCountsAddress(),
            this.rangeBaseVerticesAddress(),
            this.rangeCapacity,
            MemoryUtil.memAddress(counters));
    long overflowRanges = counters.get(0);
    long snapshotMismatches = counters.get(1);
    if (snapshotMismatches > 0 && !this.loggedLodMismatch) {
      this.loggedLodMismatch = true;
      Logger.warn(
          "Voxy GL41Metal drawlist ranges conservatively kept all face groups for "
              + snapshotMismatches
              + " work items whose traversal-time section snapshot changed");
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
      if (irisProgram != null) {
        return this.prepareEmptyIrisOpaqueTarget(stack, irisPayload, bridge, reverseDepth);
      }
      return false;
    }
    if (rangeCount <= 0) {
      if (irisProgram != null) {
        return this.prepareEmptyIrisOpaqueTarget(stack, irisPayload, bridge, reverseDepth);
      }
      return false;
    }
    if (irisProgram == null) {
      this.drawRanges(context, drawMvp, vanillaDrawMvp, colorWriteEnabled, rangeCount, stack);
    } else {
      DistantBridgeJob job = DistantTerrainBridge.irisJob(irisPayload);
      boolean prepared = bridge.prepareDirectIrisOpaque(stack, job, reverseDepth);
      if (!prepared) {
        return false;
      }
      try {
        this.drawIrisRanges(
            context,
            drawMvp,
            vanillaDrawMvp,
            colorWriteEnabled,
            rangeCount,
            stack,
            irisProgram,
            job,
            bridge);
      } finally {
        bridge.finishDirectIrisOpaque();
      }
    }
    if (!this.loggedFirstDraw) {
      this.loggedFirstDraw = true;
      Logger.info("Voxy GL41Metal drawlist rendered first opaque direct-GL range frame");
    }
    return true;
  }

  private boolean prepareEmptyIrisOpaqueTarget(
      MemoryStack stack,
      ShaderPatchBridgePayload payload,
      DistantTerrainBridge bridge,
      boolean reverseDepth) {
    DistantBridgeJob job = DistantTerrainBridge.irisJob(payload);
    if (!bridge.prepareDirectIrisOpaque(stack, job, reverseDepth)) {
      return false;
    }
    bridge.finishDirectIrisOpaque();
    return true;
  }

  boolean renderTranslucent(
      long nativeHandle,
      int slot,
      RenderFrameContext context,
      Matrix4fc drawMvp,
      Matrix4fc vanillaDrawMvp,
      LoadedVolumeBound bound,
      boolean colorWriteEnabled) {
    if (FogCapture.vanillaFogHidesDistant()) {
      return false;
    }
    StateSnapshot state = StateSnapshot.capture();
    try (MemoryStack stack = MemoryStack.stackPush()) {
      this.ensureRangeCommandBuffer(this.rangeCapacity);
      var counters = stack.mallocLong(1);
      MemoryUtil.memSet(MemoryUtil.memAddress(counters), 0, Long.BYTES);
      int rangeCount =
          NativeBindings.buildTranslucentRanges(
              nativeHandle,
              slot,
              this.rangeCountsAddress(),
              this.rangeBaseVerticesAddress(),
              this.rangeCapacity,
              MemoryUtil.memAddress(counters));
      long overflowRanges = counters.get(0);
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
      if (rangeCount <= 0) {
        return false;
      }
      this.drawTranslucentRanges(
          context, drawMvp, vanillaDrawMvp, bound, colorWriteEnabled, rangeCount, stack);
      return true;
    } finally {
      state.restore();
    }
  }

  boolean renderIrisTranslucent(
      long nativeHandle,
      int slot,
      RenderFrameContext context,
      Matrix4fc drawMvp,
      Matrix4fc vanillaDrawMvp,
      LoadedVolumeBound bound,
      ShaderPatchBridgePayload payload,
      DistantTerrainBridge bridge,
      boolean colorWriteEnabled) {
    if (payload == null || !payload.strictBridgeAvailable() || bridge == null) {
      return false;
    }
    DirectIrisTranslucentProgram program = this.irisTranslucentProgramFor(payload);
    if (program == null) {
      return false;
    }
    StateSnapshot state = StateSnapshot.captureDirectIris();
    IrisStencilState stencilState = IrisStencilState.capture(state.stencilEnabled);
    IrisTextureState textureState = IrisTextureState.capture(payload.packSamplerTargets());
    try (MemoryStack stack = MemoryStack.stackPush()) {
      this.ensureRangeCommandBuffer(this.rangeCapacity);
      var counters = stack.mallocLong(1);
      MemoryUtil.memSet(MemoryUtil.memAddress(counters), 0, Long.BYTES);
      int rangeCount =
          NativeBindings.buildTranslucentRanges(
              nativeHandle,
              slot,
              this.rangeCountsAddress(),
              this.rangeBaseVerticesAddress(),
              this.rangeCapacity,
              MemoryUtil.memAddress(counters));
      long overflowRanges = counters.get(0);
      if (overflowRanges > 0) {
        int oldCapacity = this.rangeCapacity;
        this.growRangeCapacity(rangeCount + overflowRanges);
        Logger.warn(
            "Voxy GL41Metal Iris translucent drawlist ranges overflowed "
                + oldCapacity
                + " ranges; grew to "
                + this.rangeCapacity
                + " and skipped this frame");
        return true;
      }
      if (rangeCount <= 0) {
        return true;
      }
      DistantBridgeJob job = DistantTerrainBridge.translucentJob(payload);
      boolean reverseDepth = state.depthFunc == GL_GEQUAL || state.depthFunc == GL_GREATER;
      boolean prepared = bridge.prepareDirectIrisTranslucent(stack, job, reverseDepth);
      if (!prepared) {
        return false;
      }
      try {
        bridge.bindDirectIrisResources(job);
        if (bound != null && bound.enabled()) {
          this.drawIrisTranslucentBound(
              context,
              drawMvp,
              vanillaDrawMvp,
              bound,
              job.outputWidth(),
              job.outputHeight(),
              rangeCount,
              stack,
              program.bound);
        }
        bridge.beginDirectIrisTranslucentColor(job);
        this.drawIrisTranslucentRanges(
            context, drawMvp, vanillaDrawMvp, colorWriteEnabled, rangeCount, stack, program.color);
      } finally {
        bridge.finishDirectIrisTranslucent();
      }
      return true;
    } finally {
      textureState.restore();
      state.restore();
      stencilState.restore();
    }
  }

  @Override
  public void uploadSectionMetadata(int sectionId, long metadataAddress) {
    if (sectionId < 0 || sectionId >= this.maxSections || metadataAddress == 0) {
      return;
    }
    glBindBuffer(GL_TEXTURE_BUFFER, this.sectionMetaBuffer);
    nglBufferSubData(
        GL_TEXTURE_BUFFER,
        (long) sectionId * SECTION_METADATA_BYTES,
        SECTION_METADATA_BYTES,
        metadataAddress);
    this.uploadQuadSectionIds(sectionId, metadataAddress);
  }

  @Override
  public void uploadGeometry(int geometryElementOffset, long geometryAddress, long geometryBytes) {
    if (geometryAddress == 0 || geometryBytes <= 0) {
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
    if (modelId < 0 || modelId >= MODEL_COUNT || modelAddress == 0) {
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
    if (modelBiomePairsAddress == 0 || modelBiomePairsBytes <= 0) {
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
    this.ssaoShader.free();
    this.compositeShader.free();
    this.translucentShader.free();
    if (this.irisOpaqueProgram != null) {
      this.irisOpaqueProgram.shader.free();
      this.irisOpaqueProgram = null;
    }
    if (this.irisTranslucentProgram != null) {
      this.irisTranslucentProgram.close();
      this.irisTranslucentProgram = null;
    }
    glDeleteVertexArrays(this.rangeVao);
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
    if (this.ssaoColourTexture != 0) {
      glDeleteTextures(this.ssaoColourTexture);
      this.ssaoColourTexture = 0;
    }
    if (this.ssaoOutputTexture != 0) {
      glDeleteTextures(this.ssaoOutputTexture);
      this.ssaoOutputTexture = 0;
    }
    if (this.ssaoDepthTexture != 0) {
      glDeleteTextures(this.ssaoDepthTexture);
      this.ssaoDepthTexture = 0;
    }
    glDeleteTextures(this.geometryTexture);
    glDeleteTextures(this.sectionMetaTexture);
    glDeleteTextures(this.modelTexture);
    glDeleteTextures(this.modelColourTexture);
    glDeleteTextures(this.quadSectionTexture);
    glDeleteFramebuffers(this.depthCopyFramebuffer);
    glDeleteFramebuffers(this.nearDepthFramebuffer);
    glDeleteFramebuffers(this.ssaoDrawFramebuffer);
    glDeleteFramebuffers(this.ssaoFramebuffer);
    if (this.rangeCommandBuffer != null) {
      this.rangeCommandBuffer.free();
      this.rangeCommandBuffer = null;
    }
    if (this.quadSectionScratch != null) {
      this.quadSectionScratch.free();
      this.quadSectionScratch = null;
    }
  }

  private void drawRanges(
      RenderFrameContext context,
      Matrix4fc drawMvp,
      Matrix4fc vanillaDrawMvp,
      boolean colorWriteEnabled,
      int rangeCount,
      MemoryStack stack) {
    int previousDepthFunc = glGetInteger(GL_DEPTH_FUNC);
    boolean reverseDepth = previousDepthFunc == GL_GEQUAL || previousDepthFunc == GL_GREATER;
    this.disableHostClipState();
    int nearDepthTexture = this.snapshotSourceDepth(context);
    int ssaoSteps = DistantRenderer.computeSsaoSteps(context);
    boolean useSsao = colorWriteEnabled && nearDepthTexture != 0 && ssaoSteps > 0;
    if (useSsao) {
      useSsao = this.prepareSsaoDrawTarget(context, reverseDepth, stack);
    }
    if (!useSsao) {
      glBindFramebuffer(GL_DRAW_FRAMEBUFFER, context.sourceFramebuffer());
    }
    this.setDrawViewport(context, useSsao);
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
    this.setFaceShadeUniforms(this.faceShadeUniform, this.faceShadeXUniform);
    this.setNearDepthUniforms(context, nearDepthTexture, reverseDepth, useSsao);
    if (this.ssaoMetadataModeUniform >= 0) {
      glUniform1i(this.ssaoMetadataModeUniform, useSsao ? 1 : 0);
    }
    if (this.useVoxyDepthUniform >= 0) {
      glUniform1i(this.useVoxyDepthUniform, useSsao ? 1 : 0);
    }

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
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.rangeIndexBuffer);
    nglMultiDrawElementsBaseVertex(
        GL_TRIANGLES,
        this.rangeCountsAddress(),
        GL_UNSIGNED_SHORT,
        this.rangeIndicesAddress(),
        rangeCount,
        this.rangeBaseVerticesAddress());
    if (useSsao) {
      this.applySsaoAndComposite(context, drawMvp, vanillaDrawMvp, ssaoSteps, stack);
    }
  }

  private void drawIrisRanges(
      RenderFrameContext context,
      Matrix4fc drawMvp,
      Matrix4fc vanillaDrawMvp,
      boolean colorWriteEnabled,
      int rangeCount,
      MemoryStack stack,
      DirectIrisProgram program,
      DistantBridgeJob job,
      DistantTerrainBridge bridge) {
    glColorMask(colorWriteEnabled, colorWriteEnabled, colorWriteEnabled, colorWriteEnabled);
    glDisable(GL_BLEND);
    glDisable(GL_CULL_FACE);

    program.shader.bind();
    this.uploadMatrix(program.voxyMvpUniform, drawMvp, stack);
    this.uploadMatrix(program.vanillaMvpUniform, vanillaDrawMvp, stack);
    glUniform1f(program.earthRadiusUniform, DistantRenderer.computeEarthRadius());
    glUniform1i(program.blockAtlasUniform, BLOCK_ATLAS_UNIT);
    glUniform3i(
        program.baseSectionFrameUniform,
        floorSection(context.cameraX()),
        floorSection(context.cameraY()),
        floorSection(context.cameraZ()));
    glUniform1i(program.geometryQuadsUniform, IRIS_VERTEX_BUFFER_UNIT_BASE);
    glUniform1i(program.sectionMetaUniform, IRIS_VERTEX_BUFFER_UNIT_BASE + 1);
    glUniform1i(program.modelBufferUniform, IRIS_VERTEX_BUFFER_UNIT_BASE + 2);
    glUniform1i(program.modelColourUniform, IRIS_VERTEX_BUFFER_UNIT_BASE + 3);
    glUniform1i(program.quadSectionIdsUniform, IRIS_VERTEX_BUFFER_UNIT_BASE + 4);
    glUniform1i(program.useVoxyDepthUniform, 1);

    bridge.bindDirectIrisResources(job);
    glActiveTexture(GL_TEXTURE0 + BLOCK_ATLAS_UNIT);
    glBindTexture(GL_TEXTURE_2D, this.atlasTexture);
    glBindSampler(BLOCK_ATLAS_UNIT, 0);
    this.bindIrisVertexBufferTexture(IRIS_VERTEX_BUFFER_UNIT_BASE, this.geometryTexture);
    this.bindIrisVertexBufferTexture(IRIS_VERTEX_BUFFER_UNIT_BASE + 1, this.sectionMetaTexture);
    this.bindIrisVertexBufferTexture(IRIS_VERTEX_BUFFER_UNIT_BASE + 2, this.modelTexture);
    this.bindIrisVertexBufferTexture(IRIS_VERTEX_BUFFER_UNIT_BASE + 3, this.modelColourTexture);
    this.bindIrisVertexBufferTexture(IRIS_VERTEX_BUFFER_UNIT_BASE + 4, this.quadSectionTexture);

    glBindVertexArray(this.rangeVao);
    glProvokingVertex(GL_FIRST_VERTEX_CONVENTION);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.rangeIndexBuffer);
    nglMultiDrawElementsBaseVertex(
        GL_TRIANGLES,
        this.rangeCountsAddress(),
        GL_UNSIGNED_SHORT,
        this.rangeIndicesAddress(),
        rangeCount,
        this.rangeBaseVerticesAddress());
  }

  private void drawIrisTranslucentBound(
      RenderFrameContext context,
      Matrix4fc drawMvp,
      Matrix4fc vanillaDrawMvp,
      LoadedVolumeBound bound,
      int targetWidth,
      int targetHeight,
      int rangeCount,
      MemoryStack stack,
      DirectIrisBoundProgram program) {
    glDisable(GL_CULL_FACE);
    program.base.shader.bind();
    this.uploadDirectIrisUniforms(
        program.base, context, drawMvp, vanillaDrawMvp, stack, BLOCK_ATLAS_UNIT);
    glUniform1i(program.boundDepthUniform, LIGHTMAP_UNIT);
    glUniform2f(program.boundSizeUniform, bound.width(), bound.height());
    glUniform2f(program.targetSizeUniform, targetWidth, targetHeight);
    glUniform1i(program.boundEnabledUniform, 1);
    this.bindDirectIrisGeometryTextures(bound.texture());
    this.drawRangeCommands(rangeCount);
  }

  private void drawIrisTranslucentRanges(
      RenderFrameContext context,
      Matrix4fc drawMvp,
      Matrix4fc vanillaDrawMvp,
      boolean colorWriteEnabled,
      int rangeCount,
      MemoryStack stack,
      DirectIrisProgram program) {
    glColorMask(colorWriteEnabled, colorWriteEnabled, colorWriteEnabled, colorWriteEnabled);
    glDisable(GL_CULL_FACE);
    program.shader.bind();
    this.uploadDirectIrisUniforms(
        program, context, drawMvp, vanillaDrawMvp, stack, BLOCK_ATLAS_UNIT);
    this.bindDirectIrisGeometryTextures(0);
    this.drawRangeCommands(rangeCount);
  }

  private void uploadDirectIrisUniforms(
      DirectIrisProgram program,
      RenderFrameContext context,
      Matrix4fc drawMvp,
      Matrix4fc vanillaDrawMvp,
      MemoryStack stack,
      int atlasUnit) {
    this.uploadMatrix(program.voxyMvpUniform, drawMvp, stack);
    this.uploadMatrix(program.vanillaMvpUniform, vanillaDrawMvp, stack);
    glUniform1f(program.earthRadiusUniform, DistantRenderer.computeEarthRadius());
    glUniform1i(program.blockAtlasUniform, atlasUnit);
    glUniform3i(
        program.baseSectionFrameUniform,
        floorSection(context.cameraX()),
        floorSection(context.cameraY()),
        floorSection(context.cameraZ()));
    glUniform1i(program.geometryQuadsUniform, IRIS_VERTEX_BUFFER_UNIT_BASE);
    glUniform1i(program.sectionMetaUniform, IRIS_VERTEX_BUFFER_UNIT_BASE + 1);
    glUniform1i(program.modelBufferUniform, IRIS_VERTEX_BUFFER_UNIT_BASE + 2);
    glUniform1i(program.modelColourUniform, IRIS_VERTEX_BUFFER_UNIT_BASE + 3);
    glUniform1i(program.quadSectionIdsUniform, IRIS_VERTEX_BUFFER_UNIT_BASE + 4);
    if (program.useVoxyDepthUniform >= 0) {
      glUniform1i(program.useVoxyDepthUniform, 1);
    }
  }

  private void bindDirectIrisGeometryTextures(int auxiliaryTexture) {
    glActiveTexture(GL_TEXTURE0 + BLOCK_ATLAS_UNIT);
    glBindTexture(GL_TEXTURE_2D, this.atlasTexture);
    glBindSampler(BLOCK_ATLAS_UNIT, 0);
    if (auxiliaryTexture != 0) {
      glActiveTexture(GL_TEXTURE0 + LIGHTMAP_UNIT);
      glBindTexture(GL_TEXTURE_2D, auxiliaryTexture);
      glBindSampler(LIGHTMAP_UNIT, 0);
    }
    this.bindIrisVertexBufferTexture(IRIS_VERTEX_BUFFER_UNIT_BASE, this.geometryTexture);
    this.bindIrisVertexBufferTexture(IRIS_VERTEX_BUFFER_UNIT_BASE + 1, this.sectionMetaTexture);
    this.bindIrisVertexBufferTexture(IRIS_VERTEX_BUFFER_UNIT_BASE + 2, this.modelTexture);
    this.bindIrisVertexBufferTexture(IRIS_VERTEX_BUFFER_UNIT_BASE + 3, this.modelColourTexture);
    this.bindIrisVertexBufferTexture(IRIS_VERTEX_BUFFER_UNIT_BASE + 4, this.quadSectionTexture);
  }

  private void drawRangeCommands(int rangeCount) {
    glBindVertexArray(this.rangeVao);
    glProvokingVertex(GL_FIRST_VERTEX_CONVENTION);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.rangeIndexBuffer);
    nglMultiDrawElementsBaseVertex(
        GL_TRIANGLES,
        this.rangeCountsAddress(),
        GL_UNSIGNED_SHORT,
        this.rangeIndicesAddress(),
        rangeCount,
        this.rangeBaseVerticesAddress());
  }

  private void bindIrisVertexBufferTexture(int unit, int texture) {
    glActiveTexture(GL_TEXTURE0 + unit);
    glBindTexture(GL_TEXTURE_BUFFER, texture);
    glBindSampler(unit, 0);
  }

  private DirectIrisProgram irisProgramFor(ShaderPatchBridgePayload payload) {
    int shaderKey = payload.shaderKey();
    if (this.irisOpaqueProgram != null && this.irisOpaqueProgram.shaderKey == shaderKey) {
      return this.irisOpaqueProgram;
    }
    if (this.failedIrisOpaqueShaderKey == shaderKey) {
      return null;
    }
    if (this.irisOpaqueProgram != null) {
      this.irisOpaqueProgram.shader.free();
      this.irisOpaqueProgram = null;
    }
    try {
      this.validateIrisSamplerBudget(payload.packSamplerCount());
      String vertexSource =
          ShaderLoader.parse("voxy:lod/gl41metal/drawlist/opaque_ranges.vert", "410 core")
                  .replace("//__VOXY_IRIS_HEADER__", payload.shaderHeader())
              + "\nvec2 taaShift() "
              + payload.vertexTaaPatch()
              + "\n";
      String fragmentSource =
          ShaderLoader.parse("voxy:lod/gl41metal/drawlist/opaque_iris.frag", "410 core")
              .replace(
                  "//__VOXY_IRIS_PATCH__",
                  payload.shaderHeader() + "\n" + payload.opaqueFragmentPatch());
      fragmentSource = BridgePrograms.prepareDirectIrisFragment(fragmentSource);
      Shader shader =
          Shader.make()
              .define("IRIS_DIRECT")
              // gl_HelperInvocation is unavailable in GLSL 410. The guard only skips helper
              // invocations after derivatives have been computed; omitting it does not change
              // coverage or the values delivered to the pack patch.
              .define("PATCHED_SHADER_ALLOW_DERIVATIVES")
              .addSource(ShaderType.VERTEX, vertexSource)
              .addSource(ShaderType.FRAGMENT, fragmentSource)
              .compile()
              .name("Voxy GL41Metal Direct Iris Opaque");
      payload.programSetup().accept(shader.id());
      DirectIrisProgram program = new DirectIrisProgram(shaderKey, shader);
      if (!program.hasRequiredUniforms()) {
        shader.free();
        throw new IllegalStateException("direct Iris opaque shader is missing required uniforms");
      }
      this.irisOpaqueProgram = program;
      Logger.info(
          "Voxy GL41Metal direct Iris opaque shader compiled: packSamplers="
              + payload.packSamplerCount());
      return program;
    } catch (RuntimeException e) {
      this.failedIrisOpaqueShaderKey = shaderKey;
      Logger.error("Failed to compile Voxy GL41Metal direct Iris opaque shader", e);
      return null;
    }
  }

  private DirectIrisTranslucentProgram irisTranslucentProgramFor(ShaderPatchBridgePayload payload) {
    int shaderKey = payload.shaderKey();
    if (this.irisTranslucentProgram != null && this.irisTranslucentProgram.shaderKey == shaderKey) {
      return this.irisTranslucentProgram;
    }
    if (this.failedIrisTranslucentShaderKey == shaderKey) {
      return null;
    }
    if (this.irisTranslucentProgram != null) {
      this.irisTranslucentProgram.close();
      this.irisTranslucentProgram = null;
    }
    Shader colorShader = null;
    Shader boundShader = null;
    try {
      this.validateIrisSamplerBudget(payload.packSamplerCount());
      String vertexSource =
          ShaderLoader.parse("voxy:lod/gl41metal/drawlist/translucent_ranges.vert", "410 core")
                  .replace("//__VOXY_IRIS_HEADER__", payload.shaderHeader())
              + "\nvec2 taaShift() "
              + payload.vertexTaaPatch()
              + "\n";
      String colorFragment =
          ShaderLoader.parse("voxy:lod/gl41metal/drawlist/translucent_iris.frag", "410 core")
              .replace(
                  "//__VOXY_IRIS_PATCH__",
                  payload.shaderHeader() + "\n" + payload.opaqueFragmentPatch());
      colorFragment = BridgePrograms.prepareDirectIrisFragment(colorFragment);
      colorShader =
          Shader.make()
              .define("IRIS_DIRECT")
              .define("PATCHED_SHADER_ALLOW_DERIVATIVES")
              .addSource(ShaderType.VERTEX, vertexSource)
              .addSource(ShaderType.FRAGMENT, colorFragment)
              .compile()
              .name("Voxy GL41Metal Direct Iris Translucent");
      payload.programSetup().accept(colorShader.id());
      DirectIrisProgram color = new DirectIrisProgram(shaderKey, colorShader);
      if (!color.hasRequiredUniforms()) {
        throw new IllegalStateException(
            "direct Iris translucent shader is missing required uniforms");
      }

      String boundFragment =
          ShaderLoader.parse("voxy:lod/gl41metal/drawlist/translucent_bound.frag", "410 core");
      boundShader =
          Shader.make()
              .define("IRIS_DIRECT")
              .define("IRIS_BOUND_PASS")
              .addSource(ShaderType.VERTEX, vertexSource)
              .addSource(ShaderType.FRAGMENT, boundFragment)
              .compile()
              .name("Voxy GL41Metal Direct Iris Translucent Bound");
      payload.programSetup().accept(boundShader.id());
      DirectIrisBoundProgram bound =
          new DirectIrisBoundProgram(new DirectIrisProgram(shaderKey, boundShader));
      if (!bound.hasRequiredUniforms()) {
        throw new IllegalStateException(
            "direct Iris translucent bound shader is missing required uniforms");
      }
      this.irisTranslucentProgram = new DirectIrisTranslucentProgram(shaderKey, color, bound);
      Logger.info(
          "Voxy GL41Metal direct Iris translucent shader compiled: packSamplers="
              + payload.packSamplerCount());
      return this.irisTranslucentProgram;
    } catch (RuntimeException e) {
      if (colorShader != null) {
        colorShader.free();
      }
      if (boundShader != null) {
        boundShader.free();
      }
      this.failedIrisTranslucentShaderKey = shaderKey;
      Logger.error("Failed to compile Voxy GL41Metal direct Iris translucent shader", e);
      return null;
    }
  }

  private void validateIrisSamplerBudget(int packSamplerCount) {
    int maxFragment = glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS);
    int maxVertex = glGetInteger(GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS);
    int maxCombined = glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
    int usableFragment = maxFragment - 1;
    int fragmentRequired = packSamplerCount + 1;
    int combinedRequired = fragmentRequired + IRIS_VERTEX_SAMPLER_COUNT;
    if (fragmentRequired > usableFragment
        || IRIS_VERTEX_SAMPLER_COUNT > maxVertex
        || combinedRequired > maxCombined
        || IRIS_VERTEX_BUFFER_UNIT_BASE + IRIS_VERTEX_SAMPLER_COUNT > maxCombined) {
      throw new IllegalStateException(
          "direct Iris sampler budget exceeded: fragment="
              + fragmentRequired
              + "/"
              + usableFragment
              + ", vertex="
              + IRIS_VERTEX_SAMPLER_COUNT
              + "/"
              + maxVertex
              + ", combined="
              + combinedRequired
              + "/"
              + maxCombined);
    }
    Logger.info(
        "Voxy GL41Metal direct Iris sampler budget: fragment="
            + packSamplerCount
            + " pack + 1 atlas = "
            + fragmentRequired
            + "/"
            + usableFragment
            + ", vertex="
            + IRIS_VERTEX_SAMPLER_COUNT
            + "/"
            + maxVertex
            + ", combined="
            + combinedRequired
            + "/"
            + maxCombined);
  }

  private boolean prepareSsaoDrawTarget(
      RenderFrameContext context, boolean reverseDepth, MemoryStack stack) {
    if (!this.ensureSsaoTargets(context.viewportWidth(), context.viewportHeight())) {
      return false;
    }
    this.disableHostClipState();
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.ssaoDrawFramebuffer);
    glClearBufferfv(GL_COLOR, 0, stack.floats(0.0f, 0.0f, 0.0f, 0.0f));
    glClearBufferfv(GL_DEPTH, 0, stack.floats(reverseDepth ? 0.0f : 1.0f));
    return true;
  }

  private void setDrawViewport(RenderFrameContext context, boolean localFramebuffer) {
    if (localFramebuffer) {
      glViewport(0, 0, context.viewportWidth(), context.viewportHeight());
      return;
    }
    this.setSourceViewport(context);
  }

  private void disableHostClipState() {
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_STENCIL_TEST);
  }

  private void setSourceViewport(RenderFrameContext context) {
    glViewport(
        context.viewportX(),
        context.viewportY(),
        context.viewportWidth(),
        context.viewportHeight());
  }

  private void applySsaoAndComposite(
      RenderFrameContext context,
      Matrix4fc drawMvp,
      Matrix4fc vanillaDrawMvp,
      int ssaoSteps,
      MemoryStack stack) {
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_BLEND);
    glDepthMask(false);
    glColorMask(true, true, true, true);

    this.ssaoShader.bind();
    if (this.ssaoColourUniform >= 0) {
      glUniform1i(this.ssaoColourUniform, 0);
    }
    if (this.ssaoDepthUniform >= 0) {
      glUniform1i(this.ssaoDepthUniform, 1);
    }
    if (this.ssaoStepsUniform >= 0) {
      glUniform1i(this.ssaoStepsUniform, ssaoSteps);
    }
    Matrix4f projection = DistantRenderer.computeProjectionMat(context.matrices().projection());
    this.uploadMatrix(this.ssaoProjectionUniform, projection, stack);
    this.uploadMatrix(this.ssaoInvProjectionUniform, projection.invert(new Matrix4f()), stack);
    this.uploadMatrix(this.ssaoModelViewUniform, context.matrices().modelView(), stack);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, this.ssaoColourTexture);
    glBindSampler(0, 0);
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, this.ssaoDepthTexture);
    glBindSampler(1, 0);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.ssaoFramebuffer);
    glViewport(0, 0, context.viewportWidth(), context.viewportHeight());
    glBindVertexArray(this.rangeVao);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    this.compositeShader.bind();
    if (this.compositeColourUniform >= 0) {
      glUniform1i(this.compositeColourUniform, 0);
    }
    if (this.compositeDepthUniform >= 0) {
      glUniform1i(this.compositeDepthUniform, 1);
    }
    this.uploadMatrix(this.compositeInvSourceMvpUniform, new Matrix4f(drawMvp).invert(), stack);
    this.uploadMatrix(this.compositeTargetMvpUniform, vanillaDrawMvp, stack);
    FogCapture.setVanillaFogUniforms(
        this.compositeFogParamsUniform,
        this.compositeFogColorUniform,
        this.compositeFogShapeUniform);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, this.ssaoOutputTexture);
    glBindSampler(0, 0);
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, this.ssaoDepthTexture);
    glBindSampler(1, 0);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, context.sourceFramebuffer());
    this.setSourceViewport(context);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_ALWAYS);
    glDepthMask(true);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
  }

  private void uploadMatrix(int uniform, Matrix4fc matrix, MemoryStack stack) {
    if (uniform < 0) {
      return;
    }
    FloatBuffer data = stack.mallocFloat(16);
    matrix.get(data);
    glUniformMatrix4fv(uniform, false, data);
  }

  private boolean ensureSsaoTargets(int width, int height) {
    if (this.ssaoColourTexture != 0 && this.ssaoWidth == width && this.ssaoHeight == height) {
      return true;
    }
    this.deleteSsaoTargets();
    this.ssaoWidth = width;
    this.ssaoHeight = height;
    this.ssaoColourTexture = this.createColourTexture(width, height);
    this.ssaoOutputTexture = this.createColourTexture(width, height);
    this.ssaoDepthTexture = this.createDepthTexture(width, height);

    glBindFramebuffer(GL_FRAMEBUFFER, this.ssaoDrawFramebuffer);
    glFramebufferTexture2D(
        GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.ssaoColourTexture, 0);
    glFramebufferTexture2D(
        GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, this.ssaoDepthTexture, 0);
    int drawStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (drawStatus != GL_FRAMEBUFFER_COMPLETE) {
      this.logSsaoFallback(
          "drawlist SSAO draw framebuffer incomplete: 0x" + Integer.toHexString(drawStatus));
      this.deleteSsaoTargets();
      return false;
    }

    glBindFramebuffer(GL_FRAMEBUFFER, this.ssaoFramebuffer);
    glFramebufferTexture2D(
        GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.ssaoOutputTexture, 0);
    int ssaoStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (ssaoStatus != GL_FRAMEBUFFER_COMPLETE) {
      this.logSsaoFallback(
          "drawlist SSAO output framebuffer incomplete: 0x" + Integer.toHexString(ssaoStatus));
      this.deleteSsaoTargets();
      return false;
    }
    return true;
  }

  private int createColourTexture(int width, int height) {
    int texture = glGenTextures();
    glBindTexture(GL_TEXTURE_2D, texture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    return texture;
  }

  private int createDepthTexture(int width, int height) {
    int texture = glGenTextures();
    glBindTexture(GL_TEXTURE_2D, texture);
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
    glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    return texture;
  }

  private void deleteSsaoTargets() {
    if (this.ssaoColourTexture != 0) {
      glDeleteTextures(this.ssaoColourTexture);
      this.ssaoColourTexture = 0;
    }
    if (this.ssaoOutputTexture != 0) {
      glDeleteTextures(this.ssaoOutputTexture);
      this.ssaoOutputTexture = 0;
    }
    if (this.ssaoDepthTexture != 0) {
      glDeleteTextures(this.ssaoDepthTexture);
      this.ssaoDepthTexture = 0;
    }
    this.ssaoWidth = 0;
    this.ssaoHeight = 0;
  }

  private void logSsaoFallback(String message) {
    if (this.loggedSsaoFallback) {
      return;
    }
    this.loggedSsaoFallback = true;
    Logger.warn("Voxy GL41Metal drawlist SSAO disabled for this frame: " + message);
  }

  private void drawTranslucentRanges(
      RenderFrameContext context,
      Matrix4fc drawMvp,
      Matrix4fc vanillaDrawMvp,
      LoadedVolumeBound bound,
      boolean colorWriteEnabled,
      int rangeCount,
      MemoryStack stack) {
    int previousDepthFunc = glGetInteger(GL_DEPTH_FUNC);
    boolean reverseDepth = previousDepthFunc == GL_GEQUAL || previousDepthFunc == GL_GREATER;
    this.disableHostClipState();
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, context.sourceFramebuffer());
    this.setSourceViewport(context);
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
    this.setFaceShadeUniforms(this.translucentFaceShadeUniform, this.translucentFaceShadeXUniform);
    this.setTranslucentBoundUniforms(context, bound);

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
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.rangeIndexBuffer);
    nglMultiDrawElementsBaseVertex(
        GL_TRIANGLES,
        this.rangeCountsAddress(),
        GL_UNSIGNED_SHORT,
        this.rangeIndicesAddress(),
        rangeCount,
        this.rangeBaseVerticesAddress());
  }

  private void setTranslucentBoundUniforms(RenderFrameContext context, LoadedVolumeBound bound) {
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
    if (this.translucentViewportOriginUniform >= 0) {
      glUniform2f(this.translucentViewportOriginUniform, context.viewportX(), context.viewportY());
    }
    if (this.translucentBoundEnabledUniform >= 0) {
      glUniform1i(this.translucentBoundEnabledUniform, enabled ? 1 : 0);
    }
  }

  private void setFaceShadeUniforms(int faceShadeUniform, int faceShadeXUniform) {
    float noShade = 1.0f;
    float up = 1.0f;
    float down = 0.5f;
    float zAxis = 0.8f;
    float xAxis = 0.6f;
    var level = Minecraft.getInstance().level;
    if (level != null) {
      noShade = level.getShade(Direction.UP, false);
      up = level.getShade(Direction.UP, true);
      down = level.getShade(Direction.DOWN, true);
      zAxis = level.getShade(Direction.NORTH, true);
      xAxis = level.getShade(Direction.EAST, true);
    }
    if (faceShadeUniform >= 0) {
      glUniform4f(faceShadeUniform, noShade, up, down, zAxis);
    }
    if (faceShadeXUniform >= 0) {
      glUniform1f(faceShadeXUniform, xAxis);
    }
  }

  private void bindBoundDepthTexture(LoadedVolumeBound bound) {
    glActiveTexture(GL_TEXTURE0 + BOUND_DEPTH_UNIT);
    glBindTexture(GL_TEXTURE_2D, bound != null && bound.enabled() ? bound.texture() : 0);
    glBindSampler(BOUND_DEPTH_UNIT, 0);
  }

  private void setNearDepthUniforms(
      RenderFrameContext context,
      int nearDepthTexture,
      boolean reverseDepth,
      boolean localFramebuffer) {
    if (this.nearDepthUniform >= 0) {
      glUniform1i(this.nearDepthUniform, NEAR_DEPTH_UNIT);
    }
    if (this.nearDepthSizeUniform >= 0) {
      glUniform2f(this.nearDepthSizeUniform, context.viewportWidth(), context.viewportHeight());
    }
    if (this.nearDepthViewportOriginUniform >= 0) {
      glUniform2f(
          this.nearDepthViewportOriginUniform,
          localFramebuffer ? 0.0f : context.viewportX(),
          localFramebuffer ? 0.0f : context.viewportY());
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
    int snapshot = this.snapshotNearDepth(sourceDepthTexture, context);
    if (snapshot == 0) {
      this.logNearDepthFallback();
    }
    return snapshot;
  }

  private int snapshotNearDepth(int depthTexture, RenderFrameContext context) {
    int width = context.viewportWidth();
    int height = context.viewportHeight();
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
    int sourceX = context.viewportX();
    int sourceY = context.viewportY();
    glBlitFramebuffer(
        sourceX,
        sourceY,
        sourceX + width,
        sourceY + height,
        0,
        0,
        width,
        height,
        GL_DEPTH_BUFFER_BIT,
        GL_NEAREST);
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

  private void initTerrainMirror() {
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

  private void warnIfTextureBufferCapacityExceeded() {
    int maxTextureBufferTexels = glGetInteger(GL_MAX_TEXTURE_BUFFER_SIZE);
    long geometryTexels = this.geometryCapacityBytes / Long.BYTES;
    if (maxTextureBufferTexels > 0 && geometryTexels > maxTextureBufferTexels) {
      Logger.warn(
          "GL41Metal drawlist geometry mirror capacity is "
              + geometryTexels
              + " texels, above GL_MAX_TEXTURE_BUFFER_SIZE="
              + maxTextureBufferTexels
              + "; lower voxy.gl41metal.geometryCapacityMb or high geometry offsets may be"
              + " unreachable from GL");
    }
  }

  private void initRangeCommands() {
    this.ensureRangeCommandBuffer(this.rangeCapacity);
    this.uploadRangeIndexBuffer();
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
    MemoryUtil.memSet(
        this.rangeCommandBuffer.address + rangeIndicesOffset(capacity),
        0,
        (long) capacity * Pointer.POINTER_SIZE);
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

  private void uploadRangeIndexBuffer() {
    long indexCount = (long) RANGE_INDEX_QUADS * 6L;
    long bytes = indexCount * Short.BYTES;
    MemoryBuffer indices = new MemoryBuffer(bytes);
    try {
      long ptr = indices.address;
      for (int quad = 0; quad < RANGE_INDEX_QUADS; quad++) {
        int base = quad * 4;
        long offset = ptr + (long) quad * 6L * Short.BYTES;
        MemoryUtil.memPutShort(offset, (short) (base + 1));
        MemoryUtil.memPutShort(offset + Short.BYTES, (short) (base + 2));
        MemoryUtil.memPutShort(offset + 2L * Short.BYTES, (short) base);
        MemoryUtil.memPutShort(offset + 3L * Short.BYTES, (short) (base + 1));
        MemoryUtil.memPutShort(offset + 4L * Short.BYTES, (short) (base + 3));
        MemoryUtil.memPutShort(offset + 5L * Short.BYTES, (short) (base + 2));
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
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, ATLAS_MIP_LEVELS - 1);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_LOD, 0);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LOD, ATLAS_MIP_LEVELS - 1);
    glBindTexture(GL_TEXTURE_2D, 0);
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

  private static final class DirectIrisProgram {
    final int shaderKey;
    final Shader shader;
    final int voxyMvpUniform;
    final int vanillaMvpUniform;
    final int earthRadiusUniform;
    final int blockAtlasUniform;
    final int baseSectionFrameUniform;
    final int geometryQuadsUniform;
    final int sectionMetaUniform;
    final int modelBufferUniform;
    final int modelColourUniform;
    final int quadSectionIdsUniform;
    final int useVoxyDepthUniform;

    DirectIrisProgram(int shaderKey, Shader shader) {
      this.shaderKey = shaderKey;
      this.shader = shader;
      int id = shader.id();
      this.voxyMvpUniform = glGetUniformLocation(id, "uVoxyMvp");
      this.vanillaMvpUniform = glGetUniformLocation(id, "uVanillaMvp");
      this.earthRadiusUniform = glGetUniformLocation(id, "uEarthRadius");
      this.blockAtlasUniform = glGetUniformLocation(id, "uBlockModelAtlas");
      this.baseSectionFrameUniform = glGetUniformLocation(id, "uBaseSectionFrame");
      this.geometryQuadsUniform = glGetUniformLocation(id, "uGeometryQuads");
      this.sectionMetaUniform = glGetUniformLocation(id, "uSectionMeta");
      this.modelBufferUniform = glGetUniformLocation(id, "uModelBuffer");
      this.modelColourUniform = glGetUniformLocation(id, "uModelColours");
      this.quadSectionIdsUniform = glGetUniformLocation(id, "uQuadSectionIds");
      this.useVoxyDepthUniform = glGetUniformLocation(id, "uUseVoxyDepth");
    }

    boolean hasRequiredUniforms() {
      return this.voxyMvpUniform >= 0
          && this.earthRadiusUniform >= 0
          && this.blockAtlasUniform >= 0
          && this.baseSectionFrameUniform >= 0
          && this.geometryQuadsUniform >= 0
          && this.sectionMetaUniform >= 0
          && this.modelBufferUniform >= 0
          && this.modelColourUniform >= 0
          && this.quadSectionIdsUniform >= 0;
    }
  }

  private static final class DirectIrisBoundProgram {
    final DirectIrisProgram base;
    final int boundDepthUniform;
    final int boundSizeUniform;
    final int targetSizeUniform;
    final int boundEnabledUniform;

    DirectIrisBoundProgram(DirectIrisProgram base) {
      this.base = base;
      int id = base.shader.id();
      this.boundDepthUniform = glGetUniformLocation(id, "uBoundDepthTex");
      this.boundSizeUniform = glGetUniformLocation(id, "uBoundSize");
      this.targetSizeUniform = glGetUniformLocation(id, "uTargetSize");
      this.boundEnabledUniform = glGetUniformLocation(id, "uBoundEnabled");
    }

    boolean hasRequiredUniforms() {
      return this.base.hasRequiredUniforms()
          && this.boundDepthUniform >= 0
          && this.boundSizeUniform >= 0
          && this.targetSizeUniform >= 0
          && this.boundEnabledUniform >= 0;
    }
  }

  private static final class DirectIrisTranslucentProgram implements AutoCloseable {
    final int shaderKey;
    final DirectIrisProgram color;
    final DirectIrisBoundProgram bound;

    DirectIrisTranslucentProgram(
        int shaderKey, DirectIrisProgram color, DirectIrisBoundProgram bound) {
      this.shaderKey = shaderKey;
      this.color = color;
      this.bound = bound;
    }

    @Override
    public void close() {
      this.color.shader.free();
      this.bound.base.shader.free();
    }
  }

  private record IrisTextureState(
      int activeTexture,
      int[] packTargets,
      int[] packTextures,
      int[] packSamplers,
      int[] vertexBuffers,
      int[] vertexSamplers) {
    static IrisTextureState capture(int[] samplerTargets) {
      int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
      int[] packTargets = samplerTargets == null ? new int[0] : samplerTargets;
      int count = packTargets.length;
      int[] packTextures = new int[count];
      int[] packSamplers = new int[count];
      for (int i = 0; i < count; i++) {
        int unit = IrisBridgeShaderBindings.SAMPLER_BINDING_BASE + i;
        glActiveTexture(GL_TEXTURE0 + unit);
        packTextures[i] = glGetInteger(textureBindingForTarget(packTargets[i]));
        packSamplers[i] = glGetInteger(GL_SAMPLER_BINDING);
      }
      int[] vertexBuffers = new int[IRIS_VERTEX_SAMPLER_COUNT];
      int[] vertexSamplers = new int[IRIS_VERTEX_SAMPLER_COUNT];
      for (int i = 0; i < IRIS_VERTEX_SAMPLER_COUNT; i++) {
        int unit = IRIS_VERTEX_BUFFER_UNIT_BASE + i;
        glActiveTexture(GL_TEXTURE0 + unit);
        vertexBuffers[i] = glGetInteger(GL_TEXTURE_BINDING_BUFFER);
        vertexSamplers[i] = glGetInteger(GL_SAMPLER_BINDING);
      }
      glActiveTexture(activeTexture);
      return new IrisTextureState(
          activeTexture, packTargets, packTextures, packSamplers, vertexBuffers, vertexSamplers);
    }

    private static int textureBindingForTarget(int target) {
      return switch (target) {
        case org.lwjgl.opengl.GL11C.GL_TEXTURE_1D -> org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_1D;
        case GL_TEXTURE_2D -> GL_TEXTURE_BINDING_2D;
        case org.lwjgl.opengl.GL12C.GL_TEXTURE_3D -> org.lwjgl.opengl.GL12C.GL_TEXTURE_BINDING_3D;
        case org.lwjgl.opengl.GL31C.GL_TEXTURE_RECTANGLE ->
            org.lwjgl.opengl.GL31C.GL_TEXTURE_BINDING_RECTANGLE;
        default -> throw new IllegalArgumentException("Unsupported Iris sampler target " + target);
      };
    }

    void restore() {
      for (int i = 0; i < this.packSamplers.length; i++) {
        int unit = IrisBridgeShaderBindings.SAMPLER_BINDING_BASE + i;
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(this.packTargets[i], this.packTextures[i]);
        glBindSampler(unit, this.packSamplers[i]);
      }
      for (int i = 0; i < IRIS_VERTEX_SAMPLER_COUNT; i++) {
        int unit = IRIS_VERTEX_BUFFER_UNIT_BASE + i;
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_BUFFER, this.vertexBuffers[i]);
        glBindSampler(unit, this.vertexSamplers[i]);
      }
      glActiveTexture(this.activeTexture);
    }
  }

  private record IrisStencilState(
      boolean enabled,
      int function,
      int reference,
      int valueMask,
      int writeMask,
      int stencilFail,
      int depthFail,
      int depthPass) {
    static IrisStencilState capture(boolean enabled) {
      if (!enabled) {
        return new IrisStencilState(false, 0, 0, 0, 0, 0, 0, 0);
      }
      return new IrisStencilState(
          true,
          glGetInteger(org.lwjgl.opengl.GL11C.GL_STENCIL_FUNC),
          glGetInteger(org.lwjgl.opengl.GL11C.GL_STENCIL_REF),
          glGetInteger(org.lwjgl.opengl.GL11C.GL_STENCIL_VALUE_MASK),
          glGetInteger(org.lwjgl.opengl.GL11C.GL_STENCIL_WRITEMASK),
          glGetInteger(org.lwjgl.opengl.GL11C.GL_STENCIL_FAIL),
          glGetInteger(org.lwjgl.opengl.GL11C.GL_STENCIL_PASS_DEPTH_FAIL),
          glGetInteger(org.lwjgl.opengl.GL11C.GL_STENCIL_PASS_DEPTH_PASS));
    }

    void restore() {
      if (!this.enabled) {
        return;
      }
      org.lwjgl.opengl.GL11C.glStencilMask(this.writeMask);
      org.lwjgl.opengl.GL11C.glStencilFunc(this.function, this.reference, this.valueMask);
      org.lwjgl.opengl.GL11C.glStencilOp(this.stencilFail, this.depthFail, this.depthPass);
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
      boolean extendedTextureState,
      boolean depthEnabled,
      boolean blendEnabled,
      boolean cullEnabled,
      boolean scissorEnabled,
      boolean stencilEnabled,
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
      int scissorX,
      int scissorY,
      int scissorWidth,
      int scissorHeight,
      int viewportX,
      int viewportY,
      int viewportWidth,
      int viewportHeight) {
    static StateSnapshot capture() {
      return capture(true);
    }

    static StateSnapshot captureDirectIris() {
      return capture(false);
    }

    private static StateSnapshot capture(boolean extendedTextureState) {
      int[] viewport = new int[4];
      int[] scissorBox = new int[4];
      glGetIntegerv(GL_VIEWPORT, viewport);
      glGetIntegerv(GL_SCISSOR_BOX, scissorBox);
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
          glIsEnabled(GL_SCISSOR_TEST),
          glIsEnabled(GL_STENCIL_TEST),
          glGetInteger(GL_DEPTH_FUNC),
          depthMask,
          colorMaskR,
          colorMaskG,
          colorMaskB,
          colorMaskA,
          scissorBox,
          extendedTextureState);
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
        boolean scissorEnabled,
        boolean stencilEnabled,
        int depthFunc,
        boolean depthMask,
        boolean colorMaskR,
        boolean colorMaskG,
        boolean colorMaskB,
        boolean colorMaskA,
        int[] scissorBox,
        boolean extendedTextureState) {
      int oldActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
      glActiveTexture(GL_TEXTURE0);
      int texture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
      int sampler0 = glGetInteger(GL_SAMPLER_BINDING);
      glActiveTexture(GL_TEXTURE1);
      int texture1 = glGetInteger(GL_TEXTURE_BINDING_2D);
      int sampler1 = glGetInteger(GL_SAMPLER_BINDING);
      int texture7 = 0;
      int sampler7 = 0;
      int texture8 = 0;
      int sampler8 = 0;
      int[] textureBuffers = extendedTextureState ? new int[5] : new int[0];
      int[] textureBufferSamplers = extendedTextureState ? new int[5] : new int[0];
      if (extendedTextureState) {
        glActiveTexture(GL_TEXTURE0 + NEAR_DEPTH_UNIT);
        texture7 = glGetInteger(GL_TEXTURE_BINDING_2D);
        sampler7 = glGetInteger(GL_SAMPLER_BINDING);
        glActiveTexture(GL_TEXTURE0 + BOUND_DEPTH_UNIT);
        texture8 = glGetInteger(GL_TEXTURE_BINDING_2D);
        sampler8 = glGetInteger(GL_SAMPLER_BINDING);
      }
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
          extendedTextureState,
          depthEnabled,
          blendEnabled,
          cullEnabled,
          scissorEnabled,
          stencilEnabled,
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
          scissorBox[0],
          scissorBox[1],
          scissorBox[2],
          scissorBox[3],
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
      if (this.scissorEnabled) {
        glEnable(GL_SCISSOR_TEST);
      } else {
        glDisable(GL_SCISSOR_TEST);
      }
      if (this.stencilEnabled) {
        glEnable(GL_STENCIL_TEST);
      } else {
        glDisable(GL_STENCIL_TEST);
      }
      glDepthFunc(this.depthFunc);
      glBlendFuncSeparate(
          this.blendSrcRgb, this.blendDstRgb, this.blendSrcAlpha, this.blendDstAlpha);
      glDepthMask(this.depthMask);
      glColorMask(this.colorMaskR, this.colorMaskG, this.colorMaskB, this.colorMaskA);
      if (this.viewportWidth > 0 && this.viewportHeight > 0) {
        glViewport(this.viewportX, this.viewportY, this.viewportWidth, this.viewportHeight);
      }
      glScissor(this.scissorX, this.scissorY, this.scissorWidth, this.scissorHeight);
      glActiveTexture(GL_TEXTURE0);
      glBindTexture(GL_TEXTURE_2D, this.texture0);
      glBindSampler(0, this.sampler0);
      glActiveTexture(GL_TEXTURE1);
      glBindTexture(GL_TEXTURE_2D, this.texture1);
      glBindSampler(1, this.sampler1);
      if (this.extendedTextureState) {
        glActiveTexture(GL_TEXTURE0 + NEAR_DEPTH_UNIT);
        glBindTexture(GL_TEXTURE_2D, this.texture7);
        glBindSampler(NEAR_DEPTH_UNIT, this.sampler7);
        glActiveTexture(GL_TEXTURE0 + BOUND_DEPTH_UNIT);
        glBindTexture(GL_TEXTURE_2D, this.texture8);
        glBindSampler(BOUND_DEPTH_UNIT, this.sampler8);
      }
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
