package net.aerulion.taerra.mod.tile;

import net.aerulion.taerra.mod.util.Urls;
import org.jetbrains.annotations.NotNull;

public record TileKey(String world, boolean isometric, int rotation, int zoom, int x, int y) {

  public @NotNull TileKey parent() {
    return new TileKey(
        this.world,
        this.isometric,
        this.rotation,
        this.zoom - 1,
        Math.floorDiv(this.x, 2),
        Math.floorDiv(this.y, 2));
  }

  public @NotNull TileKey child(final int dx, final int dy) {
    return new TileKey(
        this.world, this.isometric, this.rotation, this.zoom + 1, 2 * this.x + dx, 2 * this.y + dy);
  }

  public @NotNull String path() {
    final @NotNull String world = Urls.segment(this.world);
    return this.isometric
        ? "/api/iso/%s/%d/%d/%d/%d.png".formatted(world, this.rotation, this.zoom, this.x, this.y)
        : "/api/tiles/%s/%d/%d/%d.png".formatted(world, this.zoom, this.x, this.y);
  }
}
