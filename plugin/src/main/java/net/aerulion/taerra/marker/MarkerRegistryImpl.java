package net.aerulion.taerra.marker;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.aerulion.taerra.api.marker.MarkerLayer;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MarkerRegistryImpl implements MarkerRegistryInternal {

  private final @NotNull Map<String, MarkerLayer> layers = new LinkedHashMap<>();
  private final @NotNull Set<NamespacedKey> changedWorlds = ConcurrentHashMap.newKeySet();

  public MarkerRegistryImpl() {
    super();
  }

  @Override
  public synchronized @NotNull MarkerLayer layer(
      final @NotNull Plugin owner,
      final @NotNull String id,
      final @NotNull String label,
      final boolean shownByDefault) {
    final @NotNull MarkerLayer layer =
        this.layers.computeIfAbsent(
            id,
            key -> new MarkerLayerImpl(owner, key, label, shownByDefault, this.changedWorlds::add));
    if (!layer.owner().equals(owner)) {
      throw new IllegalArgumentException(
          "The layer " + id + " belongs to " + layer.owner().getName());
    }
    return layer;
  }

  @Override
  public synchronized @Nullable MarkerLayer existingLayer(final @NotNull String id) {
    return this.layers.get(id);
  }

  @Override
  public synchronized @NotNull Collection<MarkerLayer> layers() {
    return List.copyOf(this.layers.values());
  }

  @Override
  public void removeLayer(final @NotNull String id) {
    final @Nullable MarkerLayer removed;
    synchronized (this) {
      removed = this.layers.remove(id);
    }
    if (removed != null) {
      removed.clear();
    }
  }

  @EventHandler
  public void onPluginDisable(final @NotNull PluginDisableEvent event) {
    removeLayers(event.getPlugin());
  }

  void removeLayers(final @NotNull Plugin owner) {
    for (final @NotNull MarkerLayer layer : layers()) {
      if (layer.owner().equals(owner)) {
        removeLayer(layer.id());
      }
    }
  }

  @Override
  public @NotNull Set<NamespacedKey> drainChangedWorlds() {
    final @NotNull Set<NamespacedKey> drained = Set.copyOf(this.changedWorlds);
    this.changedWorlds.removeAll(drained);
    return drained;
  }
}
