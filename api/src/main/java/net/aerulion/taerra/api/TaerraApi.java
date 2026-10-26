package net.aerulion.taerra.api;

import java.util.Collection;
import net.aerulion.taerra.api.marker.MarkerIcons;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.api.region.Regions;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Entry point to the TAERRA API, registered with Bukkit's {@link org.bukkit.plugin.ServicesManager}
 * while TAERRA enables.
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
   * Tells the map that blocks changed in a way no Bukkit event reports, such as a plugin writing
   * chunks directly, so their tiles are rendered again with the next scheduled re-render. Blocks
   * may lie in several worlds; blocks in worlds the map does not show are ignored. Safe to call
   * from any thread.
   *
   * @param blocks the changed blocks
   */
  void blocksChanged(@NotNull Collection<? extends Block> blocks);

  /**
   * Tells the map that every block in a box changed, like {@link #blocksChanged(Collection)} but
   * without listing each block. Corners are inclusive and may be given in any order; heights
   * outside the world are clamped to it. Safe to call from any thread.
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
