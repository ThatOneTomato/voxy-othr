package me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge;

import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_COLOR_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_FUNC;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_GREATER;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_NONE;
import static org.lwjgl.opengl.GL11C.GL_SCISSOR_BOX;
import static org.lwjgl.opengl.GL11C.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glClear;
import static org.lwjgl.opengl.GL11C.glClearDepth;
import static org.lwjgl.opengl.GL11C.glColorMask;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glDepthFunc;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL11C.glGetBooleanv;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glGetIntegerv;
import static org.lwjgl.opengl.GL11C.glIsEnabled;
import static org.lwjgl.opengl.GL11C.glScissor;
import static org.lwjgl.opengl.GL11C.glTexImage2D;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL11C.glViewport;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glBufferData;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20C.glGetUniformLocation;
import static org.lwjgl.opengl.GL20C.glUniform1f;
import static org.lwjgl.opengl.GL20C.glUniform2f;
import static org.lwjgl.opengl.GL20C.glUniform2i;
import static org.lwjgl.opengl.GL20C.glUniform3i;
import static org.lwjgl.opengl.GL20C.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_COMPONENT32F;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30C.glGenFramebuffers;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL30C.glVertexAttribIPointer;
import static org.lwjgl.opengl.GL31C.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL33C.glVertexAttribDivisor;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.backend.gl41metal.terrain.LoadedVolumeBound;
import me.cortex.voxy.common.Logger;
import net.minecraft.core.SectionPos;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * GL-only loaded-volume bound renderer for the gl41metal distant bridge (P1).
 *
 * <p>It tracks the set of Sodium-loaded 16-block render sections (fed from the same {@code
 * onSectionRenderStateChanged}/{@code onChunkTrackerReset} 16-block near-scene signals that Sodium
 * already emits) and, each frame, rasterizes a unit cube per loaded section into a private depth
 * texture using the farther-depth compare. The result is the per-pixel FAR boundary of the
 * near-scene loaded volume in Voxy NDC (the same projection Metal rasterizes distant terrain with)
 * - see {@link LoadedVolumeBound}.
 *
 * <p>Each loaded column is rasterized as ONE box (not the section's own 16 blocks) spanning the
 * camera-centred vertical band {@code cameraY +/- renderDistance} via {@code uColumnMinY}/{@code
 * uColumnMaxY} (clamped to the world floor/ceiling), and unique XZ columns are de-duplicated (see
 * {@code ensureInstanceData}). This makes the loaded volume a vertical cylinder that ENCLOSES the
 * camera horizontally and matches Sodium's reach vertically. If instead only the terrain's own
 * 16-block cubes were rasterized, a high-flying camera would sit in the empty air ABOVE the terrain
 * "puck": from a steep top-down angle the puck's deep far-bottom corners read FARTHER than distant
 * LOD water just past the horizontal edge, and the farther-depth bound would wrongly clip that
 * water (it vanished when flying high). The camera-centred band fixes that horizontally while NOT
 * clipping the water directly BELOW a high camera: Sodium's visibility BFS only flood-fills
 * ~renderDistance chunks straight down, so the ground far below a high camera is rendered by
 * neither the near scene nor (if the bound claimed full height) the distant LOD - a "neither near
 * nor far" hole. Bounding each column to the band Sodium can actually reach mirrors voxy-fabric
 * ChunkBoundRenderer (whose {@code findEmitBoundingChunks} expands mincy/maxcy only while within
 * {@code renderDistance} of the camera Y; outline.vsh documents the same intent as its
 * commented-out "Expand the y height" TODO).
 *
 * <p>The vertex shader applies voxy-fabric outline.vsh's {@code shouldRender()} horizontal cylinder
 * cull: a column is skipped when its XZ footprint is farther than {@code renderDistance} from the
 * REAL (fractional) camera. This makes the bound's horizontal extent track the camera CONTINUOUSLY
 * each frame, exactly as the float vertical band does in Y. It is required because the section SET
 * is discrete in XZ (updated only by Sodium's lagged add/remove events): without it, a column the
 * camera has already receded past stays in the bound for the few frames until Sodium's
 * removeSection arrives, clipping distant water at the receding frontier = a hole while flying
 * BACKWARD. There is NO {@code lodBoundaryBuffer} inward shrink, though: an inward shrink would
 * leave a boundary band where BOTH near and distant water draw (a double-water overlap).
 *
 * <p>Add/remove timing mirrors voxy-fabric ChunkBoundRenderer's add/remove queues, NOT a "200ms
 * removal delay". Fabric processes its {@code remQueue} BEFORE the bound render (removal is
 * immediate) but its {@code addQueue} AFTER (a section is not bounded until the frame AFTER it is
 * reported). This renderer does the same: removal is immediate, but a freshly built section waits
 * {@code ADD_DELAY_FRAMES} (default 1) before joining the bound. Sodium reports a section built the
 * instant its geometry uploads, yet only DRAWS it a frame later, so an immediate add would clip the
 * distant water a frame before the near water is painted - a moving hole band at the FORWARD edge.
 * Removal must stay immediate because gl41metal distant LOD is persistently resident (independent
 * of the Sodium near distance), so the bound must stop clipping the instant near water unloads to
 * reveal the already-resident distant water; a removal delay would keep clipping a trailing gap
 * band while moving.
 *
 * <p>This is the gl41metal port of voxy-fabric's {@code ChunkBoundRenderer}. The two forced
 * divergences from fabric (documented in AGENTS.md):
 *
 * <ul>
 *   <li>Apple GL4.1 has no SSBO/compute, so the per-section positions are an INSTANCED integer
 *       vertex attribute ({@code aSectionCoord}) rather than fabric's SSBO; the unit cube corners
 *       are derived from {@code gl_VertexID} exactly like fabric's outline.vsh.
 *   <li>This renderer ONLY produces the shared bound texture. How the bound is CONSUMED rides the
 *       single existing occlusion divergence in {@link DistantTerrainBridge} (vanilla samples it
 *       in-shader; the strict Iris path feeds it into the stencil-mask pass), so the bound clip
 *       never adds a sampler to the budgeted Iris colour program.
 * </ul>
 *
 * <p>Per AGENTS.md these 16-block Sodium signals must NOT touch Metal traversal/residency; this
 * renderer is purely a GL near-scene volume and changes no Metal state.
 */
public final class DistantChunkBoundRenderer implements AutoCloseable {
  // Unit cube corners are derived from gl_VertexID like voxy-fabric outline.vsh:
  //   corner = ivec3(id & 1, (id >> 2) & 1, (id >> 1) & 1) * 16
  // The 36 indices below (12 triangles) reference corners 0..7 of that mapping. Winding is
  // irrelevant: culling is disabled and the farther-depth compare keeps the farthest surface per
  // pixel (= the loaded volume's far boundary) regardless of which faces draw.
  private static final byte[] CUBE_INDICES = {
    0, 1, 3, 0, 3, 2, // bottom (y=0)
    4, 5, 7, 4, 7, 6, // top (y=1)
    0, 1, 5, 0, 5, 4, // z=0
    2, 3, 7, 2, 7, 6, // z=1
    0, 2, 6, 0, 6, 4, // x=0
    1, 3, 7, 1, 7, 5 // x=1
  };

  private static final String VERTEX_SOURCE = BridgeGlsl.load("chunk_bound.vert");

  private static final String FRAGMENT_SOURCE = BridgeGlsl.load("chunk_bound.frag");

  private static final int INITIAL_CAPACITY = 1 << 12;

  // Fine-tune (in blocks) where the vertical near/distant handoff sits relative to Sodium's exact
  // downward BFS reach. The float band already removes the integer +/-1 seam; this absorbs the
  // small
  // systematic offset that Sodium reaches slightly MORE than renderDistance vertically. The default
  // +1 reproduces voxy-fabric ChunkBoundRenderer.testYPos, which expands each section's Y span by 1
  // block on each side (nearestToZero(ry - 1, ry + 17)) - i.e. an effective renderDistance + 1
  // reach.
  // Positive = grow the band (more distant discarded, less overlap, risks a hole); negative =
  // shrink
  // it (more overlap, no hole). 0 = exact cameraY +/- renderDistance.
  private static final double BOUND_VERTICAL_MARGIN =
      Double.parseDouble(System.getProperty("voxy.gl41metal.boundVerticalMargin", "1"));

  // Fine-tune (in blocks) the camera-relative horizontal cylinder cull radius (see the shader's
  // uCameraBlockXZ comment). Positive = grow the cull radius (bound covers a wider ring); negative
  // =
  // shrink it (more distant kept in the outer ring). Default 0 = cull at exactly renderDistance.
  private static final double BOUND_HORIZONTAL_MARGIN =
      Double.parseDouble(System.getProperty("voxy.gl41metal.boundHorizontalMargin", "0"));

  // Frames to DELAY a section ENTERING the bound after Sodium reports it built. The Sodium hook
  // (MixinRenderSectionManager#voxy$updateOnUpload) fires the instant a section's geometry is
  // uploaded (setInfo), but Sodium does not actually DRAW that section until a later frame, when
  // its
  // visibility graph next includes it. If the bound covered the section immediately, then while
  // flying FORWARD the leading ring of freshly-built columns would claim "near already covers this"
  // and clip the distant water there a frame BEFORE Sodium paints the near water - a moving hole
  // band at the front edge (and harmless overlap, not a hole, when flying backward, since there is
  // no fresh leading ring behind). Deferring the add by one frame lines the bound up with Sodium's
  // actual first draw of the section. Removal stays IMMEDIATE (the safe side: uncovering early only
  // shows distant under near for a frame = overlap, never a hole). 0 = old immediate-add behaviour.
  private static final int ADD_DELAY_FRAMES =
      Integer.parseInt(System.getProperty("voxy.gl41metal.boundAddDelayFrames", "1"));

  // Loaded 16-block section positions (SectionPos longs). This set tracks Sodium's near render
  // sections (immediate remove on absent; add deferred by ADD_DELAY_FRAMES, see above) - there is
  // no voxy-fabric-style delayed REMOVAL here, on purpose. Fabric delays bound removal to mask its
  // on-demand GL46 LOD build window; gl41metal distant LOD is PERSISTENTLY resident (keyed by Voxy
  // 32-block WorldSection ids over the whole Voxy render distance, independent of the Sodium near
  // distance - see TerrainResources.onSectionRenderStateChanged). With distant already
  // resident, any removal delay would only keep the bound CLIPPING distant for ~200ms after Sodium
  // stopped drawing near water, opening a persistent gap band trailing the boundary while moving.
  // Mutated only on the render thread from the Sodium hooks; guarded for the rare off-thread
  // report.
  private final LongOpenHashSet sections = new LongOpenHashSet(INITIAL_CAPACITY);
  // Sections waiting out their ADD_DELAY_FRAMES countdown before joining {@link #sections}. Keyed
  // by
  // SectionPos long -> frames remaining; ticked down once per rendered frame in tickPendingAdds().
  private final Long2IntOpenHashMap pendingAdds = new Long2IntOpenHashMap();
  private boolean sectionsDirty = true;

  private int program;
  private int mvpUniform = -1;
  private int secOriginUniform = -1;
  private int columnMinYUniform = -1;
  private int columnMaxYUniform = -1;
  private int cameraBlockXZUniform = -1;
  private int cameraFracXZUniform = -1;
  private int horizontalRadiusUniform = -1;
  private boolean programFailed;
  private Shader shader;

  private int vao;
  private int indexBuffer;
  private int instanceBuffer;
  private int instanceCapacity;
  private int instanceCount;

  private int depthTexture;
  private int framebuffer;
  private int width;
  private int height;

  private boolean initialised;

  public void addSection(long sectionPos) {
    synchronized (this.sections) {
      if (this.sections.contains(sectionPos)) {
        return;
      }
      if (ADD_DELAY_FRAMES <= 0) {
        if (this.sections.add(sectionPos)) {
          this.sectionsDirty = true;
        }
        return;
      }
      // First add only: do not restart the countdown if it is already pending.
      this.pendingAdds.putIfAbsent(sectionPos, ADD_DELAY_FRAMES);
    }
  }

  public void removeSection(long sectionPos) {
    synchronized (this.sections) {
      this.pendingAdds.remove(sectionPos);
      if (this.sections.remove(sectionPos)) {
        this.sectionsDirty = true;
      }
    }
  }

  public void reset() {
    synchronized (this.sections) {
      this.pendingAdds.clear();
      if (!this.sections.isEmpty()) {
        this.sections.clear();
        this.sectionsDirty = true;
      }
    }
  }

  // Advances the deferred-add countdowns by one rendered frame, promoting any section that has
  // waited
  // out ADD_DELAY_FRAMES into the live bound set. Called once per frame from render() before the
  // instance data is (re)built, so a promotion this frame is picked up this frame.
  private void tickPendingAdds() {
    synchronized (this.sections) {
      if (this.pendingAdds.isEmpty()) {
        return;
      }
      var it = this.pendingAdds.long2IntEntrySet().fastIterator();
      while (it.hasNext()) {
        var entry = it.next();
        int remaining = entry.getIntValue();
        if (remaining <= 0) {
          if (this.sections.add(entry.getLongKey())) {
            this.sectionsDirty = true;
          }
          it.remove();
        } else {
          entry.setValue(remaining - 1);
        }
      }
    }
  }

  /**
   * Rasterizes the loaded-volume far boundary into the private depth texture for this frame and
   * returns it. {@code voxyMvp} is the Voxy draw MVP ({@code drawMvp}) - the SAME projection Metal
   * rasterizes distant terrain with - so the bound depth is directly comparable to the distant
   * {@code g.depth} with no reprojection (mirrors voxy-fabric, which compares the LOD's own
   * gl_FragCoord.z against the bound). That projection is always non-reverse (0 near .. 1 far), so
   * the bound keeps the FARTHEST surface (GL_GREATER, cleared to 0) regardless of host reverse-Z.
   *
   * <p>Saves/restores the GL draw framebuffer, viewport and depth/cull/blend state it touches so
   * the caller's subsequent {@code StateSnapshot.capture()} still sees the host's depth func.
   */
  public LoadedVolumeBound render(
      Matrix4fc voxyMvp,
      double cameraX,
      double cameraY,
      double cameraZ,
      int worldMinY,
      int worldMaxY,
      int verticalRadiusBlocks,
      int w,
      int h) {
    if (w <= 0 || h <= 0) {
      return LoadedVolumeBound.DISABLED;
    }
    if (!this.ensureProgram()) {
      return LoadedVolumeBound.DISABLED;
    }
    this.ensureTarget(w, h);
    this.tickPendingAdds();
    this.ensureInstanceData();

    int prevDrawFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
    int prevDepthFunc = glGetInteger(GL_DEPTH_FUNC);
    boolean prevDepthTest = glIsEnabled(GL_DEPTH_TEST);
    boolean prevCull = glIsEnabled(GL_CULL_FACE);
    boolean prevBlend = glIsEnabled(GL_BLEND);
    boolean prevScissor = glIsEnabled(GL_SCISSOR_TEST);
    boolean prevStencil = glIsEnabled(GL_STENCIL_TEST);
    int prevProgram = glGetInteger(GL_CURRENT_PROGRAM);
    int prevVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
    int[] prevViewport = new int[4];
    int[] prevScissorBox = new int[4];
    glGetIntegerv(GL_VIEWPORT, prevViewport);
    glGetIntegerv(GL_SCISSOR_BOX, prevScissorBox);
    boolean prevDepthMask;
    boolean prevColorMaskR, prevColorMaskG, prevColorMaskB, prevColorMaskA;
    try (MemoryStack stateStack = MemoryStack.stackPush()) {
      var dm = stateStack.malloc(1);
      glGetBooleanv(GL_DEPTH_WRITEMASK, dm);
      prevDepthMask = dm.get(0) != 0;
      var cm = stateStack.malloc(4);
      glGetBooleanv(GL_COLOR_WRITEMASK, cm);
      prevColorMaskR = cm.get(0) != 0;
      prevColorMaskG = cm.get(1) != 0;
      prevColorMaskB = cm.get(2) != 0;
      prevColorMaskA = cm.get(3) != 0;
    }

    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.framebuffer);
    glViewport(0, 0, this.width, this.height);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_STENCIL_TEST);
    glColorMask(false, false, false, false);
    glDepthMask(true);
    // Clear to the NEAR value (0) so that, where no chunks are loaded, nothing is "nearer than the
    // boundary" and distant terrain is fully kept (fabric inverseClearDepth() for non-reverse).
    glClearDepth(0.0);
    glClear(GL_DEPTH_BUFFER_BIT);

    boolean enabled = false;
    if (this.instanceCount > 0) {
      int bx = (int) Math.floor(cameraX);
      int by = (int) Math.floor(cameraY);
      int bz = (int) Math.floor(cameraZ);
      int secOriginX = (bx >> 5) << 5;
      int secOriginY = (by >> 5) << 5;
      int secOriginZ = (bz >> 5) << 5;

      // Bound each column to the camera-centred vertical band [cameraY - R, cameraY + R] (R =
      // verticalRadiusBlocks = renderDistance in blocks), as FLOATS so the boundary sits at the
      // exact
      // fractional camera height with no integer quantization. Sodium's visibility BFS only reaches
      // ~renderDistance chunks straight down through open air, so a full-height column under a high
      // camera would claim the near scene covers ground Sodium never renders -> a "neither near nor
      // far" hole directly below. An integer floor(cameraY) +/- R band (fraction dropped) left a
      // ~1-block seam that drifted with the camera's fractional height; the float band removes that
      // quantization, and BOUND_VERTICAL_MARGIN fine-tunes the handoff against Sodium's exact
      // vertical
      // reach (tune live with -Dvoxy.gl41metal.boundVerticalMargin). When the whole band falls
      // outside
      // the world (camera far above build height) it is degenerate and we skip it, so the bound is
      // empty there and the distant water below is fully kept.
      double radius = verticalRadiusBlocks + BOUND_VERTICAL_MARGIN;
      double bandMinY = Math.max(worldMinY, cameraY - radius);
      double bandMaxY = Math.min(worldMaxY, cameraY + radius);

      if (bandMaxY > bandMinY) {
        glEnable(GL_DEPTH_TEST);
        // Keep the FARTHEST surface per pixel = the far boundary of the loaded volume. Voxy NDC is
        // non-reverse (1 = far), so farthest = largest = GL_GREATER (fabric furtherDepthCompare()).
        glDepthFunc(GL_GREATER);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);

        this.shader.bind();
        try (MemoryStack stack = MemoryStack.stackPush()) {
          FloatBuffer mvp = stack.mallocFloat(16);
          voxyMvp.get(mvp);
          glUniformMatrix4fv(this.mvpUniform, false, mvp);
        }
        glUniform3i(this.secOriginUniform, secOriginX, secOriginY, secOriginZ);
        glUniform1f(this.columnMinYUniform, (float) (bandMinY - secOriginY));
        glUniform1f(this.columnMaxYUniform, (float) (bandMaxY - secOriginY));
        glUniform2i(this.cameraBlockXZUniform, bx, bz);
        glUniform2f(this.cameraFracXZUniform, (float) (cameraX - bx), (float) (cameraZ - bz));
        glUniform1f(
            this.horizontalRadiusUniform, (float) (verticalRadiusBlocks + BOUND_HORIZONTAL_MARGIN));

        glBindVertexArray(this.vao);
        glDrawElementsInstanced(
            GL_TRIANGLES, CUBE_INDICES.length, GL_UNSIGNED_BYTE, 0L, this.instanceCount);
        glBindVertexArray(0);
        enabled = true;
      }
    }

    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, prevDrawFbo);
    glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
    glDepthFunc(prevDepthFunc);
    setEnabled(GL_DEPTH_TEST, prevDepthTest);
    setEnabled(GL_CULL_FACE, prevCull);
    setEnabled(GL_BLEND, prevBlend);
    setEnabled(GL_SCISSOR_TEST, prevScissor);
    setEnabled(GL_STENCIL_TEST, prevStencil);
    glScissor(prevScissorBox[0], prevScissorBox[1], prevScissorBox[2], prevScissorBox[3]);
    glDepthMask(prevDepthMask);
    glColorMask(prevColorMaskR, prevColorMaskG, prevColorMaskB, prevColorMaskA);
    glClearDepth(1.0);
    glUseProgram(prevProgram);
    glBindVertexArray(prevVao);

    return new LoadedVolumeBound(this.depthTexture, this.width, this.height, enabled);
  }

  private static long packColumn(int sectionX, int sectionZ) {
    return ((long) sectionX << 32) | (sectionZ & 0xFFFFFFFFL);
  }

  private static int unpackColumnX(long column) {
    return (int) (column >> 32);
  }

  private static int unpackColumnZ(long column) {
    return (int) column;
  }

  private static void setEnabled(int cap, boolean enabled) {
    if (enabled) {
      glEnable(cap);
    } else {
      glDisable(cap);
    }
  }

  private boolean ensureProgram() {
    if (this.program != 0) {
      return true;
    }
    if (this.programFailed) {
      return false;
    }
    try {
      this.shader =
          Shader.make()
              .addSource(ShaderType.VERTEX, VERTEX_SOURCE)
              .addSource(ShaderType.FRAGMENT, FRAGMENT_SOURCE)
              .compile()
              .name("GL41Metal chunk bound");
      this.program = this.shader.id();
      this.mvpUniform = glGetUniformLocation(this.program, "uMVP");
      this.secOriginUniform = glGetUniformLocation(this.program, "uSecOrigin");
      this.columnMinYUniform = glGetUniformLocation(this.program, "uColumnMinY");
      this.columnMaxYUniform = glGetUniformLocation(this.program, "uColumnMaxY");
      this.cameraBlockXZUniform = glGetUniformLocation(this.program, "uCameraBlockXZ");
      this.cameraFracXZUniform = glGetUniformLocation(this.program, "uCameraFracXZ");
      this.horizontalRadiusUniform = glGetUniformLocation(this.program, "uHorizontalRadius");
      if (this.mvpUniform < 0
          || this.secOriginUniform < 0
          || this.columnMinYUniform < 0
          || this.columnMaxYUniform < 0
          || this.cameraBlockXZUniform < 0
          || this.cameraFracXZUniform < 0
          || this.horizontalRadiusUniform < 0) {
        Logger.error("Voxy GL41Metal chunk bound shader is missing required uniforms");
        this.shader.free();
        this.shader = null;
        this.program = 0;
        this.programFailed = true;
        return false;
      }
      this.ensureGeometry();
      return true;
    } catch (RuntimeException e) {
      this.programFailed = true;
      Logger.error("Failed to compile Voxy GL41Metal chunk bound shader", e);
      return false;
    }
  }

  private void ensureGeometry() {
    if (this.initialised) {
      return;
    }
    this.vao = glGenVertexArrays();
    this.indexBuffer = glGenBuffers();
    this.instanceBuffer = glGenBuffers();
    glBindVertexArray(this.vao);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.indexBuffer);
    try (MemoryStack stack = MemoryStack.stackPush()) {
      ByteBuffer indices = stack.malloc(CUBE_INDICES.length);
      indices.put(CUBE_INDICES);
      indices.flip();
      glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
    }
    glBindBuffer(GL_ARRAY_BUFFER, this.instanceBuffer);
    glEnableVertexAttribArray(0);
    glVertexAttribIPointer(0, 3, org.lwjgl.opengl.GL11C.GL_INT, 3 * Integer.BYTES, 0L);
    glVertexAttribDivisor(0, 1);
    glBindVertexArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    this.initialised = true;
  }

  private void ensureInstanceData() {
    long[] snapshot;
    synchronized (this.sections) {
      if (!this.sectionsDirty) {
        return;
      }
      snapshot = this.sections.toLongArray();
      this.sectionsDirty = false;
    }
    // Each instance is rasterized as a FULL vertical column (uColumnMinY..uColumnMaxY), so a
    // section's own Y is irrelevant. Collapse the 3D section set to unique XZ columns: a column
    // with
    // many stacked loaded sections then emits ONE tall slab instead of one per section, avoiding a
    // ~(worldHeight/16)x depth-overdraw blow-up. The emitted Y is a placeholder (0); the shader
    // uses
    // only XZ from aSectionCoord.
    LongOpenHashSet columns = new LongOpenHashSet(snapshot.length);
    for (long pos : snapshot) {
      columns.add(packColumn(SectionPos.x(pos), SectionPos.z(pos)));
    }
    long[] columnArray = columns.toLongArray();
    this.instanceCount = columnArray.length;
    if (this.instanceCount == 0) {
      return;
    }
    IntBuffer data = MemoryUtil.memAllocInt(this.instanceCount * 3);
    try {
      for (long col : columnArray) {
        data.put(unpackColumnX(col)).put(0).put(unpackColumnZ(col));
      }
      data.flip();
      glBindBuffer(GL_ARRAY_BUFFER, this.instanceBuffer);
      if (this.instanceCount > this.instanceCapacity) {
        // Orphan-grow with some slack so steady-state section churn does not reallocate each frame.
        this.instanceCapacity = Math.max(this.instanceCount, this.instanceCapacity * 2);
        glBufferData(
            GL_ARRAY_BUFFER, (long) this.instanceCapacity * 3 * Integer.BYTES, GL_DYNAMIC_DRAW);
      }
      org.lwjgl.opengl.GL15C.glBufferSubData(GL_ARRAY_BUFFER, 0L, data);
      glBindBuffer(GL_ARRAY_BUFFER, 0);
    } finally {
      MemoryUtil.memFree(data);
    }
  }

  private void ensureTarget(int w, int h) {
    if (this.depthTexture != 0 && this.width == w && this.height == h) {
      return;
    }
    this.width = w;
    this.height = h;
    if (this.depthTexture == 0) {
      this.depthTexture = glGenTextures();
    }
    glBindTexture(GL_TEXTURE_2D, this.depthTexture);
    glTexImage2D(
        GL_TEXTURE_2D,
        0,
        GL_DEPTH_COMPONENT32F,
        w,
        h,
        0,
        GL_DEPTH_COMPONENT,
        GL_FLOAT,
        (java.nio.ByteBuffer) null);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    // Read as a plain depth value (texelFetch/texture .r), never as a shadow sampler.
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_NONE);
    glBindTexture(GL_TEXTURE_2D, 0);
    if (this.framebuffer == 0) {
      this.framebuffer = glGenFramebuffers();
    }
    glBindFramebuffer(GL_FRAMEBUFFER, this.framebuffer);
    glFramebufferTexture2D(
        GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, this.depthTexture, 0);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
  }

  @Override
  public void close() {
    if (this.shader != null) {
      this.shader.free();
      this.shader = null;
      this.program = 0;
    }
    if (this.vao != 0) {
      glDeleteVertexArrays(this.vao);
      this.vao = 0;
    }
    if (this.indexBuffer != 0) {
      glDeleteBuffers(this.indexBuffer);
      this.indexBuffer = 0;
    }
    if (this.instanceBuffer != 0) {
      glDeleteBuffers(this.instanceBuffer);
      this.instanceBuffer = 0;
    }
    if (this.framebuffer != 0) {
      glDeleteFramebuffers(this.framebuffer);
      this.framebuffer = 0;
    }
    if (this.depthTexture != 0) {
      glDeleteTextures(this.depthTexture);
      this.depthTexture = 0;
    }
  }
}
