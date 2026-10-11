package net.aerulion.taerra.world;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.permission.TaerraPermission;
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
    final @NotNull CompletableFuture<Outcome> result = new CompletableFuture<>();
    this.plugin
        .getServer()
        .getScheduler()
        .runTask(
            this.plugin,
            () -> {
              final @Nullable Player player = this.plugin.getServer().getPlayer(uuid);
              final @Nullable World world = WorldKeys.resolve(worldName);
              if (player == null) {
                result.complete(Outcome.OFFLINE);
                return;
              }
              if (!TaerraPermission.WEB_TELEPORT.check(player) || world == null) {
                result.complete(Outcome.FORBIDDEN);
                return;
              }
              if (!WorldPositions.insideBorder(world, x, z)) {
                result.complete(Outcome.OUTSIDE_BORDER);
                return;
              }
              WorldPositions.clearY(world, x, y, z)
                  .thenCompose(
                      feet ->
                          player
                              .teleportAsync(
                                  new Location(
                                      world,
                                      x + .5,
                                      feet,
                                      z + .5,
                                      player.getYaw(),
                                      player.getPitch()),
                                  PlayerTeleportEvent.TeleportCause.PLUGIN)
                              .thenApply(
                                  teleported -> {
                                    if (teleported && announce) {
                                      player.sendMessage(
                                          Messages.webTeleport(
                                              x + " " + feet + " " + z, WorldKeys.id(world)));
                                    }
                                    return teleported ? Outcome.TELEPORTED : Outcome.REFUSED;
                                  }))
                  .whenComplete(
                      (outcome, error) ->
                          result.complete(error == null ? outcome : Outcome.FAILED));
            });
    return result;
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
