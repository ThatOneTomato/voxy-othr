package me.cortex.voxy.client.mixin.iris;

import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.rendering.backend.RenderFrameStageState;
import me.cortex.voxy.client.core.rendering.backend.RenderStage;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.client.iris.IGetIrisVoxyPipelineData;
import me.cortex.voxy.client.iris.IGetVoxyPatchData;
import me.cortex.voxy.client.iris.IrisShaderPatch;
import me.cortex.voxy.client.iris.IrisVoxyRenderPipelineData;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public class MixinIrisRenderingPipeline implements IGetVoxyPatchData, IGetIrisVoxyPipelineData {
    @Shadow @Final private CustomUniforms customUniforms;
    @Shadow private ShaderStorageBufferHolder shaderStorageBufferHolder;
    @Unique IrisShaderPatch patchData;
    @Unique
    IrisVoxyRenderPipelineData pipeline;

    // The IrisRenderingPipeline constructor builds every program's samplers
    // (IrisSamplers.addRenderTargetSamplers -> MixinIrisSamplers, which binds vxDepthTexOpaque /
    // vxDepthTexTrans) DURING its body, before patchData below is assigned. MixinIrisSamplers gates
    // Voxy sampler binding on the pack being Voxy-patched, so it needs the patch data at
    // sampler-build time. We cannot inject an instance handler before super() (must be static), so
    // a STATIC HEAD handler stashes the programSet's patch (already populated by MixinProgramSet)
    // into a render-thread ThreadLocal that MixinIrisSamplers consults; the RETURN handler clears
    // it. Without this, the gate saw null throughout sampler construction, vxDepthTexOpaque was
    // never bound, and the pack silently sampled texture unit 0 (colortex0) as the distant depth.
    @Inject(method = "<init>", at = @At("HEAD"), remap = false)
    private static void voxy$stashPatchData(ProgramSet programSet, CallbackInfo ci) {
        IrisShaderPatch.CONSTRUCTING_PIPELINE_PATCH.set(
                ((IGetVoxyPatchData) programSet).voxy$getPatchData());
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/pipeline/transform/ShaderPrinter;resetPrintState()V", shift = At.Shift.AFTER))
    private void voxy$injectPatchDataStore(ProgramSet programSet, CallbackInfo ci) {
        if (IrisUtil.SHADER_SUPPORT) {
            this.patchData = ((IGetVoxyPatchData) programSet).voxy$getPatchData();
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void voxy$clearStashedPatchData(ProgramSet programSet, CallbackInfo ci) {
        IrisShaderPatch.CONSTRUCTING_PIPELINE_PATCH.remove();
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/pipeline/IrisRenderingPipeline;createSetupComputes([Lnet/irisshaders/iris/shaderpack/programs/ComputeSource;Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;Lnet/irisshaders/iris/shaderpack/texture/TextureStage;)[Lnet/irisshaders/iris/gl/program/ComputeProgram;"))
    private void voxy$injectPipeline(ProgramSet programSet, CallbackInfo ci) {
        // Only the GL46 Iris pipeline consumes this data (via IrisVoxyRenderPipeline). On gl41metal
        // hosts (no compute/indirectParameters, e.g. Apple GL4.1) the reference excludes this whole
        // subsystem from compilation; building it anyway touches Iris render targets/custom uniforms
        // and interferes with the gl41metal bridge, so skip it when GL46 cannot be selected.
        if (this.patchData != null
                && me.cortex.voxy.client.core.gl.Capabilities.INSTANCE.compute
                && me.cortex.voxy.client.core.gl.Capabilities.INSTANCE.indirectParameters) {
            this.pipeline = IrisVoxyRenderPipelineData.buildPipeline((IrisRenderingPipeline)(Object)this, this.patchData, this.customUniforms, this.shaderStorageBufferHolder);
        }
    }

    // MUST be HEAD, not RETURN. For gl41metal, FRAME_BEGIN runs submitMetalFrame, which submits the
    // Metal distant pass AND publishes this frame's matrices via VoxyRenderSystem.getLastFrameMatrices().
    // Iris evaluates the pack's global vx* PER_FRAME uniforms (fed by VoxyUniforms) at
    // customUniforms.update(), early inside beginLevelRendering. HEAD publishes frame N BEFORE that
    // call, so the pack's global vx* resolve to frame N - matching the frame-N distant depth the pack
    // reconstructs from (Complementary's taa.glsl / deferred1.glsl reproject the CURRENT-frame
    // vxDepth* with the GLOBAL vx* matrices). RETURN left the global vx* one frame behind the depth,
    // an error that grows with camera velocity (distant terrain darkens/ghosts under translation).
    // The gl46 backend ignores this stage (its setup runs at LEGACY_VIEWPORT_SETUP below).
    @Inject(method = "beginLevelRendering", at = @At("HEAD"), remap = false)
    private void voxy$injectFrameBegin(CallbackInfo ci) {
        var parameters = IrisUtil.getCapturedOrFallbackViewportParameters();
        if (parameters != null) {
            var renderer = ((IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer).voxy$getRenderSystem();
            if (renderer != null) {
                RenderFrameStageState.store(
                        parameters.runStage(
                                renderer, RenderStage.FRAME_BEGIN, RenderFrameStageState.currentFrame()));
            }
        }
    }

    @Inject(method = "beginLevelRendering", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;activeTexture(I)V", shift = At.Shift.BEFORE), remap = false)
    private void voxy$injectViewportSetup(CallbackInfo ci) {
        if (IrisUtil.CAPTURED_VIEWPORT_PARAMETERS != null) {
            var renderer = ((IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer).voxy$getRenderSystem();
            if (renderer != null) {
                RenderFrameStageState.store(
                        IrisUtil.CAPTURED_VIEWPORT_PARAMETERS.runStage(
                                renderer, RenderStage.LEGACY_VIEWPORT_SETUP, RenderFrameStageState.currentFrame()));
            }
        }
    }

    // Iris copies the current opaque depth into depthtex2/noHand inside beginHand().
    // Run after that copy so GL41Metal's near-depth mask samples current-frame depth.
    @Inject(method = "beginHand", at = @At("RETURN"), remap = false)
    private void voxy$injectPreTranslucentBridge(CallbackInfo ci) {
        var parameters = IrisUtil.getCapturedOrFallbackViewportParameters();
        if (parameters != null) {
            var renderer = ((IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer).voxy$getRenderSystem();
            if (renderer != null) {
                parameters.runStage(
                        renderer,
                        RenderStage.PRE_TRANSLUCENT,
                        RenderFrameStageState.consume(),
                        IrisUtil.captureShaderPatchBridgePayload((IrisRenderingPipeline) (Object) this));
            }
        }
    }

    // Distant translucent (water) composite. beginTranslucents() copies depthtex1/noTranslucents (the
    // opaque scene depth) and runs the pack's deferred passes at its START, so at RETURN: the opaque
    // scene is lit into the colour targets, depthtex1 is current-frame fresh, blend is enabled, and
    // the near translucent geometry (Sodium water/glass) has NOT drawn yet. Compositing the distant
    // water here blends it over the lit opaque scene and lets near translucents blend over it after.
    // The backend reuses the same shared slot it sampled for the opaque pass at beginHand RETURN (it
    // was held, not retired) and retires it after this stage; see Gl41MetalRenderBackend Plan A.
    @Inject(method = "beginTranslucents", at = @At("RETURN"), remap = false)
    private void voxy$injectTranslucentBridge(CallbackInfo ci) {
        var parameters = IrisUtil.getCapturedOrFallbackViewportParameters();
        if (parameters != null) {
            var renderer = ((IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer).voxy$getRenderSystem();
            if (renderer != null) {
                parameters.runStage(
                        renderer,
                        RenderStage.TRANSLUCENT,
                        RenderFrameStageState.currentFrame(),
                        IrisUtil.captureTranslucentShaderPatchBridgePayload((IrisRenderingPipeline) (Object) this));
            }
        }
    }

    @Override
    public IrisShaderPatch voxy$getPatchData() {
        return this.patchData;
    }

    @Override
    public IrisVoxyRenderPipelineData voxy$getPipelineData() {
        return this.pipeline;
    }
}
