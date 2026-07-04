package me.cortex.voxy.client.core.rendering.backend;

public enum RenderStage {
    FRAME_BEGIN,
    // Legacy (gl46) viewport setup point inside Iris beginLevelRendering, kept distinct from
    // FRAME_BEGIN (HEAD) so the gl46 backend preserves the pre-abstraction setup timing relative
    // to Iris' customUniforms.update() while gl41metal can consume the earlier FRAME_BEGIN hook.
    LEGACY_VIEWPORT_SETUP,
    SODIUM_SOLID_SYNC,
    SODIUM_CUTOUT_SYNC,
    PRE_TRANSLUCENT,
    // The host reached the start of translucent rendering (Iris beginTranslucents RETURN, before
    // near Sodium/vanilla translucent geometry draws). gl41metal composites its distant translucent
    // gbuffer here so near-scene translucents blend over the already-composited distant water.
    TRANSLUCENT,
    LEGACY_OPAQUE,
    FRAME_END
}
