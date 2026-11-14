package net.aerulion.taerra.config;

import net.aerulion.taerra.util.Names;
import net.aerulion.taerra.web.auth.WebSessions;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WebSettings {

  private static final int DEFAULT_PORT = 8100;

  private final @NotNull Plugin plugin;

  public WebSettings(final @NotNull Plugin plugin) {
    super();
    this.plugin = plugin;
  }

  public @NotNull String host() {
    return this.plugin.getConfig().getString("web.host", "0.0.0.0");
  }

  public int port() {
    return this.plugin.getConfig().getInt("web.port", DEFAULT_PORT);
  }

  public @NotNull String publicUrl() {
    return this.plugin.getConfig().getString("web.public-url", "").strip().replaceAll("/+$", "");
  }

  public @NotNull String baseUrl() {
    final @NotNull String configured = publicUrl();
    if (!configured.isEmpty()) {
      return configured;
    }
    final @NotNull String ip = this.plugin.getServer().getIp();
    return "http://" + (ip.isBlank() ? "localhost" : ip) + ":" + port();
  }

  public int sessionDays() {
    return Math.clamp(
        this.plugin.getConfig().getInt("web.session-days", WebSessions.DEFAULT_DAYS),
        0,
        WebSessions.MAX_DAYS);
  }

  public @NotNull String name() {
    final @Nullable String name = Names.of(rawName());
    return name == null ? "" : name;
  }

  public boolean nameRejected() {
    return !rawName().isBlank() && name().isEmpty();
  }

  private @NotNull String rawName() {
    return this.plugin.getConfig().getString("web.name", "");
  }
}
