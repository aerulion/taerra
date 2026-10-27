package net.aerulion.taerra.web.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.region.MapRegions;
import net.aerulion.taerra.rendering.pipeline.TileGeneratorService;
import net.aerulion.taerra.web.auth.Viewer;
import net.aerulion.taerra.web.socket.MapWebSocketHandler;
import net.aerulion.taerra.web.socket.SocketMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WorldList {

  private final @NotNull Plugin plugin;
  private final @NotNull TaerraConfig taerraConfig;
  private final @NotNull TileGeneratorService generatorService;
  private final @NotNull MapWebSocketHandler wsHandler;
  private final @NotNull MapRegions regions;
  private final @NotNull ObjectMapper mapper = new ObjectMapper();
  private volatile @NotNull List<Map<String, Object>> publishedWorlds = List.of();
  private volatile @Nullable String publishedWorldsJson;

  public WorldList(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig taerraConfig,
      final @NotNull TileGeneratorService generatorService,
      final @NotNull MapWebSocketHandler wsHandler,
      final @NotNull MapRegions regions) {
    super();
    this.regions = regions;
    this.plugin = plugin;
    this.taerraConfig = taerraConfig;
    this.generatorService = generatorService;
    this.wsHandler = wsHandler;
  }

  private static int environmentSortOrder(final @NotNull String environmentName) {
    return switch (environmentName) {
      case "NORMAL" -> 0;
      case "NETHER" -> 1;
      case "THE_END" -> 2;
      default -> 3;
    };
  }

  public @NotNull List<Map<String, Object>> visibleTo(final @NotNull Viewer viewer) {
    return this.publishedWorlds.stream()
        .filter(world -> viewer.canSee(this.taerraConfig, (String) world.get("name")))
        .toList();
  }

  public void publish() {
    try {
      final @NotNull List<Map<String, Object>> worlds = worlds();
      final @NotNull String json = this.mapper.writeValueAsString(worlds);
      this.publishedWorlds = worlds;
      if (this.publishedWorldsJson != null && !json.equals(this.publishedWorldsJson)) {
        this.wsHandler.revalidate();
        this.wsHandler.broadcast(SocketMessage.WORLDS.json());
      }
      this.publishedWorldsJson = json;
    } catch (final JsonProcessingException e) {
      this.plugin.getLogger().log(Level.WARNING, "Failed to serialize the world list", e);
    }
  }

  private @NotNull List<Map<String, Object>> worlds() {
    return Bukkit.getWorlds().stream()
        .filter(world -> this.taerraConfig.worldEnabled(WorldKeys.id(world)))
        .sorted(
            Comparator.comparingInt(
                    (World world) -> this.taerraConfig.enabledWorlds().indexOf(WorldKeys.id(world)))
                .thenComparingInt(
                    (World world) -> environmentSortOrder(world.getEnvironment().name()))
                .thenComparing(World::getName, String.CASE_INSENSITIVE_ORDER))
        .map(
            world -> {
              final @NotNull Map<String, Object> worldData = new HashMap<>();
              worldData.put("name", WorldKeys.id(world));
              worldData.put(
                  "displayName",
                  this.taerraConfig
                      .worldNames()
                      .getOrDefault(WorldKeys.id(world), WorldKeys.displayName(world.getKey())));
              worldData.put("environment", world.getEnvironment().name());
              worldData.put("private", this.taerraConfig.worldPrivate(WorldKeys.id(world)));
              worldData.put("layered", this.regions.anyFogged(WorldKeys.id(world)));
              worldData.put(
                  "views",
                  this.taerraConfig.views(WorldKeys.id(world)).views().stream()
                      .map(MapView::id)
                      .toList());
              worldData.put(
                  "depth",
                  Map.of(
                      "top_down",
                      this.generatorService.pyramidDepth(
                          TileCoordinate.topDown(WorldKeys.id(world), 0, 0, 0)),
                      "isometric",
                      Arrays.stream(MapRotation.values())
                          .map(
                              rotation ->
                                  this.generatorService.pyramidDepth(
                                      TileCoordinate.isometric(
                                          WorldKeys.id(world), rotation, 0, 0, 0)))
                          .toList()));

              final int @Nullable [] area = this.generatorService.renderedArea(WorldKeys.id(world));
              if (area != null) {
                worldData.put(
                    "bounds",
                    Map.of("minX", area[0], "minZ", area[1], "maxX", area[2], "maxZ", area[3]));
              }

              final @NotNull org.bukkit.Location spawn = world.getSpawnLocation();
              worldData.put(
                  "spawn",
                  Map.of("x", spawn.getBlockX(), "y", spawn.getBlockY(), "z", spawn.getBlockZ()));
              return worldData;
            })
        .toList();
  }
}
