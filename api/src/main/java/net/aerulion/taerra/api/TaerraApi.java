package net.aerulion.taerra.api;

import net.aerulion.taerra.api.marker.MarkerRegistry;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public interface TaerraApi {

  static @NotNull TaerraApi get() {
    final @Nullable TaerraApi api = Bukkit.getServicesManager().load(TaerraApi.class);
    if (api == null) {
      throw new IllegalStateException("TAERRA is not enabled");
    }
    return api;
  }

  @NotNull
  MarkerRegistry markers();
}
