package net.aerulion.taerra.api.waypoint;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.PointMarker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads and sets waypoints. Get it from {@link net.aerulion.taerra.api.TaerraApi#waypoints()}.
 *
 * <p>Unlike the markers of your own layers, waypoints belong to the players: they can edit,
 * navigate to and delete them, and TAERRA keeps them across restarts. A waypoint is a marker whose
 * {@link Marker#label() label} is its name, unique among the shared waypoints and among each
 * player's own, ignoring case; TAERRA gives it its id from that name. Players also draw lines and
 * areas, which you read here too; a plugin sets points.
 *
 * <p>Reading is thread safe. Setting and removing must happen on the server thread, and fires the
 * {@link net.aerulion.taerra.api.event.waypoint waypoint events}.
 */
@ApiStatus.NonExtendable
public interface Waypoints {

  /** The most personal waypoints one player keeps. */
  int MAX_PERSONAL = 100;

  /**
   * Returns the shared waypoints, of every group.
   *
   * @return a snapshot of the shared waypoints, in no particular order
   */
  @NotNull
  Collection<Marker> shared();

  /**
   * Returns a player's personal waypoints. The first call for a player who is offline may read
   * their data from disk, so prefer calling it off the server thread for them.
   *
   * @param player the player's UUID
   * @return a snapshot of the player's waypoints, hidden ones included, in the order they were last
   *     saved
   */
  @NotNull
  List<Marker> personal(@NotNull UUID player);

  /**
   * Sets a shared waypoint, replacing the one with the same name. Its id is ignored.
   *
   * @param waypoint the waypoint, its label a valid name
   * @param group the name of the group it is listed in, created if it is new, or {@code null} for
   *     none
   * @throws IllegalArgumentException if the label or the group is not a valid name: up to 32
   *     letters, digits, spaces and {@code _ . , : ' & ( ) # ! ? + -}, with at least one letter or
   *     digit
   * @throws IllegalStateException if not called on the server thread
   */
  void setShared(@NotNull PointMarker waypoint, @Nullable String group);

  /**
   * Removes a shared waypoint.
   *
   * @param name the waypoint's name, in any case
   * @return {@code true} if there was one
   * @throws IllegalStateException if not called on the server thread
   */
  boolean removeShared(@NotNull String name);

  /**
   * Sets one of a player's personal waypoints, replacing theirs with the same name, and keeping it
   * hidden if they hid it. Its id is ignored.
   *
   * @param player the player's UUID
   * @param waypoint the waypoint, its label a valid name
   * @return whether it was saved
   * @throws IllegalArgumentException if the label is not a valid name: up to 32 letters, digits,
   *     spaces and {@code _ . , : ' & ( ) # ! ? + -}, with at least one letter or digit
   * @throws IllegalStateException if not called on the server thread
   */
  @NotNull
  Result setPersonal(@NotNull UUID player, @NotNull PointMarker waypoint);

  /**
   * Removes one of a player's personal waypoints.
   *
   * @param player the player's UUID
   * @param name the waypoint's name, in any case
   * @return {@code true} if there was one
   * @throws IllegalStateException if not called on the server thread
   */
  boolean removePersonal(@NotNull UUID player, @NotNull String name);

  /** Whether a personal waypoint was saved. */
  enum Result {
    /** The waypoint was saved. */
    SAVED,
    /** The player already keeps {@link #MAX_PERSONAL} personal waypoints. */
    FULL,
    /** The waypoint could not be written to disk. */
    FAILED
  }
}
