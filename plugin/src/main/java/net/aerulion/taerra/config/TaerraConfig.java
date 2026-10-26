package net.aerulion.taerra.config;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.aerulion.taerra.integration.TownyTowns;
import net.aerulion.taerra.model.MapViews;
import net.aerulion.taerra.world.SpawnMarkers;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record TaerraConfig(
    int renderThreads,
    int updateInterval,
    int maxCacheTiles,
    int maxCacheChunks,
    @NotNull CopyOnWriteArrayList<String> enabledWorlds,
    @NotNull CopyOnWriteArrayList<String> privateWorlds,
    @NotNull CopyOnWriteArrayList<String> privatePlayers,
    @NotNull CopyOnWriteArrayList<String> privateLayers,
    @NotNull Map<String, String> worldNames,
    @NotNull Map<String, String> layerIcons,
    @NotNull Map<String, MapViews> worldViews) {

  private static final long HEAP_BYTES = Runtime.getRuntime().maxMemory();
  private static final long TILE_BYTES = 256L << 10;
  private static final long CHUNK_BYTES = 19L << 10;
  private static final int MIN_CACHE_CHUNKS = 256;

  public static final int CACHE_TILES_LIMIT = cacheEntries(HEAP_BYTES, 10, TILE_BYTES, 1, 50_000);
  public static final int CACHE_CHUNKS_LIMIT =
      cacheEntries(HEAP_BYTES, 5, CHUNK_BYTES, MIN_CACHE_CHUNKS, 131_072);

  private static final @NotNull Map<String, String> DEFAULT_LAYER_ICONS =
      Map.of(SpawnMarkers.LAYER, "taerra:star", TownyTowns.LAYER, "taerra:castle");
  private static final @NotNull String WORLD_PERMISSION = "taerra.world.";
  private static final @NotNull String PLAYERS_PERMISSION = "taerra.players.";
  private static final @NotNull String LAYER_PERMISSION = "taerra.layer.";

  public static @NotNull TaerraConfig load(final @NotNull Plugin plugin) {
    plugin.saveDefaultConfig();
    plugin.reloadConfig();
    final @NotNull FileConfiguration file = plugin.getConfig();

    return new TaerraConfig(
        renderThreads(file),
        updateInterval(file),
        maxCacheTiles(file),
        maxCacheChunks(file),
        new CopyOnWriteArrayList<>(file.getStringList("worlds")),
        new CopyOnWriteArrayList<>(file.getStringList("private-worlds")),
        new CopyOnWriteArrayList<>(file.getStringList("private-players")),
        new CopyOnWriteArrayList<>(file.getStringList("markers.private-layers")),
        new ConcurrentHashMap<>(names(file.getConfigurationSection("world-names"))),
        new ConcurrentHashMap<>(layerIcons(file.getMapList("markers.layer-icons"))),
        new ConcurrentHashMap<>(viewsOf(file.getConfigurationSection("world-views"))));
  }

  static @NotNull Map<String, MapViews> viewsOf(final @Nullable ConfigurationSection section) {
    final @NotNull Map<String, MapViews> views = new HashMap<>();
    if (section != null) {
      for (final @NotNull String world : section.getKeys(false)) {
        final @Nullable MapViews shown = MapViews.parse(section.getString(world));
        if (shown != null && shown != MapViews.BOTH) {
          views.put(world, shown);
        }
      }
    }
    return views;
  }

  static @NotNull Map<String, String> layerIcons(final @NotNull List<Map<?, ?>> entries) {
    final @NotNull Map<String, String> icons = new HashMap<>();
    for (final @NotNull Map<?, ?> entry : entries) {
      if (entry.get("layer") instanceof final @NotNull String layer
          && entry.get("icon") instanceof final @NotNull String icon) {
        icons.put(layer, icon);
      }
    }
    return icons;
  }

  public static int renderThreads(final @NotNull FileConfiguration file) {
    final int configured = file.getInt("rendering.render-threads", 0);
    return configured > 0
        ? configured
        : defaultRenderThreads(Runtime.getRuntime().availableProcessors());
  }

  public static int updateInterval(final @NotNull FileConfiguration file) {
    return Math.max(0, file.getInt("rendering.update-interval", 900));
  }

  public static int maxCacheTiles(final @NotNull FileConfiguration file) {
    return cacheSize(
        file.getInt("cache.max-tiles", 0),
        cacheEntries(HEAP_BYTES, 64, TILE_BYTES, 1, CACHE_TILES_LIMIT),
        1,
        CACHE_TILES_LIMIT);
  }

  public static int maxCacheChunks(final @NotNull FileConfiguration file) {
    return cacheSize(
        file.getInt("cache.max-chunks", 0),
        cacheEntries(HEAP_BYTES, 50, CHUNK_BYTES, MIN_CACHE_CHUNKS, CACHE_CHUNKS_LIMIT),
        MIN_CACHE_CHUNKS,
        CACHE_CHUNKS_LIMIT);
  }

  static int cacheSize(
      final int configured, final int automatic, final int floor, final int limit) {
    return configured <= 0 ? automatic : Math.clamp(configured, floor, limit);
  }

  static int cacheEntries(
      final long heapBytes,
      final int heapShare,
      final long entryBytes,
      final int floor,
      final int ceiling) {
    return Math.clamp(heapBytes / heapShare / entryBytes, floor, ceiling);
  }

  private static @NotNull Map<String, String> names(final @Nullable ConfigurationSection section) {
    final @NotNull Map<String, String> names = new HashMap<>();
    if (section != null) {
      for (final @NotNull String world : section.getKeys(false)) {
        final @Nullable String name = section.getString(world);
        if (name != null && !name.isBlank()) {
          names.put(world, name);
        }
      }
    }
    return names;
  }

  static int defaultRenderThreads(final int cores) {
    return Math.max(1, Math.min(cores / 2, cores - 2));
  }

  public static @NotNull String worldPermission(final @NotNull String worldName) {
    return WORLD_PERMISSION + worldName.replace(':', '.');
  }

  public static @NotNull String playersPermission(final @NotNull String worldName) {
    return PLAYERS_PERMISSION + worldName.replace(':', '.');
  }

  public static @NotNull String layerPermission(final @NotNull String layerId) {
    return LAYER_PERMISSION + layerId.replace(':', '.');
  }

  private static void setListed(
      final @NotNull Plugin plugin,
      final @NotNull String path,
      final @NotNull CopyOnWriteArrayList<String> list,
      final @NotNull String value,
      final boolean listed) {
    if (listed) {
      list.addIfAbsent(value);
    } else {
      list.remove(value);
    }
    plugin.getConfig().set(path, List.copyOf(list));
    plugin.saveConfig();
  }

  public boolean worldVisible(
      final @NotNull String worldName, final @NotNull Predicate<String> hasPermission) {
    return worldEnabled(worldName)
        && (!worldPrivate(worldName) || hasPermission.test(worldPermission(worldName)));
  }

  public boolean worldPrivate(final @NotNull String worldName) {
    return this.privateWorlds.contains(worldName);
  }

  public boolean playersPrivate(final @NotNull String worldName) {
    return this.privatePlayers.contains(worldName);
  }

  public boolean layerPrivate(final @NotNull String layerId) {
    return this.privateLayers.contains(layerId);
  }

  public @Nullable String layerIcon(final @NotNull String layerId) {
    return this.layerIcons.getOrDefault(layerId, DEFAULT_LAYER_ICONS.get(layerId));
  }

  public synchronized void setLayerIcon(
      final @NotNull Plugin plugin, final @NotNull String layerId, final @Nullable String icon) {
    if ((icon == null || icon.isEmpty()) && !DEFAULT_LAYER_ICONS.containsKey(layerId)) {
      this.layerIcons.remove(layerId);
    } else {
      this.layerIcons.put(layerId, icon == null ? "" : icon);
    }
    plugin.getConfig().set("markers.layer-icons", layerIconEntries(this.layerIcons));
    plugin.saveConfig();
  }

  static @NotNull List<Map<String, String>> layerIconEntries(
      final @NotNull Map<String, String> icons) {
    return new TreeMap<>(icons)
        .entrySet().stream()
            .map(entry -> Map.of("layer", entry.getKey(), "icon", entry.getValue()))
            .toList();
  }

  public @NotNull Set<String> webPermissions() {
    return Stream.concat(
            Stream.concat(
                this.privateWorlds.stream().map(TaerraConfig::worldPermission),
                this.privatePlayers.stream().map(TaerraConfig::playersPermission)),
            this.privateLayers.stream().map(TaerraConfig::layerPermission))
        .collect(Collectors.toUnmodifiableSet());
  }

  public synchronized void setWorldPrivate(
      final @NotNull Plugin plugin, final @NotNull String worldName, final boolean hidden) {
    setListed(plugin, "private-worlds", this.privateWorlds, worldName, hidden);
    registerPermissions(plugin);
  }

  public synchronized void setPlayersPrivate(
      final @NotNull Plugin plugin, final @NotNull String worldName, final boolean hidden) {
    setListed(plugin, "private-players", this.privatePlayers, worldName, hidden);
    registerPermissions(plugin);
  }

  public synchronized void setLayerPrivate(
      final @NotNull Plugin plugin, final @NotNull String layerId, final boolean hidden) {
    setListed(plugin, "markers.private-layers", this.privateLayers, layerId, hidden);
    registerPermissions(plugin);
  }

  public synchronized void registerPermissions(final @NotNull Plugin plugin) {
    final @NotNull PluginManager manager = plugin.getServer().getPluginManager();
    final @NotNull Set<String> nodes = webPermissions();
    for (final @NotNull Permission permission : List.copyOf(manager.getPermissions())) {
      final @NotNull String node = permission.getName();
      if ((node.startsWith(WORLD_PERMISSION)
              || node.startsWith(PLAYERS_PERMISSION)
              || node.startsWith(LAYER_PERMISSION))
          && !nodes.contains(node)) {
        manager.removePermission(permission);
      }
    }
    for (final @NotNull String world : this.privateWorlds) {
      register(
          manager,
          worldPermission(world),
          "Lets the user see the private world " + world + " on the web map while logged in.");
    }
    for (final @NotNull String world : this.privatePlayers) {
      register(
          manager,
          playersPermission(world),
          "Lets the user see where players are in " + world + " on the web map while logged in.");
    }
    for (final @NotNull String layer : this.privateLayers) {
      register(
          manager,
          layerPermission(layer),
          "Lets the user see the private layer " + layer + " on the web map while logged in.");
    }
  }

  private static void register(
      final @NotNull PluginManager manager,
      final @NotNull String node,
      final @NotNull String description) {
    if (manager.getPermission(node) == null) {
      manager.addPermission(new Permission(node, description, PermissionDefault.OP));
    }
  }

  public boolean worldEnabled(final @NotNull String worldName) {
    return this.enabledWorlds.contains(worldName);
  }

  public synchronized void setWorldName(
      final @NotNull Plugin plugin, final @NotNull String worldName, final @NotNull String name) {
    if (name.isEmpty()) {
      this.worldNames.remove(worldName);
    } else {
      this.worldNames.put(worldName, name);
    }
    plugin.getConfig().set("world-names", new TreeMap<>(this.worldNames));
    plugin.saveConfig();
  }

  public @NotNull MapViews views(final @NotNull String worldName) {
    return this.worldViews.getOrDefault(worldName, MapViews.BOTH);
  }

  public synchronized void setViews(
      final @NotNull Plugin plugin,
      final @NotNull String worldName,
      final @NotNull MapViews views) {
    if (views == MapViews.BOTH) {
      this.worldViews.remove(worldName);
    } else {
      this.worldViews.put(worldName, views);
    }
    final @NotNull Map<String, String> ids = new TreeMap<>();
    this.worldViews.forEach((world, shown) -> ids.put(world, shown.id()));
    plugin.getConfig().set("world-views", ids);
    plugin.saveConfig();
  }

  public synchronized void setWorlds(
      final @NotNull Plugin plugin,
      final @NotNull List<String> enabled,
      final @NotNull Set<String> hidden,
      final @NotNull Set<String> hiddenPlayers,
      final @NotNull Map<String, String> names) {
    this.enabledWorlds.addAllAbsent(enabled);
    this.enabledWorlds.retainAll(enabled);
    this.enabledWorlds.sort(Comparator.comparingInt(enabled::indexOf));
    this.privateWorlds.addAllAbsent(hidden);
    this.privateWorlds.retainAll(hidden);
    this.privatePlayers.addAllAbsent(hiddenPlayers);
    this.privatePlayers.retainAll(hiddenPlayers);
    this.worldNames.putAll(names);
    this.worldNames.keySet().retainAll(names.keySet());
    plugin.getConfig().set("worlds", List.copyOf(this.enabledWorlds));
    plugin.getConfig().set("private-worlds", List.copyOf(this.privateWorlds));
    plugin.getConfig().set("private-players", List.copyOf(this.privatePlayers));
    plugin.getConfig().set("world-names", new TreeMap<>(this.worldNames));
    plugin.saveConfig();
    registerPermissions(plugin);
  }

  public synchronized void setWorldEnabled(
      final @NotNull Plugin plugin, final @NotNull String worldName, final boolean enabled) {
    setListed(plugin, "worlds", this.enabledWorlds, worldName, enabled);
  }
}
