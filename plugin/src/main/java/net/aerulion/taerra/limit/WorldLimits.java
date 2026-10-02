package net.aerulion.taerra.limit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.aerulion.taerra.model.ChunkCoordinate;
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
  private final @NotNull Map<String, Set<Long>> hidden = new ConcurrentHashMap<>();

  public WorldLimits(final @NotNull Plugin plugin) {
    super();
    this.plugin = plugin;
    this.file = new File(plugin.getDataFolder(), "limits.yml");
    final @NotNull YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
    for (final @NotNull Map<?, ?> entry : yaml.getMapList("limits")) {
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
    for (final @NotNull Map<?, ?> entry : yaml.getMapList("hidden")) {
      try {
        final @NotNull Set<Long> chunks = new HashSet<>();
        for (final @NotNull Object chunk : (List<?>) entry.get("chunks")) {
          final @NotNull String[] xz = String.valueOf(chunk).split(",");
          chunks.add(
              ChunkCoordinate.key(
                  Integer.parseInt(xz[0].strip()), Integer.parseInt(xz[1].strip())));
        }
        this.hidden.put((String) entry.get("world"), Set.copyOf(chunks));
      } catch (final RuntimeException e) {
        plugin.getLogger().warning("Skipping malformed hidden chunks in limits.yml: " + entry);
      }
    }
  }

  public @Nullable WorldLimit get(final @NotNull String worldName) {
    return this.limits.get(worldName);
  }

  public @NotNull Map<String, Set<Long>> hidden() {
    return Map.copyOf(this.hidden);
  }

  public boolean hidden(final @NotNull String worldName, final int chunkX, final int chunkZ) {
    final @Nullable Set<Long> chunks = this.hidden.get(worldName);
    return chunks != null && chunks.contains(ChunkCoordinate.key(chunkX, chunkZ));
  }

  public synchronized void set(final @NotNull String worldName, final @Nullable WorldLimit limit) {
    if (limit == null) {
      this.limits.remove(worldName);
    } else {
      this.limits.put(worldName, limit);
    }
    save();
  }

  public synchronized void setHidden(
      final @NotNull String worldName, final @NotNull Collection<Long> chunks, final boolean hide) {
    final @NotNull Set<Long> updated = new HashSet<>(this.hidden.getOrDefault(worldName, Set.of()));
    if (hide) {
      updated.addAll(chunks);
    } else {
      updated.removeAll(chunks);
    }
    if (updated.isEmpty()) {
      this.hidden.remove(worldName);
    } else {
      this.hidden.put(worldName, Set.copyOf(updated));
    }
    save();
  }

  private void save() {
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
    final @NotNull List<Map<String, Object>> hidden = new ArrayList<>();
    this.hidden.forEach(
        (world, chunks) ->
            hidden.add(
                Map.of(
                    "world",
                    world,
                    "chunks",
                    chunks.stream()
                        .sorted()
                        .map(
                            chunk ->
                                ChunkCoordinate.keyX(chunk) + "," + ChunkCoordinate.keyZ(chunk))
                        .toList())));
    yaml.set("hidden", hidden);
    try {
      AtomicFiles.write(this.file, yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    } catch (final IOException e) {
      this.plugin.getLogger().log(Level.SEVERE, "Failed to save limits.yml", e);
    }
  }
}
