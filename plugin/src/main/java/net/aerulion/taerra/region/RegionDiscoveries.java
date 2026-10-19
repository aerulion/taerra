package net.aerulion.taerra.region;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.aerulion.taerra.player.PlayerFiles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RegionDiscoveries {

  private final @NotNull PlayerFiles files;
  private final @NotNull MapRegions regions;

  public RegionDiscoveries(final @NotNull PlayerFiles files, final @NotNull MapRegions regions) {
    super();
    this.files = files;
    this.regions = regions;
  }

  public long discoveredAt(final @NotNull UUID player, final @NotNull MapRegion region) {
    final @Nullable Long at = this.files.get(player).discoveries().get(region.id());
    return at != null && at > region.reset() ? at : 0L;
  }

  public boolean discover(final @NotNull UUID player, final @NotNull MapRegion region) {
    if (discoveredAt(player, region) > 0L) {
      return false;
    }
    final boolean[] added = {false};
    this.files.update(
        player,
        data -> {
          final @Nullable Long at = data.discoveries().get(region.id());
          if (at != null && at > region.reset()) {
            return data;
          }
          final @NotNull Map<UUID, Long> kept = new HashMap<>();
          data.discoveries()
              .forEach(
                  (id, when) -> {
                    final @Nullable MapRegion known = this.regions.get(id);
                    if (known != null && when > known.reset()) {
                      kept.put(id, when);
                    }
                  });
          kept.put(region.id(), Math.max(System.currentTimeMillis(), region.reset() + 1L));
          added[0] = true;
          return data.withDiscoveries(kept);
        });
    return added[0];
  }
}
