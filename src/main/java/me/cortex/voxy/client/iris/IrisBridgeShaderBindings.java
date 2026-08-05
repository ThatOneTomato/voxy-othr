package me.cortex.voxy.client.iris;

import static org.lwjgl.opengl.ARBUniformBufferObject.glBindBufferBase;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL20C.GL_MAX_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL20C.glGetUniformLocation;
import static org.lwjgl.opengl.GL31C.GL_INVALID_INDEX;
import static org.lwjgl.opengl.GL31C.glGetUniformBlockIndex;
import static org.lwjgl.opengl.GL31C.glUniformBlockBinding;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.opengl.GL41C.glProgramUniform1i;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import kroppeb.stareval.function.FunctionReturn;
import kroppeb.stareval.function.Type;
import me.cortex.voxy.client.mixin.iris.CustomUniformsAccessor;
import me.cortex.voxy.client.mixin.iris.IrisRenderingPipelineAccessor;
import me.cortex.voxy.common.Logger;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.gl.image.ImageHolder;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.gl.uniform.DynamicLocationalUniformHolder;
import net.irisshaders.iris.gl.uniform.FloatSupplier;
import net.irisshaders.iris.gl.uniform.LocationalUniformHolder;
import net.irisshaders.iris.gl.uniform.Uniform;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformType;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.uniforms.custom.cached.BooleanCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.Float2VectorCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.Float3VectorCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.Float4MatrixCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.Float4VectorCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.FloatCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.Int2VectorCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.Int3VectorCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.IntCachedUniform;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.lwjgl.system.MemoryUtil;

public final class IrisBridgeShaderBindings {
  public static final int UNIFORM_BINDING_POINT = 5;
  public static final int SSBO_BINDING_BASE = 10;
  // Direct drawlist reserves fragment unit 0 for the block atlas. Pack samplers start at 1, leaving
  // 14 pack slots under Apple's empirically safe 15-unit limit.
  public static final int SAMPLER_BINDING_BASE = 1;

  // Deduplicates the per-build sampler-budget INFO log: see the comment at the log site for why
  // build() runs many times per pack load. Concurrent-safe so the render thread can race other
  // bookkeeping threads if Iris ever moves program setup off the render thread.
  private static final Set<String> LOGGED_SAMPLER_BUDGETS = ConcurrentHashMap.newKeySet();
  // Same rationale as LOGGED_SAMPLER_BUDGETS: dedup the "uniforms could not be found" diagnostic so
  // it logs once per unique missing-set instead of once per queried program slot at pack load.
  private static final Set<String> LOGGED_MISSING_UNIFORMS = ConcurrentHashMap.newKeySet();

  private IrisBridgeShaderBindings() {}

  // JOML's `Vector*.getToAddress(long)` / `Matrix4f.getToAddress(long)` are Unsafe-only APIs routed
  // directly to MemUtil$MemUtilUnsafe, bypassing the MemUtilNIO fallback that catches the rest of
  // JOML on this JDK. Apple's JDK21 build (with the joml-1.10.5 shipped via MC's BOM) reliably
  // fails
  // MemUtilUnsafe.<clinit> at bootstrap with `UnsupportedOperationException: Unexpected Matrix4f
  // element offset` (JOML's checkMatrix4f rejects the JVM's actual field layout). The general JOML
  // path keeps working via MemUtilNIO, but every subsequent reference to MemUtilUnsafe throws
  // NoClassDefFoundError, so any `getToAddress(...)` call from the bridge crashes the render thread
  // the first time it runs. These small helpers write the components one-by-one through
  // MemoryUtil.memPut* — same column-major layout as `getToAddress` (matrix m00..m33), same
  // in-memory
  // size — but never trigger MemUtilUnsafe initialization. Iris's own uniform pipeline does not hit
  // this because Iris writes uniforms through NIO ByteBuffer APIs (e.g. glUniformMatrix4fv).
  private static void putVec2f(long addr, Vector2f v) {
    MemoryUtil.memPutFloat(addr, v.x);
    MemoryUtil.memPutFloat(addr + 4, v.y);
  }

  private static void putVec3f(long addr, Vector3f v) {
    MemoryUtil.memPutFloat(addr, v.x);
    MemoryUtil.memPutFloat(addr + 4, v.y);
    MemoryUtil.memPutFloat(addr + 8, v.z);
  }

  private static void putVec4f(long addr, Vector4f v) {
    MemoryUtil.memPutFloat(addr, v.x);
    MemoryUtil.memPutFloat(addr + 4, v.y);
    MemoryUtil.memPutFloat(addr + 8, v.z);
    MemoryUtil.memPutFloat(addr + 12, v.w);
  }

  private static void putVec2i(long addr, Vector2i v) {
    MemoryUtil.memPutInt(addr, v.x);
    MemoryUtil.memPutInt(addr + 4, v.y);
  }

  private static void putVec3i(long addr, Vector3i v) {
    MemoryUtil.memPutInt(addr, v.x);
    MemoryUtil.memPutInt(addr + 4, v.y);
    MemoryUtil.memPutInt(addr + 8, v.z);
  }

  private static void putVec4i(long addr, Vector4i v) {
    MemoryUtil.memPutInt(addr, v.x);
    MemoryUtil.memPutInt(addr + 4, v.y);
    MemoryUtil.memPutInt(addr + 8, v.z);
    MemoryUtil.memPutInt(addr + 12, v.w);
  }

  private static void putMatrix4f(long addr, Matrix4fc m) {
    MemoryUtil.memPutFloat(addr, m.m00());
    MemoryUtil.memPutFloat(addr + 4, m.m01());
    MemoryUtil.memPutFloat(addr + 8, m.m02());
    MemoryUtil.memPutFloat(addr + 12, m.m03());
    MemoryUtil.memPutFloat(addr + 16, m.m10());
    MemoryUtil.memPutFloat(addr + 20, m.m11());
    MemoryUtil.memPutFloat(addr + 24, m.m12());
    MemoryUtil.memPutFloat(addr + 28, m.m13());
    MemoryUtil.memPutFloat(addr + 32, m.m20());
    MemoryUtil.memPutFloat(addr + 36, m.m21());
    MemoryUtil.memPutFloat(addr + 40, m.m22());
    MemoryUtil.memPutFloat(addr + 44, m.m23());
    MemoryUtil.memPutFloat(addr + 48, m.m30());
    MemoryUtil.memPutFloat(addr + 52, m.m31());
    MemoryUtil.memPutFloat(addr + 56, m.m32());
    MemoryUtil.memPutFloat(addr + 60, m.m33());
  }

  public record Bindings(
      String shaderHeader,
      int uniformSize,
      LongConsumer uniformUpdater,
      Runnable resourceBinder,
      IntConsumer programSetup,
      int samplerCount,
      int[] samplerTargets) {
    public Bindings {
      samplerTargets = samplerTargets == null ? new int[0] : samplerTargets.clone();
    }

    @Override
    public int[] samplerTargets() {
      return this.samplerTargets.clone();
    }
  }

  public static Bindings build(
      IrisRenderingPipeline pipeline,
      IrisShaderPatch patch,
      CustomUniforms customUniforms,
      ShaderStorageBufferHolder ssboHolder) {
    StructLayout uniforms = null;
    if (customUniforms != null) {
      uniforms =
          createUniformLayoutStructAndUpdater(createUniformSet(pipeline, customUniforms, patch));
    } else if (patch.getUniformList().length != 0) {
      throw new IllegalStateException(
          "shader pack declares Voxy uniforms but Iris custom uniforms are unavailable");
    }
    if (!patch.getSSBOs().isEmpty() && ssboHolder == null) {
      throw new IllegalStateException(
          "shader pack declares Voxy SSBOs but Iris SSBO holder is unavailable");
    }

    ImageSet imageSet = createImageSet(pipeline, patch);
    SSBOSet ssboSet = createSSBOLayouts(patch.getSSBOs(), ssboHolder);

    StringBuilder header = new StringBuilder();
    if (uniforms != null) {
      // GL 4.1 / GLSL 410 (macOS) does not support layout(binding=...) qualifiers, so the block is
      // associated with UNIFORM_BINDING_POINT through glUniformBlockBinding in programSetup below
      // rather than a declared binding point (which fails with "Unknown identifier 'binding'").
      header
          .append("layout(std140) uniform ShaderUniformBindings ")
          .append(uniforms.layout())
          .append(";\n\n");
    }
    if (ssboSet != null) {
      header.append("#define BUFFER_BINDING_INDEX_BASE ").append(SSBO_BINDING_BASE).append("\n");
      header.append(ssboSet.layout()).append("\n\n");
    }
    if (imageSet != null) {
      header.append(imageSet.layout()).append("\n\n");
    }

    StructLayout capturedUniforms = uniforms;
    ImageSet capturedImageSet = imageSet;
    boolean hasUniformBlock = uniforms != null;
    Runnable binder =
        () -> {
          if (ssboSet != null) {
            ssboSet.bindingFunction().accept(SSBO_BINDING_BASE);
          }
          if (imageSet != null) {
            imageSet.bindingFunction().accept(SAMPLER_BINDING_BASE);
          }
        };
    // Post-link wiring for GL 4.1 (which lacks layout(binding=...)): point the std140 block at
    // UNIFORM_BINDING_POINT and assign each shader-pack sampler its texture unit. Done once per
    // program compile; glBindBufferBase / the per-frame binder above then feed those bind points.
    IntConsumer programSetup =
        program -> {
          if (hasUniformBlock) {
            int blockIndex = glGetUniformBlockIndex(program, "ShaderUniformBindings");
            if (blockIndex != GL_INVALID_INDEX) {
              glUniformBlockBinding(program, blockIndex, UNIFORM_BINDING_POINT);
            }
          }
          if (capturedImageSet != null) {
            String[] names = capturedImageSet.samplerNames();
            for (int i = 0; i < names.length; i++) {
              int location = glGetUniformLocation(program, names[i]);
              if (location >= 0) {
                glProgramUniform1i(program, location, SAMPLER_BINDING_BASE + i);
              }
            }
          }
        };
    return new Bindings(
        header.toString(),
        capturedUniforms == null ? 0 : capturedUniforms.size(),
        capturedUniforms == null ? ptr -> {} : capturedUniforms.updater(),
        binder,
        programSetup,
        capturedImageSet == null ? 0 : capturedImageSet.samplerNames().length,
        capturedImageSet == null ? new int[0] : capturedImageSet.samplerTargets());
  }

  private static String convertToGlslType(UniformType type) {
    return switch (type) {
      case INT -> "int";
      case FLOAT -> "float";
      case MAT3 -> "mat3";
      case MAT4 -> "mat4";
      case VEC2 -> "vec2";
      case VEC2I -> "ivec2";
      case VEC3 -> "vec3";
      case VEC3I -> "ivec3";
      case VEC4 -> "vec4";
      case VEC4I -> "ivec4";
    };
  }

  private record StructLayout(int size, String layout, LongConsumer updater) {}

  @SuppressWarnings("unchecked")
  private static StructLayout createUniformLayoutStructAndUpdater(
      List<UniformWritingHolder> uniforms) {
    if (uniforms.isEmpty()) {
      return null;
    }

    List<UniformWritingHolder>[] ordering =
        new List[] {new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};
    for (var uniform : uniforms) {
      ordering[getUniformOrdering(uniform.type)].add(uniform);
    }

    int pos = 0;
    // MUST be the LINKED variant (matches voxy-fabric IrisVoxyRenderPipelineData): the std140
    // struct
    // below is emitted by iterating this map, and the per-uniform updaters write at the map KEY
    // (the
    // computed std140 byte offset / 4). A plain Int2ObjectOpenHashMap iterates in hash-bucket
    // order,
    // so the GLSL compiler would assign std140 offsets in a scrambled declaration order that does
    // NOT
    // match the offsets the updaters write to -> every pack uniform reads a different slot than it
    // was
    // written (compounded by the un-zeroed scratch alloc), producing per-run-random ambientColor/
    // lightColor and the "distant flickers black/garbage" failure. Insertion order here is
    // ascending
    // pos, so the linked map keeps declaration order == offset order == what the updaters assume.
    Int2ObjectLinkedOpenHashMap<UniformWritingHolder> layout = new Int2ObjectLinkedOpenHashMap<>();
    for (var uniform : ordering[0]) {
      layout.put(pos, uniform);
      pos += getSizeAndAlignment(uniform.type) >> 5;
    }
    if (!ordering[1].isEmpty() && (ordering[1].size() & 1) == 0) {
      for (var uniform : ordering[1]) {
        layout.put(pos, uniform);
        pos += getSizeAndAlignment(uniform.type) >> 5;
      }
      ordering[1].clear();
    }
    for (var uniform : ordering[2]) {
      layout.put(pos, uniform);
      pos += getSizeAndAlignment(uniform.type) >> 5;
      if (!ordering[3].isEmpty()) {
        uniform = ordering[3].remove(0);
        layout.put(pos, uniform);
        pos += getSizeAndAlignment(uniform.type) >> 5;
      } else {
        pos += 1;
      }
    }
    for (var uniform : ordering[1]) {
      layout.put(pos, uniform);
      pos += getSizeAndAlignment(uniform.type) >> 5;
    }
    for (var uniform : ordering[3]) {
      layout.put(pos, uniform);
      pos += getSizeAndAlignment(uniform.type) >> 5;
    }

    if (layout.size() != uniforms.size()) {
      throw new IllegalStateException("Iris Voxy uniform layout lost entries");
    }

    StringBuilder struct = new StringBuilder("{\n");
    for (var pair : layout.int2ObjectEntrySet()) {
      struct
          .append("\t")
          .append(convertToGlslType(pair.getValue().type))
          .append(" ")
          .append(pair.getValue().name)
          .append(";\n");
    }
    struct.append("}");

    LongConsumer[] updaters = new LongConsumer[uniforms.size()];
    int i = 0;
    for (var pair : layout.int2ObjectEntrySet()) {
      updaters[i++] = pair.getValue().writingFactory.get(pair.getIntKey() * 4L);
    }
    return new StructLayout(
        pos * 4,
        struct.toString(),
        ptr -> {
          for (var updater : updaters) {
            updater.accept(ptr);
          }
        });
  }

  private static LongConsumer createWriter(long offset, FunctionReturn ret, CachedUniform uniform) {
    if (uniform instanceof BooleanCachedUniform bcu) {
      return ptr -> {
        bcu.writeTo(ret);
        MemoryUtil.memPutInt(ptr + offset, ret.booleanReturn ? 1 : 0);
      };
    } else if (uniform instanceof FloatCachedUniform fcu) {
      return ptr -> {
        fcu.writeTo(ret);
        MemoryUtil.memPutFloat(ptr + offset, ret.floatReturn);
      };
    } else if (uniform instanceof IntCachedUniform icu) {
      return ptr -> {
        icu.writeTo(ret);
        MemoryUtil.memPutInt(ptr + offset, ret.intReturn);
      };
    } else if (uniform instanceof Float2VectorCachedUniform v2fcu) {
      return ptr -> {
        v2fcu.writeTo(ret);
        putVec2f(ptr + offset, (Vector2f) ret.objectReturn);
      };
    } else if (uniform instanceof Float3VectorCachedUniform v3fcu) {
      return ptr -> {
        v3fcu.writeTo(ret);
        putVec3f(ptr + offset, (Vector3f) ret.objectReturn);
      };
    } else if (uniform instanceof Float4VectorCachedUniform v4fcu) {
      return ptr -> {
        v4fcu.writeTo(ret);
        putVec4f(ptr + offset, (Vector4f) ret.objectReturn);
      };
    } else if (uniform instanceof Int2VectorCachedUniform v2icu) {
      return ptr -> {
        v2icu.writeTo(ret);
        putVec2i(ptr + offset, (Vector2i) ret.objectReturn);
      };
    } else if (uniform instanceof Int3VectorCachedUniform v3icu) {
      return ptr -> {
        v3icu.writeTo(ret);
        putVec3i(ptr + offset, (Vector3i) ret.objectReturn);
      };
    } else if (uniform instanceof Float4MatrixCachedUniform f4mcu) {
      return ptr -> {
        f4mcu.writeTo(ret);
        putMatrix4f(ptr + offset, (Matrix4f) ret.objectReturn);
      };
    }
    throw new IllegalStateException("Unknown uniform type " + uniform.getClass().getName());
  }

  private static int P(int size, int align) {
    return size << 5 | align;
  }

  private static int getSizeAndAlignment(UniformType type) {
    return switch (type) {
      case INT, FLOAT -> P(1, 1);
      case MAT3 -> P(4 + 4 + 3, 4);
      case MAT4 -> P(4 * 4, 4);
      case VEC2, VEC2I -> P(2, 2);
      case VEC3, VEC3I -> P(3, 4);
      case VEC4, VEC4I -> P(4, 4);
    };
  }

  private static int getUniformOrdering(UniformType type) {
    return switch (type) {
      case MAT4, VEC4, VEC4I -> 0;
      case VEC2, VEC2I -> 1;
      case VEC3, VEC3I, MAT3 -> 2;
      case INT, FLOAT -> 3;
    };
  }

  private record UniformWritingHolder(
      String name, UniformType type, Long2ObjectFunction<LongConsumer> writingFactory) {}

  private static List<UniformWritingHolder> createUniformSet(
      IrisRenderingPipeline pipeline, CustomUniforms customUniforms, IrisShaderPatch patch) {
    List<UniformWritingHolder> uniforms = new ArrayList<>();
    Set<String> seenUniforms = new HashSet<>();
    DynamicLocationalUniformHolder uniformBuilder =
        new DynamicLocationalUniformHolder() {
          // The frequency-based overloads default (via LocationalUniformHolder) to
          // location()+addUniform(), which this builder cannot honor because it captures values
          // into a std140 block instead of binding GL uniform locations. Route them to the
          // notifier-based overloads so uniforms registered with an update frequency (e.g.
          // fogColor, iris_FogColor, the vx* matrices) are actually captured. Matches the Fabric
          // IrisVoxyRenderPipelineData builder.
          @Override
          public DynamicLocationalUniformHolder uniform1i(
              UniformUpdateFrequency updateFrequency, String name, IntSupplier value) {
            return this.uniform1i(name, value, null);
          }

          @Override
          public DynamicLocationalUniformHolder uniform1f(
              UniformUpdateFrequency updateFrequency, String name, FloatSupplier value) {
            return this.uniform1f(name, value, null);
          }

          @Override
          public DynamicLocationalUniformHolder uniform1f(
              UniformUpdateFrequency updateFrequency, String name, IntSupplier value) {
            return this.uniform1f(name, value, null);
          }

          @Override
          public DynamicLocationalUniformHolder uniform1f(
              UniformUpdateFrequency updateFrequency, String name, DoubleSupplier value) {
            return this.uniform1f(name, value, null);
          }

          @Override
          public DynamicLocationalUniformHolder uniform1b(
              UniformUpdateFrequency updateFrequency, String name, BooleanSupplier value) {
            this.injectDynamicUniformType(
                name,
                UniformType.INT,
                offset -> ptr -> MemoryUtil.memPutInt(ptr + offset, value.getAsBoolean() ? 1 : 0));
            return this;
          }

          @Override
          public DynamicLocationalUniformHolder uniform2f(
              UniformUpdateFrequency updateFrequency, String name, Supplier<Vector2f> value) {
            return this.uniform2f(name, value, null);
          }

          @Override
          public DynamicLocationalUniformHolder uniform2i(
              UniformUpdateFrequency updateFrequency, String name, Supplier<Vector2i> value) {
            return this.uniform2i(name, value, null);
          }

          @Override
          public DynamicLocationalUniformHolder uniform3f(
              UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3f> value) {
            return this.uniform3f(name, value, null);
          }

          @Override
          public DynamicLocationalUniformHolder uniform3i(
              UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3i> value) {
            this.injectDynamicUniformType(
                name, UniformType.VEC3I, offset -> ptr -> putVec3i(ptr + offset, value.get()));
            return this;
          }

          @Override
          public DynamicLocationalUniformHolder uniformTruncated3f(
              UniformUpdateFrequency updateFrequency, String name, Supplier<Vector4f> value) {
            this.injectDynamicUniformType(
                name,
                UniformType.VEC3,
                offset ->
                    ptr -> {
                      Vector4f vector = value.get();
                      MemoryUtil.memPutFloat(ptr + offset, vector.x);
                      MemoryUtil.memPutFloat(ptr + offset + 4, vector.y);
                      MemoryUtil.memPutFloat(ptr + offset + 8, vector.z);
                    });
            return this;
          }

          @Override
          public DynamicLocationalUniformHolder uniform3d(
              UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3d> value) {
            this.injectDynamicUniformType(
                name,
                UniformType.VEC3,
                offset ->
                    ptr -> {
                      Vector3d vector = value.get();
                      MemoryUtil.memPutFloat(ptr + offset, (float) vector.x);
                      MemoryUtil.memPutFloat(ptr + offset + 4, (float) vector.y);
                      MemoryUtil.memPutFloat(ptr + offset + 8, (float) vector.z);
                    });
            return this;
          }

          @Override
          public DynamicLocationalUniformHolder uniform4f(
              UniformUpdateFrequency updateFrequency, String name, Supplier<Vector4f> value) {
            return this.uniform4f(name, value, null);
          }

          @Override
          public DynamicLocationalUniformHolder uniform4fArray(
              UniformUpdateFrequency updateFrequency, String name, Supplier<float[]> value) {
            return this.uniform4fArray(name, value, null);
          }

          @Override
          public DynamicLocationalUniformHolder uniformMatrix(
              UniformUpdateFrequency updateFrequency, String name, Supplier<Matrix4fc> value) {
            return this.uniformMatrix(name, value, null);
          }

          @Override
          public DynamicLocationalUniformHolder uniformMatrixFromArray(
              UniformUpdateFrequency updateFrequency, String name, Supplier<float[]> value) {
            this.injectDynamicUniformType(
                name,
                UniformType.MAT4,
                offset ->
                    ptr -> {
                      float[] matrix = value.get();
                      for (int i = 0; i < 16; i++) {
                        MemoryUtil.memPutFloat(
                            ptr + offset + i * Float.BYTES, i < matrix.length ? matrix[i] : 0.0f);
                      }
                    });
            return this;
          }

          @Override
          public DynamicLocationalUniformHolder uniform1i(
              String name, IntSupplier value, ValueUpdateNotifier notifier) {
            this.injectDynamicUniformType(
                name,
                UniformType.INT,
                offset -> ptr -> MemoryUtil.memPutInt(ptr + offset, value.getAsInt()));
            return this;
          }

          @Override
          public DynamicLocationalUniformHolder uniform1f(
              String name, FloatSupplier value, ValueUpdateNotifier notifier) {
            this.injectDynamicUniformType(
                name,
                UniformType.FLOAT,
                offset -> ptr -> MemoryUtil.memPutFloat(ptr + offset, value.getAsFloat()));
            return this;
          }

          @Override
          public DynamicLocationalUniformHolder uniform1f(
              String name, IntSupplier value, ValueUpdateNotifier notifier) {
            this.injectDynamicUniformType(
                name,
                UniformType.FLOAT,
                offset -> ptr -> MemoryUtil.memPutFloat(ptr + offset, value.getAsInt()));
            return this;
          }

          @Override
          public DynamicLocationalUniformHolder uniform1f(
              String name, DoubleSupplier value, ValueUpdateNotifier notifier) {
            this.injectDynamicUniformType(
                name,
                UniformType.FLOAT,
                offset -> ptr -> MemoryUtil.memPutFloat(ptr + offset, (float) value.getAsDouble()));
            return this;
          }

          @Override
          public DynamicLocationalUniformHolder uniform2f(
              String name, Supplier<Vector2f> value, ValueUpdateNotifier notifier) {
            this.injectDynamicUniformType(
                name, UniformType.VEC2, offset -> ptr -> putVec2f(ptr + offset, value.get()));
            return this;
          }

          @Override
          public DynamicLocationalUniformHolder uniform2i(
              String name, Supplier<Vector2i> value, ValueUpdateNotifier notifier) {
            this.injectDynamicUniformType(
                name, UniformType.VEC2I, offset -> ptr -> putVec2i(ptr + offset, value.get()));
            return this;
          }

          @Override
          public DynamicLocationalUniformHolder uniform3f(
              String name, Supplier<Vector3f> value, ValueUpdateNotifier notifier) {
            this.injectDynamicUniformType(
                name, UniformType.VEC3, offset -> ptr -> putVec3f(ptr + offset, value.get()));
            return this;
          }

          @Override
          public DynamicLocationalUniformHolder uniform4f(
              String name, Supplier<Vector4f> value, ValueUpdateNotifier notifier) {
            this.injectDynamicUniformType(
                name, UniformType.VEC4, offset -> ptr -> putVec4f(ptr + offset, value.get()));
            return this;
          }

          @Override
          public DynamicLocationalUniformHolder uniform4fArray(
              String name, Supplier<float[]> value, ValueUpdateNotifier notifier) {
            this.injectDynamicUniformType(
                name,
                UniformType.VEC4,
                offset ->
                    ptr -> {
                      float[] data = value.get();
                      MemoryUtil.memPutFloat(ptr + offset, data.length > 0 ? data[0] : 0.0f);
                      MemoryUtil.memPutFloat(ptr + offset + 4, data.length > 1 ? data[1] : 0.0f);
                      MemoryUtil.memPutFloat(ptr + offset + 8, data.length > 2 ? data[2] : 0.0f);
                      MemoryUtil.memPutFloat(ptr + offset + 12, data.length > 3 ? data[3] : 0.0f);
                    });
            return this;
          }

          @Override
          public DynamicLocationalUniformHolder uniform4i(
              String name, Supplier<Vector4i> value, ValueUpdateNotifier notifier) {
            this.injectDynamicUniformType(
                name, UniformType.VEC4I, offset -> ptr -> putVec4i(ptr + offset, value.get()));
            return this;
          }

          @Override
          public DynamicLocationalUniformHolder uniformMatrix(
              String name, Supplier<Matrix4fc> value, ValueUpdateNotifier notifier) {
            this.injectDynamicUniformType(
                name, UniformType.MAT4, offset -> ptr -> putMatrix4f(ptr + offset, value.get()));
            return this;
          }

          private void injectDynamicUniformType(
              String name, UniformType type, Long2ObjectFunction<LongConsumer> supplier) {
            for (String expected : patch.getUniformList()) {
              if (expected.equals(name)) {
                if (!seenUniforms.add(name)) {
                  throw new IllegalArgumentException("Already added uniform: " + name);
                }
                uniforms.add(new UniformWritingHolder(name, type, supplier));
                break;
              }
            }
          }

          @Override
          public DynamicLocationalUniformHolder addDynamicUniform(
              Uniform uniform, ValueUpdateNotifier valueUpdateNotifier) {
            throw new IllegalStateException("Type not implemented for uniform: " + uniform);
          }

          @Override
          public LocationalUniformHolder addUniform(
              UniformUpdateFrequency uniformUpdateFrequency, Uniform uniform) {
            return this;
          }

          @Override
          public OptionalInt location(String uniformName, UniformType uniformType) {
            String[] names = patch.getUniformList();
            for (int i = 0; i < names.length; i++) {
              if (names[i].equals(uniformName)) {
                return OptionalInt.of(i);
              }
            }
            return OptionalInt.empty();
          }

          @Override
          public UniformHolder externallyManagedUniform(String name, UniformType uniformType) {
            return null;
          }
        };

    IrisRenderingPipelineAccessor pipelineAccessor = (IrisRenderingPipelineAccessor) pipeline;
    // Replay Iris's complete built-in uniform registration, not only its dynamic subset. Camera
    // uniforms such as previousCameraPosition live in addNonDynamicUniforms; relying on the
    // optimized CustomUniforms graph silently dropped them when no ordinary shader-pack pass used
    // them, leaving the standalone Voxy translucent patch with undeclared identifiers.
    CommonUniforms.addNonDynamicUniforms(
        uniformBuilder,
        pipelineAccessor.getPack().getIdMap(),
        pipelineAccessor.getPackDirectives(),
        pipeline.getFrameUpdateNotifier());
    CommonUniforms.addDynamicUniforms(uniformBuilder, FogMode.PER_FRAGMENT);
    customUniforms.assignTo(uniformBuilder);
    customUniforms.mapholderToPass(uniformBuilder, patch);

    FunctionReturn cachedReturn = new FunctionReturn();
    var locationMap = ((CustomUniformsAccessor) customUniforms).getLocationMap().get(patch);
    if (locationMap != null) {
      locationMap
          .object2IntEntrySet()
          .forEach(
              entry -> {
                String name = entry.getKey().getName();
                if (!seenUniforms.add(name)) {
                  // Already registered above: vx* via VoxyUniforms, fog/etc via addDynamicUniforms.
                  // The value source is identical (the global mixin reuses the same suppliers), so
                  // keep the first registration and skip this CustomUniforms duplicate rather than
                  // aborting the whole strict bridge.
                  return;
                }
                uniforms.add(
                    new UniformWritingHolder(
                        name,
                        Type.convert(entry.getKey().getType()),
                        offset -> createWriter(offset, cachedReturn, entry.getKey())));
              });
    }

    if (uniforms.size() != patch.getUniformList().length) {
      Set<String> uniformsUnseen = new HashSet<>(List.of(patch.getUniformList()));
      for (var uniform : uniforms) {
        uniformsUnseen.remove(uniform.name);
      }
      // These are pack uniforms the bridge has no value-supplier for: host built-ins that only
      // CommonUniforms.addNonDynamicUniforms (NOT addDynamicUniforms) would provide AND that no
      // normal Iris pass references (so the CustomUniforms replay never captures them either).
      // The canonical example is framemod8 (frameCounter % 8, used only by voxy.json's taaOffset
      // sub-pixel jitter): it stays 0, which only flattens TAA jitter and is harmless.
      // voxy-fabric's
      // createUniformSet hits the exact same gap, so this is expected, not a regression. We must
      // NOT
      // force them into the std140 block: the patched source already declares them, so a second
      // declaration would be a duplicate-symbol link failure on Apple GL4.1. Just deduplicate the
      // diagnostic (build() runs ~20x per pack load, once per queried program slot) so it does not
      // spam an identical ERROR every slot; a genuinely new missing-set still logs on pack reload.
      String message =
          "The following Voxy/Iris uniforms could not be found (left unfed, matches voxy-fabric; "
              + "framemod8 etc. only affect TAA jitter): ["
              + uniformsUnseen.stream()
                  .sorted(String::compareToIgnoreCase)
                  .collect(Collectors.joining(","))
              + "]";
      if (LOGGED_MISSING_UNIFORMS.add(message)) {
        Logger.warn(message);
      }
    }
    return uniforms;
  }

  private record TextureWithSampler(
      String name, TextureType type, IntSupplier texture, IntSupplier sampler) {}

  private record ImageSet(
      String layout, IntConsumer bindingFunction, String[] samplerNames, int[] samplerTargets) {}

  private static ImageSet createImageSet(IrisRenderingPipeline pipeline, IrisShaderPatch patch) {
    var samplerDataSet = patch.getSamplerSet();
    if (samplerDataSet == null || samplerDataSet.isEmpty()) {
      return null;
    }
    Set<String> samplerNameSet = new LinkedHashSet<>(samplerDataSet.keySet());
    Set<TextureWithSampler> samplerSet = new LinkedHashSet<>();
    SamplerHolder samplerBuilder =
        new SamplerHolder() {
          @Override
          public boolean hasSampler(String name) {
            return samplerNameSet.contains(name);
          }

          public boolean hasSampler(String... names) {
            for (String name : names) {
              if (samplerNameSet.contains(name)) {
                return true;
              }
            }
            return false;
          }

          private String name(String... names) {
            for (String name : names) {
              if (samplerNameSet.contains(name)) {
                return name;
              }
            }
            return null;
          }

          @Override
          public boolean addDefaultSampler(
              TextureType type,
              IntSupplier texture,
              ValueUpdateNotifier notifier,
              GlSampler sampler,
              String... names) {
            return false;
          }

          @Override
          public boolean addDynamicSampler(
              TextureType type, IntSupplier texture, GlSampler sampler, String... names) {
            return this.addDynamicSampler(type, texture, null, sampler, names);
          }

          @Override
          public boolean addDynamicSampler(
              TextureType type,
              IntSupplier texture,
              ValueUpdateNotifier notifier,
              GlSampler sampler,
              String... names) {
            if (!this.hasSampler(names)) {
              return false;
            }
            samplerSet.add(
                new TextureWithSampler(
                    this.name(names), type, texture, sampler != null ? sampler::getId : () -> -1));
            return true;
          }

          @Override
          public void addExternalSampler(int texture, String... names) {
            if (this.hasSampler(names)) {
              samplerSet.add(
                  new TextureWithSampler(
                      this.name(names), TextureType.TEXTURE_2D, () -> texture, () -> -1));
            }
          }
        };

    ImageHolder imageBuilder =
        new ImageHolder() {
          @Override
          public boolean hasImage(String name) {
            return false;
          }

          @Override
          public void addTextureImage(
              IntSupplier texture, InternalTextureFormat internalTextureFormat, String name) {}
        };

    pipeline.addGbufferOrShadowSamplers(
        samplerBuilder, imageBuilder, pipeline::getFlippedAfterPrepare, false, true, true, false);

    if (samplerSet.size() != samplerNameSet.size()) {
      throw new IllegalStateException(
          "did not find all requested Iris samplers; found ["
              + samplerSet.stream().map(TextureWithSampler::name).collect(Collectors.joining(", "))
              + "] expected "
              + samplerNameSet);
    }

    StringBuilder header = new StringBuilder();
    TextureWithSampler[] samplers = samplerSet.toArray(TextureWithSampler[]::new);
    // The direct shader declares one block-atlas sampler plus every shader-pack sampler. Apple's
    // GL 4.1 driver does not merely return a
    // link error when a fragment program uses too many samplers: it SIGSEGVs inside glLinkProgram
    // (glpLLVMGetFunctionGlobalVariableUse). Empirically it crashes at *exactly*
    // GL_MAX_TEXTURE_IMAGE_UNITS (16 on Apple Silicon), not just above it, so the usable budget is
    // maxTextureUnits - 1 (= 15). Reject the build cleanly above that and let the caller skip the
    // direct path rather than taking down the process.
    int maxTextureUnits = glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS);
    int usableTextureUnits = maxTextureUnits - 1;
    int requiredTextureUnits = SAMPLER_BINDING_BASE + samplers.length;
    String budgetMessage =
        "Voxy GL41Metal Iris bridge sampler budget: "
            + samplers.length
            + " pack samplers + "
            + SAMPLER_BINDING_BASE
            + " Voxy atlas = "
            + requiredTextureUnits
            + " / "
            + usableTextureUnits
            + " usable (of "
            + maxTextureUnits
            + " hw) fragment texture units ["
            + java.util.Arrays.stream(samplers)
                .map(TextureWithSampler::name)
                .collect(Collectors.joining(", "))
            + "]";
    // Iris invokes IrisBridgeShaderBindings.build() once per program slot it queries voxy for
    // (gbuffers_terrain / entities / translucent / block / hand / deferred1..N / composite1..N /
    // final etc.), all with the same sampler set and budget, which spammed ~20 identical INFO
    // lines at every world load. Dedup against the exact message string so a real budget change
    // (pack swap, /shader reload) re-logs naturally without us needing to track program-slot
    // identity here.
    if (LOGGED_SAMPLER_BUDGETS.add(budgetMessage)) {
      Logger.info(budgetMessage);
    }
    if (requiredTextureUnits > usableTextureUnits) {
      throw new IllegalStateException(
          "shader pack needs "
              + samplers.length
              + " samplers ("
              + requiredTextureUnits
              + " fragment texture units including "
              + SAMPLER_BINDING_BASE
              + " Voxy atlas sampler) but Apple GL4.1 only safely allows "
              + usableTextureUnits
              + " (of "
              + maxTextureUnits
              + "; the driver crashes at exactly the max)");
    }
    String[] samplerNames = new String[samplers.length];
    int[] samplerTargets = new int[samplers.length];
    for (int i = 0; i < samplers.length; i++) {
      String samplerType = samplerDataSet.get(samplers[i].name);
      samplerNames[i] = samplers[i].name;
      samplerTargets[i] = samplers[i].type.getGlType();
      // GL 4.1 / GLSL 410 (macOS) does not support layout(binding=...); the GL46 form
      // layout(binding=(BASE_SAMPLER_BINDING_INDEX+i)) fails to compile here. Declare a plain
      // sampler
      // uniform and assign its texture unit with glProgramUniform1i in programSetup (the per-frame
      // binder below binds the matching texture to SAMPLER_BINDING_BASE + i).
      header
          .append("uniform ")
          .append(samplerType)
          .append(" ")
          .append(samplers[i].name)
          .append(";\n");
    }

    return new ImageSet(
        header.toString(),
        base -> {
          for (int i = 0; i < samplers.length; i++) {
            int unit = base + i;
            TextureWithSampler sampler = samplers[i];
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(sampler.type.getGlType(), sampler.texture.getAsInt());
            int samplerId = sampler.sampler.getAsInt();
            glBindSampler(unit, samplerId == -1 ? 0 : samplerId);
          }
        },
        samplerNames,
        samplerTargets);
  }

  private record SSBOSet(String layout, IntConsumer bindingFunction) {}

  private record SSBOBinding(int irisIndex, int bindingOffset) {}

  private static SSBOSet createSSBOLayouts(
      Int2ObjectMap<String> ssbos, ShaderStorageBufferHolder ssboStore) {
    if (ssboStore == null || ssbos.isEmpty()) {
      return null;
    }
    String header = "";
    if (ssbos.containsKey(-1)) {
      header = ssbos.remove(-1);
    }
    StringBuilder builder = new StringBuilder(header);
    builder.append("\n");
    SSBOBinding[] bindings = new SSBOBinding[ssbos.size()];
    int i = 0;
    for (var entry : ssbos.int2ObjectEntrySet()) {
      bindings[i] = new SSBOBinding(entry.getIntKey(), i);
      builder
          .append("layout(binding = (BUFFER_BINDING_INDEX_BASE+")
          .append(i)
          .append(")) restrict buffer IrisBufferBinding")
          .append(i)
          .append(" ")
          .append(entry.getValue())
          .append(";\n");
      i++;
    }
    return new SSBOSet(
        builder.toString(),
        base -> {
          for (SSBOBinding binding : bindings) {
            glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER,
                base + binding.bindingOffset,
                ssboStore.getBufferIndex(binding.irisIndex));
          }
        });
  }
}
