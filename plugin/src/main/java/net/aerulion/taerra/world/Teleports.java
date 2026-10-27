package net.aerulion.taerra.world;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.aerulion.taerra.api.event.web.WebTeleportEvent;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.util.MainThread;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Teleports {

  private final @NotNull Plugin plugin;

  public Teleports(final @NotNull Plugin plugin) {
    super();
    this.plugin = plugin;
  }

  public @NotNull CompletableFuture<Outcome> teleport(
      final @NotNull UUID uuid,
      final @NotNull String worldName,
      final int x,
      final @Nullable Integer y,
      final int z,
      final boolean announce) {
    return MainThread.compose(
            this.plugin,
            () -> {
              final @Nullable Player player = this.plugin.getServer().getPlayer(uuid);
              final @Nullable World world = WorldKeys.resolve(worldName);
              if (player == null) {
                return CompletableFuture.completedFuture(Outcome.OFFLINE);
              }
              if (!TaerraPermission.TELEPORT.check(player) || world == null) {
                return CompletableFuture.completedFuture(Outcome.FORBIDDEN);
              }
              if (!WorldPositions.insideBorder(world, x, z)) {
                return CompletableFuture.completedFuture(Outcome.OUTSIDE_BORDER);
              }
              return WorldPositions.clearY(world, x, y, z)
                  .thenCompose(
                      feet -> {
                        final @NotNull Location to =
                            new Location(
                                world, x + .5, feet, z + .5, player.getYaw(), player.getPitch());
                        final @NotNull WebTeleportEvent event = new WebTeleportEvent(player, to);
                        this.plugin.getServer().getPluginManager().callEvent(event);
                        if (event.isCancelled()) {
                          return CompletableFuture.completedFuture(Outcome.REFUSED);
                        }
                        return player
                            .teleportAsync(to, PlayerTeleportEvent.TeleportCause.PLUGIN)
                            .thenApply(
                                teleported -> {
                                  if (teleported && announce) {
                                    player.sendMessage(
                                        Messages.webTeleport(
                                            x + " " + feet + " " + z, WorldKeys.id(world)));
                                  }
                                  return teleported ? Outcome.TELEPORTED : Outcome.REFUSED;
                                });
                      });
            })
        .exceptionally(error -> Outcome.FAILED);
  }

  public enum Outcome {
    TELEPORTED,
    OFFLINE,
    FORBIDDEN,
    OUTSIDE_BORDER,
    REFUSED,
    FAILED
  }
}
