package net.aerulion.taerra.web.player;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.limit.WorldLimits;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.model.WorldLimit;
import net.aerulion.taerra.region.MapRegion;
import net.aerulion.taerra.region.RegionVisibility;
import net.aerulion.taerra.web.auth.Viewer;
import net.aerulion.taerra.web.socket.MapWebSocketHandler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlayerPositionBroadcasterImpl implements PlayerPositionBroadcaster {

  private static final long UPDATE_INTERVAL_TICKS = 20L;
  private static final String ANONYMOUS_COLOR = "#a0a0a0";
  private final @NotNull Plugin plugin;
  private final @NotNull TaerraConfig config;
  private final @NotNull MapWebSocketHandler wsHandler;
  private final @NotNull WorldLimits limits;
  private final @NotNull RegionVisibility visibility;
  private final @NotNull ObjectMapper mapper;
  private final @NotNull String anonymousSalt = UUID.randomUUID().toString();
  private volatile @NotNull Map<String, List<Entry>> lastSent = Map.of();
  private volatile @NotNull Map<UUID, Entry> shown = Map.of();
  private @Nullable BukkitTask task;

  public PlayerPositionBroadcasterImpl(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig config,
      final @NotNull MapWebSocketHandler wsHandler,
      final @NotNull WorldLimits limits,
      final @NotNull RegionVisibility visibility) {
    super();
    this.plugin = plugin;
    this.config = config;
    this.wsHandler = wsHandler;
    this.limits = limits;
    this.visibility = visibility;
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
    this.lastSent.forEach(
        (world, entries) -> {
          if (!viewer.canSee(this.config, world)) {
            return;
          }
          for (final @NotNull Entry entry : entries) {
            if (viewer.canSeeHidden()) {
              players.add(entry.withHidden());
            } else if (entry.listed() != null && visible(entry, viewer)) {
              players.add(entry.listed());
            }
          }
        });
    return players.toString();
  }

  @Override
  public @Nullable Player shown(final @NotNull UUID uuid, final @NotNull Viewer viewer) {
    final @Nullable Entry entry = this.shown.get(uuid);
    return entry == null || (!viewer.canSeeHidden() && (!entry.named() || !visible(entry, viewer)))
        ? null
        : entry.player();
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
    final @NotNull Map<String, List<Entry>> sent = new HashMap<>();
    final @NotNull Map<UUID, Entry> shown = new HashMap<>();
    for (final @NotNull World world : Bukkit.getWorlds()) {
      final @NotNull String worldName = WorldKeys.id(world);
      if (!this.config.worldEnabled(worldName)) {
        continue;
      }

      final @NotNull List<Entry> entries = new ArrayList<>();
      final @Nullable WorldLimit limit = this.limits.get(worldName);

      for (final @NotNull Player player : world.getPlayers()) {
        if (limit != null
            && !limit.contains(
                player.getLocation().getBlockX(), player.getLocation().getBlockZ())) {
          continue;
        }
        final @Nullable MapRegion region =
            this.visibility
                .regions()
                .foggedAt(
                    worldName,
                    player.getLocation().getBlockX() >> 4,
                    player.getLocation().getBlockZ() >> 4);
        final boolean included = PlayerMapPayloadSerializer.shouldIncludePlayer(player);
        final @NotNull Map<String, Object> data = PlayerMapPayloadSerializer.serialize(player);
        try {
          data.put("hidden", !included || region != null);
          final @NotNull String withHidden = this.mapper.writeValueAsString(data);
          data.put("hidden", false);
          final @Nullable String listed =
              !included
                  ? null
                  : this.mapper.writeValueAsString(
                      player.isAllowingServerListings() ? data : anonymize(data, player));
          final @NotNull Entry entry =
              new Entry(
                  player,
                  region == null ? null : region.id(),
                  included && player.isAllowingServerListings(),
                  listed,
                  withHidden);
          entries.add(entry);
          shown.put(player.getUniqueId(), entry);
        } catch (final Exception e) {
          this.plugin
              .getLogger()
              .warning("Failed to serialize player positions: " + e.getMessage());
        }
      }
      sent.put(worldName, List.copyOf(entries));
    }
    final boolean changed = !sent.equals(this.lastSent);
    this.lastSent = Map.copyOf(sent);
    this.shown = Map.copyOf(shown);
    if (changed) {
      this.wsHandler.broadcast(viewer -> "{\"type\":\"players\",\"data\":" + players(viewer) + "}");
    }
  }

  private record Entry(
      @NotNull Player player,
      @Nullable UUID region,
      boolean named,
      @Nullable String listed,
      @NotNull String withHidden) {

    @Override
    public boolean equals(final @Nullable Object other) {
      return other instanceof Entry entry
          && Objects.equals(entry.region, this.region)
          && entry.withHidden.equals(this.withHidden)
          && Objects.equals(entry.listed, this.listed);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.region, this.listed, this.withHidden);
    }
  }
}
