package me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge;

import static org.lwjgl.opengl.GL20C.glGetUniformLocation;

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
import me.cortex.voxy.common.Logger;

/**
 * Owns every GL program of the distant bridge: the cached opaque + translucent colour programs
 * (assembled from the composable GLSL under assets/voxy/shaders/lod/gl41metal/bridge/ plus the
 * shader pack's patch), the four small fixed helper programs (near-coverage stencil mask,
 * behind-layers blend, translucent depth write, loaded-volume bound mask), and the Apple GL4.1
 * source transforms (global-initializer hoisting, unreachable-function pruning) the pack patches
 * need to survive Apple's GLSL linker.
 */
public final class BridgePrograms {
  // Word-boundary regex used by buildFragmentShader() to rewrite gl_FragCoord ->
  // voxy_OverrideFragCoord
  // in patched pack source. Apple's GL4.1 GLSL preprocessor silently refuses to redefine the
  // built-in
  // gl_FragCoord via #define, so we do the substitution at the Java string level before submitting
  // the assembled fragment shader to the driver. See GLSL_GBUFFER_DECODE's voxy_OverrideFragCoord
  // declaration and the buildFragmentShader() comment for the full failure mode.
  private static final Pattern REWRITE_GL_FRAG_COORD = Pattern.compile("\\bgl_FragCoord\\b");
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

  // Captured-vanilla environmental fog, the gl41metal equivalent of GL46's USE_ENV_FOG final
  // blit (blit_texture_depth_cutout.frag): MixinFogRenderer captures the vanilla terrain fog
  // parameters before neutralising them, and the vanilla composite re-applies them to the
  // distant lit colour. uFogParams = (start, end, intensity, density); intensity <= 0 disables
  // (also how the Java side encodes renderVoxyFog=off / degenerate fog). Shape and the distance
  // helper mirror sodium's fog.glsl getFragDistance (0 = spherical, 1 = cylindrical). The
  // position comes from rev3d (camera-relative, the same space GL46 feeds getFragDistance).
  // Shared verbatim by VANILLA_PATCH and VANILLA_WATER_PATCH (separate programs, so each patch
  // embeds its own copy). The Iris paths never include this: shader packs fog their own scene.
  // (The fog block is embedded verbatim inside vanilla_patch.glsl / vanilla_water_patch.glsl.)
  // GL46 quads.frag non-patched lighting expressed as a built-in voxy_emitFragment: sample the MC
  // lightmap with the baked light UV, fold in the conditional tint, then apply the directional face
  // shade. uLightmapTex and voxyQuadFlags are provided by the shared header below.
  static final String VANILLA_PATCH = BridgeGlsl.load("vanilla_patch.glsl");

  private BridgeProgram program;
  private int failedShaderKey = Integer.MIN_VALUE;

  // === Distant translucent (water) bridge state =======================================
  // Separate cached program from the opaque path: the opaque colour pass runs at beginHand RETURN
  // and the translucent pass at beginTranslucents RETURN every frame, so they must NOT share the
  // single-slot opaque program cache (that would thrash a recompile twice per frame). The
  // translucent program samples the tgbuffer0/1 front-surface ABI and runs the pack's translucent
  // patch; see GLSL_TGBUFFER_DECODE / translucentColorMain and renderTranslucent.
  private TranslucentBridgeProgram translucentProgram;
  private int failedTranslucentShaderKey = Integer.MIN_VALUE;

  // Fixed helper programs, lazily compiled once; a failure latches so a broken driver does not
  // retry-compile every frame.
  private StencilMask stencilMaskProgram;
  private boolean stencilMaskProgramFailed;
  private BehindLayers behindLayersProgram;
  private boolean behindLayersProgramFailed;
  private TranslucentDepthWrite translucentDepthProgram;
  private boolean translucentDepthProgramFailed;
  private BoundMask boundMaskProgram;
  private boolean boundMaskProgramFailed;

  BridgeProgram programFor(DistantBridgeJob job) {
    int shaderKey = job.shaderKey();
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
    // The strict Iris path (own framebuffer) omits the near-mask + lightmap blocks so the
    // colour program stays at 3 base samplers; occlusion is done by the hardware depth test in
    // runOpaquePass. Vanilla keeps the inline near mask.
    boolean usesNearMask = !job.ownFramebuffer();
    try {
      String fragmentSource =
          this.buildFragmentShader(job.shaderHeader(), job.fragmentPatch(), usesNearMask);
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
            glGetUniformLocation(shader.id(), "uInvVoxyMvp"),
            glGetUniformLocation(shader.id(), "uVanillaMvp"),
            glGetUniformLocation(shader.id(), "uFogParams"),
            glGetUniformLocation(shader.id(), "uFogColor"),
            glGetUniformLocation(shader.id(), "uFogShape"));
    if (!bridgeProgram.hasRequiredUniforms()) {
      shader.free();
      Logger.error("Voxy GL41Metal distant terrain bridge is missing required uniforms");
      return null;
    }
    return bridgeProgram;
  }

  TranslucentBridgeProgram programForTranslucent(DistantBridgeJob job) {
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
              glGetUniformLocation(shader.id(), "uBoundEnabled"),
              glGetUniformLocation(shader.id(), "uFogParams"),
              glGetUniformLocation(shader.id(), "uFogColor"),
              glGetUniformLocation(shader.id(), "uFogShape"));
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
  private static final String GLSL_GBUFFER_DECODE = BridgeGlsl.load("gbuffer_decode.glsl");

  // Near-depth occlusion mask. Used by the vanilla single pass and the debug visualisations, but
  // NOT by the strict Iris colour pass (which resolves occlusion via the hardware depth test
  // against the private near-seeded depth attachment instead of sampling uSourceDepthTex).
  private static final String GLSL_NEAR_MASK = BridgeGlsl.load("near_mask.glsl");

  // Loaded-volume clip (P1), in-shader form for the vanilla/debug colour programs. Discards distant
  // fragments that lie INSIDE the Sodium near-scene volume (nearer than its far boundary, captured
  // per pixel in uBoundDepthTex by DistantChunkBoundRenderer). This is what stops distant LOD
  // water - which writes no opaque depth, so the near-depth mask cannot hide it - from overlapping
  // the near Sodium water in the transition band. Mirrors voxy-fabric quads.frag's
  // DEPTH_SCALAR_COMPARE(gl_FragCoord.z, depthTex) discard.
  //
  // This block is compiled ONLY into the vanilla/debug programs (alongside GLSL_NEAR_MASK); they
  // have spare texture units. The strict Iris colour program never sees it - its identical clip
  // rides the stencil-mask pass (GLSL_BOUND_MASK) so the budgeted colour program gains no sampler.
  // uBoundEnabled==0 (no sections loaded) disables the clip so a stale boundary is never sampled.
  private static final String GLSL_BOUND_CLIP = BridgeGlsl.load("bound_clip.glsl");

  // Near-scene coverage stencil mask (strict Iris path). A standalone fragment program that samples
  // the Iris near (noHand/opaque) depth and DISCARDS sky pixels so they keep stencil 0, while
  // non-sky (near geometry) pixels pass and get stencil := 1 via the caller's GL_REPLACE op. The
  // distant colour pass then renders only where stencil==0, i.e. where the near scene is empty,
  // matching voxy-fabric's "render only where there isn't mc terrain". Sky thresholds mirror
  // GLSL_NEAR_MASK's reverse-Z handling (vanilla far plane: 1.0 forward / 0.0 reverse). This is a
  // separate 1-sampler program on purpose: it must not add a texture unit to the 15-unit colour
  // program (Apple GL4.1 SIGSEGVs at 16; Complementary already uses 12 pack + 3 gbuffer samplers).
  private static final String GLSL_STENCIL_MASK = BridgeGlsl.load("stencil_mask.frag");

  // Behind-layers blend shader: subtracts the front surface's premultiplied contribution from
  // tgbufferAccum and outputs the remainder as premultiplied colour. Blended with ONE,
  // ONE_MINUS_SRC_ALPHA (premultiplied OVER) so the water behind glass adds its colour to the
  // targets after the front surface (glass) was already composited by the pack's shader. For
  // single-layer translucent surfaces (the dominant ocean case), behind_alpha == 0 and the shader
  // discards, making this a no-op.
  private static final String GLSL_BEHIND_LAYERS_BLEND =
      BridgeGlsl.load("behind_layers_blend.frag");

  // Translucent depth-write shader: reads tgbuffer1.x (the distant translucent front-surface NDC
  // depth) and writes gl_FragDepth. Used after the translucent colour pass to merge the distant
  // translucent depth into irisPrivateDepthTexture, so shader packs can detect LOD translucent
  // pixels via vxDepthTexTrans (texelFetch(vxDepthTexTrans, p).r < 1.0). Discards where there is
  // no translucent coverage (depth == 0 or 1). The caller configures GL_LEQUAL + stencil == 0 so
  // closer opaque terrain and near-scene coverage are preserved.
  private static final String GLSL_TRANSLUCENT_DEPTH_WRITE =
      BridgeGlsl.load("translucent_depth_write.frag");

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
  private static final String GLSL_BOUND_MASK = BridgeGlsl.load("bound_mask.frag");

  // MC lightmap, used by the vanilla built-in patch only.
  private static final String GLSL_LIGHTMAP = BridgeGlsl.load("lightmap.glsl");

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
    return BridgeGlsl.load("opaque_color_main.glsl")
        .replace("__VOXY_OPAQUE_MASK__", maskBlock)
        .replace("__VOXY_OPAQUE_FRAGDEPTH__", fragDepth);
  }

  /**
   * Builds the bridge fragment shader.
   *
   * @param includeNearMask when true the near-depth mask + lightmap blocks are inlined (vanilla
   *     single pass). When false (strict Iris single pass) they are omitted so only gbuffer0-2 stay
   *     active and the shader pack's samplers fit; occlusion is instead enforced by the hardware
   *     depth test against the Voxy private depth attachment that {@link #runOpaquePass} pre-seeds
   *     with the Iris near depth.
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
  private static final String GLSL_TGBUFFER_DECODE = BridgeGlsl.load("tgbuffer_decode.glsl");

  // Built-in (no shader pack) distant water shade, expressed as a voxy_emitFragment patch so
  // vanilla
  // reuses the SAME translucent main as the strict Iris path -- vanilla is a default
  // specialization,
  // not a separate renderer. Mirrors the opaque VANILLA_PATCH: lightmap * tint * albedo + the
  // directional face shade, keeping the real water alpha for blending. Declares its own colour
  // output and reads voxyQuadFlags (the shared main sets it before calling voxy_emitFragment).
  private static final String VANILLA_WATER_PATCH = BridgeGlsl.load("vanilla_water_patch.glsl");

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
    // (near-)identical depth across the whole near region. A depth test there z-fights and draws
    // the
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
    return BridgeGlsl.load("translucent_color_main.glsl")
        .replace("__VOXY_TRANSLUCENT_BOUND__", boundDiscard)
        .replace("__VOXY_TRANSLUCENT_TAIL__", tail);
  }

  /** Applies the Apple GLSL 4.1 linker workarounds to a fully assembled direct-geometry shader. */
  public static String prepareDirectIrisFragment(String source) {
    return hoistGlobalInitializers(pruneUnreachableFunctions(source));
  }

  private String buildFragmentShader(
      String shaderHeader, String patchSource, boolean includeNearMask) {
    StringBuilder shader = new StringBuilder(gbufferDecode());
    if (includeNearMask) {
      shader.append(GLSL_NEAR_MASK).append(GLSL_LIGHTMAP);
    }
    // Rewrite gl_FragCoord -> voxy_OverrideFragCoord in patched pack source only. main() (below)
    // keeps the real built-in gl_FragCoord. See GLSL_GBUFFER_DECODE's voxy_OverrideFragCoord
    // declaration for the full rationale: Apple's GL4.1 driver preprocessor silently ignores
    // attempts to redefine the built-in gl_FragCoord via #define, so we substitute at the Java
    // string level to guarantee the per-pixel Voxy-NDC depth reaches the pack's ScreenToView().
    String rewrittenPatch =
        REWRITE_GL_FRAG_COORD.matcher(patchSource).replaceAll("voxy_OverrideFragCoord");
    shader.append("\n").append(shaderHeader).append("\n").append(rewrittenPatch).append("\n");
    shader.append(opaqueColorMain(includeNearMask));
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

  /**
   * Lazily compiles the near-scene coverage stencil-mask program (see {@link #GLSL_STENCIL_MASK}).
   * Returns null if it ever fails to compile, so the strict Iris path skips that frame's distant
   * output rather than painting unmasked distant terrain over the near scene.
   */
  StencilMask stencilMask() {
    if (this.stencilMaskProgram != null) {
      return this.stencilMaskProgram;
    }
    if (this.stencilMaskProgramFailed) {
      return null;
    }
    try {
      Shader shader =
          compileHelper(GLSL_STENCIL_MASK, "GL41Metal distant terrain near-coverage stencil mask");
      this.stencilMaskProgram =
          new StencilMask(
              shader,
              glGetUniformLocation(shader.id(), "uNearDepth"),
              glGetUniformLocation(shader.id(), "uNearSize"),
              glGetUniformLocation(shader.id(), "uTargetSize"),
              glGetUniformLocation(shader.id(), "uReverseDepth"));
      return this.stencilMaskProgram;
    } catch (RuntimeException e) {
      this.stencilMaskProgramFailed = true;
      Logger.error("Failed to compile Voxy GL41Metal near-coverage stencil mask", e);
      return null;
    }
  }

  BehindLayers behindLayers() {
    if (this.behindLayersProgram != null) {
      return this.behindLayersProgram;
    }
    if (this.behindLayersProgramFailed) {
      return null;
    }
    try {
      Shader shader =
          compileHelper(GLSL_BEHIND_LAYERS_BLEND, "GL41Metal translucent behind-layers blend");
      this.behindLayersProgram =
          new BehindLayers(
              shader,
              glGetUniformLocation(shader.id(), "uTgbuffer0Tex"),
              glGetUniformLocation(shader.id(), "uTgbuffer1Tex"),
              glGetUniformLocation(shader.id(), "uTgbufferAccumTex"),
              glGetUniformLocation(shader.id(), "uLightmapTex"),
              glGetUniformLocation(shader.id(), "uSharedSize"),
              glGetUniformLocation(shader.id(), "uTargetSize"));
      return this.behindLayersProgram;
    } catch (RuntimeException e) {
      this.behindLayersProgramFailed = true;
      Logger.error("Failed to compile Voxy GL41Metal behind-layers blend program", e);
      return null;
    }
  }

  TranslucentDepthWrite translucentDepthWrite() {
    if (this.translucentDepthProgram != null) {
      return this.translucentDepthProgram;
    }
    if (this.translucentDepthProgramFailed) {
      return null;
    }
    try {
      Shader shader =
          compileHelper(GLSL_TRANSLUCENT_DEPTH_WRITE, "GL41Metal translucent distant depth write");
      this.translucentDepthProgram =
          new TranslucentDepthWrite(
              shader,
              glGetUniformLocation(shader.id(), "uTgbuffer1Tex"),
              glGetUniformLocation(shader.id(), "uSharedSize"),
              glGetUniformLocation(shader.id(), "uTargetSize"));
      return this.translucentDepthProgram;
    } catch (RuntimeException e) {
      this.translucentDepthProgramFailed = true;
      Logger.error("Failed to compile Voxy GL41Metal translucent depth write program", e);
      return null;
    }
  }

  BoundMask boundMask() {
    if (this.boundMaskProgram != null) {
      return this.boundMaskProgram;
    }
    if (this.boundMaskProgramFailed) {
      return null;
    }
    try {
      Shader shader =
          compileHelper(
              GLSL_BOUND_MASK, "GL41Metal distant terrain loaded-volume bound stencil mask");
      this.boundMaskProgram =
          new BoundMask(
              shader,
              glGetUniformLocation(shader.id(), "uDistantDepthTex"),
              glGetUniformLocation(shader.id(), "uBoundDepthTex"),
              glGetUniformLocation(shader.id(), "uBoundSize"),
              glGetUniformLocation(shader.id(), "uSharedSize"),
              glGetUniformLocation(shader.id(), "uTargetSize"));
      return this.boundMaskProgram;
    } catch (RuntimeException e) {
      this.boundMaskProgramFailed = true;
      Logger.error("Failed to compile Voxy GL41Metal loaded-volume bound stencil mask", e);
      return null;
    }
  }

  private static Shader compileHelper(String fragmentSource, String name) {
    return Shader.make()
        .addSource(
            ShaderType.VERTEX,
            ShaderLoader.parse("voxy:lod/gl41metal/interop_bridge.vert", "410 core"))
        .addSource(ShaderType.FRAGMENT, fragmentSource)
        .compile()
        .name(name);
  }

  void close() {
    if (this.program != null) {
      this.program.shader().free();
      this.program = null;
    }
    if (this.translucentProgram != null) {
      this.translucentProgram.shader().free();
      this.translucentProgram = null;
    }
    if (this.stencilMaskProgram != null) {
      this.stencilMaskProgram.shader().free();
      this.stencilMaskProgram = null;
    }
    if (this.behindLayersProgram != null) {
      this.behindLayersProgram.shader().free();
      this.behindLayersProgram = null;
    }
    if (this.translucentDepthProgram != null) {
      this.translucentDepthProgram.shader().free();
      this.translucentDepthProgram = null;
    }
    if (this.boundMaskProgram != null) {
      this.boundMaskProgram.shader().free();
      this.boundMaskProgram = null;
    }
  }

  record StencilMask(
      Shader shader,
      int nearDepthUniform,
      int nearSizeUniform,
      int targetSizeUniform,
      int reverseDepthUniform) {}

  record BehindLayers(
      Shader shader,
      int tgbuffer0Uniform,
      int tgbuffer1Uniform,
      int accumUniform,
      int lightmapUniform,
      int sharedSizeUniform,
      int targetSizeUniform) {}

  record TranslucentDepthWrite(
      Shader shader, int tgbuffer1Uniform, int sharedSizeUniform, int targetSizeUniform) {}

  record BoundMask(
      Shader shader,
      int distantDepthUniform,
      int boundDepthUniform,
      int boundSizeUniform,
      int sharedSizeUniform,
      int targetSizeUniform) {}

  record BridgeProgram(
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
      int invVoxyMvpUniform,
      int vanillaMvpUniform,
      int fogParamsUniform,
      int fogColorUniform,
      int fogShapeUniform) {
    boolean hasRequiredUniforms() {
      // The strict Iris colour pass (usesNearMask=false) deliberately omits uSourceDepthTex and the
      // near-mask uniforms so it stays at 3 base samplers; only require them for the vanilla
      // single pass.
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
          && this.sharedSizeUniform >= 0
          && this.targetSizeUniform >= 0;
    }
  }

  // Translucent colour program. Only 2 base samplers (tgbuffer0/1) plus the pack's water samplers,
  // and no near-mask uniforms (occlusion is the stencil coverage mask). The projection matrices are
  // optional: the pack patch may reference rev3d/projectDepth, but the strict translucent main()
  // does not, so they are stripped when unused (location -1) and must not be required.
  record TranslucentBridgeProgram(
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
      int boundEnabledUniform,
      int fogParamsUniform,
      int fogColorUniform,
      int fogShapeUniform) {
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
}
