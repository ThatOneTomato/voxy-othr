package me.cortex.voxy.client.core.rendering.backend.gl41metal;

/**
 * One slot of the shared distant gbuffer. Metal renders distant terrain into 3 packed textures (see
 * {@code quad_raster.metal}'s {@code QuadFragmentOut} for the authoritative per-channel layout);
 * these three GL texture names are the OpenGL/Iris view of the same IOSurfaces. Three is a hard
 * limit: Apple GL4.1 caps a fragment program at 16 samplers and SIGSEGVs at exactly 16, and the
 * Iris bridge program also binds up to 12 shader-pack samplers, so the distant reconstruction must
 * fit in 3 (12 + 3 = 15).
 *
 * <ul>
 *   <li>{@code gbuffer0} RGBA32F: {@code .xy} atlas uv, {@code .zw} quad tile
 *   <li>{@code gbuffer1} RGBA32F: {@code .x} ndc depth, {@code .y} modelId, {@code .zw} customId
 *       (lo 24 bits / hi 8 bits)
 *   <li>{@code gbuffer2} RGBA32F: {@code .x} packed albedo rgb, {@code .y} packed lightMap xy,
 *       {@code .z} packed tint rgb, {@code .w} packed face/flags/coverage (coverage = low bit, 1 =
 *       fragment ran)
 * </ul>
 *
 * <p>The translucent distant gbuffer adds 3 more shared textures (see {@code
 * TranslucentFragmentOut}): {@code tgbuffer0}/{@code tgbuffer1} hold the front-most translucent
 * surface (for strict Iris water-program shading) and {@code tgbufferAccum} holds the back-to-front
 * over-blended flat colour + alpha of all translucent layers.
 *
 * <ul>
 *   <li>{@code tgbuffer0} RGBA32F: {@code .x} packed albedo rgb, {@code .y} packed lightMap xy,
 *       {@code .z} packed tint rgb, {@code .w} packed face/flags/coverage
 *   <li>{@code tgbuffer1} RGBA32F: {@code .x} ndc depth, {@code .y} alpha, {@code .zw} customId (lo
 *       24 bits / hi 8 bits)
 *   <li>{@code tgbufferAccum} RGBA32F: {@code .xyz} premultiplied (albedo*tint*alpha), {@code .w}
 *       accumulated alpha
 * </ul>
 */
record DistantGbufferSlot(
    int index,
    int textureTarget,
    int gbuffer0Texture,
    int gbuffer1Texture,
    int gbuffer2Texture,
    int tgbuffer0Texture,
    int tgbuffer1Texture,
    int tgbufferAccumTexture,
    int width,
    int height) {}
