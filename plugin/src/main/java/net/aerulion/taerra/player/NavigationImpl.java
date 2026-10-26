package net.aerulion.taerra.player;

import java.util.UUID;
import net.aerulion.taerra.api.navigation.Navigation;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.util.HexColors;
import net.aerulion.taerra.util.Names;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record NavigationImpl(@NotNull Preferences preferences) implements Navigation {

  @Override
  public void set(
      final @NotNull UUID player,
      final @NotNull String label,
      final @NotNull Location target,
      final @NotNull Color color) {
    final @Nullable String name = Names.of(label);
    final @Nullable World world = target.getWorld();
    if (name == null) {
      throw new IllegalArgumentException("Not a valid navigation label: " + label);
    }
    if (world == null) {
      throw new IllegalArgumentException("The navigation target has no world");
    }
    this.preferences.navigate(
        player,
        new net.aerulion.taerra.player.Navigation(
            name,
            WorldKeys.id(world),
            target.getX(),
            target.getY(),
            target.getZ(),
            HexColors.format(color.asRGB()),
            null));
  }

  @Override
  public void clear(final @NotNull UUID player) {
    this.preferences.navigate(player, null);
  }
}
