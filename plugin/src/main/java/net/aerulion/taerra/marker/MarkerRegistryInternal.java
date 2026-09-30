package net.aerulion.taerra.marker;

import java.util.Set;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/** Also a listener: register it so a disabled plugin's layers go with it. */
public sealed interface MarkerRegistryInternal extends MarkerRegistry, Listener
    permits MarkerRegistryImpl {

  @NotNull
  Set<NamespacedKey> drainChangedWorlds();
}
