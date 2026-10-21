package net.aerulion.taerra.integration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.model.WorldKeys;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GriefPreventionClaims {

  private static final long SYNC_INTERVAL_TICKS = 200L;

  private static final @NotNull Color PLAYER = Color.fromRGB(0xE3A33A);
  private static final @NotNull Color ADMIN = Color.fromRGB(0xE34A4A);

  private GriefPreventionClaims() {
    super();
  }

  public static @NotNull BukkitTask start(
      final @NotNull Plugin plugin,
      final @NotNull MarkerRegistry registry,
      final @NotNull TaerraConfig config,
      final boolean shownByDefault) {
    final @NotNull MarkerLayer layer =
        registry.layer(plugin, "griefprevention", "GriefPrevention Claims", shownByDefault);
    return Bukkit.getScheduler()
        .runTaskTimer(plugin, () -> layer.setAll(claims(config)), 20L, SYNC_INTERVAL_TICKS);
  }

  private static @NotNull List<Marker> claims(final @NotNull TaerraConfig config) {
    final @NotNull List<Marker> markers = new ArrayList<>();
    for (final @NotNull Claim claim : GriefPrevention.instance.dataStore.getClaims()) {
      final @NotNull Location lesser = claim.getLesserBoundaryCorner();
      final @NotNull Location greater = claim.getGreaterBoundaryCorner();
      final @Nullable World world = lesser.getWorld();
      if (world == null || !config.worldEnabled(WorldKeys.id(world))) {
        continue;
      }
      final @NotNull BoundingBox box =
          new BoundingBox(
              lesser.getBlockX(),
              lesser.getBlockY(),
              lesser.getBlockZ(),
              greater.getBlockX() + 1,
              world.getMaxHeight(),
              greater.getBlockZ() + 1);
      markers.add(
          Marker.cuboid(
                  WorldKeys.id(world) + "/" + claim.getID(),
                  world.getKey(),
                  box,
                  claim.isAdminClaim() ? "Administrator" : claim.getOwnerName())
              .withColor(claim.isAdminClaim() ? ADMIN : PLAYER)
              .withDetails(details(claim)));
    }
    return markers;
  }

  private static @NotNull Map<String, String> details(final @NotNull Claim claim) {
    final @NotNull ArrayList<String> builders = new ArrayList<>();
    final @NotNull ArrayList<String> containers = new ArrayList<>();
    final @NotNull ArrayList<String> accessors = new ArrayList<>();
    final @NotNull ArrayList<String> managers = new ArrayList<>();
    claim.getPermissions(builders, containers, accessors, managers);
    final @NotNull String trusted =
        Stream.of(managers, builders, containers, accessors)
            .flatMap(List::stream)
            .map(GriefPreventionClaims::name)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.joining(", "));
    final @NotNull Map<String, String> details = new LinkedHashMap<>();
    if (!trusted.isEmpty()) {
      details.put("trusted", trusted);
    }
    details.put("size", claim.getWidth() + " × " + claim.getHeight());
    return details;
  }

  private static @Nullable String name(final @NotNull String trusted) {
    try {
      return Bukkit.getOfflinePlayer(UUID.fromString(trusted)).getName();
    } catch (final IllegalArgumentException _) {
      return null;
    }
  }
}
