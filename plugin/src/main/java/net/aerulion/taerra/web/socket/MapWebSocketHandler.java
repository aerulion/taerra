package net.aerulion.taerra.web.socket;

import io.javalin.websocket.WsConfig;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.aerulion.taerra.web.auth.Viewer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface MapWebSocketHandler permits MapWebSocketHandlerImpl {

  void configure(@NotNull WsConfig ws);

  void broadcastToWorld(@NotNull String worldName, @NotNull String message);

  void broadcast(@NotNull String message);

  void broadcast(@NotNull Function<Viewer, String> message);

  void refreshMarkers(@NotNull UUID player);

  void sendTo(@NotNull UUID player, @NotNull Object message);

  void publishProgress(
      @Nullable String worldName, @NotNull String message, @NotNull String withoutWorld);

  int clientCount(@NotNull String worldName);

  void revalidate();

  void broadcastToConfigurers(@NotNull String message);

  @NotNull
  List<Editor> settingsEditors();

  record Editor(@NotNull UUID uuid, @NotNull String name, @NotNull String client) {}
}
