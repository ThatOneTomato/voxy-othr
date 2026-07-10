package me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge;

import static org.lwjgl.opengl.GL11C.GL_ALWAYS;
import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_EQUAL;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_GEQUAL;
import static org.lwjgl.opengl.GL11C.GL_KEEP;
import static org.lwjgl.opengl.GL11C.GL_LEQUAL;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_NONE;
import static org.lwjgl.opengl.GL11C.GL_REPLACE;
import static org.lwjgl.opengl.GL11C.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL11C.glClear;
import static org.lwjgl.opengl.GL11C.glClearDepth;
import static org.lwjgl.opengl.GL11C.glClearStencil;
import static org.lwjgl.opengl.GL11C.glColorMask;
import static org.lwjgl.opengl.GL11C.glDepthFunc;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glReadBuffer;
import static org.lwjgl.opengl.GL11C.glStencilFunc;
import static org.lwjgl.opengl.GL11C.glStencilMask;
import static org.lwjgl.opengl.GL11C.glStencilOp;
import static org.lwjgl.opengl.GL11C.glTexImage2D;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL11C.glViewport;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL14C.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glBufferData;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL15C.nglBufferSubData;
import static org.lwjgl.opengl.GL20C.glDrawBuffers;
import static org.lwjgl.opengl.GL20C.glUniform1i;
import static org.lwjgl.opengl.GL20C.glUniform2f;
import static org.lwjgl.opengl.GL20C.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_DEPTH24_STENCIL8;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_COMPONENT32F;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_STENCIL;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_STENCIL_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_UNSIGNED_INT_24_8;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
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
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL33C.glBindSampler;

import java.nio.FloatBuffer;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge.BridgePrograms.BridgeProgram;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge.BridgePrograms.TranslucentBridgeProgram;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.terrain.LoadedVolumeBound;
import me.cortex.voxy.client.core.rendering.util.LightMapHelper;
import me.cortex.voxy.client.iris.IrisBridgeShaderBindings;
import me.cortex.voxy.common.Logger;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * GL state side of the distant bridge: owns the scratch framebuffers, the fullscreen VAO, the
 * near-depth snapshot / Voxy-private depth-stencil textures, and the shader-pack UBO, and runs the
 * opaque + translucent composite passes (near-coverage stencil masks, colour draws, depth writes)
 * using the programs {@link BridgePrograms} compiles.
 */
final class GbufferCompositor {
  // Fragment texture units. Metal packs distant terrain into 3 shared textures (see
  // quad_raster.metal QuadFragmentOut), so the reconstruction samplers occupy units 0-2. That
  // is the whole point of the packing: Apple GL4.1 caps a fragment program at 16 texture units
  // AND SIGSEGVs at exactly 16, so a usable program needs <= 15. The Iris colour bridge program
  // also binds every shader-pack sampler (Complementary: 12), so the base reconstruction has to
  // stay at 3 (12 + 3 = 15). uSourceDepthTex (near-depth mask) and the MC lightmap follow; they
  // only appear in the masking/vanilla/debug shapes, never in the Iris colour pass.
  static final int GBUFFER0_TEXTURE_UNIT = 0;
  static final int GBUFFER1_TEXTURE_UNIT = 1;
  static final int GBUFFER2_TEXTURE_UNIT = 2;
  static final int SOURCE_DEPTH_TEXTURE_UNIT = 3;
  static final int LIGHTMAP_TEXTURE_UNIT = 4;
  // Loaded-volume bound depth (P1). Only the vanilla/debug colour programs sample it (unit 5, well
  // within their spare budget); the strict Iris colour program never binds it - its loaded-volume
  // clip rides the stencil-mask pass, which uses its own units (see GLSL_BOUND_MASK).
  static final int BOUND_TEXTURE_UNIT = 5;

  private final BridgePrograms programs;
  private final int framebuffer;
  private final int depthCopyFramebuffer;
  private final int nearDepthFramebuffer;
  private final int fullscreenVao;
  // Shader-pack UBO for the strict Iris bridge path. We do NOT reuse MDIC's
  // me.cortex.voxy.client.core.gl.GlBuffer / UploadStream here: both require GL4.5 DSA
  // (glCreateBuffers, glNamedBufferStorage, glClearNamedBufferData) and GL4.4 persistent-mapped
  // buffers, none of which exist on Apple's GL4.1 driver, so any pack that declares at least one
  // UBO uniform (Complementary, etc.) used to abort the JVM with "No context is current or a
  // function that is not available in the current context was called" the first time
  // bindShaderPackResources walked into ensureUniformBuffer. The bridge owns this buffer entirely
  // (single-buffered, single-shot per draw with a coarse glBufferSubData upload), so the plain
  // GL3.1-era UBO pattern below is sufficient and avoids dragging MDIC-only GPU primitives into the
  // gl41metal package boundary that AGENTS.md asks us to keep clean.
  private int uniformBuffer;
  private int uniformBufferBytes;
  // Pinned native scratch matching uniformBuffer's size. job.uniformUpdater().accept(ptr) writes
  // directly into this region each frame; we then push it to the GL UBO with glBufferSubData. The
  // pointer is invalidated whenever the buffer is resized (see ensureUniformBuffer).
  private long uniformScratchAddr;
  private int nearDepthTexture;
  private int nearDepthWidth;
  private int nearDepthHeight;
  // Voxy-private depth-STENCIL target for the strict Iris path, aligned with voxy-fabric's
  // AbstractRenderPipeline.fb (new DepthFramebuffer(GL_DEPTH24_STENCIL8)). The depth component is
  // what Iris shader packs sample as vxDepthTexOpaque / vxDepthTexTrans: voxy-only, Voxy-NDC depth
  // (g.depth where Voxy drew, far=1.0 elsewhere). The stencil component is the near-scene coverage
  // mask that confines the distant colour pass to pixels with no near geometry. The Iris main depth
  // target is deliberately never touched (matching voxy-fabric renderToVanillaDepth=false). See
  // runOpaquePass for the full near/far compositing contract.
  private int irisPrivateDepthTexture;
  private int irisPrivateDepthWidth;
  private int irisPrivateDepthHeight;
  // Private depth-STENCIL for the translucent near-coverage mask. It is distinct from
  // irisPrivateDepthTexture so the translucent pass never clobbers vxDepthTexOpaque. The old
  // shared-gbuffer composite only uses its stencil; the direct drawlist path also stores actual
  // translucent geometry depth here for vxDepthTexTrans.
  private int translucentMaskDepthStencil;
  private int translucentMaskWidth;
  private int translucentMaskHeight;
  private boolean directTranslucentDepthValid;

  GbufferCompositor(BridgePrograms programs) {
    this.programs = programs;
    this.framebuffer = glGenFramebuffers();
    this.depthCopyFramebuffer = glGenFramebuffers();
    this.nearDepthFramebuffer = glGenFramebuffers();
    this.fullscreenVao = glGenVertexArrays();
  }

  /**
   * Distant translucent (water) colour pass for BOTH targets. Vanilla ({@code vanilla == true},
   * default specialization) draws straight into the MC framebuffer with a hardware depth test
   * against the real scene depth (depth write OFF) and alpha blend, so distant water is occluded by
   * near opaque terrain and the near Sodium water blends over it later. The strict Iris path
   * mirrors {@link #runOpaquePass}: a near-coverage stencil mask (fed with
   * noTranslucents/depthtex1) confines the distant water to pixels where the near opaque scene is
   * empty, then a colour pass shades the front surface and blends it into the pack's translucent
   * targets with the pack's blend, using its own throwaway depth-stencil so it never touches
   * vxDepthTexOpaque. Neither path depth-tests distant layers against each other (Metal already
   * resolved their order into tgbuffer0/1). The occlusion/target/blend split is the one genuine,
   * forced divergence (MC framebuffer real depth vs pack targets with no stencil-testable shared
   * depth); everything downstream of it (programs, decode, shading via voxy_emitFragment) is
   * shared.
   */
  boolean runTranslucentPass(
      MemoryStack stack,
      TranslucentBridgeProgram colorProgram,
      DistantGbufferSlot slot,
      DistantBridgeJob job,
      Matrix4fc voxyMvp,
      Matrix4fc vanillaMvp,
      LoadedVolumeBound bound,
      int sourceDepthTexture,
      int sourceDepthWidth,
      int sourceDepthHeight,
      boolean reverseDepth,
      boolean vanilla) {
    this.directTranslucentDepthValid = false;
    if (vanilla) {
      glBindFramebuffer(GL_DRAW_FRAMEBUFFER, job.sourceFramebuffer());
      glViewport(0, 0, job.outputWidth(), job.outputHeight());
      boolean colorWrite = job.colorWriteEnabled();
      glColorMask(colorWrite, colorWrite, colorWrite, colorWrite);
      glDisable(GL_STENCIL_TEST);
      glEnable(GL_DEPTH_TEST);
      glDepthFunc(reverseDepth ? GL_GEQUAL : GL_LEQUAL);
      glDepthMask(false);
      glEnable(GL_BLEND);
      org.lwjgl.opengl.GL14C.glBlendFuncSeparate(
          org.lwjgl.opengl.GL11C.GL_SRC_ALPHA,
          org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA,
          org.lwjgl.opengl.GL11C.GL_ONE,
          org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA);
      this.bindTranslucentColorProgram(
          stack, colorProgram, slot, job, voxyMvp, vanillaMvp, bound, reverseDepth, true);
      glBindVertexArray(this.fullscreenVao);
      glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
      return true;
    }

    int maskDepth = this.ensureTranslucentMaskDepth(job.outputWidth(), job.outputHeight());
    if (maskDepth == 0) {
      return false;
    }
    var maskShader = this.programs.stencilMask();
    if (maskShader == null) {
      return false;
    }
    if (!this.bindTargetFramebuffer(stack, job, maskDepth, true)) {
      return false;
    }
    glViewport(0, 0, job.outputWidth(), job.outputHeight());

    // (1) Clear ONLY the coverage stencil to 0. Colour is left untouched: the Iris targets hold the
    // pack-lit opaque scene the distant water blends over.
    glColorMask(false, false, false, false);
    glDepthMask(true);
    glStencilMask(0xFF);
    glClearStencil(0);
    glClear(GL_STENCIL_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    // (2) Coverage mask: stencil := 1 wherever the near opaque scene (noTranslucents/depthtex1) has
    // geometry. Distant water is beyond the near render distance, so any near opaque pixel occludes
    // it; near translucents are excluded from depthtex1 and therefore still let distant water show.
    glDisable(GL_DEPTH_TEST);
    glDepthMask(false);
    glDisable(GL_BLEND);
    glEnable(GL_STENCIL_TEST);
    glStencilMask(0xFF);
    glStencilFunc(GL_ALWAYS, 1, 0xFF);
    glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
    maskShader.shader().bind();
    glUniform1i(maskShader.nearDepthUniform(), SOURCE_DEPTH_TEXTURE_UNIT);
    glUniform2f(maskShader.nearSizeUniform(), sourceDepthWidth, sourceDepthHeight);
    glUniform2f(maskShader.targetSizeUniform(), job.outputWidth(), job.outputHeight());
    glUniform1i(maskShader.reverseDepthUniform(), reverseDepth ? 1 : 0);
    this.bind2DTexture(SOURCE_DEPTH_TEXTURE_UNIT, sourceDepthTexture);
    glBindVertexArray(this.fullscreenVao);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    // (2b) Loaded-volume clip (P1): ADD stencil := 1 where the distant water lies inside the Sodium
    // loaded volume. This is the crux of the near/far water fix: near translucents are excluded
    // from
    // depthtex1, so the near-coverage mask above leaves those pixels at stencil 0; without this the
    // distant LOD water would draw over the near Sodium water in the transition band. tgbuffer1.x
    // carries the distant water depth.
    this.runBoundMaskPass(bound, slot, job, slot.tgbuffer1Texture());

    // (3) Colour pass: shade the front translucent surface only where the near scene is empty
    // (stencil==0), blending with the pack's translucent blend. No depth test/write: Metal already
    // resolved distant translucent ordering into tgbuffer0/1.
    glColorMask(true, true, true, true);
    glDisable(GL_DEPTH_TEST);
    glDepthMask(false);
    glStencilMask(0x00);
    glStencilFunc(GL_EQUAL, 0, 0xFF);
    glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
    // Baseline alpha blend, then let the pack's per-buffer blend override it (no-op if the pack
    // declares no translucent blending).
    glEnable(GL_BLEND);
    org.lwjgl.opengl.GL14C.glBlendFuncSeparate(
        org.lwjgl.opengl.GL11C.GL_SRC_ALPHA,
        org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA,
        org.lwjgl.opengl.GL11C.GL_ONE,
        org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA);
    job.blendSetup().run();

    this.bindTranslucentColorProgram(
        stack, colorProgram, slot, job, voxyMvp, vanillaMvp, bound, reverseDepth, false);

    glBindVertexArray(this.fullscreenVao);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    // (3b) Behind-layers blend: composite the translucent layers behind the front surface (e.g.
    // water behind stained glass). The shader subtracts the front surface's premultiplied flat
    // colour from tgbufferAccum and outputs the remainder; single-layer cases (behind==0) discard.
    // Uses premultiplied OVER blend (ONE, ONE_MINUS_SRC_ALPHA). Same stencil (== 0) as the front
    // surface pass to confine to distant pixels.
    var behindShader = this.programs.behindLayers();
    if (behindShader != null) {
      glEnable(GL_BLEND);
      org.lwjgl.opengl.GL14C.glBlendFuncSeparate(
          org.lwjgl.opengl.GL11C.GL_ONE,
          org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA,
          org.lwjgl.opengl.GL11C.GL_ONE,
          org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA);
      behindShader.shader().bind();
      glUniform1i(behindShader.tgbuffer0Uniform(), GBUFFER0_TEXTURE_UNIT);
      glUniform1i(behindShader.tgbuffer1Uniform(), GBUFFER1_TEXTURE_UNIT);
      glUniform1i(behindShader.accumUniform(), GBUFFER2_TEXTURE_UNIT);
      if (behindShader.lightmapUniform() >= 0) {
        glUniform1i(behindShader.lightmapUniform(), LIGHTMAP_TEXTURE_UNIT);
      }
      glUniform2f(behindShader.sharedSizeUniform(), slot.width(), slot.height());
      glUniform2f(behindShader.targetSizeUniform(), job.outputWidth(), job.outputHeight());
      this.bindSharedTexture(GBUFFER0_TEXTURE_UNIT, slot.textureTarget(), slot.tgbuffer0Texture());
      this.bindSharedTexture(GBUFFER1_TEXTURE_UNIT, slot.textureTarget(), slot.tgbuffer1Texture());
      this.bindSharedTexture(
          GBUFFER2_TEXTURE_UNIT, slot.textureTarget(), slot.tgbufferAccumTexture());
      LightMapHelper.bind(LIGHTMAP_TEXTURE_UNIT);
      glBindVertexArray(this.fullscreenVao);
      glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    }

    // (4) Write the translucent distant depth into irisPrivateDepthTexture so the shader pack's
    // vxDepthTexTrans detects LOD translucent pixels (deferred1.glsl: z0lod < 1.0). Rebind the
    // bridge FBO with irisPrivateDepthTexture as depth-stencil: its opaque stencil (== 0 where
    // the near scene is empty) masks writes, and GL_LEQUAL prevents overwriting closer opaque
    // terrain. This is a no-op depth-only pass: no colour output.
    var depthWriteShader = this.programs.translucentDepthWrite();
    if (depthWriteShader != null && this.irisPrivateDepthTexture != 0) {
      glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.framebuffer);
      glFramebufferTexture2D(
          GL_DRAW_FRAMEBUFFER,
          GL_DEPTH_STENCIL_ATTACHMENT,
          GL_TEXTURE_2D,
          this.irisPrivateDepthTexture,
          0);
      org.lwjgl.opengl.GL11C.glDrawBuffer(GL_NONE);
      glColorMask(false, false, false, false);
      glDepthMask(true);
      glEnable(GL_DEPTH_TEST);
      glDepthFunc(GL_LEQUAL);
      glStencilMask(0x00);
      glStencilFunc(GL_EQUAL, 0, 0xFF);
      glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
      glDisable(GL_BLEND);

      depthWriteShader.shader().bind();
      glUniform1i(depthWriteShader.tgbuffer1Uniform(), GBUFFER1_TEXTURE_UNIT);
      glUniform2f(depthWriteShader.sharedSizeUniform(), slot.width(), slot.height());
      glUniform2f(depthWriteShader.targetSizeUniform(), job.outputWidth(), job.outputHeight());
      this.bindSharedTexture(GBUFFER1_TEXTURE_UNIT, slot.textureTarget(), slot.tgbuffer1Texture());
      glBindVertexArray(this.fullscreenVao);
      glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    }

    glDisable(GL_STENCIL_TEST);
    glStencilMask(0xFF);
    return true;
  }

  /**
   * Binds the translucent colour program's samplers, sizes and matrices, then the tgbuffer
   * textures. Shared by both translucent targets; vanilla additionally binds the MC lightmap (its
   * built-in water patch samples it), while the strict Iris path binds the pack's UBO + samplers.
   */
  private void bindTranslucentColorProgram(
      MemoryStack stack,
      TranslucentBridgeProgram colorProgram,
      DistantGbufferSlot slot,
      DistantBridgeJob job,
      Matrix4fc voxyMvp,
      Matrix4fc vanillaMvp,
      LoadedVolumeBound bound,
      boolean reverseDepth,
      boolean vanilla) {
    colorProgram.shader().bind();
    glUniform1i(colorProgram.tgbuffer0TexUniform(), GBUFFER0_TEXTURE_UNIT);
    glUniform1i(colorProgram.tgbuffer1TexUniform(), GBUFFER1_TEXTURE_UNIT);
    if (colorProgram.lightmapTexUniform() >= 0) {
      glUniform1i(colorProgram.lightmapTexUniform(), LIGHTMAP_TEXTURE_UNIT);
    }
    if (colorProgram.lightSamplerUniform() >= 0) {
      glUniform1i(colorProgram.lightSamplerUniform(), LIGHTMAP_TEXTURE_UNIT);
    }
    glUniform2f(colorProgram.sharedSizeUniform(), slot.width(), slot.height());
    glUniform2f(colorProgram.targetSizeUniform(), job.outputWidth(), job.outputHeight());
    if (colorProgram.invVoxyMvpUniform() >= 0) {
      FloatBuffer matrixBuffer = stack.mallocFloat(16);
      new Matrix4f(voxyMvp).invert().get(matrixBuffer);
      glUniformMatrix4fv(colorProgram.invVoxyMvpUniform(), false, matrixBuffer);
    }
    if (colorProgram.vanillaMvpUniform() >= 0) {
      FloatBuffer matrixBuffer = stack.mallocFloat(16);
      vanillaMvp.get(matrixBuffer);
      glUniformMatrix4fv(colorProgram.vanillaMvpUniform(), false, matrixBuffer);
    }
    this.bindSharedTexture(GBUFFER0_TEXTURE_UNIT, slot.textureTarget(), slot.tgbuffer0Texture());
    this.bindSharedTexture(GBUFFER1_TEXTURE_UNIT, slot.textureTarget(), slot.tgbuffer1Texture());
    if (vanilla) {
      if (colorProgram.tgbufferAccumTexUniform() >= 0) {
        glUniform1i(colorProgram.tgbufferAccumTexUniform(), GBUFFER2_TEXTURE_UNIT);
        this.bindSharedTexture(
            GBUFFER2_TEXTURE_UNIT, slot.textureTarget(), slot.tgbufferAccumTexture());
      }
      LightMapHelper.bind(LIGHTMAP_TEXTURE_UNIT);
      // Vanilla water clips against the loaded volume in-shader (P1); Iris does it via the
      // stencil-mask pass instead, so its colour program has no bound uniforms to set here.
      this.bindBoundForVanilla(
          colorProgram.boundDepthTexUniform(),
          colorProgram.boundSizeUniform(),
          colorProgram.boundEnabledUniform(),
          bound);
      FogCapture.setVanillaFogUniforms(
          colorProgram.fogParamsUniform(),
          colorProgram.fogColorUniform(),
          colorProgram.fogShapeUniform());
    } else {
      this.bindShaderPackResources(job);
    }
  }

  /**
   * Strict Iris path, aligned with voxy-fabric's {@code AbstractRenderPipeline.initDepthStencil} +
   * {@code IrisVoxyRenderPipeline} shaderDepthHackFix. A single colour pass shades the distant
   * terrain into the Iris colour targets while depth-testing/writing against a Voxy-private
   * depth-STENCIL attachment, and the Iris main depth target is never written
   * (renderToVanillaDepth=false).
   *
   * <p>The private depth-stencil is what the shader pack samples as {@code vxDepthTexOpaque} /
   * {@code vxDepthTexTrans}, so it must end up holding voxy-ONLY, voxy-NDC depth: the distant
   * geometry's Voxy-projection depth where Voxy drew, and the far value (1.0) everywhere else. Two
   * properties the pack's deferred passes rely on (see Complementary deferred1.glsl):
   *
   * <ul>
   *   <li>{@code z0lod = texelFetch(vxDepthTexTrans, p).r; if (z0lod < 1.0) { ...LOD pixel... }} —
   *       so non-Voxy pixels MUST read 1.0, otherwise the whole screen is treated as LOD and the
   *       lodShadow/SSAO math corrupts the distant colour to black.
   *   <li>{@code viewPosLod = vxProjInv * (vec4(texCoord, z0lod, 1) * 2 - 1)} — so the stored depth
   *       MUST be Voxy NDC (g.depth), not the vanilla-remapped depth the non-Iris path writes.
   * </ul>
   *
   * <p>We achieve voxy-only depth via a stencil coverage mask instead of an in-shader near-depth
   * sample: Apple's GL4.1 driver SIGSEGVs at 16 fragment texture units and Complementary already
   * binds 12 pack samplers + our 3 gbuffer samplers (= 15), so the colour program cannot afford a
   * uSourceDepthTex unit. The mask pass (a separate 1-sampler program) marks every pixel that has
   * near-scene geometry with stencil=1; the colour pass then draws only where stencil==0 (sky / no
   * near geometry), exactly like voxy-fabric's {@code glStencilFunc(GL_EQUAL, 1)} "render only
   * where there isn't mc terrain". Pixels Voxy skips keep the cleared far depth (1.0), giving the
   * voxy-only result without any depth-reset hack.
   */
  boolean runOpaquePass(
      MemoryStack stack,
      BridgeProgram colorProgram,
      DistantGbufferSlot slot,
      DistantBridgeJob job,
      Matrix4fc voxyMvp,
      Matrix4fc vanillaMvp,
      int sourceDepthTexture,
      int sourceDepthWidth,
      int sourceDepthHeight,
      boolean useManualDepthMask,
      boolean reverseDepth,
      boolean strict) {
    if (!strict) {
      // Vanilla specialization: one colour pass straight into the source framebuffer (MC main
      // target) with a hardware depth test against the real scene depth, plus the optional
      // in-shader near-depth mask. It writes MC depth for later passes. This is the forced
      // divergence from the strict branch below: the MC framebuffer has a real, hardware-testable
      // depth attachment and must keep MC depth, whereas the strict Iris targets have no
      // stencil-testable shared depth and must emit voxy-only private depth.
      glBindFramebuffer(GL_DRAW_FRAMEBUFFER, job.sourceFramebuffer());

      glViewport(0, 0, job.outputWidth(), job.outputHeight());
      boolean colorWrite = job.colorWriteEnabled();
      glColorMask(colorWrite, colorWrite, colorWrite, colorWrite);
      glEnable(GL_DEPTH_TEST);
      glDepthFunc(reverseDepth ? GL_GEQUAL : GL_LEQUAL);
      glDepthMask(true);
      glDisable(GL_BLEND);

      colorProgram.shader().bind();
      this.setReconstructionUniforms(stack, colorProgram, slot, job, voxyMvp, vanillaMvp);
      this.setNearMaskUniforms(
          colorProgram, sourceDepthWidth, sourceDepthHeight, reverseDepth, useManualDepthMask);
      if (colorProgram.lightmapTexUniform() >= 0) {
        glUniform1i(colorProgram.lightmapTexUniform(), LIGHTMAP_TEXTURE_UNIT);
      }
      if (colorProgram.lightSamplerUniform() >= 0) {
        glUniform1i(colorProgram.lightSamplerUniform(), LIGHTMAP_TEXTURE_UNIT);
      }
      FogCapture.setVanillaFogUniforms(
          colorProgram.fogParamsUniform(),
          colorProgram.fogColorUniform(),
          colorProgram.fogShapeUniform());

      this.bindGbufferTextures(slot);
      if (useManualDepthMask) {
        this.bind2DTexture(SOURCE_DEPTH_TEXTURE_UNIT, sourceDepthTexture);
      }
      // The MC lightmap is bound for the vanilla patch (direct sample).
      LightMapHelper.bind(LIGHTMAP_TEXTURE_UNIT);
      this.bindShaderPackResources(job);

      glBindVertexArray(this.fullscreenVao);
      glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
      return true;
    }

    if (!this.prepareDirectIrisOpaque(
        stack, job, sourceDepthTexture, sourceDepthWidth, sourceDepthHeight, reverseDepth)) {
      return false;
    }

    colorProgram.shader().bind();
    this.setReconstructionUniforms(stack, colorProgram, slot, job, voxyMvp, vanillaMvp);
    this.bindGbufferTextures(slot);
    this.bindShaderPackResources(job);

    glBindVertexArray(this.fullscreenVao);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    this.finishDirectIrisOpaque();
    return true;
  }

  boolean prepareDirectIrisOpaque(
      MemoryStack stack,
      DistantBridgeJob job,
      int sourceDepthTexture,
      int sourceDepthWidth,
      int sourceDepthHeight,
      boolean reverseDepth) {
    this.directTranslucentDepthValid = false;
    int privateDepth = this.ensureIrisPrivateDepth(job.outputWidth(), job.outputHeight());
    if (privateDepth == 0) {
      return false;
    }
    var maskShader = this.programs.stencilMask();
    if (maskShader == null) {
      return false;
    }
    if (!this.bindTargetFramebuffer(stack, job, privateDepth, true)) {
      return false;
    }
    glViewport(0, 0, job.outputWidth(), job.outputHeight());
    glDisable(GL_SCISSOR_TEST);

    // Keep the colour targets produced by Iris, but reset Voxy-only depth and near coverage.
    glDepthMask(true);
    glStencilMask(0xFF);
    glColorMask(false, false, false, false);
    glClearDepth(1.0);
    glClearStencil(0);
    glClear(GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

    glDisable(GL_DEPTH_TEST);
    glDepthMask(false);
    glEnable(GL_STENCIL_TEST);
    glStencilMask(0xFF);
    glStencilFunc(GL_ALWAYS, 1, 0xFF);
    glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
    maskShader.shader().bind();
    glUniform1i(maskShader.nearDepthUniform(), SOURCE_DEPTH_TEXTURE_UNIT);
    glUniform2f(maskShader.nearSizeUniform(), sourceDepthWidth, sourceDepthHeight);
    glUniform2f(maskShader.targetSizeUniform(), job.outputWidth(), job.outputHeight());
    glUniform1i(maskShader.reverseDepthUniform(), reverseDepth ? 1 : 0);
    this.bind2DTexture(SOURCE_DEPTH_TEXTURE_UNIT, sourceDepthTexture);
    glBindVertexArray(this.fullscreenVao);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    boolean colorWrite = job.colorWriteEnabled();
    glColorMask(colorWrite, colorWrite, colorWrite, colorWrite);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);
    glDepthMask(true);
    glStencilMask(0x00);
    glStencilFunc(GL_EQUAL, 0, 0xFF);
    glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
    glDisable(GL_BLEND);
    return true;
  }

  void bindDirectIrisResources(DistantBridgeJob job) {
    this.bindShaderPackResources(job);
  }

  void finishDirectIrisOpaque() {
    glDisable(GL_STENCIL_TEST);
    glStencilMask(0xFF);
  }

  boolean prepareDirectIrisTranslucent(
      MemoryStack stack,
      DistantBridgeJob job,
      int sourceDepthTexture,
      int sourceDepthWidth,
      int sourceDepthHeight,
      boolean reverseDepth) {
    int translucentDepth = this.ensureTranslucentMaskDepth(job.outputWidth(), job.outputHeight());
    if (translucentDepth == 0
        || this.irisPrivateDepthTexture == 0
        || !this.copyIrisOpaqueDepthToTranslucent(
            job.outputWidth(), job.outputHeight(), translucentDepth)) {
      return false;
    }
    var maskShader = this.programs.stencilMask();
    if (maskShader == null || !this.bindTargetFramebuffer(stack, job, translucentDepth, true)) {
      return false;
    }
    glViewport(0, 0, job.outputWidth(), job.outputHeight());
    glDisable(GL_SCISSOR_TEST);

    // Preserve the copied opaque depth but rebuild coverage from the current noTranslucents depth.
    glColorMask(false, false, false, false);
    glDepthMask(false);
    glStencilMask(0xFF);
    glClearStencil(0);
    glClear(GL_STENCIL_BUFFER_BIT);

    glDisable(GL_DEPTH_TEST);
    glDisable(GL_BLEND);
    glEnable(GL_STENCIL_TEST);
    glStencilFunc(GL_ALWAYS, 1, 0xFF);
    glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
    maskShader.shader().bind();
    glUniform1i(maskShader.nearDepthUniform(), SOURCE_DEPTH_TEXTURE_UNIT);
    glUniform2f(maskShader.nearSizeUniform(), sourceDepthWidth, sourceDepthHeight);
    glUniform2f(maskShader.targetSizeUniform(), job.outputWidth(), job.outputHeight());
    glUniform1i(maskShader.reverseDepthUniform(), reverseDepth ? 1 : 0);
    this.bind2DTexture(SOURCE_DEPTH_TEXTURE_UNIT, sourceDepthTexture);
    glBindVertexArray(this.fullscreenVao);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    this.directTranslucentDepthValid = true;
    return true;
  }

  void beginDirectIrisTranslucentColor(DistantBridgeJob job) {
    boolean colorWrite = job.colorWriteEnabled();
    glColorMask(colorWrite, colorWrite, colorWrite, colorWrite);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);
    glDepthMask(true);
    glStencilMask(0x00);
    glStencilFunc(GL_EQUAL, 0, 0xFF);
    glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
    glEnable(GL_BLEND);
    org.lwjgl.opengl.GL14C.glBlendFuncSeparate(
        org.lwjgl.opengl.GL11C.GL_SRC_ALPHA,
        org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA,
        org.lwjgl.opengl.GL11C.GL_ONE,
        org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA);
    job.blendSetup().run();
    this.bindShaderPackResources(job);
  }

  void finishDirectIrisTranslucent() {
    glDisable(GL_STENCIL_TEST);
    glStencilMask(0xFF);
  }

  private boolean copyIrisOpaqueDepthToTranslucent(int width, int height, int translucentDepth) {
    glBindFramebuffer(GL_READ_FRAMEBUFFER, this.depthCopyFramebuffer);
    glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, 0, 0);
    glFramebufferTexture2D(
        GL_READ_FRAMEBUFFER,
        GL_DEPTH_STENCIL_ATTACHMENT,
        GL_TEXTURE_2D,
        this.irisPrivateDepthTexture,
        0);
    glReadBuffer(GL_NONE);
    boolean readComplete = glCheckFramebufferStatus(GL_READ_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.nearDepthFramebuffer);
    glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, 0, 0);
    glFramebufferTexture2D(
        GL_DRAW_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, translucentDepth, 0);
    org.lwjgl.opengl.GL11C.glDrawBuffer(GL_NONE);
    boolean drawComplete = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
    if (readComplete && drawComplete) {
      glBlitFramebuffer(
          0,
          0,
          width,
          height,
          0,
          0,
          width,
          height,
          GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT,
          GL_NEAREST);
    }
    glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, 0, 0);
    glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, 0, 0);
    glFramebufferTexture2D(
        GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, this.nearDepthTexture, 0);
    return readComplete && drawComplete;
  }

  /**
   * Binds {@link #framebuffer} with the Iris colour targets + the given depth attachment texture.
   *
   * @param depthStencil when true {@code depthTexture} is a GL_DEPTH24_STENCIL8 texture and is
   *     attached to GL_DEPTH_STENCIL_ATTACHMENT (the strict Iris path needs the stencil for the
   *     near-scene coverage mask); when false it is a plain depth texture on GL_DEPTH_ATTACHMENT
   *     (the debug visualisations, which reuse the Iris main depth).
   */
  private boolean bindTargetFramebuffer(
      MemoryStack stack, DistantBridgeJob job, int depthTexture, boolean depthStencil) {
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.framebuffer);
    int[] targetTextures = job.targetTextureIds();
    var drawBuffers = stack.mallocInt(targetTextures.length);
    for (int i = 0; i < targetTextures.length; i++) {
      glFramebufferTexture2D(
          GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, GL_TEXTURE_2D, targetTextures[i], 0);
      drawBuffers.put(i, GL_COLOR_ATTACHMENT0 + i);
    }
    glFramebufferTexture2D(
        GL_DRAW_FRAMEBUFFER,
        depthStencil ? GL_DEPTH_STENCIL_ATTACHMENT : GL_DEPTH_ATTACHMENT,
        GL_TEXTURE_2D,
        depthTexture,
        0);
    glDrawBuffers(drawBuffers);
    int framebufferStatus = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER);
    if (framebufferStatus != GL_FRAMEBUFFER_COMPLETE) {
      Logger.error(
          "Voxy GL41Metal distant bridge framebuffer incomplete: 0x"
              + Integer.toHexString(framebufferStatus));
      return false;
    }
    return true;
  }

  /** gbuffer0-2 sampler units + reconstruction sizes + matrices shared by every pass. */
  private void setReconstructionUniforms(
      MemoryStack stack,
      BridgeProgram program,
      DistantGbufferSlot slot,
      DistantBridgeJob job,
      Matrix4fc voxyMvp,
      Matrix4fc vanillaMvp) {
    glUniform1i(program.gbuffer0TexUniform(), GBUFFER0_TEXTURE_UNIT);
    glUniform1i(program.gbuffer1TexUniform(), GBUFFER1_TEXTURE_UNIT);
    glUniform1i(program.gbuffer2TexUniform(), GBUFFER2_TEXTURE_UNIT);
    glUniform2f(program.sharedSizeUniform(), slot.width(), slot.height());
    glUniform2f(program.targetSizeUniform(), job.outputWidth(), job.outputHeight());
    FloatBuffer matrixBuffer = stack.mallocFloat(16);
    new Matrix4f(voxyMvp).invert().get(matrixBuffer);
    glUniformMatrix4fv(program.invVoxyMvpUniform(), false, matrixBuffer);
    matrixBuffer.clear();
    vanillaMvp.get(matrixBuffer);
    glUniformMatrix4fv(program.vanillaMvpUniform(), false, matrixBuffer);
  }

  /** uSourceDepthTex unit + near-mask parameters (only present in masking programs). */
  private void setNearMaskUniforms(
      BridgeProgram program,
      int sourceDepthWidth,
      int sourceDepthHeight,
      boolean reverseDepth,
      boolean useManualDepthMask) {
    glUniform1i(program.sourceDepthTexUniform(), SOURCE_DEPTH_TEXTURE_UNIT);
    glUniform2f(program.sourceDepthSizeUniform(), sourceDepthWidth, sourceDepthHeight);
    glUniform1i(program.reverseDepthUniform(), reverseDepth ? 1 : 0);
    glUniform1i(program.useManualDepthMaskUniform(), useManualDepthMask ? 1 : 0);
  }

  private void bindGbufferTextures(DistantGbufferSlot slot) {
    this.bindSharedTexture(GBUFFER0_TEXTURE_UNIT, slot.textureTarget(), slot.gbuffer0Texture());
    this.bindSharedTexture(GBUFFER1_TEXTURE_UNIT, slot.textureTarget(), slot.gbuffer1Texture());
    this.bindSharedTexture(GBUFFER2_TEXTURE_UNIT, slot.textureTarget(), slot.gbuffer2Texture());
  }

  int snapshotNearDepth(int depthTexture, int width, int height) {
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
          "Voxy GL41Metal depth read framebuffer incomplete: 0x" + Integer.toHexString(readStatus));
      return 0;
    }
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.nearDepthFramebuffer);
    int drawStatus = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER);
    if (drawStatus != GL_FRAMEBUFFER_COMPLETE) {
      Logger.error(
          "Voxy GL41Metal depth snapshot framebuffer incomplete: 0x"
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
      org.lwjgl.opengl.GL11C.glDeleteTextures(this.nearDepthTexture);
      this.nearDepthTexture = 0;
    }
    this.nearDepthTexture = org.lwjgl.opengl.GL11C.glGenTextures();
    this.nearDepthWidth = width;
    this.nearDepthHeight = height;
    glActiveTexture(GL_TEXTURE0 + SOURCE_DEPTH_TEXTURE_UNIT);
    org.lwjgl.opengl.GL11C.glBindTexture(GL_TEXTURE_2D, this.nearDepthTexture);
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

  /**
   * Lazily (re)allocates the Voxy-private Iris depth-stencil attachment. GL_DEPTH24_STENCIL8
   * mirrors voxy-fabric's {@code AbstractRenderPipeline.fb} ({@code new
   * DepthFramebuffer(GL_DEPTH24_STENCIL8)}): the depth component is what shader packs sample as
   * {@code vxDepthTexOpaque}/{@code vxDepthTexTrans} ({@code texelFetch(...).r} -> normalised [0,1]
   * Voxy-NDC depth), and the stencil component is the near-scene coverage mask that lets the
   * distant terrain render ONLY where the near scene did not (see runOpaquePass). Sized to the Iris
   * output target.
   */
  private int ensureIrisPrivateDepth(int width, int height) {
    if (width <= 0 || height <= 0) {
      return 0;
    }
    if (this.irisPrivateDepthTexture != 0
        && this.irisPrivateDepthWidth == width
        && this.irisPrivateDepthHeight == height) {
      return this.irisPrivateDepthTexture;
    }
    if (this.irisPrivateDepthTexture != 0) {
      org.lwjgl.opengl.GL11C.glDeleteTextures(this.irisPrivateDepthTexture);
      this.irisPrivateDepthTexture = 0;
    }
    this.irisPrivateDepthTexture = org.lwjgl.opengl.GL11C.glGenTextures();
    this.irisPrivateDepthWidth = width;
    this.irisPrivateDepthHeight = height;
    // Bind on the scratch source-depth unit; the draw path rebinds whatever sampler units it needs
    // before drawing, and this texture is consumed as an FBO attachment / shader-pack sampler, not
    // a bridge reconstruction sampler.
    glActiveTexture(GL_TEXTURE0 + SOURCE_DEPTH_TEXTURE_UNIT);
    org.lwjgl.opengl.GL11C.glBindTexture(GL_TEXTURE_2D, this.irisPrivateDepthTexture);
    glTexImage2D(
        GL_TEXTURE_2D,
        0,
        GL_DEPTH24_STENCIL8,
        width,
        height,
        0,
        GL_DEPTH_STENCIL,
        GL_UNSIGNED_INT_24_8,
        0L);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    // GL_TEXTURE_COMPARE_MODE NONE: shader packs read the raw depth value (texelFetch .r), they do
    // not use it as a shadow sampler, so it must not be in compare mode.
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_NONE);
    return this.irisPrivateDepthTexture;
  }

  int irisOpaqueDepthTextureId() {
    return this.irisPrivateDepthTexture;
  }

  int irisTranslucentDepthTextureId() {
    return this.directTranslucentDepthValid
        ? this.translucentMaskDepthStencil
        : this.irisPrivateDepthTexture;
  }

  /**
   * Lazily (re)allocates the translucent pass's private depth-STENCIL attachment. Distinct from
   * {@link #irisPrivateDepthTexture} so the translucent stencil mask never clobbers the opaque
   * Voxy-NDC depth the pack samples as vxDepthTexOpaque. The stencil stores the near-coverage mask;
   * the direct drawlist path also writes translucent geometry depth for vxDepthTexTrans.
   */
  private int ensureTranslucentMaskDepth(int width, int height) {
    if (width <= 0 || height <= 0) {
      return 0;
    }
    if (this.translucentMaskDepthStencil != 0
        && this.translucentMaskWidth == width
        && this.translucentMaskHeight == height) {
      return this.translucentMaskDepthStencil;
    }
    if (this.translucentMaskDepthStencil != 0) {
      org.lwjgl.opengl.GL11C.glDeleteTextures(this.translucentMaskDepthStencil);
      this.translucentMaskDepthStencil = 0;
    }
    this.translucentMaskDepthStencil = org.lwjgl.opengl.GL11C.glGenTextures();
    this.translucentMaskWidth = width;
    this.translucentMaskHeight = height;
    glActiveTexture(GL_TEXTURE0 + SOURCE_DEPTH_TEXTURE_UNIT);
    org.lwjgl.opengl.GL11C.glBindTexture(GL_TEXTURE_2D, this.translucentMaskDepthStencil);
    glTexImage2D(
        GL_TEXTURE_2D,
        0,
        GL_DEPTH24_STENCIL8,
        width,
        height,
        0,
        GL_DEPTH_STENCIL,
        GL_UNSIGNED_INT_24_8,
        0L);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_NONE);
    return this.translucentMaskDepthStencil;
  }

  /**
   * Strict-Iris loaded-volume clip (P1): marks stencil := 1 where a distant fragment lies inside
   * the Sodium loaded volume, ADDING to the near-coverage stencil already set by the caller. Must
   * run with the caller's mask stencil state (GL_ALWAYS / GL_REPLACE, ref 1, depth+colour writes
   * off) already configured. {@code distantDepthCarrier} is the {@code .x}-depth shared texture for
   * this pass (gbuffer1 for opaque, tgbuffer1 for translucent); its depth is compared directly
   * against the Voxy-NDC bound (no reprojection). No-op when the bound is disabled.
   */
  private void runBoundMaskPass(
      LoadedVolumeBound bound,
      DistantGbufferSlot slot,
      DistantBridgeJob job,
      int distantDepthCarrier) {
    if (bound == null || !bound.enabled()) {
      return;
    }
    var maskShader = this.programs.boundMask();
    if (maskShader == null) {
      return;
    }
    maskShader.shader().bind();
    glUniform1i(maskShader.distantDepthUniform(), GBUFFER0_TEXTURE_UNIT);
    glUniform1i(maskShader.boundDepthUniform(), SOURCE_DEPTH_TEXTURE_UNIT);
    glUniform2f(maskShader.boundSizeUniform(), bound.width(), bound.height());
    glUniform2f(maskShader.sharedSizeUniform(), slot.width(), slot.height());
    glUniform2f(maskShader.targetSizeUniform(), job.outputWidth(), job.outputHeight());
    this.bindSharedTexture(GBUFFER0_TEXTURE_UNIT, slot.textureTarget(), distantDepthCarrier);
    this.bind2DTexture(SOURCE_DEPTH_TEXTURE_UNIT, bound.texture());
    glBindVertexArray(this.fullscreenVao);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
  }

  /**
   * Binds the loaded-volume bound texture + uniforms for a vanilla/debug colour program (the
   * in-shader clip arm). Guards every location so it is a no-op when the block was stripped (e.g.
   * the debug main never calls {@code isInsideLoadedBound}). The strict Iris colour program never
   * goes through here - it clips via {@link #runBoundMaskPass} instead.
   */
  private void bindBoundForVanilla(
      int boundDepthTexUniform,
      int boundSizeUniform,
      int boundEnabledUniform,
      LoadedVolumeBound bound) {
    boolean enabled = bound != null && bound.enabled();
    if (boundEnabledUniform >= 0) {
      glUniform1i(boundEnabledUniform, enabled ? 1 : 0);
    }
    // ALWAYS point the sampler at its dedicated unit, even when the bound is disabled. A sampler
    // uniform left at its default value (0) aliases uTgbuffer0Tex's unit, and two samplers of
    // DIFFERENT types (sampler2D vs sampler2DRect) on one unit make the whole draw fail with
    // GL_INVALID_OPERATION - the entire distant-water pass silently vanished whenever the bound
    // was disabled (e.g. camera above build height, or before Sodium reported any section).
    if (boundDepthTexUniform >= 0) {
      glUniform1i(boundDepthTexUniform, BOUND_TEXTURE_UNIT);
    }
    if (!enabled) {
      return;
    }
    if (boundSizeUniform >= 0) {
      glUniform2f(boundSizeUniform, bound.width(), bound.height());
    }
    this.bind2DTexture(BOUND_TEXTURE_UNIT, bound.texture());
  }

  int findFramebufferDepthTexture(int framebuffer) {
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

  private void bindSharedTexture(int unit, int target, int texture) {
    glActiveTexture(GL_TEXTURE0 + unit);
    org.lwjgl.opengl.GL11C.glBindTexture(target, texture);
    glBindSampler(unit, 0);
    glTexParameteri(target, GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
    glTexParameteri(target, GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
  }

  private void bind2DTexture(int unit, int texture) {
    glActiveTexture(GL_TEXTURE0 + unit);
    org.lwjgl.opengl.GL11C.glBindTexture(GL_TEXTURE_2D, texture);
    glBindSampler(unit, 0);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
  }

  private void bindShaderPackResources(DistantBridgeJob job) {
    int size = job.uniformBufferBytes();
    if (size > 0) {
      this.ensureUniformBuffer(size);
      // Pack writes straight into our native scratch and we push the whole region in one shot.
      // No multi-frame fencing is needed: the strict bridge draws synchronously inside the same
      // render call that uploads, and the next frame overwrites the entire UBO contents anyway.
      job.uniformUpdater().accept(this.uniformScratchAddr);
      glBindBuffer(GL_UNIFORM_BUFFER, this.uniformBuffer);
      nglBufferSubData(GL_UNIFORM_BUFFER, 0L, size, this.uniformScratchAddr);
      glBindBufferBase(
          GL_UNIFORM_BUFFER, IrisBridgeShaderBindings.UNIFORM_BINDING_POINT, this.uniformBuffer);
    }
    job.resourceBinder().run();
  }

  private void ensureUniformBuffer(int size) {
    if (this.uniformBuffer != 0 && this.uniformBufferBytes == size) {
      return;
    }
    if (this.uniformBuffer != 0) {
      glDeleteBuffers(this.uniformBuffer);
      this.uniformBuffer = 0;
    }
    if (this.uniformScratchAddr != 0L) {
      MemoryUtil.nmemFree(this.uniformScratchAddr);
      this.uniformScratchAddr = 0L;
    }
    this.uniformBuffer = glGenBuffers();
    glBindBuffer(GL_UNIFORM_BUFFER, this.uniformBuffer);
    // GL4.1-era allocation: glBufferData with a null data pointer is the GL3.1+ way to reserve a
    // mutable UBO of the requested size. Per-frame uploads go through glBufferSubData above.
    glBufferData(GL_UNIFORM_BUFFER, (long) size, GL_DYNAMIC_DRAW);
    this.uniformScratchAddr = MemoryUtil.nmemAlloc(size);
    // nmemAlloc does not zero: std140 padding slots (vec3 tails, etc.) are never written by the
    // per-uniform updaters, so without this they would upload per-run garbage and the shader could
    // read uninitialised lighting uniforms. Zero once on (re)alloc; the updater overwrites the real
    // slots each frame and the padding stays deterministically zero.
    MemoryUtil.memSet(this.uniformScratchAddr, 0, size);
    this.uniformBufferBytes = size;
  }

  void close() {
    if (this.translucentMaskDepthStencil != 0) {
      org.lwjgl.opengl.GL11C.glDeleteTextures(this.translucentMaskDepthStencil);
      this.translucentMaskDepthStencil = 0;
    }
    if (this.uniformBuffer != 0) {
      glDeleteBuffers(this.uniformBuffer);
      this.uniformBuffer = 0;
    }
    if (this.uniformScratchAddr != 0L) {
      MemoryUtil.nmemFree(this.uniformScratchAddr);
      this.uniformScratchAddr = 0L;
    }
    this.uniformBufferBytes = 0;
    if (this.nearDepthTexture != 0) {
      org.lwjgl.opengl.GL11C.glDeleteTextures(this.nearDepthTexture);
      this.nearDepthTexture = 0;
    }
    if (this.irisPrivateDepthTexture != 0) {
      org.lwjgl.opengl.GL11C.glDeleteTextures(this.irisPrivateDepthTexture);
      this.irisPrivateDepthTexture = 0;
    }
    glDeleteVertexArrays(this.fullscreenVao);
    glDeleteFramebuffers(this.nearDepthFramebuffer);
    glDeleteFramebuffers(this.depthCopyFramebuffer);
    glDeleteFramebuffers(this.framebuffer);
  }
}
