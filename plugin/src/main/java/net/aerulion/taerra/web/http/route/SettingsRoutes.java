package net.aerulion.taerra.web.http.route;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.aerulion.taerra.config.ConfigEditor;
import net.aerulion.taerra.web.auth.Viewer;
import net.aerulion.taerra.web.auth.WebSessions;
import net.aerulion.taerra.web.http.Requests;
import net.aerulion.taerra.web.socket.MapWebSocketHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SettingsRoutes implements Routes {

  private final @NotNull Requests requests;
  private final @NotNull ConfigEditor editor;
  private final @NotNull MapWebSocketHandler wsHandler;

  public SettingsRoutes(
      final @NotNull Requests requests,
      final @NotNull ConfigEditor editor,
      final @NotNull MapWebSocketHandler wsHandler) {
    super();
    this.requests = requests;
    this.editor = editor;
    this.wsHandler = wsHandler;
  }

  @Override
  public void register(final @NotNull RoutesConfig routes) {
    routes.get("/api/settings", this::handleSettingsRequest);
    routes.put("/api/settings", this::handleSettingsSaveRequest);
  }

  private @NotNull Map<String, Object> payload(final ConfigEditor.@NotNull View view) {
    final @NotNull Map<String, Object> json = new LinkedHashMap<>();
    json.put("revision", view.revision());
    json.put("settings", view.settings());
    json.put("worlds", view.worlds());
    json.put("layers", view.layers());
    json.put("values", view.values());
    json.put("authors", view.authors());
    json.put("editors", this.wsHandler.settingsEditors());
    return json;
  }

  private @Nullable Viewer configurer(final @NotNull Context ctx) {
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    if (viewer.uuid() == null) {
      ctx.status(401).result("Not logged in");
      return null;
    }
    if (!viewer.canConfigure()) {
      ctx.status(403).result("Not allowed");
      return null;
    }
    return viewer;
  }

  private void handleSettingsRequest(final @NotNull Context ctx) {
    ctx.header("Cache-Control", "no-store");
    if (configurer(ctx) == null) {
      return;
    }
    ctx.future(() -> this.editor.view().thenAccept(view -> ctx.json(payload(view))));
  }

  private void handleSettingsSaveRequest(final @NotNull Context ctx) {
    ctx.header("Cache-Control", "no-store");
    if (!Requests.sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    final @Nullable Viewer viewer = configurer(ctx);
    if (viewer == null) {
      return;
    }
    final @NotNull SaveRequest request;
    try {
      request = ctx.bodyAsClass(SaveRequest.class);
    } catch (final Exception e) {
      ctx.status(400).result("Invalid settings");
      return;
    }
    if (request.base() == null || request.changes() == null) {
      ctx.status(400).result("Invalid settings");
      return;
    }
    final WebSessions.@Nullable Session session = this.requests.session(ctx);
    final @NotNull String who =
        session != null ? session.name() : Objects.requireNonNull(viewer.uuid()).toString();
    ctx.future(
        () ->
            this.editor
                .apply(request.base(), request.changes(), who)
                .thenAccept(
                    outcome -> {
                      switch (outcome) {
                        case ConfigEditor.Applied(final ConfigEditor.View view) ->
                            ctx.json(payload(view));
                        case ConfigEditor.Conflicted(
                                final var conflicts,
                                final ConfigEditor.View view) -> {
                          final @NotNull Map<String, Object> json = payload(view);
                          json.put("conflicts", conflicts);
                          ctx.status(409).json(json);
                        }
                        case ConfigEditor.Invalid(final String field) ->
                            ctx.status(400).json(Map.of("invalid", field));
                        case ConfigEditor.Unreachable(final String field, final String reason) ->
                            ctx.status(400).json(Map.of("invalid", field, "unreachable", reason));
                        case ConfigEditor.Busy(
                                final String field,
                                final ConfigEditor.View view) -> {
                          final @NotNull Map<String, Object> json = payload(view);
                          json.put("busy", field);
                          ctx.status(409).json(json);
                        }
                      }
                    }));
  }

  private record SaveRequest(@Nullable Long base, @Nullable Map<String, Object> changes) {}
}
