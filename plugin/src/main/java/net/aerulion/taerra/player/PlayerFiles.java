package net.aerulion.taerra.player;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import net.aerulion.taerra.util.AtomicFiles;
import net.aerulion.taerra.waypoint.PersonalWaypoints;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlayerFiles {

  private static final long IDLE_MINUTES = 10L;

  private final @NotNull Plugin plugin;
  private final @NotNull File directory;
  private final @NotNull Cache<UUID, PlayerData> loaded =
      Caffeine.newBuilder().expireAfterAccess(IDLE_MINUTES, TimeUnit.MINUTES).build();
  private final @NotNull Map<UUID, PlayerData> unsaved = new ConcurrentHashMap<>();
  private final @NotNull AtomicBoolean saving = new AtomicBoolean();

  public PlayerFiles(final @NotNull Plugin plugin) {
    super();
    this.plugin = plugin;
    this.directory = new File(plugin.getDataFolder(), "player-data");
    importLegacy(new File(plugin.getDataFolder(), "personal-waypoints"));
  }

  private void importLegacy(final @NotNull File legacy) {
    final @Nullable File @Nullable [] files = legacy.listFiles((_, name) -> name.endsWith(".yml"));
    if (files == null) {
      return;
    }
    this.directory.mkdirs();
    int moved = 0;
    for (final @Nullable File file : files) {
      final @NotNull File target = new File(this.directory, file.getName());
      if (target.exists()) {
        this.plugin
            .getLogger()
            .warning("Kept " + file + " in place, " + target + " already exists");
        continue;
      }
      try {
        Files.move(file.toPath(), target.toPath());
        moved++;
      } catch (final IOException e) {
        this.plugin.getLogger().log(Level.WARNING, "Failed to move " + file, e);
      }
    }
    legacy.delete();
    if (moved > 0) {
      this.plugin
          .getLogger()
          .info("Moved the personal waypoints of " + moved + " players into player-data");
    }
  }

  public @NotNull PlayerData get(final @NotNull UUID player) {
    return this.loaded.get(player, this::load);
  }

  public void update(final @NotNull UUID player, final @NotNull UnaryOperator<PlayerData> change) {
    if (change(player, change) && this.saving.compareAndSet(false, true)) {
      this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, this::saveAll);
    }
  }

  public boolean updateNow(
      final @NotNull UUID player, final @NotNull UnaryOperator<PlayerData> change) {
    change(player, change);
    return save(player);
  }

  private boolean change(
      final @NotNull UUID player, final @NotNull UnaryOperator<PlayerData> change) {
    final boolean[] changed = {false};
    this.loaded
        .asMap()
        .compute(
            player,
            (key, known) -> {
              final @NotNull PlayerData before = known == null ? load(key) : known;
              final @NotNull PlayerData after = change.apply(before);
              if (after != before) {
                this.unsaved.put(key, after);
                changed[0] = true;
              }
              return after;
            });
    return changed[0];
  }

  public void saveAll() {
    this.saving.set(false);
    for (final @NotNull UUID player : this.unsaved.keySet()) {
      save(player);
    }
  }

  private synchronized boolean save(final @NotNull UUID player) {
    final @Nullable PlayerData data = this.unsaved.get(player);
    if (data == null) {
      return true;
    }
    final @NotNull File file = file(player);
    try {
      if (data.isEmpty()) {
        Files.deleteIfExists(file.toPath());
      } else {
        final @NotNull YamlConfiguration yaml = new YamlConfiguration();
        if (!data.waypoints().isEmpty()) {
          yaml.set("waypoints", PersonalWaypoints.entries(data.waypoints()));
        }
        if (!data.discoveries().isEmpty()) {
          final @NotNull Map<String, Long> discoveries = new LinkedHashMap<>();
          data.discoveries().entrySet().stream()
              .sorted(Map.Entry.comparingByValue())
              .forEach(entry -> discoveries.put(entry.getKey().toString(), entry.getValue()));
          yaml.set("discoveries", discoveries);
        }
        AtomicFiles.write(file, yaml.saveToString().getBytes(StandardCharsets.UTF_8));
      }
    } catch (final IOException e) {
      this.plugin.getLogger().log(Level.SEVERE, "Failed to save " + file, e);
      return false;
    }
    this.unsaved.remove(player, data);
    return true;
  }

  private @NotNull PlayerData load(final @NotNull UUID player) {
    final @Nullable PlayerData unsaved = this.unsaved.get(player);
    if (unsaved != null) {
      return unsaved;
    }
    final @NotNull File file = file(player);
    if (!file.isFile()) {
      return PlayerData.EMPTY;
    }
    final @NotNull YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    final @NotNull Map<UUID, Long> discoveries = new HashMap<>();
    final @Nullable ConfigurationSection section = yaml.getConfigurationSection("discoveries");
    if (section != null) {
      for (final @NotNull String key : section.getKeys(false)) {
        try {
          discoveries.put(UUID.fromString(key), section.getLong(key));
        } catch (final IllegalArgumentException e) {
          this.plugin.getLogger().warning("Skipping a malformed discovery in " + file + ": " + key);
        }
      }
    }
    return new PlayerData(PersonalWaypoints.read(yaml.getMapList("waypoints")), discoveries);
  }

  private @NotNull File file(final @NotNull UUID player) {
    return new File(this.directory, player + ".yml");
  }
}
