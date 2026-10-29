package net.aerulion.taerra.web.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import net.aerulion.taerra.marker.TestPlugins;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MapPlayersImplTest {

  private static @NotNull Player player(final @NotNull UUID uuid) {
    return (Player)
        Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getUniqueId" -> uuid;
                  case "getGameMode" -> GameMode.SURVIVAL;
                  case "hasPotionEffect", "hasPermission" -> false;
                  case "getMetadata" -> List.of();
                  default -> throw new UnsupportedOperationException(method.getName());
                });
  }

  @Test
  @DisplayName("a player stays hidden while any plugin hides them, until it shows them or stops")
  void hiddenWhileAnyPluginHides() {
    final @NotNull MapPlayersImpl players = new MapPlayersImpl();
    final @NotNull Plugin vanish = TestPlugins.named("Vanish");
    final @NotNull Plugin arena = TestPlugins.named("Arena");
    final @NotNull Player alex = player(UUID.randomUUID());
    assertFalse(players.hidden(alex));

    players.hide(vanish, alex.getUniqueId());
    players.hide(arena, alex.getUniqueId());
    players.show(vanish, alex.getUniqueId());
    assertTrue(players.hidden(alex));

    players.forget(arena);
    assertFalse(players.hidden(alex));
  }

  @Test
  @DisplayName("spectators are not hidden, unlike invisible players")
  void spectatorsAreListed() {
    final @NotNull MapPlayersImpl players = new MapPlayersImpl();
    assertFalse(players.hidden(spectator(UUID.randomUUID())));
  }

  private static @NotNull Player spectator(final @NotNull UUID uuid) {
    return (Player)
        Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getUniqueId" -> uuid;
                  case "getGameMode" -> GameMode.SPECTATOR;
                  case "hasPotionEffect", "hasPermission" -> false;
                  case "getMetadata" -> List.of();
                  default -> throw new UnsupportedOperationException(method.getName());
                });
  }
}
