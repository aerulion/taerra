package net.aerulion.taerra.marker;

import java.lang.reflect.Proxy;
import org.bukkit.plugin.Plugin;
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
                  case "equals" -> proxy == args[0];
                  case "hashCode" -> System.identityHashCode(proxy);
                  default -> throw new UnsupportedOperationException(method.getName());
                });
  }
}
