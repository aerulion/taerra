package net.aerulion.taerra.web.socket;

import io.javalin.websocket.WsConfig;
import java.util.function.Function;
import net.aerulion.taerra.web.auth.Viewer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface MapWebSocketHandler permits MapWebSocketHandlerImpl {

  void configure(@NotNull WsConfig ws);

  void broadcastToWorld(@NotNull String worldName, @NotNull String message);

  void broadcast(@NotNull String message);

  void broadcast(@NotNull Function<Viewer, String> message);

  void publishProgress(
      @Nullable String worldName, @NotNull String message, @NotNull String withoutWorld);

  int clientCount(@NotNull String worldName);

  void revalidate();
}
