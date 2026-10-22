package net.aerulion.taerra.icon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.MarkerIcons;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.marker.MarkerRegistryImpl;
import net.aerulion.taerra.marker.TestPlugins;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkerIconsImplTest {

  private final @NotNull MarkerRegistry markers = new MarkerRegistryImpl();
  @TempDir Path folder;

  private static byte @NotNull [] png(final int size) throws IOException {
    final @NotNull BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    image.setRGB(3, 4, 0xFFFF0000);
    final @NotNull ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  private @NotNull MarkerIconsImpl icons() {
    final @NotNull MarkerIconsImpl icons =
        new MarkerIconsImpl(
            TestPlugins.in(this.folder.toFile(), TestPlugins.named("Towns")),
            this.markers,
            this.folder.resolve("icons").toFile());
    icons.load();
    return icons;
  }

  @Test
  @DisplayName("every built-in icon ships with the plugin")
  void builtIns() {
    final @NotNull MarkerIconsImpl icons = icons();

    for (final @NotNull String name : MarkerIconsImpl.BUILT_IN) {
      final MarkerIconsImpl.Icon icon = icons.icon(MarkerIcons.builtIn(name));
      assertNotNull(icon, name);
      assertNotNull(MarkerIconsImpl.decode(icon.png()), name);
      assertTrue(icon.tint(), name);
    }
  }

  @Test
  @DisplayName("an uploaded icon is kept across restarts under its name until it is deleted")
  void uploadRenameDelete() throws IOException {
    final MarkerIconsImpl.@NotNull Result uploaded = icons().upload(" Town Hall ", png(8));
    assertEquals(MarkerIconsImpl.Outcome.SAVED, uploaded.outcome());
    final @NotNull NamespacedKey key = uploaded.icon().key();
    assertTrue(key.asString().startsWith("taerra:upload-"));

    final @NotNull MarkerIconsImpl restarted = icons();
    assertEquals("Town Hall", restarted.icon(key).label());
    assertEquals(
        MarkerIconsImpl.Outcome.SAVED, restarted.update(key, "Guild Hall", null).outcome());
    assertEquals("Guild Hall", icons().icon(key).label());

    assertTrue(restarted.delete(key));
    assertFalse(restarted.exists(key));
    assertFalse(icons().exists(key));
    assertEquals(List.of("icons.yml"), List.of(this.folder.resolve("icons").toFile().list()));
  }

  @Test
  @DisplayName("uploads must be named, unique and an 8 × 8 PNG")
  void rejectsBadUploads() throws IOException {
    final @NotNull MarkerIconsImpl icons = icons();
    icons.upload("Mine", png(8));

    assertEquals(MarkerIconsImpl.Outcome.SAVED, icons.upload("Small", png(8)).outcome());
    assertEquals(MarkerIconsImpl.Outcome.BAD_IMAGE, icons.upload("Big", png(16)).outcome());
    assertEquals(
        MarkerIconsImpl.Outcome.BAD_IMAGE, icons.upload("Text", "not a png".getBytes()).outcome());
    assertEquals(MarkerIconsImpl.Outcome.BAD_NAME, icons.upload("<script>", png(8)).outcome());
    assertEquals(MarkerIconsImpl.Outcome.TAKEN, icons.upload("mine", png(8)).outcome());
    assertEquals(
        MarkerIconsImpl.Outcome.NOT_FOUND,
        icons.update(MarkerIcons.builtIn("home"), "Home", null).outcome());
    assertFalse(icons.delete(MarkerIcons.builtIn("home")));
  }

  @Test
  @DisplayName("plugins register 8 × 8 icons only in their own namespace and remove only their own")
  void pluginIcons() {
    final @NotNull MarkerIconsImpl icons = icons();
    final @NotNull BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
    final @NotNull NamespacedKey key = new NamespacedKey("towns", "spawn");

    icons.register(key, image, "Town spawn");
    assertTrue(icons.exists(key));
    assertThrows(
        IllegalArgumentException.class,
        () -> icons.register(MarkerIcons.builtIn("home"), image, "Home"));
    assertThrows(
        IllegalArgumentException.class,
        () -> icons.register(new NamespacedKey("elsewhere", "spawn"), image, "Spawn"));
    assertThrows(
        IllegalArgumentException.class,
        () -> icons.register(new NamespacedKey("towns", "spawn/big"), image, "Big spawn"));
    assertThrows(
        IllegalArgumentException.class,
        () -> icons.register(key, new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "Big"));
    assertFalse(icons.unregister(MarkerIcons.builtIn("home")));
    assertTrue(icons.unregister(key));
    assertFalse(icons.exists(key));
  }

  @Test
  @DisplayName("an icon counts the points showing it and the layers using it as their default")
  void uses() throws IOException {
    final @NotNull MarkerIconsImpl icons = icons();
    final @NotNull NamespacedKey key = icons.upload("Shop", png(8)).icon().key();
    this.markers
        .layer(TestPlugins.named("Taerra"), "shops", "Shops")
        .set(
            Marker.point("bakery", NamespacedKey.minecraft("overworld"), new Vector(), "Bakery")
                .withIcon(key));

    assertEquals(Map.of(key, 2), icons.uses(List.of(key.asString())));
    assertTrue(new File(this.folder.toFile(), "icons/icons.yml").isFile());
  }

  @Test
  @DisplayName("an icon is tinted only if its visible pixels are all pure white or all pure black")
  void tint() {
    final @NotNull BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
    assertFalse(MarkerIconsImpl.monochrome(image), "empty");
    image.setRGB(0, 0, 0xFFFFFFFF);
    image.setRGB(1, 0, 0x80FFFFFF);
    assertTrue(MarkerIconsImpl.monochrome(image), "white");
    image.setRGB(2, 0, 0xFF000000);
    assertFalse(MarkerIconsImpl.monochrome(image), "white and black");
    image.setRGB(0, 0, 0xFF000000);
    image.setRGB(1, 0, 0x80000000);
    assertTrue(MarkerIconsImpl.monochrome(image), "black");
    image.setRGB(3, 0, 0xFF010101);
    assertFalse(MarkerIconsImpl.monochrome(image), "nearly black");
  }
}
