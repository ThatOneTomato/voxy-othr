package me.cortex.voxy.client.core.rendering.backend.gl41metal.bridge;

import static org.lwjgl.opengl.GL20C.glGetUniformLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.common.Logger;

/** Iris fragment compatibility transforms and the direct-path stencil helper. */
public final class BridgePrograms {
  private static final boolean DUMP_SHADERS =
      Boolean.parseBoolean(System.getProperty("voxy.gl41metal.dumpShaders", "false"));
  private static final String GLSL_STENCIL_MASK = BridgeGlsl.load("stencil_mask.frag");

  private StencilMask stencilMaskProgram;
  private boolean stencilMaskProgramFailed;

  /** Applies the Apple GLSL 4.1 linker workarounds to a fully assembled direct-geometry shader. */
  public static String prepareDirectIrisFragment(String source) {
    return hoistGlobalInitializers(pruneUnreachableFunctions(source));
  }

  /**
   * Removes every function not transitively reachable from {@code main()} (plus any function
   * referenced from global scope), mirroring Iris {@code CompatibilityTransformer}'s
   * unused-function removal.
   *
   * <p>This is a cleanup, not the Apple GL4.1 linker fix: hoisting global initializers (see {@link
   * #hoistGlobalInitializers}) is what actually defuses the {@code
   * glpLLVMGetFunctionGlobalVariableUse} SIGSEGV. Pruning is kept because it typically halves the
   * compiled shader (a shader pack ships an entire lighting library that {@code voxy_emitFragment}
   * usually exercises a small fraction of), which both speeds up compilation and is cheap defensive
   * armour against driver bugs that scale with program size or symbol-table pressure.
   *
   * <p>Reachability is sound: an actually-called function appears as an identifier inside a
   * reachable body, so it is kept. Over-removal could therefore only happen if a real call were
   * missed, which would surface as a loud {@code undefined function} compile error rather than a
   * silent miscompile. Any parsing failure falls back to the original (unpruned) source, so the
   * worst case is the pre-existing behaviour. The source is already fully preprocessed (only a
   * single {@code #version} directive, no {@code #include}/{@code #define}) and GLSL has no string
   * literals, which is what makes brace-matching + identifier scanning safe here.
   */
  private static String pruneUnreachableFunctions(String source) {
    try {
      String stripped = stripGlslComments(source);
      List<FunctionRegion> functions = findTopLevelFunctions(stripped);
      if (functions.isEmpty()) {
        return source;
      }
      Set<String> functionNames = new HashSet<>();
      for (FunctionRegion f : functions) {
        functionNames.add(f.name);
      }
      if (!functionNames.contains("main")) {
        return source; // unexpected shape; don't risk pruning
      }
      // Build call edges (function -> declared functions referenced in its body) and mark the spans
      // covered by function definitions so the remaining global text can seed extra roots.
      Map<String, Set<String>> callees = new HashMap<>();
      boolean[] inFunction = new boolean[stripped.length()];
      for (FunctionRegion f : functions) {
        Set<String> edges = callees.computeIfAbsent(f.name, k -> new HashSet<>());
        collectFunctionRefs(stripped, f.start, f.end, functionNames, edges);
        for (int i = f.start; i < f.end; i++) {
          inFunction[i] = true;
        }
      }
      // Roots: main plus any declared function referenced from global scope (e.g. global
      // initializers or a forward-declared prototype), so we never strip a globally-referenced fn.
      Set<String> roots = new HashSet<>();
      roots.add("main");
      StringBuilder globalText = new StringBuilder();
      for (int i = 0; i < stripped.length(); i++) {
        if (!inFunction[i]) {
          globalText.append(stripped.charAt(i));
        }
      }
      collectFunctionRefs(globalText, 0, globalText.length(), functionNames, roots);
      // BFS the reachable set.
      Set<String> reachable = new HashSet<>();
      Deque<String> queue = new ArrayDeque<>(roots);
      while (!queue.isEmpty()) {
        String name = queue.poll();
        if (!reachable.add(name)) {
          continue;
        }
        Set<String> edges = callees.get(name);
        if (edges != null) {
          for (String c : edges) {
            if (!reachable.contains(c)) {
              queue.add(c);
            }
          }
        }
      }
      if (reachable.size() == functionNames.size()) {
        return stripped; // nothing to prune; still return the comment-stripped form
      }
      // Reassemble, dropping unreachable definitions (functions are in source order).
      StringBuilder out = new StringBuilder(stripped.length());
      int cursor = 0;
      for (FunctionRegion f : functions) {
        out.append(stripped, cursor, f.start);
        if (reachable.contains(f.name)) {
          out.append(stripped, f.start, f.end);
        }
        cursor = f.end;
      }
      out.append(stripped, cursor, stripped.length());
      if (DUMP_SHADERS) {
        Logger.info(
            "gl41metal: pruned Iris colour shader from "
                + functionNames.size()
                + " to "
                + reachable.size()
                + " functions ("
                + source.length()
                + " -> "
                + out.length()
                + " chars)");
      }
      return out.toString();
    } catch (RuntimeException e) {
      Logger.warn("gl41metal: unused-function pruning failed, compiling full shader: " + e);
      return source;
    }
  }

  // A top-level function definition: its name and the [start, end) span (header through closing }).
  private record FunctionRegion(String name, int start, int end) {}

  // Strips // line and /* block */ comments. GLSL has no string/char literals so this needs no
  // string-awareness. Block comments become a single space so they can't merge adjacent tokens.
  private static String stripGlslComments(String src) {
    int n = src.length();
    StringBuilder out = new StringBuilder(n);
    int i = 0;
    while (i < n) {
      char c = src.charAt(i);
      if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
        i += 2;
        while (i < n && src.charAt(i) != '\n') {
          i++;
        }
      } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
        i += 2;
        while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
          i++;
        }
        i = Math.min(n, i + 2);
        out.append(' ');
      } else {
        out.append(c);
        i++;
      }
    }
    return out.toString();
  }

  // Enumerates top-level function definitions. A top-level brace whose preceding header (since the
  // last top-level ';' or '}') trims to something ending in ')' is a function body; anything else
  // (struct / interface / uniform block) is skipped over. Bodies are jumped via brace matching, so
  // only ever sees depth-0 braces.
  private static List<FunctionRegion> findTopLevelFunctions(String src) {
    List<FunctionRegion> result = new ArrayList<>();
    int n = src.length();
    int unitStart = 0;
    int i = 0;
    while (i < n) {
      char c = src.charAt(i);
      if (c == ';') {
        unitStart = i + 1;
        i++;
      } else if (c == '}') {
        unitStart = i + 1;
        i++;
      } else if (c == '{') {
        String header = src.substring(unitStart, i).strip();
        int bodyEnd = matchBrace(src, i);
        if (bodyEnd < 0) {
          break; // unbalanced; stop and keep what we found
        }
        if (header.endsWith(")")) {
          String name = extractFunctionName(header);
          if (name != null) {
            result.add(new FunctionRegion(name, unitStart, bodyEnd));
          }
          unitStart = bodyEnd; // function definitions have no trailing ';'
        }
        // For both function and non-function blocks, resume scanning after the matched block.
        i = bodyEnd;
      } else {
        i++;
      }
    }
    return result;
  }

  // Returns the identifier immediately preceding the first '(' of a function header, or null.
  private static String extractFunctionName(String header) {
    int paren = header.indexOf('(');
    if (paren < 0) {
      return null;
    }
    int end = paren;
    while (end > 0 && Character.isWhitespace(header.charAt(end - 1))) {
      end--;
    }
    int start = end;
    while (start > 0) {
      char ch = header.charAt(start - 1);
      if (Character.isLetterOrDigit(ch) || ch == '_') {
        start--;
      } else {
        break;
      }
    }
    if (start >= end) {
      return null;
    }
    char first = header.charAt(start);
    if (!(Character.isLetter(first) || first == '_')) {
      return null;
    }
    return header.substring(start, end);
  }

  // Index after the '}' matching the '{' at openIdx, or -1 if unbalanced.
  private static int matchBrace(String src, int openIdx) {
    int depth = 0;
    int n = src.length();
    for (int i = openIdx; i < n; i++) {
      char c = src.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return i + 1;
        }
      }
    }
    return -1;
  }

  // Adds to out every declared-function name that is *called* in src[start, end). A call is the
  // only
  // way a function name can be referenced in GLSL (no function pointers), so we require the next
  // non-whitespace character after the identifier to be '('. This deliberately ignores collisions
  // with variable / struct / field names that merely share a function's spelling, which is what
  // makes the reachability set tight enough to match what Iris compiles. Number literals (incl.
  // type
  // suffixes like 255u / 0x1p2f) are skipped so they can't be misread as identifiers.
  private static void collectFunctionRefs(
      CharSequence src, int start, int end, Set<String> functionNames, Set<String> out) {
    int i = start;
    while (i < end) {
      char c = src.charAt(i);
      if (Character.isLetter(c) || c == '_') {
        int j = i + 1;
        while (j < end) {
          char d = src.charAt(j);
          if (Character.isLetterOrDigit(d) || d == '_') {
            j++;
          } else {
            break;
          }
        }
        String id = src.subSequence(i, j).toString();
        if (functionNames.contains(id)) {
          int k = j;
          while (k < end && Character.isWhitespace(src.charAt(k))) {
            k++;
          }
          if (k < end && src.charAt(k) == '(') {
            out.add(id);
          }
        }
        i = j;
      } else if (Character.isDigit(c)) {
        int j = i + 1;
        while (j < end) {
          char d = src.charAt(j);
          if (Character.isLetterOrDigit(d) || d == '_' || d == '.') {
            j++;
          } else {
            break;
          }
        }
        i = j;
      } else {
        i++;
      }
    }
  }

  /**
   * Hoists every non-{@code const} file-scope variable initializer to the top of {@code main()},
   * leaving bare global declarations behind.
   *
   * <p>Apple's GL4.1 GLSL linker SIGSEGVs in {@code glpLLVMGetFunctionGlobalVariableUse} while
   * analysing the global-initializer dependency graph that a shader-pack fragment patch produces
   * (e.g. {@code vec3 upVec = normalize(gbufferModelView[1].xyz);}, {@code sunVec =
   * GetSunVector();} and their transitive chains through the std140 uniforms). Iris's own programs
   * never hit this because the pack computes that preamble in the VERTEX stage and passes it as
   * {@code flat in} varyings; our full-screen distant-terrain bridge has no vertex stage, so the
   * identical preamble lands as fragment-scope global initializers. The crash is specific to
   * global-init analysis: a single function using many globals is fine (Iris's own {@code main}
   * does), only the synthesized initializer graph overflows.
   *
   * <p>Moving the initializers into {@code main()} in source order is behaviour-preserving: source
   * order is already dependency order (GLSL requires declaration-before-use), {@code main()} is the
   * sole entry point, and it runs the assignments before sampling/shading. {@code const} globals
   * keep their compile-time-constant initializers. Any parse anomaly falls back to the
   * untransformed source, and a mis-hoist would surface as a loud compile error rather than a
   * silent miscompile.
   */
  private static String hoistGlobalInitializers(String src) {
    try {
      List<FunctionRegion> funcs = findTopLevelFunctions(src);
      if (funcs.isEmpty()) {
        return src;
      }
      StringBuilder out = new StringBuilder(src.length());
      StringBuilder hoist = new StringBuilder();
      int cursor = 0;
      for (FunctionRegion f : funcs) {
        appendGlobalRegionHoisted(src, cursor, f.start, out, hoist);
        out.append(src, f.start, f.end);
        cursor = f.end;
      }
      appendGlobalRegionHoisted(src, cursor, src.length(), out, hoist);
      if (hoist.length() == 0) {
        return src;
      }
      String result = out.toString();
      int mainIdx = result.indexOf("void main(");
      int brace = mainIdx < 0 ? -1 : result.indexOf('{', mainIdx);
      if (brace < 0) {
        return src;
      }
      String injected = result.substring(0, brace + 1) + "\n" + hoist + result.substring(brace + 1);
      if (DUMP_SHADERS) {
        Logger.info(
            "gl41metal: hoisted global initializers into main() (Apple GL4.1 linker workaround)");
      }
      return injected;
    } catch (RuntimeException e) {
      Logger.warn("gl41metal: global-initializer hoisting failed, compiling as-is: " + e);
      return src;
    }
  }

  // Splits src[start,end) (a region outside any function) into ';'-terminated, brace/paren/bracket
  // aware statements. Each non-const variable declaration with an initializer becomes a bare
  // declaration in `out`, with its `name = init;` assignment appended to `hoist`. Everything else
  // (blocks, qualified declarations, prototypes, directives, whitespace) is copied verbatim.
  private static void appendGlobalRegionHoisted(
      String src, int start, int end, StringBuilder out, StringBuilder hoist) {
    int i = start;
    int stmtStart = start;
    int paren = 0;
    int bracket = 0;
    int brace = 0;
    while (i < end) {
      char c = src.charAt(i);
      switch (c) {
        case '(' -> paren++;
        case ')' -> paren--;
        case '[' -> bracket++;
        case ']' -> bracket--;
        case '{' -> brace++;
        case '}' -> brace--;
        default -> {}
      }
      if (c == ';' && paren == 0 && bracket == 0 && brace == 0) {
        out.append(hoistStatement(src.substring(stmtStart, i), hoist)).append(';');
        i++;
        stmtStart = i;
      } else {
        i++;
      }
    }
    if (stmtStart < end) {
      out.append(src, stmtStart, end);
    }
  }

  // Given one global statement (without its trailing ';'), returns the bare declaration text and
  // appends any hoisted `name = init;` assignments to `hoist`. Non-declarations are returned as-is.
  private static String hoistStatement(String stmt, StringBuilder hoist) {
    String t = stmt.strip();
    if (t.isEmpty() || t.indexOf('{') >= 0 || t.startsWith("#")) {
      return stmt;
    }
    if (startsWithKeyword(t, "const")
        || startsWithKeyword(t, "uniform")
        || startsWithKeyword(t, "in")
        || startsWithKeyword(t, "out")
        || startsWithKeyword(t, "flat")
        || startsWithKeyword(t, "layout")
        || startsWithKeyword(t, "precision")
        || startsWithKeyword(t, "struct")) {
      return stmt;
    }
    if (topLevelAssignIndex(t) < 0) {
      return stmt;
    }
    int typeEnd = 0;
    while (typeEnd < t.length() && !Character.isWhitespace(t.charAt(typeEnd))) {
      typeEnd++;
    }
    String type = t.substring(0, typeEnd);
    if (!type.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      return stmt;
    }
    String rest = t.substring(typeEnd).strip();
    StringBuilder bare = new StringBuilder();
    StringBuilder local = new StringBuilder();
    List<String> declarators = splitTopLevel(rest, ',');
    boolean hoisted = false;
    for (int d = 0; d < declarators.size(); d++) {
      String decl = declarators.get(d).strip();
      if (d > 0) {
        bare.append(", ");
      }
      int eq = topLevelAssignIndex(decl);
      if (eq < 0) {
        bare.append(decl);
        continue;
      }
      String name = decl.substring(0, eq).strip();
      String init = decl.substring(eq + 1).strip();
      // GLSL "T name[N] = T[N](...);" mixes the array-size suffix into the LHS. Splitting that
      // into "T name[N]; name[N] = T[N](...);" turns the size suffix into an out-of-bounds index
      // access on the hoisted assignment, which the GLSL compiler rejects statically as
      // "Index N beyond bounds (size N)". Real shader packs do this for jitter tables, blue-noise
      // offset tables, etc. (Complementary's lib/antialiasing/jitter.glsl is one example). Leave
      // array declarators inline; a literal array initializer at file scope is not part of the
      // global-init dependency graph that crashes Apple's GL4.1 linker, so skipping hoisting
      // here does not regress the SIGSEGV fix.
      if (name.indexOf('[') >= 0) {
        bare.append(decl);
        continue;
      }
      bare.append(name);
      local.append("  ").append(name).append(" = ").append(init).append(";\n");
      hoisted = true;
    }
    if (!hoisted) {
      return stmt;
    }
    hoist.append(local);
    String lead = stmt.substring(0, stmt.length() - stmt.stripLeading().length());
    return lead + type + " " + bare;
  }

  private static boolean startsWithKeyword(String s, String kw) {
    if (!s.startsWith(kw)) {
      return false;
    }
    if (s.length() == kw.length()) {
      return true;
    }
    char next = s.charAt(kw.length());
    return !(Character.isLetterOrDigit(next) || next == '_');
  }

  // First top-level single '=' (skipping ==, !=, <=, >=), or -1. Tracks (), [], {} nesting.
  private static int topLevelAssignIndex(String s) {
    int paren = 0;
    int bracket = 0;
    int brace = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '(' -> paren++;
        case ')' -> paren--;
        case '[' -> bracket++;
        case ']' -> bracket--;
        case '{' -> brace++;
        case '}' -> brace--;
        case '=' -> {
          if (paren == 0 && bracket == 0 && brace == 0) {
            char prev = i > 0 ? s.charAt(i - 1) : ' ';
            char next = i + 1 < s.length() ? s.charAt(i + 1) : ' ';
            if (prev != '=' && prev != '!' && prev != '<' && prev != '>' && next != '=') {
              return i;
            }
          }
        }
        default -> {}
      }
    }
    return -1;
  }

  private static List<String> splitTopLevel(String s, char sep) {
    List<String> parts = new ArrayList<>();
    int paren = 0;
    int bracket = 0;
    int brace = 0;
    int last = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '(' -> paren++;
        case ')' -> paren--;
        case '[' -> bracket++;
        case ']' -> bracket--;
        case '{' -> brace++;
        case '}' -> brace--;
        default -> {}
      }
      if (c == sep && paren == 0 && bracket == 0 && brace == 0) {
        parts.add(s.substring(last, i));
        last = i + 1;
      }
    }
    parts.add(s.substring(last));
    return parts;
  }

  /**
   * Lazily compiles the near-scene coverage stencil-mask program (see {@link #GLSL_STENCIL_MASK}).
   * Returns null if it ever fails to compile, so the strict Iris path skips that frame's distant
   * output rather than painting unmasked distant terrain over the near scene.
   */
  StencilMask stencilMask() {
    if (this.stencilMaskProgram != null) {
      return this.stencilMaskProgram;
    }
    if (this.stencilMaskProgramFailed) {
      return null;
    }
    try {
      Shader shader =
          compileHelper(GLSL_STENCIL_MASK, "GL41Metal distant terrain near-coverage stencil mask");
      this.stencilMaskProgram =
          new StencilMask(
              shader,
              glGetUniformLocation(shader.id(), "uNearDepth"),
              glGetUniformLocation(shader.id(), "uNearSize"),
              glGetUniformLocation(shader.id(), "uTargetSize"),
              glGetUniformLocation(shader.id(), "uReverseDepth"));
      return this.stencilMaskProgram;
    } catch (RuntimeException e) {
      this.stencilMaskProgramFailed = true;
      Logger.error("Failed to compile Voxy GL41Metal near-coverage stencil mask", e);
      return null;
    }
  }

  private static Shader compileHelper(String fragmentSource, String name) {
    return Shader.make()
        .addSource(
            ShaderType.VERTEX,
            ShaderLoader.parse("voxy:lod/gl41metal/interop_bridge.vert", "410 core"))
        .addSource(ShaderType.FRAGMENT, fragmentSource)
        .compile()
        .name(name);
  }

  void close() {
    if (this.stencilMaskProgram != null) {
      this.stencilMaskProgram.shader().free();
      this.stencilMaskProgram = null;
    }
  }

  record StencilMask(
      Shader shader,
      int nearDepthUniform,
      int nearSizeUniform,
      int targetSizeUniform,
      int reverseDepthUniform) {}
}
