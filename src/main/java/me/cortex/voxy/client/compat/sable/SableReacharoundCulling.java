package me.cortex.voxy.client.compat.sable;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.joml.Vector3d;

/**
 * Culls Sable sub levels that are further away than the simulated contraption render distance
 * (Sable itself only culls at a fixed radius).
 */
public final class SableReacharoundCulling {
  private static final double HYSTERESIS_BLOCKS = 32.0D;

  private SableReacharoundCulling() {}

  public static Iterable<ClientSubLevel> filter(
      Iterable<ClientSubLevel> subLevels, double cameraX, double cameraZ) {
    if (!SableClientRenderDistance.isVoxyRenderDistanceActive()) {
      return subLevels;
    }

    int vanillaRenderDistanceChunks = Minecraft.getInstance().options.getEffectiveRenderDistance();
    double renderDistanceBlocks =
        SableClientRenderDistance.getRenderDistanceBlocks(vanillaRenderDistanceChunks)
            + HYSTERESIS_BLOCKS;
    if (!Double.isFinite(renderDistanceBlocks) || renderDistanceBlocks <= 0.0D) {
      return subLevels;
    }

    List<ClientSubLevel> visibleSubLevels = new ArrayList<>();
    for (ClientSubLevel subLevel : subLevels) {
      if (isInRenderDistance(subLevel, cameraX, cameraZ, renderDistanceBlocks)) {
        visibleSubLevels.add(subLevel);
      }
    }
    return visibleSubLevels;
  }

  private static boolean isInRenderDistance(
      ClientSubLevel subLevel, double cameraX, double cameraZ, double renderDistanceBlocks) {
    BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
    if (bounds == null) {
      return true;
    }

    Vector3d center =
        new Vector3d(
            (bounds.minX() + bounds.maxX() + 1) * 0.5D,
            (bounds.minY() + bounds.maxY() + 1) * 0.5D,
            (bounds.minZ() + bounds.maxZ() + 1) * 0.5D);
    subLevel.renderPose().transformPosition(center);

    double halfWidth = Math.max(0.5D, bounds.width() * 0.5D);
    double halfLength = Math.max(0.5D, bounds.length() * 0.5D);
    double horizontalRadius = Math.sqrt(halfWidth * halfWidth + halfLength * halfLength);

    double dx = center.x - cameraX;
    double dz = center.z - cameraZ;
    return (dx * dx) + (dz * dz)
        <= (renderDistanceBlocks + horizontalRadius) * (renderDistanceBlocks + horizontalRadius);
  }
}
