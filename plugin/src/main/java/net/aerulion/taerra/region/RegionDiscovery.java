package net.aerulion.taerra.region;

import java.util.UUID;
import java.util.function.BiConsumer;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.web.auth.ModHandshake;
import org.bukkit.Bukkit;
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
      final @NotNull MapRegions regions,
      final @NotNull RegionDiscoveries discoveries,
      final @NotNull ModHandshake mods,
      final @NotNull BiConsumer<UUID, String> discovered) {
    Bukkit.getScheduler()
        .runTaskTimer(
            plugin,
            () -> {
              for (final @NotNull Player player : Bukkit.getOnlinePlayers()) {
                final @Nullable MapRegion region =
                    regions.at(
                        WorldKeys.id(player.getWorld()),
                        player.getLocation().getBlockX() >> 4,
                        player.getLocation().getBlockZ() >> 4);
                if (region == null
                    || !region.discoverable()
                    || !discoveries.discover(player.getUniqueId(), region.id())) {
                  continue;
                }
                if (!region.name().isEmpty()) {
                  player.playSound(
                      player, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.UI, 1.0f, 1.0f);
                  if (!mods.discovered(player, region.name())) {
                    player.sendMessage(Messages.discovered(region.name()));
                  }
                }
                discovered.accept(player.getUniqueId(), region.name());
              }
            },
            INTERVAL_TICKS,
            INTERVAL_TICKS);
  }
}
