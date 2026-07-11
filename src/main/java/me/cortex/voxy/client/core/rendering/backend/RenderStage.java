package me.cortex.voxy.client.core.rendering.backend;

public enum RenderStage {
  FRAME_BEGIN,
  // Viewport setup point inside Iris beginLevelRendering, kept distinct from FRAME_BEGIN (HEAD)
  // so a backend can pick its setup timing relative to Iris' customUniforms.update(): gl46 sets
  // its frame up here, gl41metal consumes the earlier FRAME_BEGIN hook and ignores this stage.
  VIEWPORT_SETUP,
  SODIUM_SOLID_SYNC,
  SODIUM_CUTOUT_SYNC,
  PRE_TRANSLUCENT,
  // The host reached the start of translucent rendering (Iris beginTranslucents RETURN, before
  // near Sodium/vanilla translucent geometry draws). gl41metal composites its distant translucent
  // drawlist here so near-scene translucents blend over the already-drawn distant water.
  TRANSLUCENT,
  // The host reached its opaque LOD terrain draw point (Sodium translucent-pass hooks in the
  // non-Iris path, or the legacy direct render path). gl46 draws its opaque terrain here.
  OPAQUE,
  FRAME_END
}
