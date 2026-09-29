package net.aerulion.taerra.limit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.aerulion.taerra.model.WorldLimit;
import net.aerulion.taerra.util.AtomicFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WorldLimits {

  private final @NotNull Plugin plugin;
  private final @NotNull File file;
  private final @NotNull Map<String, WorldLimit> limits = new ConcurrentHashMap<>();

  public WorldLimits(final @NotNull Plugin plugin) {
    super();
    this.plugin = plugin;
    this.file = new File(plugin.getDataFolder(), "limits.yml");
    for (final @NotNull Map<?, ?> entry :
        YamlConfiguration.loadConfiguration(this.file).getMapList("limits")) {
      try {
        this.limits.put(
            (String) entry.get("world"),
            new WorldLimit(
                WorldLimit.Shape.valueOf(
                    String.valueOf(entry.get("shape")).toUpperCase(Locale.ROOT)),
                ((Number) entry.get("min-x")).intValue(),
                ((Number) entry.get("min-z")).intValue(),
                ((Number) entry.get("max-x")).intValue(),
                ((Number) entry.get("max-z")).intValue()));
      } catch (final RuntimeException e) {
        plugin.getLogger().warning("Skipping a malformed limit in limits.yml: " + entry);
      }
    }
  }

  public @Nullable WorldLimit get(final @NotNull String worldName) {
    return this.limits.get(worldName);
  }

  public synchronized void set(final @NotNull String worldName, final @Nullable WorldLimit limit) {
    if (limit == null) {
      this.limits.remove(worldName);
    } else {
      this.limits.put(worldName, limit);
    }
    final @NotNull List<Map<String, Object>> entries = new ArrayList<>();
    this.limits.forEach(
        (world, saved) ->
            entries.add(
                Map.of(
                    "world", world,
                    "shape", saved.shape().name().toLowerCase(Locale.ROOT),
                    "min-x", saved.minX(),
                    "min-z", saved.minZ(),
                    "max-x", saved.maxX(),
                    "max-z", saved.maxZ())));
    final @NotNull YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("limits", entries);
    try {
      AtomicFiles.write(this.file, yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    } catch (final IOException e) {
      this.plugin.getLogger().log(Level.SEVERE, "Failed to save limits.yml", e);
    }
  }
}
