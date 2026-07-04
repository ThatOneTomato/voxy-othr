package me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Raw loader for the bridge GLSL sources under assets/voxy/shaders/lod/gl41metal/bridge/. The
 * bridge assembles its programs from these snippets at runtime (see DistantTerrainBridge's
 * buildFragmentShader), so they are read verbatim - no #version injection or #import resolution
 * like {@code ShaderLoader} performs.
 */
final class BridgeGlsl {
  private BridgeGlsl() {}

  static String load(String name) {
    String path = "/assets/voxy/shaders/lod/gl41metal/bridge/" + name;
    try (InputStream in = BridgeGlsl.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new RuntimeException("Bridge shader source not found: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read bridge shader source " + path, e);
    }
  }
}
