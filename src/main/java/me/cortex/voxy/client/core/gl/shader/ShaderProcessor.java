package me.cortex.voxy.client.core.gl.shader;

public interface ShaderProcessor {
  String process(ShaderType type, String source);
}
