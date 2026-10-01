package net.aerulion.taerra.api;

import net.aerulion.taerra.api.marker.MarkerRegistry;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Entry point to the TAERRA API, registered with Bukkit's {@link org.bukkit.plugin.ServicesManager}
 * while TAERRA enables.
 */
@ApiStatus.NonExtendable
public interface TaerraApi {

  /**
   * Returns the API of the running TAERRA plugin.
   *
   * @return the API
   * @throws IllegalStateException if TAERRA is not enabled
   */
  static @NotNull TaerraApi get() {
    final @Nullable TaerraApi api = Bukkit.getServicesManager().load(TaerraApi.class);
    if (api == null) {
      throw new IllegalStateException("TAERRA is not enabled");
    }
    return api;
  }

  /**
   * Returns the registry of the map's marker layers.
   *
   * @return the marker registry
   */
  @NotNull
  MarkerRegistry markers();
}
