package net.aerulion.taerra.region;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RegionVisibility {

  private final @NotNull MapRegions regions;
  private final @NotNull RegionDiscoveries discoveries;

  public RegionVisibility(
      final @NotNull MapRegions regions, final @NotNull RegionDiscoveries discoveries) {
    super();
    this.regions = regions;
    this.discoveries = discoveries;
  }

  public @NotNull MapRegions regions() {
    return this.regions;
  }

  public boolean revealed(
      final @Nullable UUID viewer, final boolean seesAll, final @NotNull MapRegion region) {
    return seesAll || !region.fog().fogged() || discoveredAt(viewer, region) > 0L;
  }

  public boolean revealed(
      final @Nullable UUID viewer, final boolean seesAll, final @NotNull UUID region) {
    final @Nullable MapRegion found = this.regions.get(region);
    return found != null && revealed(viewer, seesAll, found);
  }

  public long discoveredAt(final @Nullable UUID viewer, final @NotNull MapRegion region) {
    return viewer != null && region.discoverable() && region.fog().fogged()
        ? this.discoveries.discoveredAt(viewer, region)
        : 0L;
  }
}
