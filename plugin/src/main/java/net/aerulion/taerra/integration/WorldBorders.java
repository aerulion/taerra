package net.aerulion.taerra.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.aerulion.taerra.api.marker.AreaMarker;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.model.WorldKeys;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.popcraft.chunky.platform.util.Vector2;
import org.popcraft.chunky.shape.AbstractEllipse;
import org.popcraft.chunky.shape.AbstractPolygon;
import org.popcraft.chunky.shape.Shape;
import org.popcraft.chunkyborder.BorderData;
import org.popcraft.chunkyborder.ChunkyBorderProvider;

public final class WorldBorders {

  private static final long SYNC_INTERVAL_TICKS = 20L;
  private static final double MAX_CURVE_ERROR = 0.5;
  private static final int MIN_ELLIPSE_SEGMENTS = 8;

  private static final @NotNull Color COLOR = Color.fromRGB(0x3FA9F5);

  private WorldBorders() {
    super();
  }

  public static void start(
      final @NotNull Plugin plugin,
      final @NotNull MarkerRegistry registry,
      final @NotNull TaerraConfig config) {
    final @NotNull MarkerLayer layer =
        registry.layer("worldborder", "World border", config.worldBordersShownByDefault());
    Bukkit.getScheduler()
        .runTaskTimer(
            plugin, () -> layer.setAll(borders(config).values()), 20L, SYNC_INTERVAL_TICKS);
  }

  private static @NotNull Map<World, AreaMarker> borders(final @NotNull TaerraConfig config) {
    final @NotNull Map<World, AreaMarker> borders = new HashMap<>();
    for (final @NotNull World world : Bukkit.getWorlds()) {
      if (!config.worldEnabled(WorldKeys.id(world))) {
        continue;
      }
      final @NotNull WorldBorder border = world.getWorldBorder();
      if (border.getSize() < border.getMaxSize()) {
        final double half = border.getSize() / 2;
        final @NotNull Location center = border.getCenter();
        keepSmaller(
            borders,
            world,
            Marker.cuboid(
                    WorldKeys.id(world) + "/vanilla",
                    world,
                    new BoundingBox(
                        center.getX() - half,
                        world.getMinHeight(),
                        center.getZ() - half,
                        center.getX() + half,
                        world.getMaxHeight(),
                        center.getZ() + half),
                    "World border")
                .hollowed()
                .withColor(COLOR));
      }
    }
    if (Bukkit.getPluginManager().isPluginEnabled("ChunkyBorder")) {
      Chunky.borders(config, borders);
    }
    return borders;
  }

  private static void keepSmaller(
      final @NotNull Map<World, AreaMarker> borders,
      final @NotNull World world,
      final @NotNull AreaMarker border) {
    borders.merge(world, border, (kept, added) -> area(added) < area(kept) ? added : kept);
  }

  private static double area(final @NotNull AreaMarker marker) {
    final @NotNull List<Vector> outline = marker.outline();
    double sum = 0;
    for (int i = 0; i < outline.size(); i++) {
      final @NotNull Vector a = outline.get(i);
      final @NotNull Vector b = outline.get((i + 1) % outline.size());
      sum += a.getX() * b.getZ() - b.getX() * a.getZ();
    }
    return Math.abs(sum) / 2;
  }

  static int ellipseSegments(final double radius) {
    final double halfAngle = Math.acos(Math.max(-1, 1 - MAX_CURVE_ERROR / radius));
    return Math.max(MIN_ELLIPSE_SEGMENTS, (int) Math.ceil(Math.PI / halfAngle));
  }

  private static final class Chunky {

    private Chunky() {
      super();
    }

    private static void borders(
        final @NotNull TaerraConfig config, final @NotNull Map<World, AreaMarker> borders) {
      for (final @NotNull BorderData data : ChunkyBorderProvider.get().getBorders().values()) {
        final @Nullable World world = Bukkit.getWorld(data.getWorld());
        final @Nullable AreaMarker marker =
            world == null || !config.worldEnabled(WorldKeys.id(world))
                ? null
                : marker(world, data.getBorder());
        if (marker != null) {
          keepSmaller(borders, world, marker);
        }
      }
    }

    private static @Nullable AreaMarker marker(
        final @NotNull World world, final @NotNull Shape shape) {
      final @NotNull List<Vector> outline = new ArrayList<>();
      switch (shape) {
        case final @NotNull AbstractPolygon polygon -> {
          for (final @NotNull Vector2 point : polygon.points()) {
            outline.add(new Vector(point.getX(), 0, point.getZ()));
          }
        }
        case final @NotNull AbstractEllipse ellipse -> {
          final @NotNull Vector2 center = ellipse.center();
          final @NotNull Vector2 radii = ellipse.radii();
          final int segments = ellipseSegments(Math.max(radii.getX(), radii.getZ()));
          for (int i = 0; i < segments; i++) {
            final double angle = 2 * Math.PI * i / segments;
            outline.add(
                new Vector(
                    center.getX() + radii.getX() * Math.cos(angle),
                    0,
                    center.getZ() + radii.getZ() * Math.sin(angle)));
          }
        }
        default -> {
          return null;
        }
      }
      return Marker.polygon(
              WorldKeys.id(world) + "/chunky",
              world,
              outline,
              world.getMinHeight(),
              world.getMaxHeight(),
              "World border")
          .hollowed()
          .withColor(COLOR)
          .withDetails(Map.of("Shape", shape.name()));
    }
  }
}
