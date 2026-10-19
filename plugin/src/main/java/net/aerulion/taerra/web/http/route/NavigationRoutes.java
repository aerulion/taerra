package net.aerulion.taerra.web.http.route;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.player.Navigation;
import net.aerulion.taerra.player.Preferences;
import net.aerulion.taerra.util.HexColors;
import net.aerulion.taerra.util.Names;
import net.aerulion.taerra.web.auth.Viewer;
import net.aerulion.taerra.web.http.Requests;
import net.aerulion.taerra.web.socket.MapWebSocketHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NavigationRoutes implements Routes {

  private final @NotNull Requests requests;
  private final @NotNull TaerraConfig taerraConfig;
  private final @NotNull MapWebSocketHandler wsHandler;
  private final @NotNull Preferences preferences;

  public NavigationRoutes(
      final @NotNull Requests requests,
      final @NotNull TaerraConfig taerraConfig,
      final @NotNull MapWebSocketHandler wsHandler,
      final @NotNull Preferences preferences) {
    super();
    this.requests = requests;
    this.taerraConfig = taerraConfig;
    this.wsHandler = wsHandler;
    this.preferences = preferences;
  }

  @Override
  public void register(final @NotNull RoutesConfig routes) {
    routes.get("/api/navigation", this::handleNavigationRequest);
    routes.put("/api/navigation", this::handleNavigationSaveRequest);
    routes.delete("/api/navigation", this::handleNavigationDeleteRequest);
  }

  private static boolean isUuid(final @NotNull String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (final IllegalArgumentException e) {
      return false;
    }
  }

  private void handleNavigationRequest(final @NotNull Context ctx) {
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    final @Nullable Navigation target =
        viewer.uuid() == null ? null : this.preferences.navigation(viewer.uuid());
    ctx.header("Cache-Control", "no-store");
    if (target == null || !viewer.canSee(this.taerraConfig, target.world())) {
      ctx.status(204);
      return;
    }
    ctx.json(target);
  }

  private void handleNavigationSaveRequest(final @NotNull Context ctx) {
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    if (!Requests.sameOrigin(ctx) || viewer.uuid() == null) {
      ctx.status(403).result("Not allowed");
      return;
    }
    final @NotNull NavigationTarget request;
    try {
      request = ctx.bodyAsClass(NavigationTarget.class);
    } catch (final Exception e) {
      ctx.status(400).result("Invalid target");
      return;
    }
    final @NotNull String cleaned =
        request.label() == null
            ? ""
            : request.label().replaceAll("[^" + Names.CHARACTERS + "]", "").strip();
    final @Nullable String label =
        Names.of(cleaned.substring(0, Math.min(cleaned.length(), Names.MAX_LENGTH)));
    final @NotNull OptionalInt color = HexColors.parseRgb(request.color());
    if (label == null
        || color.isEmpty()
        || request.world() == null
        || !viewer.canSee(this.taerraConfig, request.world())
        || !Double.isFinite(request.x())
        || !Double.isFinite(request.z())
        || (request.y() != null && !Double.isFinite(request.y()))
        || (request.player() != null && !isUuid(request.player()))) {
      ctx.status(400).result("Invalid target");
      return;
    }
    final @NotNull Navigation target =
        new Navigation(
            label,
            request.world(),
            request.x(),
            request.y(),
            request.z(),
            HexColors.format(color.getAsInt()),
            request.player());
    this.preferences.navigate(viewer.uuid(), target);
    publishNavigation(viewer.uuid(), target);
    ctx.status(204);
  }

  private void handleNavigationDeleteRequest(final @NotNull Context ctx) {
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    if (!Requests.sameOrigin(ctx) || viewer.uuid() == null) {
      ctx.status(403).result("Not allowed");
      return;
    }
    this.preferences.navigate(viewer.uuid(), null);
    publishNavigation(viewer.uuid(), null);
    ctx.status(204);
  }

  private void publishNavigation(final @NotNull UUID player, final @Nullable Navigation target) {
    final @NotNull Map<String, Object> message = new HashMap<>();
    message.put("type", "navigation");
    message.put("target", target);
    this.wsHandler.sendTo(player, message);
  }

  private record NavigationTarget(
      @Nullable String label,
      @Nullable String world,
      double x,
      @Nullable Double y,
      double z,
      @Nullable String color,
      @Nullable String player) {}
}
