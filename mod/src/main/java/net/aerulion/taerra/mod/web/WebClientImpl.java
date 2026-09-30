package net.aerulion.taerra.mod.web;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class WebClientImpl implements WebClient {

  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Gson GSON = new Gson();
  private static final Duration TIMEOUT = Duration.ofSeconds(15);
  private static final String COOKIE = "taerra_session=";
  private static final HttpResponse.BodyHandler<byte[]> BODY =
      info ->
          "gzip".equalsIgnoreCase(info.headers().firstValue("Content-Encoding").orElse(""))
              ? HttpResponse.BodySubscribers.mapping(
                  HttpResponse.BodySubscribers.ofByteArray(), WebClientImpl::gunzip)
              : HttpResponse.BodySubscribers.ofByteArray();

  private final String base;
  private final @Nullable String token;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private boolean closed;

  public WebClientImpl(final String base, final @Nullable String token) {
    this.base = base;
    this.token = token;
  }

  static byte[] gunzip(final byte @NotNull [] body) {
    try (final @NotNull GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
      return in.readAllBytes();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public String base() {
    return this.base;
  }

  @Override
  public CompletableFuture<HttpResponse<byte[]>> get(
      final String path, final @Nullable String etag) {
    final HttpRequest.Builder request = request(path);
    if (etag != null) {
      request.header("If-None-Match", etag);
    }
    return this.http.sendAsync(request.build(), BODY);
  }

  @Override
  public <T> void json(
      final String path, final @NotNull Type type, final @NotNull Consumer<@Nullable T> then) {
    get(path, null)
        .thenAccept(
            response -> {
              if (response.statusCode() != 200 && response.statusCode() != 204) {
                LOGGER.warn("Taerra: {} answered {}", path, response.statusCode());
                return;
              }
              final T value =
                  response.statusCode() == 204
                      ? null
                      : GSON.fromJson(new String(response.body(), StandardCharsets.UTF_8), type);
              execute(() -> then.accept(value));
            })
        .exceptionally(
            error -> {
              LOGGER.warn("Taerra: {} failed: {}", path, error.toString());
              return null;
            });
  }

  @Override
  public void send(
      final String method,
      final String path,
      final @Nullable Object body,
      final @NotNull IntConsumer then) {
    final HttpRequest request =
        request(path)
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
            .build();
    this.http
        .sendAsync(request, HttpResponse.BodyHandlers.discarding())
        .handle((response, error) -> error == null ? response.statusCode() : 0)
        .thenAccept(status -> execute(() -> then.accept(status)));
  }

  @Override
  public CompletableFuture<WebSocket> socket(final WebSocket.Listener listener) {
    final WebSocket.Builder builder = this.http.newWebSocketBuilder();
    if (this.token != null) {
      builder.header("Cookie", COOKIE + this.token);
    }
    return builder.buildAsync(URI.create(this.base.replaceFirst("^http", "ws") + "/ws"), listener);
  }

  @Override
  public void execute(final @NotNull Runnable task) {
    Minecraft.getInstance()
        .execute(
            () -> {
              if (!this.closed) {
                task.run();
              }
            });
  }

  @Override
  public void close() {
    this.closed = true;
    this.http.shutdownNow();
  }

  private HttpRequest.Builder request(final String path) {
    final HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(this.base + path))
            .timeout(TIMEOUT)
            .header("Accept-Encoding", "gzip");
    if (this.token != null) {
      request.header("Cookie", COOKIE + this.token);
    }
    return request;
  }
}
