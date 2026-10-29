package net.aerulion.taerra.web.player;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;
import net.aerulion.taerra.api.player.MapPlayers;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.limit.WorldLimits;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.model.WorldLimit;
import net.aerulion.taerra.region.MapRegion;
import net.aerulion.taerra.region.RegionVisibility;
import net.aerulion.taerra.web.auth.Viewer;
import net.aerulion.taerra.web.socket.MapWebSocketHandler;
import net.aerulion.taerra.web.socket.SocketMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlayerPositionBroadcasterImpl implements PlayerPositionBroadcaster {

  private static final long UPDATE_INTERVAL_TICKS = 20L;
  private static final String ANONYMOUS_COLOR = "#a0a0a0";
  private static final List<String> POSITION = List.of("x", "y", "z", "yaw");
  private final @NotNull Plugin plugin;
  private final @NotNull TaerraConfig config;
  private final @NotNull MapWebSocketHandler wsHandler;
  private final @NotNull WorldLimits limits;
  private final @NotNull MapPlayers players;
  private final @NotNull RegionVisibility visibility;
  private final @NotNull ObjectMapper mapper;
  private final @NotNull String anonymousSalt = UUID.randomUUID().toString();
  private volatile @NotNull List<Entry> lastSent = List.of();
  private volatile @NotNull Map<UUID, Entry> shown = Map.of();
  private @Nullable BukkitTask task;

  public PlayerPositionBroadcasterImpl(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig config,
      final @NotNull MapWebSocketHandler wsHandler,
      final @NotNull WorldLimits limits,
      final @NotNull RegionVisibility visibility,
      final @NotNull MapPlayers players) {
    super();
    this.plugin = plugin;
    this.config = config;
    this.wsHandler = wsHandler;
    this.limits = limits;
    this.visibility = visibility;
    this.players = players;
    this.mapper = new ObjectMapper();
  }

  @Override
  public void start() {
    this.task =
        Bukkit.getScheduler()
            .runTaskTimer(
                this.plugin,
                () -> {
                  try {
                    broadcastPlayerUpdates();
                  } catch (final Exception e) {
                    this.plugin
                        .getLogger()
                        .warning("Failed to send player positions: " + e.getMessage());
                  }
                },
                0L,
                UPDATE_INTERVAL_TICKS);
  }

  @Override
  public void stop() {
    if (this.task != null) {
      this.task.cancel();
      this.task = null;
    }
  }

  @Override
  public @NotNull String players(final @NotNull Viewer viewer) {
    final @NotNull StringJoiner players = new StringJoiner(",", "[", "]");
    for (final @NotNull Entry entry : this.lastSent) {
      final @Nullable String json = json(entry, viewer);
      if (json != null) {
        players.add(json);
      }
    }
    return players.toString();
  }

  @Override
  public @Nullable Player listed(final @NotNull UUID uuid, final @NotNull Viewer viewer) {
    final @Nullable Entry entry = this.shown.get(uuid);
    return entry == null || (!viewer.canSeeHiddenPlayers() && !entry.named())
        ? null
        : entry.player();
  }

  private @Nullable String json(final @NotNull Entry entry, final @NotNull Viewer viewer) {
    final boolean seesHidden = viewer.canSeeHiddenPlayers();
    final boolean seesFog = viewer.canSeeThroughFog();
    if (!seesHidden && entry.full().listed() == null) {
      return null;
    }
    final boolean known = viewer.canSee(this.config, entry.world());
    final boolean located =
        known
            && entry.inside()
            && (seesFog || visible(entry, viewer))
            && (!entry.playersPrivate() || viewer.canSeePlayers(this.config, entry.world()));
    final @NotNull Views views = !known ? entry.unknown() : located ? entry.full() : entry.away();
    final boolean marked =
        entry.spectator()
            ? seesHidden
            : entry.full().listed() == null || (located && seesFog && entry.region() != null);
    if (seesHidden) {
      return marked ? views.withHidden() : views.unmasked();
    }
    return marked ? views.listedHidden() : views.listed();
  }

  private boolean visible(final @NotNull Entry entry, final @NotNull Viewer viewer) {
    return entry.region() == null || this.visibility.revealed(viewer.uuid(), false, entry.region());
  }

  private @NotNull Map<String, Object> anonymize(
      final @NotNull Map<String, Object> data, final @NotNull Player player) {
    final @NotNull Map<String, Object> anonymous = new HashMap<>(data);
    anonymous.remove("name");
    anonymous.remove("mod");
    anonymous.put("anonymous", true);
    anonymous.put("color", ANONYMOUS_COLOR);
    anonymous.put(
        "uuid",
        UUID.nameUUIDFromBytes(
                (this.anonymousSalt + player.getUniqueId() + player.getLastLogin())
                    .getBytes(StandardCharsets.UTF_8))
            .toString());
    return anonymous;
  }

  private void broadcastPlayerUpdates() {
    final @NotNull List<Entry> sent = new ArrayList<>();
    final @NotNull Map<UUID, Entry> shown = new HashMap<>();
    for (final @NotNull Player player : Bukkit.getOnlinePlayers()) {
      final @NotNull String worldName = WorldKeys.id(player.getWorld());
      final int x = player.getLocation().getBlockX();
      final int z = player.getLocation().getBlockZ();
      final boolean enabled = this.config.worldEnabled(worldName);
      final @Nullable WorldLimit limit = this.limits.get(worldName);
      final @Nullable MapRegion region =
          enabled ? this.visibility.regions().foggedAt(worldName, x >> 4, z >> 4) : null;
      final boolean included = !this.players.hidden(player);
      final boolean spectator = PlayerMapPayloadSerializer.isSpectator(player);
      final @NotNull Map<String, Object> data = PlayerMapPayloadSerializer.serialize(player);
      try {
        final @NotNull Views full = views(data, included, spectator, player);
        POSITION.forEach(data::remove);
        final @NotNull Views away = views(data, included, spectator, player);
        data.remove("world");
        final @NotNull Views unknown = views(data, included, spectator, player);
        final @NotNull Entry entry =
            new Entry(
                player,
                worldName,
                enabled && (limit == null || limit.contains(x, z)),
                region == null ? null : region.id(),
                this.config.playersPrivate(worldName),
                included && player.isAllowingServerListings(),
                spectator,
                full,
                away,
                unknown);
        sent.add(entry);
        shown.put(player.getUniqueId(), entry);
      } catch (final Exception e) {
        this.plugin.getLogger().warning("Failed to serialize player positions: " + e.getMessage());
      }
    }
    final boolean changed = !sent.equals(this.lastSent);
    this.lastSent = List.copyOf(sent);
    this.shown = Map.copyOf(shown);
    if (changed) {
      this.wsHandler.broadcast(viewer -> SocketMessage.PLAYERS.withData(players(viewer)));
    }
  }

  private @NotNull Views views(
      final @NotNull Map<String, Object> data,
      final boolean included,
      final boolean spectator,
      final @NotNull Player player)
      throws JsonProcessingException {
    final @NotNull Map<String, Object> named = new HashMap<>(data);
    named.put("hidden", true);
    final @NotNull String withHidden = this.mapper.writeValueAsString(named);
    named.put("hidden", false);
    final @NotNull String unmasked = this.mapper.writeValueAsString(named);
    if (!included) {
      return new Views(null, null, withHidden, unmasked);
    }
    final @NotNull Map<String, Object> listed =
        new HashMap<>(player.isAllowingServerListings() ? named : anonymize(named, player));
    if (spectator) {
      listed.remove("world");
      POSITION.forEach(listed::remove);
    }
    final @NotNull String shown = this.mapper.writeValueAsString(listed);
    listed.put("hidden", true);
    return new Views(shown, this.mapper.writeValueAsString(listed), withHidden, unmasked);
  }

  private record Views(
      @Nullable String listed,
      @Nullable String listedHidden,
      @NotNull String withHidden,
      @NotNull String unmasked) {}

  private record Entry(
      @NotNull Player player,
      @NotNull String world,
      boolean inside,
      @Nullable UUID region,
      boolean playersPrivate,
      boolean named,
      boolean spectator,
      @NotNull Views full,
      @NotNull Views away,
      @NotNull Views unknown) {

    @Override
    public boolean equals(final @Nullable Object other) {
      return other instanceof Entry entry
          && entry.inside == this.inside
          && entry.playersPrivate == this.playersPrivate
          && Objects.equals(entry.region, this.region)
          && entry.full.equals(this.full);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.inside, this.playersPrivate, this.region, this.full);
    }
  }
}
