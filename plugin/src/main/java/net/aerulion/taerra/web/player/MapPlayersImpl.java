package net.aerulion.taerra.web.player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.aerulion.taerra.api.player.MapPlayers;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MapPlayersImpl implements MapPlayers, Listener {

  private final @NotNull Map<UUID, Set<String>> hiddenBy = new ConcurrentHashMap<>();

  @Override
  public void hide(final @NotNull Plugin plugin, final @NotNull UUID player) {
    this.hiddenBy.compute(
        player,
        (uuid, plugins) -> {
          final @NotNull Set<String> hiding = plugins == null ? new HashSet<>() : plugins;
          hiding.add(plugin.getName());
          return hiding;
        });
  }

  @Override
  public void show(final @NotNull Plugin plugin, final @NotNull UUID player) {
    this.hiddenBy.computeIfPresent(player, (uuid, plugins) -> without(plugins, plugin.getName()));
  }

  @Override
  public boolean hidden(final @NotNull Player player) {
    return this.hiddenBy.containsKey(player.getUniqueId())
        || !PlayerMapPayloadSerializer.shouldIncludePlayer(player);
  }

  @EventHandler
  public void onDisable(final @NotNull PluginDisableEvent event) {
    forget(event.getPlugin());
  }

  void forget(final @NotNull Plugin plugin) {
    final @NotNull String name = plugin.getName();
    for (final @NotNull UUID player : Set.copyOf(this.hiddenBy.keySet())) {
      this.hiddenBy.computeIfPresent(player, (uuid, plugins) -> without(plugins, name));
    }
  }

  private static @Nullable Set<String> without(
      final @NotNull Set<String> plugins, final @NotNull String name) {
    plugins.remove(name);
    return plugins.isEmpty() ? null : plugins;
  }
}
