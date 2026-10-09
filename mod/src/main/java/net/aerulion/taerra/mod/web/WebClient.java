package net.aerulion.taerra.mod.web;

import java.lang.reflect.Type;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.jetbrains.annotations.Nullable;

public sealed interface WebClient permits WebClientImpl {

  String base();

  CompletableFuture<HttpResponse<byte[]>> get(String path, @Nullable String etag);

  <T> void json(String path, Type type, Consumer<@Nullable T> then);

  void send(String method, String path, @Nullable Object body, IntConsumer then);

  void request(
      String method,
      String path,
      @Nullable Object body,
      BiConsumer<Integer, @Nullable String> then);

  CompletableFuture<WebSocket> socket(WebSocket.Listener listener);

  void execute(Runnable task);

  void close();
}
