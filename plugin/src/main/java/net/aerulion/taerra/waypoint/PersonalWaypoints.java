package net.aerulion.taerra.waypoint;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.api.marker.PlayerMarkerLayer;
import net.aerulion.taerra.command.Messages;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.util.AtomicFiles;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PersonalWaypoints implements Listener {

  public static final String LAYER = "personal";
  public static final int MAX = 100;
  private static final String PREFIX = "personal:";
  private static final String DEATH = "taerra.waypoint.death";
  private static final Color DEATH_COLOR = Color.fromRGB(0xE34A4A);

  private final @NotNull Plugin plugin;
  private final @NotNull File directory;
  private final @NotNull PlayerMarkerLayer layer;

  public PersonalWaypoints(
      final @NotNull Plugin plugin,
      final @NotNull MarkerRegistry registry,
      final @NotNull File directory) {
    super();
    this.plugin = plugin;
    this.directory = directory;
    this.layer = registry.playerLayer(plugin, LAYER, "My waypoints", true, this::markers);
  }

  public static @NotNull String markerId(final @NotNull String name) {
    return PREFIX + name.toLowerCase(Locale.ROOT);
  }

  public @NotNull List<Marker> markers(final @NotNull UUID player) {
    return all(player).stream().filter(saved -> !saved.hidden()).map(Saved::marker).toList();
  }

  public @NotNull List<Saved> all(final @NotNull UUID player) {
    final @NotNull File file = file(player);
    if (!file.isFile()) {
      return List.of();
    }
    return YamlConfiguration.loadConfiguration(file).getMapList("waypoints").stream()
        .map(
            entry -> {
              final @Nullable Marker marker = WaypointsImpl.read(entry, PREFIX);
              return marker == null
                  ? null
                  : new Saved(marker, Boolean.TRUE.equals(entry.get("hidden")));
            })
        .filter(Objects::nonNull)
        .toList();
  }

  public synchronized @NotNull Result save(
      final @NotNull UUID player, final @NotNull Marker marker, final @Nullable String previous) {
    final @NotNull List<Saved> saved = new ArrayList<>(all(player));
    final boolean hidden =
        previous != null
            && saved.stream()
                .anyMatch(
                    known -> known.marker().id().equals(markerId(previous)) && known.hidden());
    if (previous != null) {
      saved.removeIf(known -> known.marker().id().equals(markerId(previous)));
    }
    if (saved.stream().anyMatch(known -> known.marker().id().equals(marker.id()))) {
      return Result.TAKEN;
    }
    if (saved.size() >= MAX) {
      return Result.FULL;
    }
    saved.add(new Saved(marker, hidden));
    return write(player, saved) ? Result.SAVED : Result.FAILED;
  }

  public synchronized @NotNull Result replace(
      final @NotNull UUID player,
      final @NotNull Marker marker,
      final @NotNull Collection<String> previous) {
    final @NotNull Set<String> ids = new HashSet<>();
    previous.forEach(name -> ids.add(markerId(name)));
    final @NotNull List<Saved> saved = new ArrayList<>(all(player));
    saved.removeIf(known -> ids.contains(known.marker().id()));
    if (saved.size() >= MAX) {
      return Result.FULL;
    }
    saved.add(new Saved(marker, false));
    return write(player, saved) ? Result.SAVED : Result.FAILED;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDeath(final @NotNull PlayerDeathEvent event) {
    final @NotNull Player player = event.getPlayer();
    if (!TaerraPermission.WEB_MARKERS_PERSONAL.check(player)) {
      return;
    }
    final @NotNull Location at = player.getLocation();
    final @NotNull World world = at.getWorld();
    final @NotNull String name = Messages.plain(DEATH, player.locale());
    final @NotNull Marker marker =
        Marker.point(
                markerId(name),
                world.getKey(),
                new Vector(
                    at.getBlockX() + .5,
                    Math.clamp(at.getBlockY(), world.getMinHeight(), world.getMaxHeight() - 1),
                    at.getBlockZ() + .5),
                name)
            .withColor(DEATH_COLOR);
    final @NotNull UUID uuid = player.getUniqueId();
    final @NotNull List<String> previous = Messages.plainInEveryLocale(DEATH);
    Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> replace(uuid, marker, previous));
  }

  public synchronized boolean remove(final @NotNull UUID player, final @NotNull String name) {
    final @NotNull List<Saved> saved = new ArrayList<>(all(player));
    return saved.removeIf(known -> known.marker().id().equals(markerId(name)))
        && write(player, saved);
  }

  public synchronized int setHidden(
      final @NotNull UUID player, final @NotNull Collection<String> names, final boolean hidden) {
    final @NotNull Set<String> ids = new HashSet<>();
    names.forEach(name -> ids.add(markerId(name)));
    int changed = 0;
    final @NotNull List<Saved> saved = new ArrayList<>();
    for (final @NotNull Saved known : all(player)) {
      if (ids.contains(known.marker().id()) && known.hidden() != hidden) {
        saved.add(new Saved(known.marker(), hidden));
        changed++;
      } else {
        saved.add(known);
      }
    }
    return changed > 0 && !write(player, saved) ? 0 : changed;
  }

  public synchronized @Nullable Imported importAll(
      final @NotNull UUID player, final @NotNull List<Saved> incoming, final boolean replace) {
    final @NotNull List<Saved> saved = new ArrayList<>(replace ? List.of() : all(player));
    final @NotNull Map<String, Saved> known = new HashMap<>();
    saved.forEach(entry -> known.put(entry.marker().id(), entry));
    int added = 0;
    int taken = 0;
    int full = 0;
    for (final @NotNull Saved entry : incoming) {
      if (known.containsKey(entry.marker().id())) {
        taken++;
      } else if (saved.size() >= MAX) {
        full++;
      } else {
        saved.add(entry);
        known.put(entry.marker().id(), entry);
        added++;
      }
    }
    return (added > 0 || replace) && !write(player, saved)
        ? null
        : new Imported(added, taken, full);
  }

  private boolean write(final @NotNull UUID player, final @NotNull List<Saved> saved) {
    final @NotNull File file = file(player);
    try {
      if (saved.isEmpty()) {
        Files.deleteIfExists(file.toPath());
      } else {
        final @NotNull YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(
            "waypoints",
            saved.stream()
                .map(
                    entry -> {
                      final @NotNull Map<String, Object> written =
                          WaypointsImpl.entry(entry.marker());
                      if (entry.hidden()) {
                        written.put("hidden", true);
                      }
                      return written;
                    })
                .toList());
        AtomicFiles.write(file, yaml.saveToString().getBytes(StandardCharsets.UTF_8));
      }
    } catch (final IOException e) {
      this.plugin.getLogger().log(Level.SEVERE, "Failed to save " + file, e);
      return false;
    }
    this.layer.refresh(player);
    return true;
  }

  private @NotNull File file(final @NotNull UUID player) {
    return new File(this.directory, player + ".yml");
  }

  public enum Result {
    SAVED,
    TAKEN,
    FULL,
    FAILED
  }

  public record Saved(@NotNull Marker marker, boolean hidden) {}

  public record Imported(int added, int taken, int full) {}
}
