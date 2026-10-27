package net.aerulion.taerra.config;

import static net.aerulion.taerra.config.ConfigEditor.LAYER;
import static net.aerulion.taerra.config.ConfigEditor.LAYER_ICON;
import static net.aerulion.taerra.config.ConfigEditor.LAYER_SCALE;
import static net.aerulion.taerra.config.ConfigEditor.LIMIT;
import static net.aerulion.taerra.config.ConfigEditor.ORDER;
import static net.aerulion.taerra.config.ConfigEditor.SETTING;
import static net.aerulion.taerra.config.ConfigEditor.VIEWS;
import static net.aerulion.taerra.config.ConfigEditor.WORLD;
import static net.aerulion.taerra.config.ConfigEditor.worldField;
import static net.aerulion.taerra.config.ConfigEditor.worldOf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.aerulion.taerra.model.MapViews;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.util.Names;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SettingsPlan {

  private SettingsPlan() {
    super();
  }

  static @NotNull Result plan(
      final long base,
      final @NotNull Map<String, Object> changes,
      final @NotNull Map<String, Object> current,
      final @NotNull SettingsRevisions revisions,
      final @NotNull Server server) {
    final @NotNull Map<String, Object> next = new LinkedHashMap<>();
    for (final Map.@NotNull Entry<String, Object> change : changes.entrySet()) {
      final @Nullable Object value =
          current.containsKey(change.getKey())
              ? normalize(change.getKey(), change.getValue(), current, server)
              : null;
      if (value == null) {
        return new Invalid(change.getKey());
      }
      next.put(change.getKey(), value);
    }
    final @NotNull List<ConfigEditor.Conflict> conflicts = new ArrayList<>();
    next.forEach(
        (field, value) -> {
          if (!Objects.equals(current.get(field), value) && revisions.changedSince(field, base)) {
            conflicts.add(
                new ConfigEditor.Conflict(field, current.get(field), revisions.author(field)));
          }
        });
    if (!conflicts.isEmpty()) {
      return new Conflicted(List.copyOf(conflicts));
    }
    next.entrySet()
        .removeIf(entry -> Objects.equals(current.get(entry.getKey()), entry.getValue()));
    final @NotNull List<String> limitFields =
        next.keySet().stream()
            .filter(field -> field.startsWith(WORLD) && field.endsWith(":" + LIMIT))
            .toList();
    final @NotNull List<String> viewFields =
        next.keySet().stream()
            .filter(field -> field.startsWith(WORLD) && field.endsWith(":" + VIEWS))
            .toList();
    final @NotNull List<String> renderFields = new ArrayList<>(limitFields);
    viewFields.stream()
        .filter(
            field ->
                enabled(next, worldOf(field), server)
                    && !server
                        .views(worldOf(field))
                        .views()
                        .containsAll(views(next.get(field)).views()))
        .forEach(renderFields::add);
    if (renderFields.size() > 1) {
      return new Invalid(renderFields.get(1));
    }
    if (!viewFields.isEmpty() && server.rendering()) {
      return new Busy(viewFields.getFirst());
    }
    for (final @NotNull String field : limitFields) {
      if (!enabled(next, worldOf(field), server)) {
        return new Invalid(field);
      }
      if (server.rendering()) {
        return new Busy(field);
      }
    }
    return new Ready(next, limitFields, viewFields);
  }

  static @Nullable Worlds worlds(
      final @NotNull Ready ready,
      final @NotNull List<String> enabledWorlds,
      final @NotNull Set<String> privateWorlds,
      final @NotNull Set<String> privatePlayers,
      final @NotNull Map<String, String> worldNames) {
    final @NotNull List<String> enabled = new ArrayList<>(enabledWorlds);
    final @NotNull Set<String> hidden = new LinkedHashSet<>(privateWorlds);
    final @NotNull Set<String> hiddenPlayers = new LinkedHashSet<>(privatePlayers);
    final @NotNull Map<String, String> names = new HashMap<>(worldNames);
    boolean worlds = false;
    for (final Map.@NotNull Entry<String, Object> change : ready.next().entrySet()) {
      final @NotNull String field = change.getKey();
      if (!field.startsWith(WORLD)
          || ready.limitFields().contains(field)
          || ready.viewFields().contains(field)) {
        continue;
      }
      worlds = true;
      final @NotNull String world = worldOf(field);
      switch (field.substring(field.lastIndexOf(':') + 1)) {
        case "enabled" -> toggle(enabled, world, change.getValue());
        case "private" -> toggle(hidden, world, change.getValue());
        case "players" -> toggle(hiddenPlayers, world, change.getValue());
        default -> {
          if (change.getValue() instanceof final @NotNull String name && !name.isEmpty()) {
            names.put(world, name);
          } else {
            names.remove(world);
          }
        }
      }
    }
    @NotNull List<String> order = List.copyOf(enabled);
    if (ready.next().get(ORDER) instanceof final @NotNull List<?> wanted) {
      worlds = true;
      order = ConfigEditor.order(wanted.stream().map(String::valueOf).toList(), enabled);
    }
    return worlds ? new Worlds(order, hidden, hiddenPlayers, names) : null;
  }

  static @Nullable String worldName(final @NotNull String worldName, final @NotNull String given) {
    final @Nullable NamespacedKey key = NamespacedKey.fromString(worldName);
    if (key == null || given.isBlank() || given.strip().equals(WorldKeys.displayName(key))) {
      return key == null ? null : "";
    }
    return worldName.contains(".") ? null : Names.of(given);
  }

  static @NotNull MapViews views(final @Nullable Object normalized) {
    return Objects.requireNonNullElse(MapViews.parse((String) normalized), MapViews.BOTH);
  }

  private static void toggle(
      final @NotNull Collection<String> worlds,
      final @NotNull String world,
      final @Nullable Object on) {
    worlds.remove(world);
    if (Boolean.TRUE.equals(on)) {
      worlds.add(world);
    }
  }

  private static boolean enabled(
      final @NotNull Map<String, Object> next,
      final @NotNull String world,
      final @NotNull Server server) {
    return next.get(worldField(world, "enabled")) instanceof final Boolean on
        ? on
        : server.worldEnabled(world);
  }

  private static @Nullable Object normalize(
      final @NotNull String field,
      final @Nullable Object value,
      final @NotNull Map<String, Object> current,
      final @NotNull Server server) {
    if (value == null) {
      return null;
    }
    if (field.startsWith(SETTING)) {
      final @Nullable Setting setting = ConfigEditor.setting(field.substring(SETTING.length()));
      return setting == null || !setting.editable() ? null : setting.valid(value);
    }
    if (field.equals(ORDER)) {
      if (!(value instanceof final @NotNull List<?> list)) {
        return null;
      }
      final @NotNull List<String> order = new ArrayList<>();
      for (final @Nullable Object world : list) {
        if (!(world instanceof final @NotNull String id)
            || order.contains(id)
            || !current.containsKey(worldField(id, "enabled"))) {
          return null;
        }
        order.add(id);
      }
      return List.copyOf(order);
    }
    if (field.startsWith(WORLD)) {
      final @NotNull String world = worldOf(field);
      if (field.endsWith(":" + LIMIT)) {
        return server.worldLoaded(world) ? ConfigEditor.normalizeLimit(value) : null;
      }
      if (field.endsWith(":" + VIEWS)) {
        return server.worldLoaded(world)
                && value instanceof final @NotNull String id
                && MapViews.parse(id) != null
            ? id
            : null;
      }
      return field.endsWith(":name")
          ? value instanceof final @NotNull String name ? worldName(world, name) : null
          : value instanceof Boolean ? value : null;
    }
    if (field.startsWith(LAYER_ICON)) {
      return value instanceof final @NotNull String icon
              && (icon.isEmpty() || server.iconExists(icon))
          ? icon
          : null;
    }
    if (field.startsWith(LAYER_SCALE)) {
      return value instanceof final @NotNull String text
          ? text.isEmpty()
              ? text
              : LayerScale.parse(text) instanceof final @NotNull Integer scale
                  ? LayerScale.format(scale)
                  : null
          : null;
    }
    return field.startsWith(LAYER) && value instanceof Boolean ? value : null;
  }

  interface Server {

    boolean worldLoaded(@NotNull String world);

    boolean worldEnabled(@NotNull String world);

    @NotNull
    MapViews views(@NotNull String world);

    boolean iconExists(@NotNull String icon);

    boolean rendering();
  }

  sealed interface Result permits Invalid, Conflicted, Busy, Ready {}

  record Invalid(@NotNull String field) implements Result {}

  record Conflicted(@NotNull List<ConfigEditor.Conflict> conflicts) implements Result {}

  record Busy(@NotNull String field) implements Result {}

  record Ready(
      @NotNull Map<String, Object> next,
      @NotNull List<String> limitFields,
      @NotNull List<String> viewFields)
      implements Result {}

  record Worlds(
      @NotNull List<String> order,
      @NotNull Set<String> hidden,
      @NotNull Set<String> hiddenPlayers,
      @NotNull Map<String, String> names) {}
}
