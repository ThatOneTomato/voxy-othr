#version 410 core

uniform mat4 uMVP;
uniform ivec3 uSecOrigin;
// Vertical band edges, secOrigin-relative blocks, as FLOATS so the boundary sits at exactly
// cameraY +/- renderDistance with no integer quantization (an integer band left a ~1-block seam
// that drifted with the camera's fractional height). The cube spans these in Y.
uniform float uColumnMinY;
uniform float uColumnMaxY;
// Horizontal cylinder cull inputs (fraction-aware, voxy-fabric outline.vsh shouldRender):
// integer camera block XZ, its fractional remainder, and the cull radius in blocks. This makes
// the bound's HORIZONTAL extent track the camera CONTINUOUSLY (every frame, camera-relative),
// just like the float vertical band tracks it in Y. The section SET is discrete in XZ (updated
// only by Sodium's lagged add/remove events), so without this cull a column the camera has
// already receded past (beyond render distance, where Sodium no longer draws near) stays in the
// bound for the few frames until Sodium's removeSection arrives - clipping the distant water
// there = a hole at the receding frontier while flying BACKWARD.
uniform ivec2 uCameraBlockXZ;
uniform vec2 uCameraFracXZ;
uniform float uHorizontalRadius;

layout(location = 0) in ivec3 aSectionCoord;

// Nearest point of an interval [mn, mx] to 0 (0 if it straddles 0), voxy-fabric outline.vsh.
float nearestToZero(float mn, float mx) {
  if (mn > 0.0) {
    return mn;
  }
  if (mx < 0.0) {
    return mx;
  }
  return 0.0;
}

void main() {
  ivec3 cornerBit = ivec3(gl_VertexID & 1, (gl_VertexID >> 2) & 1, (gl_VertexID >> 1) & 1);
  // Skip a whole column when the nearest point of its EXACT 16-block XZ footprint is farther
  // than uHorizontalRadius from the REAL (fractional) camera (camera-relative cylinder cull, see
  // the uniform comment above). No outward expansion (fabric's +1 made the cull a no-op);
  // uHorizontalRadius carries the tunable inset.
  vec2 icornerXZ = vec2(aSectionCoord.xz * 16 - uCameraBlockXZ);
  float cullX = nearestToZero(icornerXZ.x, icornerXZ.x + 16.0) - uCameraFracXZ.x;
  float cullZ = nearestToZero(icornerXZ.y, icornerXZ.y + 16.0) - uCameraFracXZ.y;
  if (cullX * cullX + cullZ * cullZ >= uHorizontalRadius * uHorizontalRadius) {
    gl_Position = vec4(-100.0, -100.0, -100.0, 0.0);
    return;
  }
  // aSectionCoord is the 16-block section position; only its XZ matters here (ensureInstanceData
  // collapses each loaded column to ONE instance, so the Y is a placeholder). uSecOrigin is the
  // 32-block-aligned origin drawMvp projects relative to (see DistantRenderer
  // translateByNegativeCameraSubSection). The subtraction is exact in int and the result is a
  // small camera-relative value, so the float cast keeps full precision.
  ivec3 origin = aSectionCoord * 16 - uSecOrigin;
  int x = cornerBit.x * 16 + origin.x;
  int z = cornerBit.z * 16 + origin.z;
  // Stretch the cube over the camera-centred vertical band [cameraY-R, cameraY+R] the caller
  // computed, NOT the section's own 16 blocks. The loaded volume is then a band of columns that
  // ENCLOSES the camera vertically, so along any ray the far boundary is the cylinder wall at
  // the near render distance, NOT a deep far corner of the terrain. Without this, a high-flying
  // camera sits in the empty air ABOVE the terrain "puck": from a steep top-down angle the
  // puck's far-bottom corners read FARTHER (larger depth) than distant LOD water just beyond the
  // horizontal edge, so the farther-depth bound wrongly classifies that water as inside the
  // loaded volume and discards it (water vanishes when flying high). voxy-fabric outline.vsh
  // documents the same intent as its commented-out "Expand the y height to be big" TODO.
  float y = (cornerBit.y != 0) ? uColumnMaxY : uColumnMinY;
  gl_Position = uMVP * vec4(float(x), y, float(z), 1.0);
  // uMVP is the Voxy draw projection, which computeProjectionMat builds as a STANDARD
  // non-reverse mapping (m22/m32 overwritten), so depth is 0=near..1=far here regardless of the
  // host's reverse-Z. Nudge the boundary slightly toward the camera (smaller depth) so distant
  // terrain exactly at the boundary is kept rather than clipped by float error (this matches
  // voxy-fabric outline.vsh CLOSER_SIGN for its non-reverse bounding buffer).
  gl_Position.z -= 0.0005;
}
