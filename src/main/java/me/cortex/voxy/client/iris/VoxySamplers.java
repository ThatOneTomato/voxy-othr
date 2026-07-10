package me.cortex.voxy.client.iris;

import java.util.function.IntSupplier;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.VoxyRenderSystemAccess;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;

public class VoxySamplers {
  public static void addSamplers(
      IrisRenderingPipeline pipeline, SamplerHolder samplers, RenderTargets renderTargets) {
    // The pipeline's own voxy$getPatchData() is not assigned until mid-construction, but this
    // runs DURING construction (the pipeline builds every program's samplers in its
    // constructor), so read the constructor-scoped thread-local set at <init> HEAD, falling
    // back to the field for any non-constructor caller. Reading the (still-null) field here
    // was the cause of vxDepthTexOpaque silently aliasing texture unit 0 / colortex0.
    var patchData = IrisShaderPatch.CONSTRUCTING_PIPELINE_PATCH.get();
    if (patchData == null) {
      patchData = ((VoxyPatchDataAccess) pipeline).voxy$getPatchData();
    }
    if (patchData != null) {
      String[] opaqueNames = new String[] {"vxDepthTexOpaque"};
      String[] translucentNames = new String[] {"vxDepthTexTrans"};
      if (IrisShaderPatch.IMPERSONATE_DISTANT_HORIZONS) {
        opaqueNames = new String[] {"vxDepthTexOpaque", "dhDepthTex1"};
        translucentNames = new String[] {"vxDepthTexTrans", "dhDepthTex", "dhDepthTex0"};
      }

      // Backends that own a private distant-depth target (the GL41Metal bridge) report it via
      // Prefer backend-owned opaque/translucent depth textures so shader packs see the same split
      // Voxy depth contract as GL46's fb/fbTranslucent pair. Backends returning 0 keep the GL46
      // IrisVoxyRenderPipeline path below, falling back to Iris depth targets before first draw.
      IntSupplier gl46Opaque =
          () -> {
            var pipeData = ((IrisVoxyPipelineDataAccess) pipeline).voxy$getPipelineData();
            if (pipeData == null || pipeData.thePipeline == null) {
              return 0;
            }
            // In theory the first frame could be null
            var dt = pipeData.thePipeline.fb.getDepthTex();
            return dt == null ? 0 : dt.id;
          };
      IntSupplier gl46Translucent =
          () -> {
            var pipeData = ((IrisVoxyPipelineDataAccess) pipeline).voxy$getPipelineData();
            if (pipeData == null || pipeData.thePipeline == null) {
              return 0;
            }
            // In theory the first frame could be null
            var dt = pipeData.thePipeline.fbTranslucent.getDepthTex();
            return dt == null ? 0 : dt.id;
          };
      IntSupplier gl41MetalOpaqueFallback = renderTargets::getDepthTexture;
      IntSupplier gl41MetalTranslucentFallback =
          () -> renderTargets.getDepthTextureNoTranslucents().getTextureId();

      // Reference HEAD registers these with the plain 2-arg overload (null GlSampler), so the
      // depth textures are sampled with their own texture parameters instead of a forced
      // sampler object. Keep that exact behaviour.
      samplers.addDynamicSampler(
          () -> voxyDistantDepthOr(true, gl46Opaque, gl41MetalOpaqueFallback), opaqueNames);
      samplers.addDynamicSampler(
          () -> voxyDistantDepthOr(false, gl46Translucent, gl41MetalTranslucentFallback),
          translucentNames);
    }
  }

  private static int voxyDistantDepthOr(
      boolean opaque, IntSupplier gl46Path, IntSupplier gl41MetalFallback) {
    try {
      VoxyRenderSystem voxy = VoxyRenderSystemAccess.getNullable();
      if (voxy == null) {
        return gl46Path.getAsInt();
      }
      int tex =
          opaque
              ? voxy.getVoxyDistantOpaqueDepthTextureId()
              : voxy.getVoxyDistantTranslucentDepthTextureId();
      if (tex != 0) {
        return tex;
      }
      return switch (voxy.getRenderBackendId()) {
        // The gl41metal backend owns a private distant depth but may not have created it
        // yet (early frames); sample the Iris depth target instead of texture 0.
        case GL41METAL -> gl41MetalFallback.getAsInt();
        default -> gl46Path.getAsInt();
      };
    } catch (Throwable ignored) {
      return gl46Path.getAsInt();
    }
  }
}
