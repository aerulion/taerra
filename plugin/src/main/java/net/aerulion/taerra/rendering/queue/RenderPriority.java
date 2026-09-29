package net.aerulion.taerra.rendering.queue;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum RenderPriority {
  LIVE_ZOOM_0(true, true, 0),
  FULL_RENDER_ZOOM_0(true, false, 1),
  LIVE_ZOOM_PARENT(false, true, 0),
  FULL_RENDER_ZOOM_PARENT(false, false, 1);

  private final boolean zoom0;
  private final boolean live;
  private final int orderWithinLane;

  RenderPriority(final boolean zoom0, final boolean live, final int orderWithinLane) {
    this.zoom0 = zoom0;
    this.live = live;
    this.orderWithinLane = orderWithinLane;
  }

  public static @Nullable RenderPriority higherPriority(
      final @Nullable RenderPriority first, final @Nullable RenderPriority second) {
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    if (first.zoom0 != second.zoom0) {
      throw new IllegalArgumentException("Cannot compare render priorities from different lanes");
    }
    return first.orderWithinLane <= second.orderWithinLane ? first : second;
  }

  public @NotNull RenderPriority parentPriority() {
    return this.live ? LIVE_ZOOM_PARENT : FULL_RENDER_ZOOM_PARENT;
  }

  public boolean isZoom0() {
    return this.zoom0;
  }

  public boolean isLive() {
    return this.live;
  }

  public boolean outranks(final @Nullable RenderPriority other) {
    if (other == null) {
      return true;
    }
    if (this.zoom0 != other.zoom0) {
      throw new IllegalArgumentException("Cannot compare render priorities from different lanes");
    }
    return this.orderWithinLane < other.orderWithinLane;
  }
}
