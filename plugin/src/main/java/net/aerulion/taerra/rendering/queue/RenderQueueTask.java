package net.aerulion.taerra.rendering.queue;

import net.aerulion.taerra.model.TileCoordinate;

public record RenderQueueTask(TileCoordinate coordinate, RenderPriority priority) {}
