package net.aerulion.taerra.waypoint;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.api.marker.PlayerMarkerLayer;
import net.aerulion.taerra.util.AtomicFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PersonalWaypoints {

  public static final String LAYER = "personal";
  public static final int MAX = 100;
  private static final String PREFIX = "personal:";

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
    final @NotNull File file = file(player);
    if (!file.isFile()) {
      return List.of();
    }
    return YamlConfiguration.loadConfiguration(file).getMapList("waypoints").stream()
        .map(entry -> WaypointsImpl.read(entry, PREFIX))
        .filter(Objects::nonNull)
        .toList();
  }

  public synchronized @NotNull Result save(
      final @NotNull UUID player, final @NotNull Marker marker, final @Nullable String previous) {
    final @NotNull List<Marker> markers = new ArrayList<>(markers(player));
    if (previous != null) {
      markers.removeIf(known -> known.id().equals(markerId(previous)));
    }
    if (markers.stream().anyMatch(known -> known.id().equals(marker.id()))) {
      return Result.TAKEN;
    }
    if (markers.size() >= MAX) {
      return Result.FULL;
    }
    markers.add(marker);
    return write(player, markers) ? Result.SAVED : Result.FAILED;
  }

  public synchronized boolean remove(final @NotNull UUID player, final @NotNull String name) {
    final @NotNull List<Marker> markers = new ArrayList<>(markers(player));
    return markers.removeIf(known -> known.id().equals(markerId(name))) && write(player, markers);
  }

  private boolean write(final @NotNull UUID player, final @NotNull List<Marker> markers) {
    final @NotNull File file = file(player);
    try {
      if (markers.isEmpty()) {
        Files.deleteIfExists(file.toPath());
      } else {
        final @NotNull YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("waypoints", markers.stream().map(WaypointsImpl::entry).toList());
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
}
