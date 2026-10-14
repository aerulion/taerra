package net.aerulion.taerra.config;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;

public record TaerraConfig(
    int renderThreads,
    int updateInterval,
    int maxCacheTiles,
    int maxCacheChunks,
    @NotNull CopyOnWriteArrayList<String> enabledWorlds,
    @NotNull CopyOnWriteArrayList<String> privateWorlds,
    @NotNull CopyOnWriteArrayList<String> privateLayers,
    boolean worldGuardRegions,
    boolean worldGuardShownByDefault,
    boolean worldBorders,
    boolean worldBordersShownByDefault,
    boolean loadedChunks,
    boolean loadedChunksShownByDefault) {

  private static final @NotNull String WORLD_PERMISSION = "taerra.web.world.";
  private static final @NotNull String LAYER_PERMISSION = "taerra.web.layer.";

  public static @NotNull TaerraConfig load(final @NotNull Plugin plugin) {
    plugin.saveDefaultConfig();
    plugin.reloadConfig();
    final @NotNull FileConfiguration file = plugin.getConfig();

    final int configuredThreads = file.getInt("rendering.render-threads", 0);
    return new TaerraConfig(
        configuredThreads > 0
            ? configuredThreads
            : defaultRenderThreads(Runtime.getRuntime().availableProcessors()),
        file.getInt("rendering.update-interval", 900),
        Math.max(1, file.getInt("cache.max-tiles", 500)),
        Math.max(256, file.getInt("cache.max-chunks", 8192)),
        new CopyOnWriteArrayList<>(file.getStringList("worlds")),
        new CopyOnWriteArrayList<>(file.getStringList("private-worlds")),
        new CopyOnWriteArrayList<>(file.getStringList("markers.private-layers")),
        file.getBoolean("markers.worldguard.enabled", false),
        file.getBoolean("markers.worldguard.shown-by-default", false),
        file.getBoolean("markers.worldborder.enabled", true),
        file.getBoolean("markers.worldborder.shown-by-default", true),
        file.getBoolean("markers.loaded-chunks.enabled", false),
        file.getBoolean("markers.loaded-chunks.shown-by-default", false));
  }

  static int defaultRenderThreads(final int cores) {
    return Math.max(1, Math.min(cores / 2, cores - 2));
  }

  public static @NotNull String worldPermission(final @NotNull String worldName) {
    return WORLD_PERMISSION + worldName.replace(':', '.');
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

  public boolean layerPrivate(final @NotNull String layerId) {
    return this.privateLayers.contains(layerId);
  }

  public @NotNull Set<String> webPermissions() {
    return Stream.concat(
            this.privateWorlds.stream().map(TaerraConfig::worldPermission),
            this.privateLayers.stream().map(TaerraConfig::layerPermission))
        .collect(Collectors.toUnmodifiableSet());
  }

  public synchronized void setWorldPrivate(
      final @NotNull Plugin plugin, final @NotNull String worldName, final boolean hidden) {
    setListed(plugin, "private-worlds", this.privateWorlds, worldName, hidden);
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
      if ((node.startsWith(WORLD_PERMISSION) || node.startsWith(LAYER_PERMISSION))
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

  public synchronized void setWorldEnabled(
      final @NotNull Plugin plugin, final @NotNull String worldName, final boolean enabled) {
    setListed(plugin, "worlds", this.enabledWorlds, worldName, enabled);
  }
}
