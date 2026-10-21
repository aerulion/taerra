package net.aerulion.taerra.integration;

import com.plotsquared.core.PlotSquared;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.PlotArea;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.model.WorldKeys;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlotSquaredPlots {

  private static final long SYNC_INTERVAL_TICKS = 200L;

  private static final @NotNull Color PLOT = Color.fromRGB(0x9B6FD6);

  private PlotSquaredPlots() {
    super();
  }

  public static @NotNull BukkitTask start(
      final @NotNull Plugin plugin,
      final @NotNull MarkerRegistry registry,
      final @NotNull TaerraConfig config,
      final boolean shownByDefault) {
    final @NotNull MarkerLayer layer =
        registry.layer(plugin, "plotsquared", "PlotSquared Plots", shownByDefault);
    return Bukkit.getScheduler()
        .runTaskTimerAsynchronously(
            plugin, () -> layer.setAll(plots(config)), 20L, SYNC_INTERVAL_TICKS);
  }

  private static @NotNull List<Marker> plots(final @NotNull TaerraConfig config) {
    final @NotNull List<Marker> markers = new ArrayList<>();
    for (final @NotNull PlotArea area : PlotSquared.get().getPlotAreaManager().getAllPlotAreas()) {
      final @Nullable World world = Bukkit.getWorld(area.getWorldName());
      if (world == null || !config.worldEnabled(WorldKeys.id(world))) {
        continue;
      }
      for (final @NotNull Plot plot : area.getPlots()) {
        if (!plot.hasOwner() || !plot.isBasePlot()) {
          continue;
        }
        final @NotNull String label =
            plot.getAlias().isEmpty() ? plot.getId().toString() : plot.getAlias();
        final @NotNull Map<String, String> details = details(plot);
        int i = 0;
        for (final @NotNull CuboidRegion region : plot.getRegions()) {
          final @NotNull BlockVector3 min = region.getMinimumPoint();
          final @NotNull BlockVector3 max = region.getMaximumPoint();
          markers.add(
              Marker.cuboid(
                      area + "/" + plot.getId() + "/" + i++,
                      world.getKey(),
                      new BoundingBox(
                          min.x(),
                          area.getMinBuildHeight(),
                          min.z(),
                          max.x() + 1,
                          area.getMaxBuildHeight(),
                          max.z() + 1),
                      label)
                  .withColor(PLOT)
                  .withDetails(details));
        }
      }
    }
    return markers;
  }

  private static @NotNull Map<String, String> details(final @NotNull Plot plot) {
    final @NotNull Map<String, String> details = new LinkedHashMap<>();
    put(details, "owners", plot.getOwners());
    put(details, "trusted", plot.getTrusted());
    put(details, "members", plot.getMembers());
    return details;
  }

  private static void put(
      final @NotNull Map<String, String> details,
      final @NotNull String key,
      final @NotNull Collection<UUID> players) {
    final @NotNull String names =
        players.stream()
            .map(player -> Bukkit.getOfflinePlayer(player).getName())
            .filter(Objects::nonNull)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.joining(", "));
    if (!names.isEmpty()) {
      details.put(key, names);
    }
  }
}
