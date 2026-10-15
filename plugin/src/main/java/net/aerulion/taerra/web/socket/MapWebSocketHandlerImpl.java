package net.aerulion.taerra.web.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsErrorContext;
import io.javalin.websocket.WsMessageContext;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Level;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.web.auth.Viewer;
import net.aerulion.taerra.web.auth.WebSessions;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MapWebSocketHandlerImpl implements MapWebSocketHandler {

  private static final @NotNull String ACCOUNT_CHANGED = "{\"type\":\"account\"}";

  private final @NotNull Plugin plugin;
  private final @NotNull TaerraConfig config;
  private final @NotNull WebSessions sessions;
  private final @NotNull Map<WsContext, Connection> connections;
  private final @NotNull Map<WsContext, String> subscribedWorlds;
  private final @NotNull Map<WsContext, String> settingsClients = new ConcurrentHashMap<>();
  private final @NotNull ObjectMapper mapper;

  private volatile @Nullable Progress progress;

  public MapWebSocketHandlerImpl(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig config,
      final @NotNull WebSessions sessions) {
    super();
    this.plugin = plugin;
    this.config = config;
    this.sessions = sessions;
    this.connections = new ConcurrentHashMap<>();
    this.subscribedWorlds = new ConcurrentHashMap<>();
    this.mapper = new ObjectMapper();
  }

  private static boolean isDisconnect(final @NotNull Throwable error) {
    for (@Nullable Throwable cause = error; cause != null; cause = cause.getCause()) {
      final @NotNull String name = cause.getClass().getSimpleName();
      if (cause instanceof IOException
          || cause instanceof TimeoutException
          || name.contains("Timeout")
          || name.contains("Closed")
          || name.contains("Eof")) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void configure(final @NotNull WsConfig ws) {
    ws.onConnect(this::onConnect);
    ws.onClose(this::onClose);
    ws.onMessage(this::onMessage);
    ws.onError(this::onError);
  }

  @Override
  public void broadcastToWorld(final @NotNull String worldName, final @NotNull String message) {
    this.subscribedWorlds.forEach(
        (ctx, world) -> {
          if (worldName.equals(world)) {
            send(ctx, message);
          }
        });
  }

  @Override
  public void broadcastToWorld(
      final @NotNull String worldName, final @NotNull Function<Viewer, String> message) {
    this.subscribedWorlds.forEach(
        (ctx, world) -> {
          final @Nullable Connection connection = this.connections.get(ctx);
          final @Nullable String text =
              connection == null || !worldName.equals(world)
                  ? null
                  : message.apply(connection.viewer());
          if (text != null) {
            send(ctx, text);
          }
        });
  }

  @Override
  public void broadcast(final @NotNull String message) {
    this.connections.keySet().forEach(ctx -> send(ctx, message));
  }

  @Override
  public void broadcast(final @NotNull Function<Viewer, String> message) {
    this.connections.forEach(
        (ctx, connection) -> {
          final @Nullable String text = message.apply(connection.viewer());
          if (text != null) {
            send(ctx, text);
          }
        });
  }

  @Override
  public void broadcastToConfigurers(final @NotNull String message) {
    broadcast(viewer -> viewer.canConfigure() ? message : null);
  }

  @Override
  public @NotNull List<Editor> settingsEditors() {
    final @NotNull Map<String, Editor> editors = new LinkedHashMap<>();
    this.settingsClients.forEach(
        (ctx, client) -> {
          final @Nullable Connection connection = this.connections.get(ctx);
          final WebSessions.@Nullable Session session =
              connection == null ? null : this.sessions.session(connection.token());
          if (session != null && connection.viewer().uuid() != null) {
            editors.putIfAbsent(
                session.uuid() + "/" + client, new Editor(session.uuid(), session.name(), client));
          }
        });
    return editors.values().stream()
        .sorted(Comparator.comparing(Editor::name, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  private void publishEditors() {
    try {
      broadcastToConfigurers(
          this.mapper.writeValueAsString(
              Map.of("type", "settings_editors", "editors", settingsEditors())));
    } catch (final Exception e) {
      this.plugin.getLogger().log(Level.FINE, "Failed to send the settings editors", e);
    }
  }

  @Override
  public void refreshMarkers(final @NotNull UUID player) {
    this.connections.forEach(
        (ctx, connection) -> {
          final @Nullable String world = this.subscribedWorlds.get(ctx);
          if (world != null && player.equals(connection.viewer().uuid())) {
            send(ctx, markersMessage(world));
          }
        });
  }

  @Override
  public void sendTo(final @NotNull UUID player, final @NotNull Object message) {
    final @NotNull String json;
    try {
      json = this.mapper.writeValueAsString(message);
    } catch (final Exception e) {
      return;
    }
    this.connections.forEach(
        (ctx, connection) -> {
          if (player.equals(connection.viewer().uuid())) {
            send(ctx, json);
          }
        });
  }

  public static @NotNull String markersMessage(final @NotNull String world) {
    return "{\"type\":\"markers\",\"world\":\"" + world + "\"}";
  }

  @Override
  public void publishProgress(
      final @Nullable String worldName,
      final @NotNull String message,
      final @NotNull String withoutWorld) {
    final @Nullable Progress last = this.progress;
    if (last != null && last.message().equals(message)) {
      return;
    }
    final @NotNull Progress progress = new Progress(worldName, message, withoutWorld);
    this.progress = progress;
    this.connections.forEach((ctx, connection) -> sendProgress(ctx, connection.viewer(), progress));
  }

  @Override
  public int clientCount(final @NotNull String worldName) {
    return (int)
        this.subscribedWorlds.values().stream().filter(world -> world.equals(worldName)).count();
  }

  @Override
  public void revalidate() {
    this.connections.forEach(
        (ctx, connection) -> {
          final @NotNull Viewer viewer =
              connection.token() == null
                  ? Viewer.ANONYMOUS
                  : this.sessions.viewer(connection.token());
          if (!viewer.equals(connection.viewer())) {
            this.connections.put(ctx, new Connection(connection.token(), viewer));
            send(ctx, ACCOUNT_CHANGED);
            if (!viewer.canConfigure() && this.settingsClients.remove(ctx) != null) {
              publishEditors();
            }
          }
          final @Nullable String world = this.subscribedWorlds.get(ctx);
          if (world != null && !viewer.canSee(this.config, world)) {
            this.subscribedWorlds.remove(ctx);
          }
        });
  }

  private void onConnect(final @NotNull WsContext ctx) {
    final @Nullable String token = ctx.cookie(WebSessions.COOKIE);
    final @NotNull Viewer viewer = this.sessions.viewer(token);
    this.connections.put(ctx, new Connection(viewer.uuid() == null ? null : token, viewer));
    final @Nullable Progress current = this.progress;
    if (current != null) {
      sendProgress(ctx, viewer, current);
    }
  }

  private void onClose(final @NotNull WsContext ctx) {
    this.connections.remove(ctx);
    this.subscribedWorlds.remove(ctx);
    if (this.settingsClients.remove(ctx) != null) {
      publishEditors();
    }
  }

  private void onMessage(final @NotNull WsMessageContext ctx) {
    try {
      final @NotNull String message = ctx.message();
      @SuppressWarnings("unchecked")
      final Map<String, Object> data = this.mapper.readValue(message, Map.class);

      final Object type = data.get("type");

      if ("subscribe".equals(type)) {
        final @Nullable Connection connection = this.connections.get(ctx);
        if (data.get("world") instanceof final @NotNull String world
            && connection != null
            && connection.viewer().canSee(this.config, world)) {
          this.subscribedWorlds.put(ctx, world);
        } else {
          this.subscribedWorlds.remove(ctx);
        }
      } else if ("settings".equals(type)) {
        final @Nullable Connection connection = this.connections.get(ctx);
        final boolean open =
            Boolean.TRUE.equals(data.get("open"))
                && connection != null
                && connection.viewer().canConfigure();
        final @NotNull String client = "mod".equals(data.get("client")) ? "mod" : "web";
        final boolean changed =
            open
                ? !client.equals(this.settingsClients.put(ctx, client))
                : this.settingsClients.remove(ctx) != null;
        if (changed) {
          publishEditors();
        }
      } else if ("ping".equals(type)) {
        send(ctx, "{\"type\":\"pong\"}");
      }
    } catch (final Exception ignored) {
    }
  }

  private void sendProgress(
      final @NotNull WsContext ctx,
      final @NotNull Viewer viewer,
      final @NotNull Progress progress) {
    send(
        ctx,
        progress.world() == null || viewer.canSee(this.config, progress.world())
            ? progress.message()
            : progress.withoutWorld());
  }

  private void onError(final @NotNull WsErrorContext ctx) {
    final @Nullable Throwable error = ctx.error();
    if (error == null || isDisconnect(error)) {
      return;
    }
    this.plugin.getLogger().log(Level.FINE, "WebSocket error", error);
  }

  private void send(final @NotNull WsContext ctx, final @NotNull String message) {
    try {
      ctx.send(message);
    } catch (final Exception ignored) {
    }
  }

  private record Connection(@Nullable String token, @NotNull Viewer viewer) {}

  private record Progress(
      @Nullable String world, @NotNull String message, @NotNull String withoutWorld) {}
}
