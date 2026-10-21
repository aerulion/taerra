package net.aerulion.taerra.web.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.plugin.bundled.CorsPluginConfig;
import jakarta.servlet.DispatcherType;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import net.aerulion.taerra.chat.RenderFeedback;
import net.aerulion.taerra.config.ConfigEditor;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.config.WebSettings;
import net.aerulion.taerra.marker.MarkerRegistryInternal;
import net.aerulion.taerra.player.Preferences;
import net.aerulion.taerra.region.RegionEditor;
import net.aerulion.taerra.region.RegionVisibility;
import net.aerulion.taerra.rendering.RenderService;
import net.aerulion.taerra.rendering.pipeline.TileGeneratorService;
import net.aerulion.taerra.storage.TileStorage;
import net.aerulion.taerra.waypoint.PersonalWaypoints;
import net.aerulion.taerra.waypoint.WaypointEditor;
import net.aerulion.taerra.waypoint.Waypoints;
import net.aerulion.taerra.web.auth.ModHandshake;
import net.aerulion.taerra.web.auth.WebSessions;
import net.aerulion.taerra.web.http.route.AccountRoutes;
import net.aerulion.taerra.web.http.route.ActionRoutes;
import net.aerulion.taerra.web.http.route.LayerRoutes;
import net.aerulion.taerra.web.http.route.MapRoutes;
import net.aerulion.taerra.web.http.route.NavigationRoutes;
import net.aerulion.taerra.web.http.route.RegionRoutes;
import net.aerulion.taerra.web.http.route.Routes;
import net.aerulion.taerra.web.http.route.SettingsRoutes;
import net.aerulion.taerra.web.http.route.WaypointRoutes;
import net.aerulion.taerra.web.player.PlayerAvatars;
import net.aerulion.taerra.web.player.PlayerPositionBroadcaster;
import net.aerulion.taerra.web.socket.MapWebSocketHandler;
import net.aerulion.taerra.web.socket.MapWebSocketHandlerImpl;
import net.aerulion.taerra.web.stats.RenderStatsPayloadSerializer;
import net.aerulion.taerra.world.Teleports;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TaerraWebServerImpl implements TaerraWebServer {

  private static final int MAX_QUEUED_FRAMES = 64;

  private final @NotNull Plugin plugin;
  private final @NotNull TaerraConfig taerraConfig;
  private final @NotNull WebSettings web;
  private final @NotNull MapWebSocketHandler wsHandler;
  private final @NotNull TileGeneratorService generatorService;
  private final @NotNull MarkerRegistryInternal markers;
  private final @NotNull WorldList worldList;
  private final @NotNull SkyList skyList;
  private final @NotNull List<Routes> routes;
  private final @NotNull ObjectMapper mapper = new ObjectMapper();
  private @Nullable Javalin app;
  private volatile boolean running;
  private @Nullable BukkitTask progressTask;
  private @Nullable BukkitTask worldsTask;

  public TaerraWebServerImpl(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig taerraConfig,
      final @NotNull WebSettings web,
      final @NotNull TileStorage tileStorage,
      final @NotNull MapWebSocketHandler wsHandler,
      final @NotNull TileGeneratorService generatorService,
      final @NotNull MarkerRegistryInternal markers,
      final @NotNull PlayerPositionBroadcaster players,
      final @NotNull WebSessions sessions,
      final @NotNull ModHandshake modHandshake,
      final @NotNull Waypoints waypoints,
      final @NotNull PersonalWaypoints personalWaypoints,
      final @NotNull RenderService renders,
      final @NotNull RenderFeedback renderFeedback,
      final @NotNull Teleports teleports,
      final @NotNull WaypointEditor waypointEditor,
      final @NotNull ConfigEditor configEditor,
      final @NotNull RegionVisibility regionVisibility,
      final @NotNull RegionEditor regionEditor,
      final @NotNull Preferences preferences) {
    super();
    this.plugin = plugin;
    this.taerraConfig = taerraConfig;
    this.web = web;
    this.wsHandler = wsHandler;
    this.generatorService = generatorService;
    this.markers = markers;
    final @NotNull Requests requests = new Requests(sessions, taerraConfig);
    final @NotNull PlayerAvatars avatars = new PlayerAvatars();
    this.worldList =
        new WorldList(
            plugin, taerraConfig, generatorService, wsHandler, regionVisibility.regions());
    this.skyList = new SkyList(plugin, taerraConfig, wsHandler);
    this.routes =
        List.of(
            new MapRoutes(
                plugin,
                requests,
                taerraConfig,
                web,
                tileStorage,
                markers,
                players,
                avatars,
                this.worldList,
                this.skyList,
                regionVisibility),
            new AccountRoutes(plugin, requests, sessions, web, avatars),
            new WaypointRoutes(
                plugin, requests, taerraConfig, waypoints, personalWaypoints, waypointEditor),
            new NavigationRoutes(requests, taerraConfig, wsHandler, preferences),
            new LayerRoutes(requests, wsHandler, preferences),
            new ActionRoutes(
                plugin, requests, taerraConfig, modHandshake, teleports, renders, renderFeedback),
            new SettingsRoutes(requests, configEditor, wsHandler),
            new RegionRoutes(
                requests, taerraConfig, regionVisibility, regionEditor, renderFeedback));
  }

  @Override
  public synchronized void start() {
    final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(this.plugin.getClass().getClassLoader());
    try {
      final @NotNull Javalin app =
          Javalin.create(
              config -> {
                config.jetty.modifyServletContextHandler(
                    handler -> {
                      handler.setClassLoader(this.plugin.getClass().getClassLoader());
                      handler.addFilter(
                          new StaticEtagFilter(this.plugin.getClass().getClassLoader()),
                          "/*",
                          EnumSet.of(DispatcherType.REQUEST));
                    });
                config.jetty.modifyWebSocketServletFactory(
                    factory -> factory.setMaxOutgoingFrames(MAX_QUEUED_FRAMES));
                config.staticFiles.add(
                    staticFiles -> {
                      staticFiles.directory = "web";
                      staticFiles.location = Location.CLASSPATH;
                      staticFiles.headers = Map.of("Cache-Control", "no-cache");
                    });

                config.bundledPlugins.enableCors(
                    cors -> cors.addRule(CorsPluginConfig.CorsRule::anyHost));

                this.routes.forEach(group -> group.register(config.routes));
                config.routes.ws("/ws", this.wsHandler::configure);
              });

      this.app = app;
      if (this.web.nameRejected()) {
        this.plugin
            .getLogger()
            .warning(
                "Ignoring web.name: names are up to 32 letters, digits, spaces and - _ . , : ' & ( ) # ! ? +");
      }
      final @NotNull String host = this.web.host();
      final int port = this.web.port();
      app.start(host, port);
      this.running = true;

      this.progressTask =
          Bukkit.getScheduler()
              .runTaskTimerAsynchronously(
                  this.plugin,
                  () -> {
                    publishProgress();
                    publishMarkerChanges();
                  },
                  20L,
                  20L);
      this.worldsTask =
          Bukkit.getScheduler()
              .runTaskTimer(
                  this.plugin,
                  () -> {
                    this.worldList.publish();
                    this.skyList.publish();
                  },
                  0L,
                  20L);

      this.plugin.getLogger().info("Web server listening on " + host + ":" + port);
      this.plugin.getLogger().info("Open the map at http://localhost:" + port);
    } catch (final Exception e) {
      this.plugin
          .getLogger()
          .log(Level.SEVERE, "Failed to start the web server: " + e.getMessage(), e);
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }
  }

  @Override
  public synchronized void stop() {
    this.running = false;
    if (this.progressTask != null) {
      this.progressTask.cancel();
      this.progressTask = null;
    }
    if (this.worldsTask != null) {
      this.worldsTask.cancel();
      this.worldsTask = null;
    }
    if (this.app != null) {
      this.app.stop();
      this.app = null;
      this.plugin.getLogger().info("Web server stopped");
    }
  }

  @Override
  public synchronized void restart() {
    stop();
    start();
  }

  @Override
  public boolean running() {
    return this.running;
  }

  @Override
  public void refreshMarkers() {
    for (final @NotNull String world : this.taerraConfig.enabledWorlds()) {
      this.wsHandler.broadcastToWorld(world, MapWebSocketHandlerImpl.markersMessage(world));
    }
  }

  private void publishProgress() {
    try {
      final @NotNull Map<String, Object> progress =
          RenderStatsPayloadSerializer.progress(this.generatorService.statusSnapshot());
      final @NotNull Map<String, Object> withoutWorld = new LinkedHashMap<>(progress);
      withoutWorld.remove("render");
      this.wsHandler.publishProgress(
          progress.get("render") instanceof final @NotNull Map<?, ?> render
              ? (String) render.get("world")
              : null,
          this.mapper.writeValueAsString(progress),
          this.mapper.writeValueAsString(withoutWorld));
    } catch (final JsonProcessingException e) {
      this.plugin.getLogger().log(Level.WARNING, "Failed to serialize render progress", e);
    }
  }

  private void publishMarkerChanges() {
    this.markers
        .drainChangedLayers()
        .forEach(
            (world, layers) -> {
              final @NotNull String message =
                  MapWebSocketHandlerImpl.markersMessage(world.asString());
              this.wsHandler.broadcastToWorld(
                  world.asString(),
                  viewer ->
                      layers.stream()
                              .anyMatch(layer -> viewer.canSeeLayer(this.taerraConfig, layer))
                          ? message
                          : null);
            });
    this.markers.drainChangedPlayers().forEach(this.wsHandler::refreshMarkers);
  }
}
