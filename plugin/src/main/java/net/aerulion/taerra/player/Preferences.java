package net.aerulion.taerra.player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Preferences {

  private static final int MAX_LAYERS = 256;
  private static final int MAX_LAYER_ID = 128;

  private final @NotNull PlayerFiles files;
  private @NotNull BiConsumer<UUID, Integer> waypointColorChanged = (player, rgb) -> {};

  public Preferences(final @NotNull PlayerFiles files) {
    super();
    this.files = files;
  }

  private static boolean valid(final @NotNull Collection<String> ids) {
    return ids.stream().allMatch(id -> !id.isEmpty() && id.length() <= MAX_LAYER_ID);
  }

  public @Nullable Navigation navigation(final @NotNull UUID player) {
    return this.files.get(player).navigation();
  }

  public void navigate(final @NotNull UUID player, final @Nullable Navigation target) {
    this.files.update(
        player,
        data -> Objects.equals(data.navigation(), target) ? data : data.withNavigation(target));
  }

  public @Nullable Integer waypointColor(final @NotNull UUID player) {
    return this.files.get(player).waypointColor();
  }

  public void onWaypointColor(final @NotNull BiConsumer<UUID, Integer> changed) {
    this.waypointColorChanged = changed;
  }

  public void rememberWaypointColor(final @NotNull UUID player, final int rgb) {
    if (Objects.equals(this.files.get(player).waypointColor(), rgb)) {
      return;
    }
    this.files.update(player, data -> data.withWaypointColor(rgb));
    this.waypointColorChanged.accept(player, rgb);
  }

  public @NotNull Map<String, Boolean> layers(final @NotNull UUID player) {
    return this.files.get(player).layers();
  }

  public @Nullable Map<String, Boolean> changeLayers(
      final @NotNull UUID player,
      final @NotNull Collection<String> show,
      final @NotNull Collection<String> hide,
      final @NotNull Collection<String> reset) {
    if (!valid(show) || !valid(hide) || !valid(reset)) {
      return null;
    }
    final @NotNull AtomicReference<Map<String, Boolean>> result = new AtomicReference<>();
    this.files.update(
        player,
        data -> {
          final @NotNull Map<String, Boolean> layers = new HashMap<>(data.layers());
          reset.forEach(layers::remove);
          show.forEach(id -> layers.put(id, true));
          hide.forEach(id -> layers.put(id, false));
          if (layers.size() > MAX_LAYERS) {
            return data;
          }
          result.set(Map.copyOf(layers));
          return layers.equals(data.layers()) ? data : data.withLayers(layers);
        });
    return result.get();
  }
}
