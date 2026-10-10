package net.aerulion.taerra.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

public final class SettingsImpl implements Settings {

  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private transient Path file;
  private transient @NotNull String saved = "";
  private boolean minimap = true;
  private int minimapSize = 100;
  private boolean minimapLeft;
  private int minimapZoom = 3;
  private boolean minimapRotates;
  private boolean grayMap;
  private boolean allWorldPlayers;
  private boolean waypointLabels = true;
  private Set<String> minimapInfo = new LinkedHashSet<>(List.of(MinimapInfo.COORDINATES.id()));

  private SettingsImpl() {}

  public static @NotNull Settings load(final @NotNull Path file) {
    @NotNull SettingsImpl settings = new SettingsImpl();
    if (Files.exists(file)) {
      try {
        final @NotNull String json = Files.readString(file);
        final SettingsImpl loaded = GSON.fromJson(json, SettingsImpl.class);
        if (loaded != null) {
          settings = loaded;
          settings.saved = json;
          settings.setMinimapSize(settings.minimapSize);
          settings.setMinimapZoom(settings.minimapZoom);
          if (settings.minimapInfo == null) {
            settings.minimapInfo = new LinkedHashSet<>();
          }
        }
      } catch (final IOException | JsonParseException e) {
        LOGGER.warn("TAERRA: could not read {}, using the defaults: {}", file, e.toString());
      }
    }
    settings.file = file;
    return settings;
  }

  @Override
  public boolean minimap() {
    return this.minimap;
  }

  @Override
  public void setMinimap(final boolean minimap) {
    this.minimap = minimap;
  }

  @Override
  public int minimapSize() {
    return this.minimapSize;
  }

  @Override
  public void setMinimapSize(final int size) {
    this.minimapSize = Math.clamp(size, MIN_SIZE, MAX_SIZE);
  }

  @Override
  public boolean minimapLeft() {
    return this.minimapLeft;
  }

  @Override
  public void setMinimapLeft(final boolean left) {
    this.minimapLeft = left;
  }

  @Override
  public int minimapZoom() {
    return this.minimapZoom;
  }

  @Override
  public void setMinimapZoom(final int zoom) {
    this.minimapZoom = Math.clamp(zoom, MIN_ZOOM, MAX_ZOOM);
  }

  @Override
  public boolean minimapRotates() {
    return this.minimapRotates;
  }

  @Override
  public void setMinimapRotates(final boolean rotates) {
    this.minimapRotates = rotates;
  }

  @Override
  public boolean grayMap() {
    return this.grayMap;
  }

  @Override
  public void setGrayMap(final boolean gray) {
    this.grayMap = gray;
  }

  @Override
  public boolean allWorldPlayers() {
    return this.allWorldPlayers;
  }

  @Override
  public void setAllWorldPlayers(final boolean all) {
    this.allWorldPlayers = all;
  }

  @Override
  public boolean waypointLabels() {
    return this.waypointLabels;
  }

  @Override
  public void setWaypointLabels(final boolean labels) {
    this.waypointLabels = labels;
  }

  @Override
  public boolean minimapInfo(final @NotNull MinimapInfo info) {
    return this.minimapInfo.contains(info.id());
  }

  @Override
  public void setMinimapInfo(final @NotNull MinimapInfo info, final boolean shown) {
    if (shown) {
      this.minimapInfo.add(info.id());
    } else {
      this.minimapInfo.remove(info.id());
    }
  }

  @Override
  public void save() {
    final String json = GSON.toJson(this);
    if (json.equals(this.saved)) {
      return;
    }
    try {
      Files.writeString(this.file, json);
      this.saved = json;
    } catch (final IOException e) {
      LOGGER.warn("TAERRA: could not save {}: {}", this.file, e.toString());
    }
  }
}
