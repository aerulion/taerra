package net.aerulion.taerra.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.limit.WorldLimits;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.model.WorldLimit;
import net.aerulion.taerra.rendering.RenderService;
import net.aerulion.taerra.util.Names;
import net.aerulion.taerra.waypoint.Waypoints;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ConfigEditor {

  public static final @NotNull List<Setting> SETTINGS =
      List.of(
          Setting.number("web.port", 1, 65535, false),
          Setting.text("web.host", false),
          Setting.text("web.public-url", false),
          Setting.text("web.name", true),
          Setting.number("join-hint.interval-days", 0, Integer.MAX_VALUE, true),
          Setting.number(
              "rendering.render-threads", 0, Runtime.getRuntime().availableProcessors(), true),
          Setting.number("rendering.update-interval", 0, Integer.MAX_VALUE, true),
          Setting.number("cache.max-tiles", 1, 50_000, true),
          Setting.number("cache.max-chunks", 256, 131_072, true),
          Setting.toggle("markers.spawn.enabled"),
          Setting.toggle("markers.spawn.shown-by-default"),
          Setting.toggle("markers.worldguard.enabled"),
          Setting.toggle("markers.worldguard.shown-by-default"),
          Setting.toggle("markers.worldborder.enabled"),
          Setting.toggle("markers.worldborder.shown-by-default"),
          Setting.toggle("markers.loaded-chunks.enabled"),
          Setting.toggle("markers.loaded-chunks.shown-by-default"));

  public static final @NotNull String SETTING = "setting:";
  public static final @NotNull String WORLD = "world:";
  public static final @NotNull String LAYER = "layer:";
  public static final @NotNull String ORDER = "order";
  public static final @NotNull String LIMIT = "limit";
  private static final long TRACK_INTERVAL_TICKS = 20L;
  private static final int MAX_COORDINATE = 30_000_000;
  private static final @NotNull Pattern CONTROL = Pattern.compile("\\p{Cntrl}");

  private final @NotNull Plugin plugin;
  private final @NotNull TaerraConfig config;
  private final @NotNull MarkerRegistry markers;
  private final @NotNull Waypoints waypoints;
  private final @NotNull WorldLimits limits;
  private final @NotNull RenderService renders;
  private final @NotNull Consumer<Set<String>> applied;
  private final @NotNull Runnable layersChanged;
  private final @NotNull LongConsumer published;
  private final @NotNull SettingsRevisions revisions = new SettingsRevisions();

  public ConfigEditor(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig config,
      final @NotNull MarkerRegistry markers,
      final @NotNull Waypoints waypoints,
      final @NotNull WorldLimits limits,
      final @NotNull RenderService renders,
      final @NotNull Consumer<Set<String>> applied,
      final @NotNull Runnable layersChanged,
      final @NotNull LongConsumer published) {
    super();
    this.plugin = plugin;
    this.config = config;
    this.markers = markers;
    this.waypoints = waypoints;
    this.limits = limits;
    this.renders = renders;
    this.applied = applied;
    this.layersChanged = layersChanged;
    this.published = published;
  }

  public static @Nullable Setting setting(final @NotNull String key) {
    return SETTINGS.stream().filter(setting -> setting.key().equals(key)).findFirst().orElse(null);
  }

  public static @NotNull Effect effect(final @NotNull String key) {
    return key.equals("web.port") || key.equals("web.host") ? Effect.WEB_RESTARTED : Effect.APPLIED;
  }

  public static @NotNull String worldField(
      final @NotNull String world, final @NotNull String part) {
    return WORLD + world + ":" + part;
  }

  static @NotNull List<String> order(
      final @NotNull List<String> wanted, final @NotNull List<String> enabled) {
    final @NotNull Set<String> on = new HashSet<>(enabled);
    final @NotNull LinkedHashSet<String> order = new LinkedHashSet<>();
    wanted.stream().filter(on::contains).forEach(order::add);
    order.addAll(enabled);
    return List.copyOf(order);
  }

  private static @NotNull String worldOf(final @NotNull String field) {
    return field.substring(WORLD.length(), field.lastIndexOf(':'));
  }

  static @NotNull Map<String, Object> limitValue(final @Nullable WorldLimit limit) {
    final @NotNull Map<String, Object> value = new LinkedHashMap<>();
    value.put("shape", limit == null ? "none" : limit.shape().name().toLowerCase(Locale.ROOT));
    if (limit != null) {
      value.put("minX", limit.minX());
      value.put("minZ", limit.minZ());
      value.put("maxX", limit.maxX());
      value.put("maxZ", limit.maxZ());
    }
    return value;
  }

  static @Nullable Map<String, Object> normalizeLimit(final @Nullable Object given) {
    if (!(given instanceof final @NotNull Map<?, ?> map)
        || !(map.get("shape") instanceof final @NotNull String shape)) {
      return null;
    }
    if (shape.equals("none")) {
      return limitValue(null);
    }
    final @Nullable WorldLimit limit = limit(shape, map);
    return limit == null ? null : limitValue(limit);
  }

  private static @Nullable WorldLimit limit(
      final @NotNull String shape, final @NotNull Map<?, ?> map) {
    final int[] bounds = new int[4];
    final @NotNull String[] keys = {"minX", "minZ", "maxX", "maxZ"};
    for (int i = 0; i < keys.length; i++) {
      if (!(map.get(keys[i]) instanceof final @NotNull Number number)
          || number.doubleValue() != number.intValue()
          || Math.abs(number.intValue()) > MAX_COORDINATE) {
        return null;
      }
      bounds[i] = number.intValue();
    }
    final int width = bounds[2] - bounds[0];
    final int depth = bounds[3] - bounds[1];
    if (width < 0 || depth < 0) {
      return null;
    }
    return switch (shape) {
      case "rectangle" ->
          new WorldLimit(WorldLimit.Shape.RECTANGLE, bounds[0], bounds[1], bounds[2], bounds[3]);
      case "circle", "square" ->
          width == depth && width >= 2 && width % 2 == 0
              ? new WorldLimit(
                  WorldLimit.Shape.valueOf(shape.toUpperCase(Locale.ROOT)),
                  bounds[0],
                  bounds[1],
                  bounds[2],
                  bounds[3])
              : null;
      default -> null;
    };
  }

  private static @Nullable WorldLimit limit(final @NotNull Object normalized) {
    return normalized instanceof final @NotNull Map<?, ?> map
            && map.get("shape") instanceof final @NotNull String shape
        ? limit(shape, map)
        : null;
  }

  private static boolean same(final @Nullable Object a, final @Nullable Object b) {
    return Objects.equals(a, b);
  }

  public void start() {
    this.revisions.seed(values(), System.currentTimeMillis());
    Bukkit.getScheduler()
        .runTaskTimer(this.plugin, () -> track(null), TRACK_INTERVAL_TICKS, TRACK_INTERVAL_TICKS);
  }

  public @Nullable Object get(final @NotNull String key) {
    return this.plugin.getConfig().get(key);
  }

  public @NotNull Change set(
      final @NotNull String key, final @NotNull Object given, final @NotNull String who) {
    final @Nullable Object value = valid(key, "\"\"".equals(given) ? "" : given);
    if (value == null) {
      return new Change(Effect.REJECTED, given);
    }
    write(key, value);
    this.plugin.saveConfig();
    this.applied.accept(Set.of(key));
    track(who);
    return new Change(effect(key), value);
  }

  public void setWorldEnabled(
      final @NotNull String worldName, final boolean enabled, final @NotNull String who) {
    this.config.setWorldEnabled(this.plugin, worldName, enabled);
    track(who);
  }

  public void setWorldPrivate(
      final @NotNull String worldName, final boolean hidden, final @NotNull String who) {
    this.config.setWorldPrivate(this.plugin, worldName, hidden);
    track(who);
  }

  public void setPlayersPrivate(
      final @NotNull String worldName, final boolean hidden, final @NotNull String who) {
    this.config.setPlayersPrivate(this.plugin, worldName, hidden);
    track(who);
  }

  public boolean setWorldName(
      final @NotNull String worldName, final @NotNull String given, final @NotNull String who) {
    final @Nullable String name = worldName(worldName, given);
    if (name == null) {
      return false;
    }
    this.config.setWorldName(this.plugin, worldName, name);
    track(who);
    return true;
  }

  public boolean moveWorld(
      final @NotNull String worldName, final int position, final @NotNull String who) {
    if (!this.config.worldEnabled(worldName)) {
      return false;
    }
    final @NotNull List<String> order = new ArrayList<>(this.config.enabledWorlds());
    order.remove(worldName);
    order.add(Math.clamp(position - 1, 0, order.size()), worldName);
    this.config.setWorlds(
        this.plugin,
        order,
        Set.copyOf(this.config.privateWorlds()),
        Set.copyOf(this.config.privatePlayers()),
        this.config.worldNames());
    track(who);
    return true;
  }

  public boolean setLayerPrivate(
      final @NotNull String layerId, final boolean hidden, final @NotNull String who) {
    if (this.markers.existingLayer(layerId) == null && !this.config.layerPrivate(layerId)) {
      return false;
    }
    this.config.setLayerPrivate(this.plugin, layerId, hidden);
    this.layersChanged.run();
    track(who);
    return true;
  }

  public RenderService.@NotNull Start setLimit(
      final @NotNull World world, final @Nullable WorldLimit limit, final @NotNull String who) {
    final RenderService.@NotNull Start start = this.renders.limit(world, limit);
    if (start instanceof RenderService.Started) {
      track(who);
    }
    return start;
  }

  public @NotNull CompletableFuture<View> view() {
    return onMainThread(this::viewNow);
  }

  public @NotNull CompletableFuture<Outcome> apply(
      final long base, final @NotNull Map<String, Object> changes, final @NotNull String who) {
    return onMainThread(() -> applyNow(base, changes, who));
  }

  private void track(final @Nullable String who) {
    if (!this.revisions.track(values(), who, System.currentTimeMillis()).isEmpty()) {
      this.published.accept(this.revisions.revision());
    }
  }

  private <T> @NotNull CompletableFuture<T> onMainThread(final @NotNull Supplier<T> task) {
    final @NotNull CompletableFuture<T> result = new CompletableFuture<>();
    Bukkit.getScheduler()
        .runTask(
            this.plugin,
            () -> {
              try {
                result.complete(task.get());
              } catch (final RuntimeException e) {
                result.completeExceptionally(e);
              }
            });
    return result;
  }

  private @NotNull View viewNow() {
    track(null);
    final @NotNull List<SettingInfo> settings =
        SETTINGS.stream()
            .map(
                setting ->
                    new SettingInfo(
                        setting.key(),
                        setting.kind().name().toLowerCase(Locale.ROOT),
                        setting.editable(),
                        setting.kind() == Kind.NUMBER ? setting.min() : null,
                        setting.kind() == Kind.NUMBER ? setting.max() : null))
            .toList();
    final @NotNull List<WorldInfo> worlds =
        worldIds().stream()
            .map(
                id -> {
                  final @Nullable World world = WorldKeys.resolve(id);
                  final @Nullable NamespacedKey key = NamespacedKey.fromString(id);
                  return new WorldInfo(
                      id,
                      key == null ? id : WorldKeys.displayName(key),
                      world == null ? null : world.getEnvironment().name());
                })
            .toList();
    final @NotNull Map<String, SettingsRevisions.Author> authors = new HashMap<>();
    this.revisions
        .authors()
        .forEach(
            (field, author) -> {
              if (author.name() != null) {
                authors.put(field, author);
              }
            });
    return new View(
        this.revisions.revision(), settings, worlds, layers(), values(), Map.copyOf(authors));
  }

  private @NotNull Outcome applyNow(
      final long base, final @NotNull Map<String, Object> changes, final @NotNull String who) {
    final @NotNull Map<String, Object> current = values();
    final @NotNull Map<String, Object> next = new LinkedHashMap<>();
    for (final Map.@NotNull Entry<String, Object> change : changes.entrySet()) {
      final @Nullable Object value =
          current.containsKey(change.getKey())
              ? normalize(change.getKey(), change.getValue(), current)
              : null;
      if (value == null) {
        return new Invalid(change.getKey());
      }
      next.put(change.getKey(), value);
    }
    final @NotNull List<Conflict> conflicts = new ArrayList<>();
    next.forEach(
        (field, value) -> {
          if (!same(current.get(field), value) && this.revisions.changedSince(field, base)) {
            conflicts.add(new Conflict(field, current.get(field), this.revisions.author(field)));
          }
        });
    if (!conflicts.isEmpty()) {
      return new Conflicted(List.copyOf(conflicts), viewNow());
    }
    next.entrySet().removeIf(entry -> same(current.get(entry.getKey()), entry.getValue()));
    if (next.isEmpty()) {
      return new Applied(viewNow());
    }
    final @NotNull List<String> limitFields =
        next.keySet().stream()
            .filter(field -> field.startsWith(WORLD) && field.endsWith(":" + LIMIT))
            .toList();
    if (limitFields.size() > 1) {
      return new Invalid(limitFields.get(1));
    }
    for (final @NotNull String field : limitFields) {
      final @NotNull String world = worldOf(field);
      if (!(next.get(worldField(world, "enabled")) instanceof final Boolean on
          ? on
          : this.config.worldEnabled(world))) {
        return new Invalid(field);
      }
      if (this.renders.rendering() != null) {
        return new Busy(field, viewNow());
      }
    }

    final @NotNull Set<String> written = new LinkedHashSet<>();
    next.forEach(
        (field, value) -> {
          if (field.startsWith(SETTING)) {
            write(field.substring(SETTING.length()), value);
            written.add(field.substring(SETTING.length()));
          }
        });
    if (!written.isEmpty()) {
      this.plugin.saveConfig();
      this.applied.accept(written);
    }

    final @NotNull List<String> enabled = new ArrayList<>(this.config.enabledWorlds());
    final @NotNull Set<String> hidden = new LinkedHashSet<>(this.config.privateWorlds());
    final @NotNull Set<String> hiddenPlayers = new LinkedHashSet<>(this.config.privatePlayers());
    final @NotNull Map<String, String> names = new HashMap<>(this.config.worldNames());
    boolean worlds = false;
    for (final Map.@NotNull Entry<String, Object> change : next.entrySet()) {
      final @NotNull String field = change.getKey();
      if (!field.startsWith(WORLD) || limitFields.contains(field)) {
        continue;
      }
      worlds = true;
      final @NotNull String world = worldOf(field);
      switch (field.substring(field.lastIndexOf(':') + 1)) {
        case "enabled" -> {
          enabled.remove(world);
          if (Boolean.TRUE.equals(change.getValue())) {
            enabled.add(world);
          }
        }
        case "private" -> {
          hidden.remove(world);
          if (Boolean.TRUE.equals(change.getValue())) {
            hidden.add(world);
          }
        }
        case "players" -> {
          hiddenPlayers.remove(world);
          if (Boolean.TRUE.equals(change.getValue())) {
            hiddenPlayers.add(world);
          }
        }
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
    if (next.get(ORDER) instanceof final @NotNull List<?> wanted) {
      worlds = true;
      order = order(wanted.stream().map(String::valueOf).toList(), enabled);
    }
    if (worlds) {
      this.config.setWorlds(this.plugin, order, hidden, hiddenPlayers, names);
    }
    for (final @NotNull String field : limitFields) {
      final @Nullable World world = WorldKeys.resolve(worldOf(field));
      if (world != null) {
        this.renders.limit(world, limit(next.get(field)));
      }
    }

    boolean layers = false;
    for (final Map.@NotNull Entry<String, Object> change : next.entrySet()) {
      if (change.getKey().startsWith(LAYER)) {
        this.config.setLayerPrivate(
            this.plugin,
            change.getKey().substring(LAYER.length()),
            Boolean.TRUE.equals(change.getValue()));
        layers = true;
      }
    }
    if (layers) {
      this.layersChanged.run();
    }

    next.forEach(
        (field, value) -> this.plugin.getLogger().info(who + " set " + field + " to " + value));
    track(who);
    return new Applied(viewNow());
  }

  private @Nullable Object normalize(
      final @NotNull String field,
      final @Nullable Object value,
      final @NotNull Map<String, Object> current) {
    if (value == null) {
      return null;
    }
    if (field.startsWith(SETTING)) {
      final @Nullable Setting setting = setting(field.substring(SETTING.length()));
      return setting == null || !setting.editable() ? null : valid(setting.key(), value);
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
        return WorldKeys.resolve(world) == null ? null : normalizeLimit(value);
      }
      return field.endsWith(":name")
          ? value instanceof final @NotNull String name ? worldName(world, name) : null
          : value instanceof Boolean ? value : null;
    }
    return field.startsWith(LAYER) && value instanceof Boolean ? value : null;
  }

  private @NotNull Map<String, Object> values() {
    final @NotNull Map<String, Object> values = new LinkedHashMap<>();
    for (final @NotNull Setting setting : SETTINGS) {
      final @Nullable Object value = get(setting.key());
      values.put(
          SETTING + setting.key(),
          switch (setting.kind()) {
            case NUMBER -> value instanceof final Number number ? number.intValue() : null;
            case TOGGLE -> Boolean.TRUE.equals(value);
            case TEXT -> value == null ? "" : String.valueOf(value);
          });
    }
    for (final @NotNull String world : worldIds()) {
      values.put(worldField(world, "enabled"), this.config.worldEnabled(world));
      values.put(worldField(world, "private"), this.config.worldPrivate(world));
      values.put(worldField(world, "players"), this.config.playersPrivate(world));
      values.put(worldField(world, "name"), this.config.worldNames().getOrDefault(world, ""));
      values.put(worldField(world, LIMIT), limitValue(this.limits.get(world)));
    }
    values.put(ORDER, List.copyOf(this.config.enabledWorlds()));
    for (final @NotNull LayerInfo layer : layers()) {
      values.put(LAYER + layer.id(), this.config.layerPrivate(layer.id()));
    }
    return values;
  }

  private @NotNull List<String> worldIds() {
    final @NotNull List<String> ids = new ArrayList<>(this.config.enabledWorlds());
    Bukkit.getWorlds().stream()
        .map(WorldKeys::id)
        .filter(id -> !ids.contains(id))
        .sorted(Comparator.naturalOrder())
        .forEach(ids::add);
    return ids;
  }

  private @NotNull List<LayerInfo> layers() {
    final @NotNull Map<String, LayerInfo> layers = new LinkedHashMap<>();
    for (final @NotNull MarkerLayer layer : this.markers.layers()) {
      layers.put(layer.id(), new LayerInfo(layer.id(), layer.label(), true));
    }
    for (final Waypoints.@NotNull Group group : this.waypoints.groups()) {
      layers.putIfAbsent(group.id(), new LayerInfo(group.id(), group.label(), true));
    }
    for (final @NotNull String layer : this.config.privateLayers()) {
      layers.putIfAbsent(layer, new LayerInfo(layer, layer, false));
    }
    return List.copyOf(layers.values());
  }

  private @Nullable String worldName(final @NotNull String worldName, final @NotNull String given) {
    final @Nullable NamespacedKey key = NamespacedKey.fromString(worldName);
    if (key == null || given.isBlank() || given.strip().equals(WorldKeys.displayName(key))) {
      return key == null ? null : "";
    }
    return worldName.contains(".") ? null : Names.of(given);
  }

  private @Nullable Object valid(final @NotNull String key, final @NotNull Object value) {
    final @Nullable Setting setting = setting(key);
    if (setting == null) {
      return null;
    }
    return switch (setting.kind()) {
      case NUMBER ->
          value instanceof final @NotNull Number number
                  && number.doubleValue() == number.intValue()
                  && number.intValue() >= setting.min()
                  && number.intValue() <= setting.max()
              ? number.intValue()
              : null;
      case TOGGLE -> value instanceof Boolean ? value : null;
      case TEXT -> {
        if (!(value instanceof final @NotNull String text) || CONTROL.matcher(text).find()) {
          yield null;
        }
        if (key.equals("web.name") && !text.isBlank()) {
          yield Names.of(text);
        }
        yield text.strip();
      }
    };
  }

  private void write(final @NotNull String key, final @NotNull Object value) {
    this.plugin.getConfig().set(key, value);
  }

  public enum Effect {
    APPLIED,
    WEB_RESTARTED,
    REJECTED
  }

  public enum Kind {
    NUMBER,
    TOGGLE,
    TEXT
  }

  public sealed interface Outcome permits Applied, Conflicted, Invalid, Busy {}

  public record Setting(
      @NotNull String key, @NotNull Kind kind, int min, int max, boolean editable) {

    private static @NotNull Setting number(
        final @NotNull String key, final int min, final int max, final boolean editable) {
      return new Setting(key, Kind.NUMBER, min, max, editable);
    }

    private static @NotNull Setting text(final @NotNull String key, final boolean editable) {
      return new Setting(key, Kind.TEXT, 0, 0, editable);
    }

    private static @NotNull Setting toggle(final @NotNull String key) {
      return new Setting(key, Kind.TOGGLE, 0, 0, true);
    }
  }

  public record Change(@NotNull Effect effect, @NotNull Object value) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SettingInfo(
      @NotNull String key,
      @NotNull String kind,
      boolean editable,
      @Nullable Integer min,
      @Nullable Integer max) {}

  public record WorldInfo(
      @NotNull String id, @NotNull String defaultName, @Nullable String environment) {}

  public record LayerInfo(@NotNull String id, @NotNull String label, boolean present) {}

  public record View(
      long revision,
      @NotNull List<SettingInfo> settings,
      @NotNull List<WorldInfo> worlds,
      @NotNull List<LayerInfo> layers,
      @NotNull Map<String, Object> values,
      @NotNull Map<String, SettingsRevisions.Author> authors) {}

  public record Conflict(
      @NotNull String field, @Nullable Object value, SettingsRevisions.@Nullable Author by) {}

  public record Applied(@NotNull View view) implements Outcome {}

  public record Conflicted(@NotNull List<Conflict> conflicts, @NotNull View view)
      implements Outcome {}

  public record Invalid(@NotNull String field) implements Outcome {}

  public record Busy(@NotNull String field, @NotNull View view) implements Outcome {}
}
