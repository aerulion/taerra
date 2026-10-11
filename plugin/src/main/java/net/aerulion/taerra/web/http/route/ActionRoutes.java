package net.aerulion.taerra.web.http.route;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.aerulion.taerra.chat.RenderFeedback;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.rendering.RenderService;
import net.aerulion.taerra.web.auth.ModHandshake;
import net.aerulion.taerra.web.auth.Viewer;
import net.aerulion.taerra.web.auth.WebSessions;
import net.aerulion.taerra.web.http.Requests;
import net.aerulion.taerra.world.Teleports;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ActionRoutes implements Routes {

  private static final int MAX_AREA_CHUNKS = 16384;

  private final @NotNull Plugin plugin;
  private final @NotNull Requests requests;
  private final @NotNull TaerraConfig taerraConfig;
  private final @NotNull ModHandshake modHandshake;
  private final @NotNull Teleports teleports;
  private final @NotNull RenderService renders;
  private final @NotNull RenderFeedback renderFeedback;

  public ActionRoutes(
      final @NotNull Plugin plugin,
      final @NotNull Requests requests,
      final @NotNull TaerraConfig taerraConfig,
      final @NotNull ModHandshake modHandshake,
      final @NotNull Teleports teleports,
      final @NotNull RenderService renders,
      final @NotNull RenderFeedback renderFeedback) {
    super();
    this.plugin = plugin;
    this.requests = requests;
    this.taerraConfig = taerraConfig;
    this.modHandshake = modHandshake;
    this.teleports = teleports;
    this.renders = renders;
    this.renderFeedback = renderFeedback;
  }

  @Override
  public void register(final @NotNull RoutesConfig routes) {
    routes.post("/api/teleport", this::handleTeleportRequest);
    routes.post("/api/area", this::handleAreaRequest);
  }

  private void handleTeleportRequest(final @NotNull Context ctx) {
    if (!Requests.sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    final @Nullable UUID uuid = viewer.uuid();
    if (uuid == null) {
      ctx.status(401).result("Not logged in");
      return;
    }
    final @NotNull TeleportRequest request;
    try {
      request = ctx.bodyAsClass(TeleportRequest.class);
    } catch (final Exception e) {
      ctx.status(400).result("Invalid teleport");
      return;
    }
    if (request.world() == null
        || request.x() == null
        || request.z() == null
        || !viewer.canSee(this.taerraConfig, request.world())) {
      ctx.status(400).result("Invalid teleport");
      return;
    }
    final boolean announce = !this.modHandshake.isMod(ctx.cookie(WebSessions.COOKIE));
    ctx.future(
        () ->
            this.teleports
                .teleport(uuid, request.world(), request.x(), request.y(), request.z(), announce)
                .thenAccept(
                    outcome ->
                        ctx.status(
                            switch (outcome) {
                              case TELEPORTED -> 204;
                              case OFFLINE, REFUSED -> 409;
                              case FORBIDDEN -> 403;
                              case OUTSIDE_BORDER -> 400;
                              case FAILED -> 500;
                            })));
  }

  private void handleAreaRequest(final @NotNull Context ctx) {
    if (!Requests.sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    final @NotNull AreaRequest request;
    try {
      request = ctx.bodyAsClass(AreaRequest.class);
    } catch (final Exception e) {
      ctx.status(400).result("Invalid area");
      return;
    }
    final @Nullable Boolean hide =
        "hide".equals(request.action())
            ? Boolean.TRUE
            : "reveal".equals(request.action()) ? Boolean.FALSE : null;
    if ((hide == null && !"render".equals(request.action()))
        || request.world() == null
        || request.chunks() == null
        || request.chunks().isEmpty()
        || request.chunks().size() > MAX_AREA_CHUNKS
        || request.chunks().stream().anyMatch(chunk -> chunk == null || chunk.length != 2)
        || !viewer.canSee(this.taerraConfig, request.world())) {
      ctx.status(400).result("Invalid area");
      return;
    }
    if (hide == null ? !viewer.canRender() : !viewer.canHide()) {
      ctx.status(403).result("Not allowed");
      return;
    }
    final @NotNull Set<Long> chunks =
        request.chunks().stream()
            .map(chunk -> ChunkCoordinate.key(chunk[0], chunk[1]))
            .collect(Collectors.toUnmodifiableSet());
    final @NotNull CompletableFuture<Integer> result = new CompletableFuture<>();
    Bukkit.getScheduler()
        .runTask(
            this.plugin,
            () -> {
              final @Nullable World world = WorldKeys.resolve(request.world());
              if (world == null) {
                result.complete(400);
                return;
              }
              final @Nullable Player player =
                  viewer.uuid() == null ? null : Bukkit.getPlayer(viewer.uuid());
              result.complete(
                  this.renderFeedback.report(
                          player == null ? Audience.empty() : player,
                          WorldKeys.id(world),
                          RenderFeedback.area(chunks.size(), hide),
                          this.renders.area(world, chunks, hide))
                      ? 202
                      : 409);
            });
    ctx.future(() -> result.thenAccept(status -> ctx.status(status)));
  }

  private record AreaRequest(
      @Nullable String action, @Nullable String world, @Nullable List<int[]> chunks) {}

  private record TeleportRequest(
      @Nullable String world, @Nullable Integer x, @Nullable Integer y, @Nullable Integer z) {}
}
