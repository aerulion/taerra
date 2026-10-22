package net.aerulion.taerra.web.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.config.WebSettings;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.web.player.PlayerAvatars;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ModHandshake implements Listener, PluginMessageListener {

  public static final @NotNull String CHANNEL = "taerra:web";

  public static final int PROTOCOL = 7;

  private static final int MAX_VERSION_LENGTH = 64;

  private final @NotNull Plugin plugin;
  private final @NotNull TaerraConfig config;
  private final @NotNull WebSettings web;
  private final @NotNull WebSessions sessions;
  private final @NotNull ObjectMapper mapper = new ObjectMapper();
  private final @NotNull Map<UUID, String> tokens = new ConcurrentHashMap<>();
  private final @NotNull Map<UUID, String> versions = new ConcurrentHashMap<>();
  private final @NotNull Map<UUID, Integer> protocols = new ConcurrentHashMap<>();

  public ModHandshake(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig config,
      final @NotNull WebSettings web,
      final @NotNull WebSessions sessions) {
    super();
    this.plugin = plugin;
    this.config = config;
    this.web = web;
    this.sessions = sessions;
  }

  public static boolean uses(final @NotNull Player player) {
    return player.getListeningPluginChannels().contains(CHANNEL);
  }

  public void start() {
    this.plugin.getServer().getMessenger().registerOutgoingPluginChannel(this.plugin, CHANNEL);
    this.plugin
        .getServer()
        .getMessenger()
        .registerIncomingPluginChannel(this.plugin, CHANNEL, this);
    this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
  }

  @EventHandler
  public void onRegister(final @NotNull PlayerRegisterChannelEvent event) {
    if (CHANNEL.equals(event.getChannel())) {
      send(event.getPlayer());
    }
  }

  @EventHandler
  public void onJoin(final @NotNull PlayerJoinEvent event) {
    if (uses(event.getPlayer())) {
      send(event.getPlayer());
    }
  }

  @EventHandler
  public void onQuit(final @NotNull PlayerQuitEvent event) {
    this.sessions.end(this.tokens.remove(event.getPlayer().getUniqueId()));
    this.versions.remove(event.getPlayer().getUniqueId());
    this.protocols.remove(event.getPlayer().getUniqueId());
  }

  @Override
  public void onPluginMessageReceived(
      final @NotNull String channel, final @NotNull Player player, final byte @NotNull [] message) {
    if (!CHANNEL.equals(channel)) {
      return;
    }
    final @NotNull JsonNode answer;
    try {
      answer = this.mapper.readTree(message);
    } catch (final IOException e) {
      return;
    }
    this.protocols.put(player.getUniqueId(), answer.path("protocol").asInt(0));
    final @NotNull String version = answer.path("version").asText("");
    if (version.length() <= MAX_VERSION_LENGTH && version.matches("[\\w.+-]+")) {
      this.versions.put(player.getUniqueId(), version);
    }
  }

  public @Nullable String version(final @NotNull Player player) {
    return this.versions.get(player.getUniqueId());
  }

  public boolean isMod(final @Nullable String token) {
    return token != null && this.tokens.containsValue(token);
  }

  public boolean discovered(final @NotNull Player player, final @NotNull String region) {
    if (this.protocols.getOrDefault(player.getUniqueId(), 0) != PROTOCOL) {
      return false;
    }
    send(player, Map.of("discovered", region));
    return true;
  }

  private void send(final @NotNull Player player) {
    final @NotNull Map<String, Object> hello = new HashMap<>();
    hello.put("protocol", PROTOCOL);
    hello.put("url", this.web.publicUrl());
    hello.put("port", this.web.port());
    final @Nullable String token = token(player);
    if (token != null) {
      hello.put("token", token);
    }
    send(player, hello);
  }

  private void send(final @NotNull Player player, final @NotNull Map<String, Object> message) {
    try {
      player.sendPluginMessage(this.plugin, CHANNEL, this.mapper.writeValueAsBytes(message));
    } catch (final JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private @Nullable String token(final @NotNull Player player) {
    if (!TaerraPermission.LOGIN.check(player)) {
      return null;
    }
    return this.tokens.computeIfAbsent(
        player.getUniqueId(),
        uuid -> {
          final @NotNull String token =
              this.sessions.open(uuid, player.getName(), PlayerAvatars.skin(player));
          this.sessions.grant(uuid, WebGrants.of(player, this.config));
          return token;
        });
  }
}
