package net.aerulion.taerra.mod.util;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;

public final class Dimensions {

  private Dimensions() {}

  public static @NotNull String current() {
    final @NotNull Minecraft minecraft = Minecraft.getInstance();
    return minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString();
  }
}
