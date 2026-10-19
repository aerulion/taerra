package net.aerulion.taerra.mod.navigation;

import net.aerulion.taerra.mod.util.Colors;
import net.aerulion.taerra.mod.util.Toasts;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.NavigationTarget;
import net.aerulion.taerra.mod.web.model.Player;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Navigation {

  private static final String OVERWORLD = "minecraft:overworld";
  private static final String NETHER = "minecraft:the_nether";
  private static final double ARRIVAL = 4;

  private @Nullable Target target;
  private @Nullable WebMap map;
  private long seen;

  public static double scale(final @NotNull String from, final @NotNull String to) {
    if (from.equals(to)) {
      return 1;
    }
    if (from.equals(OVERWORLD) && to.equals(NETHER)) {
      return 1 / 8.0;
    }
    if (from.equals(NETHER) && to.equals(OVERWORLD)) {
      return 8;
    }
    return Double.NaN;
  }

  public @Nullable Target target() {
    return this.target;
  }

  public void start(final @NotNull Target target) {
    this.target = target;
    if (this.map != null) {
      this.map.navigate(target.shared());
    }
  }

  public void stop() {
    this.target = null;
    if (this.map != null) {
      this.map.navigate(null);
    }
  }

  public void forget() {
    this.target = null;
    this.map = null;
  }

  public void tick(
      final @Nullable WebMap map, final @Nullable LocalPlayer self, final @NotNull String world) {
    if (map != this.map) {
      this.map = map;
      this.seen = 0;
    }
    if (map != null && map.navigationVersion() != this.seen) {
      this.seen = map.navigationVersion();
      final @Nullable NavigationTarget shared = map.navigation();
      this.target = shared == null ? null : Target.of(shared);
    }
    final @Nullable Target target = this.target;
    if (target == null) {
      return;
    }
    if (target.player() != null && map != null) {
      final @Nullable Player player = map.player(target.player());
      if (player != null && player.located()) {
        this.target = target.at(player.world(), player.x(), player.y(), player.z());
      }
    }
    final @NotNull Target now = this.target;
    if (self != null
        && now.world().equals(world)
        && Math.hypot(now.x() - self.getX(), now.z() - self.getZ()) < ARRIVAL) {
      stop();
      Toasts.show(
          Component.translatable("taerra.navigation.arrived"), Component.literal(now.label()));
    }
  }

  public record Target(
      String label,
      String world,
      double x,
      double y,
      double z,
      int color,
      @Nullable String player) {

    static @NotNull Target of(final @NotNull NavigationTarget shared) {
      return new Target(
          shared.label(),
          shared.world(),
          shared.x(),
          shared.y() == null ? Double.NaN : shared.y(),
          shared.z(),
          Colors.argb(shared.color()),
          shared.player());
    }

    public double @Nullable [] in(final @NotNull String world) {
      final double scale = scale(this.world, world);
      return Double.isNaN(scale)
          ? null
          : new double[] {this.x * scale, scale == 1 ? this.y : Double.NaN, this.z * scale};
    }

    @NotNull
    NavigationTarget shared() {
      return new NavigationTarget(
          this.label,
          this.world,
          this.x,
          Double.isNaN(this.y) ? null : this.y,
          this.z,
          Colors.hex(this.color & 0xFFFFFF),
          this.player);
    }

    @NotNull
    Target at(final String world, final double x, final double y, final double z) {
      return new Target(this.label, world, x, y, z, this.color, this.player);
    }
  }
}
