package net.aerulion.taerra.bootstrap;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import net.aerulion.taerra.api.TaerraApi;
import net.aerulion.taerra.api.event.region.RegionDiscoverEvent;
import net.aerulion.taerra.api.event.web.WebLoginEvent;
import net.aerulion.taerra.api.event.web.WebLogoutEvent;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.chat.RenderFeedback;
import net.aerulion.taerra.command.ConfigSubCommand;
import net.aerulion.taerra.command.DebugSubCommand;
import net.aerulion.taerra.command.DevicesSubCommand;
import net.aerulion.taerra.command.ImportSubCommand;
import net.aerulion.taerra.command.LimitSubCommand;
import net.aerulion.taerra.command.LoginSubCommand;
import net.aerulion.taerra.command.LogoutSubCommand;
import net.aerulion.taerra.command.ModSubCommand;
import net.aerulion.taerra.command.RenderSubCommand;
import net.aerulion.taerra.command.StatusSubCommand;
import net.aerulion.taerra.command.TaerraCommands;
import net.aerulion.taerra.command.WaypointSubCommand;
import net.aerulion.taerra.config.ConfigEditor;
import net.aerulion.taerra.config.LiveSettings;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.config.WebSettings;
import net.aerulion.taerra.diagnostics.DebugReport;
import net.aerulion.taerra.icon.MarkerIconsImpl;
import net.aerulion.taerra.integration.ChunkyGeneration;
import net.aerulion.taerra.integration.LuckPermsGrants;
import net.aerulion.taerra.integration.WorldEditChanges;
import net.aerulion.taerra.limit.WorldLimits;
import net.aerulion.taerra.listener.JoinHint;
import net.aerulion.taerra.listener.world.WorldChangeListener;
import net.aerulion.taerra.marker.MarkerRegistryImpl;
import net.aerulion.taerra.marker.MarkerRegistryInternal;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.player.NavigationImpl;
import net.aerulion.taerra.player.PlayerFiles;
import net.aerulion.taerra.player.Preferences;
import net.aerulion.taerra.region.MapRegions;
import net.aerulion.taerra.region.RegionDiscoveries;
import net.aerulion.taerra.region.RegionDiscovery;
import net.aerulion.taerra.region.RegionEditor;
import net.aerulion.taerra.region.RegionVisibility;
import net.aerulion.taerra.region.RegionsImpl;
import net.aerulion.taerra.rendering.RenderService;
import net.aerulion.taerra.rendering.block.Fluids;
import net.aerulion.taerra.rendering.color.BiomeTintMapperImpl;
import net.aerulion.taerra.rendering.color.BlockDataStore;
import net.aerulion.taerra.rendering.color.BlockDataStoreImpl;
import net.aerulion.taerra.rendering.color.Colormaps;
import net.aerulion.taerra.rendering.color.MapColorResolver;
import net.aerulion.taerra.rendering.color.MapColorResolverImpl;
import net.aerulion.taerra.rendering.iso.IsoTileRendererImpl;
import net.aerulion.taerra.rendering.iso.IsoTileService;
import net.aerulion.taerra.rendering.iso.IsoTileServiceImpl;
import net.aerulion.taerra.rendering.pipeline.TileGeneratorService;
import net.aerulion.taerra.rendering.pipeline.TileGeneratorServiceImpl;
import net.aerulion.taerra.rendering.queue.ChangeBuffer;
import net.aerulion.taerra.rendering.queue.ChangeBufferImpl;
import net.aerulion.taerra.rendering.queue.RenderQueue;
import net.aerulion.taerra.rendering.queue.RenderQueueImpl;
import net.aerulion.taerra.rendering.queue.RenderQueueStoreImpl;
import net.aerulion.taerra.rendering.queue.WorldChanges;
import net.aerulion.taerra.rendering.sample.ChunkColumnsCache;
import net.aerulion.taerra.rendering.sample.ChunkColumnsCacheImpl;
import net.aerulion.taerra.rendering.style.MapStyle;
import net.aerulion.taerra.rendering.topdown.TopDownTileRendererImpl;
import net.aerulion.taerra.storage.MapImageExports;
import net.aerulion.taerra.storage.TileCache;
import net.aerulion.taerra.storage.TileCacheImpl;
import net.aerulion.taerra.storage.TileStorage;
import net.aerulion.taerra.storage.TileStorageImpl;
import net.aerulion.taerra.util.Events;
import net.aerulion.taerra.util.HexColors;
import net.aerulion.taerra.util.Throwables;
import net.aerulion.taerra.waypoint.DynmapImport;
import net.aerulion.taerra.waypoint.PersonalWaypoints;
import net.aerulion.taerra.waypoint.WaypointEditor;
import net.aerulion.taerra.waypoint.Waypoints;
import net.aerulion.taerra.waypoint.WaypointsApiImpl;
import net.aerulion.taerra.waypoint.WaypointsImpl;
import net.aerulion.taerra.web.auth.ModHandshake;
import net.aerulion.taerra.web.auth.WebGrants;
import net.aerulion.taerra.web.auth.WebSessions;
import net.aerulion.taerra.web.http.TaerraWebServer;
import net.aerulion.taerra.web.http.TaerraWebServerImpl;
import net.aerulion.taerra.web.marker.RegionMarkers;
import net.aerulion.taerra.web.player.MapPlayersImpl;
import net.aerulion.taerra.web.player.PlayerPositionBroadcaster;
import net.aerulion.taerra.web.player.PlayerPositionBroadcasterImpl;
import net.aerulion.taerra.web.socket.MapWebSocketHandler;
import net.aerulion.taerra.web.socket.MapWebSocketHandlerImpl;
import net.aerulion.taerra.web.socket.TileUpdateBroadcaster;
import net.aerulion.taerra.web.socket.TileUpdateBroadcasterImpl;
import net.aerulion.taerra.world.Teleports;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import org.bukkit.World;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Taerra extends JavaPlugin {

  private static final long QUEUE_SAVE_INTERVAL_TICKS = 20L * 60L * 5L;
  private static final long WEB_RESTART_DELAY = 20L;

  private @Nullable TileCache tileCache;
  private @Nullable TileStorage tileStorage;
  private @Nullable RenderQueue renderQueue;
  private @Nullable TileGeneratorService generatorService;
  private @Nullable TaerraWebServer webServer;
  private @Nullable TileUpdateBroadcaster tileUpdateBroadcaster;
  private @Nullable PlayerPositionBroadcaster playerBroadcaster;
  private @Nullable ChangeBuffer changeBuffer;
  private @Nullable Translator translations;
  private @Nullable PlayerFiles playerFiles;

  static @NotNull String trimVersion(final @NotNull String version) {
    return version.replaceFirst("(\\.0)+$", "");
  }

  @Override
  public void onDisable() {
    final @Nullable PlayerPositionBroadcaster playerBroadcaster = this.playerBroadcaster;
    final @Nullable ChangeBuffer changeBuffer = this.changeBuffer;
    final @Nullable TileUpdateBroadcaster tileUpdateBroadcaster = this.tileUpdateBroadcaster;
    final @Nullable TaerraWebServer webServer = this.webServer;
    final @Nullable TileGeneratorService generatorService = this.generatorService;
    final @Nullable RenderQueue renderQueue = this.renderQueue;
    final @Nullable TileCache tileCache = this.tileCache;
    final @Nullable TileStorage tileStorage = this.tileStorage;
    final @Nullable Translator translations = this.translations;
    final @Nullable PlayerFiles playerFiles = this.playerFiles;
    if (playerBroadcaster != null) {
      shutdownStep("stop player updates", playerBroadcaster::stop);
    }
    if (changeBuffer != null) {
      shutdownStep("queue world changes", changeBuffer::stop);
    }
    if (tileUpdateBroadcaster != null) {
      shutdownStep("stop tile updates", tileUpdateBroadcaster::stop);
    }
    if (webServer != null) {
      shutdownStep("stop the web server", webServer::stop);
    }
    if (generatorService != null) {
      shutdownStep("stop the renderer", generatorService::shutdown);
    }
    if (renderQueue != null) {
      shutdownStep(
          "save the render queue",
          () -> {
            renderQueue.save();
            if (renderQueue.size() > 0) {
              getLogger().info("Saved " + renderQueue.size() + " queued tiles for the next start");
            }
          });
    }
    if (tileCache != null) {
      tileCache.clear();
    }
    if (tileStorage != null) {
      shutdownStep("close the tile storage", tileStorage::close);
    }
    if (playerFiles != null) {
      shutdownStep("save player data", playerFiles::saveAll);
    }
    if (translations != null) {
      GlobalTranslator.translator().removeSource(translations);
    }
  }

  @Override
  public void onEnable() {
    final @Nullable String builtFor = getPluginMeta().getAPIVersion();
    if (builtFor == null
        || !trimVersion(getServer().getMinecraftVersion()).equals(trimVersion(builtFor))) {
      getLogger()
          .severe(
              "This build is for Minecraft "
                  + builtFor
                  + " but the server runs "
                  + getServer().getMinecraftVersion()
                  + ", download the matching TAERRA build");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    final long startTime = System.currentTimeMillis();

    try {
      Colormaps.load(getLogger());

      final @NotNull TaerraConfig configuration = TaerraConfig.load(this);
      final @NotNull WebSettings webSettings = new WebSettings(this);

      TaerraPermission.registerPermissions(this);
      configuration.registerPermissions(this);
      this.translations = Messages.registerTranslations();

      final @NotNull BlockDataStore blockDataStore = new BlockDataStoreImpl(getLogger());
      final @NotNull MapColorResolver colorResolver =
          new MapColorResolverImpl(getLogger(), blockDataStore, new BiomeTintMapperImpl());

      final @NotNull MapRegions regions = new MapRegions(this);
      final @NotNull PlayerFiles playerFiles = new PlayerFiles(this);
      this.playerFiles = playerFiles;
      final @NotNull RegionDiscoveries discoveries = new RegionDiscoveries(playerFiles, regions);
      final @NotNull RegionVisibility regionVisibility = new RegionVisibility(regions, discoveries);
      final @NotNull WorldLimits worldLimits = new WorldLimits(this);
      final @NotNull ChunkColumnsCache chunkColumnsCache =
          new ChunkColumnsCacheImpl(
              this, configuration.maxCacheChunks(), blockDataStore, worldLimits, regions);
      Fluids.reportUnhandled(this);

      final @NotNull TileStorage tileStorage =
          TileStorageImpl.open(this, configuration.maxCacheTiles());
      this.tileStorage = tileStorage;
      final @NotNull TileCache tileCache =
          new TileCacheImpl(tileStorage, configuration.maxCacheTiles());
      this.tileCache = tileCache;
      final @NotNull RenderQueue renderQueue =
          new RenderQueueImpl(
              new RenderQueueStoreImpl(
                  getDataFolder().toPath().resolve("cache/dirty-tiles.json"), getLogger()));
      this.renderQueue = renderQueue;

      final @NotNull IsoTileService isoTileService =
          new IsoTileServiceImpl(
              chunkColumnsCache,
              new IsoTileRendererImpl(
                  colorResolver, MapStyle.ISO_SHADOW_DISTANCE, MapStyle.TOP_FACE_WEIGHT),
              regions);

      final @NotNull WebSessions webSessions =
          new WebSessions(new File(getDataFolder(), "sessions.yml"), getLogger());
      webSessions.load();
      final @NotNull MapWebSocketHandler webSocketHandler =
          new MapWebSocketHandlerImpl(this, configuration, webSessions, webSettings);
      webSessions.onChange(webSocketHandler::revalidate);
      webSessions.onDevices(uuid -> webSocketHandler.sendTo(uuid, Map.of("type", "devices")));
      webSessions.onLogin(
          (session, game) ->
              Events.fire(
                  this,
                  new WebLoginEvent(
                      getServer().getOfflinePlayer(session.uuid()),
                      game,
                      session.device() == null ? null : session.device().browser(),
                      session.device() == null ? null : session.device().os())));
      webSessions.onLogout(
          (session, game) ->
              Events.fire(
                  this,
                  new WebLogoutEvent(
                      getServer().getOfflinePlayer(session.uuid()),
                      game,
                      session.device() == null ? null : session.device().browser(),
                      session.device() == null ? null : session.device().os())));
      WebGrants.start(this, configuration, webSessions);
      if (getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
        LuckPermsGrants.start(this, configuration, webSessions);
      }
      final @NotNull ModHandshake modHandshake =
          new ModHandshake(this, configuration, webSettings, webSessions);
      modHandshake.start();
      getServer()
          .getPluginManager()
          .registerEvents(new JoinHint(this, configuration, webSettings), this);
      final @NotNull TileUpdateBroadcaster tileUpdateBroadcaster =
          new TileUpdateBroadcasterImpl(this, webSocketHandler);
      this.tileUpdateBroadcaster = tileUpdateBroadcaster;

      final @NotNull TileGeneratorService generatorService =
          new TileGeneratorServiceImpl(
              this,
              new TopDownTileRendererImpl(
                  colorResolver, MapStyle.TOP_FACE_WEIGHT, MapStyle.TOP_DOWN_SHADOW_DISTANCE),
              chunkColumnsCache,
              isoTileService,
              tileCache,
              tileStorage,
              renderQueue,
              tileUpdateBroadcaster,
              regions,
              configuration,
              configuration.renderThreads());
      this.generatorService = generatorService;

      final @NotNull MarkerRegistryInternal markers = new MarkerRegistryImpl();
      getServer().getPluginManager().registerEvents(markers, this);
      final @NotNull MarkerIconsImpl icons =
          new MarkerIconsImpl(this, markers, new File(getDataFolder(), "icons"));
      icons.load();
      icons.onChange(() -> webSocketHandler.broadcast("{\"type\":\"icons\"}"));
      getServer().getPluginManager().registerEvents(icons, this);
      final @NotNull MapPlayersImpl mapPlayers = new MapPlayersImpl();
      getServer().getPluginManager().registerEvents(mapPlayers, this);
      final @NotNull PlayerPositionBroadcaster playerBroadcaster =
          new PlayerPositionBroadcasterImpl(
              this, configuration, webSocketHandler, worldLimits, regionVisibility, mapPlayers);
      this.playerBroadcaster = playerBroadcaster;
      final @NotNull WaypointsImpl waypoints = new WaypointsImpl(this, markers, configuration);
      waypoints.load();
      final @NotNull ChangeBuffer changeBuffer =
          new ChangeBufferImpl(this, renderQueue, configuration.updateInterval());
      this.changeBuffer = changeBuffer;
      final @NotNull RenderService renders =
          new RenderService(
              this,
              generatorService,
              renderQueue,
              changeBuffer,
              chunkColumnsCache,
              isoTileService,
              worldLimits,
              configuration);
      final @NotNull RenderFeedback renderFeedback = new RenderFeedback(this);
      final @NotNull PersonalWaypoints personalWaypoints =
          new PersonalWaypoints(this, markers, playerFiles);
      getServer().getPluginManager().registerEvents(personalWaypoints, this);
      final @NotNull LiveSettings liveSettings =
          new LiveSettings(
              this,
              markers,
              configuration,
              worldLimits,
              regions,
              generatorService,
              tileCache,
              tileStorage,
              chunkColumnsCache,
              changeBuffer,
              () ->
                  getServer()
                      .getScheduler()
                      .runTaskLaterAsynchronously(
                          this, () -> onWebServer(TaerraWebServer::restart), WEB_RESTART_DELAY),
              () -> onWebServer(TaerraWebServer::refreshMarkers),
              () -> webSocketHandler.broadcast("{\"type\":\"config\"}"));
      final @NotNull ConfigEditor configEditor =
          new ConfigEditor(
              this,
              configuration,
              markers,
              waypoints,
              worldLimits,
              renders,
              liveSettings::apply,
              () -> onWebServer(TaerraWebServer::refreshMarkers),
              revision ->
                  webSocketHandler.broadcastToConfigurers(
                      "{\"type\":\"settings\",\"revision\":" + revision + "}"),
              icons::exists,
              tileStorage);
      configEditor.start();
      final @NotNull RegionEditor regionEditor =
          new RegionEditor(
              this, regions, renders, () -> webSocketHandler.broadcast("{\"type\":\"regions\"}"));
      final @NotNull Preferences preferences = new Preferences(playerFiles);
      preferences.onNavigation(
          (player, target) -> {
            final @NotNull Map<String, Object> message = new HashMap<>();
            message.put("type", "navigation");
            message.put("target", target);
            webSocketHandler.sendTo(player, message);
          });
      preferences.onWaypointColor(
          (player, rgb) ->
              webSocketHandler.sendTo(
                  player, Map.of("type", "waypointColor", "color", HexColors.format(rgb))));
      final @NotNull TaerraWebServer webServer =
          new TaerraWebServerImpl(
              this,
              configuration,
              webSettings,
              tileStorage,
              webSocketHandler,
              generatorService,
              markers,
              playerBroadcaster,
              webSessions,
              modHandshake,
              waypoints,
              personalWaypoints,
              renders,
              renderFeedback,
              new Teleports(this),
              new WaypointEditor(this, configuration, waypoints, personalWaypoints, preferences),
              configEditor,
              regionVisibility,
              regionEditor,
              preferences,
              icons);
      this.webServer = webServer;
      webServer.start();
      tileUpdateBroadcaster.start();
      generatorService.start();

      getServer()
          .getScheduler()
          .runTaskTimerAsynchronously(
              this, renderQueue::save, QUEUE_SAVE_INTERVAL_TICKS, QUEUE_SAVE_INTERVAL_TICKS);

      changeBuffer.start();

      final @Nullable Predicate<World> chunkyGenerating =
          getServer().getPluginManager().isPluginEnabled("Chunky")
              ? ChunkyGeneration.start(this)
              : null;
      final @NotNull WorldChanges worldChanges =
          new WorldChanges(
              changeBuffer,
              chunkColumnsCache,
              configuration,
              isoTileService,
              chunkyGenerating == null ? _ -> false : chunkyGenerating);
      getServer().getPluginManager().registerEvents(new WorldChangeListener(worldChanges), this);
      if (getServer().getPluginManager().isPluginEnabled("WorldEdit")) {
        WorldEditChanges.start(this, worldChanges);
      }

      playerBroadcaster.start();

      final @NotNull RegionDiscovery regionDiscovery =
          new RegionDiscovery(
              this,
              configuration,
              regions,
              discoveries,
              modHandshake,
              (uuid, name) ->
                  webSocketHandler.sendTo(
                      uuid,
                      name.isEmpty()
                          ? Map.of("type", "regions")
                          : Map.of("type", "regions", "discovered", name)));
      getServer()
          .getServicesManager()
          .register(
              TaerraApi.class,
              new TaerraApiImpl(
                  this,
                  markers,
                  icons,
                  new RegionsImpl(
                      regions,
                      discoveries,
                      (uuid, region) ->
                          regionDiscovery.discover(uuid, region, RegionDiscoverEvent.Cause.PLUGIN)),
                  new WaypointsApiImpl(this, waypoints, personalWaypoints),
                  new NavigationImpl(preferences),
                  mapPlayers,
                  webSettings,
                  worldChanges),
              this,
              ServicePriority.Normal);
      liveSettings.startLayers();
      markers.layer(this, RegionMarkers.LAYER, RegionMarkers.LABEL, true);
      regionDiscovery.start();

      registerCommands(
          generatorService,
          renders,
          renderFeedback,
          new MapImageExports(this, tileStorage),
          tileCache,
          isoTileService,
          chunkColumnsCache,
          waypoints,
          worldLimits,
          configuration,
          webSettings,
          configEditor,
          webSessions,
          modHandshake,
          markers,
          icons);

      final long duration = System.currentTimeMillis() - startTime;
      getLogger().info("Enabled in " + duration + "ms");
    } catch (final Exception e) {
      getLogger().log(Level.SEVERE, "Failed to enable: " + Throwables.describe(e), e);
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  private void onWebServer(final @NotNull Consumer<TaerraWebServer> action) {
    final @Nullable TaerraWebServer webServer = this.webServer;
    if (webServer != null) {
      action.accept(webServer);
    }
  }

  private boolean webRunning() {
    final @Nullable TaerraWebServer webServer = this.webServer;
    return webServer != null && webServer.running();
  }

  private void shutdownStep(final @NotNull String step, final @NotNull Runnable action) {
    try {
      action.run();
    } catch (final Exception e) {
      getLogger().log(Level.SEVERE, "Failed to " + step + ": " + Throwables.describe(e), e);
    }
  }

  private void registerCommands(
      final @NotNull TileGeneratorService generatorService,
      final @NotNull RenderService renders,
      final @NotNull RenderFeedback renderFeedback,
      final @NotNull MapImageExports exports,
      final @NotNull TileCache tileCache,
      final @NotNull IsoTileService isoTileService,
      final @NotNull ChunkColumnsCache chunkColumnsCache,
      final @NotNull Waypoints waypoints,
      final @NotNull WorldLimits worldLimits,
      final @NotNull TaerraConfig configuration,
      final @NotNull WebSettings webSettings,
      final @NotNull ConfigEditor configEditor,
      final @NotNull WebSessions webSessions,
      final @NotNull ModHandshake modHandshake,
      final @NotNull MarkerRegistryInternal markers,
      final @NotNull MarkerIconsImpl icons) {
    TaerraCommands.register(
        this,
        List.of(
            new StatusSubCommand(
                generatorService, tileCache, isoTileService, chunkColumnsCache, tileStorage),
            new RenderSubCommand(renders, renderFeedback, exports),
            new LimitSubCommand(configEditor, renderFeedback, worldLimits),
            new WaypointSubCommand(waypoints),
            new ConfigSubCommand(configEditor, configuration, markers, icons, renderFeedback),
            new ImportSubCommand(new DynmapImport(this, waypoints)),
            new LoginSubCommand(webSettings, configuration, webSessions),
            new LogoutSubCommand(webSessions),
            new DevicesSubCommand(webSessions),
            new ModSubCommand(modHandshake),
            new DebugSubCommand(
                new DebugReport(
                    this,
                    webSettings,
                    this::webRunning,
                    configuration,
                    generatorService,
                    modHandshake))));
  }
}
