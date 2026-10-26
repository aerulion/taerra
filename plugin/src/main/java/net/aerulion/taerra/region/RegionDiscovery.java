package net.aerulion.taerra.region;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.aerulion.taerra.api.event.region.RegionDiscoverEvent;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.util.Events;
import net.aerulion.taerra.web.auth.ModHandshake;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RegionDiscovery {

  private static final long INTERVAL_TICKS = 10L;

  private final @NotNull Plugin plugin;
  private final @NotNull TaerraConfig config;
  private final @NotNull MapRegions regions;
  private final @NotNull RegionDiscoveries discoveries;
  private final @NotNull ModHandshake mods;
  private final @NotNull BiConsumer<UUID, String> discovered;

  public RegionDiscovery(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig config,
      final @NotNull MapRegions regions,
      final @NotNull RegionDiscoveries discoveries,
      final @NotNull ModHandshake mods,
      final @NotNull BiConsumer<UUID, String> discovered) {
    super();
    this.plugin = plugin;
    this.config = config;
    this.regions = regions;
    this.discoveries = discoveries;
    this.mods = mods;
    this.discovered = discovered;
  }

  public void start() {
    Bukkit.getScheduler()
        .runTaskTimer(
            this.plugin,
            () -> {
              final @NotNull Map<UUID, MapRegion> visits = new HashMap<>();
              for (final @NotNull Player player : Bukkit.getOnlinePlayers()) {
                final @NotNull String world = WorldKeys.id(player.getWorld());
                if (!this.config.worldVisible(world, player::hasPermission)) {
                  continue;
                }
                final @NotNull Location location = player.getLocation();
                final @Nullable MapRegion region =
                    this.regions.at(world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
                if (region != null && region.discoverable()) {
                  visits.put(player.getUniqueId(), region);
                }
              }
              if (!visits.isEmpty()) {
                Bukkit.getScheduler()
                    .runTaskAsynchronously(
                        this.plugin,
                        () ->
                            visits.forEach(
                                (uuid, region) ->
                                    discover(uuid, region, RegionDiscoverEvent.Cause.ENTERED)));
              }
            },
            INTERVAL_TICKS,
            INTERVAL_TICKS);
  }

  public boolean discover(
      final @NotNull UUID uuid,
      final @NotNull MapRegion region,
      final RegionDiscoverEvent.@NotNull Cause cause) {
    if (!this.discoveries.discover(uuid, region)) {
      return false;
    }
    Bukkit.getScheduler()
        .runTask(
            this.plugin,
            () -> {
              final @Nullable Player player = Bukkit.getPlayer(uuid);
              if (player != null && !region.name().isEmpty()) {
                player.playSound(
                    player, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.UI, 1.0f, 1.0f);
                if (!this.mods.discovered(player, region.name())) {
                  player.sendMessage(Messages.discovered(region.name()));
                }
              }
              this.discovered.accept(uuid, region.name());
              if (player != null) {
                Events.fire(
                    this.plugin, new RegionDiscoverEvent(player, new RegionImpl(region), cause));
              }
            });
    return true;
  }
}
