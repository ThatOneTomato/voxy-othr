package me.cortex.voxy.client.core.rendering.backend.gl41metal;

import static org.lwjgl.opengl.GL11C.GL_ALWAYS;
import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_COLOR_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_FUNC;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_EQUAL;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_GEQUAL;
import static org.lwjgl.opengl.GL11C.GL_GREATER;
import static org.lwjgl.opengl.GL11C.GL_KEEP;
import static org.lwjgl.opengl.GL11C.GL_LEQUAL;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_NONE;
import static org.lwjgl.opengl.GL11C.GL_REPLACE;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_1D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL11C.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11C.glClear;
import static org.lwjgl.opengl.GL11C.glClearDepth;
import static org.lwjgl.opengl.GL11C.glClearStencil;
import static org.lwjgl.opengl.GL11C.glColorMask;
import static org.lwjgl.opengl.GL11C.glDepthFunc;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGetBooleanv;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glGetIntegerv;
import static org.lwjgl.opengl.GL11C.glReadBuffer;
import static org.lwjgl.opengl.GL11C.glReadPixels;
import static org.lwjgl.opengl.GL11C.glStencilFunc;
import static org.lwjgl.opengl.GL11C.glStencilMask;
import static org.lwjgl.opengl.GL11C.glStencilOp;
import static org.lwjgl.opengl.GL11C.glTexImage2D;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL11C.glViewport;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_BINDING_3D;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL14C.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glBufferData;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL15C.nglBufferSubData;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL20C.glDrawBuffers;
import static org.lwjgl.opengl.GL20C.glGetUniformLocation;
import static org.lwjgl.opengl.GL20C.glUniform1i;
import static org.lwjgl.opengl.GL20C.glUniform2f;
import static org.lwjgl.opengl.GL20C.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_DEPTH24_STENCIL8;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_COMPONENT32F;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_STENCIL;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_STENCIL_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_UNSIGNED_INT_24_8;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
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
import static org.lwjgl.opengl.GL31C.GL_TEXTURE_BINDING_RECTANGLE;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL33C.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33C.glBindSampler;

import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.backend.RenderFrameContext;
import me.cortex.voxy.client.core.rendering.backend.ShaderPatchBridgePayload;
import me.cortex.voxy.client.core.rendering.util.LightMapHelper;
import me.cortex.voxy.client.iris.IrisBridgeShaderBindings;
import me.cortex.voxy.common.Logger;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Single OpenGL bridge that samples the Metal-produced shared distant gbuffer, reconstructs {@link
 * me.cortex.voxy.client.core.rendering.backend.ShaderPatchBridgePayload VoxyFragmentParameters} and
 * emits the distant terrain fragment through {@code voxy_emitFragment}.
 *
 * <p>This is the OpenGL counterpart of GL46 {@code quads.frag}: the gbuffer decode, depth
 * reprojection and near-depth occlusion mask are shared, and only the {@code voxy_emitFragment}
 * implementation differs between the two {@link DistantBridgeJob} flavours:
 *
 * <ul>
 *   <li>vanilla / no shader pack uses the built-in {@link #VANILLA_PATCH} (GL46 non-patched
 *       lighting: lightmap sample + directional face tint), drawing straight into the source
 *       framebuffer; and
 *   <li>Iris strict uses the shader pack's patch, header, uniforms and SSBOs, drawing into the Iris
 *       render targets.
 * </ul>
 *
 * Keeping both flavours on one shader template and one Java bridge is the design boundary required
 * by the backend contract: the vanilla path must remain a specialization of the same distant output
 * the Iris path generalizes, not a parallel duplicate.
 */
final class GlDistantTerrainBridge implements AutoCloseable {
  private static final int DEBUG_NONE = 0;
  private static final int DEBUG_COLOR = 1;
  private static final int DEBUG_CUSTOM_ID = 2;
  private static final int DEBUG_DEPTH = 3;
  private static final int DEBUG_COVERAGE = 4;
  private static final int DEBUG_LIT_COLOR = 5;

  // Word-boundary regex used by buildFragmentShader() to rewrite gl_FragCoord ->
  // voxy_OverrideFragCoord
  // in patched pack source. Apple's GL4.1 GLSL preprocessor silently refuses to redefine the
  // built-in
  // gl_FragCoord via #define, so we do the substitution at the Java string level before submitting
  // the assembled fragment shader to the driver. See GLSL_GBUFFER_DECODE's voxy_OverrideFragCoord
  // declaration and the buildFragmentShader() comment for the full failure mode.
  private static final Pattern REWRITE_GL_FRAG_COORD = Pattern.compile("\\bgl_FragCoord\\b");
  // Visualises the raw gbuffer lightMap (block, sky, 0). Used to triage "distant turns black in
  // pack mode but shows in litColor" against the hypothesis that Metal is writing sky=0 to the
  // gbuffer for distant LODs (in which case Complementary's lmCoord.y=0 path correctly outputs
  // black even though the MC vanilla lightmap texture in litColor mode still returns a dim
  // ambient at UV (0.03125, 0.03125)).
  private static final int DEBUG_LIGHTMAP = 6;
  private static final int DEBUG_MODE = parseDebugMode();
  // Strict-Iris diagnostic: keep the full strict pipeline (private depth-stencil coverage,
  // colortex0
  // write, Voxy-NDC depth, deferred1 LOD branch) but overwrite the pack's voxy_emitFragment lit
  // colour with the raw gbuffer albedo*tint right before output. Enable with
  // -Dvoxy.gl41metal.debugStrictAlbedo=true. This isolates "does the strict pipeline deliver colour
  // to the deferred LOD branch at all" from "is the pack's lighting maths producing black": if the
  // distant shows flat albedo with this flag but is black without it, the pack lighting is the
  // culprit; if it stays black, the strict colour never reaches deferred1's LOD branch.
  private static final boolean DEBUG_STRICT_ALBEDO =
      Boolean.parseBoolean(System.getProperty("voxy.gl41metal.debugStrictAlbedo", "false"));
  // Which gbuffer field the DEBUG_STRICT_ALBEDO override writes into gbufferData0, so all three
  // "why is the strict distant colour black" hypotheses can be triaged in a single build by setting
  // -Dvoxy.gl41metal.debugStrictAlbedoSource=albedoTint|albedo|tint|white:
  //   albedoTint (default) -> g.colour.rgb * g.tint.rgb (raw unlit surface colour)
  //   albedo               -> g.colour.rgb only (isolate the baked block albedo)
  //   tint                 -> g.tint.rgb only (isolate the biome/vertex tint)
  //   white                -> vec3(0.75) constant (isolate the deferred1 LOD branch: if even a
  //                           constant comes out black, ssao^4/fog/lodShadow is the darkener, not
  //                           the gbuffer)
  private static final String DEBUG_STRICT_ALBEDO_EXPR = parseDebugStrictAlbedoExpr();
  private static final boolean USE_MANUAL_DEPTH_MASK =
      Boolean.parseBoolean(System.getProperty("voxy.gl41metal.manualDepthMask", "true"));
  // Diagnostic for the distant translucent (water) path. When enabled, the vanilla water pass draws
  // a full-screen probe with depth test + blend off: GREEN (opaque) wherever the Metal translucent
  // gbuffer has a covered fragment (water/glass present), and a faint RED wash everywhere else
  // (proving the composite pass itself ran). No green anywhere -> no translucent data reached the
  // tgbuffer (traversal/raster issue); green only where water is -> data is fine and the failure is
  // shading/depth/blend. Enable with -Dvoxy.gl41metal.debugTranslucent=true.
  private static final boolean DEBUG_TRANSLUCENT =
      Boolean.parseBoolean(System.getProperty("voxy.gl41metal.debugTranslucent", "false"));
  // Diagnostic: dump generated bridge fragment shaders to run/bridge_*.frag and log their size +
  // sampler count. Enable with -Dvoxy.gl41metal.dumpShaders=true.
  private static final boolean DUMP_SHADERS =
      Boolean.parseBoolean(System.getProperty("voxy.gl41metal.dumpShaders", "false"));
  // The Iris colour pass embeds a shader pack's whole fragment patch, which Apple's GL4.1 GLSL
  // linker cannot compile as-is -- it SIGSEGVs in glpLLVMGetFunctionGlobalVariableUse while
  // analysing the synthesized global-init routine for the pack's file-scope initializer chain
  // (`vec3 upVec = normalize(gbufferModelView[1].xyz);`, `sunVec = GetSunVector();`, ...). Iris's
  // own programs never hit this because the same preamble lives in a `flat in` vertex varying; our
  // full-screen distant bridge has no vertex stage.
  //
  // hoistGlobalInitializers is the root-cause fix: it rewrites every non-const file-scope
  // initializer `T x = expr;` to a bare `T x;` plus `x = expr;` injected into main(), dissolving
  // the global-init graph that crashes the linker.
  //
  // pruneUnreachableFunctions is a separate cleanup (not part of the root-cause fix) that strips
  // never-reachable library helpers from the patch before compile, mirroring what Iris's
  // (JarJar-nested, non-classpath) glsl-transformer CompatibilityTransformer does. It typically
  // halves the shader size and is kept as cheap defensive armour for future packs even though
  // hoisting alone is what makes Apple's linker accept the program.
  //
  // Both apply only to the Iris colour pass; the vanilla single pass is byte-for-byte unchanged.

  // GL46 quads.frag non-patched lighting expressed as a built-in voxy_emitFragment: sample the MC
  // lightmap with the baked light UV, fold in the conditional tint, then apply the directional face
  // shade. uLightmapTex and voxyQuadFlags are provided by the shared header below.
  private static final String VANILLA_PATCH =
      """
      layout(location = 0) out vec4 voxyVanillaColor;

      float voxyVanillaFaceTint(bool shaded, uint face) {
        if (!shaded) {
          return 1.0;
        }
        if ((face >> 1u) == 1u) {
          return 0.8;
        }
        if ((face >> 1u) == 2u) {
          return 0.6;
        }
        if (face == 1u) {
          return 1.0;
        }
        return 0.5;
      }

      void voxy_emitFragment(VoxyFragmentParameters parameters) {
        vec4 light = texture(uLightmapTex, parameters.lightMap);
        vec4 color = parameters.sampledColour * parameters.tinting * light;
        bool shaded = ((voxyQuadFlags >> 6u) & 1u) != 0u;
        color.rgb *= voxyVanillaFaceTint(shaded, uint(parameters.face));
        voxyVanillaColor = vec4(color.rgb, 1.0);
      }
      """;

  // Fragment texture units. Metal packs distant terrain into 3 shared textures (see
  // quad_raster.metal QuadFragmentOut), so the reconstruction samplers occupy units 0-2. That
  // is the whole point of the packing: Apple GL4.1 caps a fragment program at 16 texture units
  // AND SIGSEGVs at exactly 16, so a usable program needs <= 15. The Iris colour bridge program
  // also binds every shader-pack sampler (Complementary: 12), so the base reconstruction has to
  // stay at 3 (12 + 3 = 15). uSourceDepthTex (near-depth mask) and the MC lightmap follow; they
  // only appear in the masking/vanilla/debug shapes, never in the Iris colour pass.
  private static final int GBUFFER0_TEXTURE_UNIT = 0;
  private static final int GBUFFER1_TEXTURE_UNIT = 1;
  private static final int GBUFFER2_TEXTURE_UNIT = 2;
  private static final int SOURCE_DEPTH_TEXTURE_UNIT = 3;
  private static final int LIGHTMAP_TEXTURE_UNIT = 4;
  // Loaded-volume bound depth (P1). Only the vanilla/debug colour programs sample it (unit 5, well
  // within their spare budget); the strict Iris colour program never binds it - its loaded-volume
  // clip rides the stencil-mask pass, which uses its own units (see GLSL_BOUND_MASK).
  private static final int BOUND_TEXTURE_UNIT = 5;

  private final int framebuffer;
  private final int depthCopyFramebuffer;
  private final int nearDepthFramebuffer;
  private final int fullscreenVao;
  private BridgeProgram program;
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
  private int failedShaderKey = Integer.MIN_VALUE;
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
  // Near-scene coverage stencil-mask program (strict Iris path only). Separate 1-sampler program so
  // the heavyweight colour program can stay at the 15 usable Apple-GL4.1 fragment texture units.
  private Shader stencilMaskProgram;
  private int stencilMaskNearDepthUniform = -1;
  private int stencilMaskNearSizeUniform = -1;
  private int stencilMaskTargetSizeUniform = -1;
  private int stencilMaskReverseUniform = -1;
  private boolean stencilMaskProgramFailed;
  // Behind-layers blend program: composites the translucent layers behind the front surface
  // (e.g. water behind stained glass). The front surface is pack-shaded (step 3), then this pass
  // subtracts the front surface's flat contribution from tgbufferAccum and blends the remainder
  // with premultiplied OVER. Single-layer cases (ocean surface) discard (behind==0) and are no-ops.
  private Shader behindLayersProgram;
  private int behindLayersTgb0Uniform = -1;
  private int behindLayersTgb1Uniform = -1;
  private int behindLayersAccumUniform = -1;
  private int behindLayersLightmapUniform = -1;
  private int behindLayersSharedSizeUniform = -1;
  private int behindLayersTargetSizeUniform = -1;
  private boolean behindLayersProgramFailed;
  // Translucent depth-write program: writes the tgbuffer1.x distant translucent depth into
  // irisPrivateDepthTexture so the shader pack can detect LOD translucent pixels via
  // vxDepthTexTrans. Separate 1-sampler program (like stencilMaskProgram) to avoid adding
  // texture units to the budgeted colour program.
  private Shader translucentDepthProgram;
  private int translucentDepthTgbuffer1Uniform = -1;
  private int translucentDepthSharedSizeUniform = -1;
  private int translucentDepthTargetSizeUniform = -1;
  private boolean translucentDepthProgramFailed;
  // Loaded-volume bound stencil-mask program (strict Iris path only). Marks stencil := 1 where a
  // distant fragment lies inside the Sodium loaded volume, feeding the loaded-volume clip into the
  // SAME stencil coverage the near mask uses (the single forced occlusion divergence). It is a
  // separate program from the colour pass on purpose, so it adds zero texture units to the budgeted
  // Iris colour program (it samples the distant depth carrier + the bound depth with its own
  // units).
  private Shader boundMaskProgram;
  private int boundMaskDistantDepthUniform = -1;
  private int boundMaskBoundDepthUniform = -1;
  private int boundMaskBoundSizeUniform = -1;
  private int boundMaskSharedSizeUniform = -1;
  private int boundMaskTargetSizeUniform = -1;
  private boolean boundMaskProgramFailed;
  private boolean loggedFirstBridge;

  // === Distant translucent (water) bridge state =======================================
  // Separate cached program from the opaque path: the opaque colour pass runs at beginHand RETURN
  // and the translucent pass at beginTranslucents RETURN every frame, so they must NOT share the
  // single-slot opaque program cache (that would thrash a recompile twice per frame). The
  // translucent program samples the tgbuffer0/1 front-surface ABI and runs the pack's translucent
  // patch; see GLSL_TGBUFFER_DECODE / translucentColorMain and renderTranslucent.
  private TranslucentBridgeProgram translucentProgram;
  private int failedTranslucentShaderKey = Integer.MIN_VALUE;
  // Private depth-STENCIL used ONLY for the translucent near-coverage stencil mask. It is distinct
  // from irisPrivateDepthTexture so the translucent pass never clobbers the opaque Voxy-NDC depth
  // the pack still samples as vxDepthTexOpaque in its later composite/post passes. Its depth
  // component is throwaway (the translucent composite does not depth-test distant layers - Metal
  // already resolved their order back-to-front into tgbuffer0/1).
  private int translucentMaskDepthStencil;
  private int translucentMaskWidth;
  private int translucentMaskHeight;
  private boolean loggedFirstTranslucentBridge;

  GlDistantTerrainBridge() {
    this.framebuffer = glGenFramebuffers();
    this.depthCopyFramebuffer = glGenFramebuffers();
    this.nearDepthFramebuffer = glGenFramebuffers();
    this.fullscreenVao = glGenVertexArrays();
  }

  /** Iris strict shader-pack job: own FBO drawing into the Iris render targets. */
  static DistantBridgeJob irisJob(ShaderPatchBridgePayload payload) {
    return new DistantBridgeJob(
        true,
        0,
        payload.targetTextureIds(),
        payload.depthTextureId(),
        true,
        payload.sourceDepthTextureId(),
        payload.sourceDepthWidth(),
        payload.sourceDepthHeight(),
        payload.outputWidth(),
        payload.outputHeight(),
        payload.shaderHeader(),
        payload.uniformBufferBytes(),
        payload.uniformUpdater(),
        payload.opaqueFragmentPatch(),
        payload.resourceBinder(),
        payload.programSetup(),
        null,
        false);
  }

  /**
   * Iris strict distant translucent (water) job. Same shape as {@link #irisJob} but the {@code
   * payload} carries the pack's TRANSLUCENT patch (in {@code opaqueFragmentPatch()}), translucent
   * draw targets and the {@code depthtex1}/noTranslucents near depth, and the {@code blendSetup}
   * applies the pack's translucent blend. Routed through {@link #renderTranslucent}, which samples
   * the tgbuffer0/1 front-surface ABI and shades it with the pack's gbuffers_water patch.
   */
  static DistantBridgeJob translucentJob(ShaderPatchBridgePayload payload) {
    return new DistantBridgeJob(
        true,
        0,
        payload.targetTextureIds(),
        payload.depthTextureId(),
        true,
        payload.sourceDepthTextureId(),
        payload.sourceDepthWidth(),
        payload.sourceDepthHeight(),
        payload.outputWidth(),
        payload.outputHeight(),
        payload.shaderHeader(),
        payload.uniformBufferBytes(),
        payload.uniformUpdater(),
        payload.opaqueFragmentPatch(),
        payload.resourceBinder(),
        payload.programSetup(),
        payload.blendSetup(),
        true);
  }

  /**
   * Vanilla job: draw straight into the source framebuffer with the built-in patch. The source
   * depth texture used for the near mask is resolved from that framebuffer at draw time.
   */
  static DistantBridgeJob vanillaJob(RenderFrameContext context, boolean colorWriteEnabled) {
    return new DistantBridgeJob(
        false,
        context.sourceFramebuffer(),
        new int[0],
        0,
        colorWriteEnabled,
        0,
        context.viewportWidth(),
        context.viewportHeight(),
        context.viewportWidth(),
        context.viewportHeight(),
        "",
        0,
        null,
        VANILLA_PATCH,
        null,
        null,
        null,
        false);
  }

  /**
   * Vanilla (no shader pack) distant water job: composite into the source framebuffer with the
   * built-in water shade. Routed through {@link #renderTranslucent}, which samples the tgbuffer0/1
   * front-surface ABI and depth-tests against the real scene depth.
   */
  static DistantBridgeJob vanillaTranslucentJob(
      RenderFrameContext context, boolean colorWriteEnabled) {
    return new DistantBridgeJob(
        false,
        context.sourceFramebuffer(),
        new int[0],
        0,
        colorWriteEnabled,
        0,
        context.viewportWidth(),
        context.viewportHeight(),
        context.viewportWidth(),
        context.viewportHeight(),
        "",
        0,
        null,
        "",
        null,
        null,
        null,
        true);
  }

  boolean render(
      RenderFrameContext context,
      DistantGbufferSlot slot,
      DistantBridgeJob job,
      Matrix4fc voxyMvp,
      Matrix4fc vanillaMvp) {
    if (!job.valid()) {
      return false;
    }
    BridgeProgram colorProgram = this.programFor(job);
    if (colorProgram == null) {
      return false;
    }
    // The strict Iris path (own FBO, shader-pack patch) renders in a single pass into a Voxy-owned
    // private depth attachment, exactly like voxy-fabric's IrisVoxyRenderPipeline. Vanilla and the
    // debug visualisations stay single pass too; only the program shape differs (see programFor).
    boolean irisStrict = job.ownFramebuffer() && DEBUG_MODE == DEBUG_NONE;

    StateSnapshot state = StateSnapshot.capture();
    try (MemoryStack stack = MemoryStack.stackPush()) {
      int sourceDepthTexture =
          job.ownFramebuffer()
              ? job.sourceDepthTextureId()
              : findFramebufferDepthTexture(job.sourceFramebuffer());
      int sourceDepthWidth = job.ownFramebuffer() ? job.sourceDepthWidth() : job.outputWidth();
      int sourceDepthHeight = job.ownFramebuffer() ? job.sourceDepthHeight() : job.outputHeight();
      boolean reverseDepth = state.depthFunc == GL_GEQUAL || state.depthFunc == GL_GREATER;

      boolean drawn;
      if (irisStrict) {
        // Strict Iris contract: we must have the near (Sodium opaque) depth to composite near vs
        // far. Without it we skip this frame's distant output rather than paint distant terrain
        // over the near scene Sodium already wrote into the Iris colour targets.
        if (sourceDepthTexture == 0) {
          return false;
        }
        drawn =
            this.runOpaquePass(
                stack,
                colorProgram,
                slot,
                job,
                voxyMvp,
                vanillaMvp,
                sourceDepthTexture,
                sourceDepthWidth,
                sourceDepthHeight,
                false,
                reverseDepth,
                true);
      } else {
        boolean useManualDepthMask = USE_MANUAL_DEPTH_MASK && sourceDepthTexture != 0;
        int nearDepthSnapshot =
            useManualDepthMask
                ? this.snapshotNearDepth(sourceDepthTexture, sourceDepthWidth, sourceDepthHeight)
                : 0;
        if (useManualDepthMask && nearDepthSnapshot == 0) {
          useManualDepthMask = false;
        }
        drawn =
            this.runOpaquePass(
                stack,
                colorProgram,
                slot,
                job,
                voxyMvp,
                vanillaMvp,
                nearDepthSnapshot,
                sourceDepthWidth,
                sourceDepthHeight,
                useManualDepthMask,
                reverseDepth,
                false);
      }
      if (drawn && !this.loggedFirstBridge) {
        this.loggedFirstBridge = true;
        Logger.info(
            "Voxy GL41Metal distant terrain bridge sampled shared textures ("
                + (job.ownFramebuffer()
                    ? (irisStrict
                        ? "Iris strict shader-pack, private-depth single pass"
                        : "Iris debug")
                    : "vanilla built-in")
                + ")");
      }
      return drawn;
    } finally {
      state.restore();
    }
  }

  /**
   * Distant translucent (water) composite, run at Iris {@code beginTranslucents()} RETURN. By that
   * point Iris has copied the opaque scene depth into {@code depthtex1}/noTranslucents, the pack's
   * deferred passes have lit the opaque scene into the colour targets, and the near translucent
   * geometry has NOT drawn yet - so compositing the distant water here blends it over the lit
   * opaque scene and lets the near Sodium water/glass blend over it afterwards.
   *
   * <p>Deferred-hybrid design (see GL41METAL_BACKEND_PLAN.md): Metal already rasterized and
   * back-to-front resolved the distant translucent layers, leaving the FRONT-most surface in
   * tgbuffer0/1. This pass shades that front surface with the shader pack's gbuffers_water patch
   * (so it does NOT degrade to vanilla water under shaders) and composites it into the pack's
   * translucent draw targets with the pack's blend. Near/far occlusion against the opaque scene is
   * resolved by the same stencil coverage mask the opaque path uses, fed with noTranslucents depth.
   *
   * <p>FIRST CUT: only the front surface is pack-shaded; the deeper-layer {@code tgbufferAccum}
   * over-blend is a documented follow-up (the dominant ocean-surface case is single-layer, where
   * front == the only layer).
   */
  boolean renderTranslucent(
      RenderFrameContext context,
      DistantGbufferSlot slot,
      DistantBridgeJob job,
      Matrix4fc voxyMvp,
      Matrix4fc vanillaMvp,
      LoadedVolumeBound bound) {
    if (!job.valid() || !job.translucent()) {
      return false;
    }
    boolean vanilla = !job.ownFramebuffer();
    if (!vanilla && job.sourceDepthTextureId() == 0) {
      // Strict Iris contract: without the near (opaque) depth we cannot occlude distant water
      // against the near scene, so skip this frame's distant translucent output.
      return false;
    }
    TranslucentBridgeProgram colorProgram = this.programForTranslucent(job);
    if (colorProgram == null) {
      return false;
    }
    StateSnapshot state = StateSnapshot.capture();
    try (MemoryStack stack = MemoryStack.stackPush()) {
      boolean reverseDepth = state.depthFunc == GL_GEQUAL || state.depthFunc == GL_GREATER;
      boolean drawn =
          this.runTranslucentPass(
              stack,
              colorProgram,
              slot,
              job,
              voxyMvp,
              vanillaMvp,
              bound,
              job.sourceDepthTextureId(),
              job.sourceDepthWidth(),
              job.sourceDepthHeight(),
              reverseDepth,
              vanilla);
      if (drawn && !this.loggedFirstTranslucentBridge) {
        this.loggedFirstTranslucentBridge = true;
        Logger.info(
            "Voxy GL41Metal distant translucent bridge composited shared water textures ("
                + (vanilla ? "vanilla built-in water" : "Iris strict, front-surface pack-shaded")
                + ")");
      }
      return drawn;
    } finally {
      state.restore();
    }
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
  private boolean runTranslucentPass(
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
    if (vanilla) {
      glBindFramebuffer(GL_DRAW_FRAMEBUFFER, job.sourceFramebuffer());
      glViewport(0, 0, job.outputWidth(), job.outputHeight());
      boolean colorWrite = job.colorWriteEnabled();
      glColorMask(colorWrite, colorWrite, colorWrite, colorWrite);
      glDisable(GL_STENCIL_TEST);
      if (DEBUG_TRANSLUCENT) {
        // Probe: always draw (no occlusion) so we can tell whether the pass ran and where the Metal
        // translucent gbuffer has coverage.
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
      } else {
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(reverseDepth ? GL_GEQUAL : GL_LEQUAL);
        glDepthMask(false);
      }
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
    Shader maskShader = this.ensureStencilMaskProgram();
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
    maskShader.bind();
    glUniform1i(this.stencilMaskNearDepthUniform, SOURCE_DEPTH_TEXTURE_UNIT);
    glUniform2f(this.stencilMaskNearSizeUniform, sourceDepthWidth, sourceDepthHeight);
    glUniform2f(this.stencilMaskTargetSizeUniform, job.outputWidth(), job.outputHeight());
    glUniform1i(this.stencilMaskReverseUniform, reverseDepth ? 1 : 0);
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
    Shader behindShader = this.ensureBehindLayersProgram();
    if (behindShader != null) {
      glEnable(GL_BLEND);
      org.lwjgl.opengl.GL14C.glBlendFuncSeparate(
          org.lwjgl.opengl.GL11C.GL_ONE,
          org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA,
          org.lwjgl.opengl.GL11C.GL_ONE,
          org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA);
      behindShader.bind();
      glUniform1i(this.behindLayersTgb0Uniform, GBUFFER0_TEXTURE_UNIT);
      glUniform1i(this.behindLayersTgb1Uniform, GBUFFER1_TEXTURE_UNIT);
      glUniform1i(this.behindLayersAccumUniform, GBUFFER2_TEXTURE_UNIT);
      if (this.behindLayersLightmapUniform >= 0) {
        glUniform1i(this.behindLayersLightmapUniform, LIGHTMAP_TEXTURE_UNIT);
      }
      glUniform2f(this.behindLayersSharedSizeUniform, slot.width(), slot.height());
      glUniform2f(this.behindLayersTargetSizeUniform, job.outputWidth(), job.outputHeight());
      this.bindSharedTexture(
          GBUFFER0_TEXTURE_UNIT, slot.textureTarget(), slot.tgbuffer0Texture());
      this.bindSharedTexture(
          GBUFFER1_TEXTURE_UNIT, slot.textureTarget(), slot.tgbuffer1Texture());
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
    Shader depthWriteShader = this.ensureTranslucentDepthProgram();
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

      depthWriteShader.bind();
      glUniform1i(this.translucentDepthTgbuffer1Uniform, GBUFFER1_TEXTURE_UNIT);
      glUniform2f(this.translucentDepthSharedSizeUniform, slot.width(), slot.height());
      glUniform2f(this.translucentDepthTargetSizeUniform, job.outputWidth(), job.outputHeight());
      this.bindSharedTexture(
          GBUFFER1_TEXTURE_UNIT, slot.textureTarget(), slot.tgbuffer1Texture());
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
  private boolean runOpaquePass(
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
      // Default (vanilla / debug) specialization: one colour pass straight into the target
      // framebuffer with a hardware depth test against the real scene depth, plus the optional
      // in-shader near-depth mask. Vanilla draws into the MC main framebuffer (so it writes MC
      // depth for later passes); debug visualisations draw into the Iris targets using the Iris
      // main
      // depth. This is the forced divergence from the strict branch below: the MC framebuffer has a
      // real, hardware-testable depth attachment and must keep MC depth, whereas the strict Iris
      // targets have no stencil-testable shared depth and must emit voxy-only private depth.
      if (job.ownFramebuffer()) {
        if (!this.bindTargetFramebuffer(stack, job, job.depthTextureId(), false)) {
          return false;
        }
      } else {
        // Vanilla draws into the source framebuffer (MC main target) using its existing colour
        // attachment 0 and depth attachment, so we must not retarget draw buffers here.
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, job.sourceFramebuffer());
      }

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
      if (DEBUG_MODE != DEBUG_NONE) {
        glUniform1i(colorProgram.debugModeUniform(), DEBUG_MODE);
      }

      this.bindGbufferTextures(slot);
      if (useManualDepthMask) {
        this.bind2DTexture(SOURCE_DEPTH_TEXTURE_UNIT, sourceDepthTexture);
      }
      // The MC lightmap is bound for the vanilla patch (direct sample) and debug litColor mode.
      LightMapHelper.bind(LIGHTMAP_TEXTURE_UNIT);
      if (DEBUG_MODE == DEBUG_NONE) {
        this.bindShaderPackResources(job);
      }

      glBindVertexArray(this.fullscreenVao);
      glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
      return true;
    }

    int privateDepth = this.ensureIrisPrivateDepth(job.outputWidth(), job.outputHeight());
    if (privateDepth == 0) {
      return false;
    }
    Shader maskShader = this.ensureStencilMaskProgram();
    if (maskShader == null) {
      return false;
    }
    // Bind the bridge FBO with the Iris colour targets + our private depth-STENCIL attachment.
    if (!this.bindTargetFramebuffer(stack, job, privateDepth, true)) {
      return false;
    }
    glViewport(0, 0, job.outputWidth(), job.outputHeight());

    // (1) Clear the private depth to the far value (1.0 = "no Voxy / non-LOD" sentinel for the
    // pack)
    // and the coverage stencil to 0. Colour is left untouched: the Iris targets already hold the
    // near scene Sodium/Iris rendered.
    glDepthMask(true);
    glStencilMask(0xFF);
    glColorMask(false, false, false, false);
    glClearDepth(1.0);
    glClearStencil(0);
    glClear(GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

    // (2) Coverage mask pass: stencil := 1 wherever the near scene has geometry (near depth is not
    // sky). Discarded (sky) pixels keep stencil 0. Depth/colour writes stay off.
    glDisable(GL_DEPTH_TEST);
    glDepthMask(false);
    glEnable(GL_STENCIL_TEST);
    glStencilMask(0xFF);
    glStencilFunc(GL_ALWAYS, 1, 0xFF);
    glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
    maskShader.bind();
    glUniform1i(this.stencilMaskNearDepthUniform, SOURCE_DEPTH_TEXTURE_UNIT);
    glUniform2f(this.stencilMaskNearSizeUniform, sourceDepthWidth, sourceDepthHeight);
    glUniform2f(this.stencilMaskTargetSizeUniform, job.outputWidth(), job.outputHeight());
    glUniform1i(this.stencilMaskReverseUniform, reverseDepth ? 1 : 0);
    this.bind2DTexture(SOURCE_DEPTH_TEXTURE_UNIT, sourceDepthTexture);
    glBindVertexArray(this.fullscreenVao);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    // No loaded-volume bound clip for opaque: the near-coverage stencil above already occludes
    // distant opaque pixel-wise wherever near geometry exists, and distant opaque harmlessly fills
    // gaps behind near cutout blocks. The coarse per-section bound is reserved for translucent
    // water
    // (which writes no opaque depth). See runTranslucentPass / buildFragmentShader.

    // (3) Colour pass: shade distant terrain only where the near scene is empty (stencil==0). The
    // private depth is non-reverse Voxy NDC with far=1.0, so depth test is always GL_LEQUAL here
    // regardless of the host's reverse-Z state (it only orders overlapping distant fragments). The
    // colour program writes gl_FragDepth = g.depth (Voxy NDC) into the private depth (see
    // GLSL_COLOR_MAIN_NO_MASK), which is exactly what the pack samples as vxDepthTex*.
    boolean colorWrite = job.colorWriteEnabled();
    glColorMask(colorWrite, colorWrite, colorWrite, colorWrite);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);
    glDepthMask(true);
    glStencilMask(0x00);
    glStencilFunc(GL_EQUAL, 0, 0xFF);
    glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
    glDisable(GL_BLEND);

    colorProgram.shader().bind();
    this.setReconstructionUniforms(stack, colorProgram, slot, job, voxyMvp, vanillaMvp);
    this.bindGbufferTextures(slot);
    this.bindShaderPackResources(job);

    glBindVertexArray(this.fullscreenVao);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    maybeReadbackCenterDepth(stack, job);

    // StateSnapshot.restore() does not track stencil state, so leave it disabled with a default
    // mask for the rest of the host frame.
    glDisable(GL_STENCIL_TEST);
    glStencilMask(0xFF);
    return true;
  }

  private static final boolean DEBUG_DEPTH_READBACK =
      Boolean.parseBoolean(System.getProperty("voxy.gl41metal.debugDepthReadback", "false"));
  private static int debugDepthReadbackFrame = 0;

  // Ground-truth diagnostic for the "distant LOD depth is too small" question. Reads the actual
  // value
  // the shader pack samples as vxDepthTexOpaque (this.framebuffer's private depth) at the screen
  // CENTRE (the crosshair), and reconstructs the camera distance with the SAME projection the
  // VOXYMTX log proved correct (near=16, far=48000, GL [0,1] depth, 0=near). depth -> ndc=2d-1 ->
  // z_view = m32 / (ndc + m22). If a far crosshair target reports only tens of blocks, the depth
  // WRITE is genuinely compressed in Metal; if it reports the true distance, the GLSL band probe
  // was
  // misreading and depth is fine.
  private void maybeReadbackCenterDepth(MemoryStack stack, DistantBridgeJob job) {
    if (!DEBUG_DEPTH_READBACK) {
      return;
    }
    if ((debugDepthReadbackFrame++ % 60) != 0) {
      return;
    }
    int cx = job.outputWidth() / 2;
    int cy = job.outputHeight() / 2;
    glBindFramebuffer(GL_READ_FRAMEBUFFER, this.framebuffer);
    FloatBuffer depthBuf = stack.mallocFloat(1);
    glReadPixels(cx, cy, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, depthBuf);
    float d = depthBuf.get(0);
    float near = 16.0f;
    float far = 16.0f * 3000.0f;
    float m22 = (far + near) / (near - far);
    float m32 = (2.0f * far * near) / (near - far);
    float ndc = d * 2.0f - 1.0f;
    float denom = ndc + m22;
    float zView = Math.abs(denom) < 1.0e-9f ? Float.POSITIVE_INFINITY : (m32 / denom);
    Logger.info(
        String.format(
            "VOXYDEPTH centre depth=%.6f -> ndc=%.6f -> camDist=%.1f blocks (near=16 far=48000)",
            d, ndc, Math.abs(zView)));
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

  private BridgeProgram programFor(DistantBridgeJob job) {
    int shaderKey = job.shaderKey() * 31 + DEBUG_MODE;
    if (this.program != null && this.program.shaderKey() == shaderKey) {
      return this.program;
    }
    if (this.failedShaderKey == shaderKey) {
      return null;
    }
    if (this.program != null) {
      this.program.shader().free();
      this.program = null;
    }
    // The strict Iris path (own framebuffer, no debug) omits the near-mask + lightmap blocks so the
    // colour program stays at 3 base samplers; occlusion is done by the hardware depth test in
    // runOpaquePass. Vanilla and debug keep the inline near mask.
    boolean usesNearMask = !(job.ownFramebuffer() && DEBUG_MODE == DEBUG_NONE);
    try {
      String fragmentSource =
          this.buildFragmentShader(
              DEBUG_MODE, job.shaderHeader(), job.fragmentPatch(), usesNearMask);
      BridgeProgram bridgeProgram =
          this.compileBridgeProgram(shaderKey, usesNearMask, fragmentSource);
      if (bridgeProgram == null) {
        this.failedShaderKey = shaderKey;
        return null;
      }
      // GL 4.1 has no layout(binding=...): bind the shader-pack std140 block and sampler units
      // here,
      // once per compiled program (no-op for the vanilla/debug job).
      job.programSetup().accept(bridgeProgram.shader().id());
      this.program = bridgeProgram;
      return bridgeProgram;
    } catch (RuntimeException e) {
      this.failedShaderKey = shaderKey;
      Logger.error("Failed to compile Voxy GL41Metal distant terrain bridge", e);
      return null;
    }
  }

  /**
   * GL texture id of the Voxy-private Iris distant-terrain depth-stencil attachment
   * (GL_DEPTH24_STENCIL8) for the most recent strict Iris frame, or 0 before one has run. Iris
   * shader packs sample its depth component as {@code vxDepthTexOpaque} / {@code vxDepthTexTrans}:
   * a voxy-only, Voxy-NDC depth (distant geometry depth where Voxy drew, far=1.0 elsewhere),
   * matching voxy-fabric's IrisVoxyRenderPipeline.fb.getDepthTex() after its shaderDepthHackFix.
   */
  int voxyDistantDepthTextureId() {
    return this.irisPrivateDepthTexture;
  }

  private BridgeProgram compileBridgeProgram(
      int shaderKey, boolean usesNearMask, String fragmentSource) {
    if (DUMP_SHADERS) {
      int samplerCount = fragmentSource.split("uniform\\s+sampler", -1).length - 1;
      String tag =
          shaderKey == Integer.MIN_VALUE ? "mask" : (usesNearMask ? "vanilla" : "iriscolor");
      Logger.info(
          "Voxy GL41Metal bridge fragment shader ["
              + tag
              + "] length="
              + fragmentSource.length()
              + " samplerDecls="
              + samplerCount);
      try {
        // Client cwd is the gradle run/ dir, so this lands in run/bridge_<tag>.frag.
        java.nio.file.Files.writeString(
            java.nio.file.Path.of("bridge_" + tag + ".frag"), fragmentSource);
      } catch (Exception ignored) {
        // diagnostic only
      }
    }
    Shader shader =
        Shader.make()
            .addSource(
                ShaderType.VERTEX,
                ShaderLoader.parse("voxy:lod/gl41metal/interop_bridge.vert", "410 core"))
            .addSource(ShaderType.FRAGMENT, fragmentSource)
            .compile()
            .name("GL41Metal distant terrain bridge");
    BridgeProgram bridgeProgram =
        new BridgeProgram(
            shaderKey,
            shader,
            usesNearMask,
            glGetUniformLocation(shader.id(), "uGbuffer0Tex"),
            glGetUniformLocation(shader.id(), "uGbuffer1Tex"),
            glGetUniformLocation(shader.id(), "uGbuffer2Tex"),
            glGetUniformLocation(shader.id(), "uSourceDepthTex"),
            glGetUniformLocation(shader.id(), "uLightmapTex"),
            glGetUniformLocation(shader.id(), "lightSampler"),
            glGetUniformLocation(shader.id(), "uSharedSize"),
            glGetUniformLocation(shader.id(), "uTargetSize"),
            glGetUniformLocation(shader.id(), "uSourceDepthSize"),
            glGetUniformLocation(shader.id(), "uReverseDepth"),
            glGetUniformLocation(shader.id(), "uUseManualDepthMask"),
            glGetUniformLocation(shader.id(), "uDebugMode"),
            glGetUniformLocation(shader.id(), "uInvVoxyMvp"),
            glGetUniformLocation(shader.id(), "uVanillaMvp"));
    if (!bridgeProgram.hasRequiredUniforms()) {
      shader.free();
      Logger.error("Voxy GL41Metal distant terrain bridge is missing required uniforms");
      return null;
    }
    return bridgeProgram;
  }

  private TranslucentBridgeProgram programForTranslucent(DistantBridgeJob job) {
    int shaderKey = job.shaderKey();
    if (this.translucentProgram != null && this.translucentProgram.shaderKey() == shaderKey) {
      return this.translucentProgram;
    }
    if (this.failedTranslucentShaderKey == shaderKey) {
      return null;
    }
    if (this.translucentProgram != null) {
      this.translucentProgram.shader().free();
      this.translucentProgram = null;
    }
    // Vanilla (no shader pack) draws into the MC framebuffer with the built-in water shade; the
    // strict Iris path draws into the pack's translucent targets with the pack's patch. Both go
    // through the SAME builder + main; vanilla is just the default (VANILLA_WATER_PATCH) shade.
    boolean vanilla = !job.ownFramebuffer();
    try {
      String fragmentSource =
          this.buildTranslucentFragmentShader(job.shaderHeader(), job.fragmentPatch(), vanilla);
      if (DUMP_SHADERS) {
        try {
          java.nio.file.Files.writeString(
              java.nio.file.Path.of(
                  vanilla ? "bridge_water_vanilla.frag" : "bridge_translucent.frag"),
              fragmentSource);
        } catch (Exception ignored) {
          // diagnostic only
        }
      }
      Shader shader =
          Shader.make()
              .addSource(
                  ShaderType.VERTEX,
                  ShaderLoader.parse("voxy:lod/gl41metal/interop_bridge.vert", "410 core"))
              .addSource(ShaderType.FRAGMENT, fragmentSource)
              .compile()
              .name("GL41Metal distant translucent bridge");
      TranslucentBridgeProgram bridgeProgram =
          new TranslucentBridgeProgram(
              shaderKey,
              shader,
              vanilla,
              glGetUniformLocation(shader.id(), "uTgbuffer0Tex"),
              glGetUniformLocation(shader.id(), "uTgbuffer1Tex"),
              glGetUniformLocation(shader.id(), "uTgbufferAccumTex"),
              glGetUniformLocation(shader.id(), "uLightmapTex"),
              glGetUniformLocation(shader.id(), "lightSampler"),
              glGetUniformLocation(shader.id(), "uSharedSize"),
              glGetUniformLocation(shader.id(), "uTargetSize"),
              glGetUniformLocation(shader.id(), "uInvVoxyMvp"),
              glGetUniformLocation(shader.id(), "uVanillaMvp"),
              glGetUniformLocation(shader.id(), "uBoundDepthTex"),
              glGetUniformLocation(shader.id(), "uBoundSize"),
              glGetUniformLocation(shader.id(), "uBoundEnabled"));
      if (!bridgeProgram.hasRequiredUniforms()) {
        shader.free();
        this.failedTranslucentShaderKey = shaderKey;
        Logger.error("Voxy GL41Metal distant translucent bridge is missing required uniforms");
        return null;
      }
      // Only the strict Iris program needs the pack UBO/sampler binding; the vanilla water program
      // binds its own 3 samplers directly.
      if (!vanilla) {
        job.programSetup().accept(shader.id());
      }
      this.translucentProgram = bridgeProgram;
      return bridgeProgram;
    } catch (RuntimeException e) {
      this.failedTranslucentShaderKey = shaderKey;
      Logger.error("Failed to compile Voxy GL41Metal distant translucent bridge", e);
      return null;
    }
  }

  /**
   * Assembles the translucent bridge fragment shader for both targets. The tgbuffer decode prologue
   * is shared; vanilla appends the MC lightmap block + the built-in {@link #VANILLA_WATER_PATCH},
   * while the strict Iris path appends the pack's shader header + translucent patch (with the
   * gl_FragCoord rewrite the opaque path also uses). Both then append the shared {@link
   * #translucentColorMain}. The Apple GL4.1 global-initializer hoisting + unreachable-function
   * pruning transforms run only for the Iris (pack-patch) source, for the same linker-SIGSEGV
   * reason as the opaque Iris path; the vanilla source embeds no pack patch and skips them
   * (matching the opaque vanilla path).
   */
  private String buildTranslucentFragmentShader(
      String shaderHeader, String patchSource, boolean vanilla) {
    if (vanilla && DEBUG_TRANSLUCENT) {
      return GLSL_TGBUFFER_DECODE + GLSL_LIGHTMAP + GLSL_VANILLA_TRANSLUCENT_DEBUG_MAIN;
    }
    StringBuilder shader = new StringBuilder(GLSL_TGBUFFER_DECODE);
    if (vanilla) {
      shader.append(GLSL_BOUND_CLIP).append(GLSL_LIGHTMAP).append(VANILLA_WATER_PATCH);
    } else {
      String rewrittenPatch =
          REWRITE_GL_FRAG_COORD.matcher(patchSource).replaceAll("voxy_OverrideFragCoord");
      shader.append("\n").append(shaderHeader).append("\n").append(rewrittenPatch).append("\n");
    }
    shader.append(translucentColorMain(vanilla));
    String composed = shader.toString();
    if (!vanilla) {
      composed = pruneUnreachableFunctions(composed);
      composed = hoistGlobalInitializers(composed);
    }
    return composed;
  }

  // === Composable GLSL for the distant bridge =========================================
  // The bridge runs in two shapes (see render()): the vanilla single pass keeps the near-depth
  // mask + MC lightmap inline (it draws straight into the MC source framebuffer), while the strict
  // Iris single pass omits both. The Iris colour program MUST stay at 3 base samplers (gbuffer0-2)
  // so a 12-sampler pack still fits Apple GL4.1's usable 15-unit budget; it does NOT need
  // uSourceDepthTex because near/far occlusion is resolved by the stencil coverage mask
  // runOpaquePass sets up (the distant colour pass draws only where the near scene is empty).
  // Both shapes reuse the same GBUFFER_DECODE/projection helpers; the vanilla path writes
  // gl_FragDepth in vanilla NDC (projectDepth) for the MC depth buffer, while the Iris path writes
  // Voxy NDC (g.depth) into its private depth-stencil for the shader pack to sample as vxDepthTex*.

  // gbuffer reconstruction shared by every bridge program. No near-mask, no lightmap, no debug.
  private static final String GLSL_GBUFFER_DECODE =
      """
      #version 410 core

      // Distant gbuffer: Metal packs everything into 3 shared RGBA32F textures (see
      // quad_raster.metal QuadFragmentOut for the authoritative bit layout). Keeping the
      // reconstruction at 3 samplers is what lets the Iris colour program also bind the shader
      // pack's samplers (up to 12) within Apple GL4.1's usable 15 fragment-texture-unit budget
      // (the driver SIGSEGVs at exactly 16). Each channel below is unpacked in sampleVoxyGbuffer;
      // the bit layout MUST stay in lockstep with the Metal QuadFragmentOut packing.
      uniform sampler2DRect uGbuffer0Tex;  // .xy atlas uv, .zw quad tile
      uniform sampler2DRect uGbuffer1Tex;  // .x depth, .y modelId, .z customId&0xFFFFFF, .w customId>>24
      uniform sampler2DRect uGbuffer2Tex;  // .x albedo, .y lightMap, .z tint, .w face/flags/coverage (all packed)
      uniform vec2 uSharedSize;
      uniform vec2 uTargetSize;
      uniform mat4 uInvVoxyMvp;
      uniform mat4 uVanillaMvp;

      // Quad flags decoded in main(); patches may read it for the directional shade bit.
      uint voxyQuadFlags = 0u;

      struct VoxyFragmentParameters {
        vec4 sampledColour;
        vec2 tile;
        vec2 uv;
        // GL46 quads.frag declares face as uint, but Apple's strict GLSL 410 compiler rejects the
        // int/uint mixes shader packs write against it (e.g. Complementary's `parameters.face & 1`),
        // which GL46's lenient implicit conversions accept. The value is the 0-5 face index, so the
        // bit ops are identical; declare it int for GL41 shader-pack compatibility.
        int face;
        uint modelId;
        vec2 lightMap;
        vec4 tinting;
        uint customId;
      };

      // Recovers an exact non-negative integer that Metal stored as a float *value* in an RGBA32F
      // channel. NEAREST sampling returns the texel bit-exact, so truncation recovers the integer.
      // Do NOT add 0.5 here: the tint field reaches 2^24-1 and +0.5 would round past the f32
      // exact-integer range. See quad_raster.metal's losslessness rules.
      uint decodePackedUint(float value) {
        return uint(max(value, 0.0));
      }

      // One decoded gbuffer texel. coverage and depth gate the discard; the rest feeds
      // VoxyFragmentParameters. tint/colour alpha is opaque (1.0) here (see quad_raster.metal).
      struct VoxyGbufferTexel {
        float coverage;
        vec4 colour;
        vec2 uv;
        vec2 tile;
        float depth;
        vec2 lightMap;
        vec4 tint;
        uint modelId;
        uint customId;
        uint face;
        uint flags;
        float ao;
      };

      // Unpacks one RGB triple stored as (r<<16)|(g<<8)|b into a normalised vec3 (each 0..255/255).
      vec3 unpackRgb8(uint packed) {
        return vec3(float((packed >> 16u) & 255u), float((packed >> 8u) & 255u), float(packed & 255u))
            / 255.0;
      }

      VoxyGbufferTexel sampleVoxyGbuffer(vec2 sharedPixel) {
        vec4 g0 = texture(uGbuffer0Tex, sharedPixel);
        vec4 g1 = texture(uGbuffer1Tex, sharedPixel);
        vec4 g2 = texture(uGbuffer2Tex, sharedPixel);
        VoxyGbufferTexel t;
        // gbuffer0: atlas uv + quad tile (both plain floats, no packing).
        t.uv = g0.xy;
        t.tile = g0.zw;
        // gbuffer1: depth + ids. customId is split lo 24 bits / hi 8 bits to keep each < 2^24.
        t.depth = clamp(g1.x, 0.0, 1.0);
        t.modelId = decodePackedUint(g1.y);
        t.customId = decodePackedUint(g1.z) | (decodePackedUint(g1.w) << 24u);
        // gbuffer2: four packed integer channels (see quad_raster.metal). albedo/tint are 8-bit
        // rgb; lightMap is two 12-bit coords; the last channel folds ao/face/flags/coverage.
        t.colour = vec4(unpackRgb8(decodePackedUint(g2.x)), 1.0);
        uint lightPacked = decodePackedUint(g2.y);
        t.lightMap = vec2(float((lightPacked >> 12u) & 4095u), float(lightPacked & 4095u)) / 4095.0;
        t.tint = vec4(unpackRgb8(decodePackedUint(g2.z)), 1.0);
        uint faceFlagsCoverage = decodePackedUint(g2.w);
        t.coverage = float(faceFlagsCoverage & 1u);
        t.flags = (faceFlagsCoverage >> 1u) & 255u;
        t.face = (faceFlagsCoverage >> 9u) & 7u;
        // Bits 12-19: SSAO factor from the Metal ssao.metal pass. 0 = no AO data (pass skipped
        // or pre-SSAO pixel) -> neutral 1.0. Folded straight into the albedo: near terrain
        // carries vanilla's per-vertex AO in its vertex colour, which flows into shader-pack
        // gbuffers the same way, so both the built-in VANILLA_PATCH and a pack's patched
        // voxy_emitFragment see AO-darkened sampledColour without any patch-side changes
        // (GL46's compute pass equivalently multiplies the lit colour post-opaque).
        uint aoPacked = (faceFlagsCoverage >> 12u) & 255u;
        t.ao = aoPacked == 0u ? 1.0 : float(aoPacked) / 255.0;
        t.colour.rgb *= t.ao;
        return t;
      }

      vec3 rev3d(vec3 clip) {
        vec4 view = uInvVoxyMvp * vec4(clip * 2.0 - 1.0, 1.0);
        return view.xyz / view.w;
      }

      float projectDepth(vec3 pos) {
        vec4 view = uVanillaMvp * vec4(pos, 1.0);
        float depth = view.z / view.w;
        depth = min(1.0 - 2.0 / 16777215.0, depth);
        depth = depth * 0.5 + 0.5;
        depth = gl_DepthRange.diff * depth + gl_DepthRange.near;
        return clamp(depth, 0.0, 1.0);
      }

      vec2 sharedPixelForTarget(vec2 targetPixel) {
        vec2 shared = targetPixel * (uSharedSize / max(uTargetSize, vec2(1.0)));
        return vec2(shared.x, uSharedSize.y - shared.y);
      }

      // gl_FragCoord.z override for the full-screen-quad bridge.
      //
      // GL46 voxy renders the distant LOD as real per-triangle geometry, so a shader pack's
      // voxy_emitFragment naturally sees gl_FragCoord.z = the per-pixel rasterizer depth of the
      // distant triangle, expressed in the active program's projection (vxProj). The gl41metal
      // bridge composites the Metal-produced distant gbuffer through a single full-screen quad,
      // whose rasterizer-interpolated z is constant across every pixel (the quad's vertex depth),
      // so any pack doing the standard
      //     vec3 screenPos = vec3(gl_FragCoord.xy / vec2(viewWidth, viewHeight), gl_FragCoord.z);
      //     vec3 viewPos = ScreenToView(screenPos);
      // would collapse the whole distant scene to one view-space point. The fix is to redirect
      // every pack-side gl_FragCoord read to a per-pixel override (g.depth as written by Metal,
      // packed into voxy_OverrideFragCoord by main() before calling voxy_emitFragment): the
      // patched pack rebinds gbufferProjection/Inverse to vxProj/vxProjInv (Voxy's extended
      // projection), so its ScreenToView inverts in *Voxy* NDC, not vanilla NDC.
      //
      // We do the substitution in Java text (see buildFragmentShader's gl_FragCoord rewrite),
      // not via #define, because Apple's GL4.1 driver preprocessor silently ignores attempts to
      // redefine the built-in gl_FragCoord identifier - the GLSL spec only forbids redefining
      // pre-defined macros, but Apple's compiler treats the built-in variable name the same way,
      // leaving the pack reading the full-screen quad's constant rasterizer z. Renaming the
      // identifier at the patch-source level bypasses the issue entirely while keeping the
      // bridge's own main() free to read the real built-in gl_FragCoord.
      //
      // Note: gl_FragDepth is written by main() AFTER voxy_emitFragment. The vanilla path keeps the
      // vanilla-remapped outputDepth (GL depth buffer stays vanilla NDC for the MC hardware depth
      // test); the strict Iris path instead writes Voxy NDC (g.depth) into its private depth-stencil
      // so the shader pack samples Voxy-NDC depth as vxDepthTex* (see GLSL_COLOR_MAIN_NO_MASK). Only
      // the pack-visible gl_FragCoord is in Voxy NDC in both shapes.
      vec4 voxy_OverrideFragCoord;
      """;

  // Near-depth occlusion mask. Used by the vanilla single pass and the debug visualisations, but
  // NOT by the strict Iris colour pass (which resolves occlusion via the hardware depth test
  // against the private near-seeded depth attachment instead of sampling uSourceDepthTex).
  private static final String GLSL_NEAR_MASK =
      """

      uniform sampler2D uSourceDepthTex;
      uniform vec2 uSourceDepthSize;
      uniform int uReverseDepth;
      uniform int uUseManualDepthMask;

      bool isHiddenByNearDepth(vec2 targetPixel, float voxyDepth) {
        if (uUseManualDepthMask == 0) {
          return false;
        }
        vec2 sourcePixel = targetPixel * (uSourceDepthSize / max(uTargetSize, vec2(1.0)));
        ivec2 texel = ivec2(clamp(floor(sourcePixel), vec2(0.0), uSourceDepthSize - vec2(1.0)));
        float nearDepth = texelFetch(uSourceDepthTex, texel, 0).r;
        const float DEPTH_EPSILON = 0.00001;
        // Near depth only hides Voxy when Voxy is not meaningfully closer. The epsilon keeps
        // boundary LOD depth noise from fighting vanilla geometry without turning the depth
        // texture into a coarse coverage mask.
        if (uReverseDepth != 0) {
          if (nearDepth <= 0.000001) {
            return false;
          }
          return voxyDepth <= nearDepth + DEPTH_EPSILON;
        }
        if (nearDepth >= 0.999999) {
          return false;
        }
        return voxyDepth >= nearDepth - DEPTH_EPSILON;
      }
      """;

  // Loaded-volume clip (P1), in-shader form for the vanilla/debug colour programs. Discards distant
  // fragments that lie INSIDE the Sodium near-scene volume (nearer than its far boundary, captured
  // per pixel in uBoundDepthTex by Gl41MetalChunkBoundRenderer). This is what stops distant LOD
  // water - which writes no opaque depth, so the near-depth mask cannot hide it - from overlapping
  // the near Sodium water in the transition band. Mirrors voxy-fabric quads.frag's
  // DEPTH_SCALAR_COMPARE(gl_FragCoord.z, depthTex) discard.
  //
  // This block is compiled ONLY into the vanilla/debug programs (alongside GLSL_NEAR_MASK); they
  // have spare texture units. The strict Iris colour program never sees it - its identical clip
  // rides the stencil-mask pass (GLSL_BOUND_MASK) so the budgeted colour program gains no sampler.
  // uBoundEnabled==0 (no sections loaded) disables the clip so a stale boundary is never sampled.
  private static final String GLSL_BOUND_CLIP =
      """

      uniform sampler2D uBoundDepthTex;
      uniform vec2 uBoundSize;
      uniform int uBoundEnabled;

      // voxyDepth is the distant fragment's Voxy-NDC depth (g.depth) - the SAME space the bound was
      // rasterized in (drawMvp), so no reprojection is needed, exactly like voxy-fabric quads.frag
      // comparing gl_FragCoord.z against depthBoundingBuffer. Voxy NDC is non-reverse (0 near .. 1
      // far): a fragment INSIDE the loaded volume is NEARER than its far boundary, i.e. smaller.
      bool isInsideLoadedBound(vec2 targetPixel, float voxyDepth) {
        if (uBoundEnabled == 0) {
          return false;
        }
        vec2 boundPixel = targetPixel * (uBoundSize / max(uTargetSize, vec2(1.0)));
        ivec2 texel = ivec2(clamp(floor(boundPixel), vec2(0.0), uBoundSize - vec2(1.0)));
        float bound = texelFetch(uBoundDepthTex, texel, 0).r;
        const float BOUND_EPSILON = 0.00001;
        return voxyDepth <= bound - BOUND_EPSILON;
      }
      """;

  // Near-scene coverage stencil mask (strict Iris path). A standalone fragment program that samples
  // the Iris near (noHand/opaque) depth and DISCARDS sky pixels so they keep stencil 0, while
  // non-sky (near geometry) pixels pass and get stencil := 1 via the caller's GL_REPLACE op. The
  // distant colour pass then renders only where stencil==0, i.e. where the near scene is empty,
  // matching voxy-fabric's "render only where there isn't mc terrain". Sky thresholds mirror
  // GLSL_NEAR_MASK's reverse-Z handling (vanilla far plane: 1.0 forward / 0.0 reverse). This is a
  // separate 1-sampler program on purpose: it must not add a texture unit to the 15-unit colour
  // program (Apple GL4.1 SIGSEGVs at 16; Complementary already uses 12 pack + 3 gbuffer samplers).
  private static final String GLSL_STENCIL_MASK =
      """
      #version 410 core

      uniform sampler2D uNearDepth;
      uniform vec2 uNearSize;
      uniform vec2 uTargetSize;
      uniform int uReverseDepth;

      void main() {
        vec2 sourcePixel = gl_FragCoord.xy * (uNearSize / max(uTargetSize, vec2(1.0)));
        ivec2 texel = ivec2(clamp(floor(sourcePixel), vec2(0.0), uNearSize - vec2(1.0)));
        float nearDepth = texelFetch(uNearDepth, texel, 0).r;
        bool sky = (uReverseDepth != 0) ? (nearDepth <= 0.000001) : (nearDepth >= 0.999999);
        if (sky) {
          // No near geometry here: keep stencil 0 so the distant colour pass may draw.
          discard;
        }
        // Near geometry present: fall through so the GL_REPLACE stencil op writes 1 (blocks Voxy).
      }
      """;

  // Behind-layers blend shader: subtracts the front surface's premultiplied contribution from
  // tgbufferAccum and outputs the remainder as premultiplied colour. Blended with ONE,
  // ONE_MINUS_SRC_ALPHA (premultiplied OVER) so the water behind glass adds its colour to the
  // targets after the front surface (glass) was already composited by the pack's shader. For
  // single-layer translucent surfaces (the dominant ocean case), behind_alpha == 0 and the shader
  // discards, making this a no-op.
  private static final String GLSL_BEHIND_LAYERS_BLEND =
      """
      #version 410 core

      uniform sampler2DRect uTgbuffer0Tex;
      uniform sampler2DRect uTgbuffer1Tex;
      uniform sampler2DRect uTgbufferAccumTex;
      uniform sampler2D uLightmapTex;
      uniform vec2 uSharedSize;
      uniform vec2 uTargetSize;

      layout(location = 0) out vec4 behindColour;

      uint decodePackedUint(float f) {
        return uint(f + 0.5);
      }

      vec3 unpackRgb8(uint packed) {
        return vec3(
            float((packed >> 16u) & 0xFFu) / 255.0,
            float((packed >> 8u) & 0xFFu) / 255.0,
            float(packed & 0xFFu) / 255.0);
      }

      vec2 sharedPixelForTarget(vec2 targetPixel) {
        vec2 shared = targetPixel * (uSharedSize / max(uTargetSize, vec2(1.0)));
        return vec2(shared.x, uSharedSize.y - shared.y);
      }

      float faceTint(uint face) {
        if ((face >> 1u) == 1u) return 0.8;
        if ((face >> 1u) == 2u) return 0.6;
        if (face == 1u) return 1.0;
        return 0.5;
      }

      void main() {
        vec2 sp = sharedPixelForTarget(gl_FragCoord.xy);
        vec4 accum = texture(uTgbufferAccumTex, sp);
        if (accum.a <= 0.0) discard;

        vec4 t0 = texture(uTgbuffer0Tex, sp);
        vec4 t1 = texture(uTgbuffer1Tex, sp);

        float frontAlpha = t1.y;
        float bA = max(accum.a - frontAlpha, 0.0);
        if (bA <= 0.001) discard;

        float depth = t1.x;
        if (depth <= 0.0 || depth >= 1.0) discard;
        gl_FragDepth = depth;

        // Decode lightmap UV from tgbuffer0.y (Metal already applied the 15/16 + 0.5/16 transform).
        uint lightPacked = decodePackedUint(t0.y);
        vec2 lightUv = vec2(float((lightPacked >> 12u) & 4095u), float(lightPacked & 4095u)) / 4095.0;
        vec3 light = texture(uLightmapTex, lightUv).rgb;

        // Metal bakes per-fragment face shade into tgbufferAccum, so the front subtraction must
        // also include the front surface's face shade. The remainder (bRgb) then already has
        // correct per-layer face shade; we only multiply by lightmap (approximated from front).
        uint faceFlagsCoverage = decodePackedUint(t0.w);
        uint face = faceFlagsCoverage >> 9u;
        uint flags = (faceFlagsCoverage >> 1u) & 255u;
        bool isShaded = ((flags >> 6u) & 1u) != 0u;
        float frontFaceShade = isShaded ? faceTint(face) : 1.0;

        vec3 frontAlbedo = unpackRgb8(decodePackedUint(t0.x));
        vec3 frontTint = unpackRgb8(decodePackedUint(t0.z));
        vec3 frontPremul = frontAlbedo * frontTint * frontFaceShade * frontAlpha;
        // accum.rgb is an order-independent SUM of premultiplied flat colours (Metal blends the
        // accum target additively because within-section draw order is arbitrary). Subtracting the
        // front layer leaves sum(behind flats); attenuating once by (1 - frontAlpha) is exact for
        // two layers in any order and mildly overestimates the deepest layers beyond that.
        vec3 bRgb = max(accum.rgb - frontPremul, vec3(0.0));
        behindColour = vec4(bRgb * light * (1.0 - frontAlpha), bA);
      }
      """;

  // Translucent depth-write shader: reads tgbuffer1.x (the distant translucent front-surface NDC
  // depth) and writes gl_FragDepth. Used after the translucent colour pass to merge the distant
  // translucent depth into irisPrivateDepthTexture, so shader packs can detect LOD translucent
  // pixels via vxDepthTexTrans (texelFetch(vxDepthTexTrans, p).r < 1.0). Discards where there is
  // no translucent coverage (depth == 0 or 1). The caller configures GL_LEQUAL + stencil == 0 so
  // closer opaque terrain and near-scene coverage are preserved.
  private static final String GLSL_TRANSLUCENT_DEPTH_WRITE =
      """
      #version 410 core

      uniform sampler2DRect uTgbuffer1Tex;
      uniform vec2 uSharedSize;
      uniform vec2 uTargetSize;

      vec2 sharedPixelForTarget(vec2 targetPixel) {
        vec2 shared = targetPixel * (uSharedSize / max(uTargetSize, vec2(1.0)));
        return vec2(shared.x, uSharedSize.y - shared.y);
      }

      void main() {
        float depth = texture(uTgbuffer1Tex, sharedPixelForTarget(gl_FragCoord.xy)).x;
        if (depth <= 0.0 || depth >= 1.0) discard;
        gl_FragDepth = depth;
      }
      """;

  // Loaded-volume clip (P1), stencil-mask form for the strict Iris path. A standalone fragment
  // program (NOT the budgeted colour program) that reconstructs each distant fragment's depth from
  // the distant depth carrier (gbuffer1.x for opaque, tgbuffer1.x for translucent - both .x of a
  // sampler2DRect) and marks stencil := 1 (via the caller's GL_REPLACE) where that fragment lies
  // inside the loaded volume, exactly like GLSL_BOUND_CLIP does in-shader for vanilla. Run right
  // after the near-coverage mask, it ADDS to the same coverage stencil; the colour pass then draws
  // only where stencil==0 (no near geometry AND beyond the loaded volume). This is the single
  // forced occlusion divergence in action: vanilla clips in-shader, Iris clips via this stencil
  // mask, both fed the same shared bound texture - no new vanilla/Iris fork, no colour-program
  // sampler. Its own samplers (distant depth + bound) live on units 0/1 of THIS program only.
  private static final String GLSL_BOUND_MASK =
      """
      #version 410 core

      uniform sampler2DRect uDistantDepthTex;
      uniform sampler2D uBoundDepthTex;
      uniform vec2 uBoundSize;
      uniform vec2 uSharedSize;
      uniform vec2 uTargetSize;

      vec2 sharedPixelForTarget(vec2 targetPixel) {
        vec2 shared = targetPixel * (uSharedSize / max(uTargetSize, vec2(1.0)));
        return vec2(shared.x, uSharedSize.y - shared.y);
      }

      void main() {
        vec2 targetPixel = gl_FragCoord.xy;
        // The distant carrier's .x is the fragment's Voxy-NDC depth (g.depth), the SAME space the
        // bound was rasterized in, so the compare is direct - no reprojection (matches the vanilla
        // in-shader arm and voxy-fabric quads.frag).
        float gd = clamp(texture(uDistantDepthTex, sharedPixelForTarget(targetPixel)).x, 0.0, 1.0);
        if (gd <= 0.0 || gd >= 1.0) {
          // No distant coverage here: leave the (near-mask) stencil untouched.
          discard;
        }
        vec2 boundPixel = targetPixel * (uBoundSize / max(uTargetSize, vec2(1.0)));
        ivec2 texel = ivec2(clamp(floor(boundPixel), vec2(0.0), uBoundSize - vec2(1.0)));
        float bound = texelFetch(uBoundDepthTex, texel, 0).r;
        const float BOUND_EPSILON = 0.00001;
        // Voxy NDC is non-reverse: inside the loaded volume == nearer than the boundary == smaller.
        if (gd > bound - BOUND_EPSILON) {
          // Distant fragment is beyond the loaded volume: keep the stencil as the near mask left it.
          discard;
        }
        // Inside the loaded volume: fall through so GL_REPLACE writes stencil := 1 (blocks distant).
      }
      """;

  // MC lightmap, used by the vanilla built-in patch and the litColor debug mode only.
  private static final String GLSL_LIGHTMAP =
      """

      uniform sampler2D uLightmapTex;
      uniform sampler2D lightSampler;
      """;

  // Debug visualisation main(). Appended after GBUFFER_DECODE + NEAR_MASK + LIGHTMAP.
  private static final String GLSL_DEBUG_MAIN =
      """

      layout(location = 0) out vec4 fragColor;

      uniform int uDebugMode;

      float hashUnit(uint value) {
        value ^= value >> 16u;
        value *= 0x7feb352du;
        value ^= value >> 15u;
        value *= 0x846ca68bu;
        value ^= value >> 16u;
        return float(value & 255u) / 255.0;
      }

      float directionalTint(bool shaded, uint face) {
        if (!shaded) {
          return 1.0;
        }
        if ((face >> 1u) == 1u) {
          return 0.8;
        }
        if ((face >> 1u) == 2u) {
          return 0.6;
        }
        if (face == 1u) {
          return 1.0;
        }
        return 0.5;
      }

      void main() {
        vec2 targetPixel = gl_FragCoord.xy;
        vec2 sharedPixel = sharedPixelForTarget(targetPixel);
        VoxyGbufferTexel g = sampleVoxyGbuffer(sharedPixel);
        bool shaded = ((g.flags >> 6u) & 1u) != 0u;
        float outputDepth = projectDepth(rev3d(vec3(targetPixel / max(uTargetSize, vec2(1.0)), g.depth)));
        if (g.coverage <= 0.0 || g.depth <= 0.0 || g.depth >= 1.0 || isHiddenByNearDepth(targetPixel, outputDepth)) {
          discard;
        }
        if (uDebugMode == 1) {
          fragColor = vec4(g.colour.rgb, 1.0);
        } else if (uDebugMode == 5) {
          vec4 light = texture(uLightmapTex, g.lightMap);
          vec4 litColor = g.colour * g.tint * light;
          litColor.rgb *= directionalTint(shaded, g.face);
          fragColor = vec4(litColor.rgb, 1.0);
        } else if (uDebugMode == 2) {
          fragColor = vec4(hashUnit(g.customId), hashUnit(g.customId * 1664525u + 1013904223u), hashUnit(g.customId * 22695477u + 1u), 1.0);
        } else if (uDebugMode == 3) {
          fragColor = vec4(vec3(g.depth), 1.0);
        } else if (uDebugMode == 6) {
          // R = block light (lightMap.x), G = sky light (lightMap.y). Remap from gbuffer encoding
          // range [0.03125, 0.96875] -> [0, 1] so "fully lit sky" shows as pure green and unlit
          // distant terrain shows as black-or-near-black. Confirms whether the gbuffer carries
          // valid sky-light data for the distant LOD.
          vec2 lm = clamp((g.lightMap - 0.03125) / 0.9375, vec2(0.0), vec2(1.0));
          fragColor = vec4(lm.x, lm.y, 0.0, 1.0);
        } else {
          fragColor = vec4(vec3(g.coverage), 1.0);
        }
        gl_FragDepth = outputDepth;
      }
      """;

  // Shared opaque colour main() for BOTH targets. voxy_OverrideFragCoord substitutes for
  // gl_FragCoord in patched pack code (see the Java-level rewrite in buildFragmentShader; the
  // bridge's own main() keeps the built-in); its .z lane MUST stay in Voxy NDC space (g.depth), not
  // the vanilla-remapped outputDepth, because the patched pack rebinds gbufferProjection/Inverse to
  // vxProj/vxProjInv and its ScreenToView() inverts in Voxy NDC.
  //
  // The one genuine, forced divergence is the near occlusion + the written gl_FragDepth:
  //
  //   * Vanilla (includeNearMask == true) discards behind the near depth in-shader and writes
  //     vanilla-NDC depth (outputDepth) into the MC depth buffer, because it composites against the
  //     real MC depth attachment via a hardware depth test.
  //   * Strict Iris (includeNearMask == false) is masked instead by the runOpaquePass stencil
  //     coverage (draws only where the near scene is empty), so it needs no uSourceDepthTex unit --
  //     keeping the active samplers at gbuffer0-2 so a pack's own samplers fit Apple GL4.1's usable
  //     15-unit budget -- and writes Voxy-NDC depth (g.depth) into the private depth-stencil the
  //     pack samples as vxDepthTexOpaque/vxDepthTexTrans (Complementary's deferred1.glsl
  //     reconstructs it with vxProjInv and tests z0lod < 1.0, so non-Voxy pixels must read the
  //     cleared far value 1.0).
  private static String opaqueColorMain(boolean includeNearMask) {
    String maskBlock =
        includeNearMask
            ? "        float outputDepth =\n"
                + "            projectDepth(rev3d(vec3(targetPixel / max(uTargetSize, vec2(1.0)),"
                + " g.depth)));\n"
                + "        if (isHiddenByNearDepth(targetPixel, outputDepth)) {\n"
                + "          discard;\n"
                + "        }\n"
            : "";
    String fragDepth = includeNearMask ? "outputDepth" : "g.depth";
    return """

      void main() {
        vec2 targetPixel = gl_FragCoord.xy;
        vec2 sharedPixel = sharedPixelForTarget(targetPixel);
        VoxyGbufferTexel g = sampleVoxyGbuffer(sharedPixel);
        if (g.coverage <= 0.0 || g.depth <= 0.0 || g.depth >= 1.0) {
          discard;
        }
__VOXY_OPAQUE_MASK__        voxyQuadFlags = g.flags;
        voxy_OverrideFragCoord = vec4(gl_FragCoord.xy, g.depth, gl_FragCoord.w);
        // parameters.tile/uv keep the GL46 split: tile = quad tile (uvTile.zw), uv = atlas uv.
        VoxyFragmentParameters parameters =
            VoxyFragmentParameters(
                g.colour, g.tile, g.uv, int(g.face), g.modelId, g.lightMap, g.tint, g.customId);
        voxy_emitFragment(parameters);
        gl_FragDepth = __VOXY_OPAQUE_FRAGDEPTH__;
      }
      """
        .replace("__VOXY_OPAQUE_MASK__", maskBlock)
        .replace("__VOXY_OPAQUE_FRAGDEPTH__", fragDepth);
  }

  // Strict-Iris colour main with the DEBUG_STRICT_ALBEDO override. Identical to
  // GLSL_COLOR_MAIN_NO_MASK except it overwrites the pack's lit gbufferData0 with the raw gbuffer
  // albedo*tint after voxy_emitFragment. Referencing gbufferData0 here is safe because the strict
  // path always embeds a pack patch that declares `layout(location = 0) out vec4 gbufferData0;`
  // (see Complementary voxy_opaque.glsl); this is a diagnostic shape, not a production path.
  private static final String GLSL_COLOR_MAIN_NO_MASK_DEBUG_ALBEDO =
      """

      // DIAGNOSTIC helper for the "worldy" probe: world-space Y from private Voxy-NDC depth.
      float voxyDebugWorldY(float depth) {
        vec2 ndcXY = gl_FragCoord.xy / vec2(viewWidth, viewHeight) * 2.0 - 1.0;
        vec4 clip = vec4(ndcXY, depth * 2.0 - 1.0, 1.0);
        vec4 view = vxProjInv * clip;
        view /= view.w;
        vec3 playerPos = mat3(vxModelViewInv) * view.xyz + vxModelViewInv[3].xyz;
        return playerPos.y + cameraPosition.y;
      }

      void main() {
        vec2 targetPixel = gl_FragCoord.xy;
        vec2 sharedPixel = sharedPixelForTarget(targetPixel);
        VoxyGbufferTexel g = sampleVoxyGbuffer(sharedPixel);
        if (g.coverage <= 0.0 || g.depth <= 0.0 || g.depth >= 1.0) {
          discard;
        }
        voxyQuadFlags = g.flags;
        voxy_OverrideFragCoord = vec4(gl_FragCoord.xy, g.depth, gl_FragCoord.w);
        VoxyFragmentParameters parameters =
            VoxyFragmentParameters(
                g.colour, g.tile, g.uv, int(g.face), g.modelId, g.lightMap, g.tint, g.customId);
        voxy_emitFragment(parameters);
        // DIAGNOSTIC: replace the pack's lit colour with the selected gbuffer source (see
        // DEBUG_STRICT_ALBEDO / DEBUG_STRICT_ALBEDO_EXPR).
        gbufferData0 = vec4(__VOXY_DEBUG_ALBEDO_EXPR__, 1.0);
        gl_FragDepth = g.depth;
      }
      """;

  /**
   * Builds the bridge fragment shader.
   *
   * @param includeNearMask when true the near-depth mask + lightmap blocks are inlined (vanilla
   *     single pass and debug). When false (strict Iris single pass) they are omitted so only
   *     gbuffer0-2 stay active and the shader pack's samplers fit; occlusion is instead enforced by
   *     the hardware depth test against the Voxy private depth attachment that {@link
   *     #runOpaquePass} pre-seeds with the Iris near depth.
   */
  // Returns the gbuffer decode prologue. The reconstruction samplers are sampler2DRect because they
  // are backed by IOSurface RECTANGLE textures (see RESULTS.md); texture(sampler, vec2) reads them.
  private static String gbufferDecode() {
    return GLSL_GBUFFER_DECODE;
  }

  // Translucent decode prologue. Mirrors GLSL_GBUFFER_DECODE's pack-facing interface
  // (VoxyFragmentParameters, voxyQuadFlags, voxy_OverrideFragCoord, the projection helpers) so the
  // same pack patch contract compiles, but reconstructs from the 2-sampler tgbuffer0/1
  // FRONT-surface
  // ABI written by quad_raster.metal's TranslucentFragmentOut. Only 2 base samplers are used (vs
  // the
  // opaque path's 3) so a pack water program's samplers still fit Apple GL4.1's usable 15-unit
  // budget. Atlas uv/tile and modelId are not stored for translucents (Metal stores the resolved
  // albedo instead), so they are reported as 0; the gbuffers_water patch shades from sampledColour
  // (the resolved water albedo), lightMap, tint and alpha.
  private static final String GLSL_TGBUFFER_DECODE =
      """
      #version 410 core

      uniform sampler2DRect uTgbuffer0Tex;  // .x albedoPacked .y lightPacked .z tintPacked .w face/flags/coverage
      uniform sampler2DRect uTgbuffer1Tex;  // .x ndc depth .y alpha .z customId&0xFFFFFF .w customId>>24
      uniform vec2 uSharedSize;
      uniform vec2 uTargetSize;
      uniform mat4 uInvVoxyMvp;
      uniform mat4 uVanillaMvp;

      uint voxyQuadFlags = 0u;

      struct VoxyFragmentParameters {
        vec4 sampledColour;
        vec2 tile;
        vec2 uv;
        int face;
        uint modelId;
        vec2 lightMap;
        vec4 tinting;
        uint customId;
      };

      uint decodePackedUint(float value) {
        return uint(max(value, 0.0));
      }

      vec3 unpackRgb8(uint packed) {
        return vec3(float((packed >> 16u) & 255u), float((packed >> 8u) & 255u), float(packed & 255u))
            / 255.0;
      }

      struct VoxyTranslucentTexel {
        float coverage;
        vec4 colour;
        vec2 uv;
        vec2 tile;
        float depth;
        float alpha;
        vec2 lightMap;
        vec4 tint;
        uint modelId;
        uint customId;
        uint face;
        uint flags;
      };

      VoxyTranslucentTexel sampleVoxyTranslucent(vec2 sharedPixel) {
        vec4 t0 = texture(uTgbuffer0Tex, sharedPixel);
        vec4 t1 = texture(uTgbuffer1Tex, sharedPixel);
        VoxyTranslucentTexel t;
        t.colour = vec4(unpackRgb8(decodePackedUint(t0.x)), 1.0);
        uint lightPacked = decodePackedUint(t0.y);
        t.lightMap = vec2(float((lightPacked >> 12u) & 4095u), float(lightPacked & 4095u)) / 4095.0;
        t.tint = vec4(unpackRgb8(decodePackedUint(t0.z)), 1.0);
        uint faceFlagsCoverage = decodePackedUint(t0.w);
        t.coverage = float(faceFlagsCoverage & 1u);
        t.flags = (faceFlagsCoverage >> 1u) & 255u;
        t.face = faceFlagsCoverage >> 9u;
        t.depth = clamp(t1.x, 0.0, 1.0);
        t.alpha = clamp(t1.y, 0.0, 1.0);
        t.customId = decodePackedUint(t1.z) | (decodePackedUint(t1.w) << 24u);
        // Not stored for translucents (the opaque ABI carries these; the translucent ABI stores the
        // resolved albedo instead). Report 0 so a pack patch that touches them stays well-defined.
        t.uv = vec2(0.0);
        t.tile = vec2(0.0);
        t.modelId = 0u;
        return t;
      }

      vec3 rev3d(vec3 clip) {
        vec4 view = uInvVoxyMvp * vec4(clip * 2.0 - 1.0, 1.0);
        return view.xyz / view.w;
      }

      float projectDepth(vec3 pos) {
        vec4 view = uVanillaMvp * vec4(pos, 1.0);
        float depth = view.z / view.w;
        depth = min(1.0 - 2.0 / 16777215.0, depth);
        depth = depth * 0.5 + 0.5;
        depth = gl_DepthRange.diff * depth + gl_DepthRange.near;
        return clamp(depth, 0.0, 1.0);
      }

      vec2 sharedPixelForTarget(vec2 targetPixel) {
        vec2 shared = targetPixel * (uSharedSize / max(uTargetSize, vec2(1.0)));
        return vec2(shared.x, uSharedSize.y - shared.y);
      }

      vec4 voxy_OverrideFragCoord;
      """;

  // Built-in (no shader pack) distant water shade, expressed as a voxy_emitFragment patch so
  // vanilla
  // reuses the SAME translucent main as the strict Iris path -- vanilla is a default
  // specialization,
  // not a separate renderer. Mirrors the opaque VANILLA_PATCH: lightmap * tint * albedo + the
  // directional face shade, keeping the real water alpha for blending. Declares its own colour
  // output and reads voxyQuadFlags (the shared main sets it before calling voxy_emitFragment).
  private static final String VANILLA_WATER_PATCH =
      """

      layout(location = 0) out vec4 voxyWaterColor;
      uniform sampler2DRect uTgbufferAccumTex;

      float voxyWaterFaceTint(bool shaded, uint face) {
        if (!shaded) {
          return 1.0;
        }
        if ((face >> 1u) == 1u) {
          return 0.8;
        }
        if ((face >> 1u) == 2u) {
          return 0.6;
        }
        if (face == 1u) {
          return 1.0;
        }
        return 0.5;
      }

      void voxy_emitFragment(VoxyFragmentParameters parameters) {
        vec4 light = texture(uLightmapTex, parameters.lightMap);
        vec4 frontColor = vec4(parameters.sampledColour.rgb, 1.0) * parameters.tinting * light;
        bool shaded = ((voxyQuadFlags >> 6u) & 1u) != 0u;
        float frontFaceShade = voxyWaterFaceTint(shaded, uint(parameters.face));
        frontColor.rgb *= frontFaceShade;
        float frontAlpha = parameters.sampledColour.a;

        vec2 sp = sharedPixelForTarget(gl_FragCoord.xy);
        vec4 accum = texture(uTgbufferAccumTex, sp);
        float bA = max(accum.a - frontAlpha, 0.0);

        if (bA <= 0.001) {
          voxyWaterColor = vec4(frontColor.rgb, frontAlpha);
          return;
        }

        vec3 frontPremul = parameters.sampledColour.rgb * parameters.tinting.rgb * frontFaceShade * frontAlpha;
        // accum.rgb is an order-independent SUM of premultiplied flat colours (Metal blends the
        // accum target additively because within-section draw order is arbitrary). Subtracting the
        // front layer leaves sum(behind flats); attenuating once by (1 - frontAlpha) is exact for
        // two layers in any order and mildly overestimates the deepest layers beyond that.
        vec3 bRgb = max(accum.rgb - frontPremul, vec3(0.0));
        vec3 behindLit = bRgb * light.rgb * (1.0 - frontAlpha);

        float combinedAlpha = frontAlpha + bA;
        vec3 combinedPremul = frontColor.rgb * frontAlpha + behindLit;
        voxyWaterColor = vec4(combinedPremul / max(combinedAlpha, 0.001), combinedAlpha);
      }
      """;

  // Shared translucent colour main() for BOTH vanilla and strict Iris: reconstruct the front
  // translucent surface (resolved back-to-front by Metal into tgbuffer0/1) and feed it to
  // voxy_emitFragment. Vanilla supplies VANILLA_WATER_PATCH; Iris supplies the pack's
  // gbuffers_water
  // patch. The near-scene occlusion is resolved OUTSIDE this main and differs per draw target (the
  // one genuine, forced divergence): the strict Iris pass uses a stencil coverage mask + the pack's
  // translucent blend and writes no gl_FragDepth, while the vanilla pass draws into the MC
  // framebuffer with a hardware depth test and so needs the gl_FragDepth tail below. sampledColour
  // carries the real water alpha for blending.
  private static String translucentColorMain(boolean vanilla) {
    // Vanilla draws into the MC framebuffer with a hardware depth test, so it writes gl_FragDepth,
    // and it applies the loaded-volume clip (P1) in-shader. The strict Iris path does neither here:
    // it writes no gl_FragDepth and its loaded-volume clip rides the stencil-mask pass instead.
    //
    // Why the loaded-volume bound and NOT a hardware depth test against the near water: the distant
    // LOD water and the near Sodium water are the SAME surface (the same water level), so they have
    // (near-)identical depth across the whole near region. A depth test there z-fights and draws the
    // distant water over the near water (full-surface flicker + double water). The volume bound
    // instead discards distant water wherever the near scene is loaded, so the two never co-occupy.
    String boundDiscard =
        vanilla
            ? "        if (isInsideLoadedBound(targetPixel, g.depth)) {\n"
                + "          discard;\n"
                + "        }\n"
            : "";
    String tail =
        vanilla
            ? "        gl_FragDepth ="
                + " projectDepth(rev3d(vec3(targetPixel / max(uTargetSize, vec2(1.0)), g.depth)));\n"
            : "";
    return """

      void main() {
        vec2 targetPixel = gl_FragCoord.xy;
        vec2 sharedPixel = sharedPixelForTarget(targetPixel);
        VoxyTranslucentTexel g = sampleVoxyTranslucent(sharedPixel);
        if (g.coverage <= 0.0 || g.depth <= 0.0 || g.depth >= 1.0 || g.alpha <= 0.0) {
          discard;
        }
__VOXY_TRANSLUCENT_BOUND__        voxyQuadFlags = g.flags;
        voxy_OverrideFragCoord = vec4(gl_FragCoord.xy, g.depth, gl_FragCoord.w);
        VoxyFragmentParameters parameters =
            VoxyFragmentParameters(
                vec4(g.colour.rgb, g.alpha), g.tile, g.uv, int(g.face), g.modelId, g.lightMap,
                g.tint, g.customId);
        voxy_emitFragment(parameters);
__VOXY_TRANSLUCENT_TAIL__      }
      """
        .replace("__VOXY_TRANSLUCENT_BOUND__", boundDiscard)
        .replace("__VOXY_TRANSLUCENT_TAIL__", tail);
  }

  // Diagnostic vanilla water main (see DEBUG_TRANSLUCENT). Green where a translucent fragment is
  // present, faint red wash elsewhere; no depth/discard so it always draws.
  private static final String GLSL_VANILLA_TRANSLUCENT_DEBUG_MAIN =
      """

      layout(location = 0) out vec4 voxyWaterColor;

      void main() {
        vec2 targetPixel = gl_FragCoord.xy;
        vec2 sharedPixel = sharedPixelForTarget(targetPixel);
        VoxyTranslucentTexel g = sampleVoxyTranslucent(sharedPixel);
        // Keep uLightmapTex / uInvVoxyMvp / uVanillaMvp live so the vanilla program passes
        // hasRequiredUniforms() (they get stripped to location -1 otherwise). Scaled to ~0 so the
        // probe colours below are unaffected.
        float keep =
            (texture(uLightmapTex, g.lightMap).r +
             projectDepth(rev3d(vec3(targetPixel / max(uTargetSize, vec2(1.0)), g.depth)))) * 1e-9;
        if (g.coverage > 0.0) {
          voxyWaterColor = vec4(0.0, 1.0, keep, 1.0);
        } else {
          voxyWaterColor = vec4(1.0, 0.0, keep, 0.25);
        }
        gl_FragDepth = 0.0;
      }
      """;

  private String buildFragmentShader(
      int debugMode, String shaderHeader, String patchSource, boolean includeNearMask) {
    StringBuilder shader = new StringBuilder(gbufferDecode());
    if (includeNearMask) {
      shader.append(GLSL_NEAR_MASK).append(GLSL_LIGHTMAP);
    }
    if (debugMode != DEBUG_NONE) {
      // Debug always runs vanilla-style (includeNearMask=true), so the mask/lightmap blocks exist.
      return shader.append(GLSL_DEBUG_MAIN).toString();
    }
    // Rewrite gl_FragCoord -> voxy_OverrideFragCoord in patched pack source only. main() (below)
    // keeps the real built-in gl_FragCoord. See GLSL_GBUFFER_DECODE's voxy_OverrideFragCoord
    // declaration for the full rationale: Apple's GL4.1 driver preprocessor silently ignores
    // attempts to redefine the built-in gl_FragCoord via #define, so we substitute at the Java
    // string level to guarantee the per-pixel Voxy-NDC depth reaches the pack's ScreenToView().
    String rewrittenPatch =
        REWRITE_GL_FRAG_COORD.matcher(patchSource).replaceAll("voxy_OverrideFragCoord");
    shader.append("\n").append(shaderHeader).append("\n").append(rewrittenPatch).append("\n");
    if (includeNearMask) {
      shader.append(opaqueColorMain(true));
    } else {
      shader.append(
          DEBUG_STRICT_ALBEDO
              ? GLSL_COLOR_MAIN_NO_MASK_DEBUG_ALBEDO.replace(
                  "__VOXY_DEBUG_ALBEDO_EXPR__", DEBUG_STRICT_ALBEDO_EXPR)
              : opaqueColorMain(false));
    }
    String composed = shader.toString();
    if (!includeNearMask) {
      // See the class-level Apple GL4.1 transform comment: hoisting is the root-cause fix for the
      // linker SIGSEGV, pruning is a cheap upstream cleanup. Vanilla pass skips both.
      composed = pruneUnreachableFunctions(composed);
      composed = hoistGlobalInitializers(composed);
    }
    return composed;
  }

  /**
   * Removes every function not transitively reachable from {@code main()} (plus any function
   * referenced from global scope), mirroring Iris {@code CompatibilityTransformer}'s
   * unused-function removal.
   *
   * <p>This is a cleanup, not the Apple GL4.1 linker fix: hoisting global initializers (see {@link
   * #hoistGlobalInitializers}) is what actually defuses the {@code
   * glpLLVMGetFunctionGlobalVariableUse} SIGSEGV. Pruning is kept because it typically halves the
   * compiled shader (a shader pack ships an entire lighting library that {@code voxy_emitFragment}
   * usually exercises a small fraction of), which both speeds up compilation and is cheap defensive
   * armour against driver bugs that scale with program size or symbol-table pressure.
   *
   * <p>Reachability is sound: an actually-called function appears as an identifier inside a
   * reachable body, so it is kept. Over-removal could therefore only happen if a real call were
   * missed, which would surface as a loud {@code undefined function} compile error rather than a
   * silent miscompile. Any parsing failure falls back to the original (unpruned) source, so the
   * worst case is the pre-existing behaviour. The source is already fully preprocessed (only a
   * single {@code #version} directive, no {@code #include}/{@code #define}) and GLSL has no string
   * literals, which is what makes brace-matching + identifier scanning safe here.
   */
  private static String pruneUnreachableFunctions(String source) {
    try {
      String stripped = stripGlslComments(source);
      List<FunctionRegion> functions = findTopLevelFunctions(stripped);
      if (functions.isEmpty()) {
        return source;
      }
      Set<String> functionNames = new HashSet<>();
      for (FunctionRegion f : functions) {
        functionNames.add(f.name);
      }
      if (!functionNames.contains("main")) {
        return source; // unexpected shape; don't risk pruning
      }
      // Build call edges (function -> declared functions referenced in its body) and mark the spans
      // covered by function definitions so the remaining global text can seed extra roots.
      Map<String, Set<String>> callees = new HashMap<>();
      boolean[] inFunction = new boolean[stripped.length()];
      for (FunctionRegion f : functions) {
        Set<String> edges = callees.computeIfAbsent(f.name, k -> new HashSet<>());
        collectFunctionRefs(stripped, f.start, f.end, functionNames, edges);
        for (int i = f.start; i < f.end; i++) {
          inFunction[i] = true;
        }
      }
      // Roots: main plus any declared function referenced from global scope (e.g. global
      // initializers or a forward-declared prototype), so we never strip a globally-referenced fn.
      Set<String> roots = new HashSet<>();
      roots.add("main");
      StringBuilder globalText = new StringBuilder();
      for (int i = 0; i < stripped.length(); i++) {
        if (!inFunction[i]) {
          globalText.append(stripped.charAt(i));
        }
      }
      collectFunctionRefs(globalText, 0, globalText.length(), functionNames, roots);
      // BFS the reachable set.
      Set<String> reachable = new HashSet<>();
      Deque<String> queue = new ArrayDeque<>(roots);
      while (!queue.isEmpty()) {
        String name = queue.poll();
        if (!reachable.add(name)) {
          continue;
        }
        Set<String> edges = callees.get(name);
        if (edges != null) {
          for (String c : edges) {
            if (!reachable.contains(c)) {
              queue.add(c);
            }
          }
        }
      }
      if (reachable.size() == functionNames.size()) {
        return stripped; // nothing to prune; still return the comment-stripped form
      }
      // Reassemble, dropping unreachable definitions (functions are in source order).
      StringBuilder out = new StringBuilder(stripped.length());
      int cursor = 0;
      for (FunctionRegion f : functions) {
        out.append(stripped, cursor, f.start);
        if (reachable.contains(f.name)) {
          out.append(stripped, f.start, f.end);
        }
        cursor = f.end;
      }
      out.append(stripped, cursor, stripped.length());
      if (DUMP_SHADERS) {
        Logger.info(
            "gl41metal: pruned Iris colour shader from "
                + functionNames.size()
                + " to "
                + reachable.size()
                + " functions ("
                + source.length()
                + " -> "
                + out.length()
                + " chars)");
      }
      return out.toString();
    } catch (RuntimeException e) {
      Logger.warn("gl41metal: unused-function pruning failed, compiling full shader: " + e);
      return source;
    }
  }

  // A top-level function definition: its name and the [start, end) span (header through closing }).
  private record FunctionRegion(String name, int start, int end) {}

  // Strips // line and /* block */ comments. GLSL has no string/char literals so this needs no
  // string-awareness. Block comments become a single space so they can't merge adjacent tokens.
  private static String stripGlslComments(String src) {
    int n = src.length();
    StringBuilder out = new StringBuilder(n);
    int i = 0;
    while (i < n) {
      char c = src.charAt(i);
      if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
        i += 2;
        while (i < n && src.charAt(i) != '\n') {
          i++;
        }
      } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
        i += 2;
        while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
          i++;
        }
        i = Math.min(n, i + 2);
        out.append(' ');
      } else {
        out.append(c);
        i++;
      }
    }
    return out.toString();
  }

  // Enumerates top-level function definitions. A top-level brace whose preceding header (since the
  // last top-level ';' or '}') trims to something ending in ')' is a function body; anything else
  // (struct / interface / uniform block) is skipped over. Bodies are jumped via brace matching, so
  // only ever sees depth-0 braces.
  private static List<FunctionRegion> findTopLevelFunctions(String src) {
    List<FunctionRegion> result = new ArrayList<>();
    int n = src.length();
    int unitStart = 0;
    int i = 0;
    while (i < n) {
      char c = src.charAt(i);
      if (c == ';') {
        unitStart = i + 1;
        i++;
      } else if (c == '}') {
        unitStart = i + 1;
        i++;
      } else if (c == '{') {
        String header = src.substring(unitStart, i).strip();
        int bodyEnd = matchBrace(src, i);
        if (bodyEnd < 0) {
          break; // unbalanced; stop and keep what we found
        }
        if (header.endsWith(")")) {
          String name = extractFunctionName(header);
          if (name != null) {
            result.add(new FunctionRegion(name, unitStart, bodyEnd));
          }
          unitStart = bodyEnd; // function definitions have no trailing ';'
        }
        // For both function and non-function blocks, resume scanning after the matched block.
        i = bodyEnd;
      } else {
        i++;
      }
    }
    return result;
  }

  // Returns the identifier immediately preceding the first '(' of a function header, or null.
  private static String extractFunctionName(String header) {
    int paren = header.indexOf('(');
    if (paren < 0) {
      return null;
    }
    int end = paren;
    while (end > 0 && Character.isWhitespace(header.charAt(end - 1))) {
      end--;
    }
    int start = end;
    while (start > 0) {
      char ch = header.charAt(start - 1);
      if (Character.isLetterOrDigit(ch) || ch == '_') {
        start--;
      } else {
        break;
      }
    }
    if (start >= end) {
      return null;
    }
    char first = header.charAt(start);
    if (!(Character.isLetter(first) || first == '_')) {
      return null;
    }
    return header.substring(start, end);
  }

  // Index after the '}' matching the '{' at openIdx, or -1 if unbalanced.
  private static int matchBrace(String src, int openIdx) {
    int depth = 0;
    int n = src.length();
    for (int i = openIdx; i < n; i++) {
      char c = src.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return i + 1;
        }
      }
    }
    return -1;
  }

  // Adds to out every declared-function name that is *called* in src[start, end). A call is the
  // only
  // way a function name can be referenced in GLSL (no function pointers), so we require the next
  // non-whitespace character after the identifier to be '('. This deliberately ignores collisions
  // with variable / struct / field names that merely share a function's spelling, which is what
  // makes the reachability set tight enough to match what Iris compiles. Number literals (incl.
  // type
  // suffixes like 255u / 0x1p2f) are skipped so they can't be misread as identifiers.
  private static void collectFunctionRefs(
      CharSequence src, int start, int end, Set<String> functionNames, Set<String> out) {
    int i = start;
    while (i < end) {
      char c = src.charAt(i);
      if (Character.isLetter(c) || c == '_') {
        int j = i + 1;
        while (j < end) {
          char d = src.charAt(j);
          if (Character.isLetterOrDigit(d) || d == '_') {
            j++;
          } else {
            break;
          }
        }
        String id = src.subSequence(i, j).toString();
        if (functionNames.contains(id)) {
          int k = j;
          while (k < end && Character.isWhitespace(src.charAt(k))) {
            k++;
          }
          if (k < end && src.charAt(k) == '(') {
            out.add(id);
          }
        }
        i = j;
      } else if (Character.isDigit(c)) {
        int j = i + 1;
        while (j < end) {
          char d = src.charAt(j);
          if (Character.isLetterOrDigit(d) || d == '_' || d == '.') {
            j++;
          } else {
            break;
          }
        }
        i = j;
      } else {
        i++;
      }
    }
  }

  /**
   * Hoists every non-{@code const} file-scope variable initializer to the top of {@code main()},
   * leaving bare global declarations behind.
   *
   * <p>Apple's GL4.1 GLSL linker SIGSEGVs in {@code glpLLVMGetFunctionGlobalVariableUse} while
   * analysing the global-initializer dependency graph that a shader-pack fragment patch produces
   * (e.g. {@code vec3 upVec = normalize(gbufferModelView[1].xyz);}, {@code sunVec =
   * GetSunVector();} and their transitive chains through the std140 uniforms). Iris's own programs
   * never hit this because the pack computes that preamble in the VERTEX stage and passes it as
   * {@code flat in} varyings; our full-screen distant-terrain bridge has no vertex stage, so the
   * identical preamble lands as fragment-scope global initializers. The crash is specific to
   * global-init analysis: a single function using many globals is fine (Iris's own {@code main}
   * does), only the synthesized initializer graph overflows.
   *
   * <p>Moving the initializers into {@code main()} in source order is behaviour-preserving: source
   * order is already dependency order (GLSL requires declaration-before-use), {@code main()} is the
   * sole entry point, and it runs the assignments before sampling/shading. {@code const} globals
   * keep their compile-time-constant initializers. Any parse anomaly falls back to the
   * untransformed source, and a mis-hoist would surface as a loud compile error rather than a
   * silent miscompile.
   */
  private static String hoistGlobalInitializers(String src) {
    try {
      List<FunctionRegion> funcs = findTopLevelFunctions(src);
      if (funcs.isEmpty()) {
        return src;
      }
      StringBuilder out = new StringBuilder(src.length());
      StringBuilder hoist = new StringBuilder();
      int cursor = 0;
      for (FunctionRegion f : funcs) {
        appendGlobalRegionHoisted(src, cursor, f.start, out, hoist);
        out.append(src, f.start, f.end);
        cursor = f.end;
      }
      appendGlobalRegionHoisted(src, cursor, src.length(), out, hoist);
      if (hoist.length() == 0) {
        return src;
      }
      String result = out.toString();
      int mainIdx = result.indexOf("void main(");
      int brace = mainIdx < 0 ? -1 : result.indexOf('{', mainIdx);
      if (brace < 0) {
        return src;
      }
      String injected = result.substring(0, brace + 1) + "\n" + hoist + result.substring(brace + 1);
      if (DUMP_SHADERS) {
        Logger.info(
            "gl41metal: hoisted global initializers into main() (Apple GL4.1 linker workaround)");
      }
      return injected;
    } catch (RuntimeException e) {
      Logger.warn("gl41metal: global-initializer hoisting failed, compiling as-is: " + e);
      return src;
    }
  }

  // Splits src[start,end) (a region outside any function) into ';'-terminated, brace/paren/bracket
  // aware statements. Each non-const variable declaration with an initializer becomes a bare
  // declaration in `out`, with its `name = init;` assignment appended to `hoist`. Everything else
  // (blocks, qualified declarations, prototypes, directives, whitespace) is copied verbatim.
  private static void appendGlobalRegionHoisted(
      String src, int start, int end, StringBuilder out, StringBuilder hoist) {
    int i = start;
    int stmtStart = start;
    int paren = 0;
    int bracket = 0;
    int brace = 0;
    while (i < end) {
      char c = src.charAt(i);
      switch (c) {
        case '(' -> paren++;
        case ')' -> paren--;
        case '[' -> bracket++;
        case ']' -> bracket--;
        case '{' -> brace++;
        case '}' -> brace--;
        default -> {}
      }
      if (c == ';' && paren == 0 && bracket == 0 && brace == 0) {
        out.append(hoistStatement(src.substring(stmtStart, i), hoist)).append(';');
        i++;
        stmtStart = i;
      } else {
        i++;
      }
    }
    if (stmtStart < end) {
      out.append(src, stmtStart, end);
    }
  }

  // Given one global statement (without its trailing ';'), returns the bare declaration text and
  // appends any hoisted `name = init;` assignments to `hoist`. Non-declarations are returned as-is.
  private static String hoistStatement(String stmt, StringBuilder hoist) {
    String t = stmt.strip();
    if (t.isEmpty() || t.indexOf('{') >= 0 || t.startsWith("#")) {
      return stmt;
    }
    if (startsWithKeyword(t, "const")
        || startsWithKeyword(t, "uniform")
        || startsWithKeyword(t, "in")
        || startsWithKeyword(t, "out")
        || startsWithKeyword(t, "flat")
        || startsWithKeyword(t, "layout")
        || startsWithKeyword(t, "precision")
        || startsWithKeyword(t, "struct")) {
      return stmt;
    }
    if (topLevelAssignIndex(t) < 0) {
      return stmt;
    }
    int typeEnd = 0;
    while (typeEnd < t.length() && !Character.isWhitespace(t.charAt(typeEnd))) {
      typeEnd++;
    }
    String type = t.substring(0, typeEnd);
    if (!type.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      return stmt;
    }
    String rest = t.substring(typeEnd).strip();
    StringBuilder bare = new StringBuilder();
    StringBuilder local = new StringBuilder();
    List<String> declarators = splitTopLevel(rest, ',');
    boolean hoisted = false;
    for (int d = 0; d < declarators.size(); d++) {
      String decl = declarators.get(d).strip();
      if (d > 0) {
        bare.append(", ");
      }
      int eq = topLevelAssignIndex(decl);
      if (eq < 0) {
        bare.append(decl);
        continue;
      }
      String name = decl.substring(0, eq).strip();
      String init = decl.substring(eq + 1).strip();
      // GLSL "T name[N] = T[N](...);" mixes the array-size suffix into the LHS. Splitting that
      // into "T name[N]; name[N] = T[N](...);" turns the size suffix into an out-of-bounds index
      // access on the hoisted assignment, which the GLSL compiler rejects statically as
      // "Index N beyond bounds (size N)". Real shader packs do this for jitter tables, blue-noise
      // offset tables, etc. (Complementary's lib/antialiasing/jitter.glsl is one example). Leave
      // array declarators inline; a literal array initializer at file scope is not part of the
      // global-init dependency graph that crashes Apple's GL4.1 linker, so skipping hoisting
      // here does not regress the SIGSEGV fix.
      if (name.indexOf('[') >= 0) {
        bare.append(decl);
        continue;
      }
      bare.append(name);
      local.append("  ").append(name).append(" = ").append(init).append(";\n");
      hoisted = true;
    }
    if (!hoisted) {
      return stmt;
    }
    hoist.append(local);
    String lead = stmt.substring(0, stmt.length() - stmt.stripLeading().length());
    return lead + type + " " + bare;
  }

  private static boolean startsWithKeyword(String s, String kw) {
    if (!s.startsWith(kw)) {
      return false;
    }
    if (s.length() == kw.length()) {
      return true;
    }
    char next = s.charAt(kw.length());
    return !(Character.isLetterOrDigit(next) || next == '_');
  }

  // First top-level single '=' (skipping ==, !=, <=, >=), or -1. Tracks (), [], {} nesting.
  private static int topLevelAssignIndex(String s) {
    int paren = 0;
    int bracket = 0;
    int brace = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '(' -> paren++;
        case ')' -> paren--;
        case '[' -> bracket++;
        case ']' -> bracket--;
        case '{' -> brace++;
        case '}' -> brace--;
        case '=' -> {
          if (paren == 0 && bracket == 0 && brace == 0) {
            char prev = i > 0 ? s.charAt(i - 1) : ' ';
            char next = i + 1 < s.length() ? s.charAt(i + 1) : ' ';
            if (prev != '=' && prev != '!' && prev != '<' && prev != '>' && next != '=') {
              return i;
            }
          }
        }
        default -> {}
      }
    }
    return -1;
  }

  private static List<String> splitTopLevel(String s, char sep) {
    List<String> parts = new ArrayList<>();
    int paren = 0;
    int bracket = 0;
    int brace = 0;
    int last = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '(' -> paren++;
        case ')' -> paren--;
        case '[' -> bracket++;
        case ']' -> bracket--;
        case '{' -> brace++;
        case '}' -> brace--;
        default -> {}
      }
      if (c == sep && paren == 0 && bracket == 0 && brace == 0) {
        parts.add(s.substring(last, i));
        last = i + 1;
      }
    }
    parts.add(s.substring(last));
    return parts;
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

  /**
   * Lazily (re)allocates the translucent pass's private depth-STENCIL attachment. Distinct from
   * {@link #irisPrivateDepthTexture} so the translucent stencil mask never clobbers the opaque
   * Voxy-NDC depth the pack samples as vxDepthTexOpaque. Only the stencil component is used (the
   * near-coverage mask); the depth component is throwaway.
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
   * Lazily compiles the near-scene coverage stencil-mask program (see {@link #GLSL_STENCIL_MASK}).
   * Returns null if it ever fails to compile, so the strict Iris path skips that frame's distant
   * output rather than painting unmasked distant terrain over the near scene.
   */
  private Shader ensureStencilMaskProgram() {
    if (this.stencilMaskProgram != null) {
      return this.stencilMaskProgram;
    }
    if (this.stencilMaskProgramFailed) {
      return null;
    }
    try {
      Shader shader =
          Shader.make()
              .addSource(
                  ShaderType.VERTEX,
                  ShaderLoader.parse("voxy:lod/gl41metal/interop_bridge.vert", "410 core"))
              .addSource(ShaderType.FRAGMENT, GLSL_STENCIL_MASK)
              .compile()
              .name("GL41Metal distant terrain near-coverage stencil mask");
      this.stencilMaskNearDepthUniform = glGetUniformLocation(shader.id(), "uNearDepth");
      this.stencilMaskNearSizeUniform = glGetUniformLocation(shader.id(), "uNearSize");
      this.stencilMaskTargetSizeUniform = glGetUniformLocation(shader.id(), "uTargetSize");
      this.stencilMaskReverseUniform = glGetUniformLocation(shader.id(), "uReverseDepth");
      this.stencilMaskProgram = shader;
      return shader;
    } catch (RuntimeException e) {
      this.stencilMaskProgramFailed = true;
      Logger.error("Failed to compile Voxy GL41Metal near-coverage stencil mask", e);
      return null;
    }
  }

  private Shader ensureBehindLayersProgram() {
    if (this.behindLayersProgram != null) {
      return this.behindLayersProgram;
    }
    if (this.behindLayersProgramFailed) {
      return null;
    }
    try {
      Shader shader =
          Shader.make()
              .addSource(
                  ShaderType.VERTEX,
                  ShaderLoader.parse("voxy:lod/gl41metal/interop_bridge.vert", "410 core"))
              .addSource(ShaderType.FRAGMENT, GLSL_BEHIND_LAYERS_BLEND)
              .compile()
              .name("GL41Metal translucent behind-layers blend");
      this.behindLayersTgb0Uniform = glGetUniformLocation(shader.id(), "uTgbuffer0Tex");
      this.behindLayersTgb1Uniform = glGetUniformLocation(shader.id(), "uTgbuffer1Tex");
      this.behindLayersAccumUniform = glGetUniformLocation(shader.id(), "uTgbufferAccumTex");
      this.behindLayersLightmapUniform = glGetUniformLocation(shader.id(), "uLightmapTex");
      this.behindLayersSharedSizeUniform = glGetUniformLocation(shader.id(), "uSharedSize");
      this.behindLayersTargetSizeUniform = glGetUniformLocation(shader.id(), "uTargetSize");
      this.behindLayersProgram = shader;
      return shader;
    } catch (RuntimeException e) {
      this.behindLayersProgramFailed = true;
      Logger.error("Failed to compile Voxy GL41Metal behind-layers blend program", e);
      return null;
    }
  }

  private Shader ensureTranslucentDepthProgram() {
    if (this.translucentDepthProgram != null) {
      return this.translucentDepthProgram;
    }
    if (this.translucentDepthProgramFailed) {
      return null;
    }
    try {
      Shader shader =
          Shader.make()
              .addSource(
                  ShaderType.VERTEX,
                  ShaderLoader.parse("voxy:lod/gl41metal/interop_bridge.vert", "410 core"))
              .addSource(ShaderType.FRAGMENT, GLSL_TRANSLUCENT_DEPTH_WRITE)
              .compile()
              .name("GL41Metal translucent distant depth write");
      this.translucentDepthTgbuffer1Uniform = glGetUniformLocation(shader.id(), "uTgbuffer1Tex");
      this.translucentDepthSharedSizeUniform = glGetUniformLocation(shader.id(), "uSharedSize");
      this.translucentDepthTargetSizeUniform = glGetUniformLocation(shader.id(), "uTargetSize");
      this.translucentDepthProgram = shader;
      return shader;
    } catch (RuntimeException e) {
      this.translucentDepthProgramFailed = true;
      Logger.error("Failed to compile Voxy GL41Metal translucent depth write program", e);
      return null;
    }
  }

  private Shader ensureBoundMaskProgram() {
    if (this.boundMaskProgram != null) {
      return this.boundMaskProgram;
    }
    if (this.boundMaskProgramFailed) {
      return null;
    }
    try {
      Shader shader =
          Shader.make()
              .addSource(
                  ShaderType.VERTEX,
                  ShaderLoader.parse("voxy:lod/gl41metal/interop_bridge.vert", "410 core"))
              .addSource(ShaderType.FRAGMENT, GLSL_BOUND_MASK)
              .compile()
              .name("GL41Metal distant terrain loaded-volume bound stencil mask");
      this.boundMaskDistantDepthUniform = glGetUniformLocation(shader.id(), "uDistantDepthTex");
      this.boundMaskBoundDepthUniform = glGetUniformLocation(shader.id(), "uBoundDepthTex");
      this.boundMaskBoundSizeUniform = glGetUniformLocation(shader.id(), "uBoundSize");
      this.boundMaskSharedSizeUniform = glGetUniformLocation(shader.id(), "uSharedSize");
      this.boundMaskTargetSizeUniform = glGetUniformLocation(shader.id(), "uTargetSize");
      this.boundMaskProgram = shader;
      return shader;
    } catch (RuntimeException e) {
      this.boundMaskProgramFailed = true;
      Logger.error("Failed to compile Voxy GL41Metal loaded-volume bound stencil mask", e);
      return null;
    }
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
    Shader maskShader = this.ensureBoundMaskProgram();
    if (maskShader == null) {
      return;
    }
    maskShader.bind();
    glUniform1i(this.boundMaskDistantDepthUniform, GBUFFER0_TEXTURE_UNIT);
    glUniform1i(this.boundMaskBoundDepthUniform, SOURCE_DEPTH_TEXTURE_UNIT);
    glUniform2f(this.boundMaskBoundSizeUniform, bound.width(), bound.height());
    glUniform2f(this.boundMaskSharedSizeUniform, slot.width(), slot.height());
    glUniform2f(this.boundMaskTargetSizeUniform, job.outputWidth(), job.outputHeight());
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
    if (!enabled) {
      return;
    }
    if (boundDepthTexUniform >= 0) {
      glUniform1i(boundDepthTexUniform, BOUND_TEXTURE_UNIT);
    }
    if (boundSizeUniform >= 0) {
      glUniform2f(boundSizeUniform, bound.width(), bound.height());
    }
    this.bind2DTexture(BOUND_TEXTURE_UNIT, bound.texture());
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

  @Override
  public void close() {
    if (this.program != null) {
      this.program.shader().free();
      this.program = null;
    }
    if (this.translucentProgram != null) {
      this.translucentProgram.shader().free();
      this.translucentProgram = null;
    }
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
    if (this.stencilMaskProgram != null) {
      this.stencilMaskProgram.free();
      this.stencilMaskProgram = null;
    }
    if (this.behindLayersProgram != null) {
      this.behindLayersProgram.free();
      this.behindLayersProgram = null;
    }
    if (this.translucentDepthProgram != null) {
      this.translucentDepthProgram.free();
      this.translucentDepthProgram = null;
    }
    if (this.boundMaskProgram != null) {
      this.boundMaskProgram.free();
      this.boundMaskProgram = null;
    }
    glDeleteVertexArrays(this.fullscreenVao);
    glDeleteFramebuffers(this.nearDepthFramebuffer);
    glDeleteFramebuffers(this.depthCopyFramebuffer);
    glDeleteFramebuffers(this.framebuffer);
  }

  private static String parseDebugStrictAlbedoExpr() {
    String src = System.getProperty("voxy.gl41metal.debugStrictAlbedoSource", "albedoTint").trim();
    return switch (src) {
      case "albedo" -> "g.colour.rgb";
      case "tint" -> "g.tint.rgb";
      case "white" -> "vec3(0.75)";
        // Lighting-input probes: visualise exactly what the pack's voxy_emitFragment receives in
        // the
        // STRICT path (bridgeDebugMode=lightMap runs the vanilla path, which can sample a different
        // slot/timing). rawsky/rawblock are the gbuffer lightmap channels; skycoord/blockcoord are
        // the
        // MC lightmap coords the pack derives (lmCoord = (lightMap-0.03125)*1.06667). simplelit is
        // a
        // minimal skylight-only shade to confirm our gbuffer can produce a lit image at all.
      case "rawsky" -> "vec3(g.lightMap.y)";
      case "rawblock" -> "vec3(g.lightMap.x)";
      case "skycoord" -> "vec3(clamp((g.lightMap.y - 0.03125) * 1.06667, 0.0, 1.0))";
        // Sky-light gate probe: reproduces DoLighting's skyLightShadowMult = lightmap.y^8
        // (mainLighting.glsl: lightmapY2 = lightmap.y^2; skyLightShadowMult =
        // pow2(pow2(lightmapY2))).
        // This is the exact term that multiplies BOTH the directional sun and the screenspace LOD
        // shadow on distant terrain, so the "above a world-Y it suddenly goes very bright" symptom
        // is
        // this curve turning the LOD skylight (lightmap.y) into a near-binary step. If this probe's
        // hard edge lands exactly on the brightness threshold, the cause is the LOD skylight value
        // (baked light in the Metal gbuffer) under the pack's ^8 curve - NOT lightColor or the sun
        // basis (already proven correct). Compare against rawsky: a smooth rawsky here means the
        // step
        // is the inherent ^8 curve (matches voxy-fabric); a hard step already in rawsky means our
        // baked LOD skylight is quantized/wrong.
      case "skygate" -> "vec3(pow(clamp((g.lightMap.y - 0.03125) * 1.06667, 0.0, 1.0), 8.0))";
      case "blockcoord" -> "vec3(clamp((g.lightMap.x - 0.03125) * 1.06667, 0.0, 0.9333))";
      case "simplelit" -> "g.colour.rgb * g.tint.rgb * clamp(g.lightMap.y, 0.15, 1.0)";
        // Pack lighting-global probes: simplelit proved our gbuffer can be lit, so a single global
        // multiplier in the pack's DoLighting must be ~0. These read the pack's own computed
        // globals
        // (in scope because main() is appended after the pack patch + its lightAndAmbientColors
        // include)
        // to find which one collapses. ambient/lightcolor are the scene light colours; darkness
        // reads
        // (1-darknessLightFactor) so a garbage non-zero potion factor shows up as black. If any
        // identifier does not exist for a given pack the strict program fails to compile (moire
        // fallback), meaning that probe is unavailable for that pack.
      case "ambient" -> "ambientColor";
      case "lightcolor" -> "lightColor";
      case "darkness" -> "vec3(1.0 - darknessLightFactor)";
        // Sun-basis probes: voxy_opaque.glsl derives the ENTIRE sun basis from gbufferModelView (=
        // vxModelView). sunvec/sdotu/upvec are file-scope globals (constant across the frame), so a
        // correct frame shows a FLAT colour; a per-pixel-varying or obviously-wrong colour means
        // vxModelView's rotation does not match MC's gbufferModelView convention. sunlit is the raw
        // directional sun term dot(normal, lightVec): the lit (white) side of the terrain must face
        // the visible on-screen sun; if it is lit from the wrong direction the matrix/normal basis
        // is
        // wrong, which would corrupt every sunlight-dependent effect at once.
      case "sunvec" -> "sunVec * 0.5 + 0.5";
      case "sdotu" -> "vec3(clamp(SdotU * 0.5 + 0.5, 0.0, 1.0))";
      case "upvec" -> "upVec * 0.5 + 0.5";
      case "normal" -> "normalize(normal) * 0.5 + 0.5";
      case "sunlit" -> "vec3(clamp(dot(normalize(normal), normalize(lightVec)), 0.0, 1.0))";
        // World-height probe: reconstructs the fragment's world-space Y from the private Voxy-NDC
        // depth via vxProjInv/vxModelViewInv (the same path voxy_emitFragment uses for playerPos),
        // then bands it every 16 blocks. Confirms whether the "above a Y threshold it goes very
        // bright" symptom lines up with a real height boundary (lightmap/skylight) or with a
        // reconstruction discontinuity. cameraPosition makes the bands absolute, not
        // camera-relative.
        // Uses the GLSL helper voxyDebugWorldY() injected just above main() so this stays readable.
      case "worldy" -> "vec3(fract(voxyDebugWorldY(g.depth) / 16.0))";
        // Screenspace LOD-shadow probe: reads colortex18 (written by deferred1's GetLODShadows for
        // VOXY) at the current pixel. This is the buffer DoLighting reprojects into shadowMult
        // (mainLighting.glsl VOXY_PATCH branch): colortex18 = lodShadow + OSIEBCA, so lit terrain
        // reads ~1 (white) and shadowed reads ~OSIEBCA (~black). The ambient/lightColor probes
        // proved
        // the scene light colours are fine, so if the distant is lit here (white) the bug is
        // elsewhere
        // in DoLighting; if it is ~black here, deferred1's depth-march is wrongly shadowing the
        // whole
        // sunlit distant (the prime "dark distant under a normal sky" suspect). We sample at the
        // raw
        // pixel (no reprojection) which matches DoLighting for a still camera. colortex18 is in
        // scope
        // because the pack includes declare `uniform sampler2D colortex18` ahead of this main().
      case "ssshadow" -> "vec3(texelFetch(colortex18, ivec2(gl_FragCoord.xy), 0).r)";
      default -> "g.colour.rgb * g.tint.rgb";
    };
  }

  private static int parseDebugMode() {
    String mode = System.getProperty("voxy.gl41metal.bridgeDebugMode", "none").trim();
    return switch (mode) {
      case "color" -> DEBUG_COLOR;
      case "litColor" -> DEBUG_LIT_COLOR;
      case "customId" -> DEBUG_CUSTOM_ID;
      case "depth" -> DEBUG_DEPTH;
      case "coverage" -> DEBUG_COVERAGE;
      case "lightMap" -> DEBUG_LIGHTMAP;
      default -> DEBUG_NONE;
    };
  }

  private record BridgeProgram(
      int shaderKey,
      Shader shader,
      boolean usesNearMask,
      int gbuffer0TexUniform,
      int gbuffer1TexUniform,
      int gbuffer2TexUniform,
      int sourceDepthTexUniform,
      int lightmapTexUniform,
      int lightSamplerUniform,
      int sharedSizeUniform,
      int targetSizeUniform,
      int sourceDepthSizeUniform,
      int reverseDepthUniform,
      int useManualDepthMaskUniform,
      int debugModeUniform,
      int invVoxyMvpUniform,
      int vanillaMvpUniform) {
    boolean hasRequiredUniforms() {
      // The strict Iris colour pass (usesNearMask=false) deliberately omits uSourceDepthTex and the
      // near-mask uniforms so it stays at 3 base samplers; only require them for the masking shapes
      // (vanilla single pass and the debug visualisations).
      //
      // uInvVoxyMvp/uVanillaMvp likewise feed projectDepth()/rev3d(), which ONLY the masking shapes
      // call to remap g.depth into vanilla NDC for gl_FragDepth. The strict Iris pass writes
      // gl_FragDepth = g.depth (Voxy NDC) directly, so those two matrices have no live reference
      // and
      // pruneUnreachableFunctions + the GLSL compiler strip them; requiring them on the strict
      // program wrongly rejected it ("missing required uniforms"), which silently disabled the
      // whole
      // strict path (no private depth -> vxDepthTex* fell back to the Iris near depth -> distant
      // LOD
      // read as sky). Gate them on usesNearMask alongside the other near-mask uniforms.
      boolean nearMaskOk =
          !this.usesNearMask
              || (this.sourceDepthTexUniform >= 0
                  && this.sourceDepthSizeUniform >= 0
                  && this.reverseDepthUniform >= 0
                  && this.useManualDepthMaskUniform >= 0
                  && this.invVoxyMvpUniform >= 0
                  && this.vanillaMvpUniform >= 0);
      return this.gbuffer0TexUniform >= 0
          && this.gbuffer1TexUniform >= 0
          && this.gbuffer2TexUniform >= 0
          && nearMaskOk
          && (DEBUG_MODE != DEBUG_LIT_COLOR || this.lightmapTexUniform >= 0)
          && this.sharedSizeUniform >= 0
          && this.targetSizeUniform >= 0
          && (DEBUG_MODE == DEBUG_NONE || this.debugModeUniform >= 0);
    }
  }

  // Translucent colour program. Only 2 base samplers (tgbuffer0/1) plus the pack's water samplers,
  // and no near-mask uniforms (occlusion is the stencil coverage mask). The projection matrices are
  // optional: the pack patch may reference rev3d/projectDepth, but the strict translucent main()
  // does not, so they are stripped when unused (location -1) and must not be required.
  private record TranslucentBridgeProgram(
      int shaderKey,
      Shader shader,
      boolean vanilla,
      int tgbuffer0TexUniform,
      int tgbuffer1TexUniform,
      int tgbufferAccumTexUniform,
      int lightmapTexUniform,
      int lightSamplerUniform,
      int sharedSizeUniform,
      int targetSizeUniform,
      int invVoxyMvpUniform,
      int vanillaMvpUniform,
      int boundDepthTexUniform,
      int boundSizeUniform,
      int boundEnabledUniform) {
    boolean hasRequiredUniforms() {
      boolean base =
          this.tgbuffer0TexUniform >= 0
              && this.tgbuffer1TexUniform >= 0
              && this.sharedSizeUniform >= 0
              && this.targetSizeUniform >= 0;
      if (!this.vanilla) {
        return base;
      }
      // The vanilla water main() samples the MC lightmap and writes gl_FragDepth via
      // projectDepth()/rev3d(), so it requires the lightmap sampler and both projection matrices.
      return base
          && (this.lightmapTexUniform >= 0 || this.lightSamplerUniform >= 0)
          && this.invVoxyMvpUniform >= 0
          && this.vanillaMvpUniform >= 0;
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
      int[] textures1d,
      int[] rectangleTextures,
      int[] textures2d,
      int[] textures3d,
      int[] samplers,
      int depthFunc,
      boolean depthMask,
      boolean colorMaskR,
      boolean colorMaskG,
      boolean colorMaskB,
      boolean colorMaskA,
      boolean blendEnabled,
      boolean depthEnabled,
      int viewportX,
      int viewportY,
      int viewportWidth,
      int viewportHeight) {
    static StateSnapshot capture() {
      int oldActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
      int[] viewport = new int[4];
      glGetIntegerv(GL_VIEWPORT, viewport);
      boolean colorMaskR;
      boolean colorMaskG;
      boolean colorMaskB;
      boolean colorMaskA;
      boolean depthMask;
      try (MemoryStack stack = MemoryStack.stackPush()) {
        var colorMask = stack.malloc(4);
        glGetBooleanv(GL_COLOR_WRITEMASK, colorMask);
        colorMaskR = colorMask.get(0) != 0;
        colorMaskG = colorMask.get(1) != 0;
        colorMaskB = colorMask.get(2) != 0;
        colorMaskA = colorMask.get(3) != 0;
        var depthMaskBuffer = stack.malloc(1);
        glGetBooleanv(GL_DEPTH_WRITEMASK, depthMaskBuffer);
        depthMask = depthMaskBuffer.get(0) != 0;
      }
      int maxUnit =
          Math.max(glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS), LIGHTMAP_TEXTURE_UNIT + 1);
      int[] rectangleTextures = new int[maxUnit];
      int[] textures1d = new int[maxUnit];
      int[] textures2d = new int[maxUnit];
      int[] textures3d = new int[maxUnit];
      int[] samplers = new int[maxUnit];
      for (int unit = 0; unit < maxUnit; unit++) {
        glActiveTexture(GL_TEXTURE0 + unit);
        textures1d[unit] = glGetInteger(GL_TEXTURE_BINDING_1D);
        rectangleTextures[unit] = glGetInteger(GL_TEXTURE_BINDING_RECTANGLE);
        textures2d[unit] = glGetInteger(GL_TEXTURE_BINDING_2D);
        textures3d[unit] = glGetInteger(GL_TEXTURE_BINDING_3D);
        samplers[unit] = glGetInteger(GL_SAMPLER_BINDING);
      }
      glActiveTexture(oldActiveTexture);
      return new StateSnapshot(
          glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),
          glGetInteger(GL_READ_FRAMEBUFFER_BINDING),
          glGetInteger(GL_CURRENT_PROGRAM),
          glGetInteger(GL_VERTEX_ARRAY_BINDING),
          glGetInteger(GL_ARRAY_BUFFER_BINDING),
          glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING),
          oldActiveTexture,
          textures1d,
          rectangleTextures,
          textures2d,
          textures3d,
          samplers,
          glGetInteger(GL_DEPTH_FUNC),
          depthMask,
          colorMaskR,
          colorMaskG,
          colorMaskB,
          colorMaskA,
          org.lwjgl.opengl.GL11C.glIsEnabled(GL_BLEND),
          org.lwjgl.opengl.GL11C.glIsEnabled(GL_DEPTH_TEST),
          viewport[0],
          viewport[1],
          viewport[2],
          viewport[3]);
    }

    void restore() {
      glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.drawFramebuffer);
      glBindFramebuffer(GL_READ_FRAMEBUFFER, this.readFramebuffer);
      glUseProgram(this.program);
      glBindVertexArray(this.vao);
      glBindBuffer(GL_ARRAY_BUFFER, this.arrayBuffer);
      glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.elementArrayBuffer);
      for (int unit = 0; unit < this.rectangleTextures.length; unit++) {
        restoreUnit(
            unit,
            this.textures1d[unit],
            this.rectangleTextures[unit],
            this.textures2d[unit],
            this.textures3d[unit],
            this.samplers[unit]);
      }
      glActiveTexture(this.activeTexture);
      glDepthFunc(this.depthFunc);
      glDepthMask(this.depthMask);
      glColorMask(this.colorMaskR, this.colorMaskG, this.colorMaskB, this.colorMaskA);
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
      glViewport(this.viewportX, this.viewportY, this.viewportWidth, this.viewportHeight);
    }

    private static void restoreUnit(
        int unit, int texture1d, int rectangleTexture, int texture2d, int texture3d, int sampler) {
      glActiveTexture(GL_TEXTURE0 + unit);
      org.lwjgl.opengl.GL11C.glBindTexture(org.lwjgl.opengl.GL11C.GL_TEXTURE_1D, texture1d);
      org.lwjgl.opengl.GL11C.glBindTexture(
          org.lwjgl.opengl.GL31C.GL_TEXTURE_RECTANGLE, rectangleTexture);
      org.lwjgl.opengl.GL11C.glBindTexture(GL_TEXTURE_2D, texture2d);
      org.lwjgl.opengl.GL11C.glBindTexture(org.lwjgl.opengl.GL12C.GL_TEXTURE_3D, texture3d);
      glBindSampler(unit, sampler);
    }
  }
}
