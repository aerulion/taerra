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

  public static final @NotNull String MOD = "taerra.mod";
  private static final long REFRESH_TICKS = 20L;

  private WebGrants() {
    super();
  }

  public static @NotNull Set<String> of(
      final @NotNull Player player, final @NotNull TaerraConfig config) {
    return Stream.concat(
            Stream.concat(
                config.webPermissions().stream(),
                player.getListeningPluginChannels().contains(ModHandshake.CHANNEL)
                    ? Stream.of(MOD)
                    : Stream.empty()),
            Stream.of(
                TaerraPermission.WEB_TELEPORT.node(),
                TaerraPermission.WEB_SEE_HIDDEN.node(),
                TaerraPermission.WEB_RENDER.node(),
                TaerraPermission.WEB_HIDE.node(),
                TaerraPermission.WEB_MARKERS.node(),
                TaerraPermission.WEB_MARKERS_PRIVATE.node(),
                TaerraPermission.WEB_MARKERS_PERSONAL.node()))
        .filter(node -> node.equals(MOD) || player.hasPermission(node))
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
                } else if (Bukkit.getOfflinePlayer(uuid).isBanned()) {
                  sessions.endAll(uuid);
                } else {
                  sessions.revoke(uuid, MOD);
                }
              }
            },
            REFRESH_TICKS,
            REFRESH_TICKS);
  }
}
