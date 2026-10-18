package net.aerulion.taerra.region;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import net.aerulion.taerra.util.AtomicFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RegionDiscoveries {

  private final @NotNull Plugin plugin;
  private final @NotNull File file;
  private final @NotNull Map<UUID, IntSet> discovered = new ConcurrentHashMap<>();
  private final @NotNull AtomicBoolean saving = new AtomicBoolean();

  public RegionDiscoveries(final @NotNull Plugin plugin) {
    super();
    this.plugin = plugin;
    this.file = new File(plugin.getDataFolder(), "discoveries.yml");
    final @Nullable ConfigurationSection players =
        YamlConfiguration.loadConfiguration(this.file).getConfigurationSection("players");
    if (players == null) {
      return;
    }
    for (final @NotNull String key : players.getKeys(false)) {
      try {
        final @NotNull IntSet regions = new IntOpenHashSet();
        for (final int region : players.getIntegerList(key)) {
          regions.add(region);
        }
        this.discovered.put(UUID.fromString(key), IntSets.unmodifiable(regions));
      } catch (final IllegalArgumentException e) {
        plugin.getLogger().warning("Skipping malformed discoveries for " + key);
      }
    }
  }

  public @NotNull IntSet of(final @NotNull UUID player) {
    return this.discovered.getOrDefault(player, IntSets.EMPTY_SET);
  }

  public boolean has(final @NotNull UUID player, final int region) {
    return of(player).contains(region);
  }

  public boolean discover(final @NotNull UUID player, final int region) {
    final boolean[] added = {false};
    this.discovered.compute(
        player,
        (_, known) -> {
          if (known != null && known.contains(region)) {
            return known;
          }
          final @NotNull IntSet next =
              known == null ? new IntOpenHashSet() : new IntOpenHashSet(known);
          next.add(region);
          added[0] = true;
          return IntSets.unmodifiable(next);
        });
    if (added[0]) {
      saveLater();
    }
    return added[0];
  }

  public void forget(final int region) {
    boolean changed = false;
    for (final @NotNull UUID player : this.discovered.keySet()) {
      final @Nullable IntSet known = this.discovered.get(player);
      if (known != null && known.contains(region)) {
        final @NotNull IntSet next = new IntOpenHashSet(known);
        next.remove(region);
        this.discovered.put(player, IntSets.unmodifiable(next));
        changed = true;
      }
    }
    if (changed) {
      saveLater();
    }
  }

  private void saveLater() {
    if (this.saving.compareAndSet(false, true)) {
      this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, this::save);
    }
  }

  public void save() {
    this.saving.set(false);
    final @NotNull YamlConfiguration yaml = new YamlConfiguration();
    this.discovered.forEach(
        (player, regions) -> {
          if (!regions.isEmpty()) {
            yaml.set("players." + player, regions.intStream().sorted().boxed().toList());
          }
        });
    try {
      AtomicFiles.write(this.file, yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    } catch (final IOException e) {
      this.plugin.getLogger().log(Level.WARNING, "Failed to save discoveries.yml", e);
    }
  }
}
