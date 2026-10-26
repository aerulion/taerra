package net.aerulion.taerra.api.region;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The map's regions, and which players discovered them. Get it from {@link
 * net.aerulion.taerra.api.TaerraApi#regions()}.
 *
 * <p>Every region returned is a snapshot. All methods are thread safe.
 */
@ApiStatus.NonExtendable
public interface Regions {

  /**
   * Returns every region, of every world.
   *
   * @return the regions, in no particular order
   */
  @NotNull
  Collection<Region> all();

  /**
   * Returns the regions of one world.
   *
   * @param world the world
   * @return the world's regions, in no particular order
   */
  @NotNull
  Collection<Region> in(@NotNull World world);

  /**
   * Returns the region with the given id.
   *
   * @param id the region's id
   * @return the region, or {@code null} if there is none, for example because it was removed
   */
  @Nullable
  Region get(@NotNull UUID id);

  /**
   * Returns the region a location lies in.
   *
   * @param location the location
   * @return the region, or {@code null} if the location is in none
   */
  @Nullable
  Region at(@NotNull Location location);

  /**
   * Returns whether a player discovered a region since its discoveries were last reset. A region is
   * discovered by walking into it while it is {@link Region#discoverable() discoverable}.
   *
   * <p>The first call for a player who is offline may read their data from disk, so prefer calling
   * it off the server thread for them.
   *
   * @param player the player's UUID
   * @param region the region; only its id counts, so an older snapshot of it works too
   * @return {@code true} if the player discovered the region
   */
  boolean discovered(@NotNull UUID player, @NotNull Region region);

  /**
   * Returns when a player discovered a region, like {@link #discovered(UUID, Region)}.
   *
   * @param player the player's UUID
   * @param region the region; only its id counts, so an older snapshot of it works too
   * @return when the player discovered the region, or {@code null} if they have not
   */
  @Nullable
  Instant discoveredAt(@NotNull UUID player, @NotNull Region region);
}
