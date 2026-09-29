package net.aerulion.taerra.model;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WorldKeys {

  private WorldKeys() {
    super();
  }

  public static @NotNull String id(final @NotNull World world) {
    return world.getKey().asString();
  }

  public static @Nullable World resolve(final @NotNull String id) {
    final @Nullable NamespacedKey key = NamespacedKey.fromString(id);
    return key == null ? null : Bukkit.getWorld(key);
  }

  public static @NotNull String displayName(final @NotNull NamespacedKey key) {
    final @NotNull StringBuilder name = new StringBuilder();
    for (final @NotNull String word : key.getKey().split("[_/]+")) {
      if (word.isEmpty()) {
        continue;
      }
      if (!name.isEmpty()) {
        name.append(' ');
      }
      name.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
    }
    return name.toString();
  }
}
