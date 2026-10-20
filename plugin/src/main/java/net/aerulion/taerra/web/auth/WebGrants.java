package net.aerulion.taerra.web.auth;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.permission.TaerraPermission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WebGrants {

  public static final @NotNull String MOD = "taerra.mod";
  private static final long REFRESH_TICKS = 20L;

  private WebGrants() {
    super();
  }

  public static @NotNull Set<String> of(
      final @NotNull Player player, final @NotNull TaerraConfig config) {
    return Stream.concat(
            nodes(config).filter(player::hasPermission),
            ModHandshake.uses(player) ? Stream.of(MOD) : Stream.empty())
        .collect(Collectors.toUnmodifiableSet());
  }

  public static @NotNull Stream<String> nodes(final @NotNull TaerraConfig config) {
    return Stream.concat(
        config.webPermissions().stream(),
        Arrays.stream(TaerraPermission.values()).map(TaerraPermission::node));
  }

  public static void start(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig config,
      final @NotNull WebSessions sessions) {
    Bukkit.getScheduler()
        .runTaskTimer(
            plugin,
            () -> {
              final @NotNull Map<UUID, Set<String>> granted = new HashMap<>();
              for (final @NotNull UUID uuid : sessions.accounts()) {
                final @Nullable Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                  granted.put(uuid, of(player, config));
                } else if (Bukkit.getOfflinePlayer(uuid).isBanned()) {
                  sessions.endAll(uuid);
                } else {
                  sessions.revoke(uuid, MOD);
                }
              }
              sessions.grant(granted);
            },
            REFRESH_TICKS,
            REFRESH_TICKS);
  }
}
