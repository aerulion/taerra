package net.aerulion.taerra.web.http.route;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.util.HexColors;
import net.aerulion.taerra.util.Names;
import net.aerulion.taerra.waypoint.PersonalWaypoints;
import net.aerulion.taerra.waypoint.WaypointEditor;
import net.aerulion.taerra.waypoint.Waypoints;
import net.aerulion.taerra.web.auth.Viewer;
import net.aerulion.taerra.web.http.Requests;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WaypointRoutes implements Routes {

  private static final int MAX_IMPORT = 1000;

  private final @NotNull Plugin plugin;
  private final @NotNull Requests requests;
  private final @NotNull TaerraConfig taerraConfig;
  private final @NotNull Waypoints waypoints;
  private final @NotNull PersonalWaypoints personalWaypoints;
  private final @NotNull WaypointEditor waypointEditor;

  public WaypointRoutes(
      final @NotNull Plugin plugin,
      final @NotNull Requests requests,
      final @NotNull TaerraConfig taerraConfig,
      final @NotNull Waypoints waypoints,
      final @NotNull PersonalWaypoints personalWaypoints,
      final @NotNull WaypointEditor waypointEditor) {
    super();
    this.plugin = plugin;
    this.requests = requests;
    this.taerraConfig = taerraConfig;
    this.waypoints = waypoints;
    this.personalWaypoints = personalWaypoints;
    this.waypointEditor = waypointEditor;
  }

  @Override
  public void register(final @NotNull RoutesConfig routes) {
    routes.get("/api/waypoints/groups", this::handleWaypointGroupsRequest);
    routes.put("/api/waypoints", this::handleWaypointSaveRequest);
    routes.delete("/api/waypoints/{name}", this::handleWaypointDeleteRequest);
    routes.get("/api/waypoints/personal", this::handlePersonalListRequest);
    routes.put("/api/waypoints/personal/hidden", this::handlePersonalHiddenRequest);
    routes.post("/api/waypoints/personal/import", this::handlePersonalImportRequest);
  }

  private static boolean validPoints(final @Nullable List<List<Double>> points, final int fewest) {
    return points != null
        && points.size() >= fewest
        && points.size() <= WaypointTransfer.MAX_SHAPE_POINTS
        && points.stream()
            .allMatch(
                point ->
                    point != null
                        && point.size() == 3
                        && point.get(0) != null
                        && point.get(2) != null
                        && point.stream()
                            .allMatch(value -> value == null || Double.isFinite(value)));
  }

  private void handleWaypointGroupsRequest(final @NotNull Context ctx) {
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    if (!viewer.canEditMarkers()) {
      ctx.status(403).result("Not allowed");
      return;
    }
    ctx.header("Cache-Control", "no-store")
        .json(
            this.waypoints.groups().stream()
                .filter(group -> viewer.canSeeLayer(this.taerraConfig, group.id()))
                .map(
                    group ->
                        Map.of(
                            "id", group.id(),
                            "label", group.label(),
                            "private", this.taerraConfig.layerPrivate(group.id()),
                            "icon",
                                Objects.requireNonNullElse(
                                    this.taerraConfig.layerIcon(group.id()), "")))
                .toList());
  }

  private void handleWaypointSaveRequest(final @NotNull Context ctx) {
    if (!Requests.sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    final @NotNull WaypointRequest request;
    try {
      request = ctx.bodyAsClass(WaypointRequest.class);
    } catch (final Exception e) {
      ctx.status(400).result("Invalid waypoint");
      return;
    }
    final boolean previousPersonal =
        request.previousPersonal() == null ? request.personal() : request.previousPersonal();
    if (request.personal() ? !viewer.canKeepPersonalMarkers() : !viewer.canEditMarkers()) {
      ctx.status(403).result("Not allowed");
      return;
    }
    if (request.previous() != null
        && previousPersonal != request.personal()
        && (previousPersonal ? !viewer.canKeepPersonalMarkers() : !viewer.canEditMarkers())) {
      ctx.status(403).result("Not allowed");
      return;
    }
    final @Nullable String name = Names.of(request.name());
    final boolean grouped =
        !request.personal() && request.group() != null && !request.group().isBlank();
    final @Nullable String group = grouped ? Names.of(request.group()) : null;
    final @NotNull OptionalInt color = HexColors.parseRgb(request.color());
    final boolean iconed = request.icon() != null && !request.icon().isEmpty();
    final @Nullable NamespacedKey icon = iconed ? NamespacedKey.fromString(request.icon()) : null;
    final boolean point = request.type() == null || "point".equals(request.type());
    if (name == null
        || (grouped && group == null)
        || color.isEmpty()
        || (iconed && icon == null)
        || request.world() == null
        || (point
            ? request.x() == null || request.z() == null
            : !validPoints(request.points(), "area".equals(request.type()) ? 3 : 2)
                || !"area".equals(request.type()) && !"line".equals(request.type()))
        || !viewer.canSee(this.taerraConfig, request.world())) {
      ctx.status(400).result("Invalid waypoint");
      return;
    }
    final boolean area = "area".equals(request.type());
    final WaypointEditor.@NotNull Draft draft =
        new WaypointEditor.Draft(
            name,
            color.getAsInt(),
            group,
            request.privateGroup(),
            request.world(),
            point
                ? WaypointEditor.Kind.POINT
                : area ? WaypointEditor.Kind.AREA : WaypointEditor.Kind.LINE,
            point ? request.x() : 0,
            request.y(),
            point ? request.z() : 0,
            request.points(),
            request.smooth(),
            request.description(),
            request.previous(),
            previousPersonal,
            request.personal(),
            point ? icon : null);
    ctx.future(
        () ->
            this.waypointEditor
                .save(draft, author(viewer))
                .thenAccept(
                    saved ->
                        ctx.status(
                            switch (saved) {
                              case SAVED -> 204;
                              case INVALID -> 400;
                              case FORBIDDEN -> 403;
                              case TAKEN -> 409;
                              case FULL -> 422;
                              case FAILED -> 500;
                            })));
  }

  private void handleWaypointDeleteRequest(final @NotNull Context ctx) {
    if (!Requests.sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    final boolean personal = "true".equals(ctx.queryParam("personal"));
    if (personal ? !viewer.canKeepPersonalMarkers() : !viewer.canEditMarkers()) {
      ctx.status(403).result("Not allowed");
      return;
    }
    final @NotNull String name = ctx.pathParam("name");
    ctx.future(
        () ->
            this.waypointEditor
                .delete(name, personal, author(viewer))
                .thenAccept(deleted -> ctx.status(deleted ? 204 : 404)));
  }

  private WaypointEditor.@NotNull Author author(final @NotNull Viewer viewer) {
    return new WaypointEditor.Author(
        viewer.uuid(),
        layer -> viewer.canSeeLayer(this.taerraConfig, layer),
        viewer.canCreatePrivateGroups());
  }

  private void handlePersonalListRequest(final @NotNull Context ctx) {
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    if (!viewer.canKeepPersonalMarkers()) {
      ctx.status(403).result("Not allowed");
      return;
    }
    ctx.header("Cache-Control", "no-store")
        .json(
            this.personalWaypoints.all(viewer.uuid()).stream()
                .map(WaypointTransfer::json)
                .toList());
  }

  private void handlePersonalHiddenRequest(final @NotNull Context ctx) {
    if (!Requests.sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    if (!viewer.canKeepPersonalMarkers()) {
      ctx.status(403).result("Not allowed");
      return;
    }
    final @NotNull HiddenRequest request;
    try {
      request = ctx.bodyAsClass(HiddenRequest.class);
    } catch (final Exception e) {
      ctx.status(400).result("Invalid request");
      return;
    }
    if (request.names() == null
        || request.names().size() > PersonalWaypoints.MAX
        || request.names().stream().anyMatch(Objects::isNull)) {
      ctx.status(400).result("Invalid request");
      return;
    }
    final @NotNull CompletableFuture<Integer> result = new CompletableFuture<>();
    Bukkit.getScheduler()
        .runTask(
            this.plugin,
            () ->
                result.complete(
                    this.personalWaypoints.setHidden(
                        viewer.uuid(), request.names(), request.hidden())));
    ctx.future(
        () ->
            result.thenAccept(
                changed ->
                    ctx.header("Cache-Control", "no-store").json(Map.of("changed", changed))));
  }

  private void handlePersonalImportRequest(final @NotNull Context ctx) {
    if (!Requests.sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    if (!viewer.canKeepPersonalMarkers()) {
      ctx.status(403).result("Not allowed");
      return;
    }
    final @NotNull ImportRequest request;
    try {
      request = ctx.bodyAsClass(ImportRequest.class);
    } catch (final Exception e) {
      ctx.status(400).result("Invalid file");
      return;
    }
    final boolean replace = "replace".equals(request.mode());
    if ((!replace && !"add".equals(request.mode()))
        || request.waypoints() == null
        || request.waypoints().size() > MAX_IMPORT) {
      ctx.status(400).result("Invalid file");
      return;
    }
    final @NotNull CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
    Bukkit.getScheduler()
        .runTask(
            this.plugin,
            () -> {
              final @NotNull List<PersonalWaypoints.Saved> valid = new ArrayList<>();
              int invalid = 0;
              for (final @Nullable Map<String, Object> entry : request.waypoints()) {
                final @Nullable World world =
                    entry == null ? null : WaypointTransfer.resolve(WaypointTransfer.world(entry));
                final PersonalWaypoints.@Nullable Saved saved =
                    world == null || !viewer.canSee(this.taerraConfig, WorldKeys.id(world))
                        ? null
                        : WaypointTransfer.saved(entry, world);
                if (saved == null) {
                  invalid++;
                } else {
                  valid.add(saved);
                }
              }
              final PersonalWaypoints.@Nullable Imported imported =
                  this.personalWaypoints.importAll(viewer.uuid(), valid, replace);
              result.complete(
                  imported == null
                      ? null
                      : Map.of(
                          "added", imported.added(),
                          "taken", imported.taken(),
                          "full", imported.full(),
                          "invalid", invalid));
            });
    ctx.future(
        () ->
            result.thenAccept(
                counts -> {
                  if (counts == null) {
                    ctx.status(500).result("Failed to save");
                  } else {
                    ctx.header("Cache-Control", "no-store").json(counts);
                  }
                }));
  }

  private record HiddenRequest(@Nullable List<String> names, boolean hidden) {}

  private record ImportRequest(
      @Nullable String mode, @Nullable List<Map<String, Object>> waypoints) {}

  private record WaypointRequest(
      @Nullable String name,
      @Nullable String color,
      @Nullable String group,
      boolean privateGroup,
      @Nullable String world,
      @Nullable Integer x,
      @Nullable Integer y,
      @Nullable Integer z,
      @Nullable String previous,
      @Nullable String type,
      @Nullable List<List<Double>> points,
      boolean smooth,
      @Nullable String description,
      boolean personal,
      @Nullable Boolean previousPersonal,
      @Nullable String icon) {}
}
