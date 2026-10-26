package net.aerulion.taerra.util;

import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class Events {

  private Events() {
    super();
  }

  public static void fire(final @NotNull Plugin plugin, final @NotNull Event event) {
    final @NotNull Server server = plugin.getServer();
    if (server.isPrimaryThread()) {
      server.getPluginManager().callEvent(event);
    } else if (plugin.isEnabled()) {
      server.getScheduler().runTask(plugin, () -> server.getPluginManager().callEvent(event));
    }
  }
}
