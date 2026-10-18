package net.aerulion.taerra.web.http.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.config.WebSettings;
import net.aerulion.taerra.marker.MarkerRegistryInternal;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.region.RegionVisibility;
import net.aerulion.taerra.rendering.iso.IsoTileGeometry;
import net.aerulion.taerra.rendering.sample.FogView;
import net.aerulion.taerra.storage.EncodedTile;
import net.aerulion.taerra.storage.TileStorage;
import net.aerulion.taerra.waypoint.PersonalWaypoints;
import net.aerulion.taerra.web.auth.Viewer;
import net.aerulion.taerra.web.http.Requests;
import net.aerulion.taerra.web.http.SkyList;
import net.aerulion.taerra.web.http.WorldList;
import net.aerulion.taerra.web.marker.MarkerPayloadSerializer;
import net.aerulion.taerra.web.marker.RegionMarkers;
import net.aerulion.taerra.web.player.PlayerAvatars;
import net.aerulion.taerra.web.player.PlayerPositionBroadcaster;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MapRoutes implements Routes {

  public static final @NotNull String LAYERS_HEADER = "X-Taerra-Layers";

  private final @NotNull Plugin plugin;
  private final @NotNull Requests requests;
  private final @NotNull TaerraConfig taerraConfig;
  private final @NotNull WebSettings web;
  private final @NotNull TileStorage tileStorage;
  private final @NotNull MarkerRegistryInternal markers;
  private final @NotNull PlayerPositionBroadcaster players;
  private final @NotNull PlayerAvatars avatars;
  private final @NotNull WorldList worldList;
  private final @NotNull SkyList skyList;
  private final @NotNull RegionVisibility visibility;
  private final @NotNull ObjectMapper mapper = new ObjectMapper();
  private @NotNull String indexHtml = "";
  private @NotNull ObjectNode manifest = JsonNodeFactory.instance.objectNode();

  public MapRoutes(
      final @NotNull Plugin plugin,
      final @NotNull Requests requests,
      final @NotNull TaerraConfig taerraConfig,
      final @NotNull WebSettings web,
      final @NotNull TileStorage tileStorage,
      final @NotNull MarkerRegistryInternal markers,
      final @NotNull PlayerPositionBroadcaster players,
      final @NotNull PlayerAvatars avatars,
      final @NotNull WorldList worldList,
      final @NotNull SkyList skyList,
      final @NotNull RegionVisibility visibility) {
    super();
    this.plugin = plugin;
    this.requests = requests;
    this.taerraConfig = taerraConfig;
    this.web = web;
    this.tileStorage = tileStorage;
    this.markers = markers;
    this.players = players;
    this.avatars = avatars;
    this.worldList = worldList;
    this.skyList = skyList;
    this.visibility = visibility;
  }

  @Override
  public void register(final @NotNull RoutesConfig routes) {
    load();
    routes.get("/", this::handleIndexRequest);
    routes.get("/api/config", this::handleConfigRequest);
    routes.get("/api/manifest.json", this::handleManifestRequest);
    routes.get("/api/worlds", this::handleWorldsRequest);
    routes.get("/api/sky", this::handleSkyRequest);
    routes.get("/api/tiles/{world}/{zoom}/{x}/{z}.png", this::handleTileRequest);
    routes.get("/api/iso/{world}/{rotation}/{zoom}/{x}/{y}.png", this::handleIsoTileRequest);
    routes.get("/api/iso/{world}/{rotation}/{x}/{y}.pick", this::handleIsoPickRequest);
    routes.get("/api/tiles/{world}/{x}/{z}.pick", this::handleTopDownPickRequest);
    routes.get("/api/players", this::handlePlayersRequest);
    routes.get("/api/markers/{world}", this::handleMarkersRequest);
    routes.get("/api/avatars/{uuid}.png", this::handleAvatarRequest);
  }

  private void respondWithLayer(final @NotNull Context ctx, final @NotNull TileCoordinate coord) {
    final @Nullable FogView layer = layer(ctx);
    if (layer == null) {
      ctx.header("Cache-Control", "no-store").status(404).result("Tile not found");
      return;
    }
    if (!layer.base()) {
      respondWithTile(ctx, this.tileStorage.readEncodedTile(coord, layer), "");
      return;
    }
    if (!this.visibility.regions().anyFogged(coord.world())) {
      respondWithTile(ctx, this.tileStorage.readEncodedTile(coord), "");
      return;
    }
    final @NotNull List<FogView> stored = this.tileStorage.layers(coord);
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    final boolean clear =
        !stored.isEmpty()
            && stored.stream()
                .filter(found -> found.kind() != FogView.Kind.CLEAR)
                .allMatch(
                    found ->
                        this.visibility.revealed(
                            viewer.uuid(), viewer.canSeeHidden(), found.region()));
    if (clear) {
      ctx.header(LAYERS_HEADER, "");
      respondWithTile(
          ctx,
          stored.contains(FogView.CLEAR)
              ? this.tileStorage.readEncodedTile(coord, FogView.CLEAR)
              : this.tileStorage.readEncodedTile(coord),
          "");
      return;
    }
    final @NotNull String layers =
        stored.stream()
            .filter(found -> found.kind() != FogView.Kind.CLEAR)
            .map(FogView::id)
            .collect(Collectors.joining(","));
    ctx.header(LAYERS_HEADER, layers);
    respondWithTile(ctx, this.tileStorage.readEncodedTile(coord), layers);
  }

  private @Nullable FogView layer(final @NotNull Context ctx) {
    final @Nullable FogView layer = FogView.parse(ctx.queryParam("layer"));
    if (layer == null || layer.kind() == FogView.Kind.CLEAR) {
      return null;
    }
    if (layer.kind() != FogView.Kind.BODY) {
      return layer;
    }
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    return this.visibility.revealed(viewer.uuid(), viewer.canSeeHidden(), layer.region())
        ? layer
        : null;
  }

  private static void respondWithTile(
      final @NotNull Context ctx,
      final @NotNull Optional<EncodedTile> tile,
      final @NotNull String layers) {
    if (tile.isEmpty()) {

      ctx.header("Cache-Control", "no-store").status(404).result("Tile not found");
      return;
    }

    final @NotNull EncodedTile encodedTile = tile.get();
    final @NotNull String eTag =
        layers.isEmpty()
            ? encodedTile.eTag()
            : encodedTile.eTag().substring(0, encodedTile.eTag().length() - 1)
                + '~'
                + Integer.toHexString(layers.hashCode())
                + '"';

    final boolean versioned = ctx.queryParam("v") != null;
    ctx.header("Cache-Control", versioned ? "private, max-age=31536000, immutable" : "no-cache")
        .header("ETag", eTag);

    if (eTag.equals(ctx.header("If-None-Match"))) {
      ctx.status(304);
      return;
    }

    ctx.contentType("image/png").result(encodedTile.data());
  }

  private void respondWithPick(final @NotNull Context ctx, final @NotNull TileCoordinate coord) {
    final @Nullable FogView layer = layer(ctx);
    if (layer != null && layer.base() && this.visibility.regions().anyFogged(coord.world())) {
      ctx.header(
          LAYERS_HEADER,
          this.tileStorage.layers(coord).stream()
              .filter(stored -> stored.kind() == FogView.Kind.BODY)
              .map(FogView::id)
              .collect(Collectors.joining(",")));
    }
    respondWithPick(
        ctx, layer == null ? Optional.empty() : this.tileStorage.readPick(coord, layer));
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

  private void load() {
    try (final @Nullable InputStream index = this.plugin.getResource("web/index.html");
        final @Nullable InputStream manifest = this.plugin.getResource("web/manifest.json")) {
      if (index != null) {
        this.indexHtml = new String(index.readAllBytes(), StandardCharsets.UTF_8);
      }
      if (manifest != null) {
        this.manifest = (ObjectNode) this.mapper.readTree(manifest);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void handleIndexRequest(final @NotNull Context ctx) {
    ctx.header("Cache-Control", "no-cache")
        .html(indexPage(this.indexHtml, this.web.name(), this.web.publicUrl()));
  }

  private void handleConfigRequest(final @NotNull Context ctx) {
    final @NotNull String name = this.web.name();
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

  private void handleManifestRequest(final @NotNull Context ctx) {
    final @NotNull ObjectNode manifest = this.manifest.deepCopy();
    final @NotNull String name = this.web.name();
    if (!name.isEmpty()) {
      manifest.put("name", name).put("short_name", name);
    }
    ctx.header("Cache-Control", "no-cache")
        .contentType("application/manifest+json")
        .result(manifest.toString());
  }

  private void handleWorldsRequest(final @NotNull Context ctx) {
    ctx.header("Cache-Control", "no-store")
        .json(this.worldList.visibleTo(this.requests.viewer(ctx)));
  }

  private void handleSkyRequest(final @NotNull Context ctx) {
    ctx.header("Cache-Control", "no-store")
        .contentType("application/json")
        .result(this.skyList.visibleTo(this.requests.viewer(ctx)));
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
    if (!this.requests.visible(ctx, worldName)) {
      ctx.status(404).result("World not found");
      return;
    }

    final int ourZoom = TileGeometry.fromLeafletZoom(leafletZoom);
    respondWithLayer(ctx, TileCoordinate.topDown(worldName, ourZoom, x, z));
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
    if (!this.requests.visible(ctx, worldName)) {
      ctx.status(404).result("World not found");
      return;
    }

    respondWithLayer(
        ctx,
        TileCoordinate.isometric(
            worldName, rotation, IsoTileGeometry.fromLeafletZoom(leafletZoom), tileX, tileY));
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
    if (rotation == null || !this.requests.visible(ctx, worldName)) {
      ctx.status(404).result("Not found");
      return;
    }

    respondWithPick(ctx, TileCoordinate.isometric(worldName, rotation, 0, tileX, tileY));
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
    if (!this.requests.visible(ctx, worldName)) {
      ctx.status(404).result("Not found");
      return;
    }
    respondWithPick(ctx, TileCoordinate.topDown(worldName, 0, tileX, tileZ));
  }

  private void handlePlayersRequest(final @NotNull Context ctx) {
    ctx.header("Cache-Control", "no-cache")
        .contentType("application/json")
        .result(this.players.players(this.requests.viewer(ctx)));
  }

  private void handleMarkersRequest(final @NotNull Context ctx) {
    final @NotNull String worldName = ctx.pathParam("world");
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    if (!viewer.canSee(this.taerraConfig, worldName)) {
      ctx.status(404).result("Not found");
      return;
    }
    final @NotNull Predicate<String> visible =
        layer ->
            viewer.canSeeLayer(this.taerraConfig, layer)
                && (viewer.canKeepPersonalMarkers() || !layer.equals(PersonalWaypoints.LAYER));
    final @NotNull List<Map<String, Object>> layers =
        new ArrayList<>(
            viewer.uuid() == null
                ? List.of()
                : MarkerPayloadSerializer.player(
                    this.markers.playerLayers(),
                    viewer.uuid(),
                    worldName,
                    visible,
                    this.taerraConfig::layerPrivate));
    layers.addAll(
        MarkerPayloadSerializer.world(
            this.markers, worldName, visible, this.taerraConfig::layerPrivate));
    final @Nullable MarkerLayer regions = this.markers.existingLayer(RegionMarkers.LAYER);
    if (regions != null && visible.test(RegionMarkers.LAYER)) {
      layers.addAll(
          RegionMarkers.layer(
              this.visibility,
              viewer,
              worldName,
              regions.label(),
              regions.shownByDefault(),
              this.taerraConfig::layerPrivate));
    }
    ctx.header("Cache-Control", "no-cache").json(layers);
  }

  private void handleAvatarRequest(final @NotNull Context ctx) {
    final @NotNull UUID uuid;
    try {
      uuid = UUID.fromString(ctx.pathParam("uuid"));
    } catch (final IllegalArgumentException e) {
      ctx.status(400).result("Invalid UUID");
      return;
    }
    final @Nullable Player player = this.players.shown(uuid, this.requests.viewer(ctx));
    final byte @Nullable [] png =
        player == null || !this.requests.visible(ctx, WorldKeys.id(player.getWorld()))
            ? null
            : this.avatars.png(player);
    if (png == null) {
      ctx.header("Cache-Control", "no-store").status(404).result("Avatar not found");
      return;
    }
    ctx.header("Cache-Control", "private, max-age=3600").contentType("image/png").result(png);
  }
}
