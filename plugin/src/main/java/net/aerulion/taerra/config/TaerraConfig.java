package net.aerulion.taerra.config;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    @NotNull Map<String, String> worldNames) {

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
        new ConcurrentHashMap<>(names(file.getConfigurationSection("world-names"))));
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
    return Math.max(1, file.getInt("cache.max-tiles", 500));
  }

  public static int maxCacheChunks(final @NotNull FileConfiguration file) {
    return Math.max(256, file.getInt("cache.max-chunks", 8192));
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

  public boolean worldPrivate(final @NotNull String worldName) {
    return this.privateWorlds.contains(worldName);
  }

  public boolean playersPrivate(final @NotNull String worldName) {
    return this.privatePlayers.contains(worldName);
  }

  public boolean layerPrivate(final @NotNull String layerId) {
    return this.privateLayers.contains(layerId);
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
