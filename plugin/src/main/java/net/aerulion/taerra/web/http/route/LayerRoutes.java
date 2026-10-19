package net.aerulion.taerra.web.http.route;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.aerulion.taerra.player.Preferences;
import net.aerulion.taerra.web.auth.Viewer;
import net.aerulion.taerra.web.http.Requests;
import net.aerulion.taerra.web.socket.MapWebSocketHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LayerRoutes implements Routes {

  private final @NotNull Requests requests;
  private final @NotNull MapWebSocketHandler wsHandler;
  private final @NotNull Preferences preferences;

  public LayerRoutes(
      final @NotNull Requests requests,
      final @NotNull MapWebSocketHandler wsHandler,
      final @NotNull Preferences preferences) {
    super();
    this.requests = requests;
    this.wsHandler = wsHandler;
    this.preferences = preferences;
  }

  private static @NotNull List<String> ids(final @Nullable List<String> ids) {
    return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).toList();
  }

  @Override
  public void register(final @NotNull RoutesConfig routes) {
    routes.get("/api/layers", this::handleLayersRequest);
    routes.patch("/api/layers", this::handleLayersChangeRequest);
  }

  private void handleLayersRequest(final @NotNull Context ctx) {
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    ctx.header("Cache-Control", "no-store");
    if (viewer.uuid() == null) {
      ctx.status(204);
      return;
    }
    ctx.json(this.preferences.layers(viewer.uuid()));
  }

  private void handleLayersChangeRequest(final @NotNull Context ctx) {
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    if (!Requests.sameOrigin(ctx) || viewer.uuid() == null) {
      ctx.status(403).result("Not allowed");
      return;
    }
    final @NotNull LayerChange change;
    try {
      change = ctx.bodyAsClass(LayerChange.class);
    } catch (final Exception e) {
      ctx.status(400).result("Invalid layers");
      return;
    }
    final @Nullable Map<String, Boolean> layers =
        this.preferences.changeLayers(
            viewer.uuid(), ids(change.show()), ids(change.hide()), ids(change.reset()));
    if (layers == null) {
      ctx.status(400).result("Invalid layers");
      return;
    }
    this.wsHandler.sendTo(viewer.uuid(), Map.of("type", "layers", "layers", layers));
    ctx.json(layers);
  }

  private record LayerChange(
      @Nullable List<String> show, @Nullable List<String> hide, @Nullable List<String> reset) {}
}
