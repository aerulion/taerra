package net.aerulion.taerra.mod.web;

import com.google.gson.JsonArray;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.aerulion.taerra.mod.pick.PickCache;
import net.aerulion.taerra.mod.tile.TileCache;
import net.aerulion.taerra.mod.web.model.Account;
import net.aerulion.taerra.mod.web.model.Device;
import net.aerulion.taerra.mod.web.model.Layer;
import net.aerulion.taerra.mod.web.model.MapConfig;
import net.aerulion.taerra.mod.web.model.NavigationTarget;
import net.aerulion.taerra.mod.web.model.PersonalWaypoint;
import net.aerulion.taerra.mod.web.model.Player;
import net.aerulion.taerra.mod.web.model.Progress;
import net.aerulion.taerra.mod.web.model.Sky;
import net.aerulion.taerra.mod.web.model.Waypoint;
import net.aerulion.taerra.mod.web.model.WaypointGroup;
import net.aerulion.taerra.mod.web.model.World;
import org.jetbrains.annotations.Nullable;

public sealed interface WebMap permits WebMapImpl {

  String base();

  boolean unreachable();

  boolean live();

  @Nullable
  MapConfig config();

  List<World> worlds();

  @Nullable
  World world(@Nullable String name);

  List<Player> players();

  @Nullable
  Sky sky(String world);

  @Nullable
  Player player(@Nullable String uuid);

  double[] position(Player player);

  @Nullable
  Account account();

  @Nullable
  NavigationTarget navigation();

  long navigationVersion();

  void navigate(@Nullable NavigationTarget target);

  @Nullable
  Progress progress();

  List<Layer> markers(String world);

  boolean shown(Layer layer);

  void setShown(Layer layer, boolean shown);

  void waypointGroups(Consumer<List<WaypointGroup>> then);

  void saveWaypoint(Waypoint waypoint, boolean privateGroup, IntConsumer then);

  void deleteWaypoint(String world, String name, boolean personal, IntConsumer then);

  void teleport(String world, int x, @Nullable Integer y, int z, IntConsumer then);

  void devices(Consumer<@Nullable List<Device>> then);

  void personalWaypoints(Consumer<@Nullable List<PersonalWaypoint>> then);

  void setHidden(List<String> names, boolean hidden, IntConsumer then);

  void importWaypoints(
      String mode, JsonArray waypoints, BiConsumer<Integer, @Nullable String> then);

  void logOutDevice(String id, IntConsumer then);

  void area(String action, String world, Collection<Long> chunks, IntConsumer then);

  String link(
      String world, int x, @Nullable Integer y, int z, long zoom, boolean isometric, int rotation);

  TileCache tiles();

  PickCache picks();

  void addListener(WebMapListener listener);

  void removeListener(WebMapListener listener);

  void focus(@Nullable String world);

  void tick(String playerWorld);

  void close();
}
