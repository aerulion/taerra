package net.aerulion.taerra.world;

import java.util.List;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.model.WorldKeys;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public final class SpawnMarkers {

  public static final @NotNull String LAYER = "spawn";

  private static final long SYNC_INTERVAL_TICKS = 20L;
  private static final @NotNull Color COLOR = Color.fromRGB(0xFFCF3F);

  private SpawnMarkers() {
    super();
  }

  public static @NotNull BukkitTask start(
      final @NotNull Plugin plugin,
      final @NotNull MarkerRegistry registry,
      final @NotNull TaerraConfig config,
      final boolean shownByDefault) {
    final @NotNull MarkerLayer layer = registry.layer(plugin, LAYER, "Spawn", shownByDefault);
    return Bukkit.getScheduler()
        .runTaskTimer(plugin, () -> layer.setAll(spawns(config)), 0L, SYNC_INTERVAL_TICKS);
  }

  private static @NotNull List<Marker> spawns(final @NotNull TaerraConfig config) {
    return Bukkit.getWorlds().stream()
        .filter(world -> config.worldEnabled(WorldKeys.id(world)))
        .map(
            world -> {
              final @NotNull Location spawn = world.getSpawnLocation();
              return (Marker)
                  Marker.point(
                          WorldKeys.id(world),
                          world.getKey(),
                          new Vector(
                              spawn.getBlockX() + .5, spawn.getBlockY(), spawn.getBlockZ() + .5),
                          "Spawn")
                      .withColor(COLOR);
            })
        .toList();
  }
}
