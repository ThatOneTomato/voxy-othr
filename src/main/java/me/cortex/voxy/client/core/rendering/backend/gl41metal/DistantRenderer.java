package me.cortex.voxy.client.core.rendering.backend.gl41metal;

import me.cortex.voxy.client.VoxyClient;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.rendering.backend.RenderFrameContext;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge.FrameSlotContext;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.jni.NativeBindings;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.common.util.MemoryBuffer;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;

public final class DistantRenderer {
  void submitTraversal(
      FrameSlotContext slots,
      int slot,
      long frameId,
      RenderFrameContext context,
      Matrix4fc traversalMvp,
      Matrix4fc drawMvp) {
    MemoryBuffer matrices = new MemoryBuffer(32L * Float.BYTES);
    try {
      writeMatrix(matrices.address, traversalMvp);
      writeMatrix(matrices.address + 16L * Float.BYTES, drawMvp);
      float subDivisionSize =
          VoxyConfig.CONFIG.subDivisionSize * computeDrawlistLodFovScale(context);
      NativeBindings.submitTraversal(
          slots.nativeHandle(),
          slot,
          frameId,
          context.cameraX(),
          context.cameraY(),
          context.cameraZ(),
          matrices.address,
          matrices.address + 16L * Float.BYTES,
          subDivisionSize,
          computeEarthRadius(),
          computeNearExclusionRadius(),
          computeRenderDistanceSquared(),
          context.viewportWidth(),
          context.viewportHeight());
    } finally {
      matrices.free();
    }
  }

  private static float computeDrawlistLodFovScale(RenderFrameContext context) {
    // GL46 lets the current projection directly drive LOD subdivision. Direct drawlist keeps the
    // draw projection current, but stabilizes the traversal threshold against transient zoom FOVs
    // (spyglass) so the zoom animation does not repeatedly switch LOD levels while child nodes are
    // still warming.
    if (context.matrices() == null) {
      return 1.0f;
    }
    double configuredFovDegrees = Minecraft.getInstance().options.fov().get();
    if (!(configuredFovDegrees > 0.0) || configuredFovDegrees >= 179.0) {
      return 1.0f;
    }
    double vanillaScale = 1.0 / Math.tan(Math.toRadians(configuredFovDegrees) * 0.5);
    if (!(vanillaScale > 0.0)) {
      return 1.0f;
    }
    float currentScale = Math.abs(context.matrices().projection().m11());
    if (!(currentScale > 0.0f)) {
      return 1.0f;
    }
    return Math.max(1.0f, Math.min(16.0f, (float) (currentScale / vanillaScale)));
  }

  // Distant SSAO sample count for this frame. This is VANILLA-only, matching GL46 where SSAO exists
  // in NormalRenderPipeline and the Iris pipeline has none: with a shader pack active the pack owns
  // distant lighting/AO, and folding our AO into the albedo underneath it double-darkens LOD
  // terrain.
  static int computeSsaoSteps(RenderFrameContext context) {
    if (context.matrices() == null) {
      return 0;
    }
    if (IrisUtil.irisShaderPackEnabled()) {
      return 0;
    }
    return switch (VoxyConfig.CONFIG.getSSAOMode()) {
      case BASIC -> 8;
      case BEST -> 24;
      // GL46's AUTO probes dedicated VRAM to pick a tier; Apple silicon is unified-memory and
      // the Metal pass is tile-local, so the mid tier is always affordable.
      case AUTO, BETTER -> 12;
    };
  }

  // Builds the Voxy distant projection from the current frame's MC projection.
  //
  // The previous implementation synthesised a reference projection from client.options.fov() and
  // multiplied base * inv(syntheticVanilla) * syntheticVoxy to swap the near/far planes. That only
  // produced an identity XY when base.FOV matched options.fov(); any current-frame FOV modifier
  // (spyglass, gameRenderer FOV effects, scoping items) made the XY scaling no longer cancel and
  // the resulting Voxy projection ended up in a different screen-space than vanilla.
  //
  // The new form keeps base.FOV / base.aspect untouched and overrides only the depth-mapping
  // entries (m22, m32) so the Voxy near/far planes replace the vanilla ones in-place. This mirrors
  // voxy-fabric VoxyRenderSystem.computeProjectionMat - the "jank way of just modifying the base
  // raw" path it eventually settled on - and removes the FOV-mismatch failure mode at the source.
  static Matrix4f computeProjectionMat(Matrix4fc base) {
    float near = Minecraft.getInstance().gameRenderer.getRenderDistance() <= 32.0f ? 8.0f : 16.0f;
    if (VoxyClient.disableSodiumChunkRender()) {
      near = 0.1f;
    }
    float far = 16.0f * 3000.0f;
    return new Matrix4f(base)
        .m22((far + near) / (near - far))
        .m32((2.0f * far * near) / (near - far));
  }

  /**
   * Computes every per-frame matrix once. The Voxy projection (and the traversal MVP derived from
   * it) is built a single time and the draw MVPs reuse it, instead of each former {@code compute*}
   * helper rebuilding the projection (with its matrix inversion) and the traversal MVP from scratch
   * in both {@code submitMetalFrame} and {@code submitTraversal}.
   */
  static FrameMatrices computeFrameMatrices(RenderFrameContext context) {
    if (context.matrices() == null) {
      return new FrameMatrices(new Matrix4f(), new Matrix4f(), new Matrix4f(), new Matrix4f());
    }
    Matrix4fc base = context.matrices().projection();
    Matrix4fc modelView = context.matrices().modelView();
    Matrix4f projection = computeProjectionMat(base);
    Matrix4f traversalMvp = new Matrix4f(projection).mul(modelView);
    Matrix4f drawMvp = new Matrix4f(traversalMvp);
    translateByNegativeCameraSubSection(context, drawMvp);
    Matrix4f vanillaDrawMvp = new Matrix4f(base).mul(modelView);
    translateByNegativeCameraSubSection(context, vanillaDrawMvp);
    return new FrameMatrices(projection, traversalMvp, drawMvp, vanillaDrawMvp);
  }

  private static void translateByNegativeCameraSubSection(
      RenderFrameContext context, Matrix4f mvp) {
    int sectionX = floorSection(context.cameraX());
    int sectionY = floorSection(context.cameraY());
    int sectionZ = floorSection(context.cameraZ());
    mvp.translate(
        -(float) (context.cameraX() - (sectionX << 5)),
        -(float) (context.cameraY() - (sectionY << 5)),
        -(float) (context.cameraZ() - (sectionZ << 5)));
  }

  private static int floorSection(double value) {
    return ((int) Math.floor(value)) >> 5;
  }

  static float computeEarthRadius() {
    int earthCurveRatio = VoxyConfig.CONFIG.earthCurveRatio;
    return earthCurveRatio >= 50 ? 6371000.0f / earthCurveRatio : 0.0f;
  }

  private static float computeNearExclusionRadius() {
    // The near-exclusion radius is the cylinder inside which Voxy suppresses its own terrain
    // because Sodium owns the near scene. It must be shrunk INWARD from the Sodium edge so
    // Voxy LOD OVERLAPS the near scene; the stencil/coverage mask (chunk_coverage.vert /
    // chunkoutline/outline.vsh) then hides the overlap exactly where Sodium actually drew.
    //
    // The original formula ADDED lodBoundaryBuffer*16 (wrong sign), pushing the exclusion a
    // chunk PAST the Sodium edge and carving a one-chunk-wide hole at the interface. A first
    // pass over-corrected by treating the buffer as a single block, which left a ~1-block
    // overlap: far too small, since Sodium's OUTERMOST chunk ring is frequently unloaded or
    // frustum-culled, and the Metal traverser also blocks the self-mesh of nodes straddling
    // this radius. The result was a residual ring of missing chunks.
    //
    // Overlap a whole-chunk amount instead. The radius is a coarse world-space cylinder (not
    // the per-chunk coverage mask), so it only needs to stay inside Sodium's GUARANTEED
    // coverage, which is conservatively (renderDistance - 1) chunks. Overlapping
    // max(lodBoundaryBuffer, 1) chunks moves the blocked straddle ring well inside Sodium's
    // range (where Sodium definitely draws) and lets every node at and beyond the old gap
    // render normally, with the stencil mask hiding the in-range overlap.
    int overlapChunks = Math.max(VoxyConfig.CONFIG.lodBoundaryBuffer, 1);
    float renderDistanceBlocks =
        Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0f;
    return Math.max(0.0f, renderDistanceBlocks - overlapChunks * 16.0f);
  }

  // GL46 HierarchicalOcclusionTraverser uploads renderDistance as
  // pow(sectionRenderDistance * 16 * 32, 2) (blocks^2); the Metal traverser uses it for the
  // isWithinRenderDistance XZ cylinder cull and the shouldRenderSelf coarse-self gate that keeps
  // out-of-range giant coarse meshes from rasterising a depth smear across the sky.
  private static float computeRenderDistanceSquared() {
    double radius = (double) VoxyConfig.CONFIG.sectionRenderDistance * 16.0 * 32.0;
    return (float) (radius * radius);
  }

  private static void writeMatrix(long address, Matrix4fc matrix) {
    MemoryUtil.memPutFloat(address, matrix.m00());
    MemoryUtil.memPutFloat(address + 4, matrix.m01());
    MemoryUtil.memPutFloat(address + 8, matrix.m02());
    MemoryUtil.memPutFloat(address + 12, matrix.m03());
    MemoryUtil.memPutFloat(address + 16, matrix.m10());
    MemoryUtil.memPutFloat(address + 20, matrix.m11());
    MemoryUtil.memPutFloat(address + 24, matrix.m12());
    MemoryUtil.memPutFloat(address + 28, matrix.m13());
    MemoryUtil.memPutFloat(address + 32, matrix.m20());
    MemoryUtil.memPutFloat(address + 36, matrix.m21());
    MemoryUtil.memPutFloat(address + 40, matrix.m22());
    MemoryUtil.memPutFloat(address + 44, matrix.m23());
    MemoryUtil.memPutFloat(address + 48, matrix.m30());
    MemoryUtil.memPutFloat(address + 52, matrix.m31());
    MemoryUtil.memPutFloat(address + 56, matrix.m32());
    MemoryUtil.memPutFloat(address + 60, matrix.m33());
  }

  /** All matrices a single GL41Metal frame needs, derived once from the frame context. */
  record FrameMatrices(
      Matrix4f projection, Matrix4f traversalMvp, Matrix4f drawMvp, Matrix4f vanillaDrawMvp) {}
}
