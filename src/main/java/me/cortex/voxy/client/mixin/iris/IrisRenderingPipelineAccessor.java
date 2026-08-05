package me.cortex.voxy.client.mixin.iris;

import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public interface IrisRenderingPipelineAccessor {
  @Accessor
  RenderTargets getRenderTargets();

  @Accessor
  ShaderStorageBufferHolder getShaderStorageBufferHolder();

  @Accessor
  ShaderPack getPack();

  @Accessor
  PackDirectives getPackDirectives();
}
