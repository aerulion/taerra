package net.aerulion.taerra.web.http.route;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.aerulion.taerra.chat.RenderFeedback;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.region.MapRegion;
import net.aerulion.taerra.region.RegionEditor;
import net.aerulion.taerra.region.RegionFog;
import net.aerulion.taerra.region.RegionVisibility;
import net.aerulion.taerra.rendering.RenderService;
import net.aerulion.taerra.util.HexColors;
import net.aerulion.taerra.web.auth.Viewer;
import net.aerulion.taerra.web.http.Requests;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RegionRoutes implements Routes {

  private final @NotNull Requests requests;
  private final @NotNull TaerraConfig taerraConfig;
  private final @NotNull RegionVisibility visibility;
  private final @NotNull RegionEditor editor;
  private final @NotNull RenderFeedback renderFeedback;

  public RegionRoutes(
      final @NotNull Requests requests,
      final @NotNull TaerraConfig taerraConfig,
      final @NotNull RegionVisibility visibility,
      final @NotNull RegionEditor editor,
      final @NotNull RenderFeedback renderFeedback) {
    super();
    this.requests = requests;
    this.taerraConfig = taerraConfig;
    this.visibility = visibility;
    this.editor = editor;
    this.renderFeedback = renderFeedback;
  }

  static int @NotNull [] label(final @NotNull MapRegion region) {
    double sumX = 0;
    double sumZ = 0;
    for (final long chunk : region.chunks()) {
      sumX += ChunkCoordinate.keyX(chunk);
      sumZ += ChunkCoordinate.keyZ(chunk);
    }
    final double centerX = sumX / region.chunks().size();
    final double centerZ = sumZ / region.chunks().size();
    long best = 0;
    double bestDistance = Double.MAX_VALUE;
    for (final long chunk : region.chunks()) {
      final double distance =
          Math.pow(ChunkCoordinate.keyX(chunk) - centerX, 2)
              + Math.pow(ChunkCoordinate.keyZ(chunk) - centerZ, 2);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = chunk;
      }
    }
    return new int[] {(ChunkCoordinate.keyX(best) << 4) + 8, (ChunkCoordinate.keyZ(best) << 4) + 8};
  }

  @Override
  public void register(final @NotNull RoutesConfig routes) {
    routes.get("/api/regions/{world}", this::handleListRequest);
    routes.post("/api/regions", this::handleSaveRequest);
    routes.delete("/api/regions/{id}", this::handleDeleteRequest);
    routes.post("/api/regions/{id}/reset", this::handleResetRequest);
  }

  private void handleListRequest(final @NotNull Context ctx) {
    final @NotNull String worldName = ctx.pathParam("world");
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    if (!viewer.canSee(this.taerraConfig, worldName)) {
      ctx.status(404).result("Not found");
      return;
    }
    final boolean editing = viewer.canEditRegions();
    final @NotNull List<String> revealed = new ArrayList<>();
    final @NotNull List<Map<String, Object>> regions = new ArrayList<>();
    for (final @NotNull MapRegion region : this.visibility.regions().in(worldName)) {
      final boolean shown =
          this.visibility.revealed(viewer.uuid(), viewer.canSeeThroughFog(), region);
      if (shown && region.fog().fogged()) {
        revealed.add(region.id().toString());
      }
      if (!editing && (!shown || region.name().isEmpty())) {
        continue;
      }
      final @NotNull Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("id", region.id().toString());
      entry.put("name", region.name());
      entry.put("label", label(region));
      entry.put("color", HexColors.format(region.color()));
      if (editing) {
        entry.put("size", region.chunks().size());
        entry.put("fog", region.fog().id());
        entry.put("discoverable", region.discoverable());
        entry.put(
            "chunks",
            region
                .chunks()
                .longStream()
                .mapToObj(
                    chunk -> new int[] {ChunkCoordinate.keyX(chunk), ChunkCoordinate.keyZ(chunk)})
                .toList());
      }
      regions.add(entry);
    }
    ctx.header("Cache-Control", "no-cache")
        .json(
            Map.of(
                "revealed",
                revealed,
                "regions",
                regions,
                "editable",
                editing,
                "layered",
                this.visibility.regions().anyFogged(worldName)));
  }

  private boolean mayEdit(final @NotNull Context ctx) {
    if (!this.requests.viewer(ctx).canEditRegions()) {
      ctx.status(403).result("Not allowed");
      return false;
    }
    return true;
  }

  private void handleSaveRequest(final @NotNull Context ctx) {
    if (!mayEdit(ctx)) {
      return;
    }
    final @NotNull SaveRequest request;
    try {
      request = ctx.bodyAsClass(SaveRequest.class);
    } catch (final Exception e) {
      ctx.status(400).result("Invalid region");
      return;
    }
    final @Nullable RegionFog fog = RegionFog.parse(request.fog());
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    if (request.world() == null
        || fog == null
        || request.chunks() == null
        || request.chunks().size() > RegionEditor.MAX_CHUNKS
        || request.chunks().stream().anyMatch(chunk -> chunk == null || chunk.length != 2)
        || !viewer.canSee(this.taerraConfig, request.world())) {
      ctx.status(400).result("Invalid region");
      return;
    }
    final @Nullable UUID id = request.id() == null ? null : uuid(request.id());
    if (request.id() != null && id == null) {
      ctx.status(400).result("Invalid region");
      return;
    }
    final @NotNull String name = request.name() == null ? "" : request.name();
    respond(
        ctx,
        viewer,
        this.editor.save(
            request.world(),
            id,
            name,
            HexColors.parseRgb(request.color()).stream().boxed().findFirst().orElse(null),
            fog,
            Boolean.TRUE.equals(request.discoverable()),
            request.chunks().stream()
                .map(chunk -> ChunkCoordinate.key(chunk[0], chunk[1]))
                .toList()));
  }

  private void handleDeleteRequest(final @NotNull Context ctx) {
    if (!mayEdit(ctx)) {
      return;
    }
    final @Nullable UUID id = id(ctx);
    if (id != null) {
      respond(ctx, this.requests.viewer(ctx), this.editor.delete(id));
    }
  }

  private void handleResetRequest(final @NotNull Context ctx) {
    if (!mayEdit(ctx)) {
      return;
    }
    if (!this.requests.viewer(ctx).canResetRegions()) {
      ctx.status(403).result("Not allowed");
      return;
    }
    final @Nullable UUID id = id(ctx);
    if (id != null) {
      ctx.status(this.editor.resetDiscoveries(id) ? 204 : 404);
    }
  }

  private static @Nullable UUID id(final @NotNull Context ctx) {
    final @Nullable UUID id = uuid(ctx.pathParam("id"));
    if (id == null) {
      ctx.status(400).result("Invalid region");
    }
    return id;
  }

  private static @Nullable UUID uuid(final @NotNull String text) {
    try {
      final @NotNull UUID id = UUID.fromString(text);
      return id.toString().equals(text) ? id : null;
    } catch (final IllegalArgumentException e) {
      return null;
    }
  }

  private void respond(
      final @NotNull Context ctx,
      final @NotNull Viewer viewer,
      final @NotNull CompletableFuture<RegionEditor.Result> result) {
    ctx.future(
        () ->
            result.thenAccept(
                outcome -> {
                  switch (outcome) {
                    case RegionEditor.Saved(
                            final MapRegion region,
                            final RenderService.Start start) -> {
                      if (start != null) {
                        final @Nullable UUID uuid = viewer.uuid();
                        final @Nullable Player player =
                            uuid == null ? null : Bukkit.getPlayer(uuid);
                        this.renderFeedback.report(
                            player == null ? Audience.empty() : player,
                            region.world(),
                            RenderFeedback.region(region.name()),
                            start);
                      }
                      ctx.status(start == null ? 200 : 202)
                          .json(Map.of("id", region.id().toString()));
                    }
                    case RegionEditor.Problem problem ->
                        ctx.status(
                                switch (problem) {
                                  case NOT_FOUND -> 404;
                                  case BUSY, OVERLAP, NAME_TAKEN -> 409;
                                  default -> 400;
                                })
                            .json(Map.of("problem", problem.name().toLowerCase(Locale.ROOT)));
                  }
                }));
  }

  private record SaveRequest(
      @Nullable String world,
      @Nullable String id,
      @Nullable String name,
      @Nullable String color,
      @Nullable String fog,
      @Nullable Boolean discoverable,
      @Nullable List<int[]> chunks) {}
}
