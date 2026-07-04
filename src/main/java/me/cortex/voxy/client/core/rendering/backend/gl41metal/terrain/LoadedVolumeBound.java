package me.cortex.voxy.client.core.rendering.backend.gl41metal.terrain;

/**
 * Per-frame result of {@link DistantChunkBoundRenderer}: a depth texture holding, per target pixel,
 * the FAR boundary of the Sodium-loaded near-scene volume (vanilla NDC, produced by rasterizing the
 * loaded sections' AABBs with the farther-depth compare). The distant bridge clips any distant
 * fragment that lies NEARER than this boundary (i.e. inside the loaded volume), which is what stops
 * distant LOD water from drawing over the near Sodium water in the transition band - the P1
 * near/far overlap. Mirrors voxy-fabric's {@code ChunkBoundRenderer.getDepthBoundTexture()} +
 * {@code quads.frag}'s {@code DEPTH_SCALAR_COMPARE(gl_FragCoord.z, depthTex)} discard.
 *
 * <p>{@code enabled} is false when no sections are loaded (or the bound could not be produced); the
 * texture is still a valid cleared depth attachment, but consumers skip the clip entirely so they
 * never sample a stale boundary.
 */
public record LoadedVolumeBound(int texture, int width, int height, boolean enabled) {
  public static final LoadedVolumeBound DISABLED = new LoadedVolumeBound(0, 0, 0, false);
}
