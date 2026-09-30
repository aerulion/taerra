package net.aerulion.taerra.mod.session;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import java.net.URI;
import net.aerulion.taerra.mod.config.Settings;
import net.aerulion.taerra.mod.util.Toasts;
import net.aerulion.taerra.mod.web.WebClientImpl;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.WebMapImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class MapSessionImpl implements MapSession {

  private static final Logger LOGGER = LogUtils.getLogger();

  private final int protocol;
  private final Settings settings;
  private @Nullable WebMap map;
  private @Nullable Component unavailable;

  public MapSessionImpl(final int protocol, final Settings settings) {
    this.protocol = protocol;
    this.settings = settings;
  }

  private static @NotNull String baseUrl(final @NotNull JsonObject handshake) {
    final String url = handshake.has("url") ? handshake.get("url").getAsString() : "";
    if (!url.isBlank()) {
      final @NotNull String scheme = String.valueOf(URI.create(url).getScheme());
      if (!scheme.equals("http") && !scheme.equals("https")) {
        throw new IllegalArgumentException("not an http(s) address: " + url);
      }
      return url;
    }
    final @Nullable ServerData server = Minecraft.getInstance().getCurrentServer();
    @NotNull
    String host = server == null ? "localhost" : ServerAddress.parseString(server.ip).getHost();
    if (host.contains(":")) {
      host = "[" + host + "]";
    }
    return "http://" + host + ":" + handshake.get("port").getAsInt();
  }

  @Override
  public @Nullable WebMap map() {
    return this.map;
  }

  @Override
  public @NotNull Component unavailable() {
    return this.unavailable != null
        ? this.unavailable
        : Component.translatable("taerra.unavailable");
  }

  @Override
  public void connect(final @NotNull HandshakePayload handshake) {
    close();
    final JsonObject json;
    final int protocol;
    final String base;
    try {
      json = JsonParser.parseString(handshake.json()).getAsJsonObject();
      protocol = json.has("protocol") ? json.get("protocol").getAsInt() : 0;
      base = protocol == this.protocol ? baseUrl(json) : "";
    } catch (final RuntimeException e) {
      // Any server may send on taerra:web; a message that is not ours must not break the game.
      LOGGER.warn("Taerra: ignoring a malformed handshake: {}", e.toString());
      return;
    }
    if (protocol != this.protocol) {
      this.unavailable =
          Component.translatable(
              protocol < this.protocol ? "taerra.outdated.plugin" : "taerra.outdated.mod");
      Toasts.show(Component.translatable("taerra.outdated"), this.unavailable);
      return;
    }
    final String token =
        json.get("token") instanceof final JsonPrimitive value && value.isString()
            ? value.getAsString()
            : null;
    this.map = new WebMapImpl(new WebClientImpl(base, token), this.settings);
  }

  @Override
  public void close() {
    this.unavailable = null;
    if (this.map != null) {
      this.map.close();
      this.map = null;
    }
  }

  @Override
  public void tick(final String playerWorld) {
    if (this.map != null) {
      this.map.tick(playerWorld);
    }
  }
}
