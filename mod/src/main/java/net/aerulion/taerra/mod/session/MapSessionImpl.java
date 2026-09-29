package net.aerulion.taerra.mod.session;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

public final class MapSessionImpl implements MapSession {

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
    final JsonObject json = JsonParser.parseString(handshake.json()).getAsJsonObject();
    final int protocol = json.has("protocol") ? json.get("protocol").getAsInt() : 0;
    if (protocol != this.protocol) {
      this.unavailable =
          Component.translatable(
              protocol < this.protocol ? "taerra.outdated.plugin" : "taerra.outdated.mod");
      Toasts.show(Component.translatable("taerra.outdated"), this.unavailable);
      return;
    }
    final String token = json.has("token") ? json.get("token").getAsString() : null;
    this.map = new WebMapImpl(new WebClientImpl(baseUrl(json), token), this.settings);
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
