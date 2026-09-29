package net.aerulion.taerra.mod.web.model;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Player(
    @Nullable String name,
    String uuid,
    String world,
    double x,
    double y,
    double z,
    float yaw,
    String color,
    boolean hidden,
    boolean anonymous) {

  public @NotNull Component displayName() {
    return this.name == null
        ? Component.translatable("taerra.players.anonymous")
        : Component.literal(this.name);
  }

  public @NotNull Component coordinates() {
    return Component.literal(
        (long) Math.floor(this.x)
            + ", "
            + (long) Math.floor(this.y)
            + ", "
            + (long) Math.floor(this.z));
  }
}
