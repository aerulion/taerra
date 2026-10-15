package net.aerulion.taerra.marker;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public sealed interface MarkerRegistryInternal extends MarkerRegistry, Listener
    permits MarkerRegistryImpl {

  @NotNull
  Map<NamespacedKey, Set<String>> drainChangedLayers();

  @NotNull
  Set<UUID> drainChangedPlayers();

  @NotNull
  Collection<PlayerMarkerLayerImpl> playerLayers();
}
