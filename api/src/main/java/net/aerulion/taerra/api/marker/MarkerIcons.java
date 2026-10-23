package net.aerulion.taerra.api.marker;

import java.awt.image.BufferedImage;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The icons a {@link PointMarker} shows on its badge, a square in the marker's color with an arrow
 * pointing at its position. Get it from {@link net.aerulion.taerra.api.TaerraApi#icons()}.
 *
 * <p>An icon is a {@value #SIZE} × {@value #SIZE} image named by a key, drawn one pixel per pixel
 * of the badge. TAERRA ships built-in icons in the {@code taerra} namespace, which {@link
 * #builtIn(String)} names: {@code marker}, {@code gem}, {@code home}, {@code castle}, {@code
 * tower}, {@code temple}, {@code shop}, {@code chest}, {@code bed}, {@code anchor}, {@code boat},
 * {@code tree}, {@code mountain}, {@code fish}, {@code fire}, {@code pickaxe}, {@code sword},
 * {@code key}, {@code potion}, {@code bell}, {@code crown}, {@code flag}, {@code star}, {@code
 * heart}, {@code skull}, {@code death}, {@code info}, {@code warning}, {@code person}, {@code
 * mail}, {@code checkmark}, {@code cross}, {@code plus}, {@code minus}, {@code ring}, {@code
 * arrow_up}, {@code arrow_right}, {@code arrow_down} and {@code arrow_left}. Icons the server's
 * admins upload on the web map or in the mod are in the {@code taerra} namespace too; icons plugins
 * register are in their own namespace and removed when the plugin disables. An icon whose visible
 * pixels are all pure white or all pure black is drawn white, or black on badges too light for
 * white, like the built-in ones; any other icon keeps its colors.
 *
 * <p>A point without a registered icon shows its layer's icon, if one is set with {@code /taerra
 * config layer}, or else {@code marker}, or {@code gem} on the layers only one player sees. All
 * methods are thread safe.
 */
@ApiStatus.NonExtendable
public interface MarkerIcons {

  /** The width and height of every icon, in pixels. */
  int SIZE = 8;

  /**
   * Returns the key of a built-in icon.
   *
   * @param name the icon's name, such as {@code home}
   * @return the icon's key
   * @throws IllegalArgumentException if the name is not a valid key
   */
  static @NotNull NamespacedKey builtIn(final @NotNull String name) {
    final @Nullable NamespacedKey key = NamespacedKey.fromString("taerra:" + name);
    if (key == null) {
      throw new IllegalArgumentException("Not an icon name: " + name);
    }
    return key;
  }

  /**
   * Adds an icon, replacing the one with the same key. Pixels are drawn as they are, without
   * smoothing; transparent pixels show the badge behind them.
   *
   * @param key the icon's key, in your plugin's namespace and without {@code /}
   * @param image the icon, {@value #SIZE} × {@value #SIZE} pixels
   * @param label the name shown in icon pickers
   * @throws IllegalArgumentException if the key is not in the namespace of an enabled plugin other
   *     than TAERRA, has a {@code /}, or the image is not {@value #SIZE} × {@value #SIZE}
   */
  void register(@NotNull NamespacedKey key, @NotNull BufferedImage image, @NotNull String label);

  /**
   * Removes an icon a plugin registered. Markers showing it fall back to their layer's icon or the
   * default one.
   *
   * @param key the icon's key
   * @return {@code true} if a plugin's icon was removed
   */
  boolean unregister(@NotNull NamespacedKey key);

  /**
   * Returns whether an icon is registered: built in, uploaded or added by a plugin.
   *
   * @param key the icon's key
   * @return {@code true} if markers showing it draw it
   */
  boolean exists(@NotNull NamespacedKey key);

  /**
   * Returns the keys of every registered icon.
   *
   * @return a snapshot of the keys
   */
  @NotNull
  Set<NamespacedKey> keys();
}
