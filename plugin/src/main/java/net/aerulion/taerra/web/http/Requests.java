package net.aerulion.taerra.web.http;

import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import java.net.URI;
import java.util.Set;
import java.util.function.Function;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.web.auth.Viewer;
import net.aerulion.taerra.web.auth.WebSessions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Requests {

  private static final @NotNull Set<HandlerType> SAFE_METHODS =
      Set.of(HandlerType.GET, HandlerType.HEAD, HandlerType.OPTIONS);

  private final @NotNull WebSessions sessions;
  private final @NotNull TaerraConfig config;

  public Requests(final @NotNull WebSessions sessions, final @NotNull TaerraConfig config) {
    super();
    this.sessions = sessions;
    this.config = config;
  }

  public static void rejectCrossSite(final @NotNull Context ctx) {
    if (!SAFE_METHODS.contains(ctx.method()) && !sameOrigin(ctx::header, "")) {
      ctx.status(403).result("Cross-site request").skipRemainingHandlers();
    }
  }

  public static boolean sameOrigin(
      final @NotNull Function<String, String> header, final @NotNull String publicUrl) {
    final @Nullable String site = header.apply("Sec-Fetch-Site");
    if (site != null) {
      return site.equals("same-origin");
    }
    final @Nullable String origin = header.apply("Origin");
    if (origin == null) {
      return true;
    }
    final @Nullable String from = authority(origin);
    return from != null
        && (from.equals(header.apply("Host"))
            || from.equals(header.apply("X-Forwarded-Host"))
            || from.equals(authority(publicUrl)));
  }

  private static @Nullable String authority(final @NotNull String url) {
    try {
      return URI.create(url).getRawAuthority();
    } catch (final IllegalArgumentException e) {
      return null;
    }
  }

  public static boolean secure(final @NotNull Context ctx) {
    return "https".equals(ctx.scheme())
        || "https".equalsIgnoreCase(ctx.header("X-Forwarded-Proto"));
  }

  public WebSessions.@Nullable Session session(final @NotNull Context ctx) {
    return this.sessions.session(ctx.cookie(WebSessions.COOKIE));
  }

  public @NotNull Viewer viewer(final @NotNull Context ctx) {
    return this.sessions.viewer(ctx.cookie(WebSessions.COOKIE));
  }

  public boolean visible(final @NotNull Context ctx, final @NotNull String worldName) {
    return viewer(ctx).canSee(this.config, worldName);
  }
}
