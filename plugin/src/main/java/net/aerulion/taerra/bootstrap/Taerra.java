package net.aerulion.taerra.bootstrap;

import java.io.File;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;
import net.aerulion.taerra.api.TaerraApi;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.chat.RenderFeedback;
import net.aerulion.taerra.command.ConfigSubCommand;
import net.aerulion.taerra.command.DevicesSubCommand;
import net.aerulion.taerra.command.ImportSubCommand;
import net.aerulion.taerra.command.LimitSubCommand;
import net.aerulion.taerra.command.LoginSubCommand;
import net.aerulion.taerra.command.LogoutSubCommand;
import net.aerulion.taerra.command.RenderSubCommand;
import net.aerulion.taerra.command.StatusSubCommand;
import net.aerulion.taerra.command.TaerraCommands;
import net.aerulion.taerra.command.WaypointSubCommand;
import net.aerulion.taerra.config.ConfigEditor;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.config.WebSettings;
import net.aerulion.taerra.integration.ChunkyGeneration;
import net.aerulion.taerra.integration.WorldBorders;
import net.aerulion.taerra.integration.WorldGuardRegions;
import net.aerulion.taerra.limit.HiddenAreaMarkers;
import net.aerulion.taerra.limit.WorldLimits;
import net.aerulion.taerra.listener.JoinHint;
import net.aerulion.taerra.listener.world.WorldChangeListener;
import net.aerulion.taerra.marker.MarkerRegistryImpl;
import net.aerulion.taerra.marker.MarkerRegistryInternal;
import net.aerulion.taerra.permission.TaerraPermission;
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
import net.aerulion.taerra.rendering.sample.ChunkColumnsCache;
import net.aerulion.taerra.rendering.sample.ChunkColumnsCacheImpl;
import net.aerulion.taerra.rendering.style.MapStyle;
import net.aerulion.taerra.rendering.topdown.TopDownTileRendererImpl;
import net.aerulion.taerra.storage.MapImageExports;
import net.aerulion.taerra.storage.TileCache;
import net.aerulion.taerra.storage.TileCacheImpl;
import net.aerulion.taerra.storage.TileStorage;
import net.aerulion.taerra.storage.TileStorageImpl;
import net.aerulion.taerra.util.Throwables;
import net.aerulion.taerra.waypoint.DynmapImport;
import net.aerulion.taerra.waypoint.PersonalWaypoints;
import net.aerulion.taerra.waypoint.Waypoints;
import net.aerulion.taerra.waypoint.WaypointsImpl;
import net.aerulion.taerra.web.auth.ModHandshake;
import net.aerulion.taerra.web.auth.WebGrants;
import net.aerulion.taerra.web.auth.WebSessions;
import net.aerulion.taerra.web.http.TaerraWebServer;
import net.aerulion.taerra.web.http.TaerraWebServerImpl;
import net.aerulion.taerra.web.player.PlayerPositionBroadcaster;
import net.aerulion.taerra.web.player.PlayerPositionBroadcasterImpl;
import net.aerulion.taerra.web.socket.MapWebSocketHandler;
import net.aerulion.taerra.web.socket.MapWebSocketHandlerImpl;
import net.aerulion.taerra.web.socket.TileUpdateBroadcaster;
import net.aerulion.taerra.web.socket.TileUpdateBroadcasterImpl;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import org.bukkit.World;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Taerra extends JavaPlugin {

  private static final long QUEUE_SAVE_INTERVAL_TICKS = 20L * 60L * 5L;

  private @Nullable TileCache tileCache;
  private @Nullable RenderQueue renderQueue;
  private @Nullable TileGeneratorService generatorService;
  private @Nullable TaerraWebServer webServer;
  private @Nullable TileUpdateBroadcaster tileUpdateBroadcaster;
  private @Nullable PlayerPositionBroadcaster playerBroadcaster;
  private @Nullable ChangeBuffer changeBuffer;
  private @Nullable Translator translations;

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
    final @Nullable Translator translations = this.translations;
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
      this.translations = Messages.registerTranslations();

      final @NotNull BlockDataStore blockDataStore = new BlockDataStoreImpl(getLogger());
      final @NotNull MapColorResolver colorResolver =
          new MapColorResolverImpl(getLogger(), blockDataStore, new BiomeTintMapperImpl());

      final @NotNull WorldLimits worldLimits = new WorldLimits(this);
      final @NotNull ChunkColumnsCache chunkColumnsCache =
          new ChunkColumnsCacheImpl(
              this, configuration.maxCacheChunks(), blockDataStore, worldLimits);
      Fluids.reportUnhandled(this);

      final @NotNull TileStorage tileStorage =
          new TileStorageImpl(this, configuration.maxCacheTiles());
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
                  colorResolver, MapStyle.ISO_SHADOW_DISTANCE, MapStyle.TOP_FACE_WEIGHT));

      final @NotNull WebSessions webSessions =
          new WebSessions(new File(getDataFolder(), "sessions.yml"), getLogger());
      webSessions.load();
      final @NotNull MapWebSocketHandler webSocketHandler =
          new MapWebSocketHandlerImpl(this, configuration, webSessions);
      webSessions.onChange(webSocketHandler::revalidate);
      WebGrants.start(this, configuration, webSessions);
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
              configuration.renderThreads());
      this.generatorService = generatorService;

      final @NotNull MarkerRegistryInternal markers = new MarkerRegistryImpl();
      getServer().getPluginManager().registerEvents(markers, this);
      final @NotNull PlayerPositionBroadcaster playerBroadcaster =
          new PlayerPositionBroadcasterImpl(this, configuration, webSocketHandler, worldLimits);
      this.playerBroadcaster = playerBroadcaster;
      final @NotNull WaypointsImpl waypoints = new WaypointsImpl(this, markers);
      waypoints.load();
      getServer().getPluginManager().registerEvents(waypoints, this);
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
              worldLimits,
              configuration);
      final @NotNull RenderFeedback renderFeedback = new RenderFeedback(this);
      final @NotNull PersonalWaypoints personalWaypoints =
          new PersonalWaypoints(this, markers, new File(getDataFolder(), "personal-waypoints"));
      getServer().getPluginManager().registerEvents(personalWaypoints, this);
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
              renderFeedback);
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
      getServer()
          .getPluginManager()
          .registerEvents(
              new WorldChangeListener(
                  changeBuffer,
                  chunkColumnsCache,
                  configuration,
                  isoTileService,
                  chunkyGenerating == null ? _ -> false : chunkyGenerating),
              this);

      playerBroadcaster.start();

      getServer()
          .getServicesManager()
          .register(TaerraApi.class, new TaerraApiImpl(markers), this, ServicePriority.Normal);
      if (configuration.worldGuardRegions()
          && getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
        WorldGuardRegions.start(this, markers, configuration);
        getLogger().info("Hooked into WorldGuard, regions show up on the map");
      }
      if (configuration.worldBorders()) {
        WorldBorders.start(this, markers, configuration);
      }
      HiddenAreaMarkers.start(this, markers, worldLimits);

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
          webServer,
          webSessions,
          markers);

      final long duration = System.currentTimeMillis() - startTime;
      getLogger().info("Enabled in " + duration + "ms");
    } catch (final Exception e) {
      getLogger().log(Level.SEVERE, "Failed to enable: " + Throwables.describe(e), e);
      getServer().getPluginManager().disablePlugin(this);
    }
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
      final @NotNull TaerraWebServer webServer,
      final @NotNull WebSessions webSessions,
      final @NotNull MarkerRegistryInternal markers) {
    TaerraCommands.register(
        this,
        List.of(
            new StatusSubCommand(generatorService, tileCache, isoTileService, chunkColumnsCache),
            new RenderSubCommand(renders, renderFeedback, exports),
            new LimitSubCommand(renders, renderFeedback, worldLimits),
            new WaypointSubCommand(waypoints),
            new ConfigSubCommand(
                new ConfigEditor(
                    this,
                    configuration,
                    markers,
                    () ->
                        getServer().getScheduler().runTaskAsynchronously(this, webServer::restart),
                    webServer::refreshMarkers),
                configuration,
                markers),
            new ImportSubCommand(new DynmapImport(this, waypoints)),
            new LoginSubCommand(webSettings, configuration, webSessions),
            new LogoutSubCommand(webSessions),
            new DevicesSubCommand(webSessions)));
  }
}
