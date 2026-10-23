package net.aerulion.taerra.waypoint;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.aerulion.taerra.api.marker.LineMarker;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.player.Preferences;
import net.aerulion.taerra.util.Names;
import net.aerulion.taerra.world.WorldPositions;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WaypointEditor {

  private final @NotNull Plugin plugin;
  private final @NotNull TaerraConfig config;
  private final @NotNull Waypoints waypoints;
  private final @NotNull PersonalWaypoints personal;
  private final @NotNull Preferences preferences;

  public WaypointEditor(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig config,
      final @NotNull Waypoints waypoints,
      final @NotNull PersonalWaypoints personal,
      final @NotNull Preferences preferences) {
    super();
    this.plugin = plugin;
    this.config = config;
    this.waypoints = waypoints;
    this.personal = personal;
    this.preferences = preferences;
  }

  private static @Nullable CompletableFuture<Marker> shape(
      final @NotNull World world, final @NotNull Draft draft) {
    final @NotNull String id =
        draft.personal()
            ? PersonalWaypoints.markerId(draft.name())
            : Waypoints.markerId(draft.name());
    final @NotNull NamespacedKey key = world.getKey();
    final @Nullable List<List<Double>> points = draft.points();
    if (draft.kind() == Kind.POINT || points == null) {
      return WorldPositions.insideBorder(world, draft.x(), draft.z())
          ? WorldPositions.standingY(world, draft.x(), draft.y(), draft.z())
              .thenApply(
                  y ->
                      Marker.point(
                              id, key, new Vector(draft.x() + .5, y, draft.z() + .5), draft.name())
                          .withIcon(draft.icon()))
          : null;
    }
    if (points.stream()
        .anyMatch(point -> !WorldPositions.nearBorder(world, point.get(0), point.get(2)))) {
      return null;
    }
    if (draft.kind() == Kind.AREA) {
      final @NotNull List<Vector> outline =
          Waypoints.outline(
              points.stream().map(point -> new Vector(point.get(0), 0, point.get(2))).toList());
      return outline.size() < 3
          ? null
          : CompletableFuture.completedFuture(
              Marker.polygon(
                  id, key, outline, world.getMinHeight(), world.getMaxHeight(), draft.name()));
    }
    final @NotNull List<CompletableFuture<Vector>> placed =
        points.stream()
            .map(
                point ->
                    WorldPositions.standingY(
                            world,
                            (int) Math.floor(point.get(0)),
                            point.get(1) == null ? null : (int) Math.floor(point.get(1)),
                            (int) Math.floor(point.get(2)))
                        .thenApply(y -> new Vector(point.get(0), y, point.get(2))))
            .toList();
    return CompletableFuture.allOf(placed.toArray(CompletableFuture[]::new))
        .thenApply(
            _ -> {
              final @NotNull LineMarker line =
                  Marker.line(
                      id, key, placed.stream().map(CompletableFuture::join).toList(), draft.name());
              return draft.smooth() ? line.smoothed() : line;
            });
  }

  public @NotNull CompletableFuture<Result> save(
      final @NotNull Draft draft, final @NotNull Author author) {
    final @Nullable String groupId = Waypoints.groupLayerId(draft.group());
    if (!author
        .layers()
        .test(
            draft.personal()
                ? PersonalWaypoints.LAYER
                : groupId == null ? Waypoints.LAYER : groupId)) {
      return CompletableFuture.completedFuture(Result.FORBIDDEN);
    }
    final @NotNull CompletableFuture<Result> result = new CompletableFuture<>();
    this.plugin
        .getServer()
        .getScheduler()
        .runTask(
            this.plugin,
            () -> {
              final @Nullable World world = WorldKeys.resolve(draft.world());
              final @Nullable CompletableFuture<Marker> shaped =
                  world == null ? null : shape(world, draft);
              if (shaped == null) {
                result.complete(Result.INVALID);
                return;
              }
              shaped
                  .thenAccept(
                      marker -> {
                        final @NotNull Result stored =
                            store(
                                marker
                                    .withColor(Color.fromRGB(draft.rgb()))
                                    .withDescription(Names.description(draft.description())),
                                draft,
                                groupId,
                                author);
                        if (stored == Result.SAVED && author.uuid() != null) {
                          this.preferences.rememberWaypointColor(author.uuid(), draft.rgb());
                        }
                        result.complete(stored);
                      })
                  .exceptionally(
                      error -> {
                        result.complete(Result.FAILED);
                        return null;
                      });
            });
    return result;
  }

  public @NotNull CompletableFuture<Boolean> delete(
      final @NotNull String name, final boolean personal, final @NotNull Author author) {
    final @NotNull CompletableFuture<Boolean> result = new CompletableFuture<>();
    this.plugin
        .getServer()
        .getScheduler()
        .runTask(
            this.plugin,
            () ->
                result.complete(
                    personal
                        ? this.personal.remove(author.uuid(), name)
                        : mayEdit(author, name) && this.waypoints.remove(name)));
    return result;
  }

  @NotNull
  Result store(
      final @NotNull Marker marker,
      final @NotNull Draft draft,
      final @Nullable String groupId,
      final @NotNull Author author) {
    final boolean crossing =
        draft.previous() != null && draft.previousPersonal() != draft.personal();
    if (draft.personal()) {
      if (crossing && !mayEdit(author, draft.previous())) {
        return Result.FORBIDDEN;
      }
      final PersonalWaypoints.@NotNull Result saved =
          this.personal.save(author.uuid(), marker, crossing ? null : draft.previous());
      if (saved == PersonalWaypoints.Result.SAVED && crossing) {
        this.waypoints.remove(draft.previous());
      }
      return switch (saved) {
        case SAVED -> Result.SAVED;
        case TAKEN -> Result.TAKEN;
        case FULL -> Result.FULL;
        case FAILED -> Result.FAILED;
      };
    }
    if (!mayEdit(author, crossing ? null : draft.previous())) {
      return Result.FORBIDDEN;
    }
    final boolean renamed =
        crossing || draft.previous() == null || !draft.previous().equalsIgnoreCase(draft.name());
    if (renamed && this.waypoints.names().stream().anyMatch(draft.name()::equalsIgnoreCase)) {
      return Result.TAKEN;
    }
    if (groupId != null
        && draft.privateGroup()
        && author.privateGroups()
        && this.waypoints.groups().stream().noneMatch(known -> known.id().equals(groupId))) {
      this.config.setLayerPrivate(this.plugin, groupId, true);
    }
    this.waypoints.add(marker, draft.group());
    if (crossing) {
      this.personal.remove(author.uuid(), draft.previous());
    } else if (renamed && draft.previous() != null) {
      this.waypoints.remove(draft.previous());
    }
    return Result.SAVED;
  }

  private boolean mayEdit(final @NotNull Author author, final @Nullable String name) {
    final @Nullable String layer = name == null ? null : this.waypoints.layerOf(name);
    return layer == null || author.layers().test(layer);
  }

  public enum Kind {
    POINT,
    LINE,
    AREA
  }

  public enum Result {
    SAVED,
    INVALID,
    FORBIDDEN,
    TAKEN,
    FULL,
    FAILED
  }

  public record Author(
      @Nullable UUID uuid, @NotNull Predicate<String> layers, boolean privateGroups) {}

  public record Draft(
      @NotNull String name,
      int rgb,
      @Nullable String group,
      boolean privateGroup,
      @NotNull String world,
      @NotNull Kind kind,
      int x,
      @Nullable Integer y,
      int z,
      @Nullable List<List<Double>> points,
      boolean smooth,
      @Nullable String description,
      @Nullable String previous,
      boolean previousPersonal,
      boolean personal,
      @Nullable NamespacedKey icon) {}
}
