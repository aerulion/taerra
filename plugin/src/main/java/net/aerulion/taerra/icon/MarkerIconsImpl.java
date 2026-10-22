package net.aerulion.taerra.icon;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import net.aerulion.taerra.api.marker.MarkerIcons;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.api.marker.PointMarker;
import net.aerulion.taerra.util.AtomicFiles;
import net.aerulion.taerra.util.Names;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MarkerIconsImpl implements MarkerIcons, Listener {

  public static final @NotNull List<String> BUILT_IN =
      List.of(
          "marker",
          "gem",
          "home",
          "castle",
          "tower",
          "temple",
          "shop",
          "chest",
          "bed",
          "anchor",
          "boat",
          "tree",
          "mountain",
          "fish",
          "fire",
          "pickaxe",
          "sword",
          "key",
          "potion",
          "bell",
          "crown",
          "flag",
          "star",
          "heart",
          "skull",
          "death",
          "info",
          "warning");
  public static final int MAX_UPLOADS = 256;
  public static final int MAX_BYTES = 16 * 1024;
  private static final @NotNull String NAMESPACE = "taerra";
  private static final @NotNull String UPLOAD = "upload-";
  private static final byte @NotNull [] SIGNATURE = {
    (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'
  };

  private final @NotNull Plugin plugin;
  private final @NotNull MarkerRegistry markers;
  private final @NotNull File folder;
  private final @NotNull File index;
  private final @NotNull Map<NamespacedKey, Icon> icons = new LinkedHashMap<>();
  private @NotNull Runnable changed = () -> {};

  public MarkerIconsImpl(
      final @NotNull Plugin plugin,
      final @NotNull MarkerRegistry markers,
      final @NotNull File folder) {
    super();
    this.plugin = plugin;
    this.markers = markers;
    this.folder = folder;
    this.index = new File(folder, "icons.yml");
  }

  public static @Nullable BufferedImage decode(final byte @Nullable [] png) {
    if (png == null
        || png.length < 24
        || png.length > MAX_BYTES
        || !Arrays.equals(png, 0, SIGNATURE.length, SIGNATURE, 0, SIGNATURE.length)
        || !fits(ByteBuffer.wrap(png, 16, 8).getInt(), ByteBuffer.wrap(png, 20, 4).getInt())) {
      return null;
    }
    try {
      final @Nullable BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
      return image != null && fits(image.getWidth(), image.getHeight()) ? image : null;
    } catch (final IOException | RuntimeException e) {
      return null;
    }
  }

  private static boolean fits(final int width, final int height) {
    return width == SIZE && height == SIZE;
  }

  static byte @NotNull [] encode(final @NotNull BufferedImage image) {
    final @NotNull BufferedImage argb = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    argb.setRGB(0, 0, SIZE, SIZE, image.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE), 0, SIZE);
    return write(argb);
  }

  private static byte @NotNull [] write(final @NotNull BufferedImage image) {
    final @NotNull ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, "png", out);
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
    return out.toByteArray();
  }

  private static @NotNull Icon icon(
      final @NotNull NamespacedKey key,
      final @Nullable String label,
      final @NotNull Source source,
      final @NotNull BufferedImage image) {
    final byte @NotNull [] png = encode(image);
    return new Icon(key, label, source, png, version(png), monochrome(image));
  }

  static boolean monochrome(final @NotNull BufferedImage image) {
    final @NotNull Set<Integer> colors = new HashSet<>();
    for (final int pixel : image.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE)) {
      if (pixel >>> 24 != 0) {
        colors.add(pixel & 0xFFFFFF);
      }
    }
    return colors.equals(Set.of(0xFFFFFF)) || colors.equals(Set.of(0));
  }

  private static @NotNull String version(final byte @NotNull [] png) {
    final @NotNull CRC32 crc = new CRC32();
    crc.update(png);
    return Long.toHexString(crc.getValue());
  }

  public void onChange(final @NotNull Runnable changed) {
    this.changed = changed;
  }

  public synchronized void load() {
    for (final @NotNull String name : BUILT_IN) {
      try (final @Nullable InputStream in = this.plugin.getResource("icons/" + name + ".png")) {
        if (in == null) {
          throw new IllegalStateException("Missing built-in icon " + name);
        }
        final @Nullable BufferedImage image = decode(in.readAllBytes());
        if (image == null) {
          throw new IllegalStateException("Unreadable built-in icon " + name);
        }
        final @NotNull NamespacedKey key = MarkerIcons.builtIn(name);
        this.icons.put(key, icon(key, null, Source.BUILT_IN, image));
      } catch (final IOException e) {
        throw new IllegalStateException("Failed to read the built-in icon " + name, e);
      }
    }
    final @Nullable ConfigurationSection names =
        YamlConfiguration.loadConfiguration(this.index).getConfigurationSection("icons");
    if (names == null) {
      return;
    }
    for (final @NotNull String id : names.getKeys(false)) {
      final @Nullable NamespacedKey key = NamespacedKey.fromString(NAMESPACE + ":" + UPLOAD + id);
      final @Nullable String name = Names.of(names.getString(id));
      final byte @Nullable [] png = key == null || name == null ? null : read(id);
      final @Nullable BufferedImage image = png == null ? null : decode(png);
      if (image == null) {
        this.plugin.getLogger().warning("Skipping the unreadable icon " + id + " in icons.yml");
        continue;
      }
      this.icons.put(key, icon(key, name, Source.UPLOAD, image));
    }
  }

  private byte @Nullable [] read(final @NotNull String id) {
    try {
      return Files.readAllBytes(new File(this.folder, id + ".png").toPath());
    } catch (final IOException e) {
      return null;
    }
  }

  @Override
  public void register(
      final @NotNull NamespacedKey key,
      final @NotNull BufferedImage image,
      final @NotNull String label) {
    if (key.getNamespace().equals(NAMESPACE)
        || key.getNamespace().equals(NamespacedKey.MINECRAFT)
        || Arrays.stream(this.plugin.getServer().getPluginManager().getPlugins())
            .noneMatch(
                owner ->
                    owner.isEnabled()
                        && owner.getName().toLowerCase(Locale.ROOT).equals(key.getNamespace()))) {
      throw new IllegalArgumentException("Register icons in your plugin's namespace, not " + key);
    }
    if (key.getKey().contains("/")) {
      throw new IllegalArgumentException("Icon keys have no /, got " + key);
    }
    if (!fits(image.getWidth(), image.getHeight())) {
      throw new IllegalArgumentException(
          "Icons are "
              + SIZE
              + " × "
              + SIZE
              + ", got "
              + image.getWidth()
              + " × "
              + image.getHeight());
    }
    final @NotNull Icon icon = icon(key, label, Source.PLUGIN, image);
    synchronized (this) {
      this.icons.put(key, icon);
    }
    this.changed.run();
  }

  @Override
  public boolean unregister(final @NotNull NamespacedKey key) {
    final boolean removed;
    synchronized (this) {
      removed =
          this.icons.get(key) instanceof final Icon icon
              && icon.source() == Source.PLUGIN
              && this.icons.remove(key) != null;
    }
    if (removed) {
      this.changed.run();
    }
    return removed;
  }

  @Override
  public synchronized boolean exists(final @NotNull NamespacedKey key) {
    return this.icons.containsKey(key);
  }

  public boolean exists(final @Nullable String key) {
    final @Nullable NamespacedKey parsed = key == null ? null : NamespacedKey.fromString(key);
    return parsed != null && exists(parsed);
  }

  @Override
  public synchronized @NotNull Set<NamespacedKey> keys() {
    return Set.copyOf(this.icons.keySet());
  }

  public synchronized @Nullable Icon icon(final @NotNull NamespacedKey key) {
    return this.icons.get(key);
  }

  public synchronized @NotNull List<Icon> all() {
    return this.icons.values().stream()
        .sorted(
            Comparator.comparing(Icon::source)
                .thenComparing(
                    icon -> icon.label() == null ? "" : icon.label().toLowerCase(Locale.ROOT)))
        .toList();
  }

  public @NotNull Map<NamespacedKey, Integer> uses(final @NotNull Iterable<String> layerIcons) {
    final @NotNull Map<NamespacedKey, Integer> uses = new HashMap<>();
    for (final @NotNull MarkerLayer layer : this.markers.layers()) {
      layer.markers().stream()
          .filter(PointMarker.class::isInstance)
          .map(marker -> ((PointMarker) marker).icon())
          .filter(Objects::nonNull)
          .forEach(icon -> uses.merge(icon, 1, Integer::sum));
    }
    for (final @NotNull String icon : layerIcons) {
      final @Nullable NamespacedKey key = icon.isEmpty() ? null : NamespacedKey.fromString(icon);
      if (key != null) {
        uses.merge(key, 1, Integer::sum);
      }
    }
    return uses;
  }

  public @NotNull Result upload(final @Nullable String name, final byte @Nullable [] png) {
    final @Nullable String label = Names.of(name);
    final @Nullable BufferedImage image = decode(png);
    if (label == null) {
      return new Result(Outcome.BAD_NAME, null);
    }
    if (image == null) {
      return new Result(Outcome.BAD_IMAGE, null);
    }
    final @NotNull Result result;
    synchronized (this) {
      if (this.icons.values().stream().filter(icon -> icon.source() == Source.UPLOAD).count()
          >= MAX_UPLOADS) {
        return new Result(Outcome.FULL, null);
      }
      if (taken(label, null)) {
        return new Result(Outcome.TAKEN, null);
      }
      final @NotNull String id = UUID.randomUUID().toString();
      result =
          store(
              icon(
                  Objects.requireNonNull(NamespacedKey.fromString(NAMESPACE + ":" + UPLOAD + id)),
                  label,
                  Source.UPLOAD,
                  image));
    }
    if (result.outcome() == Outcome.SAVED) {
      this.changed.run();
    }
    return result;
  }

  public @NotNull Result update(
      final @NotNull NamespacedKey key, final @Nullable String name, final byte @Nullable [] png) {
    final @Nullable String label = name == null ? null : Names.of(name);
    final @Nullable BufferedImage image = png == null ? null : decode(png);
    if (name != null && label == null) {
      return new Result(Outcome.BAD_NAME, null);
    }
    if (png != null && image == null) {
      return new Result(Outcome.BAD_IMAGE, null);
    }
    final @NotNull Result result;
    synchronized (this) {
      final @Nullable Icon icon = this.icons.get(key);
      if (icon == null || icon.source() != Source.UPLOAD) {
        return new Result(Outcome.NOT_FOUND, null);
      }
      if (label != null && taken(label, key)) {
        return new Result(Outcome.TAKEN, null);
      }
      final @NotNull String named = label == null ? icon.label() : label;
      result =
          store(
              image == null
                  ? new Icon(key, named, Source.UPLOAD, icon.png(), icon.version(), icon.tint())
                  : icon(key, named, Source.UPLOAD, image));
    }
    if (result.outcome() == Outcome.SAVED) {
      this.changed.run();
    }
    return result;
  }

  public boolean delete(final @NotNull NamespacedKey key) {
    synchronized (this) {
      if (!(this.icons.get(key) instanceof final Icon icon) || icon.source() != Source.UPLOAD) {
        return false;
      }
      this.icons.remove(key);
      if (!save()) {
        this.icons.put(key, icon);
        return false;
      }
      new File(this.folder, id(key) + ".png").delete();
    }
    this.changed.run();
    return true;
  }

  @EventHandler
  public void onPluginDisable(final @NotNull PluginDisableEvent event) {
    final @NotNull String namespace = event.getPlugin().getName().toLowerCase(Locale.ROOT);
    final boolean removed;
    synchronized (this) {
      removed =
          this.icons
              .entrySet()
              .removeIf(
                  entry ->
                      entry.getValue().source() == Source.PLUGIN
                          && entry.getKey().getNamespace().equals(namespace));
    }
    if (removed) {
      this.changed.run();
    }
  }

  private boolean taken(final @NotNull String label, final @Nullable NamespacedKey except) {
    return this.icons.values().stream()
        .anyMatch(
            icon ->
                icon.source() == Source.UPLOAD
                    && !icon.key().equals(except)
                    && label.equalsIgnoreCase(icon.label()));
  }

  private @NotNull String id(final @NotNull NamespacedKey key) {
    return key.getKey().substring(UPLOAD.length());
  }

  private @NotNull Result store(final @NotNull Icon icon) {
    final @NotNull NamespacedKey key = icon.key();
    final @Nullable Icon previous = this.icons.get(key);
    final @NotNull File file = new File(this.folder, id(key) + ".png");
    try {
      AtomicFiles.write(file, icon.png());
    } catch (final IOException e) {
      this.plugin.getLogger().log(Level.SEVERE, "Failed to save the icon " + icon.label(), e);
      return new Result(Outcome.FAILED, null);
    }
    this.icons.put(key, icon);
    if (!save()) {
      if (previous == null) {
        this.icons.remove(key);
        file.delete();
      } else {
        this.icons.put(key, previous);
        try {
          AtomicFiles.write(file, previous.png());
        } catch (final IOException e) {
          this.plugin
              .getLogger()
              .log(Level.SEVERE, "Failed to restore the icon " + previous.label(), e);
        }
      }
      return new Result(Outcome.FAILED, null);
    }
    return new Result(Outcome.SAVED, icon);
  }

  private boolean save() {
    final @NotNull YamlConfiguration yaml = new YamlConfiguration();
    this.icons.values().stream()
        .filter(icon -> icon.source() == Source.UPLOAD)
        .forEach(icon -> yaml.set("icons." + id(icon.key()), icon.label()));
    try {
      AtomicFiles.write(this.index, yaml.saveToString().getBytes(StandardCharsets.UTF_8));
      return true;
    } catch (final IOException e) {
      this.plugin.getLogger().log(Level.SEVERE, "Failed to save icons.yml", e);
      return false;
    }
  }

  public enum Source {
    BUILT_IN,
    UPLOAD,
    PLUGIN
  }

  public enum Outcome {
    SAVED,
    NOT_FOUND,
    BAD_NAME,
    BAD_IMAGE,
    TAKEN,
    FULL,
    FAILED
  }

  public record Icon(
      @NotNull NamespacedKey key,
      @Nullable String label,
      @NotNull Source source,
      byte @NotNull [] png,
      @NotNull String version,
      boolean tint) {}

  public record Result(@NotNull Outcome outcome, @Nullable Icon icon) {}
}
