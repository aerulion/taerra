package net.aerulion.taerra.player;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Navigation(
    @NotNull String label,
    @NotNull String world,
    double x,
    @Nullable Double y,
    double z,
    @NotNull String color,
    @Nullable String player) {

  static @Nullable Navigation read(final @Nullable ConfigurationSection section) {
    if (section == null) {
      return null;
    }
    final @Nullable String label = section.getString("label");
    final @Nullable String world = section.getString("world");
    final @Nullable String color = section.getString("color");
    if (label == null || world == null || color == null) {
      return null;
    }
    return new Navigation(
        label,
        world,
        section.getDouble("x"),
        section.isSet("y") ? section.getDouble("y") : null,
        section.getDouble("z"),
        color,
        section.getString("player"));
  }

  @NotNull
  Map<String, Object> entry() {
    final @NotNull Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("label", this.label);
    entry.put("world", this.world);
    entry.put("x", this.x);
    if (this.y != null) {
      entry.put("y", this.y);
    }
    entry.put("z", this.z);
    entry.put("color", this.color);
    if (this.player != null) {
      entry.put("player", this.player);
    }
    return entry;
  }
}
