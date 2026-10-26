package net.aerulion.taerra.api;

import java.util.Collection;
import net.aerulion.taerra.api.marker.MarkerIcons;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.api.navigation.Navigation;
import net.aerulion.taerra.api.player.MapPlayers;
import net.aerulion.taerra.api.region.Regions;
import net.aerulion.taerra.api.waypoint.Waypoints;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Entry point to the TAERRA API, registered with Bukkit's {@link org.bukkit.plugin.ServicesManager}
 * while TAERRA enables. All methods are thread safe.
 */
@ApiStatus.NonExtendable
public interface TaerraApi {

  /**
   * Returns the API of the running TAERRA plugin.
   *
   * @return the API
   * @throws IllegalStateException if TAERRA is not enabled
   */
  static @NotNull TaerraApi get() {
    final @Nullable TaerraApi api = Bukkit.getServicesManager().load(TaerraApi.class);
    if (api == null) {
      throw new IllegalStateException("TAERRA is not enabled");
    }
    return api;
  }

  /**
   * Returns the registry of the map's marker layers.
   *
   * @return the marker registry
   */
  @NotNull
  MarkerRegistry markers();

  /**
   * Returns the icons point markers can show.
   *
   * @return the marker icons
   */
  @NotNull
  MarkerIcons icons();

  /**
   * Returns the map's regions, and which players discovered them.
   *
   * @return the regions
   */
  @NotNull
  Regions regions();

  /**
   * Returns the shared and personal waypoints.
   *
   * @return the waypoints
   */
  @NotNull
  Waypoints waypoints();

  /**
   * Returns where players navigate to.
   *
   * @return the navigation targets
   */
  @NotNull
  Navigation navigation();

  /**
   * Returns how players show on the map.
   *
   * @return the map's players
   */
  @NotNull
  MapPlayers players();

  /**
   * Returns the web map's address: {@code web.public-url}, or without it the server's address and
   * the map's port, which only reaches players on the same network.
   *
   * @return the map's address, without a trailing slash
   */
  @NotNull
  String mapUrl();

  /**
   * Returns a link that opens the web map on a world. Viewers see it only if they may see that
   * world.
   *
   * @param world the world
   * @return the link
   */
  @NotNull
  String mapUrl(@NotNull World world);

  /**
   * Returns a link that opens the web map centered on a location, for a chat message or a Discord
   * bot. Viewers see the place only if they may see its world.
   *
   * @param location the location
   * @return the link
   * @throws IllegalArgumentException if the location has no world
   */
  @NotNull
  String mapUrl(@NotNull Location location);

  /**
   * Tells the map that blocks changed in a way no Bukkit event reports, such as a plugin writing
   * chunks directly, so their tiles are rendered again with the next scheduled re-render. Blocks
   * may lie in several worlds; blocks in worlds the map does not show are ignored.
   *
   * @param blocks the changed blocks
   */
  void blocksChanged(@NotNull Collection<? extends Block> blocks);

  /**
   * Tells the map that every block in a box changed, like {@link #blocksChanged(Collection)} but
   * without listing each block. Corners are inclusive and may be given in any order; heights
   * outside the world are clamped to it.
   *
   * @param world the world the box lies in
   * @param x1 the x coordinate of one corner
   * @param y1 the y coordinate of one corner
   * @param z1 the z coordinate of one corner
   * @param x2 the x coordinate of the opposite corner
   * @param y2 the y coordinate of the opposite corner
   * @param z2 the z coordinate of the opposite corner
   */
  void areaChanged(@NotNull World world, int x1, int y1, int z1, int x2, int y2, int z2);
}
