package net.aerulion.taerra.web.player;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.limit.WorldLimits;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.model.WorldLimit;
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
  private final @NotNull ObjectMapper mapper;
  private final @NotNull String anonymousSalt = UUID.randomUUID().toString();
  private volatile @NotNull Map<String, String> lastSent = Map.of();
  private volatile @NotNull Map<String, String> lastSentWithHidden = Map.of();
  private volatile @NotNull Map<UUID, Player> shown = Map.of();
  private volatile @NotNull Map<UUID, Player> shownWithHidden = Map.of();
  private @Nullable BukkitTask task;

  public PlayerPositionBroadcasterImpl(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig config,
      final @NotNull MapWebSocketHandler wsHandler,
      final @NotNull WorldLimits limits) {
    super();
    this.plugin = plugin;
    this.config = config;
    this.wsHandler = wsHandler;
    this.limits = limits;
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
    (viewer.canSeeHidden() ? this.lastSentWithHidden : this.lastSent)
        .forEach(
            (world, json) -> {
              if (json.length() > 2 && viewer.canSee(this.config, world)) {
                players.add(json.substring(1, json.length() - 1));
              }
            });
    return players.toString();
  }

  @Override
  public @Nullable Player shown(final @NotNull UUID uuid, final boolean withHidden) {
    return (withHidden ? this.shownWithHidden : this.shown).get(uuid);
  }

  private @NotNull Map<String, Object> anonymize(
      final @NotNull Map<String, Object> data, final @NotNull UUID uuid) {
    final @NotNull Map<String, Object> anonymous = new HashMap<>(data);
    anonymous.remove("name");
    anonymous.put("anonymous", true);
    anonymous.put("color", ANONYMOUS_COLOR);
    anonymous.put(
        "uuid",
        UUID.nameUUIDFromBytes((this.anonymousSalt + uuid).getBytes(StandardCharsets.UTF_8))
            .toString());
    return anonymous;
  }

  private void broadcastPlayerUpdates() {
    final @NotNull Map<String, String> sent = new HashMap<>();
    final @NotNull Map<String, String> sentWithHidden = new HashMap<>();
    final @NotNull Map<UUID, Player> shown = new HashMap<>();
    final @NotNull Map<UUID, Player> shownWithHidden = new HashMap<>();
    for (final @NotNull World world : Bukkit.getWorlds()) {
      final @NotNull String worldName = WorldKeys.id(world);
      if (!this.config.worldEnabled(worldName)) {
        continue;
      }

      final @NotNull List<Map<String, Object>> playerData = new ArrayList<>();
      final @NotNull List<Map<String, Object>> withHidden = new ArrayList<>();
      final @Nullable WorldLimit limit = this.limits.get(worldName);

      for (final @NotNull Player player : world.getPlayers()) {
        if (limit != null
            && !limit.contains(
                player.getLocation().getBlockX(), player.getLocation().getBlockZ())) {
          continue;
        }
        final boolean hidden =
            !PlayerMapPayloadSerializer.shouldIncludePlayer(player)
                || this.limits.hidden(
                    worldName,
                    player.getLocation().getBlockX() >> 4,
                    player.getLocation().getBlockZ() >> 4);
        final @NotNull Map<String, Object> data = PlayerMapPayloadSerializer.serialize(player);
        data.put("hidden", hidden);
        withHidden.add(data);
        shownWithHidden.put(player.getUniqueId(), player);
        if (hidden) {
          continue;
        }
        if (player.isAllowingServerListings()) {
          playerData.add(data);
          shown.put(player.getUniqueId(), player);
        } else {
          playerData.add(anonymize(data, player.getUniqueId()));
        }
      }

      try {
        sent.put(worldName, this.mapper.writeValueAsString(playerData));
        sentWithHidden.put(worldName, this.mapper.writeValueAsString(withHidden));
      } catch (final Exception e) {
        this.plugin.getLogger().warning("Failed to serialize player positions: " + e.getMessage());
      }
    }
    final boolean changed =
        !sent.equals(this.lastSent) || !sentWithHidden.equals(this.lastSentWithHidden);
    this.lastSent = Map.copyOf(sent);
    this.lastSentWithHidden = Map.copyOf(sentWithHidden);
    this.shown = Map.copyOf(shown);
    this.shownWithHidden = Map.copyOf(shownWithHidden);
    if (changed) {
      this.wsHandler.broadcast(viewer -> "{\"type\":\"players\",\"data\":" + players(viewer) + "}");
    }
  }
}
