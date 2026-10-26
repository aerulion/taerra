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

  private RegionDiscovery() {
    super();
  }

  public static void start(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig config,
      final @NotNull MapRegions regions,
      final @NotNull RegionDiscoveries discoveries,
      final @NotNull ModHandshake mods,
      final @NotNull BiConsumer<UUID, String> discovered) {
    Bukkit.getScheduler()
        .runTaskTimer(
            plugin,
            () -> {
              final @NotNull Map<UUID, MapRegion> visits = new HashMap<>();
              for (final @NotNull Player player : Bukkit.getOnlinePlayers()) {
                final @NotNull String world = WorldKeys.id(player.getWorld());
                if (!config.worldVisible(world, player::hasPermission)) {
                  continue;
                }
                final @NotNull Location location = player.getLocation();
                final @Nullable MapRegion region =
                    regions.at(world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
                if (region != null && region.discoverable()) {
                  visits.put(player.getUniqueId(), region);
                }
              }
              if (!visits.isEmpty()) {
                Bukkit.getScheduler()
                    .runTaskAsynchronously(
                        plugin, () -> discover(plugin, discoveries, mods, discovered, visits));
              }
            },
            INTERVAL_TICKS,
            INTERVAL_TICKS);
  }

  private static void discover(
      final @NotNull Plugin plugin,
      final @NotNull RegionDiscoveries discoveries,
      final @NotNull ModHandshake mods,
      final @NotNull BiConsumer<UUID, String> discovered,
      final @NotNull Map<UUID, MapRegion> visits) {
    visits.forEach(
        (uuid, region) -> {
          if (!discoveries.discover(uuid, region)) {
            return;
          }
          Bukkit.getScheduler()
              .runTask(
                  plugin,
                  () -> {
                    final @Nullable Player player = Bukkit.getPlayer(uuid);
                    if (player != null && !region.name().isEmpty()) {
                      player.playSound(
                          player, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.UI, 1.0f, 1.0f);
                      if (!mods.discovered(player, region.name())) {
                        player.sendMessage(Messages.discovered(region.name()));
                      }
                    }
                    discovered.accept(uuid, region.name());
                    if (player != null) {
                      Events.fire(plugin, new RegionDiscoverEvent(player, new RegionImpl(region)));
                    }
                  });
        });
  }
}
