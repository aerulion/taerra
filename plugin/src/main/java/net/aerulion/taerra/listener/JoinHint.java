package net.aerulion.taerra.listener;

import java.util.concurrent.TimeUnit;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.config.WebSettings;
import net.aerulion.taerra.web.auth.ModHandshake;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class JoinHint implements Listener {

  private static final long DELAY_TICKS = 100L;

  private final @NotNull Plugin plugin;
  private final @NotNull TaerraConfig config;
  private final @NotNull WebSettings web;
  private final @NotNull NamespacedKey lastShown;

  public JoinHint(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig config,
      final @NotNull WebSettings web) {
    super();
    this.plugin = plugin;
    this.config = config;
    this.web = web;
    this.lastShown = new NamespacedKey(plugin, "join_hint");
  }

  static boolean due(final long lastShown, final long now, final int intervalDays) {
    return intervalDays > 0 && now - lastShown >= TimeUnit.DAYS.toMillis(intervalDays);
  }

  @EventHandler
  public void onJoin(final @NotNull PlayerJoinEvent event) {
    final @NotNull Player player = event.getPlayer();
    player.getScheduler().runDelayed(this.plugin, task -> show(player), null, DELAY_TICKS);
  }

  private void show(final @NotNull Player player) {
    if (this.config.enabledWorlds().isEmpty()
        || player.getListeningPluginChannels().contains(ModHandshake.CHANNEL)) {
      return;
    }
    final long now = System.currentTimeMillis();
    final @NotNull PersistentDataContainer data = player.getPersistentDataContainer();
    if (!due(
        data.getOrDefault(this.lastShown, PersistentDataType.LONG, 0L),
        now,
        this.plugin.getConfig().getInt("join-hint.interval-days", 7))) {
      return;
    }
    data.set(this.lastShown, PersistentDataType.LONG, now);
    player.sendMessage(Messages.joinHint(this.web.publicUrl()));
  }
}
