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
import java.util.function.Predicate;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.limit.WorldLimits;
import net.aerulion.taerra.model.MapViews;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.model.WorldLimit;
import net.aerulion.taerra.rendering.RenderService;
import net.aerulion.taerra.storage.StorageSettings;
import net.aerulion.taerra.storage.TileStorage;
import net.aerulion.taerra.util.MainThread;
import net.aerulion.taerra.util.Throwables;
import net.aerulion.taerra.waypoint.Waypoints;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
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
          Setting.number("cache.max-tiles", 0, TaerraConfig.CACHE_TILES_LIMIT, true),
          Setting.number("cache.max-chunks", 0, TaerraConfig.CACHE_CHUNKS_LIMIT, true),
          Setting.toggle("markers.spawn.enabled"),
          Setting.toggle("markers.spawn.shown-by-default"),
          Setting.toggle("markers.worldguard.enabled"),
          Setting.toggle("markers.worldguard.shown-by-default"),
          Setting.toggle("markers.towny.enabled"),
          Setting.toggle("markers.towny.shown-by-default"),
          Setting.toggle("markers.griefprevention.enabled"),
          Setting.toggle("markers.griefprevention.shown-by-default"),
          Setting.toggle("markers.plotsquared.enabled"),
          Setting.toggle("markers.plotsquared.shown-by-default"),
          Setting.toggle("markers.worldborder.enabled"),
          Setting.toggle("markers.worldborder.shown-by-default"),
          Setting.toggle("markers.loaded-chunks.enabled"),
          Setting.toggle("markers.loaded-chunks.shown-by-default"),
          Setting.choice("storage.type", StorageSettings.FILES, "mariadb", "mysql"),
          Setting.text("storage.database.host", true),
          Setting.number("storage.database.port", 1, 65535, true),
          Setting.text("storage.database.name", true),
          Setting.text("storage.database.user", true),
          Setting.secret("storage.database.password"));

  public static final @NotNull String SETTING = "setting:";
  public static final @NotNull String STORAGE = "storage.";
  public static final @NotNull String MASK = "********";
  public static final @NotNull String WORLD = "world:";
  public static final @NotNull String LAYER = "layer:";
  public static final @NotNull String LAYER_ICON = "layer-icon:";
  public static final @NotNull String LAYER_SCALE = "layer-scale:";
  public static final @NotNull String ORDER = "order";
  public static final @NotNull String LIMIT = "limit";
  public static final @NotNull String VIEWS = "views";
  private static final long TRACK_INTERVAL_TICKS = 20L;
  private static final int MAX_COORDINATE = 30_000_000;

  private final @NotNull Plugin plugin;
  private final @NotNull TaerraConfig config;
  private final @NotNull MarkerRegistry markers;
  private final @NotNull Waypoints waypoints;
  private final @NotNull WorldLimits limits;
  private final @NotNull RenderService renders;
  private final @NotNull Consumer<Set<String>> applied;
  private final @NotNull Runnable layersChanged;
  private final @NotNull LongConsumer published;
  private final @NotNull Predicate<String> iconExists;
  private final @NotNull TileStorage tileStorage;
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
      final @NotNull LongConsumer published,
      final @NotNull Predicate<String> iconExists,
      final @NotNull TileStorage tileStorage) {
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
    this.iconExists = iconExists;
    this.tileStorage = tileStorage;
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

  static @NotNull String worldOf(final @NotNull String field) {
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

  public void start() {
    this.revisions.seed(values(), System.currentTimeMillis());
    this.tileStorage.resume(Set.copyOf(worldIds()));
    Bukkit.getScheduler()
        .runTaskTimer(this.plugin, () -> track(null), TRACK_INTERVAL_TICKS, TRACK_INTERVAL_TICKS);
  }

  public @Nullable Object get(final @NotNull String key) {
    final @Nullable Object value = this.plugin.getConfig().get(key);
    final @Nullable Setting setting = setting(key);
    return setting == null || setting.kind() != Setting.Kind.SECRET
        ? value
        : value == null || String.valueOf(value).isEmpty() ? "" : MASK;
  }

  public @NotNull StorageSettings storage() {
    return this.tileStorage.settings();
  }

  public @NotNull CompletableFuture<Outcome> setStorage(
      final @NotNull String key, final @NotNull Object given, final @NotNull String who) {
    return apply(
        this.revisions.revision(), Map.of(SETTING + key, "\"\"".equals(given) ? "" : given), who);
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
    final @Nullable String name = SettingsPlan.worldName(worldName, given);
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

  public @NotNull LayerIcon setLayerIcon(
      final @NotNull String layerId, final @NotNull String icon, final @NotNull String who) {
    if (this.markers.existingLayer(layerId) == null && this.config.layerIcon(layerId) == null) {
      return LayerIcon.UNKNOWN_LAYER;
    }
    if (!icon.isEmpty() && !this.iconExists.test(icon)) {
      return LayerIcon.UNKNOWN_ICON;
    }
    this.config.setLayerIcon(this.plugin, layerId, icon);
    this.layersChanged.run();
    track(who);
    return LayerIcon.SET;
  }

  public boolean setLayerScale(
      final @NotNull String layerId, final @Nullable Integer scale, final @NotNull String who) {
    if (this.markers.existingLayer(layerId) == null && this.config.layerScale(layerId) == null) {
      return false;
    }
    this.config.setLayerScale(this.plugin, layerId, scale);
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

  public RenderService.@Nullable Start setViews(
      final @NotNull World world, final @NotNull MapViews views, final @NotNull String who) {
    final RenderService.@Nullable Start start = this.renders.views(world, views);
    if (!(start instanceof RenderService.Refused)) {
      track(who);
    }
    return start;
  }

  public @NotNull CompletableFuture<View> view() {
    return MainThread.supply(this.plugin, this::viewNow);
  }

  public @NotNull CompletableFuture<Outcome> apply(
      final long base, final @NotNull Map<String, Object> changes, final @NotNull String who) {
    return MainThread.supply(
            this.plugin,
            () -> {
              final @Nullable StorageSettings target = storageTarget(changes);
              return target == null
                  ? new Step(applyNow(base, changes, who, null), null)
                  : new Step(
                      storageBusy() ? new Busy(SETTING + "storage.type", viewNow()) : null, target);
            })
        .thenCompose(
            step ->
                step.outcome() != null
                    ? CompletableFuture.completedFuture(step.outcome())
                    : prepare(Objects.requireNonNull(step.target()))
                        .handle(
                            (prepared, error) ->
                                MainThread.supply(
                                    this.plugin,
                                    () ->
                                        error != null
                                            ? new Unreachable(
                                                SETTING + "storage.type", reason(error))
                                            : applyNow(base, changes, who, prepared)))
                        .thenCompose(outcome -> outcome));
  }

  private record Step(@Nullable Outcome outcome, @Nullable StorageSettings target) {}

  private static @NotNull String reason(final @NotNull Throwable error) {
    @NotNull Throwable root = error;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    final @Nullable String message = root.getMessage();
    return message == null || message.isBlank()
        ? Throwables.describe(root)
        : message.replaceFirst("^\\(conn=\\d+\\) ", "");
  }

  private boolean storageBusy() {
    return this.renders.rendering() != null || this.tileStorage.moving();
  }

  private @NotNull CompletableFuture<TileStorage.Prepared> prepare(
      final @NotNull StorageSettings target) {
    final @NotNull CompletableFuture<TileStorage.Prepared> prepared = new CompletableFuture<>();
    Thread.ofPlatform()
        .name("Taerra-Storage")
        .daemon()
        .start(
            () -> {
              try {
                prepared.complete(this.tileStorage.prepare(target));
              } catch (final Exception e) {
                prepared.completeExceptionally(e);
              }
            });
    return prepared;
  }

  private @Nullable StorageSettings storageTarget(final @NotNull Map<String, Object> changes) {
    final @NotNull YamlConfiguration merged = new YamlConfiguration();
    boolean touched = false;
    for (final @NotNull Setting setting : SETTINGS) {
      if (!setting.key().startsWith(STORAGE)) {
        continue;
      }
      merged.set(setting.key(), this.plugin.getConfig().get(setting.key()));
      final @Nullable Object change = changes.get(SETTING + setting.key());
      final @Nullable Object value = change == null ? null : valid(setting.key(), change);
      if (value != null && !MASK.equals(value)) {
        merged.set(setting.key(), value);
        touched = true;
      }
    }
    if (!touched) {
      return null;
    }
    final @NotNull StorageSettings target = StorageSettings.of(merged);
    return target.equals(this.tileStorage.settings()) ? null : target;
  }

  private SettingsPlan.@NotNull Server server() {
    return new SettingsPlan.Server() {
      @Override
      public boolean worldLoaded(final @NotNull String world) {
        return WorldKeys.resolve(world) != null;
      }

      @Override
      public boolean worldEnabled(final @NotNull String world) {
        return ConfigEditor.this.config.worldEnabled(world);
      }

      @Override
      public @NotNull MapViews views(final @NotNull String world) {
        return ConfigEditor.this.config.views(world);
      }

      @Override
      public boolean iconExists(final @NotNull String icon) {
        return ConfigEditor.this.iconExists.test(icon);
      }

      @Override
      public boolean rendering() {
        return ConfigEditor.this.renders.rendering() != null;
      }
    };
  }

  private void track(final @Nullable String who) {
    if (!this.revisions.track(values(), who, System.currentTimeMillis()).isEmpty()) {
      this.published.accept(this.revisions.revision());
    }
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
                        setting.kind() == Setting.Kind.NUMBER ? setting.min() : null,
                        setting.kind() == Setting.Kind.NUMBER ? setting.max() : null,
                        setting.kind() == Setting.Kind.CHOICE ? setting.options() : null))
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
      final long base,
      final @NotNull Map<String, Object> changes,
      final @NotNull String who,
      final TileStorage.@Nullable Prepared prepared) {
    try {
      return applyChecked(base, changes, who, prepared);
    } finally {
      if (prepared != null) {
        prepared.discard();
      }
    }
  }

  private @NotNull Outcome applyChecked(
      final long base,
      final @NotNull Map<String, Object> changes,
      final @NotNull String who,
      final TileStorage.@Nullable Prepared prepared) {
    final SettingsPlan.@NotNull Ready ready;
    switch (SettingsPlan.plan(base, changes, values(), this.revisions, server())) {
      case SettingsPlan.Invalid(final String field) -> {
        return new Invalid(field);
      }
      case SettingsPlan.Conflicted(final List<Conflict> conflicts) -> {
        return new Conflicted(conflicts, viewNow());
      }
      case SettingsPlan.Busy(final String field) -> {
        return new Busy(field, viewNow());
      }
      case final SettingsPlan.Ready planned -> ready = planned;
    }
    final @NotNull Map<String, Object> next = ready.next();
    if (next.isEmpty()) {
      return new Applied(viewNow());
    }
    final @NotNull List<String> limitFields = ready.limitFields();
    final @NotNull List<String> viewFields = ready.viewFields();
    final @Nullable StorageSettings target = storageTarget(changes);
    if (target != null
        && (prepared == null || !target.equals(prepared.settings()) || storageBusy())) {
      return new Busy(SETTING + "storage.type", viewNow());
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
    if (target != null) {
      this.tileStorage.move(prepared, Set.copyOf(worldIds()));
    }

    final SettingsPlan.@Nullable Worlds worlds =
        SettingsPlan.worlds(
            ready,
            this.config.enabledWorlds(),
            new LinkedHashSet<>(this.config.privateWorlds()),
            new LinkedHashSet<>(this.config.privatePlayers()),
            this.config.worldNames());
    if (worlds != null) {
      this.config.setWorlds(
          this.plugin, worlds.order(), worlds.hidden(), worlds.hiddenPlayers(), worlds.names());
    }
    for (final @NotNull String field : viewFields) {
      final @Nullable World world = WorldKeys.resolve(worldOf(field));
      if (world != null) {
        this.renders.views(world, SettingsPlan.views(next.get(field)));
      }
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
      } else if (change.getKey().startsWith(LAYER_ICON)) {
        this.config.setLayerIcon(
            this.plugin,
            change.getKey().substring(LAYER_ICON.length()),
            (String) change.getValue());
        layers = true;
      } else if (change.getKey().startsWith(LAYER_SCALE)) {
        this.config.setLayerScale(
            this.plugin,
            change.getKey().substring(LAYER_SCALE.length()),
            LayerScale.parse((String) change.getValue()));
        layers = true;
      }
    }
    if (layers) {
      this.layersChanged.run();
    }

    next.forEach(
        (field, value) ->
            this.plugin
                .getLogger()
                .info(
                    who
                        + " set "
                        + field
                        + " to "
                        + (field.equals(SETTING + "storage.database.password") ? MASK : value)));
    track(who);
    return new Applied(viewNow());
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
            case TEXT, CHOICE, SECRET -> value == null ? "" : String.valueOf(value);
          });
    }
    for (final @NotNull String world : worldIds()) {
      values.put(worldField(world, "enabled"), this.config.worldEnabled(world));
      values.put(worldField(world, "private"), this.config.worldPrivate(world));
      values.put(worldField(world, "players"), this.config.playersPrivate(world));
      values.put(worldField(world, "name"), this.config.worldNames().getOrDefault(world, ""));
      values.put(worldField(world, LIMIT), limitValue(this.limits.get(world)));
      values.put(worldField(world, VIEWS), this.config.views(world).id());
    }
    values.put(ORDER, List.copyOf(this.config.enabledWorlds()));
    for (final @NotNull LayerInfo layer : layers()) {
      values.put(LAYER + layer.id(), this.config.layerPrivate(layer.id()));
      values.put(
          LAYER_ICON + layer.id(),
          Objects.requireNonNullElse(this.config.layerIcon(layer.id()), ""));
      final @Nullable Integer scale = this.config.layerScale(layer.id());
      values.put(LAYER_SCALE + layer.id(), scale == null ? "" : LayerScale.format(scale));
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
    for (final @NotNull String layer : this.config.layerIcons().keySet()) {
      layers.putIfAbsent(layer, new LayerInfo(layer, layer, false));
    }
    for (final @NotNull String layer : this.config.layerScales().keySet()) {
      layers.putIfAbsent(layer, new LayerInfo(layer, layer, false));
    }
    return List.copyOf(layers.values());
  }

  private static @Nullable Object valid(final @NotNull String key, final @NotNull Object value) {
    final @Nullable Setting setting = setting(key);
    return setting == null ? null : setting.valid(value);
  }

  private void write(final @NotNull String key, final @NotNull Object value) {
    this.plugin.getConfig().set(key, value);
  }

  public enum Effect {
    APPLIED,
    WEB_RESTARTED,
    REJECTED
  }

  public enum LayerIcon {
    SET,
    UNKNOWN_LAYER,
    UNKNOWN_ICON
  }

  public sealed interface Outcome permits Applied, Conflicted, Invalid, Busy, Unreachable {}

  public record Change(@NotNull Effect effect, @NotNull Object value) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SettingInfo(
      @NotNull String key,
      @NotNull String kind,
      boolean editable,
      @Nullable Integer min,
      @Nullable Integer max,
      @Nullable List<String> options) {}

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

  public record Unreachable(@NotNull String field, @NotNull String reason) implements Outcome {}
}
