package net.aerulion.taerra.marker;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;
import org.bukkit.Server;
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
                (proxy, method, args) -> {
                  if (!method.getName().equals("getPlugins")) {
                    throw new UnsupportedOperationException(method.getName());
                  }
                  return enabled;
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
                  case "getResource" ->
                      TestPlugins.class.getClassLoader().getResourceAsStream((String) args[0]);
                  case "equals" -> proxy == args[0];
                  case "hashCode" -> System.identityHashCode(proxy);
                  default -> throw new UnsupportedOperationException(method.getName());
                });
  }
}
