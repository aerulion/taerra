package net.aerulion.taerra.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.integration.GriefPreventionClaims;
import net.aerulion.taerra.integration.TownyTowns;
import net.aerulion.taerra.integration.WorldBorders;
import net.aerulion.taerra.integration.WorldGuardRegions;
import net.aerulion.taerra.limit.WorldLimits;
import net.aerulion.taerra.region.MapRegions;
import net.aerulion.taerra.rendering.pipeline.TileGeneratorService;
import net.aerulion.taerra.rendering.queue.ChangeBuffer;
import net.aerulion.taerra.rendering.sample.ChunkColumnsCache;
import net.aerulion.taerra.storage.TileCache;
import net.aerulion.taerra.storage.TileStorage;
import net.aerulion.taerra.world.LoadedChunkMarkers;
import net.aerulion.taerra.world.SpawnMarkers;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LiveSettings {

  private final @NotNull Plugin plugin;
  private final @NotNull MarkerRegistry markers;
  private final @NotNull TaerraConfig config;
  private final @NotNull WorldLimits limits;
  private final @NotNull MapRegions regions;
  private final @NotNull TileGeneratorService generator;
  private final @NotNull TileCache tileCache;
  private final @NotNull TileStorage tileStorage;
  private final @NotNull ChunkColumnsCache columnsCache;
  private final @NotNull ChangeBuffer changes;
  private final @NotNull Runnable restartWeb;
  private final @NotNull Runnable refreshMarkers;
  private final @NotNull Runnable webChanged;
  private final @NotNull Map<Layer, BukkitTask> layers = new HashMap<>();

  public LiveSettings(
      final @NotNull Plugin plugin,
      final @NotNull MarkerRegistry markers,
      final @NotNull TaerraConfig config,
      final @NotNull WorldLimits limits,
      final @NotNull MapRegions regions,
      final @NotNull TileGeneratorService generator,
      final @NotNull TileCache tileCache,
      final @NotNull TileStorage tileStorage,
      final @NotNull ChunkColumnsCache columnsCache,
      final @NotNull ChangeBuffer changes,
      final @NotNull Runnable restartWeb,
      final @NotNull Runnable refreshMarkers,
      final @NotNull Runnable webChanged) {
    super();
    this.plugin = plugin;
    this.markers = markers;
    this.config = config;
    this.limits = limits;
    this.regions = regions;
    this.generator = generator;
    this.tileCache = tileCache;
    this.tileStorage = tileStorage;
    this.columnsCache = columnsCache;
    this.changes = changes;
    this.restartWeb = restartWeb;
    this.refreshMarkers = refreshMarkers;
    this.webChanged = webChanged;
  }

  public void startLayers() {
    for (final @NotNull Layer layer : Layer.values()) {
      restart(layer);
    }
  }

  public void apply(final @NotNull Set<String> keys) {
    final @NotNull FileConfiguration file = this.plugin.getConfig();
    if (keys.contains("rendering.render-threads")) {
      this.generator.setThreads(TaerraConfig.renderThreads(file));
    }
    if (keys.contains("rendering.update-interval")) {
      this.changes.setInterval(TaerraConfig.updateInterval(file));
    }
    if (keys.contains("cache.max-tiles")) {
      this.tileCache.resize(TaerraConfig.maxCacheTiles(file));
      this.tileStorage.resize(TaerraConfig.maxCacheTiles(file));
    }
    if (keys.contains("cache.max-chunks")) {
      this.columnsCache.resize(TaerraConfig.maxCacheChunks(file));
    }
    boolean layersChanged = false;
    for (final @NotNull Layer layer : Layer.values()) {
      if (keys.stream().anyMatch(key -> key.startsWith("markers." + layer.setting + "."))) {
        restart(layer);
        layersChanged = true;
      }
    }
    if (layersChanged) {
      this.refreshMarkers.run();
    }
    if (keys.stream()
        .anyMatch(key -> ConfigEditor.effect(key) == ConfigEditor.Effect.WEB_RESTARTED)) {
      this.restartWeb.run();
    } else if (keys.stream().anyMatch(key -> key.startsWith("web."))) {
      this.webChanged.run();
    }
  }

  private void restart(final @NotNull Layer layer) {
    final @Nullable BukkitTask running = this.layers.remove(layer);
    if (running != null) {
      running.cancel();
      this.markers.removeLayer(layer.id);
    }
    final @NotNull FileConfiguration file = this.plugin.getConfig();
    if (!file.getBoolean("markers." + layer.setting + ".enabled", layer.enabled)) {
      return;
    }
    final boolean shown =
        file.getBoolean("markers." + layer.setting + ".shown-by-default", layer.shown);
    final @Nullable BukkitTask task =
        switch (layer) {
          case WORLDGUARD -> {
            if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
              yield null;
            }
            this.plugin.getLogger().info("Hooked into WorldGuard, regions show up on the map");
            yield WorldGuardRegions.start(this.plugin, this.markers, this.config, shown);
          }
          case TOWNY -> {
            if (!Bukkit.getPluginManager().isPluginEnabled("Towny")) {
              yield null;
            }
            this.plugin.getLogger().info("Hooked into Towny, towns show up on the map");
            yield TownyTowns.start(this.plugin, this.markers, this.config, shown);
          }
          case GRIEFPREVENTION -> {
            if (!Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")) {
              yield null;
            }
            this.plugin.getLogger().info("Hooked into GriefPrevention, claims show up on the map");
            yield GriefPreventionClaims.start(this.plugin, this.markers, this.config, shown);
          }
          case SPAWN -> SpawnMarkers.start(this.plugin, this.markers, this.config, shown);
          case WORLDBORDER -> WorldBorders.start(this.plugin, this.markers, this.config, shown);
          case LOADED_CHUNKS ->
              LoadedChunkMarkers.start(
                  this.plugin, this.markers, this.config, this.limits, this.regions, shown);
        };
    if (task != null) {
      this.layers.put(layer, task);
    }
  }

  private enum Layer {
    SPAWN("spawn", "spawn", true, true),
    WORLDGUARD("worldguard", "worldguard", false, false),
    TOWNY("towny", "towny", true, true),
    GRIEFPREVENTION("griefprevention", "griefprevention", false, false),
    WORLDBORDER("worldborder", "worldborder", true, true),
    LOADED_CHUNKS("loaded-chunks", "chunks", false, false);

    private final @NotNull String setting;
    private final @NotNull String id;
    private final boolean enabled;
    private final boolean shown;

    Layer(
        final @NotNull String setting,
        final @NotNull String id,
        final boolean enabled,
        final boolean shown) {
      this.setting = setting;
      this.id = id;
      this.enabled = enabled;
      this.shown = shown;
    }
  }
}
