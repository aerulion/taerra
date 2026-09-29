package net.aerulion.taerra.mod.web.model;

public record MapConfig(int tileSize, int maxZoom, Isometric isometric) {

  public record Isometric(int tileSize, int maxZoom, int blockWidth) {}
}
