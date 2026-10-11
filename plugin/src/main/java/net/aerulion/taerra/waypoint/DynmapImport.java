package net.aerulion.taerra.waypoint;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DynmapImport {

  private final @NotNull Plugin plugin;
  private final @NotNull Waypoints waypoints;

  public DynmapImport(final @NotNull Plugin plugin, final @NotNull Waypoints waypoints) {
    super();
    this.plugin = plugin;
    this.waypoints = waypoints;
  }

  public @Nullable Report run(final @NotNull String path) {
    final @NotNull File file = new File(this.plugin.getDataFolder().getParentFile(), path);
    if (!file.isFile()) {
      return null;
    }
    final DynmapMarkers.@NotNull Parsed parsed =
        DynmapMarkers.parse(YamlConfiguration.loadConfiguration(file));
    final @NotNull Set<String> taken = new HashSet<>();
    this.waypoints.names().forEach(name -> taken.add(name.toLowerCase(Locale.ROOT)));
    final @NotNull List<Waypoints.Waypoint> imported = new ArrayList<>();
    final @NotNull Set<String> groups = new HashSet<>();
    int existing = 0;
    int unknownWorlds = 0;
    for (final DynmapMarkers.@NotNull Found found : parsed.points()) {
      final @Nullable World world = this.plugin.getServer().getWorld(found.world());
      if (world == null) {
        unknownWorlds++;
      } else if (!taken.add(found.name().toLowerCase(Locale.ROOT))) {
        existing++;
      } else {
        imported.add(
            new Waypoints.Waypoint(
                found.name(),
                new Location(world, found.x(), found.y(), found.z()),
                Color.fromRGB(found.rgb()),
                found.group()));
        final @Nullable String groupId = Waypoints.groupLayerId(found.group());
        if (groupId != null) {
          groups.add(groupId);
        }
      }
    }
    parsed.hiddenGroups().forEach(this.waypoints::hideByDefault);
    this.waypoints.addAll(imported);
    return new Report(
        parsed.points().size(),
        parsed.shapes(),
        imported.size(),
        groups.size(),
        existing,
        unknownWorlds);
  }

  public record Report(
      int points, int shapes, int imported, int groups, int existing, int unknownWorlds) {

    public boolean empty() {
      return this.points == 0 && this.shapes == 0;
    }
  }
}
