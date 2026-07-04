package me.cortex.voxy.client.core.model.bakery;

import static me.cortex.voxy.client.core.model.BakedModelPayload.FALLBACK_FLUID_ONLY;
import static me.cortex.voxy.client.core.model.BakedModelPayload.FALLBACK_GENERAL_QUADS;
import static me.cortex.voxy.client.core.model.BakedModelPayload.FALLBACK_INVISIBLE;
import static me.cortex.voxy.client.core.model.BakedModelPayload.FALLBACK_MISSING_SPRITE;
import static me.cortex.voxy.client.core.model.BakedModelPayload.FALLBACK_NONE;
import static me.cortex.voxy.client.core.model.BakedModelPayload.FALLBACK_NO_QUADS;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import me.cortex.voxy.client.core.model.ColourDepthTextureData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;

public final class CpuModelTextureBakery {
  public record Result(
      ColourDepthTextureData[] faces,
      boolean shaded,
      boolean darkenedTextures,
      int fallbackReason) {
    public boolean fallback() {
      return this.fallbackReason != FALLBACK_NONE;
    }
  }

  private final int size;

  public CpuModelTextureBakery(int size) {
    this.size = size;
  }

  public Result bake(BlockState state) {
    ColourDepthTextureData[] faces = new ColourDepthTextureData[6];
    for (int i = 0; i < faces.length; i++) {
      faces[i] = emptyFace();
    }

    if (state.getRenderShape() == RenderShape.INVISIBLE) {
      return new Result(faces, false, false, FALLBACK_INVISIBLE);
    }
    if (state.getBlock() instanceof LiquidBlock) {
      return new Result(faces, false, false, FALLBACK_FLUID_ONLY);
    }

    RenderType layer =
        state.getBlock() instanceof LeavesBlock
            ? RenderType.solid()
            : ItemBlockRenderTypes.getChunkRenderType(state);
    int meta = ModelTextureBakery.getMetaFromLayer(layer);
    var model =
        Minecraft.getInstance().getModelManager().getBlockModelShaper().getBlockModel(state);
    RandomSource random = new SingleThreadedRandomSource(42L);
    boolean shaded = false;
    boolean hasDirectionalQuads = false;

    for (Direction direction : Direction.values()) {
      List<BakedQuad> quads = model.getQuads(state, direction, random);
      if (!quads.isEmpty()) {
        hasDirectionalQuads = true;
        FaceBake face = bakeFace(quads, meta);
        faces[direction.get3DDataValue()] = face.texture;
        shaded |= face.shaded;
      }
    }

    if (hasDirectionalQuads) {
      boolean missingSprite =
          bakeDirectedGeneralQuads(model.getQuads(state, null, random), faces, meta);
      return new Result(
          faces, shaded, false, missingSprite ? FALLBACK_MISSING_SPRITE : FALLBACK_NONE);
    }

    List<BakedQuad> fallbackQuads = new ArrayList<>(model.getQuads(state, null, random));
    if (fallbackQuads.isEmpty()) {
      return new Result(faces, shaded, false, FALLBACK_NO_QUADS);
    }

    Map<Direction, List<BakedQuad>> directedQuads = groupByDirection(fallbackQuads);
    if (!directedQuads.isEmpty()) {
      boolean missingSprite = false;
      for (Map.Entry<Direction, List<BakedQuad>> entry : directedQuads.entrySet()) {
        FaceBake face = bakeFace(entry.getValue(), meta);
        faces[entry.getKey().get3DDataValue()] = face.texture;
        shaded |= face.shaded;
        missingSprite |= face.missingSprite;
      }
      return new Result(
          faces, shaded, false, missingSprite ? FALLBACK_MISSING_SPRITE : FALLBACK_NONE);
    }

    FaceBake fallback = bakeFace(fallbackQuads, meta);
    for (int i = 0; i < faces.length; i++) {
      faces[i] = fallback.texture.clone();
    }
    return new Result(
        faces,
        fallback.shaded,
        false,
        fallback.missingSprite ? FALLBACK_MISSING_SPRITE : FALLBACK_GENERAL_QUADS);
  }

  private boolean bakeDirectedGeneralQuads(
      List<BakedQuad> quads, ColourDepthTextureData[] faces, int meta) {
    if (quads.isEmpty()) {
      return false;
    }
    Map<Direction, List<BakedQuad>> directedQuads = groupByDirection(quads);
    boolean missingSprite = false;
    for (Map.Entry<Direction, List<BakedQuad>> entry : directedQuads.entrySet()) {
      FaceBake face = bakeFace(entry.getValue(), meta);
      faces[entry.getKey().get3DDataValue()] = face.texture;
      missingSprite |= face.missingSprite;
    }
    return missingSprite;
  }

  private static Map<Direction, List<BakedQuad>> groupByDirection(List<BakedQuad> quads) {
    Map<Direction, List<BakedQuad>> grouped = new EnumMap<>(Direction.class);
    for (BakedQuad quad : quads) {
      Direction direction = quad.getDirection();
      if (direction != null) {
        grouped.computeIfAbsent(direction, ignored -> new ArrayList<>()).add(quad);
      }
    }
    return grouped;
  }

  private FaceBake bakeFace(List<BakedQuad> quads, int meta) {
    int[] colour = new int[this.size * this.size];
    int[] depth = new int[this.size * this.size];
    boolean shaded = false;
    boolean missingSprite = false;
    for (BakedQuad quad : quads) {
      shaded |= quad.isShade();
      missingSprite |= !blitQuadSprite(quad, colour, depth, meta | (quad.isTinted() ? 4 : 0));
    }
    return new FaceBake(
        new ColourDepthTextureData(colour, depth, this.size, this.size), shaded, missingSprite);
  }

  private boolean blitQuadSprite(BakedQuad quad, int[] colour, int[] depth, int meta) {
    TextureAtlasSprite sprite = quad.getSprite();
    if (sprite == null) {
      return false;
    }

    float minU = 1.0f;
    float maxU = 0.0f;
    float minV = 1.0f;
    float maxV = 0.0f;
    int[] vertices = quad.getVertices();
    for (int vertex = 0; vertex < 4; vertex++) {
      int base = vertex * 8;
      float u = sprite.getUOffset(Float.intBitsToFloat(vertices[base + 4]));
      float v = sprite.getVOffset(Float.intBitsToFloat(vertices[base + 5]));
      minU = Math.min(minU, u);
      maxU = Math.max(maxU, u);
      minV = Math.min(minV, v);
      maxV = Math.max(maxV, v);
    }

    if (maxU <= minU || maxV <= minV) {
      minU = 0.0f;
      maxU = 1.0f;
      minV = 0.0f;
      maxV = 1.0f;
    }

    int spriteWidth = sprite.contents().width();
    int spriteHeight = sprite.contents().height();
    for (int y = 0; y < this.size; y++) {
      float v = lerp(minV, maxV, (y + 0.5f) / this.size);
      int sy = Math.clamp((int) (v * spriteHeight), 0, spriteHeight - 1);
      for (int x = 0; x < this.size; x++) {
        float u = lerp(minU, maxU, (x + 0.5f) / this.size);
        int sx = Math.clamp((int) (u * spriteWidth), 0, spriteWidth - 1);
        int idx = x + y * this.size;
        // NeoForge exposes sprite.getPixelRGBA(frame, x, y); on Fabric read frame 0 straight from
        // the sprite's original image (frame 0 sits at the image origin).
        int pixel = sprite.contents().originalImage.getPixelRGBA(sx, sy);
        if (shouldDiscardTransparentPixel(pixel, meta)) {
          continue;
        }
        colour[idx] = pixel;
        if (((pixel >>> 24) & 0xFF) > 1) {
          depth[idx] = writtenDepthMetadata(meta);
        }
      }
    }
    return true;
  }

  private static boolean shouldDiscardTransparentPixel(int pixel, int meta) {
    return (meta & 1) != 0 && ((pixel >>> 24) & 0xFF) <= 1;
  }

  private static int writtenDepthMetadata(int meta) {
    int stencil = 1;
    if ((meta & 4) != 0) {
      stencil |= 1 << 7;
    }
    return stencil | (1 << 8);
  }

  private ColourDepthTextureData emptyFace() {
    return new ColourDepthTextureData(
        new int[this.size * this.size], new int[this.size * this.size], this.size, this.size);
  }

  private static float lerp(float min, float max, float t) {
    return min + (max - min) * t;
  }

  private record FaceBake(ColourDepthTextureData texture, boolean shaded, boolean missingSprite) {}
}
