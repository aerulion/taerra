package net.aerulion.taerra.mod.web.model;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;

public record Sky(
    String phase, double progress, int time, int day, boolean advancing, String weather) {

  public @NotNull String clock() {
    final int time = (this.time + 6000) % 24000;
    return "%02d:%02d".formatted(time / 1000, time % 1000 * 60 / 1000);
  }

  public @NotNull Component phaseName() {
    return Component.translatable("taerra.sky." + this.phase);
  }

  public @NotNull Component describe() {
    final @NotNull Component separator = Component.literal(" · ");
    final @NotNull MutableComponent text = phaseName().copy();
    if (!"clear".equals(this.weather)) {
      text.append(separator)
          .append(Component.translatable("taerra.minimap.weather." + this.weather));
    }
    text.append(separator)
        .append(this.clock())
        .append(separator)
        .append(Component.translatable("taerra.minimap.day", this.day));
    if (!this.advancing) {
      text.append(separator).append(Component.translatable("taerra.sky.stopped"));
    }
    return text;
  }
}
