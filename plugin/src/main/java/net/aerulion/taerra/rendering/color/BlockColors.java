package net.aerulion.taerra.rendering.color;

import org.jetbrains.annotations.Nullable;

public interface BlockColors {

  int topColor(@Nullable TintColors tint);

  int sideColor(@Nullable TintColors tint);

  default boolean emitsLight() {
    return false;
  }
}
