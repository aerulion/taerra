package net.aerulion.taerra.region;

import java.util.Objects;
import java.util.UUID;
import net.aerulion.taerra.api.region.Region;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record RegionImpl(@NotNull MapRegion region) implements Region {

  @Override
  public @NotNull UUID id() {
    return this.region.id();
  }

  @Override
  public @NotNull String name() {
    return this.region.name();
  }

  @Override
  public @NotNull NamespacedKey world() {
    return Objects.requireNonNull(NamespacedKey.fromString(this.region.world()));
  }

  @Override
  public @NotNull Color color() {
    return Color.fromRGB(this.region.color());
  }

  @Override
  public net.aerulion.taerra.api.region.@NotNull RegionFog fog() {
    return net.aerulion.taerra.api.region.RegionFog.valueOf(this.region.fog().name());
  }

  @Override
  public boolean discoverable() {
    return this.region.discoverable();
  }

  @Override
  public boolean contains(final int chunkX, final int chunkZ) {
    return this.region.contains(chunkX, chunkZ);
  }

  @Override
  public boolean contains(final @NotNull Location location) {
    final @Nullable World world = location.getWorld();
    return world != null
        && world.getKey().asString().equals(this.region.world())
        && contains(location.getBlockX() >> 4, location.getBlockZ() >> 4);
  }
}
