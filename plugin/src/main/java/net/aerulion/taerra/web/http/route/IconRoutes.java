package net.aerulion.taerra.web.http.route;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.icon.MarkerIconsImpl;
import net.aerulion.taerra.web.auth.Viewer;
import net.aerulion.taerra.web.http.Requests;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IconRoutes implements Routes {

  private final @NotNull Requests requests;
  private final @NotNull TaerraConfig taerraConfig;
  private final @NotNull MarkerIconsImpl icons;

  public IconRoutes(
      final @NotNull Requests requests,
      final @NotNull TaerraConfig taerraConfig,
      final @NotNull MarkerIconsImpl icons) {
    super();
    this.requests = requests;
    this.taerraConfig = taerraConfig;
    this.icons = icons;
  }

  private static @Nullable NamespacedKey key(final @NotNull Context ctx) {
    return NamespacedKey.fromString(ctx.pathParam("namespace") + ":" + ctx.pathParam("key"));
  }

  private static byte @Nullable [] png(final @Nullable String base64) {
    if (base64 == null) {
      return null;
    }
    try {
      return Base64.getDecoder().decode(base64);
    } catch (final IllegalArgumentException e) {
      return new byte[0];
    }
  }

  private static @NotNull Map<String, Object> json(
      final MarkerIconsImpl.@NotNull Icon icon, final @Nullable Integer uses) {
    final @NotNull Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("id", icon.key().asString());
    if (icon.label() != null) {
      entry.put("label", icon.label());
    }
    entry.put("source", icon.source().name().toLowerCase(Locale.ROOT).replace("_", ""));
    entry.put("version", icon.version());
    entry.put("tint", icon.tint());
    if (uses != null) {
      entry.put("uses", uses);
    }
    return entry;
  }

  private static int status(final MarkerIconsImpl.@NotNull Outcome outcome) {
    return switch (outcome) {
      case SAVED -> 200;
      case NOT_FOUND -> 404;
      case BAD_NAME -> 400;
      case BAD_IMAGE -> 415;
      case TAKEN -> 409;
      case FULL -> 422;
      case FAILED -> 500;
    };
  }

  @Override
  public void register(final @NotNull RoutesConfig routes) {
    routes.get("/api/icons", this::handleListRequest);
    routes.post("/api/icons", this::handleUploadRequest);
    routes.get("/api/icons/{namespace}/{key}.png", this::handleImageRequest);
    routes.put("/api/icons/{namespace}/{key}", this::handleUpdateRequest);
    routes.delete("/api/icons/{namespace}/{key}", this::handleDeleteRequest);
  }

  private void handleListRequest(final @NotNull Context ctx) {
    final @Nullable Map<NamespacedKey, Integer> uses =
        this.requests.viewer(ctx).canEditIcons()
            ? this.icons.uses(this.taerraConfig.layerIcons().values())
            : null;
    ctx.header("Cache-Control", "no-store")
        .json(
            this.icons.all().stream()
                .map(
                    icon ->
                        json(
                            icon,
                            uses == null || icon.source() != MarkerIconsImpl.Source.UPLOAD
                                ? null
                                : uses.getOrDefault(icon.key(), 0)))
                .toList());
  }

  private void handleImageRequest(final @NotNull Context ctx) {
    final @Nullable NamespacedKey key = key(ctx);
    final MarkerIconsImpl.@Nullable Icon icon = key == null ? null : this.icons.icon(key);
    if (icon == null) {
      ctx.header("Cache-Control", "no-store").status(404).result("Icon not found");
      return;
    }
    ctx.header(
            "Cache-Control",
            icon.version().equals(ctx.queryParam("v"))
                ? "public, max-age=31536000, immutable"
                : "no-cache")
        .contentType("image/png")
        .result(icon.png());
  }

  private @Nullable Viewer manager(final @NotNull Context ctx) {
    final @NotNull Viewer viewer = this.requests.viewer(ctx);
    if (!viewer.canEditIcons()) {
      ctx.status(403).result("Not allowed");
      return null;
    }
    return viewer;
  }

  private void handleUploadRequest(final @NotNull Context ctx) {
    if (manager(ctx) == null) {
      return;
    }
    final @NotNull IconRequest request;
    try {
      request = ctx.bodyAsClass(IconRequest.class);
    } catch (final Exception e) {
      ctx.status(400).result("Invalid icon");
      return;
    }
    respond(ctx, this.icons.upload(request.name(), png(request.png())), 201);
  }

  private void handleUpdateRequest(final @NotNull Context ctx) {
    if (manager(ctx) == null) {
      return;
    }
    final @Nullable NamespacedKey key = key(ctx);
    final @NotNull IconRequest request;
    try {
      request = ctx.bodyAsClass(IconRequest.class);
    } catch (final Exception e) {
      ctx.status(400).result("Invalid icon");
      return;
    }
    if (key == null) {
      ctx.status(404).result("Icon not found");
      return;
    }
    respond(ctx, this.icons.update(key, request.name(), png(request.png())), 200);
  }

  private void handleDeleteRequest(final @NotNull Context ctx) {
    if (manager(ctx) == null) {
      return;
    }
    final @Nullable NamespacedKey key = key(ctx);
    ctx.status(key != null && this.icons.delete(key) ? 204 : 404);
  }

  private void respond(
      final @NotNull Context ctx, final MarkerIconsImpl.@NotNull Result result, final int saved) {
    if (result.icon() == null) {
      ctx.status(status(result.outcome())).result(result.outcome().name());
      return;
    }
    ctx.status(saved).header("Cache-Control", "no-store").json(json(result.icon(), null));
  }

  private record IconRequest(@Nullable String name, @Nullable String png) {}
}
