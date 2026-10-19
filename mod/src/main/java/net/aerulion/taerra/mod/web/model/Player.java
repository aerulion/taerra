package net.aerulion.taerra.mod.web.model;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Player(
    @Nullable String name,
    String uuid,
    @Nullable String world,
    @Nullable Double x,
    @Nullable Double y,
    @Nullable Double z,
    float yaw,
    String color,
    boolean hidden,
    boolean anonymous) {

  public @NotNull Component displayName() {
    return this.name == null
        ? Component.translatable("taerra.players.anonymous")
        : Component.literal(this.name);
  }

  public boolean located() {
    return this.world != null && this.x != null && this.y != null && this.z != null;
  }

  public @NotNull Component coordinates() {
    if (!located()) {
      return Component.translatable("taerra.players.unlocated");
    }
    return Component.literal(
        (long) Math.floor(this.x)
            + ", "
            + (long) Math.floor(this.y)
            + ", "
            + (long) Math.floor(this.z));
  }
}
