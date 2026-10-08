package net.aerulion.taerra.web.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;
import io.javalin.http.staticfiles.Location;
import io.javalin.plugin.bundled.CorsPluginConfig;
import jakarta.servlet.DispatcherType;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.aerulion.taerra.command.Messages;
import net.aerulion.taerra.command.RenderSubCommand;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.marker.MarkerRegistryInternal;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.rendering.iso.IsoTileGeometry;
import net.aerulion.taerra.rendering.pipeline.TileGeneratorService;
import net.aerulion.taerra.storage.EncodedTile;
import net.aerulion.taerra.storage.TileStorage;
import net.aerulion.taerra.util.HexColors;
import net.aerulion.taerra.util.Names;
import net.aerulion.taerra.waypoint.Waypoints;
import net.aerulion.taerra.web.auth.ModHandshake;
import net.aerulion.taerra.web.auth.Viewer;
import net.aerulion.taerra.web.auth.WebSessions;
import net.aerulion.taerra.web.marker.MarkerPayloadSerializer;
import net.aerulion.taerra.web.player.PlayerAvatars;
import net.aerulion.taerra.web.player.PlayerPositionBroadcaster;
import net.aerulion.taerra.web.socket.MapWebSocketHandler;
import net.aerulion.taerra.web.stats.RenderStatsPayloadSerializer;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TaerraWebServerImpl implements TaerraWebServer {

  private static final int MAX_QUEUED_FRAMES = 64;
  private static final int MAX_AREA_CHUNKS = 16384;

  private final @NotNull Plugin plugin;
  private final @NotNull TaerraConfig taerraConfig;
  private final @NotNull TileStorage tileStorage;
  private final @NotNull MapWebSocketHandler wsHandler;
  private final @NotNull TileGeneratorService generatorService;
  private final @NotNull MarkerRegistryInternal markers;
  private final @NotNull PlayerPositionBroadcaster players;
  private final @NotNull WebSessions sessions;
  private final @NotNull ModHandshake modHandshake;
  private final @NotNull Waypoints waypoints;
  private final @NotNull RenderSubCommand render;
  private final @NotNull ObjectMapper mapper = new ObjectMapper();
  private final @NotNull PlayerAvatars avatars = new PlayerAvatars();
  private @Nullable Javalin app;
  private @Nullable BukkitTask progressTask;
  private @Nullable BukkitTask worldsTask;
  private volatile @NotNull List<Map<String, Object>> publishedWorlds = List.of();
  private volatile @Nullable String publishedWorldsJson;
  private @NotNull String indexHtml = "";
  private @NotNull ObjectNode manifest = JsonNodeFactory.instance.objectNode();

  public TaerraWebServerImpl(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig taerraConfig,
      final @NotNull TileStorage tileStorage,
      final @NotNull MapWebSocketHandler wsHandler,
      final @NotNull TileGeneratorService generatorService,
      final @NotNull MarkerRegistryInternal markers,
      final @NotNull PlayerPositionBroadcaster players,
      final @NotNull WebSessions sessions,
      final @NotNull ModHandshake modHandshake,
      final @NotNull Waypoints waypoints,
      final @NotNull RenderSubCommand render) {
    super();
    this.plugin = plugin;
    this.taerraConfig = taerraConfig;
    this.tileStorage = tileStorage;
    this.wsHandler = wsHandler;
    this.generatorService = generatorService;
    this.markers = markers;
    this.players = players;
    this.sessions = sessions;
    this.modHandshake = modHandshake;
    this.waypoints = waypoints;
    this.render = render;
  }

  private static int environmentSortOrder(final @NotNull String environmentName) {
    return switch (environmentName) {
      case "NORMAL" -> 0;
      case "NETHER" -> 1;
      case "THE_END" -> 2;
      default -> 3;
    };
  }

  private static void respondWithTile(
      final @NotNull Context ctx, final @NotNull Optional<EncodedTile> tile) {
    if (tile.isEmpty()) {

      ctx.header("Cache-Control", "no-store").status(404).result("Tile not found");
      return;
    }

    final @NotNull EncodedTile encodedTile = tile.get();

    final boolean versioned = ctx.queryParam("v") != null;
    ctx.header("Cache-Control", versioned ? "private, max-age=31536000, immutable" : "no-cache")
        .header("ETag", encodedTile.eTag());

    if (encodedTile.eTag().equals(ctx.header("If-None-Match"))) {
      ctx.status(304);
      return;
    }

    ctx.contentType("image/png").result(encodedTile.data());
  }

  private static boolean sameOrigin(final @NotNull Context ctx) {
    final @Nullable String site = ctx.header("Sec-Fetch-Site");
    return site == null ? ctx.header("Origin") == null : site.equals("same-origin");
  }

  private static boolean secure(final @NotNull Context ctx) {
    return "https".equals(ctx.scheme())
        || "https".equalsIgnoreCase(ctx.header("X-Forwarded-Proto"));
  }

  private static @NotNull Map<String, Object> account(
      final WebSessions.@NotNull Session session, final @NotNull Viewer viewer) {
    return Map.of(
        "uuid", session.uuid().toString(),
        "name", session.name(),
        "teleport", viewer.canTeleport(),
        "markers", viewer.canEditMarkers(),
        "privateGroups", viewer.canCreatePrivateGroups(),
        "render", viewer.canRender(),
        "hide", viewer.canHide());
  }

  private static void respondWithPick(
      final @NotNull Context ctx, final @NotNull Optional<byte[]> pick) {
    if (pick.isEmpty()) {
      ctx.status(404).result("Pick buffer not found");
      return;
    }
    final @NotNull String eTag = EncodedTile.eTagOf(pick.get());
    ctx.header("Cache-Control", "no-cache").header("ETag", eTag);
    if (eTag.equals(ctx.header("If-None-Match"))) {
      ctx.status(304);
      return;
    }
    ctx.contentType("application/octet-stream").result(pick.get());
  }

  private static boolean insideBorder(final @NotNull World world, final int x, final int z) {
    return world.getWorldBorder().isInside(new org.bukkit.Location(world, x + .5, 0, z + .5));
  }

  private static @NotNull CompletableFuture<Integer> standingY(
      final @NotNull World world, final int x, final @Nullable Integer y, final int z) {
    return y != null
        ? CompletableFuture.completedFuture(
            Math.clamp(y, world.getMinHeight(), world.getMaxHeight()))
        : world
            .getChunkAtAsync(x >> 4, z >> 4)
            .thenApply(chunk -> world.getHighestBlockYAt(x, z) + 1);
  }

  private @NotNull Viewer viewer(final @NotNull Context ctx) {
    return this.sessions.viewer(ctx.cookie(WebSessions.COOKIE));
  }

  private boolean visible(final @NotNull Context ctx, final @NotNull String worldName) {
    return viewer(ctx).canSee(this.taerraConfig, worldName);
  }

  private void handlePlayersRequest(final @NotNull Context ctx) {
    ctx.header("Cache-Control", "no-cache")
        .contentType("application/json")
        .result(this.players.players(viewer(ctx)));
  }

  private static @NotNull String escapeHtml(final @NotNull String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  static @NotNull String indexPage(
      final @NotNull String template, final @NotNull String name, final @NotNull String publicUrl) {
    @NotNull String html = template;
    if (!publicUrl.isEmpty()) {
      try {
        html =
            html.replace(
                "content=\"images/og.png\"",
                "content=\"" + URI.create(publicUrl + "/images/og.png").toASCIIString() + "\"");
      } catch (final IllegalArgumentException ignored) {
      }
    }
    if (name.isEmpty()) {
      return html;
    }
    final @NotNull String escaped = escapeHtml(name);
    return html.replace("<title>TAERRA</title>", "<title>" + escaped + "</title>")
        .replace(
            "content=\"TAERRA\" property=\"og:title\"",
            "content=\"" + escaped + "\" property=\"og:title\"")
        .replace(
            "content=\"TAERRA\" property=\"og:image:alt\"",
            "content=\"" + escaped + "\" property=\"og:image:alt\"")
        .replace(
            "<span class=\"server-name\" hidden id=\"server-name\"></span>",
            "<span class=\"server-name\" id=\"server-name\">" + escaped + "</span>");
  }

  private @NotNull String serverName() {
    final @Nullable String name = Names.of(this.plugin.getConfig().getString("web.name", ""));
    return name == null ? "" : name;
  }

  private void handleIndexRequest(final @NotNull Context ctx) {
    ctx.header("Cache-Control", "no-cache")
        .html(
            indexPage(
                this.indexHtml,
                serverName(),
                this.plugin
                    .getConfig()
                    .getString("web.public-url", "")
                    .strip()
                    .replaceAll("/+$", "")));
  }

  private void handleManifestRequest(final @NotNull Context ctx) {
    final @NotNull ObjectNode manifest = this.manifest.deepCopy();
    final @NotNull String name = serverName();
    if (!name.isEmpty()) {
      manifest.put("name", name).put("short_name", name);
    }
    ctx.header("Cache-Control", "no-cache")
        .contentType("application/manifest+json")
        .result(manifest.toString());
  }

  private void handleConfigRequest(final @NotNull Context ctx) {
    final @NotNull String name = serverName();
    ctx.json(
        Map.of(
            "name",
            name,
            "tileSize",
            TileGeometry.TILE_SIZE,
            "maxZoom",
            TileGeometry.LEAFLET_GROUND_ZOOM,
            "isometric",
            Map.of(
                "tileSize",
                IsoTileGeometry.TILE_SIZE,
                "maxZoom",
                IsoTileGeometry.LEAFLET_GROUND_ZOOM,
                "blockWidth",
                IsoTileGeometry.BASE_BLOCK_WIDTH)));
  }

  @Override
  public synchronized void start() {
    final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(this.plugin.getClass().getClassLoader());
    try (final @Nullable InputStream index = this.plugin.getResource("web/index.html")) {
      if (index != null) {
        this.indexHtml = new String(index.readAllBytes(), StandardCharsets.UTF_8);
      }
      try (final @Nullable InputStream manifest = this.plugin.getResource("web/manifest.json")) {
        if (manifest != null) {
          this.manifest = (ObjectNode) this.mapper.readTree(manifest);
        }
      }
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

                config.routes.get("/api/tiles/{world}/{zoom}/{x}/{z}.png", this::handleTileRequest);
                config.routes.get("/", this::handleIndexRequest);
                config.routes.get("/api/worlds", this::handleWorldsRequest);
                config.routes.get("/api/config", this::handleConfigRequest);
                config.routes.get("/api/manifest.json", this::handleManifestRequest);
                config.routes.get(
                    "/api/iso/{world}/{rotation}/{zoom}/{x}/{y}.png", this::handleIsoTileRequest);
                config.routes.get(
                    "/api/iso/{world}/{rotation}/{x}/{y}.pick", this::handleIsoPickRequest);
                config.routes.get(
                    "/api/tiles/{world}/{x}/{z}.pick", this::handleTopDownPickRequest);
                config.routes.get("/api/players", this::handlePlayersRequest);
                config.routes.get("/api/markers/{world}", this::handleMarkersRequest);
                config.routes.get("/api/avatars/{uuid}.png", this::handleAvatarRequest);
                config.routes.get("/api/account", this::handleAccountRequest);
                config.routes.get("/api/account/avatar.png", this::handleAccountAvatarRequest);
                config.routes.post("/api/login", this::handleLoginRequest);
                config.routes.post("/api/logout", this::handleLogoutRequest);
                config.routes.post("/api/teleport", this::handleTeleportRequest);
                config.routes.get("/api/waypoints/groups", this::handleWaypointGroupsRequest);
                config.routes.put("/api/waypoints", this::handleWaypointSaveRequest);
                config.routes.delete("/api/waypoints/{name}", this::handleWaypointDeleteRequest);
                config.routes.post("/api/area", this::handleAreaRequest);

                config.routes.ws("/ws", this.wsHandler::configure);
              });

      this.app = app;
      if (!this.plugin.getConfig().getString("web.name", "").isBlank() && serverName().isEmpty()) {
        this.plugin
            .getLogger()
            .warning(
                "Ignoring web.name: names are up to 32 letters, digits, spaces and - _ . , : ' & ( ) # ! ? +");
      }
      final @NotNull String host = this.plugin.getConfig().getString("web.host", "0.0.0.0");
      final int port = this.plugin.getConfig().getInt("web.port", 8100);
      app.start(host, port);

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
          Bukkit.getScheduler().runTaskTimer(this.plugin, this::publishWorldChanges, 0L, 20L);

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
  public void refreshMarkers() {
    for (final @NotNull String world : this.taerraConfig.enabledWorlds()) {
      this.wsHandler.broadcastToWorld(world, "{\"type\":\"markers\",\"world\":\"" + world + "\"}");
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
    for (final @NotNull NamespacedKey world : this.markers.drainChangedWorlds()) {
      this.wsHandler.broadcastToWorld(
          world.asString(), "{\"type\":\"markers\",\"world\":\"" + world.asString() + "\"}");
    }
  }

  private void publishWorldChanges() {
    try {
      final @NotNull List<Map<String, Object>> worlds = worlds();
      final @NotNull String json = this.mapper.writeValueAsString(worlds);
      this.publishedWorlds = worlds;
      if (this.publishedWorldsJson != null && !json.equals(this.publishedWorldsJson)) {
        this.wsHandler.revalidate();
        this.wsHandler.broadcast("{\"type\":\"worlds\"}");
      }
      this.publishedWorldsJson = json;
    } catch (final JsonProcessingException e) {
      this.plugin.getLogger().log(Level.WARNING, "Failed to serialize the world list", e);
    }
  }

  private void handleAccountRequest(final @NotNull Context ctx) {
    ctx.header("Cache-Control", "no-store");
    final WebSessions.@Nullable Session session =
        this.sessions.session(ctx.cookie(WebSessions.COOKIE));
    if (session == null) {
      ctx.status(204);
      return;
    }
    ctx.json(account(session, viewer(ctx)));
  }

  private void handleAccountAvatarRequest(final @NotNull Context ctx) {
    final WebSessions.@Nullable Session session =
        this.sessions.session(ctx.cookie(WebSessions.COOKIE));
    final @Nullable String skin = session == null ? null : session.skin();
    final byte @Nullable [] png = skin == null ? null : this.avatars.png(skin);
    if (png == null) {
      ctx.header("Cache-Control", "no-store").status(404).result("Avatar not found");
      return;
    }
    ctx.header("Cache-Control", "private, max-age=3600").contentType("image/png").result(png);
  }

  private void handleLoginRequest(final @NotNull Context ctx) {
    if (!sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    final WebSessions.@Nullable Login login = this.sessions.redeem(ctx.body().strip());
    if (login == null) {
      ctx.status(401).result("Invalid or expired code");
      return;
    }
    ctx.cookie(
        new Cookie(
            WebSessions.COOKIE,
            login.token(),
            "/",
            (int) WebSessions.SESSION_LIFETIME.toSeconds(),
            secure(ctx),
            true,
            null,
            SameSite.STRICT));
    Bukkit.getScheduler()
        .runTask(
            this.plugin,
            () -> {
              final @Nullable Player player = Bukkit.getPlayer(login.session().uuid());
              if (player != null) {
                player.sendMessage(Messages.webLogin());
              }
            });
    ctx.header("Cache-Control", "no-store")
        .json(account(login.session(), this.sessions.viewer(login.token())));
  }

  private void handleLogoutRequest(final @NotNull Context ctx) {
    if (!sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    this.sessions.end(ctx.cookie(WebSessions.COOKIE));
    ctx.removeCookie(WebSessions.COOKIE, "/");
    ctx.status(204);
  }

  private void handleTeleportRequest(final @NotNull Context ctx) {
    if (!sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    final @NotNull Viewer viewer = viewer(ctx);
    final @Nullable UUID uuid = viewer.uuid();
    if (uuid == null) {
      ctx.status(401).result("Not logged in");
      return;
    }
    final @NotNull TeleportRequest request;
    try {
      request = ctx.bodyAsClass(TeleportRequest.class);
    } catch (final Exception e) {
      ctx.status(400).result("Invalid teleport");
      return;
    }
    if (request.world() == null
        || request.x() == null
        || request.z() == null
        || !viewer.canSee(this.taerraConfig, request.world())) {
      ctx.status(400).result("Invalid teleport");
      return;
    }
    final boolean announce = !this.modHandshake.isMod(ctx.cookie(WebSessions.COOKIE));
    ctx.future(() -> teleport(uuid, request, announce).thenAccept(status -> ctx.status(status)));
  }

  private @NotNull CompletableFuture<Integer> teleport(
      final @NotNull UUID uuid, final @NotNull TeleportRequest request, final boolean announce) {
    final @NotNull CompletableFuture<Integer> result = new CompletableFuture<>();
    Bukkit.getScheduler()
        .runTask(
            this.plugin,
            () -> {
              final @Nullable Player player = Bukkit.getPlayer(uuid);
              final @Nullable World world = WorldKeys.resolve(request.world());
              if (player == null) {
                result.complete(409);
                return;
              }
              if (!TaerraPermission.WEB_TELEPORT.check(player) || world == null) {
                result.complete(403);
                return;
              }
              final int x = request.x();
              final int z = request.z();
              if (!insideBorder(world, x, z)) {
                result.complete(400);
                return;
              }
              standingY(world, x, request.y(), z)
                  .thenCompose(
                      y ->
                          player
                              .teleportAsync(
                                  new org.bukkit.Location(
                                      world, x + .5, y, z + .5, player.getYaw(), player.getPitch()),
                                  PlayerTeleportEvent.TeleportCause.PLUGIN)
                              .thenApply(
                                  teleported -> {
                                    if (teleported && announce) {
                                      player.sendMessage(
                                          Messages.webTeleport(
                                              x + " " + y + " " + z, WorldKeys.id(world)));
                                    }
                                    return teleported ? 204 : 409;
                                  }))
                  .whenComplete((status, error) -> result.complete(error == null ? status : 500));
            });
    return result;
  }

  private void handleWaypointGroupsRequest(final @NotNull Context ctx) {
    final @NotNull Viewer viewer = viewer(ctx);
    if (!viewer.canEditMarkers()) {
      ctx.status(403).result("Not allowed");
      return;
    }
    ctx.header("Cache-Control", "no-store")
        .json(
            this.waypoints.groups().stream()
                .filter(group -> viewer.canSeeLayer(this.taerraConfig, group.id()))
                .map(
                    group ->
                        Map.of(
                            "id", group.id(),
                            "label", group.label(),
                            "private", this.taerraConfig.layerPrivate(group.id())))
                .toList());
  }

  private void handleWaypointSaveRequest(final @NotNull Context ctx) {
    if (!sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    final @NotNull Viewer viewer = viewer(ctx);
    if (!viewer.canEditMarkers()) {
      ctx.status(403).result("Not allowed");
      return;
    }
    final @NotNull WaypointRequest request;
    try {
      request = ctx.bodyAsClass(WaypointRequest.class);
    } catch (final Exception e) {
      ctx.status(400).result("Invalid waypoint");
      return;
    }
    final @Nullable String name = Names.of(request.name());
    final boolean grouped = request.group() != null && !request.group().isBlank();
    final @Nullable String group = grouped ? Names.of(request.group()) : null;
    final @NotNull OptionalInt color = HexColors.parseRgb(request.color());
    if (name == null
        || (grouped && group == null)
        || color.isEmpty()
        || request.world() == null
        || request.x() == null
        || request.z() == null
        || !viewer.canSee(this.taerraConfig, request.world())) {
      ctx.status(400).result("Invalid waypoint");
      return;
    }
    final @Nullable String groupId = Waypoints.groupLayerId(group);
    if (!viewer.canSeeLayer(this.taerraConfig, groupId == null ? Waypoints.LAYER : groupId)) {
      ctx.status(403).result("Not allowed");
      return;
    }
    final boolean renamed =
        request.previous() == null || !request.previous().equalsIgnoreCase(name);
    final @NotNull CompletableFuture<Integer> result = new CompletableFuture<>();
    Bukkit.getScheduler()
        .runTask(
            this.plugin,
            () -> {
              final @Nullable World world = WorldKeys.resolve(request.world());
              if (world == null || !insideBorder(world, request.x(), request.z())) {
                result.complete(400);
                return;
              }
              standingY(world, request.x(), request.y(), request.z())
                  .thenAccept(
                      y -> {
                        if (!canEditWaypoint(viewer, request.previous())) {
                          result.complete(403);
                          return;
                        }
                        if (renamed
                            && this.waypoints.names().stream().anyMatch(name::equalsIgnoreCase)) {
                          result.complete(409);
                          return;
                        }
                        if (groupId != null
                            && request.privateGroup()
                            && viewer.canCreatePrivateGroups()
                            && this.waypoints.groups().stream()
                                .noneMatch(known -> known.id().equals(groupId))) {
                          this.taerraConfig.setLayerPrivate(this.plugin, groupId, true);
                        }
                        if (renamed && request.previous() != null) {
                          this.waypoints.remove(request.previous());
                        }
                        this.waypoints.add(
                            name,
                            new org.bukkit.Location(world, request.x(), y, request.z()),
                            Color.fromRGB(color.getAsInt()),
                            group);
                        result.complete(204);
                      })
                  .exceptionally(
                      error -> {
                        result.complete(500);
                        return null;
                      });
            });
    ctx.future(() -> result.thenAccept(status -> ctx.status(status)));
  }

  private void handleWaypointDeleteRequest(final @NotNull Context ctx) {
    if (!sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    final @NotNull Viewer viewer = viewer(ctx);
    if (!viewer.canEditMarkers()) {
      ctx.status(403).result("Not allowed");
      return;
    }
    final @NotNull String name = ctx.pathParam("name");
    final @NotNull CompletableFuture<Integer> result = new CompletableFuture<>();
    Bukkit.getScheduler()
        .runTask(
            this.plugin,
            () ->
                result.complete(
                    canEditWaypoint(viewer, name) && this.waypoints.remove(name) ? 204 : 404));
    ctx.future(() -> result.thenAccept(status -> ctx.status(status)));
  }

  private void handleAreaRequest(final @NotNull Context ctx) {
    if (!sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    final @NotNull Viewer viewer = viewer(ctx);
    final @NotNull AreaRequest request;
    try {
      request = ctx.bodyAsClass(AreaRequest.class);
    } catch (final Exception e) {
      ctx.status(400).result("Invalid area");
      return;
    }
    final @Nullable Boolean hide =
        "hide".equals(request.action())
            ? Boolean.TRUE
            : "reveal".equals(request.action()) ? Boolean.FALSE : null;
    if ((hide == null && !"render".equals(request.action()))
        || request.world() == null
        || request.chunks() == null
        || request.chunks().isEmpty()
        || request.chunks().size() > MAX_AREA_CHUNKS
        || request.chunks().stream().anyMatch(chunk -> chunk == null || chunk.length != 2)
        || !viewer.canSee(this.taerraConfig, request.world())) {
      ctx.status(400).result("Invalid area");
      return;
    }
    if (hide == null ? !viewer.canRender() : !viewer.canHide()) {
      ctx.status(403).result("Not allowed");
      return;
    }
    final @NotNull Set<Long> chunks =
        request.chunks().stream()
            .map(chunk -> ChunkCoordinate.key(chunk[0], chunk[1]))
            .collect(Collectors.toUnmodifiableSet());
    final @NotNull CompletableFuture<Integer> result = new CompletableFuture<>();
    Bukkit.getScheduler()
        .runTask(
            this.plugin,
            () -> {
              final @Nullable World world = WorldKeys.resolve(request.world());
              if (world == null) {
                result.complete(400);
                return;
              }
              final @Nullable Player player =
                  viewer.uuid() == null ? null : Bukkit.getPlayer(viewer.uuid());
              result.complete(
                  this.render.chunks(
                          player == null ? Audience.empty() : player, world, chunks, hide)
                      ? 202
                      : 409);
            });
    ctx.future(() -> result.thenAccept(status -> ctx.status(status)));
  }

  private boolean canEditWaypoint(final @NotNull Viewer viewer, final @Nullable String name) {
    final @Nullable String layer = name == null ? null : this.waypoints.layerOf(name);
    return layer == null || viewer.canSeeLayer(this.taerraConfig, layer);
  }

  private void handleAvatarRequest(final @NotNull Context ctx) {
    final @NotNull UUID uuid;
    try {
      uuid = UUID.fromString(ctx.pathParam("uuid"));
    } catch (final IllegalArgumentException e) {
      ctx.status(400).result("Invalid UUID");
      return;
    }
    final @Nullable Player player = this.players.shown(uuid, viewer(ctx).canSeeHidden());
    final byte @Nullable [] png =
        player == null || !visible(ctx, WorldKeys.id(player.getWorld()))
            ? null
            : this.avatars.png(player);
    if (png == null) {
      ctx.header("Cache-Control", "no-store").status(404).result("Avatar not found");
      return;
    }
    ctx.header("Cache-Control", "private, max-age=3600").contentType("image/png").result(png);
  }

  private void handleMarkersRequest(final @NotNull Context ctx) {
    final @NotNull String worldName = ctx.pathParam("world");
    final @NotNull Viewer viewer = viewer(ctx);
    if (!viewer.canSee(this.taerraConfig, worldName)) {
      ctx.status(404).result("Not found");
      return;
    }
    ctx.header("Cache-Control", "no-cache")
        .json(
            MarkerPayloadSerializer.world(
                this.markers,
                worldName,
                layer -> viewer.canSeeLayer(this.taerraConfig, layer),
                this.taerraConfig::layerPrivate));
  }

  private void handleIsoPickRequest(final @NotNull Context ctx) {
    final int rotationIndex;
    final int tileX;
    final int tileY;
    try {
      rotationIndex = Integer.parseInt(ctx.pathParam("rotation"));
      tileX = Integer.parseInt(ctx.pathParam("x"));
      tileY = Integer.parseInt(ctx.pathParam("y"));
    } catch (final NumberFormatException e) {
      ctx.status(400).result("Invalid coordinates");
      return;
    }

    final @Nullable MapRotation rotation = MapRotation.ofIndex(rotationIndex);
    final @NotNull String worldName = ctx.pathParam("world");
    if (rotation == null || !visible(ctx, worldName)) {
      ctx.status(404).result("Not found");
      return;
    }

    respondWithPick(
        ctx,
        this.tileStorage.readPick(TileCoordinate.isometric(worldName, rotation, 0, tileX, tileY)));
  }

  private void handleTopDownPickRequest(final @NotNull Context ctx) {
    final int tileX;
    final int tileZ;
    try {
      tileX = Integer.parseInt(ctx.pathParam("x"));
      tileZ = Integer.parseInt(ctx.pathParam("z"));
    } catch (final NumberFormatException e) {
      ctx.status(400).result("Invalid coordinates");
      return;
    }
    final @NotNull String worldName = ctx.pathParam("world");
    if (!visible(ctx, worldName)) {
      ctx.status(404).result("Not found");
      return;
    }
    respondWithPick(
        ctx, this.tileStorage.readPick(TileCoordinate.topDown(worldName, 0, tileX, tileZ)));
  }

  private void handleIsoTileRequest(final @NotNull Context ctx) {
    final int leafletZoom;
    final int rotationIndex;
    final int tileX;
    final int tileY;
    try {
      rotationIndex = Integer.parseInt(ctx.pathParam("rotation"));
      leafletZoom = Integer.parseInt(ctx.pathParam("zoom"));
      tileX = Integer.parseInt(ctx.pathParam("x"));
      tileY = Integer.parseInt(ctx.pathParam("y"));
    } catch (final NumberFormatException e) {
      ctx.status(400).result("Invalid coordinates");
      return;
    }

    if (!IsoTileGeometry.isValidLeafletZoom(leafletZoom)) {
      ctx.status(400).result("Invalid zoom level");
      return;
    }

    final @Nullable MapRotation rotation = MapRotation.ofIndex(rotationIndex);
    if (rotation == null) {
      ctx.status(404).result("Rotation not served");
      return;
    }

    final @NotNull String worldName = ctx.pathParam("world");
    if (!visible(ctx, worldName)) {
      ctx.status(404).result("World not found");
      return;
    }

    respondWithTile(
        ctx,
        this.tileStorage.readEncodedTile(
            TileCoordinate.isometric(
                worldName, rotation, IsoTileGeometry.fromLeafletZoom(leafletZoom), tileX, tileY)));
  }

  private void handleTileRequest(final @NotNull Context ctx) {
    final int leafletZoom;
    final int x;
    final int z;
    try {
      leafletZoom = Integer.parseInt(ctx.pathParam("zoom"));
      x = Integer.parseInt(ctx.pathParam("x"));
      z = Integer.parseInt(ctx.pathParam("z"));
    } catch (final NumberFormatException e) {
      ctx.status(400).result("Invalid coordinates");
      return;
    }

    if (!TileGeometry.isValidLeafletZoom(leafletZoom)) {
      ctx.status(400).result("Invalid zoom level");
      return;
    }

    final @NotNull String worldName = ctx.pathParam("world");
    if (!visible(ctx, worldName)) {
      ctx.status(404).result("World not found");
      return;
    }

    final int ourZoom = TileGeometry.fromLeafletZoom(leafletZoom);
    final @NotNull TileCoordinate coord = TileCoordinate.topDown(worldName, ourZoom, x, z);
    final @NotNull Optional<EncodedTile> tile = this.tileStorage.readEncodedTile(coord);

    respondWithTile(ctx, tile);
  }

  private void handleWorldsRequest(final @NotNull Context ctx) {
    final @NotNull Viewer viewer = viewer(ctx);
    ctx.header("Cache-Control", "no-store")
        .json(
            this.publishedWorlds.stream()
                .filter(world -> viewer.canSee(this.taerraConfig, (String) world.get("name")))
                .toList());
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
              worldData.put("displayName", WorldKeys.displayName(world.getKey()));
              worldData.put("environment", world.getEnvironment().name());
              worldData.put("private", this.taerraConfig.worldPrivate(WorldKeys.id(world)));
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

  private record AreaRequest(
      @Nullable String action, @Nullable String world, @Nullable List<int[]> chunks) {}

  private record TeleportRequest(
      @Nullable String world, @Nullable Integer x, @Nullable Integer y, @Nullable Integer z) {}

  private record WaypointRequest(
      @Nullable String name,
      @Nullable String color,
      @Nullable String group,
      boolean privateGroup,
      @Nullable String world,
      @Nullable Integer x,
      @Nullable Integer y,
      @Nullable Integer z,
      @Nullable String previous) {}
}
