package net.aerulion.taerra.mod.map;

import java.util.List;
import org.jetbrains.annotations.NotNull;

record OverlayScene(
    long version,
    String world,
    Projection projection,
    List<OverlayItem> items,
    Look look,
    double pixels,
    int maxZoom) {

  boolean sameSpace(final @NotNull OverlayScene other) {
    return this.world.equals(other.world) && this.projection.equals(other.projection);
  }
}
