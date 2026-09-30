package net.aerulion.taerra.mod.web.model;

import org.jetbrains.annotations.Nullable;

/** {@code name} is the server's name from {@code web.name}, missing when none is set. */
public record MapConfig(int tileSize, int maxZoom, Isometric isometric, @Nullable String name) {

  public record Isometric(int tileSize, int maxZoom, int blockWidth) {}
}
