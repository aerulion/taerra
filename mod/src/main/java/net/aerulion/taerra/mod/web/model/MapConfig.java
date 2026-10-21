package net.aerulion.taerra.mod.web.model;

import org.jetbrains.annotations.Nullable;

public record MapConfig(
    int tileSize,
    int maxZoom,
    Isometric isometric,
    @Nullable String name,
    @Nullable String version) {

  public record Isometric(int tileSize, int maxZoom, int blockWidth) {}
}
