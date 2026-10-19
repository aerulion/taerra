package net.aerulion.taerra.mod.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Type;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.aerulion.taerra.mod.config.Settings;
import net.aerulion.taerra.mod.pick.PickCache;
import net.aerulion.taerra.mod.pick.PickCacheImpl;
import net.aerulion.taerra.mod.tile.TileCache;
import net.aerulion.taerra.mod.tile.TileCacheImpl;
import net.aerulion.taerra.mod.tile.TileKey;
import net.aerulion.taerra.mod.util.Toasts;
import net.aerulion.taerra.mod.util.Urls;
import net.aerulion.taerra.mod.web.model.Account;
import net.aerulion.taerra.mod.web.model.Device;
import net.aerulion.taerra.mod.web.model.Layer;
import net.aerulion.taerra.mod.web.model.MapConfig;
import net.aerulion.taerra.mod.web.model.MarkerTexts;
import net.aerulion.taerra.mod.web.model.NavigationTarget;
import net.aerulion.taerra.mod.web.model.PersonalWaypoint;
import net.aerulion.taerra.mod.web.model.Player;
import net.aerulion.taerra.mod.web.model.Progress;
import net.aerulion.taerra.mod.web.model.Regions;
import net.aerulion.taerra.mod.web.model.ServerSettings;
import net.aerulion.taerra.mod.web.model.Sky;
import net.aerulion.taerra.mod.web.model.Waypoint;
import net.aerulion.taerra.mod.web.model.WaypointGroup;
import net.aerulion.taerra.mod.web.model.World;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class WebMapImpl implements WebMap {

  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Gson GSON = new Gson();
  private static final Type WORLDS = new TypeToken<List<World>>() {}.getType();
  private static final Type PLAYERS = new TypeToken<List<Player>>() {}.getType();
  private static final Type SKIES = new TypeToken<Map<String, Sky>>() {}.getType();
  private static final Type LAYERS = new TypeToken<List<Layer>>() {}.getType();
  private static final Type SHOWN = new TypeToken<Map<String, Boolean>>() {}.getType();
  private static final Type EDITORS = new TypeToken<List<ServerSettings.Editor>>() {}.getType();
  private static final Type GROUPS = new TypeToken<List<WaypointGroup>>() {}.getType();
  private static final Type DEVICES = new TypeToken<List<Device>>() {}.getType();
  private static final Type PERSONAL = new TypeToken<List<PersonalWaypoint>>() {}.getType();
  private static final long KEEP_ALIVE_MILLIS = 20_000;
  private static final long RECONNECT_MILLIS = 5_000;
  private static final long UNREACHABLE_RETRY_MILLIS = 10_000;
  private static final double GLIDE_PER_SECOND = 6;
  private static final double JUMP_BLOCKS = 32;

  private final WebClient client;
  private final @NotNull TileCache tiles;
  private final @NotNull PickCache picks;
  private final Map<String, List<Layer>> markers = new HashMap<>();
  private final Map<String, Shown> shown = new HashMap<>();
  private final Map<String, Boolean> layers = new HashMap<>();
  private final List<WebMapListener> listeners = new ArrayList<>();
  private final Set<String> staleMarkers = new HashSet<>();
  private final Map<String, Regions> regions = new HashMap<>();
  private final Map<String, Set<String>> revealed = new HashMap<>();
  private final Set<String> staleRegions = new HashSet<>();
  private @Nullable MapConfig config;
  private @NotNull List<World> worlds = List.of();
  private @NotNull List<Player> players = List.of();
  private @NotNull Map<String, Sky> skies = Map.of();
  private @Nullable Account account;
  private @Nullable NavigationTarget navigation;
  private long navigationVersion;
  private @Nullable Progress progress;
  private @Nullable WebSocket socket;
  private boolean settingsOpen;
  private @Nullable String subscribed;
  private @Nullable String focused;
  private boolean connecting;
  private boolean closed;
  private boolean loadingConfig;
  private boolean unreachable;
  private long nextConfigTry;
  private long nextPing;
  private long nextConnect;

  public WebMapImpl(final WebClient client, final Settings settings) {
    this.client = client;
    this.tiles = new TileCacheImpl(client, settings, this::revealed);
    this.picks = new PickCacheImpl(client, this::config, this::revealed);
  }

  @Override
  public String base() {
    return this.client.base();
  }

  @Override
  public boolean unreachable() {
    return this.unreachable;
  }

  @Override
  public boolean live() {
    return this.socket != null;
  }

  @Override
  public @Nullable MapConfig config() {
    return this.config;
  }

  @Override
  public @NotNull List<World> worlds() {
    return this.worlds;
  }

  @Override
  public @Nullable World world(final @Nullable String name) {
    return this.worlds.stream().filter(world -> world.name().equals(name)).findFirst().orElse(null);
  }

  @Override
  public @NotNull List<Player> players() {
    return this.players;
  }

  @Override
  public @Nullable Sky sky(final @NotNull String world) {
    return this.skies.get(world);
  }

  @Override
  public @Nullable Player player(final @Nullable String uuid) {
    return this.players.stream()
        .filter(player -> player.uuid().equals(uuid))
        .findFirst()
        .orElse(null);
  }

  @Override
  public double[] position(final @NotNull Player player) {
    final long now = System.nanoTime();
    final Shown last = this.shown.get(player.uuid());
    if (last == null
        || !last.world().equals(player.world())
        || Math.abs(last.x() - player.x()) + Math.abs(last.z() - player.z()) > JUMP_BLOCKS) {
      this.shown.put(
          player.uuid(), new Shown(player.world(), player.x(), player.y(), player.z(), now));
      return new double[] {player.x(), player.y(), player.z()};
    }
    final double step = 1 - Math.exp(-GLIDE_PER_SECOND * (now - last.at()) / 1e9);
    final @NotNull Shown next =
        new Shown(
            player.world(),
            last.x() + (player.x() - last.x()) * step,
            last.y() + (player.y() - last.y()) * step,
            last.z() + (player.z() - last.z()) * step,
            now);
    this.shown.put(player.uuid(), next);
    return new double[] {next.x(), next.y(), next.z()};
  }

  @Override
  public @Nullable Account account() {
    return this.account;
  }

  @Override
  public @Nullable NavigationTarget navigation() {
    return this.navigation;
  }

  @Override
  public long navigationVersion() {
    return this.navigationVersion;
  }

  @Override
  public void navigate(final @Nullable NavigationTarget target) {
    this.client.send(target == null ? "DELETE" : "PUT", "/api/navigation", target, _ -> {});
  }

  private void setNavigation(final @Nullable NavigationTarget target) {
    this.navigation = target;
    this.navigationVersion++;
  }

  @Override
  public @Nullable Progress progress() {
    return this.progress;
  }

  @Override
  public @NotNull List<Layer> markers(final @NotNull String world) {
    final List<Layer> layers = this.markers.get(world);
    if (layers == null) {
      this.markers.put(world, List.of());
      refreshMarkers(world);
      return List.of();
    }
    if (this.staleMarkers.remove(world)) {
      refreshMarkers(world);
    }
    return layers;
  }

  @Override
  public boolean shown(final @NotNull Layer layer) {
    return this.layers.getOrDefault(layer.id(), layer.shown());
  }

  @Override
  public void setShown(final @NotNull Layer layer, final boolean shown) {
    if (shown == layer.shown()) {
      this.layers.remove(layer.id());
    } else {
      this.layers.put(layer.id(), shown);
    }
    this.client.send(
        "PATCH",
        "/api/layers",
        Map.of(shown == layer.shown() ? "reset" : shown ? "show" : "hide", List.of(layer.id())),
        _ -> {});
  }

  private void refreshLayers() {
    this.client.json("/api/layers", SHOWN, this::setLayers);
  }

  private void setLayers(final @Nullable Map<String, Boolean> layers) {
    if (layers == null) {
      return;
    }
    this.layers.clear();
    this.layers.putAll(layers);
    List.copyOf(this.listeners).forEach(WebMapListener::layersChanged);
  }

  @Override
  public void waypointGroups(final @NotNull Consumer<List<WaypointGroup>> then) {
    this.client.<List<WaypointGroup>>json(
        "/api/waypoints/groups",
        GROUPS,
        groups -> then.accept(groups == null ? List.of() : groups));
  }

  @Override
  public void saveWaypoint(
      final @NotNull Waypoint waypoint,
      final boolean privateGroup,
      final @NotNull IntConsumer then) {
    final @NotNull Map<String, Object> body = new HashMap<>();
    body.put("name", waypoint.name());
    body.put("color", waypoint.color());
    body.put("description", waypoint.description());
    if (waypoint.personal()) {
      body.put("personal", true);
    } else if (!waypoint.group().isEmpty()) {
      body.put("group", waypoint.group());
    }
    body.put("privateGroup", privateGroup);
    body.put("world", waypoint.world());
    if (waypoint.points() == null) {
      body.put("x", waypoint.x());
      if (waypoint.y() != null) {
        body.put("y", waypoint.y());
      }
      body.put("z", waypoint.z());
    } else {
      body.put("type", waypoint.type());
      body.put("points", waypoint.points());
      body.put("smooth", waypoint.smooth());
    }
    if (waypoint.previous() != null) {
      body.put("previous", waypoint.previous());
    }
    this.client.send("PUT", "/api/waypoints", body, refreshing(waypoint.world(), then));
  }

  @Override
  public void deleteWaypoint(
      final @NotNull String world,
      final @NotNull String name,
      final boolean personal,
      final @NotNull IntConsumer then) {
    this.client.send(
        "DELETE",
        "/api/waypoints/" + Urls.segment(name) + (personal ? "?personal=true" : ""),
        null,
        refreshing(world, then));
  }

  @Override
  public void devices(final @NotNull Consumer<@Nullable List<Device>> then) {
    this.client
        .get("/api/account/sessions", null)
        .handle(
            (response, error) -> {
              @Nullable List<Device> devices = null;
              if (error == null && response.statusCode() == 200) {
                try {
                  devices =
                      GSON.fromJson(new String(response.body(), StandardCharsets.UTF_8), DEVICES);
                } catch (final RuntimeException e) {
                  LOGGER.warn("TAERRA: unreadable device list: {}", e.toString());
                }
              }
              final @Nullable List<Device> found = devices;
              this.client.execute(() -> then.accept(found));
              return null;
            });
  }

  @Override
  public void personalWaypoints(final @NotNull Consumer<@Nullable List<PersonalWaypoint>> then) {
    this.client.request(
        "GET",
        "/api/waypoints/personal",
        null,
        (status, body) -> {
          @Nullable List<PersonalWaypoint> waypoints = null;
          if (status == 200 && body != null) {
            try {
              waypoints = GSON.fromJson(body, PERSONAL);
            } catch (final RuntimeException e) {
              LOGGER.warn("TAERRA: unreadable waypoint list: {}", e.toString());
            }
          }
          then.accept(waypoints);
        });
  }

  @Override
  public void setHidden(
      final @NotNull List<String> names, final boolean hidden, final @NotNull IntConsumer then) {
    this.client.send(
        "PUT", "/api/waypoints/personal/hidden", Map.of("names", names, "hidden", hidden), then);
  }

  @Override
  public void importWaypoints(
      final @NotNull String mode,
      final @NotNull JsonArray waypoints,
      final @NotNull BiConsumer<Integer, @Nullable String> then) {
    final @NotNull JsonObject body = new JsonObject();
    body.addProperty("mode", mode);
    body.add("waypoints", waypoints);
    this.client.request("POST", "/api/waypoints/personal/import", body, then);
  }

  @Override
  public void settings(final @NotNull Consumer<@Nullable ServerSettings> then) {
    this.client.request(
        "GET",
        "/api/settings",
        null,
        (status, body) -> {
          @Nullable ServerSettings settings = null;
          if (status == 200 && body != null) {
            try {
              settings = GSON.fromJson(body, ServerSettings.class);
            } catch (final RuntimeException e) {
              LOGGER.warn("TAERRA: unreadable settings: {}", e.toString());
            }
          }
          then.accept(settings);
        });
  }

  @Override
  public void saveSettings(
      final @NotNull JsonObject edit, final @NotNull BiConsumer<Integer, @Nullable String> then) {
    this.client.request("PUT", "/api/settings", edit, then);
  }

  @Override
  public void setSettingsOpen(final boolean open) {
    this.settingsOpen = open;
    send(Map.of("type", "settings", "open", open, "client", "mod"));
  }

  @Override
  public void logOutDevice(final @NotNull String id, final @NotNull IntConsumer then) {
    this.client.send("DELETE", "/api/account/sessions/" + Urls.segment(id), null, then);
  }

  @Override
  public void teleport(
      final String world,
      final int x,
      final @Nullable Integer y,
      final int z,
      final IntConsumer then) {
    final @NotNull Map<String, Object> target = new HashMap<>();
    target.put("world", world);
    target.put("x", x);
    if (y != null) {
      target.put("y", y);
    }
    target.put("z", z);
    this.client.send("POST", "/api/teleport", target, then);
  }

  @Override
  public void render(final String action, final String world, final IntConsumer then) {
    this.client.send("POST", "/api/render", Map.of("action", action, "world", world), then);
  }

  @Override
  public void area(
      final String action,
      final String world,
      final Collection<Long> chunks,
      final IntConsumer then) {
    this.client.send(
        "POST",
        "/api/area",
        Map.of(
            "action",
            action,
            "world",
            world,
            "chunks",
            chunks.stream()
                .map(chunk -> new int[] {(int) (chunk >> 32), chunk.intValue()})
                .toList()),
        then);
  }

  @Override
  public @NotNull Regions regions(final @NotNull String world) {
    final Regions known = this.regions.get(world);
    if (known == null) {
      this.regions.put(world, Regions.NONE);
      refreshRegions(world);
      return Regions.NONE;
    }
    if (this.staleRegions.remove(world)) {
      refreshRegions(world);
    }
    return known;
  }

  @Override
  public @NotNull Set<String> revealed(final @NotNull String world) {
    return this.revealed.getOrDefault(world, Set.of());
  }

  @Override
  public void saveRegion(
      final @NotNull String world,
      final @Nullable String id,
      final @NotNull String name,
      final @NotNull String color,
      final @NotNull String fog,
      final boolean discoverable,
      final @NotNull Collection<Long> chunks,
      final @NotNull BiConsumer<Integer, @Nullable String> then) {
    final @NotNull Map<String, Object> body = new HashMap<>();
    body.put("world", world);
    body.put("color", color);
    if (id != null) {
      body.put("id", id);
    }
    body.put("name", name);
    body.put("fog", fog);
    body.put("discoverable", discoverable);
    body.put(
        "chunks",
        chunks.stream().map(chunk -> new int[] {(int) (chunk >> 32), chunk.intValue()}).toList());
    this.client.request("POST", "/api/regions", body, then);
  }

  @Override
  public void deleteRegion(
      final @NotNull String id, final @NotNull BiConsumer<Integer, @Nullable String> then) {
    this.client.request("DELETE", "/api/regions/" + id, null, then);
  }

  @Override
  public void resetRegion(final @NotNull String id, final @NotNull IntConsumer then) {
    this.client.send("POST", "/api/regions/" + id + "/reset", null, then);
  }

  private void refreshRegions(final @NotNull String world) {
    this.client.<Regions>json(
        "/api/regions/" + Urls.segment(world),
        Regions.class,
        loaded -> {
          final @NotNull Regions regions = loaded == null ? Regions.NONE : loaded;
          this.regions.put(world, regions);
          final @NotNull Set<String> revealed =
              Set.copyOf(regions.revealed() == null ? List.of() : regions.revealed());
          if (!revealed.equals(this.revealed.getOrDefault(world, Set.of()))) {
            this.revealed.put(world, revealed);
            this.tiles.refreshWorld(world);
            this.picks.forgetWorld(world);
          }
          List.copyOf(this.listeners).forEach(listener -> listener.regionsChanged(world));
        });
  }

  @Override
  public @NotNull String link(
      final @NotNull String world,
      final int x,
      final @Nullable Integer y,
      final int z,
      final long zoom,
      final boolean isometric,
      final int rotation) {
    final @NotNull StringJoiner params = new StringJoiner("&");
    params.add("world=" + Urls.query(world));
    params.add("x=" + x);
    params.add("z=" + z);
    params.add("zoom=" + zoom);
    if (y != null) {
      params.add("y=" + y);
    }
    if (isometric) {
      params.add("view=iso");
      params.add("r=" + rotation);
    }
    return base() + "/#" + params;
  }

  @Override
  public @NotNull TileCache tiles() {
    return this.tiles;
  }

  @Override
  public @NotNull PickCache picks() {
    return this.picks;
  }

  @Override
  public void addListener(final WebMapListener listener) {
    this.listeners.add(listener);
  }

  @Override
  public void removeListener(final WebMapListener listener) {
    this.listeners.remove(listener);
  }

  @Override
  public void focus(final @Nullable String world) {
    this.focused = world;
  }

  @Override
  public void tick(final String playerWorld) {
    final String world = this.focused != null ? this.focused : playerWorld;
    final long now = System.currentTimeMillis();
    if (this.config == null) {
      if (!this.loadingConfig && now >= this.nextConfigTry) {
        loadConfig();
      }
      return;
    }
    if (this.socket == null) {
      if (!this.connecting && now >= this.nextConnect) {
        connect();
      }
      return;
    }
    if (!world.isEmpty()) {
      regions(world);
    }
    if (!world.isEmpty() && !world.equals(this.subscribed)) {
      this.subscribed = world;
      this.tiles.refreshWorld(world);
      this.picks.refreshWorld(world);
      send(Map.of("type", "subscribe", "world", world));
    }
    if (now >= this.nextPing) {
      this.nextPing = now + KEEP_ALIVE_MILLIS;
      send(Map.of("type", "ping"));
    }
  }

  @Override
  public void close() {
    this.closed = true;
    this.client.close();
    if (this.socket != null) {
      this.socket.abort();
      this.socket = null;
    }
    this.tiles.close();
  }

  private @NotNull IntConsumer refreshing(
      final @NotNull String world, final @NotNull IntConsumer then) {
    return status -> {
      if (status == 204) {
        refreshMarkers(world);
      }
      then.accept(status);
    };
  }

  private void loadConfig() {
    this.loadingConfig = true;
    this.client
        .get("/api/config", null)
        .handle(
            (response, error) -> {
              final MapConfig config =
                  error == null && response.statusCode() == 200
                      ? GSON.fromJson(
                          new String(response.body(), StandardCharsets.UTF_8), MapConfig.class)
                      : null;
              final String failure =
                  error == null ? "HTTP " + response.statusCode() : error.toString();
              this.client.execute(() -> configLoaded(config, failure));
              return null;
            });
  }

  private void configLoaded(final @Nullable MapConfig config, final String failure) {
    this.loadingConfig = false;
    if (config == null) {
      if (!this.unreachable) {
        LOGGER.warn("TAERRA: the web map at {} did not answer: {}", base(), failure);
        Toasts.show(
            Component.translatable("taerra.unreachable"),
            Component.translatable("taerra.unreachable.detail", base()));
      }
      this.unreachable = true;
      this.nextConfigTry = System.currentTimeMillis() + UNREACHABLE_RETRY_MILLIS;
      return;
    }
    this.unreachable = false;
    this.config = config;
    refreshWorlds();
    refreshPlayers();
    refreshAccount();
  }

  private void refreshMarkers(final @NotNull String world) {
    if (this.markers.containsKey(world)) {
      this.client.<List<Layer>>json(
          "/api/markers/" + Urls.segment(world),
          LAYERS,
          layers -> {
            this.markers.put(
                world,
                layers == null ? List.of() : layers.stream().map(MarkerTexts::localize).toList());
            List.copyOf(this.listeners).forEach(listener -> listener.markersChanged(world));
          });
    }
  }

  private void refreshAccount() {
    this.client.<Account>json("/api/account", Account.class, account -> this.account = account);
    this.client.<NavigationTarget>json(
        "/api/navigation", NavigationTarget.class, this::setNavigation);
    refreshLayers();
  }

  private void refreshWorlds() {
    this.client.<List<World>>json(
        "/api/worlds",
        WORLDS,
        worlds -> {
          this.worlds = worlds == null ? List.of() : List.copyOf(worlds);
          List.copyOf(this.listeners).forEach(WebMapListener::worldsChanged);
        });
    this.client.json("/api/sky", SKIES, this::setSkies);
  }

  private void refreshPlayers() {
    this.client.json("/api/players", PLAYERS, this::setPlayers);
  }

  private void setSkies(final @Nullable Map<String, Sky> skies) {
    this.skies = skies == null ? Map.of() : Map.copyOf(skies);
  }

  private void setPlayers(final @Nullable List<Player> players) {
    this.players = players == null ? List.of() : List.copyOf(players);
    this.shown.keySet().retainAll(this.players.stream().map(Player::uuid).toList());
    List.copyOf(this.listeners).forEach(WebMapListener::playersChanged);
  }

  private void send(final Map<String, ?> message) {
    if (this.socket != null) {
      this.socket.sendText(GSON.toJson(message), true);
    }
  }

  private void connect() {
    this.connecting = true;
    this.client
        .socket(new SocketListener())
        .whenComplete(
            (socket, error) ->
                Minecraft.getInstance()
                    .execute(
                        () -> {
                          this.connecting = false;
                          if (this.closed) {
                            if (socket != null) {
                              socket.abort();
                            }
                          } else if (socket != null) {
                            this.socket = socket;
                            this.subscribed = null;
                            this.tiles.refreshAll();
                            this.picks.refreshAll();
                            refreshWorlds();
                            this.staleMarkers.addAll(this.markers.keySet());
                            this.staleRegions.addAll(this.regions.keySet());
                            refreshPlayers();
                            refreshLayers();
                            if (this.settingsOpen) {
                              send(Map.of("type", "settings", "open", true, "client", "mod"));
                              List.copyOf(this.listeners)
                                  .forEach(listener -> listener.settingsChanged(Long.MAX_VALUE));
                            }
                          } else {
                            this.nextConnect = System.currentTimeMillis() + RECONNECT_MILLIS;
                          }
                        }));
  }

  private void disconnected(final WebSocket socket) {
    if (this.socket == socket) {
      this.socket = null;
      this.progress = null;
      this.nextConnect = System.currentTimeMillis() + RECONNECT_MILLIS;
    }
  }

  private void receive(final @NotNull JsonObject message) {
    final String type = message.has("type") ? message.get("type").getAsString() : "";
    switch (type) {
      case "tile_updated" -> {
        if (message.has("bulk")) {
          this.tiles.refreshAll();
          this.picks.refreshAll();
          return;
        }
        final String world = message.get("world").getAsString();
        for (final @NotNull JsonElement element : message.getAsJsonArray("tiles")) {
          final JsonArray tile = element.getAsJsonArray();
          final @NotNull TileKey key =
              new TileKey(
                  world,
                  tile.size() > 3 && "isometric".equals(tile.get(3).getAsString()),
                  tile.size() > 4 ? tile.get(4).getAsInt() : 0,
                  tile.get(0).getAsInt(),
                  tile.get(1).getAsInt(),
                  tile.get(2).getAsInt());
          this.tiles.refresh(key);
          this.picks.refresh(key);
        }
      }
      case "players" -> setPlayers(GSON.fromJson(message.get("data"), PLAYERS));
      case "sky" -> setSkies(GSON.fromJson(message.get("data"), SKIES));
      case "navigation" ->
          setNavigation(GSON.fromJson(message.get("target"), NavigationTarget.class));
      case "layers" -> setLayers(GSON.fromJson(message.get("layers"), SHOWN));
      case "markers" -> {
        final String world = message.get("world").getAsString();
        if (this.markers.containsKey(world)) {
          this.staleMarkers.add(world);
        }
      }
      case "worlds" -> {
        refreshWorlds();
        refreshPlayers();
      }
      case "account" -> {
        refreshAccount();
        refreshWorlds();
        this.staleMarkers.addAll(this.markers.keySet());
        this.staleRegions.addAll(this.regions.keySet());
      }
      case "regions" -> {
        this.staleRegions.addAll(this.regions.keySet());
        this.staleMarkers.addAll(this.markers.keySet());
        refreshPlayers();
      }
      case "progress" -> this.progress = GSON.fromJson(message, Progress.class);
      case "config" -> {
        if (!this.loadingConfig) {
          loadConfig();
        }
      }
      case "settings" -> {
        final long revision = message.get("revision").getAsLong();
        List.copyOf(this.listeners).forEach(listener -> listener.settingsChanged(revision));
      }
      case "settings_editors" -> {
        final List<ServerSettings.Editor> editors = GSON.fromJson(message.get("editors"), EDITORS);
        List.copyOf(this.listeners).forEach(listener -> listener.settingsEditors(editors));
      }
      default -> {}
    }
  }

  private record Shown(String world, double x, double y, double z, long at) {}

  private final class SocketListener implements WebSocket.Listener {

    private final StringBuilder text = new StringBuilder();

    @Override
    public @Nullable CompletionStage<?> onText(
        final @NotNull WebSocket socket, final CharSequence data, final boolean last) {
      this.text.append(data);
      if (last) {
        final @NotNull String message = this.text.toString();
        this.text.setLength(0);
        WebMapImpl.this.client.execute(
            () -> {
              try {
                receive(JsonParser.parseString(message).getAsJsonObject());
              } catch (final RuntimeException e) {
                LOGGER.warn("TAERRA: unreadable socket message: {}", e.toString());
              }
            });
      }
      socket.request(1);
      return null;
    }

    @Override
    public @Nullable CompletionStage<?> onClose(
        final WebSocket socket, final int statusCode, final String reason) {
      WebMapImpl.this.client.execute(() -> disconnected(socket));
      return null;
    }

    @Override
    public void onError(final WebSocket socket, final Throwable error) {
      WebMapImpl.this.client.execute(() -> disconnected(socket));
    }
  }
}
