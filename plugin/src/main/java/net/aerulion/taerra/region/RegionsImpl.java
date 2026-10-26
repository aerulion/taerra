package net.aerulion.taerra.region;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.aerulion.taerra.api.region.Region;
import net.aerulion.taerra.api.region.Regions;
import net.aerulion.taerra.model.WorldKeys;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record RegionsImpl(
    @NotNull MapRegions regions,
    @NotNull RegionDiscoveries discoveries,
    @NotNull BiPredicate<UUID, MapRegion> discover)
    implements Regions {

  @Override
  public @NotNull Collection<Region> all() {
    return this.regions.all().stream().<Region>map(RegionImpl::new).toList();
  }

  @Override
  public @NotNull Collection<Region> in(final @NotNull World world) {
    return this.regions.in(WorldKeys.id(world)).stream().<Region>map(RegionImpl::new).toList();
  }

  @Override
  public @Nullable Region get(final @NotNull UUID id) {
    final @Nullable MapRegion region = this.regions.get(id);
    return region == null ? null : new RegionImpl(region);
  }

  @Override
  public @Nullable Region at(final @NotNull Location location) {
    final @Nullable World world = location.getWorld();
    final @Nullable MapRegion region =
        world == null
            ? null
            : this.regions.at(
                WorldKeys.id(world), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    return region == null ? null : new RegionImpl(region);
  }

  @Override
  public boolean discovered(final @NotNull UUID player, final @NotNull Region region) {
    return discoveredAt(player, region) != null;
  }

  @Override
  public @Nullable Instant discoveredAt(final @NotNull UUID player, final @NotNull Region region) {
    final @Nullable MapRegion current = this.regions.get(region.id());
    final long at = current == null ? 0L : this.discoveries.discoveredAt(player, current);
    return at > 0L ? Instant.ofEpochMilli(at) : null;
  }

  @Override
  public boolean discover(final @NotNull UUID player, final @NotNull Region region) {
    final @Nullable MapRegion current = this.regions.get(region.id());
    return current != null && current.discoverable() && this.discover.test(player, current);
  }
}
