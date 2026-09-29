package net.aerulion.taerra.rendering.tile;

import net.aerulion.taerra.model.TileCoordinate;
import org.jetbrains.annotations.NotNull;

public final class TileHierarchy {

  private TileHierarchy() {
    super();
  }

  public static int childIndex(final @NotNull TileCoordinate child) {
    return (Math.floorMod(child.z(), 2) << 1) | Math.floorMod(child.x(), 2);
  }

  public static TileCoordinate @NotNull [] childrenOf(final @NotNull TileCoordinate parent) {
    final int childBaseX = parent.x() * 2;
    final int childBaseZ = parent.z() * 2;
    final int childZoom = parent.zoom() - 1;
    return new TileCoordinate[] {
      parent.at(childZoom, childBaseX, childBaseZ),
      parent.at(childZoom, childBaseX + 1, childBaseZ),
      parent.at(childZoom, childBaseX, childBaseZ + 1),
      parent.at(childZoom, childBaseX + 1, childBaseZ + 1)
    };
  }

  public static @NotNull TileCoordinate parentOf(final @NotNull TileCoordinate child) {
    return child.at(child.zoom() + 1, Math.floorDiv(child.x(), 2), Math.floorDiv(child.z(), 2));
  }
}
