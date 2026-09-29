package net.aerulion.taerra.mod.map;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Place(int x, @Nullable Integer y, int z) {

  public @Nullable Integer standing() {
    return this.y == null ? null : this.y + 1;
  }

  public @NotNull Component describe() {
    return Component.literal(
        this.y == null ? this.x + ", " + this.z : this.x + ", " + this.y + ", " + this.z);
  }

  public @NotNull String coordinates() {
    return this.y == null ? this.x + " " + this.z : this.x + " " + this.y + " " + this.z;
  }
}
