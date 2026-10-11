package net.aerulion.taerra.web.http;

import io.javalin.http.Context;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.web.auth.Viewer;
import net.aerulion.taerra.web.auth.WebSessions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Requests {

  private final @NotNull WebSessions sessions;
  private final @NotNull TaerraConfig config;

  public Requests(final @NotNull WebSessions sessions, final @NotNull TaerraConfig config) {
    super();
    this.sessions = sessions;
    this.config = config;
  }

  public static boolean sameOrigin(final @NotNull Context ctx) {
    final @Nullable String site = ctx.header("Sec-Fetch-Site");
    return site == null ? ctx.header("Origin") == null : site.equals("same-origin");
  }

  public static boolean secure(final @NotNull Context ctx) {
    return "https".equals(ctx.scheme())
        || "https".equalsIgnoreCase(ctx.header("X-Forwarded-Proto"));
  }

  public @NotNull Viewer viewer(final @NotNull Context ctx) {
    return this.sessions.viewer(ctx.cookie(WebSessions.COOKIE));
  }

  public boolean visible(final @NotNull Context ctx, final @NotNull String worldName) {
    return viewer(ctx).canSee(this.config, worldName);
  }
}
