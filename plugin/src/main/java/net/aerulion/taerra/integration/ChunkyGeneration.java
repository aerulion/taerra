package net.aerulion.taerra.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.permission.TaerraPermission;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.popcraft.chunky.api.ChunkyAPI;

public final class ChunkyGeneration implements Predicate<World> {

  private static final long CHECK_INTERVAL_TICKS = 20L;

  private final @NotNull Plugin plugin;
  private final @NotNull ChunkyAPI chunky;
  private final @NotNull Map<World, Integer> skipped = new HashMap<>();

  private ChunkyGeneration(final @NotNull Plugin plugin, final @NotNull ChunkyAPI chunky) {
    super();
    this.plugin = plugin;
    this.chunky = chunky;
  }

  public static @Nullable Predicate<World> start(final @NotNull Plugin plugin) {
    final @Nullable ChunkyAPI chunky =
        plugin.getServer().getServicesManager().load(ChunkyAPI.class);
    if (chunky == null) {
      return null;
    }
    final @NotNull ChunkyGeneration generation = new ChunkyGeneration(plugin, chunky);
    plugin
        .getServer()
        .getScheduler()
        .runTaskTimer(plugin, generation::check, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    return generation;
  }

  @Override
  public boolean test(final @NotNull World world) {
    if (!this.chunky.isRunning(world.getName())) {
      return false;
    }
    this.skipped.merge(world, 1, Integer::sum);
    return true;
  }

  private void check() {
    this.skipped
        .entrySet()
        .removeIf(
            entry -> {
              if (this.chunky.isRunning(entry.getKey().getName())) {
                return false;
              }
              final @NotNull Component message =
                  Messages.renderSuggestion(WorldKeys.id(entry.getKey()), entry.getValue());
              for (final @NotNull Player player : this.plugin.getServer().getOnlinePlayers()) {
                if (TaerraPermission.COMMAND_RENDER.check(player)) {
                  player.sendMessage(message);
                }
              }
              this.plugin.getServer().getConsoleSender().sendMessage(message);
              return true;
            });
  }
}
