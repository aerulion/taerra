package net.aerulion.taerra.web.http.route;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;
import java.util.HashMap;
import java.util.Map;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.config.WebSettings;
import net.aerulion.taerra.web.auth.Device;
import net.aerulion.taerra.web.auth.Viewer;
import net.aerulion.taerra.web.auth.WebSessions;
import net.aerulion.taerra.web.http.Requests;
import net.aerulion.taerra.web.player.PlayerAvatars;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AccountRoutes implements Routes {

  private final @NotNull Plugin plugin;
  private final @NotNull Requests requests;
  private final @NotNull WebSessions sessions;
  private final @NotNull WebSettings web;
  private final @NotNull PlayerAvatars avatars;

  public AccountRoutes(
      final @NotNull Plugin plugin,
      final @NotNull Requests requests,
      final @NotNull WebSessions sessions,
      final @NotNull WebSettings web,
      final @NotNull PlayerAvatars avatars) {
    super();
    this.plugin = plugin;
    this.requests = requests;
    this.sessions = sessions;
    this.web = web;
    this.avatars = avatars;
  }

  @Override
  public void register(final @NotNull RoutesConfig routes) {
    routes.get("/api/account", this::handleAccountRequest);
    routes.get("/api/account/avatar.png", this::handleAccountAvatarRequest);
    routes.get("/api/account/sessions", this::handleDevicesRequest);
    routes.delete("/api/account/sessions/{id}", this::handleDeviceLogoutRequest);
    routes.post("/api/login", this::handleLoginRequest);
    routes.post("/api/logout", this::handleLogoutRequest);
    routes.post("/api/login/pair", this::handlePairRequest);
    routes.post("/api/login/claim", this::handleClaimRequest);
    routes.post("/api/login/link", this::handleLinkRequest);
  }

  private static @NotNull Map<String, Object> account(
      final WebSessions.@NotNull Session session, final @NotNull Viewer viewer) {
    return Map.of(
        "uuid", session.uuid().toString(),
        "name", session.name(),
        "teleport", viewer.canTeleport(),
        "mod", viewer.usesMod(),
        "markers", viewer.canEditMarkers(),
        "privateGroups", viewer.canCreatePrivateGroups(),
        "personal", viewer.canKeepPersonalMarkers(),
        "render", viewer.canRender(),
        "hide", viewer.canHide(),
        "config", viewer.canConfigure());
  }

  private void handleAccountRequest(final @NotNull Context ctx) {
    ctx.header("Cache-Control", "no-store");
    final WebSessions.@Nullable Session session =
        this.sessions.session(ctx.cookie(WebSessions.COOKIE));
    if (session == null) {
      ctx.status(204);
      return;
    }
    ctx.json(account(session, this.requests.viewer(ctx)));
  }

  private void handleAccountAvatarRequest(final @NotNull Context ctx) {
    final WebSessions.@Nullable Session session =
        this.sessions.session(ctx.cookie(WebSessions.COOKIE));
    final @Nullable String skin = session == null ? null : session.skin();
    final byte @Nullable [] png = skin == null ? null : this.avatars.png(skin);
    if (png == null) {
      ctx.header("Cache-Control", "no-store").status(404).result("Avatar not found");
      return;
    }
    ctx.header("Cache-Control", "private, max-age=3600").contentType("image/png").result(png);
  }

  private void handleDevicesRequest(final @NotNull Context ctx) {
    ctx.header("Cache-Control", "no-store");
    final @Nullable String token = ctx.cookie(WebSessions.COOKIE);
    final WebSessions.@Nullable Session session = this.sessions.session(token);
    if (session == null) {
      ctx.status(401).result("Not logged in");
      return;
    }
    ctx.json(
        this.sessions.devices(session.uuid(), token).stream()
            .map(
                device -> {
                  final @NotNull Map<String, Object> entry = new HashMap<>();
                  entry.put("id", device.id());
                  entry.put("game", device.game());
                  entry.put("browser", device.device() == null ? null : device.device().browser());
                  entry.put("os", device.device() == null ? null : device.device().os());
                  entry.put("created", device.created());
                  entry.put("seen", device.seen());
                  entry.put("current", device.current());
                  return entry;
                })
            .toList());
  }

  private void handleDeviceLogoutRequest(final @NotNull Context ctx) {
    if (!Requests.sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    final @Nullable String token = ctx.cookie(WebSessions.COOKIE);
    final WebSessions.@Nullable Session session = this.sessions.session(token);
    if (session == null) {
      ctx.status(401).result("Not logged in");
      return;
    }
    final boolean current =
        this.sessions.devices(session.uuid(), token).stream()
            .anyMatch(device -> device.current() && device.id().equals(ctx.pathParam("id")));
    if (!this.sessions.endDevice(session.uuid(), ctx.pathParam("id"))) {
      ctx.status(404).result("No such device");
      return;
    }
    if (current) {
      ctx.removeCookie(WebSessions.COOKIE, "/");
    }
    ctx.status(204);
  }

  private void handleLoginRequest(final @NotNull Context ctx) {
    if (!Requests.sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    final WebSessions.@Nullable Login login =
        this.sessions.redeem(ctx.body().strip(), Device.of(ctx.userAgent()));
    if (login == null) {
      ctx.status(401).result("Invalid or expired code");
      return;
    }
    logIn(ctx, login);
  }

  private void handlePairRequest(final @NotNull Context ctx) {
    if (!Requests.sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    final WebSessions.@Nullable Pair pair = this.sessions.pair();
    if (pair == null) {
      ctx.status(503).result("Too many logins waiting");
      return;
    }
    ctx.header("Cache-Control", "no-store")
        .json(
            Map.of(
                "code", pair.code(),
                "claim", pair.claim(),
                "seconds", WebSessions.CODE_LIFETIME.toSeconds()));
  }

  private void handleClaimRequest(final @NotNull Context ctx) {
    if (!Requests.sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    final WebSessions.@Nullable Claim claim =
        this.sessions.claim(ctx.body().strip(), Device.of(ctx.userAgent()));
    if (claim == null) {
      ctx.header("Cache-Control", "no-store").status(410).result("Expired");
    } else if (claim.login() == null) {
      ctx.header("Cache-Control", "no-store").status(202);
    } else {
      logIn(ctx, claim.login());
    }
  }

  private void handleLinkRequest(final @NotNull Context ctx) {
    if (!Requests.sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    final @Nullable String code = this.sessions.linkCode(ctx.cookie(WebSessions.COOKIE));
    if (code == null) {
      ctx.status(403).result("Not allowed");
      return;
    }
    ctx.header("Cache-Control", "no-store")
        .json(
            Map.of(
                "code",
                code,
                "base",
                this.web.publicUrl(),
                "seconds",
                WebSessions.CODE_LIFETIME.toSeconds()));
  }

  private void logIn(final @NotNull Context ctx, final WebSessions.@NotNull Login login) {
    ctx.cookie(
        new Cookie(
            WebSessions.COOKIE,
            login.token(),
            "/",
            (int) ((login.session().expires() - System.currentTimeMillis()) / 1000),
            Requests.secure(ctx),
            true,
            null,
            SameSite.STRICT));
    Bukkit.getScheduler()
        .runTask(
            this.plugin,
            () -> {
              final @Nullable Player player = Bukkit.getPlayer(login.session().uuid());
              if (player != null) {
                player.sendMessage(Messages.webLogin());
              }
            });
    ctx.header("Cache-Control", "no-store")
        .json(account(login.session(), this.sessions.viewer(login.token())));
  }

  private void handleLogoutRequest(final @NotNull Context ctx) {
    if (!Requests.sameOrigin(ctx)) {
      ctx.status(403).result("Cross-site request");
      return;
    }
    this.sessions.end(ctx.cookie(WebSessions.COOKIE));
    ctx.removeCookie(WebSessions.COOKIE, "/");
    ctx.status(204);
  }
}
