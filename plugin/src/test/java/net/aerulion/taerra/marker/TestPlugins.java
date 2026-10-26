package net.aerulion.taerra.marker;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;

public final class TestPlugins {

  private TestPlugins() {
    super();
  }

  public static @NotNull Plugin named(final @NotNull String name) {
    return (Plugin)
        Proxy.newProxyInstance(
            Plugin.class.getClassLoader(),
            new Class<?>[] {Plugin.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getName", "toString" -> name;
                  case "isEnabled" -> true;
                  case "equals" -> proxy == args[0];
                  case "hashCode" -> System.identityHashCode(proxy);
                  default -> throw new UnsupportedOperationException(method.getName());
                });
  }

  public static @NotNull Plugin in(
      final @NotNull File dataFolder, final @NotNull Plugin... enabled) {
    return in(dataFolder, new ArrayList<>(), enabled);
  }

  public static @NotNull Plugin in(
      final @NotNull File dataFolder,
      final @NotNull List<Event> fired,
      final @NotNull Plugin... enabled) {
    final @NotNull BukkitScheduler scheduler =
        (BukkitScheduler)
            Proxy.newProxyInstance(
                BukkitScheduler.class.getClassLoader(),
                new Class<?>[] {BukkitScheduler.class},
                (proxy, method, args) -> {
                  if (!method.getName().equals("runTaskAsynchronously")) {
                    throw new UnsupportedOperationException(method.getName());
                  }
                  ((Runnable) args[1]).run();
                  return null;
                });
    final @NotNull PluginManager plugins =
        (PluginManager)
            Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(),
                new Class<?>[] {PluginManager.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "getPlugins" -> enabled;
                      case "callEvent" -> fired.add((Event) args[0]);
                      default -> throw new UnsupportedOperationException(method.getName());
                    });
    final @NotNull Server server =
        (Server)
            Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[] {Server.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "getScheduler" -> scheduler;
                      case "getPluginManager" -> plugins;
                      case "isPrimaryThread" -> true;
                      case "getOfflinePlayer" -> player((UUID) args[0]);
                      default -> throw new UnsupportedOperationException(method.getName());
                    });
    return (Plugin)
        Proxy.newProxyInstance(
            Plugin.class.getClassLoader(),
            new Class<?>[] {Plugin.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getName", "toString" -> "TAERRA";
                  case "getDataFolder" -> dataFolder;
                  case "getLogger" -> Logger.getLogger("test");
                  case "getServer" -> server;
                  case "isEnabled" -> true;
                  case "getResource" ->
                      TestPlugins.class.getClassLoader().getResourceAsStream((String) args[0]);
                  case "equals" -> proxy == args[0];
                  case "hashCode" -> System.identityHashCode(proxy);
                  default -> throw new UnsupportedOperationException(method.getName());
                });
  }

  public static @NotNull OfflinePlayer player(final @NotNull UUID uuid) {
    return (OfflinePlayer)
        Proxy.newProxyInstance(
            OfflinePlayer.class.getClassLoader(),
            new Class<?>[] {OfflinePlayer.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getUniqueId" -> uuid;
                  case "toString" -> uuid.toString();
                  case "equals" ->
                      args[0] instanceof final OfflinePlayer other
                          && uuid.equals(other.getUniqueId());
                  case "hashCode" -> uuid.hashCode();
                  default -> throw new UnsupportedOperationException(method.getName());
                });
  }
}
