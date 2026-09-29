package net.aerulion.taerra.marker;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.aerulion.taerra.api.marker.MarkerLayer;
import org.bukkit.NamespacedKey;
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
      final @NotNull String id, final @NotNull String label, final boolean shownByDefault) {
    return this.layers.computeIfAbsent(
        id, key -> new MarkerLayerImpl(key, label, shownByDefault, this.changedWorlds::add));
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

  @Override
  public @NotNull Set<NamespacedKey> drainChangedWorlds() {
    final @NotNull Set<NamespacedKey> drained = Set.copyOf(this.changedWorlds);
    this.changedWorlds.removeAll(drained);
    return drained;
  }
}
