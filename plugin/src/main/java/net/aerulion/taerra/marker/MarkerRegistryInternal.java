package net.aerulion.taerra.marker;

import java.util.Set;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

public sealed interface MarkerRegistryInternal extends MarkerRegistry permits MarkerRegistryImpl {

  @NotNull
  Set<NamespacedKey> drainChangedWorlds();
}
