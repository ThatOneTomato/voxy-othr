package me.cortex.voxy.client.core.model.bakery;

import static me.cortex.voxy.client.core.model.BakedModelPayload.FALLBACK_FLUID_ONLY;
import static me.cortex.voxy.client.core.model.BakedModelPayload.FALLBACK_INVISIBLE;
import static me.cortex.voxy.client.core.model.BakedModelPayload.FALLBACK_NONE;
import static me.cortex.voxy.client.core.model.BakedModelPayload.FALLBACK_NO_QUADS;
import static me.cortex.voxy.client.core.model.ModelFactory.MODEL_TEXTURE_SIZE;
import static me.cortex.voxy.client.core.model.bakery.ReuseVertexConsumer.VERTEX_FORMAT_SIZE;
import static org.lwjgl.opengl.GL11C.GL_ALWAYS;
import static org.lwjgl.opengl.GL11C.GL_BACK;
import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_COLOR;
import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_CULL_FACE_MODE;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_FUNC;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_FRONT;
import static org.lwjgl.opengl.GL11C.GL_KEEP;
import static org.lwjgl.opengl.GL11C.GL_LESS;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_INDEX;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11C.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glCullFace;
import static org.lwjgl.opengl.GL11C.glDepthFunc;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glDrawElements;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glGetIntegerv;
import static org.lwjgl.opengl.GL11C.glIsEnabled;
import static org.lwjgl.opengl.GL11C.glReadBuffer;
import static org.lwjgl.opengl.GL11C.glReadPixels;
import static org.lwjgl.opengl.GL11C.glStencilFunc;
import static org.lwjgl.opengl.GL11C.glStencilMask;
import static org.lwjgl.opengl.GL11C.glStencilOp;
import static org.lwjgl.opengl.GL11C.glTexImage2D;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL11C.glViewport;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15C.GL_STREAM_DRAW;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glBufferData;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL15C.nglBufferData;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.glDrawBuffers;
import static org.lwjgl.opengl.GL20C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20C.glGetUniformLocation;
import static org.lwjgl.opengl.GL20C.glUniform1i;
import static org.lwjgl.opengl.GL20C.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL20C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT1;
import static org.lwjgl.opengl.GL30C.GL_DEPTH24_STENCIL8;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_STENCIL_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30C.GL_R32UI;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL30C.GL_RENDERBUFFER;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL30C.glBindRenderbuffer;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30C.glClearBufferfi;
import static org.lwjgl.opengl.GL30C.glClearBufferfv;
import static org.lwjgl.opengl.GL30C.glClearBufferuiv;
import static org.lwjgl.opengl.GL30C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30C.glDeleteRenderbuffers;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glFramebufferRenderbuffer;
import static org.lwjgl.opengl.GL30C.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30C.glGenFramebuffers;
import static org.lwjgl.opengl.GL30C.glGenRenderbuffers;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL30C.glRenderbufferStorage;
import static org.lwjgl.opengl.GL33C.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33C.glBindSampler;

import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.model.ColourDepthTextureData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * GL41 port of the GL46 {@link ModelTextureBakery} path.
 *
 * <p>Intentional divergence from GL46: readback is CPU-side glReadPixels instead of GlViewCapture's
 * compute/SSBO bufferreorder path because GL41 cannot use compute shaders or shader storage
 * buffers. The packed output format matches bakery buffer-reorder shader so this can be removed if
 * a GL41-safe GPU reorder path is introduced later.
 */
public final class Gl41OffscreenModelTextureBakery implements AutoCloseable {
  public record Result(
      ColourDepthTextureData[] faces,
      boolean shaded,
      boolean darkenedTextures,
      int fallbackReason) {}

  private static final int FACE_SIZE = MODEL_TEXTURE_SIZE * MODEL_TEXTURE_SIZE;
  private static final int CAPTURE_WIDTH = MODEL_TEXTURE_SIZE * 3;
  private static final int CAPTURE_HEIGHT = MODEL_TEXTURE_SIZE * 2;
  private static final Matrix4f[] VIEWS = new Matrix4f[6];

  private final ReuseVertexConsumer vertexConsumer = new ReuseVertexConsumer();
  private final Shader shader;
  private final int transformUniform;
  private final int textureUniform;
  private final int framebuffer;
  private final int colourTexture;
  private final int metadataTexture;
  private final int depthStencilBuffer;
  private final int vao;
  private final int vbo;
  private final int indexBuffer;
  private int indexedQuads;

  public Gl41OffscreenModelTextureBakery() {
    this.shader =
        Shader.make()
            .addSource(
                ShaderType.VERTEX,
                ShaderLoader.parse("voxy:bakery/gl41/position_tex.vert", "410 core"))
            .addSource(
                ShaderType.FRAGMENT,
                ShaderLoader.parse("voxy:bakery/gl41/position_tex.frag", "410 core"))
            .compile()
            .name("GL41 offscreen model bakery");
    this.transformUniform = glGetUniformLocation(this.shader.id(), "uTransform");
    this.textureUniform = glGetUniformLocation(this.shader.id(), "uTexture");
    if (this.transformUniform < 0 || this.textureUniform < 0) {
      throw new IllegalStateException("GL41 offscreen bakery shader is missing required uniforms");
    }

    int oldTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
    int oldFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
    int oldRenderbuffer = glGetInteger(org.lwjgl.opengl.GL30C.GL_RENDERBUFFER_BINDING);

    this.colourTexture = org.lwjgl.opengl.GL11C.glGenTextures();
    glBindTexture(GL_TEXTURE_2D, this.colourTexture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
    glTexImage2D(
        GL_TEXTURE_2D,
        0,
        GL_RGBA8,
        CAPTURE_WIDTH,
        CAPTURE_HEIGHT,
        0,
        GL_RGBA,
        GL_UNSIGNED_BYTE,
        0L);

    this.metadataTexture = org.lwjgl.opengl.GL11C.glGenTextures();
    glBindTexture(GL_TEXTURE_2D, this.metadataTexture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
    glTexImage2D(
        GL_TEXTURE_2D,
        0,
        GL_R32UI,
        CAPTURE_WIDTH,
        CAPTURE_HEIGHT,
        0,
        GL_RED_INTEGER,
        GL_UNSIGNED_INT,
        0L);

    this.depthStencilBuffer = glGenRenderbuffers();
    glBindRenderbuffer(GL_RENDERBUFFER, this.depthStencilBuffer);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, CAPTURE_WIDTH, CAPTURE_HEIGHT);

    this.framebuffer = glGenFramebuffers();
    glBindFramebuffer(GL_FRAMEBUFFER, this.framebuffer);
    glFramebufferTexture2D(
        GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.colourTexture, 0);
    glFramebufferTexture2D(
        GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, this.metadataTexture, 0);
    glFramebufferRenderbuffer(
        GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, this.depthStencilBuffer);
    glDrawBuffers(new int[] {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1});
    int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
      throw new IllegalStateException(
          "GL41 offscreen model bakery framebuffer is incomplete: 0x"
              + Integer.toHexString(status));
    }

    glBindFramebuffer(GL_FRAMEBUFFER, oldFramebuffer);
    glBindRenderbuffer(GL_RENDERBUFFER, oldRenderbuffer);
    glBindTexture(GL_TEXTURE_2D, oldTexture);

    this.vao = glGenVertexArrays();
    this.vbo = glGenBuffers();
    this.indexBuffer = glGenBuffers();
    glBindVertexArray(this.vao);
    glBindBuffer(GL_ARRAY_BUFFER, this.vbo);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 4, GL_FLOAT, false, VERTEX_FORMAT_SIZE, 0L);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, false, VERTEX_FORMAT_SIZE, 16L);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.indexBuffer);
    glBindVertexArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    me.cortex.voxy.common.Logger.info("GL41 offscreen model bakery active");
  }

  public Result bake(BlockState state) {
    ColourDepthTextureData[] emptyFaces = emptyFaces();
    // Fluids MUST be checked before the INVISIBLE guard: LiquidBlock.getRenderShape() is INVISIBLE
    // (fluids are not drawn through the block-model system), so an INVISIBLE-first check returned
    // empty faces (FALLBACK_INVISIBLE) and the distant renderer dropped every fluid quad. Bake the
    // fluid surface via the vanilla liquid renderer per face, mirroring GL46 ModelTextureBakery.
    if (state.getBlock() instanceof LiquidBlock) {
      RenderType fluidLayer = ItemBlockRenderTypes.getRenderLayer(state.getFluidState());
      if (this.renderCapturedFluidFaces(state, fluidLayer)) {
        return new Result(this.readCapturedFaces(), false, false, FALLBACK_NONE);
      }
      return new Result(emptyFaces, false, false, FALLBACK_FLUID_ONLY);
    }
    if (state.getRenderShape() == RenderShape.INVISIBLE) {
      return new Result(emptyFaces, false, false, FALLBACK_INVISIBLE);
    }

    RenderType layer =
        state.getBlock() instanceof LeavesBlock
            ? RenderType.solid()
            : ItemBlockRenderTypes.getChunkRenderType(state);
    this.vertexConsumer.reset();
    this.collectBlockModelQuads(state, layer);
    if (this.vertexConsumer.isEmpty()) {
      return new Result(
          emptyFaces,
          this.vertexConsumer.anyShaded,
          this.vertexConsumer.anyDarkendTex,
          FALLBACK_NO_QUADS);
    }

    this.renderCapturedFaces(this.vertexConsumer.quadCount(), blockAtlasTextureId());
    return new Result(
        this.readCapturedFaces(),
        this.vertexConsumer.anyShaded,
        this.vertexConsumer.anyDarkendTex,
        FALLBACK_NONE);
  }

  private void collectBlockModelQuads(BlockState state, RenderType layer) {
    int meta = ModelTextureBakery.getMetaFromLayer(layer);
    var model =
        Minecraft.getInstance().getModelManager().getBlockModelShaper().getBlockModel(state);

    RandomSource random = new SingleThreadedRandomSource(42L);
    for (Direction direction :
        new Direction[] {
          Direction.DOWN,
          Direction.UP,
          Direction.NORTH,
          Direction.SOUTH,
          Direction.WEST,
          Direction.EAST,
          null
        }) {
      for (BakedQuad quad : model.getQuads(state, direction, random)) {
        this.vertexConsumer.quad(quad, meta | (quad.isTinted() ? 4 : 0));
      }
    }
  }

  /**
   * Bakes a fluid surface per face, mirroring the GL46 {@link ModelTextureBakery} fluid path.
   * Unlike the block path (one vertex buffer rendered from all six views), the vanilla liquid
   * renderer must be re-run per face with neighbour-air culling, so each face view re-bakes and
   * re-uploads. Renders straight into the shared capture framebuffer; the caller follows up with
   * {@link #readCapturedFaces()} when this returns {@code true}.
   */
  private boolean renderCapturedFluidFaces(BlockState state, RenderType layer) {
    StateSnapshot snapshot = StateSnapshot.capture();
    boolean anyRendered = false;
    try {
      glBindFramebuffer(GL_FRAMEBUFFER, this.framebuffer);
      glDrawBuffers(new int[] {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1});
      this.clearFramebuffer();

      glDisable(GL_BLEND);
      glEnable(GL_STENCIL_TEST);
      glEnable(GL_DEPTH_TEST);
      glEnable(GL_CULL_FACE);
      glDepthFunc(GL_LESS);
      glDepthMask(true);
      glStencilOp(GL_KEEP, GL_KEEP, org.lwjgl.opengl.GL11C.GL_INCR);
      glStencilFunc(GL_ALWAYS, 1, 0xFF);
      glStencilMask(0xFF);

      this.shader.bind();
      glUniform1i(this.textureUniform, 0);
      glActiveTexture(GL_TEXTURE0);
      glBindTexture(GL_TEXTURE_2D, blockAtlasTextureId());
      glBindSampler(0, 0);
      glBindVertexArray(this.vao);

      Matrix4f matrix = new Matrix4f();
      for (int face = 0; face < VIEWS.length; face++) {
        this.vertexConsumer.reset();
        this.bakeFluidStateForFace(state, layer, face);
        if (this.vertexConsumer.isEmpty()) {
          continue;
        }
        int quadCount = this.vertexConsumer.quadCount();
        this.uploadQuads(quadCount);
        glCullFace(face == 1 || face == 2 || face == 4 ? GL_FRONT : GL_BACK);
        glViewport(
            (face % 3) * MODEL_TEXTURE_SIZE,
            (face / 3) * MODEL_TEXTURE_SIZE,
            MODEL_TEXTURE_SIZE,
            MODEL_TEXTURE_SIZE);
        matrix.set(2, 0, 0, 0, 0, 2, 0, 0, 0, 0, -1.0f, 0, -1, -1, 0, 1).mul(VIEWS[face]);
        try (MemoryStack stack = MemoryStack.stackPush()) {
          FloatBuffer buffer = stack.mallocFloat(16);
          matrix.get(buffer);
          glUniformMatrix4fv(this.transformUniform, false, buffer);
        }
        glDrawElements(GL_TRIANGLES, quadCount * 6, GL_UNSIGNED_INT, 0L);
        anyRendered = true;
      }
    } finally {
      snapshot.restore();
    }
    return anyRendered;
  }

  /**
   * Emits the fluid surface geometry for one face view into {@link #vertexConsumer}, mirroring GL46
   * {@link ModelTextureBakery}'s bakeFluidState. The face index selects which neighbours report air
   * (see {@link #shouldReturnAirForFluid}) so the vanilla liquid renderer produces the silhouette
   * of the surface visible along that face.
   */
  private void bakeFluidStateForFace(BlockState state, RenderType layer, int face) {
    // Fluids are assumed tinted (the blue/biome colour comes from the colour provider; untinted
    // fluids cull the tint implicitly during model baking). Matches GL46 bakeFluidState.
    this.vertexConsumer.setDefaultMeta(ModelTextureBakery.getMetaFromLayer(layer) | 4);
    Minecraft.getInstance()
        .getBlockRenderer()
        .renderLiquid(
            BlockPos.ZERO,
            new BlockAndTintGetter() {
              @Override
              public float getShade(Direction direction, boolean shaded) {
                return 0;
              }

              @Override
              public LevelLightEngine getLightEngine() {
                return null;
              }

              @Override
              public int getBrightness(LightLayer type, BlockPos pos) {
                return 0;
              }

              @Override
              public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
                return 0;
              }

              @Nullable
              @Override
              public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
              }

              @Override
              public BlockState getBlockState(BlockPos pos) {
                if (shouldReturnAirForFluid(pos, face)) {
                  return Blocks.AIR.defaultBlockState();
                }
                return state;
              }

              @Override
              public FluidState getFluidState(BlockPos pos) {
                if (shouldReturnAirForFluid(pos, face)) {
                  return Blocks.AIR.defaultBlockState().getFluidState();
                }
                return state.getFluidState();
              }

              @Override
              public int getHeight() {
                return 0;
              }

              public int getMinY() {
                return 0;
              }

              @Override
              public int getMinBuildHeight() {
                return 0;
              }
            },
            this.vertexConsumer,
            state,
            state.getFluidState());
    this.vertexConsumer.setDefaultMeta(0);
  }

  /**
   * Mirrors voxy-fabric {@code SoftwareModelTextureBakery.shouldReturnAirForFluid}: only the
   * neighbour in the face's own direction reports air, so the vanilla liquid renderer emits the
   * silhouette visible along that face.
   *
   * <p>Fabric deliberately does NOT also force the cell above to air (its {@code pos.getY()==1}
   * branch is commented out): leaving the upper neighbour as fluid keeps the side walls full so
   * vertically stacked water does not develop see-through gaps. The trade-off it accepts is that an
   * isolated lowered surface keeps a full-height side ("cup"). We match fabric exactly rather than
   * re-deriving a different rule.
   */
  private static boolean shouldReturnAirForFluid(BlockPos pos, int face) {
    var fv = Direction.from3DDataValue(face).getNormal();
    int dot = fv.getX() * pos.getX() + fv.getY() * pos.getY() + fv.getZ() * pos.getZ();
    return dot >= 1;
  }

  private void renderCapturedFaces(int quadCount, int blockAtlasTextureId) {
    StateSnapshot state = StateSnapshot.capture();
    try {
      glBindFramebuffer(GL_FRAMEBUFFER, this.framebuffer);
      glDrawBuffers(new int[] {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1});
      this.clearFramebuffer();
      this.uploadQuads(quadCount);

      glDisable(GL_BLEND);
      glEnable(GL_STENCIL_TEST);
      glEnable(GL_DEPTH_TEST);
      glEnable(GL_CULL_FACE);
      glDepthFunc(GL_LESS);
      glDepthMask(true);
      glStencilOp(GL_KEEP, GL_KEEP, org.lwjgl.opengl.GL11C.GL_INCR);
      glStencilFunc(GL_ALWAYS, 1, 0xFF);
      glStencilMask(0xFF);

      this.shader.bind();
      glUniform1i(this.textureUniform, 0);
      glActiveTexture(GL_TEXTURE0);
      glBindTexture(GL_TEXTURE_2D, blockAtlasTextureId);
      glBindSampler(0, 0);
      glBindVertexArray(this.vao);

      Matrix4f matrix = new Matrix4f();
      for (int face = 0; face < VIEWS.length; face++) {
        glCullFace(face == 1 || face == 2 || face == 4 ? GL_FRONT : GL_BACK);
        glViewport(
            (face % 3) * MODEL_TEXTURE_SIZE,
            (face / 3) * MODEL_TEXTURE_SIZE,
            MODEL_TEXTURE_SIZE,
            MODEL_TEXTURE_SIZE);
        matrix.set(2, 0, 0, 0, 0, 2, 0, 0, 0, 0, -1.0f, 0, -1, -1, 0, 1).mul(VIEWS[face]);
        try (MemoryStack stack = MemoryStack.stackPush()) {
          FloatBuffer buffer = stack.mallocFloat(16);
          matrix.get(buffer);
          glUniformMatrix4fv(this.transformUniform, false, buffer);
        }
        glDrawElements(GL_TRIANGLES, quadCount * 6, GL_UNSIGNED_INT, 0L);
      }
    } finally {
      state.restore();
    }
  }

  private void clearFramebuffer() {
    try (MemoryStack stack = MemoryStack.stackPush()) {
      FloatBuffer colour = stack.floats(0.0f, 0.0f, 0.0f, 0.0f);
      IntBuffer metadata = stack.ints(0, 0, 0, 0);
      glClearBufferfv(GL_COLOR, 0, colour);
      glClearBufferuiv(GL_COLOR, 1, metadata);
      glClearBufferfi(org.lwjgl.opengl.GL30C.GL_DEPTH_STENCIL, 0, 1.0f, 0);
    }
  }

  private void uploadQuads(int quadCount) {
    this.ensureIndexCapacity(quadCount);
    glBindVertexArray(this.vao);
    glBindBuffer(GL_ARRAY_BUFFER, this.vbo);
    long vertexBytes = (long) quadCount * 4 * VERTEX_FORMAT_SIZE;
    nglBufferData(GL_ARRAY_BUFFER, vertexBytes, this.vertexConsumer.getAddress(), GL_STREAM_DRAW);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.indexBuffer);
  }

  private void ensureIndexCapacity(int quadCount) {
    if (quadCount <= this.indexedQuads) {
      return;
    }
    int targetQuads = Math.max(quadCount, Math.max(128, this.indexedQuads * 2));
    IntBuffer indices = MemoryUtil.memAllocInt(targetQuads * 6);
    try {
      for (int quad = 0; quad < targetQuads; quad++) {
        int base = quad * 4;
        indices.put(base);
        indices.put(base + 1);
        indices.put(base + 2);
        indices.put(base + 2);
        indices.put(base + 3);
        indices.put(base);
      }
      indices.flip();
      glBindVertexArray(this.vao);
      glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.indexBuffer);
      glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
      this.indexedQuads = targetQuads;
    } finally {
      MemoryUtil.memFree(indices);
    }
  }

  private ColourDepthTextureData[] readCapturedFaces() {
    ByteBuffer colourBuffer = MemoryUtil.memAlloc(CAPTURE_WIDTH * CAPTURE_HEIGHT * 4);
    ByteBuffer metadataBuffer = MemoryUtil.memAlloc(CAPTURE_WIDTH * CAPTURE_HEIGHT * 4);
    FloatBuffer depthBuffer = MemoryUtil.memAllocFloat(CAPTURE_WIDTH * CAPTURE_HEIGHT);
    ByteBuffer stencilBuffer = MemoryUtil.memAlloc(CAPTURE_WIDTH * CAPTURE_HEIGHT);
    try {
      glBindFramebuffer(GL_FRAMEBUFFER, this.framebuffer);
      glReadBuffer(GL_COLOR_ATTACHMENT0);
      glReadPixels(0, 0, CAPTURE_WIDTH, CAPTURE_HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, colourBuffer);
      glReadBuffer(GL_COLOR_ATTACHMENT1);
      glReadPixels(
          0, 0, CAPTURE_WIDTH, CAPTURE_HEIGHT, GL_RED_INTEGER, GL_UNSIGNED_INT, metadataBuffer);
      glReadPixels(0, 0, CAPTURE_WIDTH, CAPTURE_HEIGHT, GL_DEPTH_COMPONENT, GL_FLOAT, depthBuffer);
      glReadPixels(
          0, 0, CAPTURE_WIDTH, CAPTURE_HEIGHT, GL_STENCIL_INDEX, GL_UNSIGNED_BYTE, stencilBuffer);

      ColourDepthTextureData[] faces = new ColourDepthTextureData[6];
      long colourAddress = MemoryUtil.memAddress(colourBuffer);
      long metadataAddress = MemoryUtil.memAddress(metadataBuffer);
      for (int face = 0; face < faces.length; face++) {
        int[] colour = new int[FACE_SIZE];
        int[] depth = new int[FACE_SIZE];
        int xBase = (face % 3) * MODEL_TEXTURE_SIZE;
        int yBase = (face / 3) * MODEL_TEXTURE_SIZE;
        for (int y = 0; y < MODEL_TEXTURE_SIZE; y++) {
          for (int x = 0; x < MODEL_TEXTURE_SIZE; x++) {
            int dst = x + y * MODEL_TEXTURE_SIZE;
            int src = (xBase + x) + (yBase + y) * CAPTURE_WIDTH;
            colour[dst] = MemoryUtil.memGetInt(colourAddress + (long) src * Integer.BYTES);
            int depthBits = (int) (Math.clamp(depthBuffer.get(src), 0.0f, 1.0f) * ((1 << 24) - 1));
            int stencil = Byte.toUnsignedInt(stencilBuffer.get(src));
            int metadata = MemoryUtil.memGetInt(metadataAddress + (long) src * Integer.BYTES);
            depth[dst] = (depthBits << 8) | stencil | ((metadata & 1) << 7);
          }
        }
        faces[face] =
            new ColourDepthTextureData(colour, depth, MODEL_TEXTURE_SIZE, MODEL_TEXTURE_SIZE);
      }
      return faces;
    } finally {
      MemoryUtil.memFree(stencilBuffer);
      MemoryUtil.memFree(depthBuffer);
      MemoryUtil.memFree(metadataBuffer);
      MemoryUtil.memFree(colourBuffer);
    }
  }

  private static ColourDepthTextureData[] emptyFaces() {
    ColourDepthTextureData[] faces = new ColourDepthTextureData[6];
    for (int i = 0; i < faces.length; i++) {
      faces[i] =
          new ColourDepthTextureData(
              new int[FACE_SIZE], new int[FACE_SIZE], MODEL_TEXTURE_SIZE, MODEL_TEXTURE_SIZE);
    }
    return faces;
  }

  private static int blockAtlasTextureId() {
    return Minecraft.getInstance()
        .getTextureManager()
        .getTexture(TextureAtlas.LOCATION_BLOCKS)
        .getId();
  }

  public String modeName() {
    return "gl41_offscreen";
  }

  @Override
  public void close() {
    this.vertexConsumer.free();
    glDeleteBuffers(this.indexBuffer);
    glDeleteBuffers(this.vbo);
    glDeleteVertexArrays(this.vao);
    glDeleteRenderbuffers(this.depthStencilBuffer);
    org.lwjgl.opengl.GL11C.glDeleteTextures(this.metadataTexture);
    org.lwjgl.opengl.GL11C.glDeleteTextures(this.colourTexture);
    glDeleteFramebuffers(this.framebuffer);
    this.shader.free();
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
      int sampler0,
      int cullFaceMode,
      int depthFunc,
      boolean cullEnabled,
      boolean blendEnabled,
      boolean depthEnabled,
      boolean stencilEnabled,
      boolean depthMask,
      int viewportX,
      int viewportY,
      int viewportWidth,
      int viewportHeight) {
    static StateSnapshot capture() {
      int oldActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
      glActiveTexture(GL_TEXTURE0);
      int[] viewport = new int[4];
      glGetIntegerv(GL_VIEWPORT, viewport);
      return new StateSnapshot(
          glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),
          glGetInteger(GL_READ_FRAMEBUFFER_BINDING),
          glGetInteger(GL_CURRENT_PROGRAM),
          glGetInteger(GL_VERTEX_ARRAY_BINDING),
          glGetInteger(GL_ARRAY_BUFFER_BINDING),
          glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING),
          oldActiveTexture,
          glGetInteger(GL_TEXTURE_BINDING_2D),
          glGetInteger(GL_SAMPLER_BINDING),
          glGetInteger(GL_CULL_FACE_MODE),
          glGetInteger(GL_DEPTH_FUNC),
          glIsEnabled(GL_CULL_FACE),
          glIsEnabled(GL_BLEND),
          glIsEnabled(GL_DEPTH_TEST),
          glIsEnabled(GL_STENCIL_TEST),
          glGetInteger(GL_DEPTH_WRITEMASK) != 0,
          viewport[0],
          viewport[1],
          viewport[2],
          viewport[3]);
    }

    void restore() {
      glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER, this.drawFramebuffer);
      glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER, this.readFramebuffer);
      glUseProgram(this.program);
      glBindVertexArray(this.vao);
      glBindBuffer(GL_ARRAY_BUFFER, this.arrayBuffer);
      if (this.vao != 0) {
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.elementArrayBuffer);
      }
      glActiveTexture(GL_TEXTURE0);
      glBindTexture(GL_TEXTURE_2D, this.texture0);
      glBindSampler(0, this.sampler0);
      glActiveTexture(this.activeTexture);
      glCullFace(this.cullFaceMode);
      glDepthFunc(this.depthFunc);
      glDepthMask(this.depthMask);
      if (this.cullEnabled) {
        glEnable(GL_CULL_FACE);
      } else {
        glDisable(GL_CULL_FACE);
      }
      if (this.blendEnabled) {
        glEnable(GL_BLEND);
      } else {
        glDisable(GL_BLEND);
      }
      if (this.depthEnabled) {
        glEnable(GL_DEPTH_TEST);
      } else {
        glDisable(GL_DEPTH_TEST);
      }
      if (this.stencilEnabled) {
        glEnable(GL_STENCIL_TEST);
      } else {
        glDisable(GL_STENCIL_TEST);
      }
      glViewport(this.viewportX, this.viewportY, this.viewportWidth, this.viewportHeight);
    }
  }

  static {
    addView(0, -90, 0, 0, 0);
    addView(1, 90, 0, 0, 0b100);
    addView(2, 0, 180, 0, 0b001);
    addView(3, 0, 0, 0, 0);
    addView(4, 0, 90, 270, 0b100);
    addView(5, 0, 270, 270, 0);
  }

  private static void addView(int i, float pitch, float yaw, float rotation, int flip) {
    var stack = new PoseStack();
    stack.translate(0.5f, 0.5f, 0.5f);
    stack.mulPose(makeQuatFromAxisExact(new Vector3f(0, 0, 1), rotation));
    stack.mulPose(makeQuatFromAxisExact(new Vector3f(1, 0, 0), pitch));
    stack.mulPose(makeQuatFromAxisExact(new Vector3f(0, 1, 0), yaw));
    stack.mulPose(new Matrix4f().scale(1 - 2 * (flip & 1), 1 - (flip & 2), 1 - ((flip >> 1) & 2)));
    stack.translate(-0.5f, -0.5f, -0.5f);
    VIEWS[i] = new Matrix4f(stack.last().pose());
  }

  private static Quaternionf makeQuatFromAxisExact(Vector3f vec, float angle) {
    angle = (float) Math.toRadians(angle);
    float halfAngle = angle / 2.0f;
    float sinAngle = (float) Math.sin(halfAngle);
    float invLength = (float) (1 / Math.sqrt(vec.lengthSquared()));
    return new Quaternionf(
        vec.x * invLength * sinAngle,
        vec.y * invLength * sinAngle,
        vec.z * invLength * sinAngle,
        Math.cos(halfAngle));
  }
}
