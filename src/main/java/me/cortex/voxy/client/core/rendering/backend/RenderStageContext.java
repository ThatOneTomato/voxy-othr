package me.cortex.voxy.client.core.rendering.backend;

public record RenderStageContext(
    RenderFrameContext frameContext, boolean irisActive, boolean shaderPackActive, Object payload) {
  public RenderStageContext(
      RenderFrameContext frameContext, boolean irisActive, boolean shaderPackActive) {
    this(frameContext, irisActive, shaderPackActive, null);
  }
}
