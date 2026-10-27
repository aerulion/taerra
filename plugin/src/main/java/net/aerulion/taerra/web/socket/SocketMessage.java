package net.aerulion.taerra.web.socket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum SocketMessage {
  ACCOUNT("account"),
  CONFIG("config"),
  DEVICES("devices"),
  ICONS("icons"),
  LAYERS("layers"),
  MARKERS("markers"),
  NAVIGATION("navigation"),
  PLAYERS("players"),
  PONG("pong"),
  PROGRESS("progress"),
  REGIONS("regions"),
  SETTINGS("settings"),
  SETTINGS_EDITORS("settings_editors"),
  SKY("sky"),
  TILE_UPDATED("tile_updated"),
  WAYPOINT_COLOR("waypointColor"),
  WORLDS("worlds");

  private static final @NotNull ObjectMapper MAPPER = new ObjectMapper();

  private final @NotNull String type;

  SocketMessage(final @NotNull String type) {
    this.type = type;
  }

  public @NotNull String type() {
    return this.type;
  }

  public @NotNull Map<String, Object> payload() {
    final @NotNull Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", this.type);
    return payload;
  }

  public @NotNull String json(final @Nullable Object @NotNull ... fields) {
    if (fields.length % 2 != 0) {
      throw new IllegalArgumentException("Fields come in name and value pairs");
    }
    final @NotNull Map<String, Object> payload = payload();
    for (int i = 0; i < fields.length; i += 2) {
      payload.put((String) fields[i], fields[i + 1]);
    }
    try {
      return MAPPER.writeValueAsString(payload);
    } catch (final JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize a " + this.type + " message", e);
    }
  }

  public @NotNull String withData(final @NotNull String data) {
    return "{\"type\":\"" + this.type + "\",\"data\":" + data + "}";
  }
}
