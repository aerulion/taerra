package net.aerulion.taerra.waypoint;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.logging.Level;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.api.marker.PointMarker;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.util.AtomicFiles;
import net.aerulion.taerra.util.HexColors;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.SpawnChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WaypointsImpl implements Waypoints, Listener {

  private static final @NotNull Color DEFAULT_COLOR = Color.fromRGB(0x4FA3E0);
  private static final @NotNull Color SPAWN_COLOR = Color.fromRGB(0xFFCF3F);
  private static final @NotNull String PLACED = "waypoint:";
  private static final @NotNull String SPAWN = "spawn:";

  private final @NotNull Plugin plugin;
  private final @NotNull MarkerRegistry registry;
  private final @NotNull MarkerLayer layer;
  private final @NotNull File file;
  private final @NotNull Set<String> hiddenGroups = new HashSet<>();

  public WaypointsImpl(final @NotNull Plugin plugin, final @NotNull MarkerRegistry registry) {
    super();
    this.plugin = plugin;
    this.registry = registry;
    this.layer = registry.layer(LAYER, "Waypoints");
    this.file = new File(plugin.getDataFolder(), "waypoints.yml");
  }

  private static @NotNull String id(final @NotNull String name) {
    return PLACED + name.toLowerCase(Locale.ROOT);
  }

  private static @NotNull List<PointMarker> placed(final @NotNull MarkerLayer waypointLayer) {
    return waypointLayer.markers().stream()
        .filter(marker -> marker.id().startsWith(PLACED))
        .map(PointMarker.class::cast)
        .toList();
  }

  @Override
  public void load() {
    final @NotNull YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
    this.hiddenGroups.addAll(yaml.getStringList("hidden-groups"));
    for (final @NotNull Map<?, ?> entry : yaml.getMapList("waypoints")) {
      final @Nullable NamespacedKey world =
          NamespacedKey.fromString(String.valueOf(entry.get("world")));
      final @NotNull OptionalInt rgb = HexColors.parseRgb(String.valueOf(entry.get("color")));
      if (world == null
          || !(entry.get("name") instanceof final @NotNull String name)
          || !(entry.get("x") instanceof final @NotNull Number x)
          || !(entry.get("y") instanceof final @NotNull Number y)
          || !(entry.get("z") instanceof final @NotNull Number z)) {
        this.plugin.getLogger().warning("Skipping a malformed waypoint in waypoints.yml: " + entry);
        continue;
      }
      layer(entry.get("group") instanceof final @NotNull String group ? group : null)
          .set(
              new PointMarker(
                  id(name),
                  world,
                  new Vector(x.doubleValue(), y.doubleValue(), z.doubleValue()),
                  name,
                  Map.of(),
                  rgb.isPresent() ? Color.fromRGB(rgb.getAsInt()) : DEFAULT_COLOR));
    }
    Bukkit.getWorlds().forEach(this::placeSpawn);
  }

  @Override
  public void add(
      final @NotNull String name, final @NotNull Location location, final @NotNull Color color) {
    add(name, location, color, group(name));
  }

  @Override
  public synchronized void add(
      final @NotNull String name,
      final @NotNull Location location,
      final @NotNull Color color,
      final @Nullable String group) {
    place(name, location, color, group);
    save();
  }

  @Override
  public synchronized void hideByDefault(final @NotNull String group) {
    final @Nullable String id = Waypoints.groupLayerId(group);
    if (id != null && this.registry.existingLayer(id) == null) {
      this.hiddenGroups.add(id);
    }
  }

  @Override
  public synchronized void addAll(final @NotNull Collection<Waypoint> waypoints) {
    for (final @NotNull Waypoint waypoint : waypoints) {
      place(waypoint.name(), waypoint.location(), waypoint.color(), waypoint.group());
    }
    save();
  }

  @Override
  public synchronized boolean remove(final @NotNull String name) {
    boolean removed = false;
    for (final @NotNull MarkerLayer waypointLayer : layers()) {
      if (waypointLayer.remove(id(name)) != null) {
        removed = true;
        dropIfEmpty(waypointLayer);
      }
    }
    if (removed) {
      save();
    }
    return removed;
  }

  @Override
  public @NotNull List<String> names() {
    return layers().stream()
        .flatMap(waypointLayer -> placed(waypointLayer).stream())
        .map(Marker::label)
        .toList();
  }

  @Override
  public @NotNull List<Group> groups() {
    return layers().stream()
        .filter(waypointLayer -> waypointLayer != this.layer)
        .map(waypointLayer -> new Group(waypointLayer.id(), waypointLayer.label()))
        .toList();
  }

  private void place(
      final @NotNull String name,
      final @NotNull Location location,
      final @NotNull Color color,
      final @Nullable String group) {
    final @NotNull MarkerLayer target = layer(group);
    for (final @NotNull MarkerLayer waypointLayer : layers()) {
      if (waypointLayer != target) {
        waypointLayer.remove(id(name));
        dropIfEmpty(waypointLayer);
      }
    }
    target.set(
        new PointMarker(
            id(name),
            location.getWorld().getKey(),
            new Vector(location.getBlockX() + .5, location.getBlockY(), location.getBlockZ() + .5),
            name,
            Map.of(),
            color));
  }

  private @Nullable String group(final @NotNull String name) {
    return layers().stream()
        .filter(waypointLayer -> waypointLayer != this.layer)
        .filter(waypointLayer -> waypointLayer.get(id(name)) != null)
        .map(MarkerLayer::label)
        .findFirst()
        .orElse(null);
  }

  private @NotNull MarkerLayer layer(final @Nullable String group) {
    final @Nullable String id = Waypoints.groupLayerId(group);
    return id == null
        ? this.layer
        : this.registry.layer(id, group.strip(), !this.hiddenGroups.contains(id));
  }

  private @NotNull List<MarkerLayer> layers() {
    return this.registry.layers().stream()
        .filter(
            waypointLayer ->
                waypointLayer.id().equals(LAYER) || waypointLayer.id().startsWith(GROUP_LAYER))
        .toList();
  }

  private void dropIfEmpty(final @NotNull MarkerLayer waypointLayer) {
    if (waypointLayer != this.layer && waypointLayer.markers().isEmpty()) {
      this.registry.removeLayer(waypointLayer.id());
    }
  }

  private void save() {
    final @NotNull List<Map<String, Object>> entries = new ArrayList<>();
    for (final @NotNull MarkerLayer waypointLayer : layers()) {
      for (final @NotNull PointMarker point : placed(waypointLayer)) {
        final @NotNull Map<String, Object> entry = new HashMap<>();
        entry.put("name", point.label());
        entry.put("world", point.world().asString());
        entry.put("x", point.position().getX());
        entry.put("y", point.position().getY());
        entry.put("z", point.position().getZ());
        entry.put("color", HexColors.format(point.color().asRGB()));
        if (waypointLayer != this.layer) {
          entry.put("group", waypointLayer.label());
        }
        entries.add(entry);
      }
    }
    final @NotNull YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("waypoints", entries);
    this.hiddenGroups.retainAll(layers().stream().map(MarkerLayer::id).toList());
    yaml.set("hidden-groups", this.hiddenGroups.stream().sorted().toList());
    try {
      AtomicFiles.write(this.file, yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    } catch (final IOException e) {
      this.plugin.getLogger().log(Level.SEVERE, "Failed to save waypoints.yml", e);
    }
  }

  private void placeSpawn(final @NotNull World world) {
    final @NotNull Location spawn = world.getSpawnLocation();
    this.layer.set(
        new PointMarker(
            SPAWN + WorldKeys.id(world),
            world.getKey(),
            new Vector(spawn.getBlockX() + .5, spawn.getBlockY(), spawn.getBlockZ() + .5),
            "Spawn",
            Map.of(),
            SPAWN_COLOR));
  }

  @EventHandler
  public void onSpawnChange(final @NotNull SpawnChangeEvent event) {
    placeSpawn(event.getWorld());
  }

  @EventHandler
  public void onWorldLoad(final @NotNull WorldLoadEvent event) {
    placeSpawn(event.getWorld());
  }

  @EventHandler
  public void onWorldUnload(final @NotNull WorldUnloadEvent event) {
    this.layer.remove(SPAWN + WorldKeys.id(event.getWorld()));
  }
}
