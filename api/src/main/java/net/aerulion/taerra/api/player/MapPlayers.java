package net.aerulion.taerra.api.player;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Hides players from the map, the way TAERRA hides spectators, invisible and vanished players. Get
 * it from {@link net.aerulion.taerra.api.TaerraApi#players()}.
 *
 * <p>A hidden player is left out for everyone but staff with {@code taerra.see.hidden-players}, who
 * still see them, marked as hidden. Each plugin hides a player for itself, and the player stays
 * hidden while any plugin hides them. Hiding lasts until your plugin shows the player again or is
 * disabled, through rejoins, but not through restarts. All methods are thread safe.
 */
@ApiStatus.NonExtendable
public interface MapPlayers {

  /**
   * Hides a player from the map for your plugin.
   *
   * @param plugin your plugin
   * @param player the player's UUID
   */
  void hide(@NotNull Plugin plugin, @NotNull UUID player);

  /**
   * Stops hiding a player for your plugin. They show again once no plugin hides them and nothing
   * else does, like spectator mode.
   *
   * @param plugin your plugin
   * @param player the player's UUID
   */
  void show(@NotNull Plugin plugin, @NotNull UUID player);

  /**
   * Returns whether the map hides a player for any reason: a plugin hiding them, spectator mode,
   * invisibility, a vanish plugin's {@code vanished} metadata, or {@code taerra.hide}.
   *
   * @param player the player
   * @return {@code true} if the player is hidden from the map
   */
  boolean hidden(@NotNull Player player);
}
