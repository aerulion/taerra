package net.aerulion.taerra.web.auth;

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

  private static final long REFRESH_TICKS = 20L;

  private WebGrants() {
    super();
  }

  public static @NotNull Set<String> of(
      final @NotNull Player player, final @NotNull TaerraConfig config) {
    return Stream.concat(
            config.webPermissions().stream(),
            Stream.of(
                TaerraPermission.WEB_TELEPORT.node(),
                TaerraPermission.WEB_SEE_HIDDEN.node(),
                TaerraPermission.COMMAND_WAYPOINT.node(),
                TaerraPermission.COMMAND_CONFIG.node()))
        .filter(player::hasPermission)
        .collect(Collectors.toUnmodifiableSet());
  }

  public static void start(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig config,
      final @NotNull WebSessions sessions) {
    Bukkit.getScheduler()
        .runTaskTimer(
            plugin,
            () -> {
              for (final @NotNull UUID uuid : sessions.accounts()) {
                final @Nullable Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                  sessions.grant(uuid, of(player, config));
                }
              }
            },
            REFRESH_TICKS,
            REFRESH_TICKS);
  }
}
