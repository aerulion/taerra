package net.aerulion.taerra.marker;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.MarkerLayer;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class MarkerLayerImpl implements MarkerLayer {

  private final @NotNull Plugin owner;
  private final @NotNull String id;
  private final @NotNull String label;
  private final boolean shownByDefault;
  private final @NotNull Map<String, Marker> markers = new ConcurrentHashMap<>();
  private final @NotNull Consumer<NamespacedKey> onChange;

  MarkerLayerImpl(
      final @NotNull Plugin owner,
      final @NotNull String id,
      final @NotNull String label,
      final boolean shownByDefault,
      final @NotNull Consumer<NamespacedKey> onChange) {
    super();
    this.owner = owner;
    this.id = id;
    this.label = label;
    this.shownByDefault = shownByDefault;
    this.onChange = onChange;
  }

  @Override
  public @NotNull Plugin owner() {
    return this.owner;
  }

  @Override
  public @NotNull String id() {
    return this.id;
  }

  @Override
  public @NotNull String label() {
    return this.label;
  }

  @Override
  public boolean shownByDefault() {
    return this.shownByDefault;
  }

  @Override
  public void set(final @NotNull Marker marker) {
    final @Nullable Marker previous = this.markers.put(marker.id(), marker);
    if (!marker.equals(previous)) {
      changed(previous);
      this.onChange.accept(marker.world());
    }
  }

  @Override
  public @Nullable Marker remove(final @NotNull String id) {
    final @Nullable Marker removed = this.markers.remove(id);
    changed(removed);
    return removed;
  }

  @Override
  public @Nullable Marker get(final @NotNull String id) {
    return this.markers.get(id);
  }

  @Override
  public @NotNull Collection<Marker> markers() {
    return List.copyOf(this.markers.values());
  }

  @Override
  public synchronized void setAll(final @NotNull Collection<? extends Marker> markers) {
    final @NotNull Map<String, Marker> next = new HashMap<>();
    for (final @NotNull Marker marker : markers) {
      next.put(marker.id(), marker);
    }
    for (final @NotNull String id : List.copyOf(this.markers.keySet())) {
      if (!next.containsKey(id)) {
        remove(id);
      }
    }
    next.values().forEach(this::set);
  }

  @Override
  public void clear() {
    setAll(List.of());
  }

  private void changed(final @Nullable Marker marker) {
    if (marker != null) {
      this.onChange.accept(marker.world());
    }
  }
}
