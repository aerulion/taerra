package net.aerulion.taerra.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import net.aerulion.taerra.api.marker.AreaMarker;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.region.RegionShapes;
import net.aerulion.taerra.util.HexColors;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TownyTowns {

  public static final @NotNull String LAYER = "towny";

  private static final long SYNC_INTERVAL_TICKS = 200L;

  private static final @NotNull Color TOWN = Color.fromRGB(0x3C8DDE);

  private TownyTowns() {
    super();
  }

  public static @NotNull BukkitTask start(
      final @NotNull Plugin plugin,
      final @NotNull MarkerRegistry registry,
      final @NotNull TaerraConfig config,
      final boolean shownByDefault) {
    final @NotNull MarkerLayer layer = registry.layer(plugin, LAYER, "Towny Towns", shownByDefault);
    return Bukkit.getScheduler()
        .runTaskTimerAsynchronously(
            plugin, () -> layer.setAll(towns(config)), 20L, SYNC_INTERVAL_TICKS);
  }

  private static @NotNull List<Marker> towns(final @NotNull TaerraConfig config) {
    final int size = TownySettings.getTownBlockSize();
    final @NotNull List<Marker> markers = new ArrayList<>();
    for (final @NotNull Town town : TownyAPI.getInstance().getTowns()) {
      final @NotNull Map<World, Set<Long>> blocks = new HashMap<>();
      for (final @NotNull TownBlock block : town.getTownBlocks()) {
        final @Nullable World world = block.getWorld().getBukkitWorld();
        if (world != null && config.worldEnabled(WorldKeys.id(world))) {
          blocks
              .computeIfAbsent(world, _ -> new HashSet<>())
              .add(ChunkCoordinate.key(block.getX(), block.getZ()));
        }
      }
      final @NotNull Color color = color(town);
      final @NotNull Map<String, String> details = details(town);
      blocks.forEach(
          (world, keys) -> {
            for (final @NotNull Set<Long> area : RegionShapes.areas(keys)) {
              final long first = area.stream().mapToLong(Long::longValue).min().orElseThrow();
              final @NotNull List<List<long[]>> rings = RegionShapes.rings(area);
              for (int i = 0; i < rings.size(); i++) {
                final @NotNull AreaMarker marker =
                    Marker.polygon(
                            WorldKeys.id(world) + "/" + town.getUUID() + "/" + first + "/" + i,
                            world.getKey(),
                            rings.get(i).stream()
                                .map(corner -> new Vector(corner[0] * size, 0, corner[1] * size))
                                .toList(),
                            world.getMinHeight(),
                            world.getMaxHeight(),
                            town.getName())
                        .withColor(color)
                        .withDetails(details);
                markers.add(rings.size() > 1 ? marker.hollowed() : marker);
              }
            }
          });
      final @Nullable Location spawn = town.isPublic() ? town.getSpawnOrNull() : null;
      if (spawn != null
          && spawn.isWorldLoaded()
          && config.worldEnabled(WorldKeys.id(spawn.getWorld()))) {
        markers.add(
            Marker.point(
                    WorldKeys.id(spawn.getWorld()) + "/" + town.getUUID() + "/spawn",
                    spawn.getWorld().getKey(),
                    new Vector(spawn.getBlockX() + .5, spawn.getBlockY(), spawn.getBlockZ() + .5),
                    town.getName())
                .withColor(color)
                .withDetails(details));
      }
    }
    return markers;
  }

  private static @NotNull Color color(final @NotNull Town town) {
    final @NotNull OptionalInt rgb = HexColors.parseRgb(town.getMapColorHexCode());
    return rgb.isPresent() ? Color.fromRGB(rgb.getAsInt()) : TOWN;
  }

  private static @NotNull Map<String, String> details(final @NotNull Town town) {
    final @NotNull Map<String, String> details = new LinkedHashMap<>();
    final @Nullable Resident mayor = town.getMayor();
    if (mayor != null) {
      details.put("mayor", mayor.getName());
    }
    final @Nullable Nation nation = town.getNationOrNull();
    if (nation != null) {
      details.put("nation", nation.getName());
    }
    details.put("residents", Integer.toString(town.getNumResidents()));
    return details;
  }
}
