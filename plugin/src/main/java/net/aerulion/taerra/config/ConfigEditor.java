package net.aerulion.taerra.config;

import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.util.Names;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ConfigEditor {

  private final @NotNull Plugin plugin;
  private final @NotNull TaerraConfig config;
  private final @NotNull MarkerRegistry markers;
  private final @NotNull Runnable webChanged;
  private final @NotNull Runnable layersChanged;

  public ConfigEditor(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig config,
      final @NotNull MarkerRegistry markers,
      final @NotNull Runnable webChanged,
      final @NotNull Runnable layersChanged) {
    super();
    this.plugin = plugin;
    this.config = config;
    this.markers = markers;
    this.webChanged = webChanged;
    this.layersChanged = layersChanged;
  }

  public @Nullable Object get(final @NotNull String key) {
    return this.plugin.getConfig().get(key);
  }

  public @NotNull Change set(final @NotNull String key, final @NotNull Object given) {
    @NotNull Object value = "\"\"".equals(given) ? "" : given;
    if (key.equals("web.name") && !value.toString().isBlank()) {
      final @Nullable String name = Names.of(value.toString());
      if (name == null) {
        return new Change(Effect.REJECTED, value);
      }
      value = name;
    }
    this.plugin.getConfig().set(key, value);
    this.plugin.saveConfig();
    if (key.startsWith("web.")) {
      this.webChanged.run();
      return new Change(Effect.WEB_RESTARTED, value);
    }
    return new Change(key.startsWith("join-hint.") ? Effect.APPLIED : Effect.NEEDS_RESTART, value);
  }

  public void setWorldEnabled(final @NotNull String worldName, final boolean enabled) {
    this.config.setWorldEnabled(this.plugin, worldName, enabled);
  }

  public void setWorldPrivate(final @NotNull String worldName, final boolean hidden) {
    this.config.setWorldPrivate(this.plugin, worldName, hidden);
  }

  public boolean setLayerPrivate(final @NotNull String layerId, final boolean hidden) {
    if (this.markers.existingLayer(layerId) == null && !this.config.layerPrivate(layerId)) {
      return false;
    }
    this.config.setLayerPrivate(this.plugin, layerId, hidden);
    this.layersChanged.run();
    return true;
  }

  public enum Effect {
    APPLIED,
    WEB_RESTARTED,
    NEEDS_RESTART,
    REJECTED
  }

  public record Change(@NotNull Effect effect, @NotNull Object value) {}
}
