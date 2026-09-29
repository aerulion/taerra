package net.aerulion.taerra.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionType;
import com.sk89q.worldguard.util.profile.cache.ProfileCache;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.model.WorldKeys;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WorldGuardRegions {

  private static final long SYNC_INTERVAL_TICKS = 200L;

  private static final @NotNull Color UNCLAIMED = Color.fromRGB(0x52A535);
  private static final @NotNull Color CLAIMED = Color.fromRGB(0xE34A4A);

  private WorldGuardRegions() {
    super();
  }

  public static void start(
      final @NotNull Plugin plugin,
      final @NotNull MarkerRegistry registry,
      final @NotNull TaerraConfig config) {
    final @NotNull MarkerLayer layer =
        registry.layer("worldguard", "Regions", config.worldGuardShownByDefault());
    Bukkit.getScheduler()
        .runTaskTimerAsynchronously(
            plugin, () -> layer.setAll(regions(config)), 20L, SYNC_INTERVAL_TICKS);
  }

  private static @NotNull List<Marker> regions(final @NotNull TaerraConfig config) {
    final @NotNull ProfileCache profiles = WorldGuard.getInstance().getProfileCache();
    final @NotNull List<Marker> markers = new ArrayList<>();
    for (final @NotNull World world : Bukkit.getWorlds()) {
      if (!config.worldEnabled(WorldKeys.id(world))) {
        continue;
      }
      final @Nullable RegionManager manager =
          WorldGuard.getInstance()
              .getPlatform()
              .getRegionContainer()
              .get(BukkitAdapter.adapt(world));
      if (manager == null) {
        continue;
      }
      for (final @NotNull ProtectedRegion region : manager.getRegions().values()) {
        if (region.getType() != RegionType.GLOBAL) {
          markers.add(marker(world, region, profiles));
        }
      }
    }
    return markers;
  }

  private static @NotNull Marker marker(
      final @NotNull World world,
      final @NotNull ProtectedRegion region,
      final @NotNull ProfileCache profiles) {
    final @NotNull BlockVector3 min = region.getMinimumPoint();
    final @NotNull BlockVector3 max = region.getMaximumPoint();
    final @NotNull String id = WorldKeys.id(world) + "/" + region.getId();
    final @NotNull List<Vector> outline =
        region.getType() == RegionType.CUBOID
            ? List.of(
                new Vector(min.x(), 0, min.z()),
                new Vector(max.x() + 1, 0, min.z()),
                new Vector(max.x() + 1, 0, max.z() + 1),
                new Vector(min.x(), 0, max.z() + 1))
            : region.getPoints().stream()
                .map((BlockVector2 point) -> new Vector(point.x() + .5, 0, point.z() + .5))
                .toList();

    return Marker.polygon(id, world, outline, min.y(), max.y() + 1, region.getId())
        .withColor(region.getOwners().size() + region.getMembers().size() > 0 ? CLAIMED : UNCLAIMED)
        .withDetails(details(region, profiles));
  }

  private static @NotNull Map<String, String> details(
      final @NotNull ProtectedRegion region, final @NotNull ProfileCache profiles) {
    final @NotNull Map<String, String> details = new LinkedHashMap<>();
    put(details, "Owners", region.getOwners(), profiles);
    put(details, "Members", region.getMembers(), profiles);
    if (region.getPriority() != 0) {
      details.put("Priority", Integer.toString(region.getPriority()));
    }
    return details;
  }

  private static void put(
      final @NotNull Map<String, String> details,
      final @NotNull String key,
      final @NotNull DefaultDomain domain,
      final @NotNull ProfileCache profiles) {
    if (domain.size() > 0) {
      details.put(key, domain.toUserFriendlyString(profiles));
    }
  }
}
