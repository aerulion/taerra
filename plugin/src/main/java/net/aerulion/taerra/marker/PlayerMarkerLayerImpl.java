package net.aerulion.taerra.marker;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.PlayerMarkerLayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PlayerMarkerLayerImpl(
    @NotNull Plugin owner,
    @NotNull String id,
    @NotNull String label,
    boolean shownByDefault,
    @NotNull Function<UUID, ? extends Collection<? extends Marker>> provider,
    @NotNull Consumer<UUID> onRefresh)
    implements PlayerMarkerLayer {

  @Override
  public void refresh(final @NotNull UUID player) {
    this.onRefresh.accept(player);
  }

  public @NotNull List<Marker> markers(final @NotNull UUID player) {
    try {
      final @Nullable Collection<? extends Marker> markers = this.provider.apply(player);
      return markers == null ? List.of() : List.copyOf(markers);
    } catch (final RuntimeException e) {
      this.owner
          .getLogger()
          .log(Level.WARNING, "The TAERRA player layer " + this.id + " failed for " + player, e);
      return List.of();
    }
  }
}
