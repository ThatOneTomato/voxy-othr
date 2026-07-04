#version 410 core

// Distant gbuffer: Metal packs everything into 3 shared RGBA32F textures (see
// quad_raster.metal QuadFragmentOut for the authoritative bit layout). Keeping the
// reconstruction at 3 samplers is what lets the Iris colour program also bind the shader
// pack's samplers (up to 12) within Apple GL4.1's usable 15 fragment-texture-unit budget
// (the driver SIGSEGVs at exactly 16). Each channel below is unpacked in sampleVoxyGbuffer;
// the bit layout MUST stay in lockstep with the Metal QuadFragmentOut packing.
uniform sampler2DRect uGbuffer0Tex;  // .xy atlas uv, .zw quad tile
uniform sampler2DRect uGbuffer1Tex;  // .x depth, .y modelId, .z customId&0xFFFFFF, .w customId>>24
uniform sampler2DRect uGbuffer2Tex;  // .x albedo, .y lightMap, .z tint, .w face/flags/coverage (all packed)
uniform vec2 uSharedSize;
uniform vec2 uTargetSize;
uniform mat4 uInvVoxyMvp;
uniform mat4 uVanillaMvp;

// Quad flags decoded in main(); patches may read it for the directional shade bit.
uint voxyQuadFlags = 0u;

struct VoxyFragmentParameters {
  vec4 sampledColour;
  vec2 tile;
  vec2 uv;
  // GL46 quads.frag declares face as uint, but Apple's strict GLSL 410 compiler rejects the
  // int/uint mixes shader packs write against it (e.g. Complementary's `parameters.face & 1`),
  // which GL46's lenient implicit conversions accept. The value is the 0-5 face index, so the
  // bit ops are identical; declare it int for GL41 shader-pack compatibility.
  int face;
  uint modelId;
  vec2 lightMap;
  vec4 tinting;
  uint customId;
};

// Recovers an exact non-negative integer that Metal stored as a float *value* in an RGBA32F
// channel. NEAREST sampling returns the texel bit-exact, so truncation recovers the integer.
// Do NOT add 0.5 here: the tint field reaches 2^24-1 and +0.5 would round past the f32
// exact-integer range. See quad_raster.metal's losslessness rules.
uint decodePackedUint(float value) {
  return uint(max(value, 0.0));
}

// One decoded gbuffer texel. coverage and depth gate the discard; the rest feeds
// VoxyFragmentParameters. tint/colour alpha is opaque (1.0) here (see quad_raster.metal).
struct VoxyGbufferTexel {
  float coverage;
  vec4 colour;
  vec2 uv;
  vec2 tile;
  float depth;
  vec2 lightMap;
  vec4 tint;
  uint modelId;
  uint customId;
  uint face;
  uint flags;
  float ao;
};

// Unpacks one RGB triple stored as (r<<16)|(g<<8)|b into a normalised vec3 (each 0..255/255).
vec3 unpackRgb8(uint packed) {
  return vec3(float((packed >> 16u) & 255u), float((packed >> 8u) & 255u), float(packed & 255u))
      / 255.0;
}

VoxyGbufferTexel sampleVoxyGbuffer(vec2 sharedPixel) {
  vec4 g0 = texture(uGbuffer0Tex, sharedPixel);
  vec4 g1 = texture(uGbuffer1Tex, sharedPixel);
  vec4 g2 = texture(uGbuffer2Tex, sharedPixel);
  VoxyGbufferTexel t;
  // gbuffer0: atlas uv + quad tile (both plain floats, no packing).
  t.uv = g0.xy;
  t.tile = g0.zw;
  // gbuffer1: depth + ids. customId is split lo 24 bits / hi 8 bits to keep each < 2^24.
  t.depth = clamp(g1.x, 0.0, 1.0);
  t.modelId = decodePackedUint(g1.y);
  t.customId = decodePackedUint(g1.z) | (decodePackedUint(g1.w) << 24u);
  // gbuffer2: four packed integer channels (see quad_raster.metal). albedo/tint are 8-bit
  // rgb; lightMap is two 12-bit coords; the last channel folds ao/face/flags/coverage.
  t.colour = vec4(unpackRgb8(decodePackedUint(g2.x)), 1.0);
  uint lightPacked = decodePackedUint(g2.y);
  t.lightMap = vec2(float((lightPacked >> 12u) & 4095u), float(lightPacked & 4095u)) / 4095.0;
  t.tint = vec4(unpackRgb8(decodePackedUint(g2.z)), 1.0);
  uint faceFlagsCoverage = decodePackedUint(g2.w);
  t.coverage = float(faceFlagsCoverage & 1u);
  t.flags = (faceFlagsCoverage >> 1u) & 255u;
  t.face = (faceFlagsCoverage >> 9u) & 7u;
  // Bits 12-19: SSAO factor from the Metal ssao.metal pass. 0 = no AO data (pass skipped
  // or pre-SSAO pixel) -> neutral 1.0. Folded straight into the albedo: near terrain
  // carries vanilla's per-vertex AO in its vertex colour, which flows into shader-pack
  // gbuffers the same way, so both the built-in VANILLA_PATCH and a pack's patched
  // voxy_emitFragment see AO-darkened sampledColour without any patch-side changes
  // (GL46's compute pass equivalently multiplies the lit colour post-opaque).
  uint aoPacked = (faceFlagsCoverage >> 12u) & 255u;
  t.ao = aoPacked == 0u ? 1.0 : float(aoPacked) / 255.0;
  t.colour.rgb *= t.ao;
  return t;
}

vec3 rev3d(vec3 clip) {
  vec4 view = uInvVoxyMvp * vec4(clip * 2.0 - 1.0, 1.0);
  return view.xyz / view.w;
}

float projectDepth(vec3 pos) {
  vec4 view = uVanillaMvp * vec4(pos, 1.0);
  float depth = view.z / view.w;
  depth = min(1.0 - 2.0 / 16777215.0, depth);
  depth = depth * 0.5 + 0.5;
  depth = gl_DepthRange.diff * depth + gl_DepthRange.near;
  return clamp(depth, 0.0, 1.0);
}

vec2 sharedPixelForTarget(vec2 targetPixel) {
  vec2 shared = targetPixel * (uSharedSize / max(uTargetSize, vec2(1.0)));
  return vec2(shared.x, uSharedSize.y - shared.y);
}

// gl_FragCoord.z override for the full-screen-quad bridge.
//
// GL46 voxy renders the distant LOD as real per-triangle geometry, so a shader pack's
// voxy_emitFragment naturally sees gl_FragCoord.z = the per-pixel rasterizer depth of the
// distant triangle, expressed in the active program's projection (vxProj). The gl41metal
// bridge composites the Metal-produced distant gbuffer through a single full-screen quad,
// whose rasterizer-interpolated z is constant across every pixel (the quad's vertex depth),
// so any pack doing the standard
//     vec3 screenPos = vec3(gl_FragCoord.xy / vec2(viewWidth, viewHeight), gl_FragCoord.z);
//     vec3 viewPos = ScreenToView(screenPos);
// would collapse the whole distant scene to one view-space point. The fix is to redirect
// every pack-side gl_FragCoord read to a per-pixel override (g.depth as written by Metal,
// packed into voxy_OverrideFragCoord by main() before calling voxy_emitFragment): the
// patched pack rebinds gbufferProjection/Inverse to vxProj/vxProjInv (Voxy's extended
// projection), so its ScreenToView inverts in *Voxy* NDC, not vanilla NDC.
//
// We do the substitution in Java text (see buildFragmentShader's gl_FragCoord rewrite),
// not via #define, because Apple's GL4.1 driver preprocessor silently ignores attempts to
// redefine the built-in gl_FragCoord identifier - the GLSL spec only forbids redefining
// pre-defined macros, but Apple's compiler treats the built-in variable name the same way,
// leaving the pack reading the full-screen quad's constant rasterizer z. Renaming the
// identifier at the patch-source level bypasses the issue entirely while keeping the
// bridge's own main() free to read the real built-in gl_FragCoord.
//
// Note: gl_FragDepth is written by main() AFTER voxy_emitFragment. The vanilla path keeps the
// vanilla-remapped outputDepth (GL depth buffer stays vanilla NDC for the MC hardware depth
// test); the strict Iris path instead writes Voxy NDC (g.depth) into its private depth-stencil
// so the shader pack samples Voxy-NDC depth as vxDepthTex* (see GLSL_COLOR_MAIN_NO_MASK). Only
// the pack-visible gl_FragCoord is in Voxy NDC in both shapes.
vec4 voxy_OverrideFragCoord;
