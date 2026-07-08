# Voxy Agent Guide

## Project Shape

This is a Minecraft 1.21.1 Voxy fork with shared core code and two loader
subprojects:

- `:fabric`
- `:neoforge`

Shared Java/resources live in `src/main`. Loader-specific code and resources
live in `src/fabric` and `src/neoforge`. The root Gradle project owns shared
Spotless formatting and the macOS `gl41metal` native/shader build tasks.

Active render backend ids are `GL46`, `GL41METAL`, and `DISABLED`.
User-facing `-Dvoxy.renderBackend` values are `auto`, `gl46`, `mdic`
(compatibility alias for `gl46`), and `gl41metal`.

Java compilation targets Java 21 in both loader subprojects. Mixin JSON files
currently keep `compatibilityLevel` at `JAVA_17`.

## Source Of Truth

Before changing renderer behavior, compare against the corresponding current
implementation or reference source:

- GL46 traversal and draw behavior: `backend/gl46`,
  `HierarchicalOcclusionTraverser`, `MDICSectionRenderer`, and GL46 shaders.
- Model/material behavior: `ModelTextureBakery`, `RenderDataFactory`, model
  payload formats, and bakery shaders.
- Iris integration: `me.cortex.voxy.client.iris.*`,
  `MixinIrisRenderingPipeline`, sampler/uniform mixins, and `.reference/Iris`.
- Sodium section lifecycle and render hooks: Sodium mixins in this repo and
  `.reference/sodium`.

Screenshots are useful for symptoms, not for specifying renderer behavior.
Document intentional divergences near the implementation.

## Backend Boundaries

`VoxyRenderSystem` is the facade. Keep backend resources inside the selected
`VoxyRenderBackend`; route render lifecycle through:

```text
runFrameStage(RenderStage stage, RenderStageContext context, RenderFrame frame)
```

`RenderStage` values describe host pipeline lifecycle points. Backends consume
the stages they need and ignore the rest.

GL46 implementation details belong under `backend/gl46`. `MDICSectionRenderer`
is a GL46 section renderer detail, not a separate backend. Metal device, shared
texture, IOSurface, native context, slot, and bridge details belong under
`backend/gl41metal`.

Sodium, Iris, loader, and compatibility mixins should call `VoxyRenderSystem`
facade methods instead of reaching into backend internals.

`ClientVoxyMixinPlugin` dynamically adds render-path mixins. In particular,
`sodium.MixinDefaultChunkRenderer` and `sodium.MixinSodiumWorldRendererVS` can be
active even though they are not listed as normal static entries in
`client.voxy.mixins.json`.

## GL41Metal

`gl41metal` renders distant Voxy terrain in Metal into shared distant gbuffer
textures. OpenGL/Iris samples those textures and composites near and distant
terrain.

The CPU side may collect frame state, submit Metal work, bind ready shared GL
textures, track slots, and manage resize/lifetime. Metal owns distant terrain
residency updates, LOD selection, culling, rasterization, and gbuffer writes.
OpenGL/Iris owns the near scene, shader-pack passes, bridge/composite, and final
presentation.

Slot readiness is explicit:

```text
Free -> MetalSubmitted -> MetalReady -> GlSampling -> Retiring -> Free
```

Sampling uses the current frame's slot. If it is not ready, use the configured
wait behavior or skip that frame's distant output.

For Iris 1.21.1:

- `gl41metal` submits Metal work through `RenderStage.FRAME_BEGIN` at
  `IrisRenderingPipeline.beginLevelRendering()` `HEAD`, before Iris updates
  shader-pack custom uniforms.
- GL46 uses the later `RenderStage.VIEWPORT_SETUP` point.
- The strict shader-pack bridge runs at `IrisRenderingPipeline.beginHand()`
  `RETURN`, after Iris copies current opaque depth into `depthtex2` / `noHand`.
- Distant translucent composition runs at `IrisRenderingPipeline.beginTranslucents()`
  `RETURN`, before near Sodium translucent geometry draws.
- Strict bridge output is skipped when the shader-pack bridge payload is
  unavailable.

## Loaded-Volume Bound

The `gl41metal` loaded-volume bound handles near/far water overlap. It is a
GL-only far-boundary depth texture for the Sodium-loaded 16-block near volume in
Voxy NDC.

Key invariants:

- The bound is driven by Sodium render-section lifecycle signals.
- Those signals are near-scene signals; Metal traversal and residency stay
  driven by Voxy `WorldEngine` dirty events and the Metal request queue.
- The bound clips translucent distant water. Opaque distant terrain remains
  pixel-occluded by depth/stencil/hardware depth so valid gap filling still
  works.
- Removal is immediate. Adds are delayed by `voxy.gl41metal.boundAddDelayFrames`
  so the bound lines up with Sodium's first actual draw.
- The camera-relative horizontal cull and camera-centered vertical band are part
  of the correctness model for flying/high-speed camera motion.

## Intentional Divergence

`voxy.fluidBridgeWalls=true` is the current accepted upstream divergence.
`RenderDataFactory` may re-add horizontal fluid-fluid boundary faces when
adjacent flowing-water columns bake to different fluid heights. This is
background meshing work, not per-frame rendering. Use
`-Dvoxy.fluidBridgeWalls=false` for fabric-exact comparison.

## Formatting And Testing

Java formatting:

```text
./gradlew spotlessApply
./gradlew spotlessCheck
```

Native `.mm`, `.h`, and `.metal` files follow `.clang-format`
(`BasedOnStyle: Google`).

Baseline checks:

```text
git diff --check
./gradlew build
```

Targeted builds:

```text
./gradlew :fabric:build
./gradlew :neoforge:build
```

Renderer validation requires client runs on suitable hardware, not just a jar
build:

```text
./gradlew -Dvoxy.renderBackend=gl41metal :fabric:runClient
./gradlew -Dvoxy.renderBackend=gl41metal :neoforge:runClient
./gradlew -Dvoxy.renderBackend=gl46 :fabric:runClient
./gradlew -Dvoxy.renderBackend=gl46 :neoforge:runClient
```

Keep commits focused and leave generated run outputs, logs, crash reports,
screenshots, `.DS_Store`, IDE files, and `.reference/` out of commits unless
explicitly requested.
