package net.aerulion.taerra.api.region;

import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A region of the map: chunks of one world that touch at their edges, drawn in the web map or in
 * game. Regions never overlap.
 *
 * <p>A region is a snapshot: it keeps the values it had when you got it, even after the region is
 * edited. Compare regions by {@link #id()}, which stays the same through every edit.
 */
@ApiStatus.NonExtendable
public interface Region {

  /**
   * Returns the id this region keeps through renames and edits.
   *
   * @return the region's id
   */
  @NotNull
  UUID id();

  /**
   * Returns the name shown on the map.
   *
   * @return the region's name, empty if it has none
   */
  @NotNull
  String name();

  /**
   * Returns the key of the world this region lies in.
   *
   * @return the world's key
   */
  @NotNull
  NamespacedKey world();

  /**
   * Returns the color of the region's outline and fill.
   *
   * @return the region's color
   */
  @NotNull
  Color color();

  /**
   * Returns how the region looks to players who have not discovered it.
   *
   * @return the region's fog
   */
  @NotNull
  RegionFog fog();

  /**
   * Returns whether players discover this region by walking into it.
   *
   * @return {@code true} if the region is discoverable
   */
  boolean discoverable();

  /**
   * Returns whether a chunk of this region's world belongs to it.
   *
   * @param chunkX the chunk's x coordinate
   * @param chunkZ the chunk's z coordinate
   * @return {@code true} if the chunk is part of the region
   */
  boolean contains(int chunkX, int chunkZ);

  /**
   * Returns whether a location lies in this region.
   *
   * @param location the location
   * @return {@code true} if the location is in this region's world and one of its chunks
   */
  boolean contains(@NotNull Location location);
}
