package net.aerulion.taerra.config;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
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
    boolean worldBordersShownByDefault) {

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
        file.getBoolean("markers.worldguard.enabled", true),
        file.getBoolean("markers.worldguard.shown-by-default", false),
        file.getBoolean("markers.worldborder.enabled", true),
        file.getBoolean("markers.worldborder.shown-by-default", true));
  }

  static int defaultRenderThreads(final int cores) {
    return Math.max(1, Math.min(cores / 2, cores - 2));
  }

  public static @NotNull String worldPermission(final @NotNull String worldName) {
    return "taerra.web.world." + worldName.replace(':', '.');
  }

  public static @NotNull String layerPermission(final @NotNull String layerId) {
    return "taerra.web.layer." + layerId.replace(':', '.');
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
  }

  public synchronized void setLayerPrivate(
      final @NotNull Plugin plugin, final @NotNull String layerId, final boolean hidden) {
    setListed(plugin, "markers.private-layers", this.privateLayers, layerId, hidden);
  }

  public boolean worldEnabled(final @NotNull String worldName) {
    return this.enabledWorlds.contains(worldName);
  }

  public synchronized void setWorldEnabled(
      final @NotNull Plugin plugin, final @NotNull String worldName, final boolean enabled) {
    setListed(plugin, "worlds", this.enabledWorlds, worldName, enabled);
  }
}
