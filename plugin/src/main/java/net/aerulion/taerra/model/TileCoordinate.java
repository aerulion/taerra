package net.aerulion.taerra.model;

import org.jetbrains.annotations.NotNull;

public record TileCoordinate(
    String world, MapView view, MapRotation rotation, int zoom, int x, int z) {

  public static @NotNull TileCoordinate topDown(
      final String world, final int zoom, final int x, final int z) {
    return new TileCoordinate(world, MapView.TOP_DOWN, MapRotation.R0, zoom, x, z);
  }

  public static @NotNull TileCoordinate isometric(
      final String world,
      final @NotNull MapRotation rotation,
      final int zoom,
      final int x,
      final int y) {
    return new TileCoordinate(world, MapView.ISOMETRIC, rotation, zoom, x, y);
  }

  public @NotNull TileCoordinate at(final int zoom, final int x, final int z) {
    return new TileCoordinate(this.world, this.view, this.rotation, zoom, x, z);
  }

  @Override
  public @NotNull String toString() {
    return "Tile["
        + this.world
        + ", "
        + this.view.id()
        + (this.view.rotated() ? ", rotation=" + this.rotation.id() : "")
        + ", zoom="
        + this.zoom
        + ", x="
        + this.x
        + ", z="
        + this.z
        + "]";
  }
}
